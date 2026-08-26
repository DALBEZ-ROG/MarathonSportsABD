-- =============================================================================
-- Fase 45 (lote L4) — REVERSION
-- =============================================================================
-- Deshace fase45_picking_bodega.sql y deja detalle_pedido exactamente como
-- estaba. Comprobado comparando pg_dump --schema-only antes y despues.
--
-- QUE SE PIERDE
-- El dato de que bodega se recogio cada linea, en las lineas creadas desde que
-- se aplico la fase 45. No hay forma de reconstruirlo. Si se revierte con
-- pedidos ya recogidos y sin despachar, esos despachos vuelven a repartirse por
-- orden estable de bodega (el comportamiento de la L1), que no falla ni corrompe
-- nada: simplemente puede descontar de una bodega distinta de la real.
--
-- El codigo de la L4 debe revertirse ANTES o A LA VEZ que este script: con la
-- columna fuera y la entidad todavia mapeandola, ddl-auto=validate impide que
-- la aplicacion arranque.
-- =============================================================================

BEGIN;

DROP INDEX IF EXISTS idx_detalle_pedido_bodega_picking;

ALTER TABLE detalle_pedido
    DROP CONSTRAINT IF EXISTS fk_detalle_bodega_picking;

ALTER TABLE detalle_pedido
    DROP COLUMN IF EXISTS id_bodega_picking;

COMMIT;

-- Verificacion
--   SELECT count(*) FROM information_schema.columns
--    WHERE table_name = 'detalle_pedido' AND column_name = 'id_bodega_picking';
--   -- esperado: 0
