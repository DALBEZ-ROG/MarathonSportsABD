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
import com.marathon.model.OrdenCompra;
import com.marathon.model.OrdenCompraDetalle;
import com.marathon.model.Producto;
import com.marathon.model.ProductoProveedor;
import com.marathon.model.Proveedor;
import com.marathon.model.RecepcionMercanciaDetalle;
import com.marathon.model.SolicitudDevolucionDetalle;
import com.marathon.model.Usuario;
import com.marathon.repository.DevolucionProveedorDetalleRepository;
import com.marathon.repository.DevolucionProveedorRepository;
import com.marathon.repository.OrdenCompraDetalleRepository;
import com.marathon.repository.OrdenCompraRepository;
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
    private final OrdenCompraRepository ordenCompraRepository;
    private final OrdenCompraDetalleRepository ordenCompraDetalleRepository;
    private final LogService logService;


    public DevolucionProveedorService(DevolucionProveedorRepository devolucionRepository,
                                      DevolucionProveedorDetalleRepository detalleRepository,
                                      SolicitudDevolucionDetalleRepository sddRepository,
                                      RecepcionMercanciaDetalleRepository rmdRepository,
                                      ProductoProveedorRepository ppRepository,
                                      ProveedorRepository proveedorRepository,
                                      UsuarioRepository usuarioRepository,
                                      OrdenCompraRepository ordenCompraRepository,
                                      OrdenCompraDetalleRepository ordenCompraDetalleRepository,
                                      LogService logService) {
        this.devolucionRepository = devolucionRepository;
        this.detalleRepository = detalleRepository;
        this.sddRepository = sddRepository;
        this.rmdRepository = rmdRepository;
        this.ppRepository = ppRepository;
        this.proveedorRepository = proveedorRepository;
        this.usuarioRepository = usuarioRepository;
        this.ordenCompraRepository = ordenCompraRepository;
        this.ordenCompraDetalleRepository = ordenCompraDetalleRepository;
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
        String texto = Filtros.textoSiNoEsNumero(busqueda);
        String[] p = Filtros.palabras(texto);
        Page<DevolucionProveedor> result = texto != null
                ? devolucionRepository.buscarConTexto(Filtros.vacioComoNulo(estado), idProveedor, p[0], p[1], p[2], pageable)
                : devolucionRepository.buscarSinTexto(Filtros.vacioComoNulo(estado), idProveedor,
                        Filtros.numeroDeDocumento(busqueda), pageable);

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

        // F69: si repone, la mercancia que viene deja rastro desde ya.
        if ("reposicion".equals(dto.getTipoResolucion())) {
            crearOrdenDeReposicion(d, idUsuarioActual);
        }

        logService.registrar(idUsuarioActual, "devoluciones_proveedor", "resolver",
                "Devolucion #" + id + " resuelta: " + dto.getTipoResolucion()
                        + (dto.getMontoReembolso() != null ? " ($" + dto.getMontoReembolso() + ")" : ""), null);
        return toDTO(d);
    }

    /**
     * Convierte una reposición aceptada en una orden de compra que espera llegar
     * (F69).
     *
     * <p><b>Por qué existe.</b> Antes, registrar «el proveedor manda otra igual»
     * cerraba la devolución y ahí moría: nada decía que había mercancía en
     * camino, ni cuánta, ni de qué reclamación venía. Y cuando llegaba, entraba
     * como una compra más — con su factura y su cuenta por pagar, es decir,
     * <b>pagando dos veces lo mismo</b>.
     *
     * <p><b>Precio real, no cero.</b> Lo natural sería una orden «gratis», pero
     * no se puede y tampoco conviene: {@code chk_oc_detalle_precio} exige
     * {@code precio_unitario > 0}, y la recepción recalcula el costo promedio
     * ponderado (F29) — entrar mercancía a cero falsearía el costo de todo lo
     * que hay en bodega. Lo que impide pagarla no es un cero: es la marca
     * {@code es_reposicion}, que {@code FacturaCompraService} respeta.
     *
     * <p><b>Nace aprobada.</b> Aprobar una orden es autorizar un gasto, y aquí
     * no se gasta: el proveedor ya se comprometió al aceptar la reclamación.
     * Hacerla pasar por aprobación sería pedir que se autorice un desembolso que
     * no existe.
     *
     * <p>Aparece en el tablero bajo «Aprobadas sin recibir», que es justo el
     * aviso de que algo está por llegar.
     */
    private void crearOrdenDeReposicion(DevolucionProveedor d, Integer idUsuarioActual) {
        List<DevolucionProveedorDetalle> lineas =
                detalleRepository.findByDevolucionProveedorIdDevolucionProv(d.getIdDevolucionProv());
        if (lineas.isEmpty()) {
            return;   // sin lineas no hay nada que reponer
        }

        Usuario usuario = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        OrdenCompra oc = new OrdenCompra();
        oc.setProveedor(d.getProveedor());
        oc.setUsuarioSolicitante(usuario);
        // NO se pone aprobador ni fecha de aprobacion, y son dos razones a la vez:
        //
        //   1. Es la verdad: nadie la aprobo. No hubo nada que aprobar, porque no
        //      se esta comprando nada. Poner un aprobador seria inventarse una
        //      firma. La orden nace "aprobada" en el sentido de "lista para
        //      recibir", no de "alguien autorizo un gasto".
        //
        //   2. La F34 no le concede a rol_encargado_compras el INSERT de
        //      id_usuario_aprobador ni fecha_aprobacion — a proposito, para que
        //      quien compra no pueda auto-aprobarse al crear. Rellenarlas aqui
        //      hacia que PostgreSQL rechazara el INSERT entero con "permiso
        //      denegado", que es como se descubrio esto. La respuesta correcta
        //      NO era conceder el privilegio: era no escribir esas columnas.
        oc.setEstado("aprobada");
        oc.setEsReposicion(true);
        oc.setDevolucionProveedor(d);
        oc.setObservaciones("Reposición por la devolución a proveedor #"
                + d.getIdDevolucionProv() + ". No se factura: ya se pagó al comprar "
                + "la mercancía que salió defectuosa.");
        oc = ordenCompraRepository.save(oc);

        for (DevolucionProveedorDetalle linea : lineas) {
            if (linea.getProducto() == null) {
                continue;
            }
            OrdenCompraDetalle det = new OrdenCompraDetalle();
            det.setOrdenCompra(oc);
            det.setTipoItem("producto");
            det.setProducto(linea.getProducto());
            det.setCantidad(linea.getCantidad());
            det.setPrecioUnitario(precioDeCompra(linea.getProducto(), d.getProveedor()));
            det.setCantidadRecibida(0);
            ordenCompraDetalleRepository.save(det);
        }

        logService.registrar(idUsuarioActual, "compras", "reposicion_creada",
                "Orden de compra #" + oc.getIdOrdenCompra() + " creada como reposición de la "
                + "devolución #" + d.getIdDevolucionProv() + ". No facturable.", null);
    }

    /**
     * El precio que se usa en la línea de la reposición.
     *
     * <p>No es lo que se va a pagar —una reposición no se paga— sino lo que vale
     * esa mercancía, para que al recibirla el costo promedio de la bodega siga
     * diciendo la verdad. Se toma el precio pactado con ese proveedor; si no lo
     * hay, el precio del catálogo; y si tampoco, un céntimo, que es lo mínimo
     * que admite {@code chk_oc_detalle_precio}.
     */
    private java.math.BigDecimal precioDeCompra(Producto producto, Proveedor proveedor) {
        java.math.BigDecimal precio = ppRepository.findByProductoIdProducto(producto.getIdProducto())
                .stream()
                .filter(pp -> pp.getProveedor() != null
                        && pp.getProveedor().getIdProveedor().equals(proveedor.getIdProveedor()))
                .map(pp -> pp.getPrecioCompra())
                .filter(p -> p != null && p.compareTo(java.math.BigDecimal.ZERO) > 0)
                .findFirst()
                .orElse(null);

        if (precio != null) {
            return precio;
        }
        if (producto.getPrecio() != null
                && producto.getPrecio().compareTo(java.math.BigDecimal.ZERO) > 0) {
            return producto.getPrecio();
        }
        return new java.math.BigDecimal("0.01");
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
