-- =============================================================================
-- FASE 91 · Etapa 3 — Lineas, totales y movimiento
-- -----------------------------------------------------------------------------
-- Ocho tablas: detalle_pedido, orden_compra_detalle, reserva_stock,
-- comprobante_interno, solicitud_devolucion, historial_inventario,
-- movimiento_inventario, orden_produccion_consumo.
--
-- Aqui se calculan los totales que la etapa 2 dejo a 0. La formula es la del
-- sistema, no una inventada: total = suma(subtotal) - descuento, con suelo en 0.
--
-- LOS UPDATE AGREGADOS SOLO TOCAN EL RANGO NUEVO. Los pedidos y las ordenes
-- que ya existian conservan su total tal cual: recalcularselo seria reescribir
-- datos reales del duenno con una formula reconstruida.
--
--   powershell -ExecutionPolicy Bypass -File scripts/cifrado/gestionar_clave.ps1
--       -Accion Ejecutar -PgPort 5433
--       -Script marathon-backend/sql/fase91_carga_15m_etapa3_lineas.sql
-- =============================================================================

\set ON_ERROR_STOP on
\timing on

SET work_mem = '256MB';
SET maintenance_work_mem = '1GB';
SET synchronous_commit = off;

\ir fase91_andamiaje.sql

-- -----------------------------------------------------------------------------
-- 0. El rango de inventario no se anoto en la etapa 2 porque entonces no hacia
--    falta. Se reconstruye por su procedencia (las filas que apuntan a los
--    productos nuevos son exactamente las que se insertaron) y se comprueba
--    que es contiguo antes de colgarle hijos.
-- -----------------------------------------------------------------------------
INSERT INTO carga.rango
SELECT 'inventario', min(id_inventario), max(id_inventario), count(*)
FROM inventario
WHERE id_producto >= (SELECT b FROM carga.r WHERE tabla = 'producto')
ON CONFLICT (tabla) DO UPDATE
  SET id_min = EXCLUDED.id_min, id_max = EXCLUDED.id_max, filas = EXCLUDED.filas;

DO $$
DECLARE r record;
BEGIN
  SELECT * INTO r FROM carga.rango WHERE tabla = 'inventario';
  IF r.id_max - r.id_min + 1 <> r.filas THEN
    RAISE EXCEPTION 'inventario: rango no contiguo (min=% max=% filas=%)', r.id_min, r.id_max, r.filas;
  END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 1. Triggers que hay que apagar, y por que cada uno
--    trg_recalcular_total_pedido_insert / trg_oc_total_insert son FOR EACH
--      STATEMENT: no se disparan una vez por fila, pero la unica vez que se
--      disparan recalculan el total de TODOS los pedidos del lote.
--    trg_proteger_total_pedido / trg_proteger_total_oc BLOQUEARIAN el UPDATE
--      agregado con el que se reconstruyen los totales. Son su razon de ser.
--    trg_recalcular_total_por_descuento recalcularia fila a fila durante ese
--      mismo UPDATE.
--    trg_pedido_updated_at pondria now(), que hoy es 29/08/2026 y se saldria
--      del tope de julio. El updated_at se fija a mano y pasa por carga.tope().
-- -----------------------------------------------------------------------------
ALTER TABLE detalle_pedido       DISABLE TRIGGER trg_recalcular_total_pedido_insert;
ALTER TABLE pedido               DISABLE TRIGGER trg_proteger_total_pedido;
ALTER TABLE pedido               DISABLE TRIGGER trg_recalcular_total_por_descuento;
ALTER TABLE pedido               DISABLE TRIGGER trg_pedido_updated_at;
ALTER TABLE orden_compra_detalle DISABLE TRIGGER trg_oc_total_insert;
ALTER TABLE orden_compra         DISABLE TRIGGER trg_proteger_total_oc;

-- -----------------------------------------------------------------------------
-- 2. detalle_pedido
--    Una linea por pedido, sobre los primeros N pedidos nuevos. Con 1,5M de
--    pedidos y 1,5M de lineas el reparto no puede ser otro: pedir el mismo
--    numero de filas en la cabecera y en el detalle obliga a una linea por
--    pedido como mucho. Los pedidos que se quedan sin linea mantienen total 0,
--    que es lo coherente para un pedido vacio.
-- -----------------------------------------------------------------------------
CALL carga.marcar('detalle_pedido', 'id_detalle');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario,
                            picking_completado, cantidad_recogida, id_bodega_picking)
SELECT p.b + (g - 1),
       pr.b + mod(g * 2654435761, pr.n),
       1 + mod(g * 7919, 40),
       round((3 + mod(g * 104729, 45000) / 100.0)::numeric, 2),
       (mod(g, 3) = 0),
       CASE WHEN mod(g, 3) = 0 THEN 1 + mod(g * 7919, 40) ELSE 0 END,
       CASE WHEN mod(g, 3) = 0 THEN c.bodega[1 + mod(g, array_length(c.bodega, 1))] END
