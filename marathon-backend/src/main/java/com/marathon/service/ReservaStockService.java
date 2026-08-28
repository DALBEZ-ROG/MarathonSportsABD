package com.marathon.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.inventario.ReservaStockResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.DetallePedido;
import com.marathon.model.Pedido;
import com.marathon.model.Producto;
import com.marathon.model.ReservaStock;
import com.marathon.repository.DetallePedidoRepository;
import com.marathon.repository.InventarioRepository;
import com.marathon.repository.ReservaStockRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Reserva de existencias (F47, cierra D-02).
 *
 * <p><b>El problema que cierra.</b> Antes de esta fase, la falta de stock solo
 * frenaba la operacion en el despacho. Eso protege el inventario —desde la L1 no
 * se descuenta mas de lo que hay— pero no impide que dos pedidos comprometan las
 * mismas unidades: los dos se crean, los dos se procesan, los dos se recogen, y
 * el segundo se entera en el muelle. La reserva adelanta ese choque al momento
 * en que el pedido entra al almacen.
 *
 * <p><b>La regla, en una linea.</b>
 * {@code disponible(p) = SUM(inventario.stock_actual de p) - SUM(reservas activas de p)}.
 *
 * <p><b>Por que el grano es el producto y no (producto, bodega).</b> Cuando se
 * reserva —al pasar el pedido a {@code procesado}— todavia no se sabe de que
 * bodega saldra la mercancia: eso lo decide el picking despues (F45). Reservar
 * contra una bodega concreta seria adivinar, y adivinar la bodega es justamente
 * el defecto D-01 que la L4 acaba de cerrar.
 *
 * <p><b>Concurrencia.</b> Dos transiciones simultaneas a {@code procesado} del
 * mismo producto leerian las dos el mismo disponible y reservarian las dos. Se
 * serializan con {@code pg_advisory_xact_lock} por producto, y no con
 * {@code SELECT … FOR UPDATE} sobre {@code inventario} como hace el resto del
 * proyecto (L1), por un motivo concreto: PostgreSQL exige privilegio UPDATE
 * sobre la tabla para bloquear sus filas, y {@code rol_operador_pedidos} solo
 * tiene SELECT sobre {@code inventario} —correctamente, porque quien toma
 * pedidos no mueve stock—. Un bloqueo consultivo no necesita privilegio sobre
 * ninguna tabla y muere solo al terminar la transaccion.
 */
@Service
public class ReservaStockService {

    /**
     * Dias que una reserva puede retener mercancia antes de aparecer en el
     * informe de vencidas. Decision de negocio del 2026-08-27: 7 dias, y vencer
     * NO libera — lo decide una persona mirando el informe.
     */
    public static final int DIAS_VIGENCIA = 7;

    /**
     * Primer argumento de pg_advisory_xact_lock. Separa estos bloqueos de
     * cualquier otro uso de bloqueos consultivos que se anada despues: dos
     * usos distintos con la misma clave se bloquearian entre si sin motivo.
     */
    private static final int ESPACIO_BLOQUEO = 47;

    private final ReservaStockRepository reservaRepository;
    private final InventarioRepository inventarioRepository;
    private final DetallePedidoRepository detallePedidoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public ReservaStockService(ReservaStockRepository reservaRepository,
                               InventarioRepository inventarioRepository,
                               DetallePedidoRepository detallePedidoRepository) {
        this.reservaRepository = reservaRepository;
        this.inventarioRepository = inventarioRepository;
        this.detallePedidoRepository = detallePedidoRepository;
    }

    // ------------------------------------------------------------------
    // Consulta
    // ------------------------------------------------------------------

    /** Existencias de un producto sumando bodegas, sin descontar reservas. */
    public int stockTotal(Integer idProducto) {
        return inventarioRepository.stockTotalDe(idProducto);
    }

    /** Unidades que otros pedidos ya tienen comprometidas. */
    public int reservado(Integer idProducto) {
        return reservaRepository.reservadoDe(idProducto);
    }

    /**
     * Lo mismo sin contar un pedido. Lo usa el despacho: un pedido no compite
     * consigo mismo, sus unidades ya estan en la reserva que va a consumir.
     */
    public int reservadoPorOtrosPedidos(Integer idProducto, Integer idPedido) {
        return reservaRepository.reservadoDePorOtrosPedidos(idProducto, idPedido);
    }

    /**
     * Lo que de verdad se puede prometer a un pedido nuevo.
     *
     * <p>Puede salir negativo, y se devuelve negativo a proposito. Ocurre si
     * alguien saca stock por un camino que no pasa por aqui (un ajuste a la
     * baja, o un despacho de uno de los 19.058 pedidos que ya estaban en
     * 'procesado' antes de la F47 y por tanto no tienen reserva). Taparlo con un
     * {@code max(0, …)} convertiria "hay 5 unidades comprometidas de mas" en
     * "no hay nada", que son cosas distintas y se leen distinto.
     */
    public int disponible(Integer idProducto) {
        return stockTotal(idProducto) - reservado(idProducto);
    }

