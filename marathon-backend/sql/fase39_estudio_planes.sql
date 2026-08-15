-- ============================================================================
-- FASE 39 · ETAPAS 3-4 — CATALOGO DE CONSULTAS Y ESTUDIO DE PLANES
-- ----------------------------------------------------------------------------
-- 18 consultas EXTRAIDAS DEL CODIGO, no inventadas. Cada una cita la clase Java
-- y el metodo de los que sale.
--
-- PROTOCOLO FIJO (el mismo de la F33, para que los dos estudios sean
-- comparables): EXPLAIN (ANALYZE, BUFFERS), 3 ejecuciones, se reporta la 3.a
-- (cache caliente). Las dos primeras se descartan con \o NUL.
--
-- USO — los tres parametros del planificador se pasan SIEMPRE, para que cada
-- corrida sea autodescriptiva:
--
--   psql -U postgres -d mod_venta_inve -v rpc=4   -v ecs=4GB  -v wm=4MB  \
--        -f fase39_estudio_planes.sql > perf/f39_baseline.txt
--
-- Un parametro a la vez: cambiar dos y medir una vez destruye la atribucion,
-- que es el unico motivo por el que postgresql.conf se dejo intacto durante
-- tres fases.
-- ============================================================================

\timing off
\pset pager off

SET random_page_cost     = :'rpc';
SET effective_cache_size = :'ecs';
SET work_mem             = :'wm';

\echo '=============================================================='
\echo 'FASE 39 - ESTUDIO DE PLANES'
SELECT 'random_page_cost=' || current_setting('random_page_cost') ||
       '  effective_cache_size=' || current_setting('effective_cache_size') ||
       '  work_mem=' || current_setting('work_mem') AS configuracion;
\echo '=============================================================='

-- ============================================================================
-- Q01 — Pedidos por estado y rango de fechas
-- PedidoRepository.findByEstadoAndFechaPedidoBetween(String, LocalDateTime, LocalDateTime, Pageable)
-- Indice candidato: idx_pedido_estado_fecha (estado, fecha_pedido)
-- ============================================================================
\echo ''
\echo '### Q01 pedidos por estado y rango de fechas [PedidoRepository.findByEstadoAndFechaPedidoBetween]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM pedido WHERE estado='entregado'
  AND fecha_pedido BETWEEN now()-interval '90 days' AND now()
  ORDER BY fecha_pedido DESC LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM pedido WHERE estado='entregado'
  AND fecha_pedido BETWEEN now()-interval '90 days' AND now()
  ORDER BY fecha_pedido DESC LIMIT 20;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM pedido WHERE estado='entregado'
  AND fecha_pedido BETWEEN now()-interval '90 days' AND now()
  ORDER BY fecha_pedido DESC LIMIT 20;

-- ============================================================================
-- Q02 — Pedidos de un cliente
-- PedidoRepository.findByClienteIdCliente(Integer, Pageable)
-- Indice candidato: idx_pedido_cliente_fecha (id_cliente, fecha_pedido DESC)
-- ============================================================================
\echo ''
\echo '### Q02 pedidos de un cliente [PedidoRepository.findByClienteIdCliente]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM pedido WHERE id_cliente=137
  ORDER BY fecha_pedido DESC LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM pedido WHERE id_cliente=137
  ORDER BY fecha_pedido DESC LIMIT 20;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM pedido WHERE id_cliente=137
  ORDER BY fecha_pedido DESC LIMIT 20;

-- ============================================================================
-- Q03 — Pedidos de un cliente filtrados por estado
-- PedidoRepository.findByClienteIdClienteAndEstado(Integer, String, Pageable)
-- ============================================================================
\echo ''
\echo '### Q03 pedidos de cliente + estado [PedidoRepository.findByClienteIdClienteAndEstado]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM pedido WHERE id_cliente=137 AND estado='entregado'
  ORDER BY fecha_pedido DESC LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM pedido WHERE id_cliente=137 AND estado='entregado'
  ORDER BY fecha_pedido DESC LIMIT 20;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM pedido WHERE id_cliente=137 AND estado='entregado'
  ORDER BY fecha_pedido DESC LIMIT 20;

