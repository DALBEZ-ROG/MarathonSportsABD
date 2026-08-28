package com.marathon.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.empaque.EmpaqueRequestDTO;
import com.marathon.dto.pedido.PedidoResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.DetallePedido;
import com.marathon.model.Inventario;
import com.marathon.model.MovimientoInventario;
import com.marathon.model.Pedido;
import com.marathon.model.Producto;
import com.marathon.model.Usuario;
import com.marathon.repository.DetallePedidoRepository;
import com.marathon.repository.InventarioRepository;
import com.marathon.repository.MovimientoInventarioRepository;
import com.marathon.repository.PedidoRepository;
import com.marathon.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class EmpaqueService {

    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final InventarioRepository inventarioRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PickingService pickingService;
    private final PedidoService pedidoService;
    private final ReservaStockService reservaStockService;
    private final LogService logService;

    @PersistenceContext
    private EntityManager entityManager;

    public EmpaqueService(PedidoRepository pedidoRepository,
                          DetallePedidoRepository detallePedidoRepository,
                          InventarioRepository inventarioRepository,
                          MovimientoInventarioRepository movimientoRepository,
                          UsuarioRepository usuarioRepository,
                          PickingService pickingService,
                          PedidoService pedidoService,
                          ReservaStockService reservaStockService,
                          LogService logService) {
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.inventarioRepository = inventarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.usuarioRepository = usuarioRepository;
        this.pickingService = pickingService;
        this.pedidoService = pedidoService;
        this.reservaStockService = reservaStockService;
        this.logService = logService;
    }

    @Transactional
    public PedidoResponseDTO confirmarEmpaque(Integer idPedido, EmpaqueRequestDTO dto, Integer idUsuarioActual) {
        Pedido pedido = pedidoRepository.findById(idPedido)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", idPedido));

        String estadoPicking = pickingService.verificarEstadoPicking(idPedido);
        if (!"completo".equals(estadoPicking)) {
            long total = detallePedidoRepository.countByPedidoIdPedido(idPedido);
            long completadas = detallePedidoRepository.countByPedidoIdPedidoAndPickingCompletadoTrue(idPedido);
            throw new ValidationException("No se puede empacar: el picking no está completo. Líneas pendientes: "
                    + completadas + " de " + total);
        }

        if (!"procesado".equals(pedido.getEstado())) {
            throw new ValidationException("El pedido debe estar en estado procesado para empacar");
        }

        Usuario usuario = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        // Se fija una sola vez para toda la transaccion, antes de la primera
        // escritura: es lo que permite al trigger trg_historial_inventario
        // registrar quien movio el stock. SET LOCAL muere en el commit.
        entityManager.createNativeQuery("SET LOCAL app.current_user_id = '" + idUsuarioActual + "'")
                .executeUpdate();

        // El stock se descuenta ANTES de marcar el pedido como enviado. Si una
        // linea no se puede cubrir, descontarLinea levanta ValidationException,
        // la transaccion revierte entera y el pedido se queda en 'procesado'.
        // Nunca se marca como enviado algo que no salio del almacen.
        List<DetallePedido> detalles = detallePedidoRepository.findByPedidoIdPedidoOrderByIdDetalleAsc(idPedido);
        for (DetallePedido detalle : detalles) {
            descontarLinea(detalle, idPedido, dto, usuario);
        }

        // F47 (D-02): la mercancia ya salio, la reserva deja de retener. Va
        // DESPUES de descontar: si alguna linea aborta, la transaccion revierte
        // y la reserva se queda activa, que es lo correcto — el pedido sigue en
        // 'procesado' esperando.
        int consumidas = reservaStockService.consumirDe(idPedido);

        pedido.setNumeroHu(dto.getNumeroHu());
        pedido.setTransportista(dto.getTransportista());
        pedido.setRegionDestino(dto.getRegionDestino());
        pedido.setFechaEmpaque(LocalDateTime.now());
        pedido.setEstado("enviado");
        pedidoRepository.save(pedido);

        logService.registrar(idUsuarioActual, "empaque", "confirmar",
                "Pedido #" + idPedido + " empacado. HU: " + dto.getNumeroHu()
                        + ". Transportista: " + dto.getTransportista()
                        + ". Reservas consumidas: " + consumidas, null);

        return pedidoService.obtener(idPedido);
    }

    /**
     * Descuenta del inventario una linea del pedido, repartiendo entre bodegas
     * si ninguna sola la cubre.
     *
     * <p>Sustituye al bucle que habia aqui hasta la L1, que tenia tres fallos
     * encadenados y ninguno avisaba (D-01):
     *
     * <ul>
     *   <li>elegia la bodega con {@code findFirst()} sobre una lista sin
     *       ordenar, o sea cualquiera;</li>
     *   <li>si ninguna bodega tenia existencias hacia {@code continue}, y el
     *       pedido salia igualmente marcado como enviado, sin ningun movimiento
     *       de inventario;</li>
     *   <li>si el stock no llegaba, recortaba el saldo a cero y grababa el
     *       movimiento con la cantidad de la linea entera, de modo que el libro
     *       de movimientos y el saldo quedaban descuadrados para siempre.</li>
     * </ul>
     *
     * <p>Ahora: o se descuenta exactamente lo que sale, o no se descuenta nada y
     * la transaccion revierte.
     *
     * <p><b>De que bodega sale.</b> Se recorren en orden estable de bodega y se
     * vacia cada una antes de pasar a la siguiente. Es determinista, pero
     * todavia no es <i>la bodega correcta</i>: el picking no registra de donde
     * se recogio la mercancia (D-14). Eso lo arregla la L4; hasta entonces este
     * reparto es lo mas defendible que se puede hacer sin ese dato.
     */
    private void descontarLinea(DetallePedido detalle, Integer idPedido,
                                EmpaqueRequestDTO dto, Usuario usuario) {

        // La columna es NOT NULL en la base, asi que esto no deberia ocurrir.
        // Se comprueba igual, y falla en vez de saltarse la linea en silencio.
        if (detalle.getProducto() == null) {
            throw new ValidationException("La linea #" + detalle.getIdDetalle() + " del pedido #"
                    + idPedido + " no tiene producto asociado; no se puede despachar.");
        }

        Producto producto = detalle.getProducto();
        int porDescontar = detalle.getCantidad() != null ? detalle.getCantidad() : 0;
        if (porDescontar <= 0) {
            return;
        }

        // ------------------------------------------------------------------
        // F47 (D-02): no comerse la mercancia que otro pedido tiene retenida.
        // ------------------------------------------------------------------
        // La comprobacion de mas abajo mira el stock fisico y basta para no
        // dejar el inventario en negativo (L1). Pero el stock fisico incluye lo
        // que otros pedidos ya procesados tienen reservado: sin esta linea, un
        // despacho puede vaciar la estanteria y dejar sin respaldo la reserva de
        // un pedido que se procesó antes. Es exactamente el choque que la
        // reserva viene a evitar, solo que en el otro sentido.
        //
        // Se mide por PRODUCTO y no por bodega porque la reserva es por producto
        // (cuando se reservo aun no habia bodega elegida; la elige el picking).
        //
        // El propio pedido queda fuera de la suma: sus unidades ya estan en la
        // reserva que este mismo despacho esta a punto de consumir.
        int reservadoAjeno = reservaStockService.reservadoPorOtrosPedidos(
                producto.getIdProducto(), idPedido);
        if (reservadoAjeno > 0) {
            int stockTotal = reservaStockService.stockTotal(producto.getIdProducto());
            if (stockTotal - reservadoAjeno < porDescontar) {
                throw new ValidationException("No se puede despachar '" + producto.getNombre()
                        + "': hay " + stockTotal + " unidades en existencias, pero " + reservadoAjeno
                        + " estan reservadas por otros pedidos ya procesados, asi que solo quedan "
                        + (stockTotal - reservadoAjeno) + " libres y se piden " + porDescontar
                        + ". No se ha registrado nada del despacho.");
            }
        }

        // ------------------------------------------------------------------
        // L4 (D-01 parte 1): de donde se descuenta.
        // ------------------------------------------------------------------
        // Si el picking registro la bodega (lineas desde la F45), se descuenta
        // de ESA y de ninguna otra: es de donde salio la mercancia de verdad.
        // Las lineas anteriores no tienen el dato, y para esas se conserva el
        // reparto por orden estable de la L1 — que no es correcto, pero es
        // determinista y no pierde unidades.
        List<Inventario> inventarios;
        if (detalle.getBodegaPicking() != null) {
            Integer idBodega = detalle.getBodegaPicking().getIdBodega();
            inventarios = inventarioRepository
                    .buscarParaActualizar(producto.getIdProducto(), idBodega)
                    .map(List::of)
                    .orElseGet(List::of);

            if (inventarios.isEmpty()) {
                throw new ValidationException("No existe inventario de '" + producto.getNombre()
                        + "' en la bodega desde la que se hizo el picking.");
            }
        } else {
            inventarios = inventarioRepository.buscarPorProductoParaActualizar(producto.getIdProducto());
        }

        int disponible = inventarios.stream()
                .mapToInt(i -> i.getStockActual() != null ? i.getStockActual() : 0)
                .sum();

        if (disponible < porDescontar) {
            throw new ValidationException("Stock insuficiente para despachar '" + producto.getNombre()
                    + "': se piden " + porDescontar + " y hay " + disponible
                    + " entre todas las bodegas. No se ha registrado nada del despacho.");
        }

        for (Inventario inv : inventarios) {
            if (porDescontar == 0) {
                break;
            }
            int enEstaBodega = inv.getStockActual() != null ? inv.getStockActual() : 0;
            if (enEstaBodega <= 0) {
                continue;
            }

            int aDescontar = Math.min(enEstaBodega, porDescontar);
            inv.setStockActual(enEstaBodega - aDescontar);
            inventarioRepository.save(inv);

            MovimientoInventario mov = new MovimientoInventario();
            mov.setInventario(inv);
            mov.setTipoMovimiento("salida");
            // La cantidad realmente descontada de ESTA bodega, no la de la linea
            // entera. Es lo que hace que el kardex vuelva a cuadrar con el saldo.
            mov.setCantidad(aDescontar);
            mov.setUsuario(usuario);
            mov.setIdPedido(idPedido);
            mov.setObservacion("Despacho pedido #" + idPedido + " - HU: " + dto.getNumeroHu());
            movimientoRepository.save(mov);

            porDescontar -= aDescontar;
        }
    }

    public PageResponseDTO<PedidoResponseDTO> listarDespachados(int page, int size, String regionDestino,
                                                                LocalDateTime desde, LocalDateTime hasta) {
        // F51 (D-41): sin Sort aqui A PROPOSITO. La consulta lleva su propio
        // ORDER BY en el JPQL, con desempate por id para que la paginacion sea
        // estable. Anadir un Sort aqui lo pisaria.
        Pageable pageable = PageRequest.of(page, size);
        String region = (regionDestino != null && !regionDestino.isEmpty()) ? regionDestino : "";
        LocalDateTime desdeFiltro = (desde != null) ? desde : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime hastaFiltro = (hasta != null) ? hasta : LocalDateTime.of(2999, 12, 31, 23, 59);
        Page<Pedido> result = pedidoRepository.findDespachados(region, desdeFiltro, hastaFiltro, pageable);

        // ------------------------------------------------------------------
        // L16 (D-28): antes esto era un N+1.
        // ------------------------------------------------------------------
        // Se hacia .map(p -> pedidoService.obtener(p.getIdPedido())), y obtener()
        // lanza dos consultas por pedido (la cabecera y sus lineas). Una pagina
        // de 20 despachos eran 41 consultas, sobre una tabla de 230.000 pedidos.
        //
        // Ahora los pedidos ya vienen de la consulta paginada, y los detalles de
        // toda la pagina se traen de una sola vez y se reparten en memoria: 2
        // consultas en total, sea cual sea el tamano de la pagina.
        List<Pedido> pedidos = result.getContent();
        List<Integer> ids = pedidos.stream().map(Pedido::getIdPedido).collect(Collectors.toList());

        Map<Integer, List<DetallePedido>> detallesPorPedido = ids.isEmpty()
                ? Map.of()
                : detallePedidoRepository.findByPedidoIdPedidoIn(ids).stream()
                        .collect(Collectors.groupingBy(d -> d.getPedido().getIdPedido()));

        List<PedidoResponseDTO> content = pedidos.stream()
                .map(p -> pedidoService.aDTOConDetalles(
                        p, detallesPorPedido.getOrDefault(p.getIdPedido(), List.of())))
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }
}
