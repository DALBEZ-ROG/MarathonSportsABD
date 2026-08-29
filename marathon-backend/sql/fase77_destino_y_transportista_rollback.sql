-- =============================================================================
-- Rollback de la Fase 77
-- =============================================================================
-- Deja la region y el catalogo de transportistas como si nunca hubieran
-- existido. Lo que NO se puede deshacer es lo que se escribio en
-- pedido.transportista y pedido.region_destino mientras estuvo puesto: eso son
-- datos de empaques reales y se quedan como estan.
-- =============================================================================

BEGIN;

DELETE FROM rol_permiso
 WHERE id_permiso IN (SELECT id_permiso FROM permiso
                       WHERE modulo = 'transportistas' AND accion = 'ver');

DELETE FROM permiso WHERE modulo = 'transportistas' AND accion = 'ver';

DROP TABLE IF EXISTS transportista;

DROP INDEX IF EXISTS idx_ciudad_region;

ALTER TABLE ciudad DROP CONSTRAINT IF EXISTS chk_ciudad_region;

ALTER TABLE ciudad DROP COLUMN IF EXISTS region;

COMMIT;
