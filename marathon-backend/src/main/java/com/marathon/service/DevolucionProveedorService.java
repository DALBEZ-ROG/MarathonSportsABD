package com.marathon.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.devolucionproveedor.DevolucionProveedorItemDTO;
import com.marathon.dto.devolucionproveedor.DevolucionProveedorRequestDTO;
import com.marathon.dto.devolucionproveedor.DevolucionProveedorResponseDTO;
import com.marathon.dto.devolucionproveedor.ItemDefectuosoDisponibleDTO;
import com.marathon.dto.devolucionproveedor.ResolucionDevolucionDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.DevolucionProveedor;
import com.marathon.model.DevolucionProveedorDetalle;
import com.marathon.model.ProductoProveedor;
import com.marathon.model.Proveedor;
import com.marathon.model.RecepcionMercanciaDetalle;
import com.marathon.model.SolicitudDevolucionDetalle;
import com.marathon.model.Usuario;
import com.marathon.repository.DevolucionProveedorDetalleRepository;
import com.marathon.repository.DevolucionProveedorRepository;
import com.marathon.repository.ProductoProveedorRepository;
import com.marathon.repository.ProveedorRepository;
import com.marathon.repository.RecepcionMercanciaDetalleRepository;
import com.marathon.repository.SolicitudDevolucionDetalleRepository;
import com.marathon.repository.UsuarioRepository;

@Service
public class DevolucionProveedorService {

    private final DevolucionProveedorRepository devolucionRepository;
    private final DevolucionProveedorDetalleRepository detalleRepository;
    private final SolicitudDevolucionDetalleRepository sddRepository;
    private final RecepcionMercanciaDetalleRepository rmdRepository;
    private final ProductoProveedorRepository ppRepository;
    private final ProveedorRepository proveedorRepository;
    private final UsuarioRepository usuarioRepository;
    private final LogService logService;


    public DevolucionProveedorService(DevolucionProveedorRepository devolucionRepository,
                                      DevolucionProveedorDetalleRepository detalleRepository,
                                      SolicitudDevolucionDetalleRepository sddRepository,
                                      RecepcionMercanciaDetalleRepository rmdRepository,
                                      ProductoProveedorRepository ppRepository,
                                      ProveedorRepository proveedorRepository,
                                      UsuarioRepository usuarioRepository,
                                      LogService logService) {
        this.devolucionRepository = devolucionRepository;
        this.detalleRepository = detalleRepository;
        this.sddRepository = sddRepository;
        this.rmdRepository = rmdRepository;
        this.ppRepository = ppRepository;
        this.proveedorRepository = proveedorRepository;
        this.usuarioRepository = usuarioRepository;
        this.logService = logService;
    }