    // ------------------------------------------------------------------
    // Reservar
    // ------------------------------------------------------------------

    /**
     * Comprueba disponibilidad SIN retener nada. Lo usa la creacion del pedido.
     *
     * @return por producto, las unidades que faltan; vacio si alcanza para todo.
     */
    public Map<Producto, Integer> faltantesPara(List<DetallePedido> lineas) {
        return faltantesDe(productosPorId(lineas), agruparPorProducto(lineas));
    }

    /**
     * Lo mismo, partiendo de una demanda ya agrupada.
     *
     * <p>Existe porque la creacion del pedido comprueba disponibilidad
     * <i>antes</i> de guardar ninguna linea: en ese momento no hay
     * {@code DetallePedido} sobre los que preguntar, solo el DTO.
     */
    public Map<Producto, Integer> faltantesDe(Map<Integer, Producto> productos,
                                              Map<Integer, Integer> demanda) {
        Map<Producto, Integer> faltantes = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> pedido : demanda.entrySet()) {
            int disponible = disponible(pedido.getKey());
            if (disponible < pedido.getValue()) {
                faltantes.put(productos.get(pedido.getKey()), pedido.getValue() - disponible);
            }
        }
        return faltantes;
    }

    /** Texto legible del faltante, para el 400 y para la bitacora. */
    public String describirFaltantes(Map<Producto, Integer> faltantes) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Producto, Integer> f : faltantes.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            Producto p = f.getKey();
            String nombre = p != null ? p.getNombre() : "(producto desconocido)";
            Integer id = p != null ? p.getIdProducto() : null;
            sb.append("'").append(nombre).append("': faltan ").append(f.getValue())
              .append(" unidades (disponibles ")
              .append(id != null ? disponible(id) : 0).append(")");
        }
        return sb.toString();
    }

    /**
     * Retiene las unidades de un pedido que pasa a {@code procesado}.
     *
     * <p>O se reserva el pedido entero o no se reserva nada: la primera linea
     * que no quepa levanta {@code ValidationException} y la transaccion revierte
     * con las anteriores. Reservar medio pedido dejaria mercancia retenida por
     * un pedido que nadie va a poder despachar.
     */
    @Transactional
    public List<ReservaStock> reservarPara(Pedido pedido) {
        List<DetallePedido> lineas =
                detallePedidoRepository.findByPedidoIdPedidoOrderByIdDetalleAsc(pedido.getIdPedido());
        Map<Integer, Integer> porProducto = agruparPorProducto(lineas);
        Map<Integer, Producto> productos = productosPorId(lineas);

        // Se bloquea en orden ascendente de id_producto. Dos pedidos que
        // compartan productos toman los bloqueos en el mismo orden y por tanto
        // no pueden abrazarse; es la misma disciplina que el ORDER BY de
        // InventarioRepository.buscarPorProductoParaActualizar (L1).
        List<Integer> ids = new ArrayList<>(porProducto.keySet());
        ids.sort(Integer::compareTo);
        for (Integer idProducto : ids) {
            bloquearProducto(idProducto);
        }

        List<ReservaStock> creadas = new ArrayList<>();
        for (Integer idProducto : ids) {
            Producto producto = productos.get(idProducto);
            int cantidad = porProducto.get(idProducto);

            int disponible = disponible(idProducto);
            if (disponible < cantidad) {
                throw new ValidationException(
                        "No se puede procesar el pedido #" + pedido.getIdPedido() + ": de '"
                        + producto.getNombre() + "' se piden " + cantidad + " y solo hay "
                        + disponible + " disponibles (" + stockTotal(producto.getIdProducto())
                        + " en existencias menos " + reservado(producto.getIdProducto())
                        + " ya comprometidas por otros pedidos). No se ha reservado nada.");
            }

            ReservaStock reserva = new ReservaStock();
            reserva.setPedido(pedido);
            reserva.setProducto(producto);
            reserva.setCantidad(cantidad);
            reserva.setEstado(ReservaStock.ACTIVA);
            reserva.setFechaReserva(LocalDateTime.now());
            creadas.add(reservaRepository.save(reserva));
        }
        return creadas;
    }

    // ------------------------------------------------------------------
    // Cerrar
    // ------------------------------------------------------------------

    /** El despacho se llevo la mercancia: la reserva deja de retener. */
    @Transactional
    public int consumirDe(Integer idPedido) {
        return cerrarActivasDe(idPedido, ReservaStock.CONSUMIDA,
                "Despacho del pedido #" + idPedido);
    }

    /** El pedido se anulo: las unidades vuelven al disponible. */
    @Transactional
    public int liberarDe(Integer idPedido, String motivo) {
        return cerrarActivasDe(idPedido, ReservaStock.LIBERADA, motivo);
    }

    private int cerrarActivasDe(Integer idPedido, String estado, String motivo) {
        List<ReservaStock> activas = reservaRepository.activasDePedido(idPedido);
        for (ReservaStock reserva : activas) {
            reserva.cerrar(estado, motivo);
            reservaRepository.save(reserva);
        }
        return activas.size();
    }

    /**
     * Suelta UNA reserva concreta, por decision de una persona que ha visto el
     * informe de vencidas. Es el unico camino manual, y exige motivo: una
     * reserva liberada sin explicacion no se puede auditar despues.
     */
    @Transactional
    public ReservaStock liberarManualmente(Integer idReserva, String motivo) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new ValidationException("Hay que indicar por que se libera la reserva");
        }
        ReservaStock reserva = reservaRepository.findById(idReserva)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva", idReserva));
        if (!reserva.estaActiva()) {
            throw new ValidationException("La reserva #" + idReserva + " ya esta '"
                    + reserva.getEstado() + "'; solo se puede liberar una reserva activa.");
        }
        reserva.cerrar(ReservaStock.LIBERADA, motivo.trim());
        return reservaRepository.save(reserva);
    }

    // ------------------------------------------------------------------
    // Informe
    // ------------------------------------------------------------------

    /** Reservas activas con mas de {@link #DIAS_VIGENCIA} dias. No las libera. */
    public List<ReservaStock> vencidas() {
        return reservaRepository.vencidasAntesDe(LocalDateTime.now().minusDays(DIAS_VIGENCIA));
    }

    /** Las vencidas, ya en DTO y con los dias retenidos calculados. */
    public List<ReservaStockResponseDTO> informeDeVencidas() {
        return vencidas().stream().map(this::aDTO).toList();
    }

    /** Las activas de un pedido, en DTO. Para la ficha del pedido. */
    public List<ReservaStockResponseDTO> activasDe(Integer idPedido) {
        return reservaRepository.activasDePedido(idPedido).stream().map(this::aDTO).toList();
    }

    public ReservaStockResponseDTO aDTO(ReservaStock r) {
        ReservaStockResponseDTO dto = new ReservaStockResponseDTO();
        dto.setIdReserva(r.getIdReserva());
        if (r.getPedido() != null) {
            dto.setIdPedido(r.getPedido().getIdPedido());
            dto.setNumeroPedido(String.format("PED-%06d", r.getPedido().getIdPedido()));
            dto.setEstadoPedido(r.getPedido().getEstado());
        }
        if (r.getProducto() != null) {
            dto.setIdProducto(r.getProducto().getIdProducto());
            dto.setProductoNombre(r.getProducto().getNombre());
        }
        dto.setCantidad(r.getCantidad());
        dto.setEstado(r.getEstado());
        dto.setFechaReserva(r.getFechaReserva());
        if (r.getFechaReserva() != null) {
            LocalDateTime hasta = r.getFechaCierre() != null ? r.getFechaCierre() : LocalDateTime.now();
            dto.setDiasRetenida(ChronoUnit.DAYS.between(r.getFechaReserva(), hasta));
        }
        dto.setFechaCierre(r.getFechaCierre());
        dto.setMotivoCierre(r.getMotivoCierre());
        return dto;
    }

    // ------------------------------------------------------------------
    // Interno
    // ------------------------------------------------------------------

    /**
     * Un pedido puede repetir el mismo producto en varias lineas. Se suman antes
     * de reservar porque el indice uq_reserva_pedido_producto_activa solo admite
     * una reserva activa por (pedido, producto) — y porque comprobar linea a
     * linea contra el mismo disponible dejaria pasar un pedido de 2 lineas de 6
     * unidades sobre un stock de 10.
     */
    private Map<Integer, Integer> agruparPorProducto(List<DetallePedido> lineas) {
        Map<Integer, Integer> porProducto = new LinkedHashMap<>();
        for (DetallePedido linea : lineas) {
            if (linea.getProducto() == null || linea.getCantidad() == null || linea.getCantidad() <= 0) {
                continue;
            }
            Integer idProducto = linea.getProducto().getIdProducto();
            porProducto.merge(idProducto, linea.getCantidad(), (a, b) -> a + b);
        }
        return porProducto;
    }

    /** Indice id -> Producto para poder nombrar el articulo en los mensajes. */
    private Map<Integer, Producto> productosPorId(List<DetallePedido> lineas) {
        Map<Integer, Producto> porId = new LinkedHashMap<>();
        for (DetallePedido linea : lineas) {
            if (linea.getProducto() != null) {
                porId.putIfAbsent(linea.getProducto().getIdProducto(), linea.getProducto());
            }
        }
        return porId;
    }

    private void bloquearProducto(Integer idProducto) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(?1, ?2)")
                .setParameter(1, ESPACIO_BLOQUEO)
                .setParameter(2, idProducto)
                .getSingleResult();
    }
}
