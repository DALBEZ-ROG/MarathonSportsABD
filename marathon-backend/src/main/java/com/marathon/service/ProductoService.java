package com.marathon.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.dao.DataAccessException;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.producto.ProductoRequestDTO;
import com.marathon.dto.producto.ProductoResponseDTO;
import com.marathon.dto.producto.ProductoResponseDTO.ProveedorSimpleDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.Categoria;
import com.marathon.model.Producto;
import com.marathon.model.ProductoProveedor;
import com.marathon.model.Proveedor;
import com.marathon.model.UnidadMedida;
import com.marathon.repository.CategoriaRepository;
import com.marathon.repository.ListaMaterialesRepository;
import com.marathon.repository.ProductoProveedorRepository;
import com.marathon.repository.ProductoRepository;
import com.marathon.repository.ProveedorRepository;
import com.marathon.repository.UnidadMedidaRepository;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoProveedorRepository productoProveedorRepository;
    private final ListaMaterialesRepository listaMaterialesRepository;

    private final LogService logService;

    public ProductoService(ProductoRepository productoRepository,
                           CategoriaRepository categoriaRepository,
                           UnidadMedidaRepository unidadMedidaRepository,
                           ProveedorRepository proveedorRepository,
                           ProductoProveedorRepository productoProveedorRepository,
                           ListaMaterialesRepository listaMaterialesRepository,
                       LogService logService) {
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.unidadMedidaRepository = unidadMedidaRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoProveedorRepository = productoProveedorRepository;
        this.listaMaterialesRepository = listaMaterialesRepository;
        this.logService = logService;
    }

    public PageResponseDTO<ProductoResponseDTO> listar(int page, int size, String nombre, String estado,
                                                       Integer idCategoria, String origen,
                                                       Integer idProveedor) {
        // F51 (D-41): el ORDEN es obligatorio en una lista paginada. Sin ORDER
        // BY, un UPDATE reescribe la fila al final del monton y la que acabas
        // de editar desaparece de su pagina.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "idProducto"));

        // F57: UNA consulta con todos los filtros opcionales, en vez de las
        // ocho ramas if/else que habia aqui. Aquella cadena solo cubria las
        // combinaciones previstas —y por eso el filtro por origen ya se habia
        // tenido que salir de ella por un lado—, asi que anadir un filtro mas
        // significaba duplicar otra vez las ramas. Ahora un filtro se anade
        // poniendo un parametro, no multiplicando caminos.
        Page<Producto> result = productoRepository.buscarConFiltros(
                Filtros.vacioComoNulo(nombre),
                Filtros.vacioComoNulo(estado),
                idCategoria,
                Filtros.vacioComoNulo(origen),
                idProveedor,
                pageable);

        // F85: el precio de compra de toda la pagina, de una consulta. Antes no
        // se pedia: se copiaba el precio de venta y se llamaba «precio de compra».
        List<Integer> ids = result.getContent().stream()
                .map(Producto::getIdProducto).collect(Collectors.toList());
        Map<Integer, BigDecimal> preciosCompra = preciosDeCompraDe(ids);

        List<ProductoResponseDTO> content = result.getContent().stream()
                .map(p -> toDTO(p, preciosCompra.get(p.getIdProducto())))
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public ProductoResponseDTO obtener(Integer id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
        List<ProductoProveedor> relaciones = productoProveedorRepository.findByProductoIdProducto(id);

        // F85: el precio de compra sale del proveedor principal; si ninguno esta
        // marcado como tal, del primero que tenga precio. Aqui las relaciones ya
        // estan cargadas, asi que no hace falta volver a la base.
        ProductoResponseDTO dto = toDTO(producto, relaciones.stream()
                .filter(pp -> pp.getPrecioCompra() != null && "activo".equals(pp.getEstado()))
                .sorted(Comparator.comparing(
                        pp -> !Boolean.TRUE.equals(pp.getEsProveedorPrincipal())))
                .map(ProductoProveedor::getPrecioCompra)
                .findFirst().orElse(null));

        List<ProveedorSimpleDTO> proveedores = relaciones.stream()
                .map(pp -> new ProveedorSimpleDTO(
                        pp.getProveedor().getIdProveedor(),
                        pp.getProveedor().getNombre(),
                        pp.getProveedor().getContacto()))
                .collect(Collectors.toList());
        dto.setProveedores(proveedores);
        return dto;
    }

    @Transactional
    public ProductoResponseDTO crear(ProductoRequestDTO reqDTO) {
        logService.fijarContextoUsuario();
        Producto producto = new Producto();
        mapFromDTO(producto, reqDTO);
        producto.setEstado(reqDTO.getEstado() != null ? reqDTO.getEstado() : "activo");
        producto = productoRepository.save(producto);

        guardarProveedores(producto, reqDTO.getProveedorIds(), reqDTO.getPrecioCompra());

        logService.registrarAccion("productos", "crear",
                "Producto #" + producto.getIdProducto() + " '" + producto.getNombre()
                + "' creado. Precio: $" + producto.getPrecio());

        return obtener(producto.getIdProducto());
    }

    @Transactional
    public ProductoResponseDTO actualizar(Integer id, ProductoRequestDTO reqDTO) {
        logService.fijarContextoUsuario();
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));

        mapFromDTO(producto, reqDTO);
        if (reqDTO.getEstado() != null) {
            producto.setEstado(reqDTO.getEstado());
        }
        try {
            productoRepository.saveAndFlush(producto);
        } catch (DataAccessException e) {
            throw traducirErrorOrigen(e);
        }

        productoProveedorRepository.deleteByProductoIdProducto(id);
        guardarProveedores(producto, reqDTO.getProveedorIds(), reqDTO.getPrecioCompra());

        // El detalle campo a campo (incluido el precio anterior) lo registra el
        // trigger trg_auditoria_producto en auditoria_cambios. Aqui queda la
        // entrada de alto nivel para la bitacora central.
        logService.registrarAccion("productos", "actualizar",
                "Producto #" + id + " '" + producto.getNombre() + "' modificado. Precio: $"
                + producto.getPrecio());

        return obtener(id);
    }

    /**
     * Cambia unicamente el origen del producto. El trigger de BD
     * (trg_validar_cambio_origen_producto) impide cambiar a 'comprado' un
     * producto con BOM activo; aqui se atrapa esa excepcion y se traduce a
     * un mensaje amigable.
     */
    @Transactional
    public ProductoResponseDTO cambiarOrigen(Integer id, String nuevoOrigen) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));

        if (nuevoOrigen == null || !(nuevoOrigen.equals("comprado") || nuevoOrigen.equals("fabricado"))) {
            throw new ValidationException("El origen debe ser 'comprado' o 'fabricado'");
        }

        producto.setOrigen(nuevoOrigen);
        try {
            productoRepository.saveAndFlush(producto);
        } catch (DataAccessException e) {
            throw traducirErrorOrigen(e);
        }
        return obtener(id);
    }

    private ValidationException traducirErrorOrigen(DataAccessException e) {
        String msg = e.getMostSpecificCause() != null
                ? e.getMostSpecificCause().getMessage() : e.getMessage();
        if (msg != null && msg.contains("lista de materiales activa")) {
            return new ValidationException(
                "No se puede cambiar el producto a comprado: tiene lista de materiales activa. "
                + "Elimine o desactive el BOM primero.");
        }
        return new ValidationException(msg != null ? msg : "Error al actualizar el origen del producto");
    }

    // L11 (D-16): SET LOCAL muere en el commit. Sin @Transactional,
    // fijarContextoUsuario() corria en su propia transaccion autocommit y ya no
    // estaba vigente cuando llegaba el UPDATE que dispara trg_auditoria_producto:
    // la traza quedaba sin autor. Lo dice el javadoc del propio metodo en
    // LogService, y el codigo lo incumplia.
    @Transactional
    public void eliminar(Integer id) {
        logService.fijarContextoUsuario();
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
        producto.setEstado("inactivo");
        productoRepository.save(producto);

        logService.registrarAccion("productos", "eliminar",
                "Producto #" + id + " '" + producto.getNombre() + "' dado de baja (estado=inactivo)");
    }

    private void mapFromDTO(Producto producto, ProductoRequestDTO dto) {
        producto.setNombre(dto.getNombre());
        producto.setDescripcion(dto.getDescripcion());
        producto.setPrecio(dto.getPrecioVenta());
        producto.setOrigen(dto.getOrigen() != null ? dto.getOrigen() : "comprado");

        Categoria categoria = categoriaRepository.findById(dto.getIdCategoria())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", dto.getIdCategoria()));
        producto.setCategoria(categoria);

        UnidadMedida unidad = unidadMedidaRepository.findById(dto.getIdUnidadMedida())
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida", dto.getIdUnidadMedida()));
        producto.setUnidadMedida(unidad);
    }

    /** El precio de compra de cada producto de la lista, en un mapa (F85). */
    private Map<Integer, BigDecimal> preciosDeCompraDe(List<Integer> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Integer, BigDecimal> precios = new LinkedHashMap<>();
        for (Object[] fila : productoProveedorRepository.preciosDeCompraDe(ids)) {
            // La consulta viene ordenada con el proveedor principal primero, asi
            // que el primero que llega de cada producto es el bueno.
            precios.putIfAbsent((Integer) fila[0], (BigDecimal) fila[1]);
        }
        return precios;
    }

    /**
     * Vincula proveedores al producto y guarda el precio de compra (F85).
     *
     * <p>Antes esto marcaba <b>siempre</b> {@code esProveedorPrincipal = false} y
     * nunca escribia {@code precioCompra}: ningun producto dado de alta por la
     * pantalla tenia proveedor principal ni precio de compra, aunque el formulario
     * obligara a escribir uno. El primero de la lista es el principal, que es lo
     * que la pantalla da a entender al ponerlo el primero.
     */
    private void guardarProveedores(Producto producto, List<Integer> proveedorIds,
                                    BigDecimal precioCompra) {
        if (proveedorIds == null || proveedorIds.isEmpty()) return;

        List<ProductoProveedor> relaciones = new ArrayList<>();
        boolean primero = true;
        for (Integer provId : proveedorIds) {
            Proveedor proveedor = proveedorRepository.findById(provId)
                    .orElseThrow(() -> new ResourceNotFoundException("Proveedor", provId));
            ProductoProveedor pp = new ProductoProveedor();
            pp.setProducto(producto);
            pp.setProveedor(proveedor);
            pp.setEsProveedorPrincipal(primero);
            // El precio escrito en el formulario es el del principal. A los
            // demas no se les inventa uno: se editan desde su propia ficha.
            if (primero) {
                pp.setPrecioCompra(precioCompra);
            }
            pp.setEstado("activo");
            relaciones.add(pp);
            primero = false;
        }
        productoProveedorRepository.saveAll(relaciones);
    }

    private ProductoResponseDTO toDTO(Producto producto, BigDecimal precioCompra) {
        ProductoResponseDTO dto = new ProductoResponseDTO();
        dto.setIdProducto(producto.getIdProducto());
        dto.setCodigo(String.format("PROD-%06d", producto.getIdProducto()));
        dto.setNombre(producto.getNombre());
        dto.setDescripcion(producto.getDescripcion());
        dto.setPrecioVenta(producto.getPrecio());
        // F85: aqui ponia producto.getPrecio() — el precio de VENTA— y la
        // pantalla lo enseñaba como precio de compra. El margen de cualquier
        // producto salia exactamente cero. Nulo si no hay proveedor con precio:
        // un producto fabricado no tiene precio de compra, y cero no es lo mismo.
        dto.setPrecioCompra(precioCompra);
        dto.setEstado(producto.getEstado());
        dto.setOrigen(producto.getOrigen());
        dto.setTieneBom(listaMaterialesRepository
                .existsByProductoIdProductoAndEstado(producto.getIdProducto(), "activo"));
        dto.setCreatedAt(producto.getCreatedAt());

        if (producto.getCategoria() != null) {
            dto.setIdCategoria(producto.getCategoria().getIdCategoria());
            dto.setCategoriaNombre(producto.getCategoria().getNombre());
        }
        if (producto.getUnidadMedida() != null) {
            dto.setIdUnidadMedida(producto.getUnidadMedida().getIdUnidadMedida());
            dto.setUnidadMedidaNombre(producto.getUnidadMedida().getNombre());
        }
        return dto;
    }
}
