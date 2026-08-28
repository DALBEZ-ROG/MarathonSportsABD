package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.SolicitudDevolucionDetalle;

public interface SolicitudDevolucionDetalleRepository extends JpaRepository<SolicitudDevolucionDetalle, Integer> {

    List<SolicitudDevolucionDetalle> findBySolicitudIdSolicitud(Integer idSolicitud);

    /**
     * Unidades de una linea de pedido que YA se han pedido devolver en
     * solicitudes anteriores.
     *
     * <p>Existe porque la comprobacion de "no devuelvas mas de lo que
     * compraste" se hacia solicitud a solicitud: dos solicitudes seguidas sobre
     * el mismo pedido podian devolver cada una las 10 unidades de una linea de
     * 10, y las dos pasaban. Lo que hay que comparar contra lo comprado es el
     * acumulado, no la solicitud de turno.
     *
     * <p>Las {@code rechazada} no cuentan, y es a proposito: una solicitud que
     * se rechazo no se llevo mercancia, asi que no debe gastar el cupo de
     * devolucion de esa linea. Los otros tres estados del CHECK
     * ({@code solicitada}, {@code en_inspeccion}, {@code completada}) si
     * cuentan: en los tres hay unidades comprometidas o ya devueltas.
     */
    @Query("SELECT COALESCE(SUM(d.cantidadDevuelta), 0) FROM SolicitudDevolucionDetalle d "
         + "WHERE d.detallePedido.idDetalle = :idDetallePedido "
         + "AND d.solicitud.estado <> 'rechazada'")
    int devueltoAcumuladoDe(@Param("idDetallePedido") Integer idDetallePedido);
}
