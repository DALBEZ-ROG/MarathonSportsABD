-- =============================================================================
-- Fase 73 — REVERSION
-- =============================================================================
-- Quita el documento del cliente.
--
-- OJO CON LO QUE SE PIERDE: los documentos que se hayan capturado desde la F73
-- SE BORRAN, y no se pueden recuperar de ningun sitio. Antes de revertir:
--
--   SELECT id_cliente, tipo_documento, numero_documento
--     FROM cliente WHERE numero_documento IS NOT NULL;
--
-- Y hay que revertir TAMBIEN el codigo: si la aplicacion de la F73 sigue
-- desplegada y las columnas no existen, dar de alta un cliente fallara.
--
-- Revertir devuelve ademas el defecto original: el formulario volveria a pedir
-- un documento obligatorio para tirarlo.
-- =============================================================================

BEGIN;

DROP INDEX IF EXISTS idx_cliente_documento_busqueda;
DROP INDEX IF EXISTS uq_cliente_documento;

ALTER TABLE cliente DROP CONSTRAINT IF EXISTS chk_cliente_documento_formato;
ALTER TABLE cliente DROP CONSTRAINT IF EXISTS chk_cliente_documento_completo;
ALTER TABLE cliente DROP CONSTRAINT IF EXISTS chk_cliente_tipo_documento;

ALTER TABLE cliente DROP COLUMN IF EXISTS numero_documento;
ALTER TABLE cliente DROP COLUMN IF EXISTS tipo_documento;

COMMIT;

-- Verificacion
--   SELECT count(*) FROM information_schema.columns
--    WHERE table_name='cliente' AND column_name='numero_documento';   -- esperado: 0
