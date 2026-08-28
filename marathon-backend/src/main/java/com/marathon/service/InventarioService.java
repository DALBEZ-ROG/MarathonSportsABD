package com.marathon.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.inventario.HistorialResponseDTO;
import com.marathon.dto.inventario.InventarioResponseDTO;
import com.marathon.dto.inventario.MovimientoRequestDTO;
import com.marathon.dto.inventario.MovimientoResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.Bodega;
import com.marathon.model.HistorialInventario;
import com.marathon.model.Inventario;
import com.marathon.model.MovimientoInventario;
import com.marathon.model.Producto;
import com.marathon.model.Usuario;
import com.marathon.repository.BodegaRepository;
import com.marathon.repository.HistorialInventarioRepository;
import com.marathon.repository.InventarioRepository;
import com.marathon.repository.MovimientoInventarioRepository;
import com.marathon.repository.ProductoRepository;
import com.marathon.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class InventarioService {

    private final InventarioRepository inventarioRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final HistorialInventarioRepository historialRepository;
    private final ProductoRepository productoRepository;
    private final BodegaRepository bodegaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReservaStockService reservaStockService;

    private final LogService logService;
    @PersistenceContext
    private EntityManager entityManager;

    public InventarioService(InventarioRepository inventarioRepository,
                             MovimientoInventarioRepository movimientoRepository,
                             HistorialInventarioRepository historialRepository,
                             ProductoRepository productoRepository,
                             BodegaRepository bodegaRepository,
                             UsuarioRepository usuarioRepository,
                             ReservaStockService reservaStockService,
                         LogService logService) {
        this.inventarioRepository = inventarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.historialRepository = historialRepository;
        this.productoRepository = productoRepository;
        this.bodegaRepository = bodegaRepository;
        this.usuarioRepository = usuarioRepository;
        this.reservaStockService = reservaStockService;
        this.logService = logService;
    }

    /**
     * Impide que un movimiento manual se lleve unidades que un pedido ya
     * procesado tiene retenidas (F47, D-02).
     *
     * <p>Se mide sobre el producto entero y no sobre una bodega porque ese es el
     * grano de la reserva: cuando se reservo todavia no habia bodega elegida.
     *
     * @param salida unidades que el movimiento retira del total (siempre > 0)
     * @param verbo  lo que se estaba intentando, para que el mensaje diga que
     *               operacion se ha frenado y no solo que algo fallo
     */
    private void comprobarQueNoInvadeReservas(Integer idProducto, int salida, String verbo) {
        int reservado = reservaStockService.reservado(idProducto);
        if (reservado <= 0) {
            return;
        }
        int total = reservaStockService.stockTotal(idProducto);
        int libre = total - reservado;
        if (libre < salida) {
            throw new ValidationException("No se puede " + verbo + " " + salida
                    + " unidades: de las " + total + " en existencias hay " + reservado
                    + " reservadas por pedidos ya procesados, asi que solo quedan " + libre
                    + " libres. Libera esas reservas o despacha esos pedidos primero.");
        }
    }

    public PageResponseDTO<InventarioResponseDTO> listar(int page, int size, Integer idBodega,
                                                         String busqueda) {
        // F51 (D-41): ver la nota de BodegaService. Sin ORDER BY, la fila que
        // se acaba de tocar se cae de su pagina.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "idInventario"));
        // F54: buscar por producto. Sin esto, con miles de filas, saber cuanto
        // queda de un articulo era recorrer paginas.
        Page<Inventario> result = inventarioRepository.buscar(
                idBodega, Filtros.vacioComoNulo(busqueda), pageable);

        List<InventarioResponseDTO> content = result.getContent().stream()
                .map(this::toInventarioDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public InventarioResponseDTO obtener(Integer id) {
        Inventario inventario = inventarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventario", id));
        return toInventarioDTO(inventario);
    }

    /**
     * Referencias que hay que reponer, segun el minimo de cada una.
     *
     * <p>Antes era {@code findStockBajo(5)}: un umbral fijo de cinco unidades
     * para todo el catalogo. Eso hacia que la misma pregunta tuviera dos
     * respuestas distintas —116 aqui, 220 en el tablero— porque el tablero si
     * usaba {@code stock_minimo}. Ahora las dos pantallas cuentan lo mismo.
     */
    public List<InventarioResponseDTO> stockBajo() {
        return inventarioRepository.findBajoMinimo().stream()
                .map(this::toInventarioDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public MovimientoResponseDTO registrarMovimiento(MovimientoRequestDTO dto, Integer idUsuarioActual) {
        Producto producto = productoRepository.findById(dto.getIdProducto())
                .orElseThrow(() -> new ResourceNotFoundException("Producto", dto.getIdProducto()));
        Bodega bodega = bodegaRepository.findById(dto.getIdBodega())
                .orElseThrow(() -> new ResourceNotFoundException("Bodega", dto.getIdBodega()));
        Usuario usuario = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        Inventario inv = inventarioRepository.buscarParaActualizar(
                dto.getIdProducto(), dto.getIdBodega())
                .orElseGet(() -> {
                    Inventario nuevo = new Inventario();
                    nuevo.setProducto(producto);
                    nuevo.setBodega(bodega);
                    nuevo.setStockActual(0);
                    nuevo.setStockMinimo(0);
                    return inventarioRepository.save(nuevo);
                });

        // La cantidad que acabara en el movimiento. Coincide con dto.getCantidad()
        // salvo en 'ajuste', donde el DTO trae un valor ABSOLUTO y el kardex
        // necesita la diferencia (L5, D-15).
        int cantidadDelMovimiento = dto.getCantidad();
        // Destino del traslado; se rellena en su rama (L5, D-35).
        Inventario inventarioDestino = null;

        switch (dto.getTipoMovimiento()) {
            case "entrada":
                inv.setStockActual(inv.getStockActual() + dto.getCantidad());
                break;
            case "salida":
                if (inv.getStockActual() < dto.getCantidad()) {
                    throw new ValidationException("Stock insuficiente. Disponible: " + inv.getStockActual());
                }
                // F47 (D-02): una salida manual tampoco puede llevarse lo que un
                // pedido ya procesado tiene reservado. La comprobacion de arriba
                // mira una bodega; esta mira el producto entero, que es el grano
                // de la reserva.
                comprobarQueNoInvadeReservas(dto.getIdProducto(), dto.getCantidad(), "sacar");
                inv.setStockActual(inv.getStockActual() - dto.getCantidad());
                break;
            case "ajuste": {
                // ------------------------------------------------------------
                // L5 (D-15): el ajuste fija un valor absoluto, pero el kardex
                // registra movimientos, no saldos.
                // ------------------------------------------------------------
                // Antes se grababa mov.cantidad = dto.getCantidad(), es decir el
                // saldo nuevo interpretado como si fuera un delta: sumar los
                // movimientos dejaba de reconstruir el stock en cuanto habia un
                // ajuste de por medio, y hay 10.757 ajustes en la base.
                int anterior = inv.getStockActual() != null ? inv.getStockActual() : 0;
                int diferencia = dto.getCantidad() - anterior;
                if (diferencia == 0) {
                    throw new ValidationException(
                            "El ajuste no cambia el stock (ya es " + anterior + ").");
                }
                // F47 (D-02): un ajuste A LA BAJA es una salida disfrazada de
                // saldo. Si deja el total por debajo de lo reservado, hay
                // pedidos procesados cuya mercancia deja de existir.
                if (diferencia < 0) {
                    comprobarQueNoInvadeReservas(dto.getIdProducto(), -diferencia, "ajustar a la baja");
                }
                inv.setStockActual(dto.getCantidad());
                cantidadDelMovimiento = Math.abs(diferencia);
                break;
            }
            case "traslado":
                if (dto.getIdBodegaDestino() == null) {
                    throw new ValidationException("La bodega destino es requerida para traslados");
                }
                // Un traslado de una bodega a si misma no mueve nada, pero si
                // deja rastro: origen y destino son LA MISMA fila de inventario,
                // asi que el -cantidad y el +cantidad se anulan y en el kardex
                // queda un movimiento de traslado que no traslado nada. No habia
                // ninguno en la base (se comprobo), asi que cerrarlo no rompe
                // historico.
                if (dto.getIdBodegaDestino().equals(dto.getIdBodega())) {
                    throw new ValidationException(
                            "El traslado tiene la misma bodega de origen y destino: no movería nada.");
                }
                if (inv.getStockActual() < dto.getCantidad()) {
                    throw new ValidationException("Stock insuficiente. Disponible: " + inv.getStockActual());
                }
                inv.setStockActual(inv.getStockActual() - dto.getCantidad());

                Bodega bodegaDestino = bodegaRepository.findById(dto.getIdBodegaDestino())
                        .orElseThrow(() -> new ResourceNotFoundException("Bodega destino", dto.getIdBodegaDestino()));

                Inventario destino = inventarioRepository.buscarParaActualizar(
                        dto.getIdProducto(), dto.getIdBodegaDestino())
                        .orElseGet(() -> {
                            Inventario nuevoDestino = new Inventario();
                            nuevoDestino.setProducto(producto);
                            nuevoDestino.setBodega(bodegaDestino);
                            nuevoDestino.setStockActual(0);
                            nuevoDestino.setStockMinimo(0);
                            return inventarioRepository.save(nuevoDestino);
                        });

                entityManager.createNativeQuery("SET LOCAL app.current_user_id = '" + idUsuarioActual + "'")
                        .executeUpdate();
                destino.setStockActual(destino.getStockActual() + dto.getCantidad());
                inventarioRepository.save(destino);
                inventarioDestino = destino;
                break;
            default:
                throw new ValidationException("Tipo de movimiento no válido: " + dto.getTipoMovimiento());
        }

        entityManager.createNativeQuery("SET LOCAL app.current_user_id = '" + idUsuarioActual + "'")
                .executeUpdate();
        inventarioRepository.save(inv);

        MovimientoInventario mov = new MovimientoInventario();
        mov.setInventario(inv);
        mov.setTipoMovimiento(dto.getTipoMovimiento());
        // En 'ajuste' esto es la diferencia, no el saldo nuevo (D-15).
        mov.setCantidad(cantidadDelMovimiento);
        mov.setUsuario(usuario);
        // --------------------------------------------------------------------
        // L5 (D-35): el traslado nunca asignaba el destino.
        // --------------------------------------------------------------------
        // setInventarioDestino no se invocaba en ninguna parte del proyecto, y
        // la tabla tiene chk_traslado_requiere_destino, asi que el INSERT
        // reventaba y el traslado entre bodegas NUNCA habia funcionado desde la
        // aplicacion. Las 6.134 filas de traslado que hay en la base las
        // insertaron los scripts de poblado, no este codigo.
        mov.setInventarioDestino(inventarioDestino);
        if ("ajuste".equals(dto.getTipoMovimiento())) {
            int nuevo = inv.getStockActual() != null ? inv.getStockActual() : 0;
            mov.setObservacion("Ajuste a " + nuevo + " unidades ("
                    + (nuevo >= cantidadDelMovimiento ? "+" : "-") + cantidadDelMovimiento
                    + "). Saldos exactos en historial_inventario.");
        }
        movimientoRepository.save(mov);

        // F40: la entrada COMPLEMENTA historial_inventario, no lo duplica.
        // El detalle (stock anterior, stock nuevo, motivo) ya lo escribe el
        // trigger trg_historial_inventario en cada UPDATE de inventario; aqui
        // se registra que hubo un ajuste y se remite a ese historial, para que
        // la bitacora central no tenga un agujero en el modulo de inventario.
        logService.registrar(idUsuarioActual, "inventario", dto.getTipoMovimiento(),
                "Movimiento de " + dto.getCantidad() + " u. sobre '" + producto.getNombre()
                + "' en bodega '" + bodega.getNombre() + "'. Detalle en historial_inventario "
                + "(inventario #" + inv.getIdInventario() + ")", null);

        return toMovimientoDTO(mov);
    }

    public PageResponseDTO<MovimientoResponseDTO> listarMovimientos(Integer idProducto, Integer idBodega, int page, int size) {
        // F51 (D-41): un kardex se lee del movimiento mas reciente hacia atras,
        // y sin ORDER BY salia en el orden fisico del monton.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "idMovimiento"));
        Page<MovimientoInventario> result = movimientoRepository
                .findByInventarioProductoIdProductoAndInventarioBodegaIdBodega(idProducto, idBodega, pageable);

        List<MovimientoResponseDTO> content = result.getContent().stream()
                .map(this::toMovimientoDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public List<HistorialResponseDTO> listarHistorial(Integer idInventario) {
        return historialRepository.findByInventarioIdInventarioOrderByFechaDesc(idInventario).stream()
                .map(this::toHistorialDTO)
                .collect(Collectors.toList());
    }

    private InventarioResponseDTO toInventarioDTO(Inventario inv) {
        InventarioResponseDTO dto = new InventarioResponseDTO();
        dto.setIdInventario(inv.getIdInventario());
        dto.setCantidad(inv.getStockActual());
        dto.setUpdatedAt(inv.getFechaActualizacion());
        if (inv.getProducto() != null) {
            dto.setProductoId(inv.getProducto().getIdProducto());
            dto.setProductoNombre(inv.getProducto().getNombre());
        }
        if (inv.getBodega() != null) {
            dto.setBodegaId(inv.getBodega().getIdBodega());
            dto.setBodegaNombre(inv.getBodega().getNombre());
        }
        return dto;
    }

    private MovimientoResponseDTO toMovimientoDTO(MovimientoInventario mov) {
        MovimientoResponseDTO dto = new MovimientoResponseDTO();
        dto.setIdMovimiento(mov.getIdMovimiento());
        dto.setTipoMovimiento(mov.getTipoMovimiento());
        dto.setCantidad(mov.getCantidad());
        dto.setFecha(mov.getFecha());
        if (mov.getInventario() != null && mov.getInventario().getProducto() != null) {
            dto.setIdProducto(mov.getInventario().getProducto().getIdProducto());
            dto.setProductoNombre(mov.getInventario().getProducto().getNombre());
        }
        if (mov.getInventario() != null && mov.getInventario().getBodega() != null) {
            dto.setIdBodega(mov.getInventario().getBodega().getIdBodega());
            dto.setBodegaNombre(mov.getInventario().getBodega().getNombre());
        }
        if (mov.getUsuario() != null) {
            dto.setIdUsuario(mov.getUsuario().getIdUsuario());
            dto.setUsuarioNombre(mov.getUsuario().getNombre() + " " + mov.getUsuario().getApellido());
        }
        return dto;
    }

    private HistorialResponseDTO toHistorialDTO(HistorialInventario h) {
        HistorialResponseDTO dto = new HistorialResponseDTO();
        dto.setIdHistorial(h.getIdHistorial());
        dto.setCantidadAnterior(h.getStockAnterior());
        dto.setCantidadNueva(h.getStockNuevo());
        dto.setFechaCambio(h.getFecha());
        dto.setTipoOperacion(h.getMotivo());
        return dto;
    }
}
