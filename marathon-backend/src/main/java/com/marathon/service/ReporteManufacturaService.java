package com.marathon.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.marathon.dto.reporte.FiltroReporteDTO;
import com.marathon.dto.reporte.ReporteConsumoMateriaPrimaDTO;
import com.marathon.dto.reporte.ReporteEficienciaProduccionDTO;
import com.marathon.dto.reporte.ResumenManufacturaDTO;
import com.marathon.dto.reporte.ResumenManufacturaDTO.EstadoOrdenProduccionDTO;
import com.marathon.dto.reporte.ResumenManufacturaDTO.ProductoFabricadoTopDTO;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * F30 — Reportes de manufactura. Fase de SOLO LECTURA: únicamente
 * consultas agregadas (SELECT), sin escrituras sobre datos de negocio.
 */
@Service
public class ReporteManufacturaService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Consumo de materia prima por producción en el período.
     * Agrupa movimiento_materia_prima de tipo 'salida_produccion' y 'merma'.
     * El costo se valoriza con el snapshot del consumo de la OP cuando existe;
     * si no, con el costo promedio actual de la materia prima.
     */
    public List<ReporteConsumoMateriaPrimaDTO> consumoMateriaPrima(FiltroReporteDTO f) {
        StringBuilder sql = new StringBuilder(
            "SELECT mp.id_materia_prima, mp.nombre, um.nombre AS unidad, "
          + "       COALESCE(SUM(m.cantidad), 0) AS cantidad_total, "
          + "       COALESCE(SUM(m.cantidad * COALESCE(c.costo_unitario_snapshot, mp.costo_unitario_promedio)), 0) AS costo_total, "
          + "       COUNT(DISTINCT m.id_orden_produccion) AS num_ordenes "
          + "FROM movimiento_materia_prima m "
          + "JOIN materia_prima mp ON mp.id_materia_prima = m.id_materia_prima "
          + "LEFT JOIN unidad_medida um ON um.id_unidad_medida = mp.id_unidad_medida "
          + "LEFT JOIN orden_produccion_consumo c "
          + "       ON c.id_orden_produccion = m.id_orden_produccion "
          + "      AND c.id_materia_prima = m.id_materia_prima "
          + "WHERE m.tipo_movimiento IN ('salida_produccion','merma') ");
        if (f.getDesde() != null) {
            sql.append("AND m.fecha >= :desde ");
        }
        if (f.getHasta() != null) {
            sql.append("AND m.fecha <= :hasta ");
        }
        if (f.getIdMateriaPrima() != null) {
            sql.append("AND mp.id_materia_prima = :idMateriaPrima ");
        }
        sql.append("GROUP BY mp.id_materia_prima, mp.nombre, um.nombre ")
           .append("ORDER BY cantidad_total DESC");

        Query q = entityManager.createNativeQuery(sql.toString());
        if (f.getDesde() != null) {
            q.setParameter("desde", f.getDesde());
        }
        if (f.getHasta() != null) {
            q.setParameter("hasta", f.getHasta());
        }
        if (f.getIdMateriaPrima() != null) {
            q.setParameter("idMateriaPrima", f.getIdMateriaPrima());
        }
        q.setMaxResults(f.getLimiteEfectivo());

        List<ReporteConsumoMateriaPrimaDTO> resultado = new ArrayList<>();
        for (Object row : q.getResultList()) {
            Object[] r = (Object[]) row;
            resultado.add(new ReporteConsumoMateriaPrimaDTO(
                    toInt(r[0]),
                    (String) r[1],
                    (String) r[2],
                    toBig(r[3]).setScale(3, RoundingMode.HALF_UP),
                    toBig(r[4]).setScale(2, RoundingMode.HALF_UP),
                    toLong(r[5])));
        }
        return resultado;
    }

    /**
     * Eficiencia de producción por orden completada en el período:
     * producidas/planificadas (%) y suma de mermas POSITIVAS de sus consumos.
     */
    public List<ReporteEficienciaProduccionDTO> eficienciaProduccion(FiltroReporteDTO f) {
        StringBuilder sql = new StringBuilder(
            "SELECT o.id_orden_produccion, p.nombre, o.cantidad_planificada, o.cantidad_producida, "
          + "       o.costo_total, o.costo_unitario_producido, o.fecha_fin, "
          + "       COALESCE((SELECT SUM(c.merma) FROM orden_produccion_consumo c "
          + "                  WHERE c.id_orden_produccion = o.id_orden_produccion AND c.merma > 0), 0) AS merma_total "
          + "FROM orden_produccion o "
          + "JOIN producto p ON p.id_producto = o.id_producto "
          + "WHERE o.estado = 'completada' ");
        if (f.getDesde() != null) {
            sql.append("AND o.fecha_fin >= :desde ");
        }
        if (f.getHasta() != null) {
            sql.append("AND o.fecha_fin <= :hasta ");
        }
        if (f.getIdCategoria() != null) {
            sql.append("AND p.id_categoria = :idCategoria ");
        }
        if (f.getIdProducto() != null) {
            sql.append("AND p.id_producto = :idProducto ");
        }
        sql.append("ORDER BY o.fecha_fin DESC");

        Query q = entityManager.createNativeQuery(sql.toString());
        if (f.getDesde() != null) {
            q.setParameter("desde", f.getDesde());
        }
        if (f.getHasta() != null) {
            q.setParameter("hasta", f.getHasta());
        }
        if (f.getIdCategoria() != null) {
            q.setParameter("idCategoria", f.getIdCategoria());
        }
        if (f.getIdProducto() != null) {
            q.setParameter("idProducto", f.getIdProducto());
        }
        q.setMaxResults(f.getLimiteEfectivo());

        List<ReporteEficienciaProduccionDTO> resultado = new ArrayList<>();
        for (Object row : q.getResultList()) {
            Object[] r = (Object[]) row;
            ReporteEficienciaProduccionDTO d = new ReporteEficienciaProduccionDTO();
            d.setIdOrdenProduccion(toInt(r[0]));
            d.setProducto((String) r[1]);
            Integer plan = toInt(r[2]);
            Integer prod = toInt(r[3]);
            d.setCantidadPlanificada(plan);
            d.setCantidadProducida(prod);
            if (plan != null && plan > 0 && prod != null) {
                d.setEficienciaProduccion(BigDecimal.valueOf(prod)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(plan), 2, RoundingMode.HALF_UP));
            } else {
                d.setEficienciaProduccion(BigDecimal.ZERO);
            }
            d.setCostoTotal(toBig(r[4]).setScale(2, RoundingMode.HALF_UP));
            d.setCostoUnitario(toBig(r[5]).setScale(4, RoundingMode.HALF_UP));
            if (r[6] instanceof java.sql.Timestamp ts) {
                d.setFechaFin(ts.toLocalDateTime().toLocalDate());
            } else if (r[6] instanceof LocalDateTime ldt) {
                d.setFechaFin(ldt.toLocalDate());
            }
            d.setMermaTotalMateriaPrima(toBig(r[7]).setScale(3, RoundingMode.HALF_UP));
            resultado.add(d);
        }
        return resultado;
    }

    /** Resumen para el dashboard de manufactura. */
    public ResumenManufacturaDTO resumenManufactura() {
        LocalDateTime inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        ResumenManufacturaDTO dto = new ResumenManufacturaDTO();

        dto.setOrdenesPlanificadas(contar("SELECT COUNT(*) FROM orden_produccion WHERE estado = 'planificada'", null));
        dto.setOrdenesEnProceso(contar("SELECT COUNT(*) FROM orden_produccion WHERE estado = 'en_proceso'", null));
        dto.setOrdenesCompletadasMes(contar(
                "SELECT COUNT(*) FROM orden_produccion WHERE estado = 'completada' AND fecha_fin >= :desde", inicioMes));
        dto.setUnidadesProducidasMes(contar(
                "SELECT COALESCE(SUM(cantidad_producida), 0) FROM orden_produccion "
              + "WHERE estado = 'completada' AND fecha_fin >= :desde", inicioMes));

        Object costo = entityManager.createNativeQuery(
                "SELECT COALESCE(SUM(costo_total), 0) FROM orden_produccion "
              + "WHERE estado = 'completada' AND fecha_fin >= :desde")
                .setParameter("desde", inicioMes).getSingleResult();
        dto.setCostoProduccionMes(toBig(costo).setScale(2, RoundingMode.HALF_UP));

        // Merma promedio % del mes = SUM(mermas positivas) / SUM(teórico) × 100
        Object merma = entityManager.createNativeQuery(
                "SELECT CASE WHEN COALESCE(SUM(c.cantidad_teorica), 0) = 0 THEN 0 "
              + "            ELSE ROUND(COALESCE(SUM(CASE WHEN c.merma > 0 THEN c.merma ELSE 0 END), 0) "
              + "                       * 100.0 / SUM(c.cantidad_teorica), 2) END "
              + "FROM orden_produccion_consumo c JOIN orden_produccion o "
              + "  ON o.id_orden_produccion = c.id_orden_produccion "
              + "WHERE o.estado = 'completada' AND o.fecha_fin >= :desde")
                .setParameter("desde", inicioMes).getSingleResult();
        dto.setMermaPromedioMes(toBig(merma).setScale(2, RoundingMode.HALF_UP));

        dto.setMateriaPrimaBajoMinimo(contar(
                "SELECT COUNT(*) FROM materia_prima WHERE stock_minimo > 0 AND stock_actual <= stock_minimo", null));

        // Top 3 productos fabricados del mes
        @SuppressWarnings("unchecked")
        List<Object[]> top = entityManager.createNativeQuery(
                "SELECT p.id_producto, p.nombre, COALESCE(SUM(o.cantidad_producida), 0) AS unidades "
              + "FROM orden_produccion o JOIN producto p ON p.id_producto = o.id_producto "
              + "WHERE o.estado = 'completada' AND o.fecha_fin >= :desde "
              + "GROUP BY p.id_producto, p.nombre ORDER BY unidades DESC LIMIT 3")
                .setParameter("desde", inicioMes).getResultList();
        List<ProductoFabricadoTopDTO> topDto = new ArrayList<>();
        for (Object[] r : top) {
            topDto.add(new ProductoFabricadoTopDTO(toInt(r[0]), (String) r[1], toLong(r[2])));
        }
        dto.setTop3ProductosFabricados(topDto);

        // Distribución de OP por estado (histórico completo)
        @SuppressWarnings("unchecked")
        List<Object[]> estados = entityManager.createNativeQuery(
                "SELECT estado, COUNT(*) FROM orden_produccion GROUP BY estado ORDER BY estado")
                .getResultList();
        List<EstadoOrdenProduccionDTO> estadosDto = new ArrayList<>();
        for (Object[] r : estados) {
            estadosDto.add(new EstadoOrdenProduccionDTO((String) r[0], toLong(r[1])));
        }
        dto.setOrdenesPorEstado(estadosDto);

        return dto;
    }

    // ===================== helpers =====================
    private Long contar(String sql, LocalDateTime desde) {
        Query q = entityManager.createNativeQuery(sql);
        if (desde != null) {
            q.setParameter("desde", desde);
        }
        return toLong(q.getSingleResult());
    }

    private static BigDecimal toBig(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        return new BigDecimal(o.toString());
    }

    private static Long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return Long.valueOf(o.toString());
    }

    private static Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        return Integer.valueOf(o.toString());
    }
}
