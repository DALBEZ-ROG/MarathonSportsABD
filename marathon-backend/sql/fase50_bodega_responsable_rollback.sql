-- =============================================================================
-- Fase 50 — REVERSION
-- =============================================================================
-- Quita la columna 'responsable' de bodega. Se pierden los valores guardados.
--
-- Hay que retirar TAMBIEN el campo de model/Bodega.java y el mapeo de
-- BodegaService, o la aplicacion no arranca: con ddl-auto=validate, Hibernate
-- comprueba que cada campo mapeado tenga su columna y aborta si falta.
-- =============================================================================

BEGIN;

ALTER TABLE bodega DROP COLUMN IF EXISTS responsable;

COMMIT;

-- Verificacion
--   SELECT count(*) FROM information_schema.columns
--    WHERE table_name='bodega' AND column_name='responsable';   -- esperado: 0
