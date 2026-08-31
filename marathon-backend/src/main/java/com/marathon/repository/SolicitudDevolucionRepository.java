package com.marathon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.SolicitudDevolucion;

public interface SolicitudDevolucionRepository extends JpaRepository<SolicitudDevolucion, Integer> {

    Page<SolicitudDevolucion> findByEstado(String estado, Pageable pageable);

    Page<SolicitudDevolucion> findByPedidoIdPedido(Integer idPedido, Pageable pageable);

    Page<SolicitudDevolucion> findByEstadoAndPedidoIdPedido(String estado, Integer idPedido, Pageable pageable);

    long countByEstado(String estado);

    /** Listado con filtros y busqueda por numero de solicitud, pedido o cliente (F54). */
    @Query("SELECT s FROM SolicitudDevolucion s WHERE "
         + "(:estado IS NULL OR s.estado = :estado) "
         // `s.pedido.idPedido` es la CLAVE AJENA de solicitud_devolucion, no una
         // columna de pedido: Hibernate la lee de la propia fila y no necesita
         // join. Se deja tal cual — lo que hay que evitar es nombrar columnas
         // que vivan en la otra tabla, como `s.pedido.cliente.nombre`.
         + "AND (:idPedido IS NULL OR s.pedido.idPedido = :idPedido) "
         + "AND (:numero IS NULL OR s.idSolicitud = :numero OR s.pedido.idPedido = :numero)")
    Page<SolicitudDevolucion> buscarSinTexto(@Param("estado") String estado,
                                             @Param("idPedido") Integer idPedido,
                                             @Param("numero") Long numero,
                                             Pageable pageable);

    // F94 — la variante con texto une pedido y cliente de forma explícita.
    // Ver la nota larga en OrdenCompraRepository.
    @Query("SELECT s FROM SolicitudDevolucion s JOIN s.pedido p JOIN p.cliente c WHERE "
         + "(:estado IS NULL OR s.estado = :estado) "
         + "AND (:idPedido IS NULL OR p.idPedido = :idPedido) "
         // F94d — por palabras, sobre nombre o apellido. Ver PedidoRepository.
         + "AND (LOWER(c.nombre) LIKE LOWER(CONCAT('%', CAST(:p1 AS string), '%')) "
         + "  OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', CAST(:p1 AS string), '%'))) "
         + "AND (:p2 IS NULL "
         + "  OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', CAST(:p2 AS string), '%')) "
         + "  OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', CAST(:p2 AS string), '%'))) "
         + "AND (:p3 IS NULL "
         + "  OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', CAST(:p3 AS string), '%')) "
         + "  OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', CAST(:p3 AS string), '%')))")
    Page<SolicitudDevolucion> buscarConTexto(@Param("estado") String estado,
                                             @Param("idPedido") Integer idPedido,
                                             @Param("p1") String p1,
                                             @Param("p2") String p2,
                                             @Param("p3") String p3,
                                             Pageable pageable);
}
