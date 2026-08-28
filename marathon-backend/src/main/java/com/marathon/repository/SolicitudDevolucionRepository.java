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
         + "AND (:idPedido IS NULL OR s.pedido.idPedido = :idPedido) "
         + "AND (:texto IS NULL "
         + "     OR CAST(s.idSolicitud AS string) LIKE CONCAT('%', CAST(:texto AS string), '%') "
         + "     OR CAST(s.pedido.idPedido AS string) LIKE CONCAT('%', CAST(:texto AS string), '%') "
         + "     OR LOWER(s.pedido.cliente.nombre) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')) "
         + "     OR LOWER(s.pedido.cliente.apellido) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')))")
    Page<SolicitudDevolucion> buscar(@Param("estado") String estado,
                                     @Param("idPedido") Integer idPedido,
                                     @Param("texto") String texto,
                                     Pageable pageable);
}
