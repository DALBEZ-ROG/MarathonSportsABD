-- ETAPA 3: indices CANDIDATOS nuevos, encima de los ya existentes.
-- Cada uno ataca el nodo mas costoso que dejo la etapa 2.
SET search_path = perf_lab;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Q1: la etapa 2 filtro por fecha con bitmap y luego descarto por estado en el
-- heap (16.438 filas leidas para devolver 11.472). Compuesto que cubre ambos.
CREATE INDEX idx_pedido_estado_fecha ON pedido (estado, fecha_pedido);

-- Q2: la etapa 2 encontro las filas por cliente pero tuvo que ordenarlas.
-- Compuesto con fecha DESC entrega el orden ya resuelto.
CREATE INDEX idx_pedido_cliente_fecha ON pedido (id_cliente, fecha_pedido DESC);

-- Q3: indice PARCIAL. Solo ~8% de las filas estan en stock bajo, asi que el
-- indice ocupa una fraccion y apunta exactamente a las filas de interes.
CREATE INDEX idx_inventario_stock_bajo ON inventario (id_bodega)
    WHERE stock_actual <= stock_minimo;

-- Q4: ILIKE '%patron%' no es indexable con btree (no hay prefijo anclado).
-- Trigramas GIN es la unica estructura que sirve para patron no anclado.
CREATE INDEX idx_producto_nombre_trgm ON producto USING gin (nombre gin_trgm_ops);

-- Q5: compuesto (modulo, fecha DESC) para resolver filtro + orden de una vez.
CREATE INDEX idx_log_modulo_fecha ON log_accion (modulo, fecha DESC);

-- Q6: indice CUBRIENTE. INCLUDE lleva las columnas agregadas al indice para
-- habilitar Index Only Scan y evitar el viaje al heap por cada pedido.
CREATE INDEX idx_detalle_pedido_cover ON detalle_pedido (id_pedido)
    INCLUDE (id_producto, cantidad, subtotal);

ANALYZE pedido; ANALYZE detalle_pedido; ANALYZE inventario; ANALYZE log_accion; ANALYZE producto;
SELECT 'etapa3: indices candidatos creados' AS estado;
