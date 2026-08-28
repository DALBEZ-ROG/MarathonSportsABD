-- =============================================================================
-- Fase 58 — producto_proveedor repartido POR MARCA, no al azar
-- =============================================================================
-- POR QUE
-- El poblado masivo de la F38 asigno los proveedores AL AZAR. Con la pantalla
-- de «Nueva orden de compra» filtrando por proveedor (F57), eso salta a la
-- vista: al elegir Nike salian zapatos Reebok y Adidas. De los 20 productos
-- que Nike tenia asignados, solo 4 llevaban «NIK» en el nombre.
--
-- El filtro era correcto —coincidia exactamente con la tabla—; los datos no.
--
-- DE DONDE SALE LA MARCA
-- De producto.descripcion, que trae «Marca: NIKE», «Marca: ADIDAS», etc. Son
-- 23 marcas distintas en 108 productos.
--
-- EL REPARTO, Y LAS DOS DECISIONES QUE LLEVA DENTRO
--
--   NIKE + JORDAN     -> Nike Ecuador S.A.       33 productos
--   ADIDAS            -> Adidas Andina           25
--   PUMA              -> Puma Sports              6
--   REEBOK            -> Reebok Distribucion      5
--   UNDER ARMOUR      -> Under Armour EC          3
--   MARATHON SPORTS
--   + 15 marcas mas   -> Distribuidora Marathon  33
--   (produccion propia)-> SIN proveedor           3
--                                               ---
--                                                108
--
--   1. JORDAN va con NIKE porque Jordan es una marca de Nike. No es una
--      suposicion sobre estos datos: es como funciona la marca.
--
--   2. Las 15 marcas que no tienen proveedor propio en la tabla `proveedor`
--      (ASTRO, BABOLAT, ENERGETICS, GYM POWER, HI-TEC, HOKA ONE ONE, JANSPORT,
--      MCKINLEY, MUNICH, OUTLAND, SIUX, UFC, UMBRO, VOLCOM, WILSON) van a
--      «Distribuidora Marathon», que es el distribuidor multimarca del
--      catalogo. Es una DECISION, no un dato: la alternativa era dejarlas sin
--      proveedor, y desde la F57 un producto sin proveedor no se puede comprar
--      desde la pantalla. Se prefiere que sean comprables a traves del
--      distribuidor antes que invisibles.
--
--      Si alguna de esas marcas tiene su propio proveedor en la realidad, se
--      da de alta en `proveedor` y se reasigna: es un UPDATE de una linea.
--
-- LOS TRES SIN PROVEEDOR SE QUEDAN SIN PROVEEDOR
-- Son los productos 106, 107 y 108 —«Produccion propia Marathon Sports»—, que
-- no se compran a nadie: se fabrican. No tenian fila en producto_proveedor y
-- siguen sin tenerla. Eso ya estaba bien.
--
-- QUE NO CAMBIA
-- Solo se toca id_proveedor. El precio de compra, es_proveedor_principal, el
-- estado y la fecha de registro de cada fila se conservan: no hay motivo para
-- inventar precios nuevos, y nada mas en el esquema depende de esta tabla
-- (comprobado: ninguna FK apunta a producto_proveedor).
--
-- REVERSION: fase58_proveedor_por_marca_rollback.sql, que restaura desde el
-- respaldo que crea este script. No se puede revertir «por reglas» porque el
-- reparto anterior era ALEATORIO: no hay forma de recalcularlo, solo de
-- guardarlo. Por eso el respaldo no es opcional.
-- =============================================================================

BEGIN;

-- 1. Respaldo del reparto anterior. Si ya existe, este script ya se ejecuto:
--    se conserva el respaldo ORIGINAL y no se pisa con el estado intermedio.
CREATE TABLE IF NOT EXISTS producto_proveedor_respaldo_f58 AS
SELECT id_producto_proveedor, id_producto, id_proveedor
FROM producto_proveedor;

COMMENT ON TABLE producto_proveedor_respaldo_f58 IS
    'Reparto de proveedores ANTERIOR a la F58, cuando era aleatorio (F38). '
    'Existe solo para poder revertir: el reparto viejo no se puede recalcular. '
    'Se puede borrar cuando la F58 se de por buena.';

-- 2. El reparto por marca.
UPDATE producto_proveedor pp
   SET id_proveedor = destino.id_proveedor_nuevo
  FROM (
        SELECT p.id_producto,
               CASE upper(coalesce(split_part(p.descripcion, 'Marca: ', 2), ''))
                   WHEN 'NIKE'         THEN 1
                   WHEN 'JORDAN'       THEN 1   -- Jordan es marca de Nike
                   WHEN 'ADIDAS'       THEN 2
                   WHEN 'PUMA'         THEN 3
                   WHEN 'UNDER ARMOUR' THEN 4
                   WHEN 'REEBOK'       THEN 6
                   ELSE 5                        -- distribuidor multimarca
               END AS id_proveedor_nuevo
          FROM producto p
       ) AS destino
 WHERE destino.id_proveedor_nuevo IS NOT NULL
   AND pp.id_producto = destino.id_producto
   AND pp.id_proveedor IS DISTINCT FROM destino.id_proveedor_nuevo;

-- 3. Comprobacion, DENTRO de la transaccion: ningun producto puede quedar
--    asignado a un proveedor que no corresponda a su marca. Si algo no cuadra,
--    no se aplica nada.
DO $$
DECLARE descuadres INT;
BEGIN
    SELECT count(*) INTO descuadres
      FROM producto_proveedor pp
      JOIN producto p ON p.id_producto = pp.id_producto
     WHERE pp.id_proveedor <> CASE upper(coalesce(split_part(p.descripcion, 'Marca: ', 2), ''))
                                  WHEN 'NIKE'         THEN 1
                                  WHEN 'JORDAN'       THEN 1
                                  WHEN 'ADIDAS'       THEN 2
                                  WHEN 'PUMA'         THEN 3
                                  WHEN 'UNDER ARMOUR' THEN 4
                                  WHEN 'REEBOK'       THEN 6
                                  ELSE 5
                              END;

    IF descuadres > 0 THEN
        RAISE EXCEPTION 'Quedan % productos con un proveedor que no corresponde a su marca. '
                        'No se aplica el reparto.', descuadres;
    END IF;
END $$;

COMMIT;

-- Verificacion
--   SELECT pr.nombre, count(*),
--          string_agg(DISTINCT split_part(p.descripcion,'Marca: ',2), ', ')
--     FROM producto_proveedor pp
--     JOIN producto p  ON p.id_producto  = pp.id_producto
--     JOIN proveedor pr ON pr.id_proveedor = pp.id_proveedor
--    GROUP BY pr.nombre ORDER BY 2 DESC;
--   -- esperado: Nike 33 (NIKE, JORDAN) · Marathon 33 · Adidas 25 · Puma 6
--   --           Reebok 5 · Under Armour 3
