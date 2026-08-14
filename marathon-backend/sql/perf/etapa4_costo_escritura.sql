-- ETAPA 4: contrapeso obligatorio. Un indice se paga en cada INSERT/UPDATE.
-- Aqui se mide (a) el tamano de cada indice, (b) el uso real de cada indice
-- segun el catalogo, y (c) el costo en escritura del indice cubriente.
SET search_path = perf_lab;

\echo '=== (a) TAMANO DE CADA INDICE ==='
SELECT indexrelname AS indice,
       pg_size_pretty(pg_relation_size(indexrelid)) AS tamano,
       idx_scan AS veces_usado
FROM pg_stat_user_indexes
WHERE schemaname = 'perf_lab'
ORDER BY pg_relation_size(indexrelid) DESC;

\echo '=== (b) Q4: cuanto costaria el indice de trigramas si se forzara su uso ==='
-- El planificador lo ignoro. Forzando, se ve si su decision fue correcta.
SET enable_seqscan = off;
EXPLAIN (ANALYZE, BUFFERS)
SELECT id_producto, nombre, precio FROM producto
WHERE nombre ILIKE '%Adidas%' AND estado = 'activo' LIMIT 50;
SET enable_seqscan = on;

\echo '=== (c) COSTO EN ESCRITURA: 30.000 INSERT con el indice cubriente ==='
\timing on
INSERT INTO detalle_pedido (id_detalle, id_pedido, id_producto, cantidad, precio_unitario)
SELECT 1000000 + g, 1 + (g % 200000), 1 + (g % 2000), 1 + (g % 4), 25.50
FROM generate_series(1, 30000) g;
\timing off

\echo '=== (c) COSTO EN ESCRITURA: mismos 30.000 INSERT SIN el indice cubriente ==='
DROP INDEX idx_detalle_pedido_cover;
\timing on
INSERT INTO detalle_pedido (id_detalle, id_pedido, id_producto, cantidad, precio_unitario)
SELECT 2000000 + g, 1 + (g % 200000), 1 + (g % 2000), 1 + (g % 4), 25.50
FROM generate_series(1, 30000) g;
\timing off

-- Dejar el laboratorio en el estado de la etapa 3 para poder repetir
CREATE INDEX idx_detalle_pedido_cover ON detalle_pedido (id_pedido)
    INCLUDE (id_producto, cantidad, subtotal);
DELETE FROM detalle_pedido WHERE id_detalle > 1000000;
ANALYZE detalle_pedido;
SELECT 'etapa4 completada' AS estado;
