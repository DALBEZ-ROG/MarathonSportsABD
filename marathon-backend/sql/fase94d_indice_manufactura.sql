-- =============================================================================
-- Fase 94d — El tablero de manufactura
-- =============================================================================
-- `/dashboard/manufactura` lanza una decena de agregados sobre orden_produccion,
-- casi todos de la forma
--     WHERE estado = 'completada' AND fecha_fin >= <inicio de mes>
-- Cada uno tardaba ~95 ms sin índice por esa pareja de columnas, y sumados
-- pasaban del segundo.
--
-- El orden importa: (estado, fecha_fin) permite ir directo al bloque del estado
-- pedido y avanzar por fecha dentro de él. Al revés habría que recorrer todas
-- las fechas filtrando por estado.
-- =============================================================================
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orden_produccion_estado_fecha_fin
    ON orden_produccion (estado, fecha_fin);
ANALYZE orden_produccion;
