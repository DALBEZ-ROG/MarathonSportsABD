-- =============================================================================
-- Fase 93 — Que «Pedido nuevo» deje de colgar el navegador
-- =============================================================================
-- EL SINTOMA
-- Abrir /pedidos/nuevo dejaba el equipo inservible durante medio minuto, y al
-- escribir en el buscador de cliente se colgaba del todo.
--
-- LA CAUSA, MEDIDA
--   GET /api/clientes/activos  ->  299 MB y 1.439.823 clientes EN UNA RESPUESTA
--
-- El endpoint devuelve la lista entera de clientes activos para que el buscador
-- del navegador filtre sobre ella. Con las 4.620 filas que había cuando se
-- escribió, era razonable. Con el millón y medio de la F91 son 299 MB de JSON
-- que el navegador tiene que descargar, parsear y convertir en objetos — y
-- después recorrer entero, normalizando acentos cadena por cadena, en cada
-- pulsación de tecla.
--
-- LA SALIDA
-- Que filtre la base, no el navegador. El buscador pasa a pedir sólo lo que se
-- escribe y a recibir 20 filas.
--
-- Para eso hace falta que la base pueda buscar deprisa, y hoy no puede:
--
--   SELECT ... FROM cliente WHERE nombre ILIKE '%mar%' OR apellido ILIKE '%mar%'
--       -> 1.104 ms
--
-- `cliente` y `producto` tienen UN solo índice cada una, el de la clave
-- primaria. Es el mismo hallazgo que el de `log_accion` en la F92: tablas que
-- crecieron por tres órdenes de magnitud con los índices de cuando eran
-- pequeñas.
--
-- POR QUE TRIGRAMAS Y NO UN BTREE
-- Un índice btree sobre `lower(apellido)` sólo sirve para buscar por el
-- PRINCIPIO ('mar%'). Quien busca un cliente escribe lo que recuerda, y muchas
-- veces es un trozo de en medio. `pg_trgm` indexa grupos de tres letras y hace
-- que `%mar%` también use índice, que es la búsqueda que la gente hace de
-- verdad.
--
-- REVERSION: fase93_buscadores_del_pedido_rollback.sql
-- =============================================================================

-- La extensión es de PostgreSQL, no un paquete externo: viene con la
-- instalación y sólo hay que activarla.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- CONCURRENTLY para no bloquear la escritura mientras se construyen, y por eso
-- van fuera de cualquier transacción.

-- El buscador de cliente de «Pedido nuevo» busca por nombre o por apellido.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cliente_nombre_trgm
    ON cliente USING gin (nombre gin_trgm_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cliente_apellido_trgm
    ON cliente USING gin (apellido gin_trgm_ops);

-- El filtro por estado acompaña siempre a la búsqueda ('activo'), y ordenar por
-- apellido es lo que hace la consulta.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cliente_estado_apellido
    ON cliente (estado, apellido);

-- El buscador de producto de la misma pantalla, y el de la lista de productos.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_producto_nombre_trgm
    ON producto USING gin (nombre gin_trgm_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_producto_estado_nombre
    ON producto (estado, nombre);

-- Verificación
--   EXPLAIN ANALYZE SELECT id_cliente, nombre, apellido FROM cliente
--    WHERE estado = 'activo' AND (nombre ILIKE '%mar%' OR apellido ILIKE '%mar%')
--    ORDER BY apellido LIMIT 20;
--   -- esperado: Bitmap Index Scan sobre los índices trgm, no Seq Scan.
