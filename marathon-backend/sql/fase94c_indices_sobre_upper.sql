-- =============================================================================
-- Fase 94c — El tercer juego de índices, y por qué hacían falta tres
-- =============================================================================
-- La misma columna se busca de TRES formas distintas en este proyecto, y para
-- PostgreSQL cada una es una expresión diferente que necesita su propio índice.
-- Hasta enterarse de esto, «Proveedores · filtro nombre» seguía tardando casi un
-- segundo con dos índices de trigramas ya creados encima de esa columna.
--
--   1. `nombre ILIKE ?`
--        Lo escriben los buscadores nativos de la F93 (/clientes/buscar,
--        /productos/buscar). Índice: gin (nombre gin_trgm_ops)        [F93/F94]
--
--   2. `LOWER(nombre) LIKE LOWER(?)`
--        Lo escriben las consultas @Query en JPQL, porque JPQL no tiene ILIKE.
--        Índice: gin (lower(nombre) gin_trgm_ops)                     [F94b]
--
--   3. `UPPER(nombre) LIKE UPPER(?)`
--        Lo genera SPRING DATA a partir del nombre del método
--        `findByNombreContainingIgnoreCase`. Y es UPPER, no LOWER: es una
--        decisión interna de Spring Data que no se ve en ninguna parte del
--        código del proyecto.                                         [ESTE]
--
-- Cómo se descubrió: mirando el SQL de verdad en el registro de PostgreSQL.
--
--     select ... from proveedor p1_0 where upper(p1_0.nombre) like upper(?)
--
-- La lección, para la próxima: antes de crear un índice, leer la sentencia que
-- la aplicación manda de verdad. Deducirla del código Java falla, porque quien
-- la escribe es el ORM.
--
-- REVERSIÓN: fase94c_indices_sobre_upper_rollback.sql
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Solo para las tablas grandes. Categorías, ciudades, bodegas y unidades de
-- medida también usan `ContainingIgnoreCase`, pero tienen decenas de filas:
-- recorrerlas enteras es más rápido que consultar un índice, y un índice de más
-- es escritura de más en cada alta.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_proveedor_nombre_upper_trgm
    ON proveedor USING gin (upper(nombre) gin_trgm_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_producto_nombre_upper_trgm
    ON producto USING gin (upper(nombre) gin_trgm_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_materia_prima_nombre_upper_trgm
    ON materia_prima USING gin (upper(nombre) gin_trgm_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_comprobante_numero_upper_trgm
    ON comprobante_interno USING gin (upper(numero_comprobante) gin_trgm_ops);

ANALYZE proveedor;
ANALYZE producto;
ANALYZE materia_prima;
ANALYZE comprobante_interno;

-- Verificación: tiene que decir «Bitmap Index Scan», no «Seq Scan».
--   EXPLAIN SELECT id_proveedor FROM proveedor
--    WHERE upper(nombre) LIKE upper('%distribuidora%') LIMIT 10;
