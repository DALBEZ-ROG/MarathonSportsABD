-- ============================================================================
-- CONSULTAS CRITICAS DEL SISTEMA  (bateria de medicion)
-- ----------------------------------------------------------------------------
-- Se ejecuta identica en cada etapa del estudio (sin indices / con indices
-- actuales / con indices candidatos) para que la comparacion sea valida.
--
-- Cada consulta se corre 3 veces y se reporta la 3a (cache caliente), de modo
-- que la diferencia medida sea de PLAN y no de I/O frio.
-- ============================================================================
SET search_path = perf_lab;
\timing off

\echo '################ Q1  Reporte de ventas por rango de fechas'
-- Modulo Reportes (F17): ventas de un trimestre agrupadas por estado.
-- Filtro por rango sobre fecha_pedido + filtro por estado.
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY OFF)
SELECT p.estado, count(*) AS pedidos, sum(p.total) AS monto
FROM pedido p
WHERE p.fecha_pedido >= now() - interval '90 days'
  AND p.estado = 'entregado'
GROUP BY p.estado;
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY OFF)
SELECT p.estado, count(*) AS pedidos, sum(p.total) AS monto
FROM pedido p
WHERE p.fecha_pedido >= now() - interval '90 days'
  AND p.estado = 'entregado'
GROUP BY p.estado;
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.estado, count(*) AS pedidos, sum(p.total) AS monto
FROM pedido p
WHERE p.fecha_pedido >= now() - interval '90 days'
  AND p.estado = 'entregado'
GROUP BY p.estado;

\echo '################ Q2  Historial de pedidos de un cliente'
-- Modulo Pedidos (F10): pantalla de historial, mas reciente primero.
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY OFF)
SELECT p.id_pedido, p.fecha_pedido, p.total, p.estado
FROM pedido p WHERE p.id_cliente = 2500
ORDER BY p.fecha_pedido DESC LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY OFF)
SELECT p.id_pedido, p.fecha_pedido, p.total, p.estado
FROM pedido p WHERE p.id_cliente = 2500
ORDER BY p.fecha_pedido DESC LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id_pedido, p.fecha_pedido, p.total, p.estado
FROM pedido p WHERE p.id_cliente = 2500
ORDER BY p.fecha_pedido DESC LIMIT 20;

\echo '################ Q3  Inventario con stock bajo en una bodega'
-- Modulo Inventario (F7): alerta de reposicion. Comparacion columna-a-columna.
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY OFF)
SELECT i.id_inventario, pr.nombre, i.stock_actual, i.stock_minimo
FROM inventario i JOIN producto pr ON pr.id_producto = i.id_producto
WHERE i.id_bodega = 7 AND i.stock_actual <= i.stock_minimo;
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY OFF)
SELECT i.id_inventario, pr.nombre, i.stock_actual, i.stock_minimo
FROM inventario i JOIN producto pr ON pr.id_producto = i.id_producto
WHERE i.id_bodega = 7 AND i.stock_actual <= i.stock_minimo;
EXPLAIN (ANALYZE, BUFFERS)
SELECT i.id_inventario, pr.nombre, i.stock_actual, i.stock_minimo
FROM inventario i JOIN producto pr ON pr.id_producto = i.id_producto
WHERE i.id_bodega = 7 AND i.stock_actual <= i.stock_minimo;

\echo '################ Q4  Busqueda de producto por nombre (parcial)'
-- Modulo Productos (F6): buscador del catalogo, patron no anclado.
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY OFF)
SELECT id_producto, nombre, precio FROM producto
WHERE nombre ILIKE '%Adidas%' AND estado = 'activo' LIMIT 50;
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY OFF)
SELECT id_producto, nombre, precio FROM producto
WHERE nombre ILIKE '%Adidas%' AND estado = 'activo' LIMIT 50;
EXPLAIN (ANALYZE, BUFFERS)
SELECT id_producto, nombre, precio FROM producto
WHERE nombre ILIKE '%Adidas%' AND estado = 'activo' LIMIT 50;

\echo '################ Q5  Auditoria por modulo y rango de fecha'
-- Modulo Auditoria (F19b): filtro combinado, el caso mas frecuente del modulo.
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY OFF)
SELECT id_log, id_usuario, accion, fecha FROM log_accion
WHERE modulo = 'compras' AND fecha >= now() - interval '30 days'
ORDER BY fecha DESC LIMIT 100;
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY OFF)
SELECT id_log, id_usuario, accion, fecha FROM log_accion
WHERE modulo = 'compras' AND fecha >= now() - interval '30 days'
ORDER BY fecha DESC LIMIT 100;
EXPLAIN (ANALYZE, BUFFERS)
SELECT id_log, id_usuario, accion, fecha FROM log_accion
WHERE modulo = 'compras' AND fecha >= now() - interval '30 days'
ORDER BY fecha DESC LIMIT 100;

\echo '################ Q6  Productos mas vendidos en un periodo'
-- Modulo Reportes (F17): join pedido-detalle-producto con agregacion.
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY OFF)
SELECT pr.id_producto, pr.nombre, sum(d.cantidad) AS unidades, sum(d.subtotal) AS monto
FROM detalle_pedido d
JOIN pedido p   ON p.id_pedido   = d.id_pedido
JOIN producto pr ON pr.id_producto = d.id_producto
WHERE p.fecha_pedido >= now() - interval '30 days' AND p.estado = 'entregado'
GROUP BY pr.id_producto, pr.nombre
ORDER BY unidades DESC LIMIT 10;
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY OFF)
SELECT pr.id_producto, pr.nombre, sum(d.cantidad) AS unidades, sum(d.subtotal) AS monto
FROM detalle_pedido d
JOIN pedido p   ON p.id_pedido   = d.id_pedido
JOIN producto pr ON pr.id_producto = d.id_producto
WHERE p.fecha_pedido >= now() - interval '30 days' AND p.estado = 'entregado'
GROUP BY pr.id_producto, pr.nombre
ORDER BY unidades DESC LIMIT 10;
EXPLAIN (ANALYZE, BUFFERS)
SELECT pr.id_producto, pr.nombre, sum(d.cantidad) AS unidades, sum(d.subtotal) AS monto
FROM detalle_pedido d
JOIN pedido p   ON p.id_pedido   = d.id_pedido
JOIN producto pr ON pr.id_producto = d.id_producto
WHERE p.fecha_pedido >= now() - interval '30 days' AND p.estado = 'entregado'
GROUP BY pr.id_producto, pr.nombre
ORDER BY unidades DESC LIMIT 10;
