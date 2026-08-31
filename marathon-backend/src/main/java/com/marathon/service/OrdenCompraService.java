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

import com.marathon.config.Permisos;
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

    private final OrdenCompraRepository ordenCompraRepository;
    private final OrdenCompraDetalleRepository detalleRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final UsuarioRepository usuarioRepository;
    private final LogService logService;

    @PersistenceContext
    private EntityManager entityManager;

    public OrdenCompraService(OrdenCompraRepository ordenCompraRepository,
                              OrdenCompraDetalleRepository detalleRepository,
                              ProveedorRepository proveedorRepository,
                              ProductoRepository productoRepository,
                              MateriaPrimaRepository materiaPrimaRepository,
                              UsuarioRepository usuarioRepository,
                              LogService logService) {
        this.ordenCompraRepository = ordenCompraRepository;
        this.detalleRepository = detalleRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoRepository = productoRepository;
        this.materiaPrimaRepository = materiaPrimaRepository;
        this.usuarioRepository = usuarioRepository;
        this.logService = logService;
    }

    // ------------------------------------------------------------------
    // Listar
    // ------------------------------------------------------------------
    public PageResponseDTO<OrdenCompraResponseDTO> listar(int page, int size, String estado,
                                                          Integer idProveedor, String busqueda) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "idOrdenCompra"));
        // F54: busqueda por texto. Antes solo habia desplegables, y para
        // encontrar un registro concreto entre miles habia que pasar paginas.
        // F94: dos consultas, y el servicio elige. Con texto hace falta unir
        // proveedor; sin texto, unir es trabajo para nada. Ver la nota larga en
        // OrdenCompraRepository.
        String texto = Filtros.textoSiNoEsNumero(busqueda);
        String[] p = Filtros.palabras(texto);
        Page<OrdenCompra> result = texto != null
                ? ordenCompraRepository.buscarConTexto(
                        Filtros.vacioComoNulo(estado), idProveedor, p[0], p[1], p[2], pageable)
                : ordenCompraRepository.buscarSinTexto(
                        Filtros.vacioComoNulo(estado), idProveedor,
                        Filtros.numeroDeDocumento(busqueda), pageable);

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
     * Modifica una orden de compra que todavia es un borrador (L13, D-14 en el
     * plan / defecto D-22).
     *
     * <p>Hasta ahora {@code OrdenCompraController} solo exponia GET, POST y
     * {@code PUT /{id}/estado}: una orden creada en {@code borrador} con una
     * cantidad o un precio mal puestos solo se podia cancelar y rehacer. El
     * estado {@code borrador} existia en el CHECK y en la maquina de estados,
     * pero no servia para lo que sirve un borrador.
     *
     * <p>Las lineas se reemplazan enteras en vez de casarlas una a una: es mas
     * simple, y el trigger {@code fn_recalcular_total_orden_compra_stmt}
     * recalcula el total en cada sentencia, asi que el importe queda correcto
     * sin que este metodo lo toque. {@code fn_proteger_total_orden_compra}
     * seguiria impidiendolo si lo intentara.
     */
    @Transactional
    public OrdenCompraResponseDTO actualizarBorrador(Integer id, OrdenCompraRequestDTO dto,
                                                     Integer idUsuarioActual) {
        OrdenCompra orden = ordenCompraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de compra", id));

        if (!"borrador".equals(orden.getEstado())) {
            throw new ValidationException("Solo se puede modificar una orden en estado 'borrador'. "
                    + "Esta orden está en '" + orden.getEstado() + "'.");
        }

        Proveedor proveedor = proveedorRepository.findById(dto.getIdProveedor())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", dto.getIdProveedor()));
        if (!"activo".equals(proveedor.getEstado())) {
            throw new ValidationException("El proveedor no está activo");
        }

        orden.setProveedor(proveedor);
        orden.setObservaciones(dto.getObservaciones());
        orden.setUpdatedAt(LocalDateTime.now());
        ordenCompraRepository.save(orden);

        // Fuera las lineas viejas, dentro las nuevas. Un borrador no tiene
        // recepciones (lo garantiza la maquina de estados), asi que no hay
        // cantidad_recibida que preservar.
        detalleRepository.deleteAll(detalleRepository.findByOrdenCompraIdOrdenCompra(id));
        entityManager.flush();

        for (OrdenCompraDetalleItemDTO item : dto.getDetalles()) {
            OrdenCompraDetalle detalle = new OrdenCompraDetalle();
            detalle.setOrdenCompra(orden);
            detalle.setTipoItem(item.getTipoItem());
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(item.getPrecioUnitario());
            detalle.setCantidadRecibida(0);
            validarYAsignarItem(detalle, item);
            detalleRepository.save(detalle);
        }

        entityManager.flush();
        entityManager.clear();
        orden = ordenCompraRepository.findById(id).orElseThrow();

        logService.registrar(idUsuarioActual, "compras", "actualizar",
                "Orden de compra #" + id + " modificada (borrador). Total: $" + orden.getTotal(), null);

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

        // ------------------------------------------------------------------
        // F48 (D-13): quien puede hacer que, decidido por la MATRIZ.
        // ------------------------------------------------------------------
        // Antes esto era roles.contains("Administrador") escrito a mano. El
        // reparto era correcto —y de el sale la matriz de fase48—, pero estaba
        // grabado en el codigo: cambiarlo exigia recompilar, y la pantalla de
        // roles, que existe justamente para editarlo, no pintaba nada.
        //
        // Las cuatro transiciones caen en el mismo endpoint, asi que la
        // comprobacion no cabe en un @PreAuthorize del controlador y vive aqui.
        //
        // La separacion de funciones (quien solicita no aprueba) NO se convierte
        // en permiso y se queda debajo tal cual: no depende de quien seas sino de
        // que orden sea, y eso ningun permiso lo puede expresar.
        String actual = orden.getEstado();
        String nuevo = dto.getEstado();

        switch (nuevo) {
            case "pendiente_aprobacion":
                if (!"borrador".equals(actual)) {
                    throw transicionInvalida(actual, nuevo);
                }
                Permisos.exigirSiHaySesion("compras:crear", "enviar una orden de compra a aprobación");
                orden.setEstado(nuevo);
                break;

            case "aprobada":
            case "rechazada":
                if (!"pendiente_aprobacion".equals(actual)) {
                    throw transicionInvalida(actual, nuevo);
                }
                Permisos.exigirSiHaySesion(
                        "aprobada".equals(nuevo) ? "compras:aprobar" : "compras:rechazar",
                        "aprobada".equals(nuevo) ? "aprobar órdenes de compra" : "rechazar órdenes de compra");
                // ----------------------------------------------------------
                // Separacion de funciones, CON una excepcion (F64).
                // ----------------------------------------------------------
                // La regla sigue siendo que quien solicita no aprueba. Lo que
                // cambia el 2026-08-28, por decision del dueño del proyecto, es
                // que el ADMINISTRADOR queda exento: puede aprobar una orden
                // que el mismo creo.
                //
                // El motivo es operativo y es real: 'compras:aprobar' lo tiene
                // solo el Administrador y solo hay UN usuario con ese rol, asi
                // que una orden creada por el administrador no la podia aprobar
                // NADIE. El flujo se quedaba muerto en 'pendiente_aprobacion'
                // sin salida, salvo dando de alta un segundo administrador.
                //
                // Lo que se pierde, dicho sin adornos: una compra del
                // administrador ya no pasa por un segundo par de ojos, y este
                // era el unico punto del sistema donde el dinero salia con
                // doble firma. Para desarrollo y demostracion es lo razonable;
                // en una instalacion real, la respuesta correcta seria tener
                // dos administradores y volver a quitar esta excepcion —es
                // borrar la condicion de abajo—.
                //
                // La restriccion SIGUE VIVA para todos los demas: si algun dia
                // se le concede 'compras:aprobar' a Encargado de Compras desde
                // la pantalla de roles, ese rol no podra aprobar lo suyo. Por
                // eso la excepcion pregunta por el ROL y no por el permiso: el
                // permiso se regala marcando una casilla, el rol no.
                if (!Permisos.esAdministrador()
                        && orden.getUsuarioSolicitante() != null
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
                Permisos.exigirSiHaySesion("compras:cancelar", "cancelar órdenes de compra");
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
        dto.setEsReposicion(oc.getEsReposicion());
        dto.setIdDevolucionProv(oc.getDevolucionProveedor() != null
                ? oc.getDevolucionProveedor().getIdDevolucionProv() : null);
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
