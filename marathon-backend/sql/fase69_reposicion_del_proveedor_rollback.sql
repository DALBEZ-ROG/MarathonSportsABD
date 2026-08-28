-- =============================================================================
-- Fase 69 — REVERSION
-- =============================================================================
-- Quita la marca de reposicion y su traza hacia la devolucion.
--
-- OJO CON LO QUE SE PIERDE. Las ordenes que ya se crearon como reposicion NO
-- desaparecen: se quedan como ordenes de compra normales, con precio real y sin
-- nada que las distinga. Es decir, **pasan a ser facturables**, y quien las mire
-- despues no tendra forma de saber que eran reposiciones. Alguien podria
-- documentarlas y generar una cuenta por pagar por mercancia que el proveedor
-- mando gratis.
--
-- Antes de revertir conviene anotarlas:
--   SELECT id_orden_compra, id_devolucion_prov FROM orden_compra WHERE es_reposicion;
--
-- Hay que revertir TAMBIEN el codigo: si la aplicacion de la F69 sigue
-- desplegada y las columnas no existen, resolver una devolucion con reposicion
-- fallara al intentar crear la orden.
-- =============================================================================

BEGIN;

ALTER TABLE orden_compra DROP CONSTRAINT IF EXISTS chk_oc_reposicion_coherente;
ALTER TABLE orden_compra DROP CONSTRAINT IF EXISTS fk_oc_devolucion_prov;
DROP INDEX IF EXISTS idx_oc_reposicion;

ALTER TABLE orden_compra DROP COLUMN IF EXISTS id_devolucion_prov;
ALTER TABLE orden_compra DROP COLUMN IF EXISTS es_reposicion;

COMMIT;

-- Verificacion
--   SELECT count(*) FROM information_schema.columns
--    WHERE table_name = 'orden_compra' AND column_name = 'es_reposicion';
--   -- esperado: 0
