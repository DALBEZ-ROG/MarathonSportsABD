-- =============================================================================
-- FASE 91 · Etapa 4 — Compras, cuentas por pagar y devoluciones
-- -----------------------------------------------------------------------------
-- Las diez tablas que faltan: recepcion_mercancia, factura_compra,
-- solicitud_devolucion_detalle, recepcion_mercancia_detalle, cuenta_por_pagar,
-- reembolso_cliente, devolucion_proveedor, pago_proveedor,
-- devolucion_proveedor_detalle, movimiento_materia_prima.
--
-- Cuatro de ellas tienen un UNIQUE que las ata 1 a 1 con su padre
-- (cuenta_por_pagar->factura, reembolso->solicitud, devolucion_proveedor_detalle
-- ->recepcion_detalle). Ahi no vale generar por aritmetica: hay padres
-- PREEXISTENTES que todavia no tienen hijo, y hay que recogerlos tambien para
-- llegar al millon y medio exacto. Por eso esas tres van por antijoin
-- (NOT EXISTS) en vez de por rango.
--
--   powershell -ExecutionPolicy Bypass -File scripts/cifrado/gestionar_clave.ps1
--       -Accion Ejecutar -PgPort 5433
--       -Script marathon-backend/sql/fase91_carga_15m_etapa4_compras_devoluciones.sql
-- =============================================================================

\set ON_ERROR_STOP on
\timing on

SET work_mem = '256MB';
SET maintenance_work_mem = '1GB';
SET synchronous_commit = off;

\ir fase91_andamiaje.sql

-- trg_cxp_pagado_insert recalcularia monto_pagado de todas las cuentas del lote
-- en una sola pasada; trg_proteger_monto_pagado_cxp bloquearia el UPDATE
-- agregado que lo reconstruye. Los dos vuelven en la etapa 5.
ALTER TABLE pago_proveedor   DISABLE TRIGGER trg_cxp_pagado_insert;
ALTER TABLE cuenta_por_pagar DISABLE TRIGGER trg_proteger_monto_pagado_cxp;

-- -----------------------------------------------------------------------------
-- 1. recepcion_mercancia
-- -----------------------------------------------------------------------------
CALL carga.marcar('recepcion_mercancia', 'id_recepcion');
INSERT INTO recepcion_mercancia (id_orden_compra, id_usuario_receptor, id_bodega,
                                 fecha_recepcion, numero_guia_remision, observaciones, created_at)
SELECT oc.b + mod(g - 1, oc.n),
       u.b + mod(g * 2654435761, u.n),
       c.bodega[1 + mod(g, array_length(c.bodega, 1))],
       carga.fecha(g),
       'GR-' || lpad(g::text, 10, '0'),
       'Recepcion de mercancia ' || g,
       carga.fecha(g)
FROM generate_series(1, carga.faltan('recepcion_mercancia')) g,
     (SELECT b, n FROM carga.r WHERE tabla = 'orden_compra') oc,
     (SELECT b, n FROM carga.r WHERE tabla = 'usuario')      u,
     carga.cat c;
CALL carga.cerrar('recepcion_mercancia', 'id_recepcion');

-- -----------------------------------------------------------------------------
-- 2. factura_compra
--    Se emiten SOLO sobre ordenes que tienen linea, para que el subtotal salga
--    del total real de la orden y no de un numero inventado: chk_fc_subtotal
--    exige subtotal > 0 y una orden sin lineas vale 0. Las ordenes con linea
--    son las de indice 0..(filas de orden_compra_detalle - 1).
--    UNIQUE es (id_orden_compra, numero_factura_proveedor), asi que dar la
--    vuelta y emitir una segunda factura a una orden es legal mientras el
--    numero sea distinto, y lo es porque lleva g.
-- -----------------------------------------------------------------------------
CALL carga.marcar('factura_compra', 'id_factura_compra');
INSERT INTO factura_compra (id_orden_compra, id_usuario_registro, numero_factura_proveedor,
                            fecha_factura, fecha_vencimiento, subtotal, impuesto, estado, created_at)
