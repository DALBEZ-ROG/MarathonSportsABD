-- =============================================================================
-- Fase 46 (lote L6) — REVERSION
-- =============================================================================
-- Elimina la secuencia y devuelve la numeracion a count()+1, con su carrera.
--
-- El codigo de la L6 debe revertirse ANTES o A LA VEZ que este script: con la
-- secuencia fuera y ComprobanteService llamando a nextval, toda emision falla.
--
-- No se pierde ningun comprobante: los ya emitidos conservan su numero. Lo unico
-- que se pierde es la garantia de unicidad bajo concurrencia.
-- =============================================================================

BEGIN;

DROP SEQUENCE IF EXISTS seq_comprobante_interno;

COMMIT;

-- Verificacion
--   SELECT count(*) FROM pg_class WHERE relname = 'seq_comprobante_interno';
--   -- esperado: 0
