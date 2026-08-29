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

    /**
     * El precio de compra de cada producto de una pagina, de una sola consulta (F85).
     *
     * <p>Existe porque la pantalla de productos venia enseñando como «precio de
     * compra» el <b>precio de venta</b>: {@code toDTO} hacia
     * {@code dto.setPrecioCompra(producto.getPrecio())}. El precio de compra de
     * verdad estaba aqui —105 de 106 vinculos lo tienen— y nadie lo miraba, asi
     * que el margen de cualquier producto salia en cero.
     *
     * <p>Devuelve pares {@code [idProducto, precioCompra]}. Se pide para toda la
     * pagina de golpe: uno por producto seria un N+1 de los de manual.
     */
    @Query("SELECT pp.producto.idProducto, pp.precioCompra FROM ProductoProveedor pp "
        + "WHERE pp.producto.idProducto IN :ids AND pp.precioCompra IS NOT NULL "
        + "AND pp.estado = 'activo' "
        + "ORDER BY CASE WHEN pp.esProveedorPrincipal = true THEN 0 ELSE 1 END, "
        + "         pp.idProductoProveedor")
    List<Object[]> preciosDeCompraDe(@Param("ids") List<Integer> ids);

    // F29 — Costo promedio de compra (precio_compra) de los productos
    // 'comprado' de una categoría. Referencia de mercado para fabricar-vs-comprar.
    @Query("SELECT AVG(pp.precioCompra) FROM ProductoProveedor pp "
        + "WHERE pp.producto.categoria.idCategoria = :idCategoria "
        + "AND pp.producto.origen = 'comprado' AND pp.precioCompra IS NOT NULL")
    BigDecimal costoPromedioCompraPorCategoria(@Param("idCategoria") Integer idCategoria);
}
