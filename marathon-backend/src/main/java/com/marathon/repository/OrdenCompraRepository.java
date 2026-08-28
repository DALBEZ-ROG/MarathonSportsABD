package com.marathon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.OrdenCompra;

public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Integer> {

    Page<OrdenCompra> findByEstado(String estado, Pageable pageable);

    Page<OrdenCompra> findByProveedorIdProveedor(Integer idProveedor, Pageable pageable);

    Page<OrdenCompra> findByEstadoAndProveedorIdProveedor(String estado, Integer idProveedor, Pageable pageable);

    long countByEstado(String estado);

    /**
     * Listado con todos los filtros y BUSQUEDA POR TEXTO en una sola consulta
     * (F54). Antes eran cuatro ramas if/else sin buscador: 2.668 ordenes en 267
     * paginas de diez, y para encontrar una habia que pasarlas.
     *
     * <p>El texto busca por numero de orden y por nombre del proveedor.
     */
    @Query("SELECT o FROM OrdenCompra o WHERE "
         + "(:estado IS NULL OR o.estado = :estado) "
         + "AND (:idProveedor IS NULL OR o.proveedor.idProveedor = :idProveedor) "
         + "AND (:texto IS NULL "
         + "     OR CAST(o.idOrdenCompra AS string) LIKE CONCAT('%', CAST(:texto AS string), '%') "
         + "     OR LOWER(o.proveedor.nombre) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')))")
    Page<OrdenCompra> buscar(@Param("estado") String estado,
                             @Param("idProveedor") Integer idProveedor,
                             @Param("texto") String texto,
                             Pageable pageable);
}
