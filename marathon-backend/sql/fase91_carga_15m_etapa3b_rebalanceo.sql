-- =============================================================================
-- FASE 91 · Etapa 3b — Rebalanceo: que los datos se sostengan entre tablas
-- -----------------------------------------------------------------------------
-- EL PROBLEMA QUE ARREGLA
-- Pedir 1,5M de filas en `pedido` Y 1,5M en `detalle_pedido` es aritmeticamente
-- incompatible con que todo pedido tenga lineas: las 614.511 lineas que ya
-- existian pertenecen a solo 230.004 pedidos (2,67 lineas cada uno, que es lo
-- normal), asi que a los 1.269.996 pedidos nuevos les quedaban 885.489 lineas.
-- 384.507 pedidos se quedaban vacios y con total 0. Igual en compras: 5.332
-- ordenes sin ninguna linea.
--
-- LA SALIDA
-- Subir las tablas de LINEA por encima de 1,5M. El encargo era "mas de un
-- millon, o metele 1,5", un suelo, no un techo; y una tabla de detalle mayor
-- que su cabecera es justo la forma que tienen los datos de verdad. Cada pedido
-- y cada orden nuevos pasan a tener entre 1 y 3 lineas, con media 2.
--
-- LO QUE ARRASTRA
--   - pedido.total y orden_compra.total hay que recalcularlos.
--   - comprobante_interno.total tiene que seguir al total de su pedido, o se
--     rompe la invariante que trg_validar_total_comprobante defiende. Se
--     aprovecha para repartir los comprobantes sobre TODOS los pedidos nuevos.
--   - reserva_stock reservaba un producto cualquiera, no uno del pedido.
--   - solicitud_devolucion pedia la devolucion de pedidos concentrados en el
--     primer tramo; se reparte sobre todos.
-- =============================================================================

\set ON_ERROR_STOP on
\timing on

SET work_mem = '512MB';
SET maintenance_work_mem = '1GB';
SET synchronous_commit = off;

\ir fase91_andamiaje.sql

ALTER TABLE detalle_pedido       DISABLE TRIGGER trg_recalcular_total_pedido_insert;
ALTER TABLE pedido               DISABLE TRIGGER trg_proteger_total_pedido;
ALTER TABLE pedido               DISABLE TRIGGER trg_recalcular_total_por_descuento;
ALTER TABLE pedido               DISABLE TRIGGER trg_pedido_updated_at;
ALTER TABLE orden_compra_detalle DISABLE TRIGGER trg_oc_total_insert;
ALTER TABLE orden_compra         DISABLE TRIGGER trg_proteger_total_oc;

-- -----------------------------------------------------------------------------
-- 1. detalle_pedido: llevar TODO pedido nuevo a entre 1 y 3 lineas
--    El pedido de indice k quiere 1 + mod(k,3) lineas y ya tiene 1 si
--    k < (lineas que puso la etapa 3). generate_series(1,0) no devuelve nada,
--    asi que los que ya estan servidos no reciben ninguna.
-- -----------------------------------------------------------------------------
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario,
                            picking_completado, cantidad_recogida, id_bodega_picking)
SELECT p.b + k,
       pr.b + mod((k * 31 + s) * 2654435761, pr.n),
       1 + mod((k * 7 + s) * 7919, 40),
       round((3 + mod((k * 5 + s) * 104729, 45000) / 100.0)::numeric, 2),
       (mod(k + s, 3) = 0),
       CASE WHEN mod(k + s, 3) = 0 THEN 1 + mod((k * 7 + s) * 7919, 40) ELSE 0 END,
       CASE WHEN mod(k + s, 3) = 0 THEN c.bodega[1 + mod(k + s, array_length(c.bodega, 1))] END
FROM (SELECT b, n FROM carga.r     WHERE tabla = 'pedido')         p,
     (SELECT b, n FROM carga.r     WHERE tabla = 'producto')       pr,
     (SELECT filas FROM carga.rango WHERE tabla = 'detalle_pedido') dl,
     carga.cat c,
     generate_series(0, p.n - 1) k,
     LATERAL generate_series(1, (1 + mod(k, 3)) - CASE WHEN k < dl.filas THEN 1 ELSE 0 END) s;

