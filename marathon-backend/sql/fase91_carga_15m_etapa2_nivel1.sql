-- =============================================================================
-- FASE 91 · Etapa 2 — Primer nivel de hijos
-- -----------------------------------------------------------------------------
-- Diez tablas que solo dependen de las seis maestras de la etapa 1:
--   producto_proveedor, inventario, lista_materiales, transportista_cobertura,
--   token_revocado, log_accion, auditoria_cambios, orden_compra, pedido,
--   orden_produccion.
--
-- pedido.total y orden_compra.total quedan a 0 a proposito: se calculan en la
-- etapa 3, cuando existan sus lineas, con un UPDATE agregado. El CHECK
-- total >= 0 se cumple mientras tanto.
--
--   powershell -ExecutionPolicy Bypass -File scripts/cifrado/gestionar_clave.ps1
--       -Accion Ejecutar -PgPort 5433
--       -Script marathon-backend/sql/fase91_carga_15m_etapa2_nivel1.sql
-- =============================================================================

\set ON_ERROR_STOP on
\timing on

SET work_mem = '256MB';
SET maintenance_work_mem = '1GB';
SET synchronous_commit = off;

\ir fase91_andamiaje.sql

-- -----------------------------------------------------------------------------
-- 1. producto_proveedor — UNIQUE (id_producto, id_proveedor)
--    Los dos rangos nuevos tienen tamanos distintos (1.499.891 y 1.499.994),
--    asi que al dar la vuelta el modulo del producto el del proveedor todavia
--    no ha vuelto: los pares siguen siendo distintos.
-- -----------------------------------------------------------------------------
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra,
                                es_proveedor_principal, estado, fecha_registro)
SELECT p.b + mod(g - 1, p.n),
       q.b + mod(g - 1, q.n),
       round((2 + mod(g * 104729, 30000) / 100.0)::numeric, 2),
       (mod(g, 3) = 0),
       CASE WHEN mod(g, 45) = 0 THEN 'inactivo' ELSE 'activo' END,
       carga.fecha(g)
FROM generate_series(1, carga.faltan('producto_proveedor')) g,
     (SELECT b, n FROM carga.r WHERE tabla = 'producto')  p,
     (SELECT b, n FROM carga.r WHERE tabla = 'proveedor') q;

-- -----------------------------------------------------------------------------
-- 2. inventario — UNIQUE (id_producto, id_bodega)
--    Un producto nuevo distinto por fila, repartido entre las 20 bodegas
--    existentes. Las 2.001 filas previas apuntan a productos con id < 610, asi
--    que no hay solape.
-- -----------------------------------------------------------------------------
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo, fecha_actualizacion)
SELECT p.b + (g - 1),
       c.bodega[1 + mod(g - 1, array_length(c.bodega, 1))],
       mod(g * 7919, 900),
       mod(g * 37, 60),
       carga.fecha(g)
FROM generate_series(1, carga.faltan('inventario')) g,
     (SELECT b, n FROM carga.r WHERE tabla = 'producto') p,
     carga.cat c;

-- -----------------------------------------------------------------------------
-- 3. lista_materiales — UNIQUE (id_producto, materia_prima), producto fabricado
--    trg_validar_bom_producto_fabricado se queda ENCENDIDO: 1,5M comprobaciones
--    gratis de que la eleccion de 'fabricado' de la etapa 1 fue correcta. Si
--    estuviera mal, la carga revienta aqui y lo dice.
--    Dos materias primas por producto fabricado, en ranuras 0 y 1, que dan
--    materias primas distintas: el par (producto, materia) nunca se repite.
-- -----------------------------------------------------------------------------
INSERT INTO lista_materiales (id_producto, id_materia_prima, cantidad_necesaria, estado, created_at)
SELECT f.id_producto,
       m.b + mod(f.rn * 3 + s, m.n),
       round((0.25 + mod(f.rn * 7919 + s, 4000) / 100.0)::numeric, 3),
       CASE WHEN mod(f.rn, 60) = 0 THEN 'inactivo' ELSE 'activo' END,
       carga.fecha(f.rn + s)
