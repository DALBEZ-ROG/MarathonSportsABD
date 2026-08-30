-- =============================================================================
-- Reversión de fase92_control_respaldos.sql
-- =============================================================================
-- ATENCION: DROP SCHEMA control CASCADE borra el diario de respaldos, borrados
-- y restauraciones. Es la única copia: no está en ningún volcado, porque el
-- volcado lo excluye a propósito. Exportarlo antes si tiene algo que valga:
--
--   \copy (SELECT * FROM control.respaldo)  TO 'respaldo.csv'  CSV HEADER
--   \copy (SELECT * FROM control.operacion) TO 'operacion.csv' CSV HEADER
-- =============================================================================

BEGIN;

DELETE FROM rol_permiso
WHERE id_permiso IN (SELECT id_permiso FROM permiso WHERE modulo = 'respaldos');

DELETE FROM permiso WHERE modulo = 'respaldos';

DROP SCHEMA IF EXISTS control CASCADE;

COMMIT;