SELECT o.id_orden_compra,
       u.b + mod(x.g * 2654435761, u.n),
       'FACM-' || lpad(x.g::text, 10, '0'),
       carga.fecha(x.g)::date,
       carga.tope(carga.fecha(x.g) + interval '30 days')::date,
       o.total,
       round(o.total * 0.15, 2),
       (ARRAY['pendiente','pagada','pendiente','anulada'])[1 + mod(x.g, 4)],
       carga.fecha(x.g)
FROM (SELECT g, oc.b + mod(g - 1, d.filas) AS ocid
      FROM generate_series(1, carga.faltan('factura_compra')) g,
           (SELECT b FROM carga.r WHERE tabla = 'orden_compra') oc,
           (SELECT filas FROM carga.rango WHERE tabla = 'orden_compra_detalle') d) x
JOIN orden_compra o ON o.id_orden_compra = x.ocid
CROSS JOIN (SELECT b, n FROM carga.r WHERE tabla = 'usuario') u;
CALL carga.cerrar('factura_compra', 'id_factura_compra');

-- -----------------------------------------------------------------------------
-- 3. solicitud_devolucion_detalle
--    Se devuelve una linea QUE ES DEL PEDIDO de la solicitud. No por aritmetica
--    sino por JOIN contra detalle_pedido: despues del rebalanceo de la etapa 3b
--    todo pedido tiene lineas, asi que el LATERAL siempre encuentra una, y la
--    coherencia queda demostrada por la propia consulta en vez de deducida.
--    La cantidad devuelta nunca supera la comprada: LEAST con la de la linea.
-- -----------------------------------------------------------------------------
CALL carga.marcar('solicitud_devolucion_detalle', 'id_detalle_sd');
INSERT INTO solicitud_devolucion_detalle (id_solicitud, id_detalle_pedido, cantidad_devuelta,
                                          resultado_inspeccion, observacion_inspeccion)
SELECT s.id_solicitud,
       d.id_detalle,
       LEAST(1 + mod(s.id_solicitud::bigint * 7919, 5), d.cantidad),
       CASE WHEN mod(s.id_solicitud, 4) <> 0
            THEN (ARRAY['apto_reventa','defectuoso','rechazado'])[1 + mod(s.id_solicitud, 3)] END,
       CASE WHEN mod(s.id_solicitud, 4) <> 0 THEN 'Inspeccion registrada' END
FROM solicitud_devolucion s
-- Hasta DOS lineas por solicitud: hay 1.499.197 solicitudes nuevas y hacen
-- falta 1.499.994 lineas, asi que con una por solicitud no se llega. Devolver
-- dos articulos de un mismo pedido es ademas lo normal.
JOIN LATERAL (SELECT dp.id_detalle, dp.cantidad
              FROM detalle_pedido dp
              WHERE dp.id_pedido = s.id_pedido
              ORDER BY dp.id_detalle LIMIT 2) d ON true
WHERE s.id_solicitud >= (SELECT b FROM carga.r WHERE tabla = 'solicitud_devolucion')
LIMIT (SELECT carga.faltan('solicitud_devolucion_detalle'));
CALL carga.cerrar('solicitud_devolucion_detalle', 'id_detalle_sd');

-- -----------------------------------------------------------------------------
-- 4. recepcion_mercancia_detalle
--    Se recibe una linea de compra a traves de una recepcion DE SU MISMA ORDEN,
--    por JOIN sobre id_orden_compra (idx_rm_orden lo sostiene). Antes esto se
--    hacia emparejando indices, que solo era correcto mientras hubiera una
--    linea por orden; tras el rebalanceo hay entre 1 y 3 y la aritmetica ya no
--    valdria.
--    Solo lineas de tipo 'producto': el punto 9 saca de aqui su id_producto,
--    que es NOT NULL, y una linea de materia prima no tiene ninguno.
--    Objetivo 1.550.000, por encima del 1,5M, para que el punto 9 tenga de
--    donde elegir 1,5M de lineas distintas sin quedarse corto.
-- -----------------------------------------------------------------------------
CALL carga.marcar('recepcion_mercancia_detalle', 'id_detalle_rm');
INSERT INTO recepcion_mercancia_detalle (id_recepcion, id_detalle_oc,
                                         cantidad_recibida_ahora, cantidad_defectuosa, observacion)
