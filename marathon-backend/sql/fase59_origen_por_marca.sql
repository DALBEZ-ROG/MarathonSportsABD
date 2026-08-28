-- =============================================================================
-- Fase 59 — producto.origen segun la marca: Marathon no fabrica Nike
-- =============================================================================
-- POR QUE
-- El poblado de la F38 repartio `origen` al azar, igual que hizo con
-- producto_proveedor (F58). Resultado: 16 productos marcados «fabricado», y 13
-- de ellos de marca ajena — unas Air Force 1, unas Samba, unas Reebok BB 1000.
-- Marathon Sports no fabrica eso: lo compra.
--
-- LO QUE HACE ESTE SCRIPT DESTRUYE DATOS. Leelo entero antes de ejecutarlo.
--
-- EL ALCANCE, MEDIDO
-- De esos 13 productos cuelga el 83% del modulo de Produccion:
--
--     2.487 ordenes de produccion   (de 3.000)
--     7.455 consumos de materia prima
--     4.219 movimientos de materia prima
--        50 lineas de lista de materiales
--
-- Cambiar solo `origen` habria sido una linea, pero dejaba 2.487 ordenes de
-- produccion de productos marcados como comprados: se cambia una incoherencia
-- visible por otra peor. Hoy los datos, aunque implausibles, al menos concuerdan
-- entre si —todo lo «fabricado» tiene BOM y ordenes—. Por eso se borra lo que
-- cuelga, y no solo se reetiqueta.
--
-- Decision del dueño del proyecto, 2026-08-27: corregir y borrar. Produccion
-- pasa de 3.000 a 513 ordenes; el tablero de manufactura, el analisis de costos
-- y los informes seguiran funcionando, con menos volumen.
--
-- LA REGLA, dicha una vez
--     Se FABRICA lo de marca propia:
--         «Marca: MARATHON SPORTS»  o  descripcion «Produccion propia ...»
--     Todo lo demas se COMPRA.
--
-- Eso deja 14 productos fabricados (11 de marca Marathon + 3 de produccion
-- propia) en vez de 16, y ninguno de marca ajena.
--
-- LOS 11 QUE PASAN A «FABRICADO» NO TIENEN BOM, y esta bien que se note: son
-- de marca propia, asi que su origen es correcto, pero hasta que alguien
-- defina su lista de materiales no se les puede crear una orden de produccion
-- —OrdenProduccionService lo exige y lo dice con un mensaje claro—. La pantalla
-- de BOM existe para eso. Se prefiere «fabricado sin BOM todavia», que es
-- verdad y se puede completar, a «comprado», que es mentira.
--
-- SOBRE EL KARDEX DE MATERIA PRIMA
-- Borrar 4.219 movimientos no reconstruye `materia_prima.stock_actual`, que es
-- un saldo que el poblado escribio directamente. Se comprobo ANTES de tocar
-- nada: el kardex no cuadraba con el saldo en NINGUNA de las 300 materias
-- primas. Es decir, este script no rompe un invariante que se cumpliera; el
-- descuadre ya estaba, y arreglarlo es otro trabajo.
--
-- REVERSION: fase59_origen_por_marca_rollback.sql, desde los respaldos que
-- crea este script. Sin ellos no hay vuelta atras: son 14.161 filas borradas.
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 0. Que productos son de marca propia. Se calcula UNA vez y se reutiliza.
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE marca_propia ON COMMIT DROP AS
SELECT id_producto
  FROM producto
 WHERE upper(coalesce(split_part(descripcion, 'Marca: ', 2), '')) = 'MARATHON SPORTS'
    OR descripcion LIKE 'Producci%n propia%';

-- Ordenes que se van: las de productos que NO son de marca propia.
CREATE TEMP TABLE ordenes_a_borrar ON COMMIT DROP AS
SELECT o.id_orden_produccion
  FROM orden_produccion o
 WHERE o.id_producto NOT IN (SELECT id_producto FROM marca_propia);

-- ---------------------------------------------------------------------------
-- 1. Respaldos. NO son opcionales: esto borra 14.161 filas.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS origen_respaldo_f59 AS
SELECT id_producto, origen FROM producto;

CREATE TABLE IF NOT EXISTS orden_produccion_respaldo_f59 AS
SELECT o.* FROM orden_produccion o
 WHERE o.id_orden_produccion IN (SELECT id_orden_produccion FROM ordenes_a_borrar);

CREATE TABLE IF NOT EXISTS orden_produccion_consumo_respaldo_f59 AS
SELECT c.* FROM orden_produccion_consumo c
 WHERE c.id_orden_produccion IN (SELECT id_orden_produccion FROM ordenes_a_borrar);

