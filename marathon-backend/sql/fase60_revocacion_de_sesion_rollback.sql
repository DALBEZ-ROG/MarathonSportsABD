-- =============================================================================
-- Fase 60 — REVERSION
-- =============================================================================
-- Retira la lista de denegacion de sesiones.
--
-- OJO: revertir devuelve D-23. El `logout` vuelve a no invalidar nada y un
-- token robado sigue sirviendo hasta que expire por si mismo (2 h el de
-- acceso, 7 dias el de refresco).
--
-- Hay que revertir TAMBIEN el codigo: si la aplicacion de la F60 sigue
-- desplegada y esta tabla no existe, toda peticion autenticada fallara al
-- consultar la lista. Esta reversion es de esquema y va acompanada, no sola.
-- =============================================================================

BEGIN;

DROP TABLE IF EXISTS token_revocado;

COMMIT;

-- Verificacion
--   SELECT to_regclass('public.token_revocado');   -- esperado: NULL