-- ============================================================================
-- Q04 — Ventas por dia (grafico del dashboard)
-- PedidoRepository.ventasPorDia(LocalDateTime)
-- ============================================================================
\echo ''
\echo '### Q04 ventas por dia [PedidoRepository.ventasPorDia]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT CAST(fecha_pedido AS date) AS dia, COALESCE(SUM(total),0), COUNT(*)
  FROM pedido WHERE estado='entregado' AND fecha_pedido >= now()-interval '180 days'
  GROUP BY CAST(fecha_pedido AS date) ORDER BY 1;
EXPLAIN (ANALYZE, BUFFERS) SELECT CAST(fecha_pedido AS date) AS dia, COALESCE(SUM(total),0), COUNT(*)
  FROM pedido WHERE estado='entregado' AND fecha_pedido >= now()-interval '180 days'
  GROUP BY CAST(fecha_pedido AS date) ORDER BY 1;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT CAST(fecha_pedido AS date) AS dia, COALESCE(SUM(total),0), COUNT(*)
  FROM pedido WHERE estado='entregado' AND fecha_pedido >= now()-interval '180 days'
  GROUP BY CAST(fecha_pedido AS date) ORDER BY 1;

-- ============================================================================
-- Q05 — Pedidos por estado (KPI del dashboard)
-- PedidoRepository.pedidosPorEstado()
-- ============================================================================
\echo ''
\echo '### Q05 conteo por estado [PedidoRepository.pedidosPorEstado]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT estado, COUNT(*) FROM pedido GROUP BY estado;
EXPLAIN (ANALYZE, BUFFERS) SELECT estado, COUNT(*) FROM pedido GROUP BY estado;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT estado, COUNT(*) FROM pedido GROUP BY estado;

-- ============================================================================
-- Q06 — Despachos por region y fecha de empaque
-- PedidoRepository.findDespachados(String, LocalDateTime, LocalDateTime, Pageable)
-- ============================================================================
\echo ''
\echo '### Q06 despachos por region [PedidoRepository.findDespachados]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM pedido WHERE estado IN ('enviado','entregado')
  AND fecha_empaque >= now()-interval '365 days' AND fecha_empaque <= now()
  ORDER BY fecha_empaque DESC LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM pedido WHERE estado IN ('enviado','entregado')
  AND fecha_empaque >= now()-interval '365 days' AND fecha_empaque <= now()
  ORDER BY fecha_empaque DESC LIMIT 20;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM pedido WHERE estado IN ('enviado','entregado')
  AND fecha_empaque >= now()-interval '365 days' AND fecha_empaque <= now()
  ORDER BY fecha_empaque DESC LIMIT 20;

-- ============================================================================
-- Q07 — Detalle de un pedido con join a producto
-- DetallePedidoRepository.findByPedidoIdPedidoOrderByIdDetalleAsc(Integer)
-- ============================================================================
\echo ''
\echo '### Q07 detalle de pedido + producto [DetallePedidoRepository.findByPedidoIdPedido]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT d.*, p.nombre, p.precio FROM detalle_pedido d
  JOIN producto p ON p.id_producto=d.id_producto WHERE d.id_pedido=90210 ORDER BY d.id_detalle;
EXPLAIN (ANALYZE, BUFFERS) SELECT d.*, p.nombre, p.precio FROM detalle_pedido d
  JOIN producto p ON p.id_producto=d.id_producto WHERE d.id_pedido=90210 ORDER BY d.id_detalle;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT d.*, p.nombre, p.precio FROM detalle_pedido d
  JOIN producto p ON p.id_producto=d.id_producto WHERE d.id_pedido=90210 ORDER BY d.id_detalle;

-- ============================================================================
-- Q08 — Top de productos vendidos (agregacion pesada)
-- DetallePedidoRepository.topProductos(Pageable)
-- ============================================================================
\echo ''
\echo '### Q08 top productos [DetallePedidoRepository.topProductos]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT d.id_producto, p.nombre, c.nombre,
  SUM(d.cantidad) AS u, COALESCE(SUM(d.subtotal),0) AS v
  FROM detalle_pedido d JOIN pedido pe ON pe.id_pedido=d.id_pedido
  JOIN producto p ON p.id_producto=d.id_producto
  JOIN categoria c ON c.id_categoria=p.id_categoria
  WHERE pe.estado='entregado' GROUP BY d.id_producto, p.nombre, c.nombre
  ORDER BY u DESC LIMIT 10;
