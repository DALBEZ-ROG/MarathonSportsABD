-- =============================================================================
-- Fase 46 (lote L6) — Numeracion de comprobantes con secuencia
-- =============================================================================
-- Cierra el defecto D-07.
--
-- POR QUE
-- ComprobanteService.generarNumero() calculaba el correlativo con
-- comprobanteRepository.count() + 1. Dos emisiones simultaneas leen el mismo
-- recuento, generan el mismo numero, y la segunda choca contra
-- uq_comprobante_numero: HTTP 500. Un contador de documentos no puede depender
-- de un COUNT(*).
--
-- EL setval NO ES OPCIONAL
-- Hay 30.000 comprobantes emitidos. Si la secuencia arrancara en 1, el primer
-- comprobante nuevo repetiria un correlativo ya usado. Se arranca por encima del
-- mayor sufijo numerico que exista hoy, sea cual sea su prefijo.
--
-- NOTA SOBRE LOS PREFIJOS
-- Los 30.000 existentes tienen formato 'CI-000000001' (los inserto el seed de la
-- fase 38). El codigo Java, en cambio, emite 'COMP-AAAA-NNNNNN'. Es decir: la
-- aplicacion no ha emitido nunca un comprobante en esta base. Se conserva el
-- formato del codigo y se continua la numeracion por encima de la existente, que
-- es lo que hacia count()+1 — pero ahora sin la carrera.
--
-- REVERSION: fase46_secuencia_comprobante_rollback.sql
-- =============================================================================

BEGIN;

CREATE SEQUENCE IF NOT EXISTS seq_comprobante_interno AS BIGINT START WITH 1;

-- Arrancar por encima de lo ya emitido. coalesce cubre la base vacia.
SELECT setval(
    'seq_comprobante_interno',
    GREATEST(
        (SELECT coalesce(max(substring(numero_comprobante from '[0-9]+$')::BIGINT), 0)
           FROM comprobante_interno),
        1),
    true);   -- true = el proximo nextval devuelve este valor + 1

COMMENT ON SEQUENCE seq_comprobante_interno IS
    'Correlativo de comprobante_interno (F46). Sustituye a count()+1, que se '
    'repetia bajo concurrencia. La usa ComprobanteService.generarNumero().';

-- La secuencia la consume la aplicacion a traves de los roles que pueden emitir
-- comprobantes: Administrador y Operador de Pedidos (ver SecurityConfig).
GRANT USAGE, SELECT ON SEQUENCE seq_comprobante_interno TO rol_administrador;
GRANT USAGE, SELECT ON SEQUENCE seq_comprobante_interno TO rol_operador_pedidos;

COMMIT;

-- Verificacion
--   SELECT last_value FROM seq_comprobante_interno;   -- esperado: 30000
--   SELECT nextval('seq_comprobante_interno');        -- esperado: 30001
