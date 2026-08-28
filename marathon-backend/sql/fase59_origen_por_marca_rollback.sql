-- =============================================================================
-- Fase 59 — REVERSION
-- =============================================================================
-- Devuelve el modulo de Produccion al estado anterior a la F59: reinserta las
-- 2.487 ordenes de produccion, sus 7.455 consumos, sus 4.219 movimientos de
-- materia prima y las 50 lineas de BOM, y restaura producto.origen.
--
-- Solo funciona si existen las cinco tablas *_respaldo_f59 que creo la F59. El
-- reparto anterior era ALEATORIO: no hay regla que lo recalcule, o se guardo o
-- se perdio. Si falta alguna, este script no hace nada y lo dice, en vez de
-- dejar el modulo a medias.
--
-- OJO: revertir devuelve tambien el problema. Marathon volvera a «fabricar»
-- unas Air Force 1.
-- =============================================================================

BEGIN;

DO $$
DECLARE falta TEXT;
BEGIN
    SELECT string_agg(t, ', ') INTO falta
      FROM (VALUES ('origen_respaldo_f59'), ('orden_produccion_respaldo_f59'),
                   ('orden_produccion_consumo_respaldo_f59'), ('movimiento_mp_respaldo_f59'),
                   ('lista_materiales_respaldo_f59')) AS v(t)
     WHERE to_regclass('public.' || t) IS NULL;

    IF falta IS NOT NULL THEN
        RAISE EXCEPTION 'Faltan respaldos de la F59: %. Sin ellos esta reversion no es '
                        'posible: lo borrado no se puede recalcular.', falta;
    END IF;
END $$;

-- De la raiz a la hoja, al reves que el borrado.
INSERT INTO orden_produccion SELECT * FROM orden_produccion_respaldo_f59
    ON CONFLICT DO NOTHING;

INSERT INTO orden_produccion_consumo SELECT * FROM orden_produccion_consumo_respaldo_f59
    ON CONFLICT DO NOTHING;

INSERT INTO movimiento_materia_prima SELECT * FROM movimiento_mp_respaldo_f59
    ON CONFLICT DO NOTHING;

INSERT INTO lista_materiales SELECT * FROM lista_materiales_respaldo_f59
    ON CONFLICT DO NOTHING;

UPDATE producto p SET origen = r.origen
  FROM origen_respaldo_f59 r
 WHERE r.id_producto = p.id_producto
   AND p.origen IS DISTINCT FROM r.origen;

COMMIT;

-- Las secuencias de las tablas reinsertadas hay que empujarlas por encima de lo
-- restaurado, o el proximo INSERT chocara con una clave que ya existe.
SELECT setval(pg_get_serial_sequence('orden_produccion', 'id_orden_produccion'),
              (SELECT max(id_orden_produccion) FROM orden_produccion), true);
SELECT setval(pg_get_serial_sequence('orden_produccion_consumo', 'id_consumo'),
              (SELECT max(id_consumo) FROM orden_produccion_consumo), true);
SELECT setval(pg_get_serial_sequence('movimiento_materia_prima', 'id_movimiento_mp'),
              (SELECT max(id_movimiento_mp) FROM movimiento_materia_prima), true);
SELECT setval(pg_get_serial_sequence('lista_materiales', 'id_bom'),
              (SELECT max(id_bom) FROM lista_materiales), true);

-- Los respaldos NO se borran: revertir la reversion tiene que seguir siendo
-- posible. Para retirarlos cuando ya no hagan falta:
--   DROP TABLE origen_respaldo_f59, orden_produccion_respaldo_f59,
--              orden_produccion_consumo_respaldo_f59, movimiento_mp_respaldo_f59,
--              lista_materiales_respaldo_f59;

-- Verificacion
--   SELECT count(*) FROM orden_produccion;   -- esperado: 3000
--   SELECT origen, count(*) FROM producto GROUP BY origen;  -- comprado 92 · fabricado 16