SELECT rm.id_recepcion,
       ocd.id_detalle_oc,
       GREATEST(ocd.cantidad / 2, 1),
       mod(ocd.id_detalle_oc::bigint * 37, GREATEST(ocd.cantidad / 8, 1)),
       CASE WHEN mod(ocd.id_detalle_oc, 9) = 0 THEN 'Bultos con embalaje danado' END
FROM orden_compra_detalle ocd
JOIN LATERAL (SELECT rr.id_recepcion FROM recepcion_mercancia rr
              WHERE rr.id_orden_compra = ocd.id_orden_compra
              ORDER BY rr.id_recepcion LIMIT 1) rm ON true
WHERE ocd.tipo_item = 'producto'
  AND ocd.id_orden_compra >= (SELECT b FROM carga.r WHERE tabla = 'orden_compra')
LIMIT (SELECT carga.faltan_hasta('recepcion_mercancia_detalle', 1550000));
CALL carga.cerrar('recepcion_mercancia_detalle', 'id_detalle_rm');

-- -----------------------------------------------------------------------------
-- 5. cuenta_por_pagar — UNIQUE (id_factura_compra), una por factura
--    Por antijoin: recoge tambien las facturas PREEXISTENTES que se habian
--    quedado sin cuenta. Con rango puro faltarian esas y no se llegaria a 1,5M.
--    monto_pagado arranca a 0; lo fija el punto 8 desde los pagos reales.
-- -----------------------------------------------------------------------------
INSERT INTO cuenta_por_pagar (id_factura_compra, monto_total, monto_pagado,
                              fecha_vencimiento, estado, created_at)
SELECT f.id_factura_compra,
       f.total,
       0,
       f.fecha_vencimiento,
       CASE WHEN mod(f.id_factura_compra, 5) = 0 THEN 'vencida' ELSE 'vigente' END,
       carga.fecha(f.id_factura_compra)
FROM factura_compra f
WHERE NOT EXISTS (SELECT 1 FROM cuenta_por_pagar c WHERE c.id_factura_compra = f.id_factura_compra)
LIMIT (SELECT carga.faltan('cuenta_por_pagar'));

-- -----------------------------------------------------------------------------
-- 6. reembolso_cliente — UNIQUE (id_solicitud), uno por solicitud. Antijoin.
-- -----------------------------------------------------------------------------
INSERT INTO reembolso_cliente (id_solicitud, id_usuario_registro, monto, metodo,
                               fecha_reembolso, observaciones)
SELECT s.id_solicitud,
       u.b + mod(s.id_solicitud * 2654435761, u.n),
       round((5 + mod(s.id_solicitud::bigint * 104729, 40000) / 100.0)::numeric, 2),
       (ARRAY['nota_credito','transferencia','efectivo'])[1 + mod(s.id_solicitud, 3)],
       carga.tope(s.fecha_solicitud + interval '5 days'),
       'Reembolso de la solicitud ' || s.id_solicitud
FROM solicitud_devolucion s
CROSS JOIN (SELECT b, n FROM carga.r WHERE tabla = 'usuario') u
WHERE NOT EXISTS (SELECT 1 FROM reembolso_cliente r WHERE r.id_solicitud = s.id_solicitud)
LIMIT (SELECT carga.faltan('reembolso_cliente'));

-- -----------------------------------------------------------------------------
-- 7. devolucion_proveedor
-- -----------------------------------------------------------------------------
CALL carga.marcar('devolucion_proveedor', 'id_devolucion_prov');
INSERT INTO devolucion_proveedor (id_proveedor, id_usuario_registro, fecha_devolucion,
                                  estado, tipo_resolucion, monto_reembolso, observaciones, created_at)
