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
import com.marathon.dto.ordencompra.CambioEstadoOrdenCompraDTO;
import com.marathon.dto.ordencompra.OrdenCompraDetalleItemDTO;
import com.marathon.dto.ordencompra.OrdenCompraRequestDTO;
import com.marathon.dto.ordencompra.OrdenCompraResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.MateriaPrima;
import com.marathon.model.OrdenCompra;
import com.marathon.model.OrdenCompraDetalle;
import com.marathon.model.Producto;
import com.marathon.model.Proveedor;
import com.marathon.model.Usuario;
import com.marathon.repository.MateriaPrimaRepository;
import com.marathon.repository.OrdenCompraDetalleRepository;
import com.marathon.repository.OrdenCompraRepository;
import com.marathon.repository.ProductoRepository;
import com.marathon.repository.ProveedorRepository;
import com.marathon.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class OrdenCompraService {

    private static final String ROL_ADMIN = "Administrador";
    private static final String ROL_COMPRAS = "Encargado de Compras";

    private final OrdenCompraRepository ordenCompraRepository;
    private final OrdenCompraDetalleRepository detalleRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioDetailsService usuarioDetailsService;
    private final LogService logService;

    @PersistenceContext
    private EntityManager entityManager;

    public OrdenCompraService(OrdenCompraRepository ordenCompraRepository,
                              OrdenCompraDetalleRepository detalleRepository,
                              ProveedorRepository proveedorRepository,
                              ProductoRepository productoRepository,
                              MateriaPrimaRepository materiaPrimaRepository,
                              UsuarioRepository usuarioRepository,
                              UsuarioDetailsService usuarioDetailsService,
                              LogService logService) {
        this.ordenCompraRepository = ordenCompraRepository;
        this.detalleRepository = detalleRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoRepository = productoRepository;
        this.materiaPrimaRepository = materiaPrimaRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioDetailsService = usuarioDetailsService;
        this.logService = logService;
    }

    // ------------------------------------------------------------------
    // Listar
    // ------------------------------------------------------------------
    public PageResponseDTO<OrdenCompraResponseDTO> listar(int page, int size, String estado, Integer idProveedor) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "idOrdenCompra"));
        Page<OrdenCompra> result;

        boolean hasEstado = estado != null && !estado.isEmpty();
        boolean hasProveedor = idProveedor != null;

        if (hasEstado && hasProveedor) {
            result = ordenCompraRepository.findByEstadoAndProveedorIdProveedor(estado, idProveedor, pageable);
        } else if (hasEstado) {
            result = ordenCompraRepository.findByEstado(estado, pageable);
        } else if (hasProveedor) {
            result = ordenCompraRepository.findByProveedorIdProveedor(idProveedor, pageable);
        } else {
            result = ordenCompraRepository.findAll(pageable);
        }

        List<OrdenCompraResponseDTO> content = result.getContent().stream()
                .map(oc -> toDTO(oc, false))
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    // ------------------------------------------------------------------
    // Obtener (con detalles)
    // ------------------------------------------------------------------
    public OrdenCompraResponseDTO obtener(Integer id) {
        OrdenCompra oc = ordenCompraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de compra", id));
        return toDTO(oc, true);
    }

    // ------------------------------------------------------------------
    // Crear
    // ------------------------------------------------------------------
    @Transactional
    public OrdenCompraResponseDTO crear(OrdenCompraRequestDTO dto, Integer idUsuarioActual) {
        Proveedor proveedor = proveedorRepository.findById(dto.getIdProveedor())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", dto.getIdProveedor()));
        if (!"activo".equals(proveedor.getEstado())) {
            throw new ValidationException("El proveedor no está activo");
        }

        Usuario solicitante = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        OrdenCompra orden = new OrdenCompra();
        orden.setProveedor(proveedor);
        orden.setUsuarioSolicitante(solicitante);
        orden.setEstado("borrador");
        orden.setObservaciones(dto.getObservaciones());
        orden = ordenCompraRepository.save(orden);

        for (OrdenCompraDetalleItemDTO item : dto.getDetalles()) {
            OrdenCompraDetalle detalle = new OrdenCompraDetalle();
            detalle.setOrdenCompra(orden);
            detalle.setTipoItem(item.getTipoItem());
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(item.getPrecioUnitario());
            detalle.setCantidadRecibida(0);
            validarYAsignarItem(detalle, item);
            // subtotal es GENERATED — no se asigna
            detalleRepository.save(detalle);
        }

        // Recargar desde BD para obtener el total real calculado por el trigger
        entityManager.flush();
        entityManager.clear();
        orden = ordenCompraRepository.findById(orden.getIdOrdenCompra()).orElseThrow();

        logService.registrar(idUsuarioActual, "compras", "crear",
                "Orden de compra #" + orden.getIdOrdenCompra() + " creada (borrador). Total: $" + orden.getTotal(), null);

        return toDTO(orden, true);
    }

    /**
     * Valida la asociación polimórfica exclusiva y asigna producto o materia prima.
     * Cada línea debe referenciar producto O materia prima, nunca ambos ni ninguno.
     */
    private void validarYAsignarItem(OrdenCompraDetalle detalle, OrdenCompraDetalleItemDTO item) {
        boolean esProducto = "producto".equals(item.getTipoItem());
        boolean esMateriaPrima = "materia_prima".equals(item.getTipoItem());

        if (esProducto) {
            if (item.getIdProducto() == null || item.getIdMateriaPrima() != null) {
                throw new ValidationException("Cada línea debe tener producto O materia prima, no ambos ni ninguno");
            }
            Producto producto = productoRepository.findById(item.getIdProducto())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", item.getIdProducto()));
            if (!"activo".equals(producto.getEstado())) {
                throw new ValidationException("El producto '" + producto.getNombre() + "' no está activo");
            }
            detalle.setProducto(producto);
            detalle.setMateriaPrima(null);
        } else if (esMateriaPrima) {
            if (item.getIdMateriaPrima() == null || item.getIdProducto() != null) {
                throw new ValidationException("Cada línea debe tener producto O materia prima, no ambos ni ninguno");
            }
            MateriaPrima mp = materiaPrimaRepository.findById(item.getIdMateriaPrima())
                    .orElseThrow(() -> new ResourceNotFoundException("Materia prima", item.getIdMateriaPrima()));
            if (!"activo".equals(mp.getEstado())) {
                throw new ValidationException("La materia prima '" + mp.getNombre() + "' no está activa");
            }
            detalle.setMateriaPrima(mp);
            detalle.setProducto(null);
        } else {
            throw new ValidationException("Cada línea debe tener producto O materia prima, no ambos ni ninguno");
        }
    }

    // ------------------------------------------------------------------
    // Cambiar estado (máquina de estados + roles)
    // ------------------------------------------------------------------
    @Transactional
    public OrdenCompraResponseDTO cambiarEstado(Integer id, CambioEstadoOrdenCompraDTO dto, Integer idUsuarioActual) {
        OrdenCompra orden = ordenCompraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de compra", id));

        List<String> roles = usuarioDetailsService.getRoles(idUsuarioActual);
        boolean esAdmin = roles.contains(ROL_ADMIN);
        boolean esCompras = roles.contains(ROL_COMPRAS);

        String actual = orden.getEstado();
        String nuevo = dto.getEstado();

        switch (nuevo) {
            case "pendiente_aprobacion":
                if (!"borrador".equals(actual)) {
                    throw transicionInvalida(actual, nuevo);
                }
                if (!(esCompras || esAdmin)) {
                    throw new ValidationException("Solo el Encargado de Compras o el Administrador pueden enviar la orden a aprobación");
                }
                orden.setEstado(nuevo);
                break;

            case "aprobada":
            case "rechazada":
                if (!"pendiente_aprobacion".equals(actual)) {
                    throw transicionInvalida(actual, nuevo);
                }
                if (!esAdmin) {
                    throw new ValidationException("Solo el Administrador puede aprobar o rechazar órdenes de compra");
                }
                // Separación de funciones: quien solicita no puede aprobar su propia orden
                if (orden.getUsuarioSolicitante() != null
                        && orden.getUsuarioSolicitante().getIdUsuario().equals(idUsuarioActual)) {
                    throw new ValidationException("No puede aprobar ni rechazar una orden que usted mismo solicitó (separación de funciones)");
                }
                orden.setEstado(nuevo);
                if ("aprobada".equals(nuevo)) {
                    Usuario aprobador = usuarioRepository.findById(idUsuarioActual)
                            .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));
                    orden.setUsuarioAprobador(aprobador);
                    orden.setFechaAprobacion(LocalDateTime.now());
                }
                break;

            case "cancelada":
                if (!("borrador".equals(actual) || "pendiente_aprobacion".equals(actual) || "aprobada".equals(actual))) {
                    throw transicionInvalida(actual, nuevo);
                }
                if (!(esCompras || esAdmin)) {
                    throw new ValidationException("Solo el Encargado de Compras o el Administrador pueden cancelar órdenes de compra");
                }
                validarSinRecepciones(id);
                orden.setEstado(nuevo);
                break;

            default:
                throw transicionInvalida(actual, nuevo);
        }

        orden.setUpdatedAt(LocalDateTime.now());
        orden = ordenCompraRepository.save(orden);

        logService.registrar(idUsuarioActual, "compras", "cambio_estado",
                "Orden de compra #" + id + ": " + actual + " → " + nuevo
                        + (dto.getObservacion() != null ? " (" + dto.getObservacion() + ")" : ""), null);

        return toDTO(orden, true);
    }

    private void validarSinRecepciones(Integer idOrden) {
        List<OrdenCompraDetalle> detalles = detalleRepository.findByOrdenCompraIdOrdenCompra(idOrden);
        boolean tieneRecepciones = detalles.stream()
                .anyMatch(d -> d.getCantidadRecibida() != null && d.getCantidadRecibida() > 0);
        if (tieneRecepciones) {
            throw new ValidationException("No se puede cancelar la orden: ya tiene mercancía recibida en una o más líneas");
        }
    }

    private ValidationException transicionInvalida(String actual, String nuevo) {
        return new ValidationException("No se puede cambiar el estado de '" + actual + "' a '" + nuevo + "'");
    }

    // ------------------------------------------------------------------
    // Mapeo a DTO
    // ------------------------------------------------------------------
    private OrdenCompraResponseDTO toDTO(OrdenCompra oc, boolean incluirDetalles) {
        OrdenCompraResponseDTO dto = new OrdenCompraResponseDTO();
        dto.setIdOrdenCompra(oc.getIdOrdenCompra());
        dto.setFechaOrden(oc.getFechaOrden());
        dto.setFechaAprobacion(oc.getFechaAprobacion());
        dto.setEstado(oc.getEstado());
        dto.setTotal(oc.getTotal());
        dto.setObservaciones(oc.getObservaciones());
        dto.setCreatedAt(oc.getCreatedAt());
        dto.setUpdatedAt(oc.getUpdatedAt());

        if (oc.getProveedor() != null) {
            dto.setProveedor(new OrdenCompraResponseDTO.ProveedorSimpleDTO(
                    oc.getProveedor().getIdProveedor(), oc.getProveedor().getNombre()));
        }
        if (oc.getUsuarioSolicitante() != null) {
            dto.setUsuarioSolicitante(new OrdenCompraResponseDTO.UsuarioSimpleDTO(
                    oc.getUsuarioSolicitante().getIdUsuario(),
                    oc.getUsuarioSolicitante().getNombre(),
                    oc.getUsuarioSolicitante().getApellido()));
        }
        if (oc.getUsuarioAprobador() != null) {
            dto.setUsuarioAprobador(new OrdenCompraResponseDTO.UsuarioSimpleDTO(
                    oc.getUsuarioAprobador().getIdUsuario(),
                    oc.getUsuarioAprobador().getNombre(),
                    oc.getUsuarioAprobador().getApellido()));
        }

        if (incluirDetalles) {
            List<OrdenCompraDetalle> detalles = detalleRepository.findByOrdenCompraIdOrdenCompra(oc.getIdOrdenCompra());
            dto.setDetalles(detalles.stream().map(this::toDetalleDTO).collect(Collectors.toList()));
        }
        return dto;
    }

    private OrdenCompraResponseDTO.DetalleDTO toDetalleDTO(OrdenCompraDetalle d) {
        OrdenCompraResponseDTO.DetalleDTO dto = new OrdenCompraResponseDTO.DetalleDTO();
        dto.setIdDetalleOc(d.getIdDetalleOc());
        dto.setTipoItem(d.getTipoItem());
        dto.setCantidad(d.getCantidad());
        dto.setPrecioUnitario(d.getPrecioUnitario());
        dto.setSubtotal(d.getSubtotal());
        dto.setCantidadRecibida(d.getCantidadRecibida());
        if ("producto".equals(d.getTipoItem()) && d.getProducto() != null) {
            dto.setIdProducto(d.getProducto().getIdProducto());
            dto.setItemNombre(d.getProducto().getNombre());
        } else if ("materia_prima".equals(d.getTipoItem()) && d.getMateriaPrima() != null) {
            dto.setIdMateriaPrima(d.getMateriaPrima().getIdMateriaPrima());
            dto.setItemNombre(d.getMateriaPrima().getNombre());
        }
        return dto;
    }
}