EXPLAIN (ANALYZE, BUFFERS) SELECT d.id_producto, p.nombre, c.nombre,
  SUM(d.cantidad) AS u, COALESCE(SUM(d.subtotal),0) AS v
  FROM detalle_pedido d JOIN pedido pe ON pe.id_pedido=d.id_pedido
  JOIN producto p ON p.id_producto=d.id_producto
  JOIN categoria c ON c.id_categoria=p.id_categoria
  WHERE pe.estado='entregado' GROUP BY d.id_producto, p.nombre, c.nombre
  ORDER BY u DESC LIMIT 10;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT d.id_producto, p.nombre, c.nombre,
  SUM(d.cantidad) AS u, COALESCE(SUM(d.subtotal),0) AS v
  FROM detalle_pedido d JOIN pedido pe ON pe.id_pedido=d.id_pedido
  JOIN producto p ON p.id_producto=d.id_producto
  JOIN categoria c ON c.id_categoria=p.id_categoria
  WHERE pe.estado='entregado' GROUP BY d.id_producto, p.nombre, c.nombre
  ORDER BY u DESC LIMIT 10;

-- ============================================================================
-- Q09 — Inventario en stock bajo (indice parcial idx_inventario_stock_bajo)
-- InventarioRepository.contarStockBajo() / findStockBajo(int)
-- ============================================================================
\echo ''
\echo '### Q09 inventario stock bajo [InventarioRepository.contarStockBajo]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT COUNT(*) FROM inventario
  WHERE stock_actual <= stock_minimo AND stock_minimo > 0;
EXPLAIN (ANALYZE, BUFFERS) SELECT COUNT(*) FROM inventario
  WHERE stock_actual <= stock_minimo AND stock_minimo > 0;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT COUNT(*) FROM inventario
  WHERE stock_actual <= stock_minimo AND stock_minimo > 0;

-- ============================================================================
-- Q10 — Inventario de un producto en todas las bodegas
-- InventarioRepository.findByProductoIdProducto(Integer)
-- ============================================================================
\echo ''
\echo '### Q10 inventario por producto [InventarioRepository.findByProductoIdProducto]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM inventario WHERE id_producto=42;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM inventario WHERE id_producto=42;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM inventario WHERE id_producto=42;

-- ============================================================================
-- Q11 — Kardex: movimientos de un producto en una bodega
-- MovimientoInventarioRepository.findByInventarioProductoIdProductoAndInventarioBodegaIdBodega(...)
-- ============================================================================
\echo ''
\echo '### Q11 kardex de inventario [MovimientoInventarioRepository.findByInventarioProducto...]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT m.* FROM movimiento_inventario m
  JOIN inventario i ON i.id_inventario=m.id_inventario
  WHERE i.id_producto=42 ORDER BY m.fecha DESC LIMIT 50;
EXPLAIN (ANALYZE, BUFFERS) SELECT m.* FROM movimiento_inventario m
  JOIN inventario i ON i.id_inventario=m.id_inventario
  WHERE i.id_producto=42 ORDER BY m.fecha DESC LIMIT 50;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT m.* FROM movimiento_inventario m
  JOIN inventario i ON i.id_inventario=m.id_inventario
  WHERE i.id_producto=42 ORDER BY m.fecha DESC LIMIT 50;

-- ============================================================================
-- Q12 — Busqueda de producto por nombre (LIKE con comodin inicial)
-- ProductoRepository.buscarConFiltros(String, String, Integer, String, Pageable)
-- La F33 rechazo un indice GIN de trigramas para este caso; con volumen real
-- se puede volver a evaluar la decision.
-- ============================================================================
\echo ''
\echo '### Q12 busqueda de producto por nombre [ProductoRepository.buscarConFiltros]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM producto
  WHERE LOWER(nombre) LIKE LOWER('%run%') AND estado='activo' LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM producto
  WHERE LOWER(nombre) LIKE LOWER('%run%') AND estado='activo' LIMIT 20;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM producto
  WHERE LOWER(nombre) LIKE LOWER('%run%') AND estado='activo' LIMIT 20;

