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

    // F29 — Costo promedio de fabricación de las OP completadas de un producto.
    // F32 — Corregido a promedio PONDERADO por cantidad producida
    // (SUM(costo_total) / SUM(cantidad_producida)) en vez de un promedio simple
    // de costos unitarios, que se sesgaba con lotes de tamaños distintos.
    @Query("SELECT SUM(o.costoTotal) / SUM(o.cantidadProducida) FROM OrdenProduccion o "
        + "WHERE o.producto.idProducto = :idProducto AND o.estado = 'completada' "
        + "AND o.cantidadProducida > 0")
    BigDecimal costoPromedioFabricacion(@Param("idProducto") Integer idProducto);

    long countByProductoIdProductoAndEstado(Integer idProducto, String estado);

    // F29 — Costo promedio POR ORDEN de producción completada desde una fecha
    // (dashboard: mes actual). Aquí el promedio por orden es lo correcto:
    // responde "cuánto cuesta en promedio una orden", no un costo unitario.
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