    // ---------------------------------------------------------------
    // Items defectuosos disponibles (bandeja)
    // ---------------------------------------------------------------
    public List<ItemDefectuosoDisponibleDTO> listarItemsDefectuososDisponibles(Integer idProveedor) {
        List<ItemDefectuosoDisponibleDTO> result = new ArrayList<>();

        // Origen 1: RMA cliente (solicitud_devolucion_detalle con resultado=defectuoso, no usado aun)
        List<SolicitudDevolucionDetalle> rmaItems = sddRepository.findAll().stream()
                .filter(d -> "defectuoso".equals(d.getResultadoInspeccion()))
                .filter(d -> !detalleRepository.existsBySolicitudDevolucionDetalleIdDetalleSd(d.getIdDetalleSd()))
                .collect(Collectors.toList());

        for (SolicitudDevolucionDetalle sdd : rmaItems) {
            ItemDefectuosoDisponibleDTO item = new ItemDefectuosoDisponibleDTO();
            item.setOrigen("rma_cliente");
            item.setIdOrigenDetalle(sdd.getIdDetalleSd());
            item.setCantidad(sdd.getCantidadDevuelta());

            if (sdd.getDetallePedido() != null && sdd.getDetallePedido().getProducto() != null) {
                item.setIdProducto(sdd.getDetallePedido().getProducto().getIdProducto());
                item.setNombreProducto(sdd.getDetallePedido().getProducto().getNombre());

                // Buscar proveedor principal
                List<ProductoProveedor> pps = ppRepository.findByProductoIdProducto(
                        sdd.getDetallePedido().getProducto().getIdProducto());
                pps.stream().filter(pp -> Boolean.TRUE.equals(pp.getEsProveedorPrincipal())).findFirst()
                        .ifPresent(pp -> {
                            item.setIdProveedorSugerido(pp.getProveedor().getIdProveedor());
                            item.setNombreProveedorSugerido(pp.getProveedor().getNombre());
                        });
            }

            if (sdd.getSolicitud() != null) {
                item.setFechaOrigen(sdd.getSolicitud().getFechaSolicitud());
                item.setReferenciaOrigen("Solicitud #" + sdd.getSolicitud().getIdSolicitud());
            }
            result.add(item);
        }

        // Origen 2: Recepcion de compra (cantidad_defectuosa > 0, no usado aun)
        List<RecepcionMercanciaDetalle> recItems = rmdRepository.findAll().stream()
                .filter(d -> d.getCantidadDefectuosa() != null && d.getCantidadDefectuosa() > 0)
                .filter(d -> !detalleRepository.existsByRecepcionDetalleIdDetalleRm(d.getIdDetalleRm()))
                .collect(Collectors.toList());

        for (RecepcionMercanciaDetalle rmd : recItems) {
            // Solo productos (no materia prima) para devolucion a proveedor
            if (rmd.getDetalleOc() != null && "producto".equals(rmd.getDetalleOc().getTipoItem())
                    && rmd.getDetalleOc().getProducto() != null) {
                ItemDefectuosoDisponibleDTO item = new ItemDefectuosoDisponibleDTO();
                item.setOrigen("recepcion_compra");
                item.setIdOrigenDetalle(rmd.getIdDetalleRm());
                item.setCantidad(rmd.getCantidadDefectuosa());
                item.setIdProducto(rmd.getDetalleOc().getProducto().getIdProducto());
                item.setNombreProducto(rmd.getDetalleOc().getProducto().getNombre());

                // Proveedor directo de la OC
                if (rmd.getRecepcion() != null && rmd.getRecepcion().getOrdenCompra() != null
                        && rmd.getRecepcion().getOrdenCompra().getProveedor() != null) {
                    item.setIdProveedorSugerido(rmd.getRecepcion().getOrdenCompra().getProveedor().getIdProveedor());
                    item.setNombreProveedorSugerido(rmd.getRecepcion().getOrdenCompra().getProveedor().getNombre());
                }

                if (rmd.getRecepcion() != null) {
                    item.setFechaOrigen(rmd.getRecepcion().getCreatedAt());
                    item.setReferenciaOrigen("Recepcion #" + rmd.getRecepcion().getIdRecepcion()
                            + " - OC #" + rmd.getRecepcion().getOrdenCompra().getIdOrdenCompra());
                }
                result.add(item);
            }
        }

        // Filtrar por proveedor si se indica
        if (idProveedor != null) {
            result = result.stream()
                    .filter(i -> idProveedor.equals(i.getIdProveedorSugerido()))
                    .collect(Collectors.toList());
        }

        // Ordenar por fecha desc
        result.sort((a, b) -> {
            if (a.getFechaOrigen() == null && b.getFechaOrigen() == null) return 0;
            if (a.getFechaOrigen() == null) return 1;
            if (b.getFechaOrigen() == null) return -1;
            return b.getFechaOrigen().compareTo(a.getFechaOrigen());
        });

        return result;
    }