SELECT q.b + mod(g * 2654435761, q.n),
       u.b + mod(g * 40503, u.n),
       carga.fecha(g),
       (ARRAY['pendiente','enviada','resuelta','rechazada'])[1 + mod(g, 4)],
       CASE WHEN mod(g, 4) = 2 THEN (ARRAY['reembolso','reposicion'])[1 + mod(g, 2)] END,
       CASE WHEN mod(g, 4) = 2 AND mod(g, 2) = 0
            THEN round((10 + mod(g * 104729, 90000) / 100.0)::numeric, 2) END,
       'Devolucion al proveedor ' || g,
       carga.fecha(g)
FROM generate_series(1, carga.faltan('devolucion_proveedor')) g,
     (SELECT b, n FROM carga.r WHERE tabla = 'proveedor') q,
     (SELECT b, n FROM carga.r WHERE tabla = 'usuario')   u;
CALL carga.cerrar('devolucion_proveedor', 'id_devolucion_prov');

-- -----------------------------------------------------------------------------
-- 8. pago_proveedor, y despues el monto_pagado de la cuenta
--    El pago es el 30% del total de la cuenta. Al dar la vuelta, unas pocas
--    cuentas reciben dos pagos: 60%, que sigue cumpliendo chk_cxp_montos
--    (monto_pagado <= monto_total). Ese es el motivo del 30 y no del 60.
-- -----------------------------------------------------------------------------
CALL carga.marcar('pago_proveedor', 'id_pago');
INSERT INTO pago_proveedor (id_cuenta_pagar, id_usuario_registro, monto, fecha_pago,
                            metodo_pago, referencia, observaciones)
SELECT c.id_cuenta_pagar,
       u.b + mod(c.id_cuenta_pagar * 2654435761, u.n),
       GREATEST(round(c.monto_total * 0.30, 2), 0.01),
       carga.tope(c.created_at + interval '10 days'),
       (ARRAY['transferencia','cheque','efectivo','tarjeta'])[1 + mod(c.id_cuenta_pagar, 4)],
       'REF-' || lpad(c.id_cuenta_pagar::text, 10, '0'),
       'Abono parcial'
FROM cuenta_por_pagar c
CROSS JOIN (SELECT b, n FROM carga.r WHERE tabla = 'usuario') u
ORDER BY c.id_cuenta_pagar
LIMIT (SELECT carga.faltan('pago_proveedor'));
CALL carga.cerrar('pago_proveedor', 'id_pago');

UPDATE cuenta_por_pagar c
SET monto_pagado = p.suma,
    estado = CASE WHEN p.suma >= c.monto_total THEN 'pagada' ELSE c.estado END
FROM (SELECT id_cuenta_pagar, sum(monto) AS suma
      FROM pago_proveedor
      WHERE id_pago > carga.desde('pago_proveedor')
      GROUP BY id_cuenta_pagar) p
WHERE p.id_cuenta_pagar = c.id_cuenta_pagar;

-- -----------------------------------------------------------------------------
-- 9. devolucion_proveedor_detalle
--    UNIQUE en las DOS columnas de origen, y chk_dpd_origen_exclusivo obliga a
--    rellenar exactamente una. Se usa la via 'recepcion_compra', con antijoin
--    para no repetir una linea de recepcion ya devuelta.
--    El producto devuelto es EL DE LA LINEA DE COMPRA recibida, no uno
--    cualquiera: se devuelve al proveedor lo que ese proveedor mando. La
--    cantidad devuelta no supera la recibida.
-- -----------------------------------------------------------------------------
INSERT INTO devolucion_proveedor_detalle (id_devolucion_prov, origen,
                                          id_solicitud_devolucion_detalle, id_recepcion_detalle,
                                          id_producto, cantidad, motivo)
