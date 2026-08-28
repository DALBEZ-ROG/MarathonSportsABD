-- =============================================================================
-- Fase 58 — REVERSION
-- =============================================================================
-- Devuelve producto_proveedor al reparto que tenia antes de la F58, leyendolo
-- del respaldo que aquel script creo.
--
-- POR QUE HACE FALTA UN RESPALDO Y NO BASTAN UNAS REGLAS
-- El reparto anterior era ALEATORIO (poblado masivo de la F38). No hay ninguna
-- regla que lo reconstruya: o se guardo, o se perdio. De ahi que la F58 cree
-- producto_proveedor_respaldo_f58 antes de tocar nada.
--
-- Si el respaldo no existe, este script no puede hacer nada y lo dice, en vez
-- de dejar la tabla a medias.
--
-- OJO: revertir devuelve el desorden. La pantalla de «Nueva orden de compra»
-- volvera a ofrecer zapatos Reebok cuando se elija Nike. El filtro seguira
-- siendo correcto; los datos, no.
-- =============================================================================

BEGIN;

DO $$
BEGIN
    IF to_regclass('public.producto_proveedor_respaldo_f58') IS NULL THEN
        RAISE EXCEPTION 'No existe producto_proveedor_respaldo_f58. El reparto anterior '
                        'era aleatorio y no se puede recalcular: sin ese respaldo, esta '
                        'reversion no es posible.';
    END IF;
END $$;

UPDATE producto_proveedor pp
   SET id_proveedor = r.id_proveedor
  FROM producto_proveedor_respaldo_f58 r
 WHERE r.id_producto_proveedor = pp.id_producto_proveedor
   AND pp.id_proveedor IS DISTINCT FROM r.id_proveedor;

COMMIT;

-- El respaldo NO se borra: revertir la reversion tiene que seguir siendo
-- posible. Para retirarlo del todo, cuando ya no haga falta:
--   DROP TABLE producto_proveedor_respaldo_f58;

-- Verificacion
--   SELECT count(*) FROM producto_proveedor pp
--     JOIN producto_proveedor_respaldo_f58 r USING (id_producto_proveedor)
--    WHERE pp.id_proveedor <> r.id_proveedor;   -- esperado: 0
