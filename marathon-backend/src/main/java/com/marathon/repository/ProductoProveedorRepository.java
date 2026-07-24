package com.marathon.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.ProductoProveedor;

public interface ProductoProveedorRepository extends JpaRepository<ProductoProveedor, Integer> {

    List<ProductoProveedor> findByProductoIdProducto(Integer idProducto);

    void deleteByProductoIdProducto(Integer idProducto);

    // F29 — Costo promedio de compra (precio_compra) de los productos
    // 'comprado' de una categoría. Referencia de mercado para fabricar-vs-comprar.
    @Query("SELECT AVG(pp.precioCompra) FROM ProductoProveedor pp "
        + "WHERE pp.producto.categoria.idCategoria = :idCategoria "
        + "AND pp.producto.origen = 'comprado' AND pp.precioCompra IS NOT NULL")
    BigDecimal costoPromedioCompraPorCategoria(@Param("idCategoria") Integer idCategoria);
}