CREATE TABLE IF NOT EXISTS movimiento_mp_respaldo_f59 AS
SELECT m.* FROM movimiento_materia_prima m
 WHERE m.id_orden_produccion IN (SELECT id_orden_produccion FROM ordenes_a_borrar);

CREATE TABLE IF NOT EXISTS lista_materiales_respaldo_f59 AS
SELECT b.* FROM lista_materiales b
 WHERE b.id_producto NOT IN (SELECT id_producto FROM marca_propia);

COMMENT ON TABLE origen_respaldo_f59 IS
    'producto.origen ANTES de la F59, cuando era aleatorio (F38). Junto con las '
    'otras cuatro tablas *_respaldo_f59, es la unica forma de revertir esta fase.';

-- ---------------------------------------------------------------------------
-- 2. Borrado, de la hoja a la raiz. El orden lo imponen las claves foraneas:
--    casi todas son ON DELETE RESTRICT, asi que el orden equivocado no corrompe
--    nada — simplemente falla.
-- ---------------------------------------------------------------------------
DELETE FROM movimiento_materia_prima
 WHERE id_orden_produccion IN (SELECT id_orden_produccion FROM ordenes_a_borrar);

DELETE FROM orden_produccion_consumo
 WHERE id_orden_produccion IN (SELECT id_orden_produccion FROM ordenes_a_borrar);

DELETE FROM orden_produccion
 WHERE id_orden_produccion IN (SELECT id_orden_produccion FROM ordenes_a_borrar);

DELETE FROM lista_materiales
 WHERE id_producto NOT IN (SELECT id_producto FROM marca_propia);

-- ---------------------------------------------------------------------------
-- 3. El origen, segun la regla.
-- ---------------------------------------------------------------------------
UPDATE producto SET origen = 'fabricado'
 WHERE id_producto IN (SELECT id_producto FROM marca_propia)
   AND origen <> 'fabricado';

UPDATE producto SET origen = 'comprado'
 WHERE id_producto NOT IN (SELECT id_producto FROM marca_propia)
   AND origen <> 'comprado';

-- ---------------------------------------------------------------------------
-- 4. Comprobaciones DENTRO de la transaccion. Si algo no cuadra, no se aplica
--    nada: es preferible quedarse con datos implausibles que con datos rotos.
-- ---------------------------------------------------------------------------
DO $$
DECLARE mal INT;
BEGIN
    -- (a) Ningun producto de marca ajena puede quedar como fabricado.
    SELECT count(*) INTO mal FROM producto p
     WHERE p.origen = 'fabricado'
       AND p.id_producto NOT IN (SELECT id_producto FROM marca_propia);
    IF mal > 0 THEN
        RAISE EXCEPTION 'Quedan % productos de marca ajena marcados como fabricados.', mal;
    END IF;

    -- (b) Ninguna orden de produccion puede apuntar a un producto comprado.
    SELECT count(*) INTO mal FROM orden_produccion o
      JOIN producto p ON p.id_producto = o.id_producto
     WHERE p.origen <> 'fabricado';
    IF mal > 0 THEN
        RAISE EXCEPTION 'Quedan % ordenes de produccion de productos comprados.', mal;
    END IF;

    -- (c) Ninguna lista de materiales puede colgar de un producto comprado.
    SELECT count(*) INTO mal FROM lista_materiales b
      JOIN producto p ON p.id_producto = b.id_producto
     WHERE p.origen <> 'fabricado';
    IF mal > 0 THEN
        RAISE EXCEPTION 'Quedan % lineas de BOM en productos comprados.', mal;
    END IF;

    -- (d) Y no puede quedar ningun consumo ni movimiento huerfano.
    SELECT count(*) INTO mal FROM orden_produccion_consumo c
     WHERE NOT EXISTS (SELECT 1 FROM orden_produccion o
                        WHERE o.id_orden_produccion = c.id_orden_produccion);
    IF mal > 0 THEN
        RAISE EXCEPTION 'Quedan % consumos huerfanos.', mal;
    END IF;
END $$;

COMMIT;

-- Verificacion
--   SELECT origen, count(*) FROM producto GROUP BY origen;
--   -- esperado: comprado 94 · fabricado 14
--   SELECT count(*) FROM orden_produccion;          -- esperado: 513
--   SELECT count(*) FROM lista_materiales;          -- esperado: 12
--   SELECT p.nombre, p.origen FROM producto p WHERE p.origen='fabricado' ORDER BY 1;
--   -- esperado: los 14 de marca Marathon, ninguno de marca ajena
