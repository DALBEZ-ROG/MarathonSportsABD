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
    // =====================================================================
    // F94 — DOS consultas, y por qué no una
    // =====================================================================
    // Había una sola, con la condición del texto anulable por `:texto IS NULL`.
    // Es cómodo de escribir y produce un plan malo en los dos casos, cada uno
    // por un motivo distinto. Medido sobre 1,5 millones de órdenes:
    //
    //   Forma            Sin filtro de texto   Con filtro que casa con mucho
    //   ---------------  -------------------   -----------------------------
    //   JOIN                       396 ms                 581 ms
    //   EXISTS                      42 ms              15.924 ms  (!)
    //   dos consultas               42 ms                 581 ms
    //
    // El JOIN se paga aunque no se busque nada, porque une igual. Y el EXISTS,
    // que arregla eso, se convierte en un subplan CORRELACIONADO: PostgreSQL
    // recorre las 1,5 millones de órdenes y por cada una consulta el proveedor.
    // Se ve en el plan: «Seq Scan on orden_compra / Filter: EXISTS(SubPlan 1)».
    //
    // Ninguna forma es buena para los dos casos, así que hay dos consultas y el
    // servicio elige. Es más código y es lo correcto: cada una recibe el plan
    // que le conviene — sin unión cuando no hay nada que unir, y un hash join
    // cuando de verdad hay que cruzar las dos tablas.

    /** Sin búsqueda por texto: no se nombra `proveedor`, así que no hay unión. */
    @Query("SELECT o FROM OrdenCompra o WHERE "
         + "(:estado IS NULL OR o.estado = :estado) "
         + "AND (:idProveedor IS NULL OR o.proveedor.idProveedor = :idProveedor) "
         + "AND (:numero IS NULL OR o.idOrdenCompra = :numero)")
    Page<OrdenCompra> buscarSinTexto(@Param("estado") String estado,
                                     @Param("idProveedor") Integer idProveedor,
                                     @Param("numero") Long numero,
                                     Pageable pageable);

    /** Con búsqueda por texto: unión explícita, que es lo que da el hash join. */
    @Query("SELECT o FROM OrdenCompra o JOIN o.proveedor pr WHERE "
         + "(:estado IS NULL OR o.estado = :estado) "
         + "AND (:idProveedor IS NULL OR pr.idProveedor = :idProveedor) "
         + "AND LOWER(pr.nombre) LIKE LOWER(CONCAT('%', CAST(:p1 AS string), '%')) "
         + "AND (:p2 IS NULL OR LOWER(pr.nombre) LIKE LOWER(CONCAT('%', CAST(:p2 AS string), '%'))) "
         + "AND (:p3 IS NULL OR LOWER(pr.nombre) LIKE LOWER(CONCAT('%', CAST(:p3 AS string), '%')))")
    Page<OrdenCompra> buscarConTexto(@Param("estado") String estado,
                                     @Param("idProveedor") Integer idProveedor,
                                     @Param("p1") String p1,
                                     @Param("p2") String p2,
                                     @Param("p3") String p3,
                                     Pageable pageable);
}