    // ---------------------------------------------------------------
    // Crear
    // ---------------------------------------------------------------
    @Transactional
    public DevolucionProveedorResponseDTO crear(DevolucionProveedorRequestDTO dto, Integer idUsuarioActual) {
        Proveedor proveedor = proveedorRepository.findById(dto.getIdProveedor())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", dto.getIdProveedor()));
        Usuario usuario = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        DevolucionProveedor dev = new DevolucionProveedor();
        dev.setProveedor(proveedor);
        dev.setUsuarioRegistro(usuario);
        dev.setEstado("pendiente");
        dev.setObservaciones(dto.getObservaciones());
        dev = devolucionRepository.save(dev);

        for (DevolucionProveedorItemDTO item : dto.getItems()) {
            DevolucionProveedorDetalle det = new DevolucionProveedorDetalle();
            det.setDevolucionProveedor(dev);
            det.setOrigen(item.getOrigen());
            det.setCantidad(item.getCantidad());
            det.setMotivo(item.getMotivo());

            if ("rma_cliente".equals(item.getOrigen())) {
                SolicitudDevolucionDetalle sdd = sddRepository.findById(item.getIdOrigenDetalle())
                        .orElseThrow(() -> new ResourceNotFoundException("Detalle solicitud devolucion", item.getIdOrigenDetalle()));
                if (!"defectuoso".equals(sdd.getResultadoInspeccion())) {
                    throw new ValidationException("El item #" + item.getIdOrigenDetalle() + " no tiene resultado 'defectuoso'");
                }
                if (detalleRepository.existsBySolicitudDevolucionDetalleIdDetalleSd(sdd.getIdDetalleSd())) {
                    throw new ValidationException("El item RMA #" + sdd.getIdDetalleSd() + " ya fue incluido en otra devolucion a proveedor");
                }
                if (item.getCantidad() > sdd.getCantidadDevuelta()) {
                    throw new ValidationException("La cantidad excede lo defectuoso disponible (max: " + sdd.getCantidadDevuelta() + ")");
                }
                det.setSolicitudDevolucionDetalle(sdd);
                det.setProducto(sdd.getDetallePedido().getProducto());

            } else if ("recepcion_compra".equals(item.getOrigen())) {
                RecepcionMercanciaDetalle rmd = rmdRepository.findById(item.getIdOrigenDetalle())
                        .orElseThrow(() -> new ResourceNotFoundException("Detalle recepcion", item.getIdOrigenDetalle()));
                if (rmd.getCantidadDefectuosa() == null || rmd.getCantidadDefectuosa() <= 0) {
                    throw new ValidationException("El item de recepcion #" + item.getIdOrigenDetalle() + " no tiene cantidad defectuosa");
                }
                if (detalleRepository.existsByRecepcionDetalleIdDetalleRm(rmd.getIdDetalleRm())) {
                    throw new ValidationException("El item de recepcion #" + rmd.getIdDetalleRm() + " ya fue incluido en otra devolucion a proveedor");
                }
                if (item.getCantidad() > rmd.getCantidadDefectuosa()) {
                    throw new ValidationException("La cantidad excede lo defectuoso disponible (max: " + rmd.getCantidadDefectuosa() + ")");
                }
                det.setRecepcionDetalle(rmd);
                det.setProducto(rmd.getDetalleOc().getProducto());
            }

            detalleRepository.save(det);
        }

        logService.registrar(idUsuarioActual, "devoluciones_proveedor", "crear",
                "Devolucion a proveedor #" + dev.getIdDevolucionProv() + " creada para " + proveedor.getNombre(), null);

        return toDTO(dev);
    }

