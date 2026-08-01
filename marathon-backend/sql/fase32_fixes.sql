-- =====================================================================
-- Fase 32 — Correcciones de deuda técnica (cierre del proyecto)
-- =====================================================================
-- Idempotente: usa CREATE OR REPLACE y DROP TRIGGER IF EXISTS.
--
-- FIX 1 — BUG CRÍTICO: fn_proteger_total_pedido no protegía nada.
--   La versión anterior condicionaba la excepción a `pg_trigger_depth() = 0`.
--   Dentro de una función de trigger, pg_trigger_depth() vale 1 (nunca 0),
--   así que la condición JAMÁS se cumplía y `UPDATE pedido SET total = 9999`
--   pasaba sin error, violando la regla de negocio #1 del proyecto.
--
--   Patrón correcto (ya validado en F21/F23/F29): comparar el nuevo valor
--   contra el TOTAL REAL recalculado y rechazar si difiere.
--
--   OJO — la fórmula real del proyecto es NETA de descuento:
--       total = GREATEST(SUM(detalle_pedido.subtotal) - pedido.descuento, 0)
--   (así lo hace fn_recalcular_total_por_descuento). Comparar solo contra la
--   suma de subtotales rompería todos los pedidos con descuento.
-- =====================================================================

SET client_encoding = 'UTF8';

CREATE OR REPLACE FUNCTION fn_proteger_total_pedido()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_total_real NUMERIC(12,2);
BEGIN
    IF NEW.total IS DISTINCT FROM OLD.total THEN
        -- Total legítimo = suma de subtotales menos descuento, nunca negativo
        SELECT GREATEST(COALESCE(SUM(d.subtotal), 0) - COALESCE(NEW.descuento, 0), 0)
          INTO v_total_real
        FROM detalle_pedido d
        WHERE d.id_pedido = NEW.id_pedido;

        IF NEW.total IS DISTINCT FROM v_total_real THEN
            RAISE EXCEPTION 'El campo pedido.total es calculado automáticamente a partir de los detalles y el descuento. No puede modificarse directamente (intento: %, valor correcto: %)', NEW.total, v_total_real;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_proteger_total_pedido ON pedido;
CREATE TRIGGER trg_proteger_total_pedido
    BEFORE UPDATE ON pedido
    FOR EACH ROW EXECUTE FUNCTION fn_proteger_total_pedido();

-- =====================================================================
-- AUDITORÍA DE LOS DEMÁS TRIGGERS DE PROTECCIÓN — sin cambios necesarios
-- =====================================================================
-- Se revisaron TODOS los triggers de protección de la base de datos buscando
-- el mismo defecto. Resultado: solo fn_proteger_total_pedido lo tenía.
--
--   fn_proteger_total_orden_compra       (F21) -> compara contra suma real  OK
--   fn_proteger_monto_pagado_cxp         (F23) -> compara contra suma real  OK
--   fn_proteger_costo_materia_prima_op   (F29) -> compara contra suma real  OK
--   fn_validar_bom_producto_fabricado    (F27) -> valida origen producto    OK
--   fn_validar_cambio_origen_producto    (F27) -> valida BOM activo         OK
--   fn_validar_op_producto_fabricado     (F28) -> valida origen producto    OK
--
-- Por eso este script solo reemplaza una función. Los demás protectores se
-- probaron individualmente (rechazan valor falso, aceptan recálculo legítimo)
-- y quedaron intactos.
-- =====================================================================


-- =====================================================================
-- VERIFICACIÓN — ejecutar tras aplicar el script
-- =====================================================================
-- 1) Ninguna función debe usar pg_trigger_depth (esperado: 0 filas)
DO $$
DECLARE v_n int;
BEGIN
    SELECT count(*) INTO v_n
    FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.prokind = 'f'
      AND pg_get_functiondef(p.oid) LIKE '%pg_trigger_depth%';

    IF v_n = 0 THEN
        RAISE NOTICE 'OK: ninguna funcion usa pg_trigger_depth';
    ELSE
        RAISE WARNING 'ATENCION: % funcion(es) aun usan pg_trigger_depth', v_n;
    END IF;
END $$;

-- 2) Todos los pedidos deben tener total coherente con el neto (esperado: 0)
DO $$
DECLARE v_n int;
BEGIN
    SELECT count(*) INTO v_n FROM (
        SELECT p.id_pedido
        FROM pedido p
        WHERE p.total <> GREATEST(
            COALESCE((SELECT SUM(d.subtotal) FROM detalle_pedido d WHERE d.id_pedido = p.id_pedido), 0)
            - p.descuento, 0)
    ) x;

    IF v_n = 0 THEN
        RAISE NOTICE 'OK: todos los pedidos tienen total coherente con el neto';
    ELSE
        RAISE WARNING 'ATENCION: % pedido(s) con total incoherente', v_n;
    END IF;
END $$;

-- 3) Prueba funcional del trigger (no modifica datos: siempre hace ROLLBACK).
--    Descomentar para ejecutarla manualmente sobre un pedido existente:
--
--    BEGIN;
--      UPDATE pedido SET total = 9999 WHERE id_pedido = (SELECT min(id_pedido) FROM pedido);
--      -- debe fallar con: El campo pedido.total es calculado automaticamente...
--    ROLLBACK;
