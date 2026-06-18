package com.marathon.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.producto.ProductoRequestDTO;
import com.marathon.dto.producto.ProductoResponseDTO;
import com.marathon.dto.producto.ProductoResponseDTO.ProveedorSimpleDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.model.Categoria;
import com.marathon.model.Producto;
import com.marathon.model.ProductoProveedor;
import com.marathon.model.Proveedor;
import com.marathon.model.UnidadMedida;
import com.marathon.repository.CategoriaRepository;
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

    public ProductoService(ProductoRepository productoRepository,
                           CategoriaRepository categoriaRepository,
                           UnidadMedidaRepository unidadMedidaRepository,
                           ProveedorRepository proveedorRepository,
                           ProductoProveedorRepository productoProveedorRepository) {
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.unidadMedidaRepository = unidadMedidaRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoProveedorRepository = productoProveedorRepository;
    }

    public PageResponseDTO<ProductoResponseDTO> listar(int page, int size, String nombre, String estado, Integer idCategoria) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Producto> result;

        boolean hasNombre = nombre != null && !nombre.isEmpty();
        boolean hasEstado = estado != null && !estado.isEmpty();
        boolean hasCategoria = idCategoria != null;

        if (hasNombre && hasEstado && hasCategoria) {
            result = productoRepository.findByNombreContainingIgnoreCaseAndEstadoAndCategoriaIdCategoria(nombre, estado, idCategoria, pageable);
        } else if (hasNombre && hasCategoria) {
            result = productoRepository.findByNombreContainingIgnoreCaseAndCategoriaIdCategoria(nombre, idCategoria, pageable);
        } else if (hasEstado && hasCategoria) {
            result = productoRepository.findByEstadoAndCategoriaIdCategoria(estado, idCategoria, pageable);
        } else if (hasNombre && hasEstado) {
            result = productoRepository.findByNombreContainingIgnoreCaseAndEstado(nombre, estado, pageable);
        } else if (hasNombre) {
            result = productoRepository.findByNombreContainingIgnoreCase(nombre, pageable);
        } else if (hasEstado) {
            result = productoRepository.findByEstado(estado, pageable);
        } else if (hasCategoria) {
            result = productoRepository.findByCategoriaIdCategoria(idCategoria, pageable);
        } else {
            result = productoRepository.findAll(pageable);
        }

        List<ProductoResponseDTO> content = result.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public ProductoResponseDTO obtener(Integer id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
        ProductoResponseDTO dto = toDTO(producto);
        List<ProductoProveedor> relaciones = productoProveedorRepository.findByProductoIdProducto(id);
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
        Producto producto = new Producto();
        mapFromDTO(producto, reqDTO);
        producto.setEstado(reqDTO.getEstado() != null ? reqDTO.getEstado() : "activo");
        producto = productoRepository.save(producto);

        guardarProveedores(producto, reqDTO.getProveedorIds());

        return obtener(producto.getIdProducto());
    }

    @Transactional
    public ProductoResponseDTO actualizar(Integer id, ProductoRequestDTO reqDTO) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));

        mapFromDTO(producto, reqDTO);
        if (reqDTO.getEstado() != null) {
            producto.setEstado(reqDTO.getEstado());
        }
        productoRepository.save(producto);

        productoProveedorRepository.deleteByProductoIdProducto(id);
        guardarProveedores(producto, reqDTO.getProveedorIds());

        return obtener(id);
    }

    public void eliminar(Integer id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
        producto.setEstado("inactivo");
        productoRepository.save(producto);
    }

    private void mapFromDTO(Producto producto, ProductoRequestDTO dto) {
        producto.setNombre(dto.getNombre());
        producto.setDescripcion(dto.getDescripcion());
        producto.setPrecio(dto.getPrecioVenta());

        Categoria categoria = categoriaRepository.findById(dto.getIdCategoria())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", dto.getIdCategoria()));
        producto.setCategoria(categoria);

        UnidadMedida unidad = unidadMedidaRepository.findById(dto.getIdUnidadMedida())
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida", dto.getIdUnidadMedida()));
        producto.setUnidadMedida(unidad);
    }

    private void guardarProveedores(Producto producto, List<Integer> proveedorIds) {
        if (proveedorIds == null || proveedorIds.isEmpty()) return;

        List<ProductoProveedor> relaciones = new ArrayList<>();
        for (Integer provId : proveedorIds) {
            Proveedor proveedor = proveedorRepository.findById(provId)
                    .orElseThrow(() -> new ResourceNotFoundException("Proveedor", provId));
            ProductoProveedor pp = new ProductoProveedor();
            pp.setProducto(producto);
            pp.setProveedor(proveedor);
            pp.setEsProveedorPrincipal(false);
            pp.setEstado("activo");
            relaciones.add(pp);
        }
        productoProveedorRepository.saveAll(relaciones);
    }

    private ProductoResponseDTO toDTO(Producto producto) {
        ProductoResponseDTO dto = new ProductoResponseDTO();
        dto.setIdProducto(producto.getIdProducto());
        dto.setNombre(producto.getNombre());
        dto.setDescripcion(producto.getDescripcion());
        dto.setPrecioVenta(producto.getPrecio());
        dto.setPrecioCompra(producto.getPrecio());
        dto.setEstado(producto.getEstado());
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