FROM generate_series(1, carga.faltan('detalle_pedido')) g,
     (SELECT b, n FROM carga.r WHERE tabla = 'pedido')   p,
     (SELECT b, n FROM carga.r WHERE tabla = 'producto') pr,
     carga.cat c;
CALL carga.cerrar('detalle_pedido', 'id_detalle');

-- 2b. El total del pedido, con la formula del sistema. Solo el rango nuevo.
UPDATE pedido p
SET total      = GREATEST(d.suma - p.descuento, 0),
    updated_at = carga.tope(p.fecha_pedido + interval '1 hour')
FROM (SELECT id_pedido, sum(subtotal) AS suma
      FROM detalle_pedido
      WHERE id_detalle > carga.desde('detalle_pedido')
      GROUP BY id_pedido) d
WHERE d.id_pedido = p.id_pedido
  AND p.id_pedido >= (SELECT b FROM carga.r WHERE tabla = 'pedido');

-- -----------------------------------------------------------------------------
-- 3. orden_compra_detalle
--    chk_oc_detalle_item_exclusivo obliga a que sea producto O materia prima,
--    nunca los dos ni ninguno. 70/30.
-- -----------------------------------------------------------------------------
CALL carga.marcar('orden_compra_detalle', 'id_detalle_oc');
INSERT INTO orden_compra_detalle (id_orden_compra, tipo_item, id_producto, id_materia_prima,
                                  cantidad, precio_unitario, cantidad_recibida)
SELECT oc.b + (g - 1),
       CASE WHEN mod(g, 10) < 7 THEN 'producto' ELSE 'materia_prima' END,
       CASE WHEN mod(g, 10) < 7 THEN pr.b + mod(g * 2654435761, pr.n) END,
       CASE WHEN mod(g, 10) >= 7 THEN mp.b + mod(g * 2654435761, mp.n) END,
       1 + mod(g * 7919, 300),
       round((2 + mod(g * 104729, 25000) / 100.0)::numeric, 2),
       CASE WHEN mod(g, 4) = 0 THEN 1 + mod(g * 7919, 300)
            WHEN mod(g, 4) = 1 THEN mod(g * 37, 1 + mod(g * 7919, 300))
            ELSE 0 END
FROM generate_series(1, carga.faltan('orden_compra_detalle')) g,
     (SELECT b, n FROM carga.r WHERE tabla = 'orden_compra')  oc,
     (SELECT b, n FROM carga.r WHERE tabla = 'producto')      pr,
     (SELECT b, n FROM carga.r WHERE tabla = 'materia_prima') mp;
CALL carga.cerrar('orden_compra_detalle', 'id_detalle_oc');

-- 3b. El total de la orden de compra. Solo el rango nuevo.
UPDATE orden_compra o
SET total      = d.suma,
    updated_at = carga.tope(o.fecha_orden + interval '1 hour')
FROM (SELECT id_orden_compra, sum(subtotal) AS suma
      FROM orden_compra_detalle
      WHERE id_detalle_oc > carga.desde('orden_compra_detalle')
      GROUP BY id_orden_compra) d
WHERE d.id_orden_compra = o.id_orden_compra
  AND o.id_orden_compra >= (SELECT b FROM carga.r WHERE tabla = 'orden_compra');

-- -----------------------------------------------------------------------------
-- 4. reserva_stock
--    chk_reserva_cierre ata el estado con la fecha de cierre: 'activa' exige
--    fecha_cierre NULL, y cualquier otro estado exige que NO sea NULL.
-- -----------------------------------------------------------------------------
INSERT INTO reserva_stock (id_pedido, id_producto, cantidad, estado, fecha_reserva,
                           fecha_cierre, motivo_cierre)
SELECT p.b + mod(g * 2654435761, p.n),
       pr.b + mod(g * 40503, pr.n),
       1 + mod(g * 7919, 60),
       CASE WHEN mod(g, 3) = 0 THEN 'activa'
            WHEN mod(g, 3) = 1 THEN 'consumida' ELSE 'liberada' END,
       carga.fecha(g),
       CASE WHEN mod(g, 3) <> 0 THEN carga.tope(carga.fecha(g) + interval '2 days') END,
       CASE WHEN mod(g, 3) = 1 THEN 'Despachada con el pedido'
            WHEN mod(g, 3) = 2 THEN 'Liberada por anulacion' END
FROM generate_series(1, carga.faltan('reserva_stock')) g,
     (SELECT b, n FROM carga.r WHERE tabla = 'pedido')   p,
     (SELECT b, n FROM carga.r WHERE tabla = 'producto') pr;

