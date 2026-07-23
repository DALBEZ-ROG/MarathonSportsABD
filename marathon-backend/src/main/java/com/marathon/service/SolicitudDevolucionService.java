package com.marathon.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.devolucion.InspeccionDetalleDTO;
import com.marathon.dto.devolucion.InspeccionRequestDTO;
import com.marathon.dto.devolucion.ReembolsoRequestDTO;
import com.marathon.dto.devolucion.SolicitudDevolucionDetalleItemDTO;
import com.marathon.dto.devolucion.SolicitudDevolucionRequestDTO;
import com.marathon.dto.devolucion.SolicitudDevolucionResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.Bodega;
import com.marathon.model.DetallePedido;
import com.marathon.model.Inventario;
import com.marathon.model.MovimientoInventario;
import com.marathon.model.Pedido;
import com.marathon.model.ReembolsoCliente;
import com.marathon.model.SolicitudDevolucion;
import com.marathon.model.SolicitudDevolucionDetalle;
import com.marathon.model.Usuario;
import com.marathon.repository.BodegaRepository;
import com.marathon.repository.DetallePedidoRepository;
import com.marathon.repository.InventarioRepository;
import com.marathon.repository.MovimientoInventarioRepository;
import com.marathon.repository.PedidoRepository;
import com.marathon.repository.ReembolsoClienteRepository;
import com.marathon.repository.SolicitudDevolucionDetalleRepository;
import com.marathon.repository.SolicitudDevolucionRepository;
import com.marathon.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class SolicitudDevolucionService {

    private final SolicitudDevolucionRepository solicitudRepository;
    private final SolicitudDevolucionDetalleRepository detalleRepository;
    private final ReembolsoClienteRepository reembolsoRepository;
    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final InventarioRepository inventarioRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final BodegaRepository bodegaRepository;
    private final UsuarioRepository usuarioRepository;
    private final LogService logService;

    @PersistenceContext
    private EntityManager entityManager;

    public SolicitudDevolucionService(SolicitudDevolucionRepository solicitudRepository,
                                      SolicitudDevolucionDetalleRepository detalleRepository,
                                      ReembolsoClienteRepository reembolsoRepository,
                                      PedidoRepository pedidoRepository,
                                      DetallePedidoRepository detallePedidoRepository,
                                      InventarioRepository inventarioRepository,
                                      MovimientoInventarioRepository movimientoRepository,
                                      BodegaRepository bodegaRepository,
                                      UsuarioRepository usuarioRepository,
                                      LogService logService) {
        this.solicitudRepository = solicitudRepository;
        this.detalleRepository = detalleRepository;
        this.reembolsoRepository = reembolsoRepository;
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.inventarioRepository = inventarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.bodegaRepository = bodegaRepository;
        this.usuarioRepository = usuarioRepository;
        this.logService = logService;
    }

    @Transactional
    public SolicitudDevolucionResponseDTO crear(SolicitudDevolucionRequestDTO dto, Integer idUsuarioActual) {
        Pedido pedido = pedidoRepository.findById(dto.getIdPedido())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", dto.getIdPedido()));

        if (!"entregado".equals(pedido.getEstado())) {
            throw new ValidationException("Solo se puede solicitar devolucion de pedidos entregados");
        }

        Usuario usuario = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        // Validar cada linea
        for (SolicitudDevolucionDetalleItemDTO item : dto.getDetalles()) {
            DetallePedido dp = detallePedidoRepository.findById(item.getIdDetallePedido())
                    .orElseThrow(() -> new ResourceNotFoundException("Detalle de pedido", item.getIdDetallePedido()));
            if (!dp.getPedido().getIdPedido().equals(pedido.getIdPedido())) {
                throw new ValidationException("La linea " + item.getIdDetallePedido() + " no pertenece al pedido #" + pedido.getIdPedido());
            }
            if (item.getCantidadDevuelta() > dp.getCantidad()) {
                throw new ValidationException("La cantidad a devolver supera lo comprado en esa linea (maximo: " + dp.getCantidad() + ")");
            }
        }

        SolicitudDevolucion solicitud = new SolicitudDevolucion();
        solicitud.setPedido(pedido);
        solicitud.setUsuarioRegistro(usuario);
        solicitud.setMotivo(dto.getMotivo());
        solicitud.setDescripcion(dto.getDescripcion());
        solicitud.setEstado("solicitada");
        solicitud = solicitudRepository.save(solicitud);

        for (SolicitudDevolucionDetalleItemDTO item : dto.getDetalles()) {
            DetallePedido dp = detallePedidoRepository.findById(item.getIdDetallePedido()).orElseThrow();
            SolicitudDevolucionDetalle det = new SolicitudDevolucionDetalle();
            det.setSolicitud(solicitud);
            det.setDetallePedido(dp);
            det.setCantidadDevuelta(item.getCantidadDevuelta());
            detalleRepository.save(det);
        }

        logService.registrar(idUsuarioActual, "devoluciones", "crear",
                "Solicitud de devolucion #" + solicitud.getIdSolicitud() + " creada para pedido #" + pedido.getIdPedido(), null);

        return toDTO(solicitud);
    }

    public PageResponseDTO<SolicitudDevolucionResponseDTO> listar(int page, int size, String estado, Integer idPedido) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "idSolicitud"));
        Page<SolicitudDevolucion> result;

        boolean hasEstado = estado != null && !estado.isEmpty();
        boolean hasPedido = idPedido != null;

        if (hasEstado && hasPedido) {
            result = solicitudRepository.findByEstadoAndPedidoIdPedido(estado, idPedido, pageable);
        } else if (hasEstado) {
            result = solicitudRepository.findByEstado(estado, pageable);
        } else if (hasPedido) {
            result = solicitudRepository.findByPedidoIdPedido(idPedido, pageable);
        } else {
            result = solicitudRepository.findAll(pageable);
        }

        List<SolicitudDevolucionResponseDTO> content = result.getContent().stream()
                .map(this::toDTO).collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public SolicitudDevolucionResponseDTO obtener(Integer id) {
        SolicitudDevolucion s = solicitudRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de devolucion", id));
        return toDTO(s);
    }

    @Transactional
    public SolicitudDevolucionResponseDTO iniciarInspeccion(Integer id, Integer idUsuarioActual) {
        SolicitudDevolucion s = solicitudRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de devolucion", id));

        if (!"solicitada".equals(s.getEstado())) {
            throw new ValidationException("Solo se puede iniciar inspeccion en solicitudes con estado 'solicitada'");
        }

        Usuario inspector = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        s.setEstado("en_inspeccion");
        s.setUsuarioInspector(inspector);
        s.setFechaInspeccion(LocalDateTime.now());
        solicitudRepository.save(s);

        logService.registrar(idUsuarioActual, "devoluciones", "iniciar_inspeccion",
                "Inspeccion iniciada para solicitud #" + id, null);

        return toDTO(s);
    }

    @Transactional
    public SolicitudDevolucionResponseDTO inspeccionar(Integer id, InspeccionRequestDTO dto, Integer idUsuarioActual) {
        SolicitudDevolucion s = solicitudRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de devolucion", id));

        if (!"en_inspeccion".equals(s.getEstado())) {
            throw new ValidationException("Solo se puede inspeccionar solicitudes en estado 'en_inspeccion'");
        }

        Bodega bodega = bodegaRepository.findById(dto.getIdBodega())
                .orElseThrow(() -> new ResourceNotFoundException("Bodega", dto.getIdBodega()));

        Usuario inspector = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        entityManager.createNativeQuery("SET LOCAL app.current_user_id = '" + idUsuarioActual + "'")
                .executeUpdate();

        for (InspeccionDetalleDTO item : dto.getItems()) {
            SolicitudDevolucionDetalle det = detalleRepository.findById(item.getIdDetalleSd())
                    .orElseThrow(() -> new ResourceNotFoundException("Detalle de solicitud", item.getIdDetalleSd()));

            if (!det.getSolicitud().getIdSolicitud().equals(id)) {
                throw new ValidationException("La linea " + item.getIdDetalleSd() + " no pertenece a esta solicitud");
            }
            if (det.getResultadoInspeccion() != null) {
                throw new ValidationException("La linea " + item.getIdDetalleSd() + " ya fue inspeccionada");
            }

            det.setResultadoInspeccion(item.getResultadoInspeccion());
            det.setObservacionInspeccion(item.getObservacionInspeccion());
            detalleRepository.save(det);

            if ("apto_reventa".equals(item.getResultadoInspeccion())) {
                aplicarEntradaStock(det.getDetallePedido().getProducto(), bodega,
                        det.getCantidadDevuelta(), inspector, s.getIdSolicitud(), idUsuarioActual);
            }
            // defectuoso: no toca stock, queda registrado para F25
            // rechazado: no hace nada
        }

        // Verificar si todas las lineas ya tienen resultado
        entityManager.flush();
        List<SolicitudDevolucionDetalle> todas = detalleRepository.findBySolicitudIdSolicitud(id);
        boolean todasInspeccionadas = todas.stream().allMatch(d -> d.getResultadoInspeccion() != null);

        if (todasInspeccionadas) {
            boolean todasRechazadas = todas.stream().allMatch(d -> "rechazado".equals(d.getResultadoInspeccion()));
            s.setEstado(todasRechazadas ? "rechazada" : "completada");
            solicitudRepository.save(s);
        }

        logService.registrar(idUsuarioActual, "devoluciones", "inspeccionar",
                "Inspeccion registrada para solicitud #" + id + ". Estado: " + s.getEstado(), null);

        return toDTO(s);
    }

    @Transactional
    public SolicitudDevolucionResponseDTO registrarReembolso(Integer idSolicitud, ReembolsoRequestDTO dto, Integer idUsuarioActual) {
        SolicitudDevolucion s = solicitudRepository.findById(idSolicitud)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de devolucion", idSolicitud));

        if (!"completada".equals(s.getEstado())) {
            throw new ValidationException("Solo se puede registrar reembolso en solicitudes completadas");
        }

        if (reembolsoRepository.existsBySolicitudIdSolicitud(idSolicitud)) {
            throw new ValidationException("Ya existe un reembolso registrado para esta solicitud");
        }

        Usuario usuario = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        ReembolsoCliente reembolso = new ReembolsoCliente();
        reembolso.setSolicitud(s);
        reembolso.setUsuarioRegistro(usuario);
        reembolso.setMonto(dto.getMonto());
        reembolso.setMetodo(dto.getMetodo());
        reembolso.setObservaciones(dto.getObservaciones());
        reembolsoRepository.save(reembolso);

        logService.registrar(idUsuarioActual, "devoluciones", "reembolso",
                "Reembolso de $" + dto.getMonto() + " registrado para solicitud #" + idSolicitud, null);

        return toDTO(s);
    }

    private void aplicarEntradaStock(com.marathon.model.Producto producto, Bodega bodega,
                                     int cantidad, Usuario usuario, Integer idSolicitud, Integer idUsuarioActual) {
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
        inv.setStockActual(inv.getStockActual() + cantidad);
        inventarioRepository.save(inv);

        MovimientoInventario mov = new MovimientoInventario();
        mov.setInventario(inv);
        mov.setTipoMovimiento("entrada");
        mov.setCantidad(cantidad);
        mov.setUsuario(usuario);
        mov.setObservacion("Devolucion de cliente - Solicitud #" + idSolicitud + " - Apto reventa");
        movimientoRepository.save(mov);
    }

    private SolicitudDevolucionResponseDTO toDTO(SolicitudDevolucion s) {
        SolicitudDevolucionResponseDTO dto = new SolicitudDevolucionResponseDTO();
        dto.setIdSolicitud(s.getIdSolicitud());
        dto.setMotivo(s.getMotivo());
        dto.setDescripcion(s.getDescripcion());
        dto.setEstado(s.getEstado());
        dto.setFechaSolicitud(s.getFechaSolicitud());
        dto.setFechaInspeccion(s.getFechaInspeccion());

        if (s.getPedido() != null) {
            dto.setIdPedido(s.getPedido().getIdPedido());
            if (s.getPedido().getCliente() != null) {
                dto.setClienteNombre(s.getPedido().getCliente().getNombre() + " " + s.getPedido().getCliente().getApellido());
            }
        }
        if (s.getUsuarioInspector() != null) {
            dto.setInspectorNombre(s.getUsuarioInspector().getNombre() + " " + s.getUsuarioInspector().getApellido());
        }
        if (s.getUsuarioRegistro() != null) {
            dto.setRegistradoPor(s.getUsuarioRegistro().getNombre() + " " + s.getUsuarioRegistro().getApellido());
        }

        List<SolicitudDevolucionDetalle> detalles = detalleRepository.findBySolicitudIdSolicitud(s.getIdSolicitud());
        dto.setDetalles(detalles.stream().map(d -> {
            SolicitudDevolucionResponseDTO.DetalleDTO det = new SolicitudDevolucionResponseDTO.DetalleDTO();
            det.setIdDetalleSd(d.getIdDetalleSd());
            det.setCantidadDevuelta(d.getCantidadDevuelta());
            det.setResultadoInspeccion(d.getResultadoInspeccion());
            det.setObservacionInspeccion(d.getObservacionInspeccion());
            if (d.getDetallePedido() != null) {
                det.setIdDetallePedido(d.getDetallePedido().getIdDetalle());
                det.setCantidadOriginal(d.getDetallePedido().getCantidad());
                if (d.getDetallePedido().getProducto() != null) {
                    det.setProductoNombre(d.getDetallePedido().getProducto().getNombre());
                }
            }
            return det;
        }).collect(Collectors.toList()));

        // Reembolso
        reembolsoRepository.findBySolicitudIdSolicitud(s.getIdSolicitud()).ifPresent(r -> {
            SolicitudDevolucionResponseDTO.ReembolsoDTO rdto = new SolicitudDevolucionResponseDTO.ReembolsoDTO();
            rdto.setIdReembolso(r.getIdReembolso());
            rdto.setMonto(r.getMonto());
            rdto.setMetodo(r.getMetodo());
            rdto.setFechaReembolso(r.getFechaReembolso());
            rdto.setObservaciones(r.getObservaciones());
            dto.setReembolso(rdto);
        });

        return dto;
    }
}