    // ---------------------------------------------------------------
    // Listar, obtener
    // ---------------------------------------------------------------
    public PageResponseDTO<DevolucionProveedorResponseDTO> listar(int page, int size, String estado,
                                                                  Integer idProveedor, String busqueda) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "idDevolucionProv"));
        // F54: busqueda por texto, sin la cual encontrar un registro concreto
        // entre miles era pasar paginas una a una.
        Page<DevolucionProveedor> result = devolucionRepository.buscar(
                Filtros.vacioComoNulo(estado), idProveedor, Filtros.vacioComoNulo(busqueda), pageable);

        List<DevolucionProveedorResponseDTO> content = result.getContent().stream()
                .map(this::toDTO).collect(Collectors.toList());
        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public DevolucionProveedorResponseDTO obtener(Integer id) {
        DevolucionProveedor d = devolucionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Devolucion a proveedor", id));
        return toDTO(d);
    }

    // ---------------------------------------------------------------
    // Cambiar estado
    // ---------------------------------------------------------------
    @Transactional
    public DevolucionProveedorResponseDTO cambiarEstado(Integer id, String nuevoEstado, Integer idUsuarioActual) {
        DevolucionProveedor d = devolucionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Devolucion a proveedor", id));
        String actual = d.getEstado();

        if ("enviada".equals(nuevoEstado) && "pendiente".equals(actual)) {
            d.setEstado("enviada");
        } else if ("rechazada".equals(nuevoEstado) && "enviada".equals(actual)) {
            d.setEstado("rechazada");
        } else {
            throw new ValidationException("No se puede cambiar el estado de '" + actual + "' a '" + nuevoEstado + "'");
        }
        devolucionRepository.save(d);

        logService.registrar(idUsuarioActual, "devoluciones_proveedor", "cambio_estado",
                "Devolucion #" + id + ": " + actual + " -> " + nuevoEstado, null);
        return toDTO(d);
    }

    // ---------------------------------------------------------------
    // Resolver
    // ---------------------------------------------------------------
    @Transactional
    public DevolucionProveedorResponseDTO resolver(Integer id, ResolucionDevolucionDTO dto, Integer idUsuarioActual) {
        DevolucionProveedor d = devolucionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Devolucion a proveedor", id));

        if (!"enviada".equals(d.getEstado())) {
            throw new ValidationException("Solo se puede resolver una devolucion en estado 'enviada'");
        }
        if ("reembolso".equals(dto.getTipoResolucion()) && (dto.getMontoReembolso() == null
                || dto.getMontoReembolso().compareTo(java.math.BigDecimal.ZERO) <= 0)) {
            throw new ValidationException("Para resolucion tipo 'reembolso' se requiere un monto mayor a 0");
        }

        d.setEstado("resuelta");
        d.setTipoResolucion(dto.getTipoResolucion());
        d.setMontoReembolso(dto.getMontoReembolso());
        if (dto.getObservaciones() != null) d.setObservaciones(dto.getObservaciones());
        devolucionRepository.save(d);

        logService.registrar(idUsuarioActual, "devoluciones_proveedor", "resolver",
                "Devolucion #" + id + " resuelta: " + dto.getTipoResolucion()
                        + (dto.getMontoReembolso() != null ? " ($" + dto.getMontoReembolso() + ")" : ""), null);
        return toDTO(d);
    }

    // ---------------------------------------------------------------
    // Mapeo
    // ---------------------------------------------------------------
    private DevolucionProveedorResponseDTO toDTO(DevolucionProveedor d) {
        DevolucionProveedorResponseDTO dto = new DevolucionProveedorResponseDTO();
        dto.setIdDevolucionProv(d.getIdDevolucionProv());
        dto.setEstado(d.getEstado());
        dto.setTipoResolucion(d.getTipoResolucion());
        dto.setMontoReembolso(d.getMontoReembolso());
        dto.setObservaciones(d.getObservaciones());
        dto.setFechaDevolucion(d.getFechaDevolucion());
        if (d.getProveedor() != null) {
            dto.setIdProveedor(d.getProveedor().getIdProveedor());
            dto.setProveedorNombre(d.getProveedor().getNombre());
        }
        if (d.getUsuarioRegistro() != null) {
            dto.setRegistradoPor(d.getUsuarioRegistro().getNombre() + " " + d.getUsuarioRegistro().getApellido());
        }

        List<DevolucionProveedorDetalle> detalles = detalleRepository.findByDevolucionProveedorIdDevolucionProv(d.getIdDevolucionProv());
        dto.setDetalles(detalles.stream().map(det -> {
            DevolucionProveedorResponseDTO.DetalleDTO ddto = new DevolucionProveedorResponseDTO.DetalleDTO();
            ddto.setIdDetalleDp(det.getIdDetalleDp());
            ddto.setOrigen(det.getOrigen());
            ddto.setCantidad(det.getCantidad());
            ddto.setMotivo(det.getMotivo());
            if (det.getProducto() != null) ddto.setProductoNombre(det.getProducto().getNombre());
            if ("rma_cliente".equals(det.getOrigen()) && det.getSolicitudDevolucionDetalle() != null) {
                ddto.setReferenciaOrigen("Solicitud #" + det.getSolicitudDevolucionDetalle().getSolicitud().getIdSolicitud());
            } else if ("recepcion_compra".equals(det.getOrigen()) && det.getRecepcionDetalle() != null) {
                ddto.setReferenciaOrigen("Recepcion #" + det.getRecepcionDetalle().getRecepcion().getIdRecepcion());
            }
            return ddto;
        }).collect(Collectors.toList()));

        return dto;
    }
}