FROM (SELECT id_producto, row_number() OVER (ORDER BY id_producto) - 1 AS rn
      FROM producto
      WHERE origen = 'fabricado'
        AND id_producto >= (SELECT b FROM carga.r WHERE tabla = 'producto')) f
   , generate_series(0, 1) s
   , (SELECT b, n FROM carga.r WHERE tabla = 'materia_prima') m
LIMIT (SELECT carga.faltan('lista_materiales'));

-- -----------------------------------------------------------------------------
-- 4. transportista_cobertura — PK (id_transportista, region)
-- -----------------------------------------------------------------------------
INSERT INTO transportista_cobertura (id_transportista, region)
SELECT t.b + (g - 1), v.region[1 + mod(g, 4)]
FROM generate_series(1, carga.faltan('transportista_cobertura')) g,
     (SELECT b, n FROM carga.r WHERE tabla = 'transportista') t,
     carga.voc v;

-- -----------------------------------------------------------------------------
-- 5. token_revocado — PK jti (36 caracteres, un UUID)
--    La expiracion pasa por carga.tope(): sumarle 15 minutos a la fecha mas
--    alta se saldria del 31/07/2026 sin el.
-- -----------------------------------------------------------------------------
INSERT INTO token_revocado (jti, correo, tipo, fecha_revocacion, fecha_expiracion)
SELECT gen_random_uuid()::text,
       'masivo' || (1 + mod(g, 1499994)) || '@marathon.test',
       CASE WHEN mod(g, 4) = 0 THEN 'refresco' ELSE 'acceso' END,
       carga.fecha(g),
       carga.tope(carga.fecha(g) + interval '15 minutes')
FROM generate_series(1, carga.faltan('token_revocado')) g;

-- -----------------------------------------------------------------------------
-- 6. log_accion
-- -----------------------------------------------------------------------------
INSERT INTO log_accion (id_usuario, modulo, accion, descripcion, ip_address, fecha)
SELECT u.b + mod(g * 2654435761, u.n),
       (ARRAY['pedidos','inventario','compras','produccion','bodega','clientes','reportes','seguridad'])[1 + mod(g, 8)],
       (ARRAY['consultar','crear','actualizar','anular','aprobar','exportar','iniciar_sesion','cerrar_sesion'])[1 + mod(g * 3, 8)],
       'Operacion ' || g || ' registrada desde el modulo ' ||
         (ARRAY['pedidos','inventario','compras','produccion','bodega','clientes','reportes','seguridad'])[1 + mod(g, 8)],
       '10.' || mod(g, 256) || '.' || mod(g * 7, 256) || '.' || mod(g * 13, 256),
       carga.fecha(g)
FROM generate_series(1, carga.faltan('log_accion')) g,
     (SELECT b, n FROM carga.r WHERE tabla = 'usuario') u;

-- -----------------------------------------------------------------------------
-- 7. auditoria_cambios
--    Se puebla directamente, no a traves de los triggers: 1,5M filas escritas
--    en bloque en lugar de 6M generadas de rebote por la carga de las maestras.
--    Los valores de las columnas ocultas van como '***', igual que hace
--    fn_auditoria_cambios con password y con los campos cifrados.
-- -----------------------------------------------------------------------------
INSERT INTO auditoria_cambios (tabla, pk_valor, operacion, campo, valor_anterior,
                               valor_nuevo, usuario_bd, usuario_app, fecha, txid)
SELECT (ARRAY['producto','cliente','proveedor','usuario','rol_permiso'])[1 + mod(g, 5)],
       (1 + mod(g * 7919, 1400000))::text,
       (ARRAY['INSERT','UPDATE','UPDATE','UPDATE','DELETE'])[1 + mod(g * 3, 5)],
       (ARRAY['nombre','estado','precio','correo_enc','id_categoria'])[1 + mod(g * 11, 5)],
       CASE WHEN mod(g * 11, 5) = 3 THEN '***' ELSE 'valor_' || mod(g, 9999) END,
       CASE WHEN mod(g * 11, 5) = 3 THEN '***' ELSE 'valor_' || mod(g * 7, 9999) END,
       (ARRAY['usr_admin_marathon','usr_supervisor_marathon','usr_bodega_marathon',
              'usr_pedidos_marathon','usr_compras_marathon','usr_produccion_marathon'])[1 + mod(g, 6)],
       u.b + mod(g * 2654435761, u.n),
       carga.fecha(g),
       100000 + mod(g * 7919, 9000000)
