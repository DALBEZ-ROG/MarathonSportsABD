package com.marathon.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.produccion.AnalisisFabricarVsComprarDTO;
import com.marathon.dto.produccion.CostoProductoFabricadoDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.model.Producto;
import com.marathon.repository.OrdenProduccionRepository;
import com.marathon.repository.ProductoProveedorRepository;
import com.marathon.repository.ProductoRepository;

@Service
public class AnalisisCostoService {

    private final ProductoRepository productoRepository;
    private final OrdenProduccionRepository ordenProduccionRepository;
    private final ProductoProveedorRepository productoProveedorRepository;

    public AnalisisCostoService(ProductoRepository productoRepository,
                                OrdenProduccionRepository ordenProduccionRepository,
                                ProductoProveedorRepository productoProveedorRepository) {
        this.productoRepository = productoRepository;
        this.ordenProduccionRepository = ordenProduccionRepository;
        this.productoProveedorRepository = productoProveedorRepository;
    }

    public AnalisisFabricarVsComprarDTO analizarFabricarVsComprar(Integer idProducto) {
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", idProducto));

        AnalisisFabricarVsComprarDTO dto = new AnalisisFabricarVsComprarDTO();
        dto.setIdProducto(producto.getIdProducto());
        dto.setNombreProducto(producto.getNombre());
        Integer idCategoria = null;
        if (producto.getCategoria() != null) {
            dto.setCategoria(producto.getCategoria().getNombre());
            idCategoria = producto.getCategoria().getIdCategoria();
        }

        BigDecimal costoFab = ordenProduccionRepository.costoPromedioFabricacion(idProducto);
        if (costoFab != null) costoFab = costoFab.setScale(4, RoundingMode.HALF_UP);
        dto.setCostoPromedioFabricacion(costoFab);
        dto.setOrdenesCompletadas((int) ordenProduccionRepository
                .countByProductoIdProductoAndEstado(idProducto, "completada"));

        BigDecimal costoCompra = idCategoria != null
                ? productoProveedorRepository.costoPromedioCompraPorCategoria(idCategoria) : null;
        if (costoCompra != null) costoCompra = costoCompra.setScale(4, RoundingMode.HALF_UP);
        dto.setCostoPromedioCompraCategoria(costoCompra);

        if (costoFab == null || costoCompra == null) {
            dto.setDiferencia(null);
            dto.setConclusion("Sin datos suficientes para comparar");
        } else {
            BigDecimal diff = costoFab.subtract(costoCompra).setScale(4, RoundingMode.HALF_UP);
            dto.setDiferencia(diff);
            if (diff.compareTo(BigDecimal.ZERO) < 0) {
                dto.setConclusion("Fabricar es más económico");
            } else if (diff.compareTo(BigDecimal.ZERO) > 0) {
                dto.setConclusion("Comprar sería más económico");
            } else {
                dto.setConclusion("Fabricar y comprar cuestan lo mismo");
            }
        }
        return dto;
    }

    public PageResponseDTO<CostoProductoFabricadoDTO> listarCostosPorProducto(int page, int size) {
        // F51 (D-41): el ORDEN es obligatorio en una lista paginada.
        // Sin ORDER BY, PostgreSQL devuelve las filas en el orden del monton, y
        // un UPDATE reescribe la fila al final: la que acabas de editar
        // desaparece de su pagina. Ademas, dos paginas consecutivas pueden
        // repetir una fila y esconder otra.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "idProducto"));
        Page<Producto> result = productoRepository.findByOrigen("fabricado", pageable);

        List<CostoProductoFabricadoDTO> content = result.getContent().stream().map(p -> {
            CostoProductoFabricadoDTO d = new CostoProductoFabricadoDTO();
            d.setIdProducto(p.getIdProducto());
            d.setNombreProducto(p.getNombre());
            d.setCategoria(p.getCategoria() != null ? p.getCategoria().getNombre() : null);
            BigDecimal costoFab = ordenProduccionRepository.costoPromedioFabricacion(p.getIdProducto());
            if (costoFab != null) costoFab = costoFab.setScale(4, RoundingMode.HALF_UP);
            d.setCostoPromedioFabricacion(costoFab);
            BigDecimal precio = p.getPrecio() != null ? p.getPrecio() : BigDecimal.ZERO;
            d.setPrecioVenta(precio);
            d.setMargen(costoFab != null ? precio.subtract(costoFab).setScale(2, RoundingMode.HALF_UP) : null);
            d.setOrdenesCompletadas((int) ordenProduccionRepository
                    .countByProductoIdProductoAndEstado(p.getIdProducto(), "completada"));
            return d;
        }).collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }
}
