-- =============================================================================
-- Fase 94b — Los índices, esta vez sobre lo que la consulta pregunta de verdad
-- =============================================================================
-- QUÉ PASÓ
-- La F94 creó índices de trigramas sobre `proveedor.nombre`, `usuario.nombre`,
-- etc., y el filtro de Proveedores siguió tardando un segundo. Al mirar el plan:
--
--   explain select id_proveedor from proveedor
--    where lower(nombre) like lower('%distribuidora%');
--   ->  Seq Scan on proveedor
--         Filter: (lower((nombre)::text) ~~ '%distribuidora%')
--
-- El índice estaba sobre `nombre` y la consulta pregunta por `lower(nombre)`.
-- Para PostgreSQL son dos cosas distintas, y con razón: no puede saber que la
-- función no cambia el orden ni el contenido de forma relevante.
--
-- Las consultas JPQL de los listados escriben
-- `LOWER(x) LIKE LOWER(CONCAT('%', :texto, '%'))` porque JPQL no tiene ILIKE.
-- Así que el índice tiene que ser sobre la EXPRESIÓN `lower(x)`.
--
-- (Los buscadores de la F93 —/clientes/buscar, /productos/buscar— usan ILIKE
-- sobre la columna cruda, y por eso sí aprovechaban los índices anteriores. Se
-- conservan los dos juegos: cada uno sirve a un tipo de consulta.)
--
-- REVERSIÓN: fase94b_indices_sobre_lower_rollback.sql
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Proveedores: su propio filtro, y el de Órdenes de compra, Devoluciones a
-- proveedor y Cuentas por pagar, que buscan por el nombre del proveedor.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_proveedor_nombre_lower_trgm
    ON proveedor USING gin (lower(nombre) gin_trgm_ops);

-- Clientes: su filtro, y el de Pedidos y Devoluciones, que buscan por el
-- nombre del cliente del pedido.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cliente_nombre_lower_trgm
    ON cliente USING gin (lower(nombre) gin_trgm_ops);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cliente_apellido_lower_trgm
    ON cliente USING gin (lower(apellido) gin_trgm_ops);

-- Productos: su filtro, el de Inventario y el de Producción.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_producto_nombre_lower_trgm
    ON producto USING gin (lower(nombre) gin_trgm_ops);

-- Usuarios.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_usuario_nombre_lower_trgm
    ON usuario USING gin (lower(nombre) gin_trgm_ops);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_usuario_apellido_lower_trgm
    ON usuario USING gin (lower(apellido) gin_trgm_ops);

-- Materia prima.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_materia_prima_nombre_lower_trgm
    ON materia_prima USING gin (lower(nombre) gin_trgm_ops);

-- Comprobantes, por número.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_comprobante_numero_lower_trgm
    ON comprobante_interno USING gin (lower(numero_comprobante) gin_trgm_ops);

-- Cuentas por pagar busca por el número de factura del proveedor.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_factura_num_prov_lower_trgm
    ON factura_compra USING gin (lower(numero_factura_proveedor) gin_trgm_ops);

ANALYZE proveedor;
ANALYZE cliente;
ANALYZE producto;
ANALYZE usuario;
ANALYZE materia_prima;
ANALYZE comprobante_interno;
ANALYZE factura_compra;

-- Verificación: el plan tiene que decir «Bitmap Index Scan», no «Seq Scan».
--   EXPLAIN SELECT id_proveedor FROM proveedor
--    WHERE lower(nombre) LIKE lower('%distribuidora%') LIMIT 10;
