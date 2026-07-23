package com.marathon.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.recepcion.RecepcionDetalleItemDTO;
import com.marathon.dto.recepcion.RecepcionMercanciaRequestDTO;
import com.marathon.dto.recepcion.RecepcionMercanciaResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.Bodega;
import com.marathon.model.Inventario;
import com.marathon.model.MateriaPrima;
import com.marathon.model.MovimientoInventario;
import com.marathon.model.OrdenCompra;
import com.marathon.model.OrdenCompraDetalle;
import com.marathon.model.Producto;
import com.marathon.model.RecepcionMercancia;
import com.marathon.model.RecepcionMercanciaDetalle;
import com.marathon.model.Usuario;
import com.marathon.repository.BodegaRepository;
import com.marathon.repository.InventarioRepository;
import com.marathon.repository.MateriaPrimaRepository;
import com.marathon.repository.MovimientoInventarioRepository;
import com.marathon.repository.OrdenCompraDetalleRepository;
import com.marathon.repository.OrdenCompraRepository;
import com.marathon.repository.RecepcionMercanciaDetalleRepository;
import com.marathon.repository.RecepcionMercanciaRepository;
import com.marathon.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class RecepcionMercanciaService {

    private final RecepcionMercanciaRepository recepcionRepository;
    private final RecepcionMercanciaDetalleRepository recepcionDetalleRepository;
    private final OrdenCompraRepository ordenCompraRepository;
    private final OrdenCompraDetalleRepository ordenCompraDetalleRepository;
    private final InventarioRepository inventarioRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final BodegaRepository bodegaRepository;
    private final UsuarioRepository usuarioRepository;
    private final LogService logService;
    private final com.marathon.repository.MovimientoMateriaPrimaRepository movimientoMateriaPrimaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public RecepcionMercanciaService(RecepcionMercanciaRepository recepcionRepository,
                                     RecepcionMercanciaDetalleRepository recepcionDetalleRepository,
                                     OrdenCompraRepository ordenCompraRepository,
                                     OrdenCompraDetalleRepository ordenCompraDetalleRepository,
                                     InventarioRepository inventarioRepository,
                                     MovimientoInventarioRepository movimientoRepository,
                                     MateriaPrimaRepository materiaPrimaRepository,
                                     BodegaRepository bodegaRepository,
                                     UsuarioRepository usuarioRepository,
                                     LogService logService,
                                     com.marathon.repository.MovimientoMateriaPrimaRepository movimientoMateriaPrimaRepository) {
        this.recepcionRepository = recepcionRepository;
        this.recepcionDetalleRepository = recepcionDetalleRepository;
        this.ordenCompraRepository = ordenCompraRepository;
        this.ordenCompraDetalleRepository = ordenCompraDetalleRepository;
        this.inventarioRepository = inventarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.materiaPrimaRepository = materiaPrimaRepository;
        this.bodegaRepository = bodegaRepository;
        this.usuarioRepository = usuarioRepository;
        this.logService = logService;
        this.movimientoMateriaPrimaRepository = movimientoMateriaPrimaRepository;
    }

    @Transactional
    public RecepcionMercanciaResponseDTO crear(RecepcionMercanciaRequestDTO dto, Integer idUsuarioActual) {
        // 1. Orden de compra debe existir y estar en estado recibible
        OrdenCompra orden = ordenCompraRepository.findById(dto.getIdOrdenCompra())
                .orElseThrow(() -> new ResourceNotFoundException("Orden de compra", dto.getIdOrdenCompra()));
        if (!("aprobada".equals(orden.getEstado()) || "recibida_parcial".equals(orden.getEstado()))) {
            throw new ValidationException("Solo se puede recibir mercancía de órdenes aprobadas");
        }

        Usuario receptor = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));
        Bodega bodega = bodegaRepository.findById(dto.getIdBodega())
                .orElseThrow(() -> new ResourceNotFoundException("Bodega", dto.getIdBodega()));

        // 2. Validar cada línea antes de persistir nada
        for (RecepcionDetalleItemDTO item : dto.getDetalles()) {
            OrdenCompraDetalle detOc = ordenCompraDetalleRepository.findById(item.getIdDetalleOc())
                    .orElseThrow(() -> new ResourceNotFoundException("Detalle de orden de compra", item.getIdDetalleOc()));

            if (!detOc.getOrdenCompra().getIdOrdenCompra().equals(orden.getIdOrdenCompra())) {
                throw new ValidationException(
                        "La línea " + item.getIdDetalleOc() + " no pertenece a la orden de compra #" + orden.getIdOrdenCompra());
            }

            int pendiente = detOc.getCantidad() - detOc.getCantidadRecibida();
            if (item.getCantidadRecibidaAhora() > pendiente) {
                throw new ValidationException(
                        "La cantidad recibida supera lo pendiente de la línea (pendiente: " + pendiente + ")");
            }

            int defectuosa = item.getCantidadDefectuosa() != null ? item.getCantidadDefectuosa() : 0;
            if (defectuosa > item.getCantidadRecibidaAhora()) {
                throw new ValidationException(
                        "La cantidad defectuosa no puede superar la cantidad recibida");
            }
        }

        // 3. Insertar encabezado
        RecepcionMercancia recepcion = new RecepcionMercancia();
        recepcion.setOrdenCompra(orden);
        recepcion.setUsuarioReceptor(receptor);
        recepcion.setBodega(bodega);
        recepcion.setNumeroGuiaRemision(dto.getNumeroGuiaRemision());
        recepcion.setObservaciones(dto.getObservaciones());
        recepcion = recepcionRepository.save(recepcion);

        // Fija el usuario para el trigger de historial de inventario (una vez por transacción)
        entityManager.createNativeQuery("SET LOCAL app.current_user_id = '" + idUsuarioActual + "'")
                .executeUpdate();

        // 4. Procesar cada línea
        for (RecepcionDetalleItemDTO item : dto.getDetalles()) {
            OrdenCompraDetalle detOc = ordenCompraDetalleRepository.findById(item.getIdDetalleOc()).orElseThrow();

            int defectuosa = item.getCantidadDefectuosa() != null ? item.getCantidadDefectuosa() : 0;
            int cantidadBuena = item.getCantidadRecibidaAhora() - defectuosa;

            // a. línea de recepción
            RecepcionMercanciaDetalle detRm = new RecepcionMercanciaDetalle();
            detRm.setRecepcion(recepcion);
            detRm.setDetalleOc(detOc);
            detRm.setCantidadRecibidaAhora(item.getCantidadRecibidaAhora());
            detRm.setCantidadDefectuosa(defectuosa);
            detRm.setObservacion(item.getObservacion());
            recepcionDetalleRepository.save(detRm);

            // b. acumular cantidad_recibida en la línea de la OC (nunca sobreescribe)
            detOc.setCantidadRecibida(detOc.getCantidadRecibida() + item.getCantidadRecibidaAhora());
            ordenCompraDetalleRepository.save(detOc);

            // d/e. afectar stock solo con la cantidad buena
            if (cantidadBuena > 0) {
                if ("producto".equals(detOc.getTipoItem()) && detOc.getProducto() != null) {
                    aplicarEntradaProducto(detOc.getProducto(), bodega, cantidadBuena, receptor,
                            orden, dto.getNumeroGuiaRemision(), idUsuarioActual);
                } else if ("materia_prima".equals(detOc.getTipoItem()) && detOc.getMateriaPrima() != null) {
                    MateriaPrima mp = detOc.getMateriaPrima();
                    BigDecimal stockAnterior = mp.getStockActual();
                    mp.setStockActual(mp.getStockActual().add(BigDecimal.valueOf(cantidadBuena)));
                    materiaPrimaRepository.save(mp);
                    // F26 — Kardex: registrar movimiento de entrada por compra
                    registrarMovimientoMateriaPrima(mp, receptor, "entrada_compra",
                            BigDecimal.valueOf(cantidadBuena), stockAnterior, mp.getStockActual(),
                            recepcion.getIdRecepcion(),
                            "Recepcion OC #" + orden.getIdOrdenCompra());
                }
            }
            // f. cantidad_defectuosa queda registrada en detRm para la Fase 25 (devolución)
        }

        // 5. Recalcular estado de la orden (automático, sin validación de roles de F21)
        entityManager.flush();
        List<OrdenCompraDetalle> todas = ordenCompraDetalleRepository.findByOrdenCompraIdOrdenCompra(orden.getIdOrdenCompra());
        boolean todasCompletas = todas.stream().allMatch(d -> d.getCantidadRecibida() >= d.getCantidad());
        boolean algunaRecibida = todas.stream().anyMatch(d -> d.getCantidadRecibida() > 0);

        String nuevoEstado = orden.getEstado();
        if (todasCompletas) {
            nuevoEstado = "recibida_completa";
        } else if (algunaRecibida) {
            nuevoEstado = "recibida_parcial";
        }
        if (!nuevoEstado.equals(orden.getEstado())) {
            orden.setEstado(nuevoEstado);
            ordenCompraRepository.save(orden);
        }

        logService.registrar(idUsuarioActual, "compras", "recepcion",
                "Recepción #" + recepcion.getIdRecepcion() + " registrada para OC #" + orden.getIdOrdenCompra()
                        + ". Estado orden: " + nuevoEstado, null);

        return toDTO(recepcion);
    }

    /**
     * Suma stock de producto en una bodega. Reutiliza/crea el registro de
     * inventario y registra el movimiento de entrada. Fija app.current_user_id
     * para que el trigger de historial de inventario registre al usuario.
     */
    private void aplicarEntradaProducto(Producto producto, Bodega bodega, int cantidadBuena,
                                        Usuario receptor, OrdenCompra orden, String numeroGuia,
                                        Integer idUsuarioActual) {
        Inventario inv = inventarioRepository
                .findByProductoIdProductoAndBodegaIdBodega(producto.getIdProducto(), bodega.getIdBodega())
                .orElseGet(() -> {
                    Inventario nuevo = new Inventario();
                    nuevo.setProducto(producto);
                    nuevo.setBodega(bodega);
                    nuevo.setStockActual(0);
                    nuevo.setStockMinimo(0);
                    return inventarioRepository.save(nuevo);
                });

        entityManager.createNativeQuery("SET LOCAL app.current_user_id = '" + idUsuarioActual + "'")
                .executeUpdate();
        inv.setStockActual(inv.getStockActual() + cantidadBuena);
        inventarioRepository.save(inv);

        MovimientoInventario mov = new MovimientoInventario();
        mov.setInventario(inv);
        mov.setTipoMovimiento("entrada");
        mov.setCantidad(cantidadBuena);
        mov.setUsuario(receptor);
        mov.setProveedor(orden.getProveedor());
        mov.setObservacion("Recepción OC #" + orden.getIdOrdenCompra()
                + (numeroGuia != null && !numeroGuia.isBlank() ? " - Guía: " + numeroGuia : ""));
        movimientoRepository.save(mov);
    }

    public List<RecepcionMercanciaResponseDTO> listarPorOrden(Integer idOrdenCompra) {
        return recepcionRepository.findByOrdenCompraIdOrdenCompraOrderByFechaRecepcionDesc(idOrdenCompra).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private RecepcionMercanciaResponseDTO toDTO(RecepcionMercancia r) {
        RecepcionMercanciaResponseDTO dto = new RecepcionMercanciaResponseDTO();
        dto.setIdRecepcion(r.getIdRecepcion());
        dto.setFechaRecepcion(r.getFechaRecepcion());
        dto.setNumeroGuiaRemision(r.getNumeroGuiaRemision());
        dto.setObservaciones(r.getObservaciones());
        if (r.getOrdenCompra() != null) {
            dto.setIdOrdenCompra(r.getOrdenCompra().getIdOrdenCompra());
            dto.setEstadoOrden(r.getOrdenCompra().getEstado());
        }
        if (r.getBodega() != null) {
            dto.setIdBodega(r.getBodega().getIdBodega());
            dto.setBodegaNombre(r.getBodega().getNombre());
        }
        if (r.getUsuarioReceptor() != null) {
            dto.setIdUsuarioReceptor(r.getUsuarioReceptor().getIdUsuario());
            dto.setReceptorNombre(r.getUsuarioReceptor().getNombre() + " " + r.getUsuarioReceptor().getApellido());
        }

        List<RecepcionMercanciaDetalle> detalles = recepcionDetalleRepository.findByRecepcionIdRecepcion(r.getIdRecepcion());
        List<RecepcionMercanciaResponseDTO.DetalleDTO> items = new ArrayList<>();
        for (RecepcionMercanciaDetalle d : detalles) {
            RecepcionMercanciaResponseDTO.DetalleDTO i = new RecepcionMercanciaResponseDTO.DetalleDTO();
            i.setIdDetalleRm(d.getIdDetalleRm());
            i.setCantidadRecibidaAhora(d.getCantidadRecibidaAhora());
            i.setCantidadDefectuosa(d.getCantidadDefectuosa());
            i.setObservacion(d.getObservacion());
            OrdenCompraDetalle detOc = d.getDetalleOc();
            if (detOc != null) {
                i.setIdDetalleOc(detOc.getIdDetalleOc());
                i.setTipoItem(detOc.getTipoItem());
                if ("producto".equals(detOc.getTipoItem()) && detOc.getProducto() != null) {
                    i.setItemNombre(detOc.getProducto().getNombre());
                } else if ("materia_prima".equals(detOc.getTipoItem()) && detOc.getMateriaPrima() != null) {
                    i.setItemNombre(detOc.getMateriaPrima().getNombre());
                }
            }
            items.add(i);
        }
        dto.setDetalles(items);
        return dto;
    }

    /**
     * F26 — Registra movimiento en el kardex de materia prima.
     */
    private void registrarMovimientoMateriaPrima(MateriaPrima mp, Usuario usuario, String tipo,
                                                  BigDecimal cantidad, BigDecimal stockAnterior,
                                                  BigDecimal stockNuevo, Integer idRecepcion,
                                                  String observacion) {
        com.marathon.model.MovimientoMateriaPrima mov = new com.marathon.model.MovimientoMateriaPrima();
        mov.setMateriaPrima(mp);
        mov.setUsuario(usuario);
        mov.setTipoMovimiento(tipo);
        mov.setCantidad(cantidad);
        mov.setStockAnterior(stockAnterior);
        mov.setStockNuevo(stockNuevo);
        mov.setIdRecepcion(idRecepcion);
        mov.setObservacion(observacion);
        movimientoMateriaPrimaRepository.save(mov);
    }
}