-- -----------------------------------------------------------------------------
-- 2. orden_compra_detalle: lo mismo para las ordenes de compra.
--    Las lineas nuevas van TODAS de tipo 'producto', a proposito: la etapa 4
--    necesita lineas de producto para que devolucion_proveedor_detalle pueda
--    sacar de ellas su id_producto, que es NOT NULL y no admite materia prima.
-- -----------------------------------------------------------------------------
INSERT INTO orden_compra_detalle (id_orden_compra, tipo_item, id_producto, id_materia_prima,
                                  cantidad, precio_unitario, cantidad_recibida)
SELECT o.b + k,
       'producto',
       pr.b + mod((k * 31 + s) * 2654435761, pr.n),
       NULL,
       1 + mod((k * 7 + s) * 7919, 300),
       round((2 + mod((k * 5 + s) * 104729, 25000) / 100.0)::numeric, 2),
       CASE WHEN mod(k + s, 4) = 0 THEN 1 + mod((k * 7 + s) * 7919, 300) ELSE 0 END
FROM (SELECT b, n FROM carga.r     WHERE tabla = 'orden_compra')         o,
     (SELECT b, n FROM carga.r     WHERE tabla = 'producto')             pr,
     (SELECT filas FROM carga.rango WHERE tabla = 'orden_compra_detalle') od,
     generate_series(0, o.n - 1) k,
     LATERAL generate_series(1, (1 + mod(k, 3)) - CASE WHEN k < od.filas THEN 1 ELSE 0 END) s;

-- -----------------------------------------------------------------------------
-- 3. Totales recalculados sobre TODAS las lineas del pedido / de la orden.
--    Solo el tramo nuevo: lo que ya existia conserva su total.
-- -----------------------------------------------------------------------------
UPDATE pedido p
SET total      = GREATEST(d.suma - p.descuento, 0),
    updated_at = carga.tope(p.fecha_pedido + interval '1 hour')
FROM (SELECT id_pedido, sum(subtotal) AS suma
      FROM detalle_pedido
      WHERE id_pedido >= (SELECT b FROM carga.r WHERE tabla = 'pedido')
      GROUP BY id_pedido) d
WHERE d.id_pedido = p.id_pedido;

UPDATE orden_compra o
SET total      = d.suma,
    updated_at = carga.tope(o.fecha_orden + interval '1 hour')
FROM (SELECT id_orden_compra, sum(subtotal) AS suma
      FROM orden_compra_detalle
      WHERE id_orden_compra >= (SELECT b FROM carga.r WHERE tabla = 'orden_compra')
      GROUP BY id_orden_compra) d
WHERE d.id_orden_compra = o.id_orden_compra;

-- -----------------------------------------------------------------------------
-- 4. comprobante_interno: se reparte sobre todos los pedidos nuevos y su total
--    vuelve a ser el del pedido. Sin esto quedaria apuntando al total viejo y
--    se rompe justo la igualdad que trg_validar_total_comprobante existe para
--    defender.
-- -----------------------------------------------------------------------------
UPDATE comprobante_interno ci
SET id_pedido = pe.id_pedido,
    total     = pe.total
FROM (SELECT b FROM carga.r WHERE tabla = 'comprobante_interno') cb,
     (SELECT b, n FROM carga.r WHERE tabla = 'pedido') p,
     pedido pe
WHERE ci.id_comprobante >= cb.b
  AND pe.id_pedido = p.b + mod(ci.id_comprobante - cb.b, p.n);

-- -----------------------------------------------------------------------------
-- 5. solicitud_devolucion: repartida sobre todos los pedidos nuevos, en vez de
--    amontonada en el primer tramo.
-- -----------------------------------------------------------------------------
UPDATE solicitud_devolucion s
SET id_pedido = p.b + mod(s.id_solicitud - sb.b, p.n)
FROM (SELECT b FROM carga.r WHERE tabla = 'solicitud_devolucion') sb,
     (SELECT b, n FROM carga.r WHERE tabla = 'pedido') p
WHERE s.id_solicitud >= sb.b;

