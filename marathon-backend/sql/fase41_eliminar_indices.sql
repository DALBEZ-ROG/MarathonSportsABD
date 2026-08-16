-- ============================================================================
-- FASE 41 · ETAPA 1 — ELIMINACION DE LOS 4 INDICES RECOMENDADOS EN LA F39
-- ----------------------------------------------------------------------------
-- La F39 recomendo eliminarlos con evidencia, no por corazonada: los cuatro
-- estaban en idx_scan = 0 tras el poblado a 1.000.000 de filas y las 18
-- consultas del catalogo, y el estudio de ROLLBACK demostro que ninguna
-- consulta real los elegia. Un indice que nadie lee no es gratis: se mantiene
-- en cada INSERT, UPDATE y DELETE de su tabla, ocupa disco y entra en cada
-- respaldo.
--
-- LECTURA ANTES DE BORRAR (capturada con pg_get_userbyid/pg_get_indexdef el
-- 15/08/2026, justo antes de ejecutar este script):
--
--   indice              tabla                   tamano   idx_scan
--   ------------------  ----------------------  -------  --------
--   idx_oc_estado       orden_compra              64 kB         0
--   idx_fc_vencimiento  factura_compra            56 kB         0
--   idx_dp_estado       devolucion_proveedor      16 kB         0
--   idx_sd_estado       solicitud_devolucion      16 kB         0
--
-- USO:
--   psql -U postgres -d mod_venta_inve -f fase41_eliminar_indices.sql
-- ============================================================================

\timing off
\pset pager off

\echo '=== ANTES: definicion exacta de los cuatro indices ==========='
SELECT indexname, indexdef
FROM pg_indexes
WHERE indexname IN ('idx_oc_estado','idx_fc_vencimiento','idx_dp_estado','idx_sd_estado')
ORDER BY indexname;

\echo '=== ANTES: uso acumulado (debe ser 0 en los cuatro) =========='
SELECT indexrelname AS indice, idx_scan AS lecturas,
       pg_size_pretty(pg_relation_size(indexrelid)) AS tamano
FROM pg_stat_user_indexes
WHERE indexrelname IN ('idx_oc_estado','idx_fc_vencimiento','idx_dp_estado','idx_sd_estado')
ORDER BY indexrelname;

BEGIN;

DROP INDEX IF EXISTS idx_oc_estado;
DROP INDEX IF EXISTS idx_fc_vencimiento;
DROP INDEX IF EXISTS idx_dp_estado;
DROP INDEX IF EXISTS idx_sd_estado;

COMMIT;

\echo '=== DESPUES: no debe quedar ninguno =========================='
SELECT count(*) AS indices_restantes
FROM pg_indexes
WHERE indexname IN ('idx_oc_estado','idx_fc_vencimiento','idx_dp_estado','idx_sd_estado');

\echo '=== Indices totales del esquema public ======================='
SELECT count(*) AS indices_public FROM pg_indexes WHERE schemaname='public';


-- ============================================================================
-- REVERSION
-- ----------------------------------------------------------------------------
-- Definiciones EXACTAS obtenidas de pg_get_indexdef() ANTES del borrado. Se
-- dejan aqui, en el mismo archivo que el DROP, y no en un documento aparte, a
-- proposito: quien tenga que revertir esto a las tres de la manana va a abrir
-- el script que hizo el dano, no el manual.
--
-- Descomentar y ejecutar para volver al estado previo:
--
-- CREATE INDEX idx_oc_estado ON public.orden_compra USING btree (estado);
-- CREATE INDEX idx_fc_vencimiento ON public.factura_compra USING btree (fecha_vencimiento);
-- CREATE INDEX idx_dp_estado ON public.devolucion_proveedor USING btree (estado);
-- CREATE INDEX idx_sd_estado ON public.solicitud_devolucion USING btree (estado);
--
-- Con la base en caliente conviene CREATE INDEX CONCURRENTLY (fuera de
-- transaccion), que no bloquea escrituras:
--
-- CREATE INDEX CONCURRENTLY idx_oc_estado ON public.orden_compra USING btree (estado);
-- CREATE INDEX CONCURRENTLY idx_fc_vencimiento ON public.factura_compra USING btree (fecha_vencimiento);
-- CREATE INDEX CONCURRENTLY idx_dp_estado ON public.devolucion_proveedor USING btree (estado);
-- CREATE INDEX CONCURRENTLY idx_sd_estado ON public.solicitud_devolucion USING btree (estado);
-- ============================================================================