FROM generate_series(1, carga.faltan('auditoria_cambios')) g,
     (SELECT b, n FROM carga.r WHERE tabla = 'usuario') u;

-- -----------------------------------------------------------------------------
-- 8. orden_compra
--    es_reposicion = false y id_devolucion_prov = NULL: asi se satisface
--    chk_oc_reposicion_coherente sin inventar una devolucion que aun no existe.
--    Las ordenes de reposicion reales las enlaza la etapa 5.
-- -----------------------------------------------------------------------------
CALL carga.marcar('orden_compra', 'id_orden_compra');
INSERT INTO orden_compra (id_proveedor, id_usuario_solicitante, id_usuario_aprobador,
                          fecha_orden, fecha_aprobacion, estado, total, observaciones,
                          created_at, es_reposicion)
SELECT q.b + mod(g * 2654435761, q.n),
       u.b + mod(g * 2654435761, u.n),
       CASE WHEN mod(g, 7) = 0 THEN NULL ELSE u.b + mod(g * 40503, u.n) END,
       carga.fecha(g),
       CASE WHEN mod(g, 7) = 0 THEN NULL ELSE carga.tope(carga.fecha(g) + interval '2 days') END,
       (ARRAY['borrador','pendiente_aprobacion','aprobada','recibida_parcial',
              'recibida_completa','rechazada','cancelada'])[1 + mod(g * 3, 7)],
       0,
       'Orden de compra ' || g || ', reposicion de temporada',
       carga.fecha(g),
       false
FROM generate_series(1, carga.faltan('orden_compra')) g,
     (SELECT b, n FROM carga.r WHERE tabla = 'proveedor') q,
     (SELECT b, n FROM carga.r WHERE tabla = 'usuario')   u;
CALL carga.cerrar('orden_compra', 'id_orden_compra');

-- -----------------------------------------------------------------------------
-- 9. pedido
--    total a 0 hasta que la etapa 3 lo calcule desde detalle_pedido.
--    tipo_especial solo se rellena cuando es_pedido_especial: el CHECK no
--    admite NULL explicitamente, pero un CHECK que evalua a NULL pasa, que es
--    justo el comportamiento que la columna nullable espera.
-- -----------------------------------------------------------------------------
CALL carga.marcar('pedido', 'id_pedido');
INSERT INTO pedido (id_cliente, id_usuario, fecha_pedido, total, descuento, estado,
                    created_at, es_pedido_especial, tipo_especial, nota_especial,
                    fecha_limite_entrega, numero_hu, fecha_empaque, id_transportista)
SELECT cl.b + mod(g * 2654435761, cl.n),
       u.b  + mod(g * 2654435761, u.n),
       carga.fecha(g),
       0,
       CASE WHEN mod(g, 8) = 0 THEN round((mod(g * 37, 1500) / 100.0)::numeric, 2) ELSE 0 END,
       (ARRAY['pendiente','procesado','enviado','entregado','anulado'])[1 + mod(g * 3, 5)],
       carga.fecha(g),
       (mod(g, 11) = 0),
       CASE WHEN mod(g, 11) = 0
            THEN (ARRAY['personalizado','regalo','corporativo'])[1 + mod(g, 3)] END,
       CASE WHEN mod(g, 11) = 0 THEN 'Pedido especial ' || g END,
       carga.tope(carga.fecha(g) + interval '5 days'),
       'HU-' || lpad(g::text, 9, '0'),
       CASE WHEN mod(g, 3) = 0 THEN carga.tope(carga.fecha(g) + interval '1 day') END,
       CASE WHEN mod(g, 3) = 0 THEN t.b + mod(g * 2654435761, t.n) END
FROM generate_series(1, carga.faltan('pedido')) g,
     (SELECT b, n FROM carga.r WHERE tabla = 'cliente')       cl,
     (SELECT b, n FROM carga.r WHERE tabla = 'usuario')       u,
     (SELECT b, n FROM carga.r WHERE tabla = 'transportista') t;