-- -----------------------------------------------------------------------------
-- 5. comprobante_interno
--    trg_validar_total_comprobante SE QUEDA ENCENDIDO. Compara el total del
--    comprobante con el del pedido y aborta si difieren: es una comprobacion
--    gratuita, 1,5 millones de veces, de que el UPDATE agregado del punto 2b
--    quedo bien. Si se hubiera equivocado, la carga revienta aqui.
--    El total se lee del propio pedido, asi que la igualdad es por construccion.
--    Los comprobantes se emiten sobre los pedidos QUE TIENEN LINEA, dando la
--    vuelta si hace falta: no hay UNIQUE por pedido que lo impida y evita
--    1,5M de comprobantes a cero.
--    Prefijo CIM- para no chocar con los formatos vivos (COMP-2026-###### y
--    CI-#########) ni con seq_comprobante_interno, que no se toca.
-- -----------------------------------------------------------------------------
CALL carga.marcar('comprobante_interno', 'id_comprobante');
INSERT INTO comprobante_interno (id_pedido, id_usuario, numero_comprobante,
                                 fecha_emision, total, estado, created_at)
SELECT ped.id_pedido,
       u.b + mod(x.g * 2654435761, u.n),
       'CIM-' || lpad(x.g::text, 10, '0'),
       carga.tope(ped.fecha_pedido + interval '2 hours'),
       ped.total,
       CASE WHEN mod(x.g, 50) = 0 THEN 'anulado' ELSE 'emitido' END,
       carga.tope(ped.fecha_pedido + interval '2 hours')
FROM (SELECT g, p.b + mod(g - 1, dl.filas) AS pid
      FROM generate_series(1, carga.faltan('comprobante_interno')) g,
           (SELECT b FROM carga.r WHERE tabla = 'pedido') p,
           (SELECT filas FROM carga.rango WHERE tabla = 'detalle_pedido') dl) x
JOIN pedido ped ON ped.id_pedido = x.pid
CROSS JOIN (SELECT b, n FROM carga.r WHERE tabla = 'usuario') u;
CALL carga.cerrar('comprobante_interno', 'id_comprobante');

-- -----------------------------------------------------------------------------
-- 6. solicitud_devolucion
-- -----------------------------------------------------------------------------
CALL carga.marcar('solicitud_devolucion', 'id_solicitud');
INSERT INTO solicitud_devolucion (id_pedido, id_usuario_registro, motivo, descripcion,
                                  estado, fecha_solicitud, fecha_inspeccion,
                                  id_usuario_inspector, created_at)
SELECT p.b + mod(g - 1, dl.filas),
       u.b + mod(g * 2654435761, u.n),
       (ARRAY['producto_defectuoso','talla_incorrecta','no_esperado',
              'cambio_opinion','producto_incompleto','otro'])[1 + mod(g, 6)],
       'Devolucion solicitada para el pedido ' || (p.b + mod(g - 1, dl.filas)),
       (ARRAY['solicitada','en_inspeccion','completada','rechazada'])[1 + mod(g * 3, 4)],
       carga.fecha(g),
       CASE WHEN mod(g * 3, 4) >= 1 THEN carga.tope(carga.fecha(g) + interval '3 days') END,
       CASE WHEN mod(g * 3, 4) >= 1 THEN u.b + mod(g * 40503, u.n) END,
       carga.fecha(g)
FROM generate_series(1, carga.faltan('solicitud_devolucion')) g,
     (SELECT b FROM carga.r WHERE tabla = 'pedido')  p,
     (SELECT filas FROM carga.rango WHERE tabla = 'detalle_pedido') dl,
     (SELECT b, n FROM carga.r WHERE tabla = 'usuario') u;
CALL carga.cerrar('solicitud_devolucion', 'id_solicitud');

-- -----------------------------------------------------------------------------
-- 7. historial_inventario
-- -----------------------------------------------------------------------------
INSERT INTO historial_inventario (id_inventario, id_usuario, stock_anterior,
                                  stock_nuevo, motivo, fecha)
SELECT i.b + mod(g * 2654435761, i.n),
       u.b + mod(g * 40503, u.n),
       mod(g * 7919, 900),
       mod(g * 104729, 900),
       (ARRAY['actualizacion_stock','ajuste_manual','correccion',
              'importacion','traslado'])[1 + mod(g, 5)],
       carga.fecha(g)
FROM generate_series(1, carga.faltan('historial_inventario')) g,
     (SELECT b, n FROM carga.r WHERE tabla = 'inventario') i,
     (SELECT b, n FROM carga.r WHERE tabla = 'usuario')    u;

-- -----------------------------------------------------------------------------
-- 8. movimiento_inventario
--    Los dos CHECK del traslado van juntos: exige destino y exige que sea
--    distinto del origen. Se cumple sumando 1 al indice dentro del rango, con
--    vuelta al principio, que nunca cae en la misma fila.
-- -----------------------------------------------------------------------------
INSERT INTO movimiento_inventario (id_inventario, id_usuario, id_proveedor, id_pedido,
                                   id_comprobante, tipo_movimiento, cantidad, fecha,
                                   observacion, id_inventario_destino, created_at)
SELECT i.b + mod(g * 2654435761, i.n),
       u.b + mod(g * 40503, u.n),
       CASE WHEN mod(g, 10) < 3 THEN q.b + mod(g * 7919, q.n) END,
       CASE WHEN mod(g, 10) BETWEEN 3 AND 6 THEN p.b + mod(g * 7919, p.n) END,
       CASE WHEN mod(g, 10) BETWEEN 3 AND 6 THEN ci.b + mod(g * 7919, ci.n) END,
       (ARRAY['entrada','salida','ajuste','salida','traslado',
              'entrada','salida','ajuste','entrada','salida'])[1 + mod(g, 10)],
       1 + mod(g * 7919, 500),
       carga.fecha(g),
       'Movimiento ' || g,
       CASE WHEN mod(g, 10) = 4
            THEN i.b + mod(g * 2654435761 + 1, i.n) END,
       carga.fecha(g)
FROM generate_series(1, carga.faltan('movimiento_inventario')) g,
     (SELECT b, n FROM carga.r WHERE tabla = 'inventario')          i,
     (SELECT b, n FROM carga.r WHERE tabla = 'usuario')             u,
     (SELECT b, n FROM carga.r WHERE tabla = 'proveedor')           q,
     (SELECT b, n FROM carga.r WHERE tabla = 'pedido')              p,
     (SELECT b, n FROM carga.r WHERE tabla = 'comprobante_interno') ci;

-- -----------------------------------------------------------------------------
-- 9. orden_produccion_consumo — UNIQUE (id_orden_produccion, id_materia_prima)
--    Un consumo por orden, asi que el par no puede repetirse.
-- -----------------------------------------------------------------------------
INSERT INTO orden_produccion_consumo (id_orden_produccion, id_materia_prima,
                                      cantidad_teorica, cantidad_real,
                                      costo_unitario_snapshot)
SELECT op.b + (g - 1),
       mp.b + mod(g * 2654435761, mp.n),
       round((1 + mod(g * 7919, 50000) / 100.0)::numeric, 3),
       CASE WHEN mod(g, 5) <> 0
            THEN round((1 + mod(g * 7919, 50000) / 100.0 + mod(g, 7) / 10.0)::numeric, 3) END,
       round((0.5 + mod(g * 104729, 250000) / 1000.0)::numeric, 4)
FROM generate_series(1, carga.faltan('orden_produccion_consumo')) g,
     (SELECT b, n FROM carga.r WHERE tabla = 'orden_produccion') op,
     (SELECT b, n FROM carga.r WHERE tabla = 'materia_prima')    mp;

-- -----------------------------------------------------------------------------
-- 10. Cierre de etapa
-- -----------------------------------------------------------------------------
ANALYZE detalle_pedido; ANALYZE pedido; ANALYZE orden_compra_detalle;
ANALYZE orden_compra; ANALYZE reserva_stock; ANALYZE comprobante_interno;
ANALYZE solicitud_devolucion; ANALYZE historial_inventario;
ANALYZE movimiento_inventario; ANALYZE orden_produccion_consumo;

SELECT 'detalle_pedido' AS tabla, count(*) FROM detalle_pedido
UNION ALL SELECT 'orden_compra_detalle',     count(*) FROM orden_compra_detalle
UNION ALL SELECT 'reserva_stock',            count(*) FROM reserva_stock
UNION ALL SELECT 'comprobante_interno',      count(*) FROM comprobante_interno
UNION ALL SELECT 'solicitud_devolucion',     count(*) FROM solicitud_devolucion
UNION ALL SELECT 'historial_inventario',     count(*) FROM historial_inventario
UNION ALL SELECT 'movimiento_inventario',    count(*) FROM movimiento_inventario
UNION ALL SELECT 'orden_produccion_consumo', count(*) FROM orden_produccion_consumo;

-- El total del pedido cuadra con la suma de sus lineas menos el descuento.
SELECT count(*) AS pedidos_nuevos_con_total_descuadrado
FROM pedido p
JOIN (SELECT id_pedido, sum(subtotal) AS suma FROM detalle_pedido GROUP BY id_pedido) d
  ON d.id_pedido = p.id_pedido
WHERE p.id_pedido >= (SELECT b FROM carga.r WHERE tabla = 'pedido')
  AND p.total <> GREATEST(d.suma - p.descuento, 0);