-- ============================================================================
-- Q13 — Auditoria: log por modulo y rango de fechas
-- LogAccionRepository.buscar(Integer, String, LocalDateTime, LocalDateTime, Pageable)
-- Indice candidato: idx_log_modulo_fecha (modulo, fecha DESC)
-- ============================================================================
\echo ''
\echo '### Q13 log por modulo y fecha [LogAccionRepository.buscar]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM log_accion WHERE modulo='compras'
  AND fecha BETWEEN now()-interval '120 days' AND now() ORDER BY fecha DESC LIMIT 25;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM log_accion WHERE modulo='compras'
  AND fecha BETWEEN now()-interval '120 days' AND now() ORDER BY fecha DESC LIMIT 25;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM log_accion WHERE modulo='compras'
  AND fecha BETWEEN now()-interval '120 days' AND now() ORDER BY fecha DESC LIMIT 25;

-- ============================================================================
-- Q14 — Ordenes de compra por estado y proveedor
-- OrdenCompraRepository.findByEstadoAndProveedorIdProveedor(String, Integer, Pageable)
-- ============================================================================
\echo ''
\echo '### Q14 ordenes de compra por estado y proveedor [OrdenCompraRepository.findByEstadoAndProveedor...]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM orden_compra WHERE estado='recibida_completa'
  AND id_proveedor=3 ORDER BY fecha_orden DESC LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM orden_compra WHERE estado='recibida_completa'
  AND id_proveedor=3 ORDER BY fecha_orden DESC LIMIT 20;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM orden_compra WHERE estado='recibida_completa'
  AND id_proveedor=3 ORDER BY fecha_orden DESC LIMIT 20;

-- ============================================================================
-- Q15 — Cuentas por pagar vencidas
-- CuentaPorPagarRepository.findByFechaVencimientoLessThanAndEstado(LocalDate, String, Pageable)
-- ============================================================================
\echo ''
\echo '### Q15 cuentas por pagar vencidas [CuentaPorPagarRepository.findByFechaVencimientoLessThanAndEstado]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM cuenta_por_pagar
  WHERE fecha_vencimiento < current_date AND estado='vencida'
  ORDER BY fecha_vencimiento LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM cuenta_por_pagar
  WHERE fecha_vencimiento < current_date AND estado='vencida'
  ORDER BY fecha_vencimiento LIMIT 20;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM cuenta_por_pagar
  WHERE fecha_vencimiento < current_date AND estado='vencida'
  ORDER BY fecha_vencimiento LIMIT 20;

-- ============================================================================
-- Q16 — Saldo total pendiente por pagar
-- CuentaPorPagarRepository.sumaSaldoPendienteTotal()
-- ============================================================================
\echo ''
\echo '### Q16 saldo pendiente total [CuentaPorPagarRepository.sumaSaldoPendienteTotal]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT COALESCE(SUM(saldo_pendiente),0) FROM cuenta_por_pagar
  WHERE estado IN ('vigente','vencida');
EXPLAIN (ANALYZE, BUFFERS) SELECT COALESCE(SUM(saldo_pendiente),0) FROM cuenta_por_pagar
  WHERE estado IN ('vigente','vencida');
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT COALESCE(SUM(saldo_pendiente),0) FROM cuenta_por_pagar
  WHERE estado IN ('vigente','vencida');

-- ============================================================================
-- Q17 — Kardex de materia prima
-- MovimientoMateriaPrimaRepository.findByMateriaPrimaIdMateriaPrimaOrderByFechaDesc(Integer)
-- ============================================================================
\echo ''
\echo '### Q17 kardex de materia prima [MovimientoMateriaPrimaRepository.findByMateriaPrima...]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM movimiento_materia_prima
  WHERE id_materia_prima=120 ORDER BY fecha DESC LIMIT 50;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM movimiento_materia_prima
  WHERE id_materia_prima=120 ORDER BY fecha DESC LIMIT 50;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM movimiento_materia_prima
  WHERE id_materia_prima=120 ORDER BY fecha DESC LIMIT 50;

-- ============================================================================
-- Q18 — Ordenes de produccion por estado
-- OrdenProduccionRepository.buscar(String, ...)
-- ============================================================================
\echo ''
\echo '### Q18 ordenes de produccion por estado [OrdenProduccionRepository.buscar]'
\o NUL
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM orden_produccion WHERE estado='completada'
  ORDER BY fecha_creacion DESC LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM orden_produccion WHERE estado='completada'
  ORDER BY fecha_creacion DESC LIMIT 20;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM orden_produccion WHERE estado='completada'
  ORDER BY fecha_creacion DESC LIMIT 20;

\echo ''
\echo '=== FIN DEL CATALOGO ==='