-- -----------------------------------------------------------------------------
-- 6. reserva_stock: reservar un producto QUE ESTE EN EL PEDIDO.
--    Cuidado con uq_reserva_pedido_producto_activa, un UNIQUE PARCIAL sobre
--    (id_pedido, id_producto) que solo aplica a las reservas 'activa'. Por eso
--    van en dos pasadas:
--      - las no activas pueden compartir producto: se les da la primera linea.
--      - las activas reciben una linea DISTINTA cada una, emparejando el orden
--        de la reserva con el orden de la linea. El GROUP BY de la subconsulta
--        garantiza que dentro de un pedido no se repita el producto, que es
--        exactamente lo que el indice exige.
--    Una reserva activa cuyo pedido no tenga tantas lineas se queda como
--    estaba: el JOIN no la encuentra. Sigue siendo valida.
-- -----------------------------------------------------------------------------
UPDATE reserva_stock rs
SET id_producto = d.id_producto,
    cantidad    = LEAST(rs.cantidad, d.cantidad)
FROM (SELECT DISTINCT ON (id_pedido) id_pedido, id_producto, cantidad
      FROM detalle_pedido ORDER BY id_pedido, id_detalle) d
WHERE d.id_pedido = rs.id_pedido
  AND rs.id_reserva >= (SELECT COALESCE(min(id_reserva), 0) FROM reserva_stock WHERE id_reserva > 4)
  AND rs.estado <> 'activa';

UPDATE reserva_stock rs
SET id_producto = x.id_producto,
    cantidad    = LEAST(rs.cantidad, x.cantidad)
FROM (
  SELECT r.id_reserva, d.id_producto, d.cantidad
  FROM (SELECT id_reserva, id_pedido,
               row_number() OVER (PARTITION BY id_pedido ORDER BY id_reserva) AS rk
        FROM reserva_stock WHERE id_reserva > 4 AND estado = 'activa') r
  JOIN (SELECT id_pedido, id_producto, min(cantidad) AS cantidad,
               row_number() OVER (PARTITION BY id_pedido ORDER BY id_producto) AS ln
        FROM detalle_pedido GROUP BY id_pedido, id_producto) d
    ON d.id_pedido = r.id_pedido AND d.ln = r.rk
) x
WHERE rs.id_reserva = x.id_reserva;

-- -----------------------------------------------------------------------------
-- 7. Cierre
-- -----------------------------------------------------------------------------
ANALYZE detalle_pedido; ANALYZE orden_compra_detalle; ANALYZE pedido;
ANALYZE orden_compra; ANALYZE comprobante_interno; ANALYZE reserva_stock;
ANALYZE solicitud_devolucion;

SELECT 'detalle_pedido' AS tabla, count(*) FROM detalle_pedido
UNION ALL SELECT 'orden_compra_detalle', count(*) FROM orden_compra_detalle;

SELECT count(*) AS pedidos_nuevos_sin_linea
FROM pedido p
WHERE p.id_pedido >= (SELECT b FROM carga.r WHERE tabla = 'pedido')
  AND NOT EXISTS (SELECT 1 FROM detalle_pedido d WHERE d.id_pedido = p.id_pedido);

SELECT count(*) AS ordenes_nuevas_sin_linea
FROM orden_compra o
WHERE o.id_orden_compra >= (SELECT b FROM carga.r WHERE tabla = 'orden_compra')
  AND NOT EXISTS (SELECT 1 FROM orden_compra_detalle d WHERE d.id_orden_compra = o.id_orden_compra);

SELECT round(avg(n), 2) AS lineas_por_pedido_nuevo FROM (
  SELECT count(*) AS n FROM detalle_pedido
  WHERE id_pedido >= (SELECT b FROM carga.r WHERE tabla = 'pedido')
  GROUP BY id_pedido) t;

SELECT count(*) AS comprobantes_que_no_cuadran
FROM comprobante_interno ci JOIN pedido p ON p.id_pedido = ci.id_pedido
WHERE ci.total <> p.total;

SELECT count(*) AS reservas_de_un_producto_ajeno_al_pedido
FROM reserva_stock rs
WHERE rs.id_reserva > 4
  AND NOT EXISTS (SELECT 1 FROM detalle_pedido d
                  WHERE d.id_pedido = rs.id_pedido AND d.id_producto = rs.id_producto);