SELECT dp.b + mod(rd.id_detalle_rm, dp.n),
       'recepcion_compra',
       NULL,
       rd.id_detalle_rm,
       ocd.id_producto,
       LEAST(1 + mod(rd.id_detalle_rm::bigint * 7919, 20), rd.cantidad_recibida_ahora),
       'Mercancia defectuosa en la recepcion'
FROM recepcion_mercancia_detalle rd
JOIN orden_compra_detalle ocd ON ocd.id_detalle_oc = rd.id_detalle_oc
CROSS JOIN (SELECT b, n FROM carga.r WHERE tabla = 'devolucion_proveedor') dp
WHERE ocd.id_producto IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM devolucion_proveedor_detalle d
                  WHERE d.id_recepcion_detalle = rd.id_detalle_rm)
LIMIT (SELECT carga.faltan('devolucion_proveedor_detalle'));

-- -----------------------------------------------------------------------------
-- 10. movimiento_materia_prima
-- -----------------------------------------------------------------------------
INSERT INTO movimiento_materia_prima (id_materia_prima, id_usuario, tipo_movimiento, cantidad,
                                      stock_anterior, stock_nuevo, id_recepcion,
                                      id_orden_produccion, observacion, fecha)
SELECT mp.b + mod(g * 2654435761, mp.n),
       u.b + mod(g * 40503, u.n),
       (ARRAY['entrada_compra','salida_produccion','ajuste','merma'])[1 + mod(g, 4)],
       round((1 + mod(g * 7919, 40000) / 100.0)::numeric, 3),
       round((mod(g * 104729, 90000) / 10.0)::numeric, 3),
       round((mod(g * 40503, 90000) / 10.0)::numeric, 3),
       CASE WHEN mod(g, 4) = 0 THEN rm.b + mod(g * 7919, rm.n) END,
       CASE WHEN mod(g, 4) = 1 THEN op.b + mod(g * 7919, op.n) END,
       'Movimiento de materia prima ' || g,
       carga.fecha(g)
FROM generate_series(1, carga.faltan('movimiento_materia_prima')) g,
     (SELECT b, n FROM carga.r WHERE tabla = 'materia_prima')       mp,
     (SELECT b, n FROM carga.r WHERE tabla = 'usuario')             u,
     (SELECT b, n FROM carga.r WHERE tabla = 'recepcion_mercancia') rm,
     (SELECT b, n FROM carga.r WHERE tabla = 'orden_produccion')    op;

-- -----------------------------------------------------------------------------
-- 11. Cierre de etapa
-- -----------------------------------------------------------------------------
ANALYZE recepcion_mercancia; ANALYZE factura_compra; ANALYZE solicitud_devolucion_detalle;
ANALYZE recepcion_mercancia_detalle; ANALYZE cuenta_por_pagar; ANALYZE reembolso_cliente;
ANALYZE devolucion_proveedor; ANALYZE pago_proveedor; ANALYZE devolucion_proveedor_detalle;
ANALYZE movimiento_materia_prima;

SELECT 'recepcion_mercancia' AS tabla, count(*) FROM recepcion_mercancia
UNION ALL SELECT 'factura_compra',               count(*) FROM factura_compra
UNION ALL SELECT 'solicitud_devolucion_detalle', count(*) FROM solicitud_devolucion_detalle
UNION ALL SELECT 'recepcion_mercancia_detalle',  count(*) FROM recepcion_mercancia_detalle
UNION ALL SELECT 'cuenta_por_pagar',             count(*) FROM cuenta_por_pagar
UNION ALL SELECT 'reembolso_cliente',            count(*) FROM reembolso_cliente
UNION ALL SELECT 'devolucion_proveedor',         count(*) FROM devolucion_proveedor
UNION ALL SELECT 'pago_proveedor',               count(*) FROM pago_proveedor
UNION ALL SELECT 'devolucion_proveedor_detalle', count(*) FROM devolucion_proveedor_detalle
UNION ALL SELECT 'movimiento_materia_prima',     count(*) FROM movimiento_materia_prima;
