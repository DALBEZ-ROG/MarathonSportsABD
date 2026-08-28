-- =============================================================================
-- Fase 47 — REVERSION
-- =============================================================================
-- Deshace fase47_reserva_stock.sql.
--
-- ATENCION: borra el historico de reservas. Si ya se han creado reservas reales,
-- exportarlas antes:
--   \copy (SELECT * FROM reserva_stock) TO 'reservas.csv' CSV HEADER
--
-- El codigo Java que la usa (ReservaStockService y sus llamadas desde
-- PedidoService, EmpaqueService e InventarioService) hay que retirarlo aparte:
-- con la tabla borrada y el codigo puesto, la aplicacion no arranca
-- (ddl-auto=validate no encontraria la entidad ReservaStock).
-- =============================================================================

BEGIN;

DROP TABLE IF EXISTS reserva_stock;   -- se lleva por delante sus indices y su secuencia

COMMIT;

-- Verificacion
--   SELECT to_regclass('public.reserva_stock');   -- esperado: NULL