CALL carga.cerrar('pedido', 'id_pedido');

-- -----------------------------------------------------------------------------
-- 10. orden_produccion — el producto tiene que ser 'fabricado'
--     trg_validar_op_producto_fabricado se queda encendido, igual que en el BOM.
--     Se recorren los fabricados dos veces (no hay UNIQUE que lo impida) para
--     llegar a 1,5M con 900.000 productos.
-- -----------------------------------------------------------------------------
CALL carga.marcar('orden_produccion', 'id_orden_produccion');
INSERT INTO orden_produccion (id_producto, id_bodega_destino, id_usuario_registro,
                              id_usuario_completa, cantidad_planificada, cantidad_producida,
                              estado, fecha_creacion, fecha_inicio, fecha_fin, observaciones,
                              costo_materia_prima, costo_mano_obra, costo_indirecto)
SELECT f.id_producto,
       c.bodega[1 + mod(f.rn + s, array_length(c.bodega, 1))],
       u.b + mod((f.rn + s) * 2654435761, u.n),
       CASE WHEN mod(f.rn + s, 4) = 0
            THEN u.b + mod((f.rn + s) * 40503, u.n) END,
       10 + mod(f.rn * 7919 + s, 900),
       CASE WHEN mod(f.rn + s, 4) = 0 THEN 10 + mod(f.rn * 7919 + s, 880) END,
       CASE WHEN mod(f.rn + s, 4) = 0 THEN 'completada'
            ELSE (ARRAY['planificada','en_proceso','cancelada'])[1 + mod(f.rn + s, 3)] END,
       carga.fecha(f.rn + s),
       carga.tope(carga.fecha(f.rn + s) + interval '1 day'),
       CASE WHEN mod(f.rn + s, 4) = 0
            THEN carga.tope(carga.fecha(f.rn + s) + interval '4 days') END,
       'Orden de produccion ' || f.rn || '-' || s,
       round((mod(f.rn * 104729 + s, 400000) / 100.0)::numeric, 2),
       round((mod(f.rn * 7919 + s, 120000) / 100.0)::numeric, 2),
       round((mod(f.rn * 37 + s, 60000) / 100.0)::numeric, 2)
FROM (SELECT id_producto, row_number() OVER (ORDER BY id_producto) - 1 AS rn
      FROM producto
      WHERE origen = 'fabricado'
        AND id_producto >= (SELECT b FROM carga.r WHERE tabla = 'producto')) f
   , generate_series(0, 1) s
   , (SELECT b, n FROM carga.r WHERE tabla = 'usuario') u
   , carga.cat c
LIMIT (SELECT carga.faltan('orden_produccion'));
CALL carga.cerrar('orden_produccion', 'id_orden_produccion');

-- -----------------------------------------------------------------------------
-- 11. Cierre de etapa
-- -----------------------------------------------------------------------------
ANALYZE producto_proveedor; ANALYZE inventario; ANALYZE lista_materiales;
ANALYZE transportista_cobertura; ANALYZE token_revocado; ANALYZE log_accion;
ANALYZE auditoria_cambios; ANALYZE orden_compra; ANALYZE pedido; ANALYZE orden_produccion;

SELECT 'producto_proveedor' AS tabla, count(*) FROM producto_proveedor
UNION ALL SELECT 'inventario',              count(*) FROM inventario
UNION ALL SELECT 'lista_materiales',        count(*) FROM lista_materiales
UNION ALL SELECT 'transportista_cobertura', count(*) FROM transportista_cobertura
UNION ALL SELECT 'token_revocado',          count(*) FROM token_revocado
UNION ALL SELECT 'log_accion',              count(*) FROM log_accion
UNION ALL SELECT 'auditoria_cambios',       count(*) FROM auditoria_cambios
UNION ALL SELECT 'orden_compra',            count(*) FROM orden_compra
UNION ALL SELECT 'pedido',                  count(*) FROM pedido
UNION ALL SELECT 'orden_produccion',        count(*) FROM orden_produccion;
