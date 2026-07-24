package com.marathon.repository;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.OrdenProduccion;

public interface OrdenProduccionRepository extends JpaRepository<OrdenProduccion, Integer> {

    @Query("SELECT o FROM OrdenProduccion o WHERE "
        + "(:estado IS NULL OR o.estado = :estado) AND "
        + "(:idProducto IS NULL OR o.producto.idProducto = :idProducto)")
    Page<OrdenProduccion> buscar(@Param("estado") String estado,
                                 @Param("idProducto") Integer idProducto,
                                 Pageable pageable);

    long countByEstado(String estado);

    // F29 — Costo promedio de fabricación (avg de costo_unitario_producido) de
    // las OP completadas de un producto. NULL si no hay ninguna.
    @Query("SELECT AVG(o.costoUnitarioProducido) FROM OrdenProduccion o "
        + "WHERE o.producto.idProducto = :idProducto AND o.estado = 'completada' "
        + "AND o.cantidadProducida > 0")
    BigDecimal costoPromedioFabricacion(@Param("idProducto") Integer idProducto);

    long countByProductoIdProductoAndEstado(Integer idProducto, String estado);

    // F29 — Costo promedio de producción (avg costo_total) de las OP completadas
    // desde una fecha (para el dashboard: mes actual).
    @Query("SELECT AVG(o.costoTotal) FROM OrdenProduccion o "
        + "WHERE o.estado = 'completada' AND o.fechaFin >= :desde")
    BigDecimal costoPromedioProduccionDesde(@Param("desde") java.time.LocalDateTime desde);

    // F29 — OP completadas dentro de un rango de fechas (para reportes)
    @Query("SELECT o FROM OrdenProduccion o WHERE o.estado = 'completada' "
        + "AND (:idProducto IS NULL OR o.producto.idProducto = :idProducto) "
        + "AND (CAST(:desde AS timestamp) IS NULL OR o.fechaFin >= :desde) "
        + "AND (CAST(:hasta AS timestamp) IS NULL OR o.fechaFin <= :hasta) "
        + "ORDER BY o.fechaFin DESC")
    java.util.List<OrdenProduccion> reporteCostos(@Param("desde") java.time.LocalDateTime desde,
                                                  @Param("hasta") java.time.LocalDateTime hasta,
                                                  @Param("idProducto") Integer idProducto);
}
