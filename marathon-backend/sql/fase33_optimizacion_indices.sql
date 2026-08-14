-- ============================================================================
-- FASE 33 — OPTIMIZACION DE CONSULTAS (indices)
-- ----------------------------------------------------------------------------
-- Script IDEMPOTENTE. Se puede correr las veces que sea necesario.
--
-- Aplica las decisiones del estudio documentado en OPTIMIZACION_CONSULTAS.md.
-- Cada CREATE y cada DROP de aqui esta respaldado por una medicion
-- EXPLAIN (ANALYZE, BUFFERS) antes/despues a volumen de produccion proyectado
-- (200.000 pedidos / 600.000 detalles / 500.000 registros de auditoria),
-- ejecutada en el esquema desechable perf_lab.
--
-- POR QUE NO SE MIDIO SOBRE public: la tabla mas grande de public tiene 267
-- filas. A esa escala PostgreSQL elige Seq Scan siempre, porque leer 1-3
-- paginas es mas barato que descender un arbol B. Un EXPLAIN ANALYZE sobre
-- public no distingue un indice util de uno inutil: los dos miden ~0 ms.
-- Medir a volumen proyectado es lo que hace que las decisiones signifiquen algo.
--
-- Requisitos del proyecto respetados:
--   - No altera ninguna tabla ni columna (solo indices).
--   - No toca datos.
--   - ddl-auto sigue en none; Hibernate no gestiona nada de esto.
-- ============================================================================

\echo '=== FASE 33: optimizacion de indices ==='

-- ============================================================================
-- PARTE 1 — INDICES QUE SE CREAN (mejora medida y sostenida)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1.1  pedido (estado, fecha_pedido)
-- Consulta: reportes de ventas por rango de fechas filtrando por estado (F17).
-- Antes: Bitmap Index Scan solo por fecha -> leia 16.438 filas del heap para
--        devolver 11.472, descartando el resto por el filtro de estado.
-- Despues: el indice resuelve ambos predicados, 11.472 filas leidas.
-- Medicion: 3.199 ms -> 2.664 ms  (16.7% de mejora)
-- Tambien acelera Q6 (productos mas vendidos), que filtra igual.
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_pedido_estado_fecha
    ON pedido (estado, fecha_pedido);

-- ---------------------------------------------------------------------------
-- 1.2  pedido (id_cliente, fecha_pedido DESC)
-- Consulta: historial de pedidos de un cliente, mas reciente primero (F10).
-- Antes: Bitmap Index Scan por cliente + nodo Sort explicito.
-- Despues: Index Scan que ya entrega el orden; desaparece el Sort.
-- Medicion: 0.040 ms -> 0.017 ms  (57.5% de mejora)
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_pedido_cliente_fecha
    ON pedido (id_cliente, fecha_pedido DESC);

-- ---------------------------------------------------------------------------
-- 1.3  inventario (id_bodega) PARCIAL: solo filas en stock bajo
-- Consulta: alerta de reposicion por bodega (F7).
-- Antes: Bitmap Index Scan por bodega leia las 2.000 filas de la bodega y
--        descartaba en el heap hasta quedarse con 140.
-- Despues: el indice contiene UNICAMENTE las filas en stock bajo (~8% de la
--        tabla), asi que apunta directo a las 140. Ocupa 40 kB contra 304 kB
--        del indice completo.
-- Medicion: 0.513 ms -> 0.336 ms  (34.5% de mejora)
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_inventario_stock_bajo
    ON inventario (id_bodega)
    WHERE stock_actual <= stock_minimo;

-- ---------------------------------------------------------------------------
-- 1.4  log_accion (modulo, fecha DESC)
-- Consulta: auditoria filtrada por modulo y rango de fecha (F19b).
-- Antes: el planificador usaba idx_log_fecha y descartaba por modulo, o
--        recorria por modulo y ordenaba despues.
-- Despues: Index Scan que resuelve filtro y orden de una sola pasada.
-- Medicion: 0.231 ms -> 0.056 ms  (75.8% de mejora)
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_log_modulo_fecha
    ON log_accion (modulo, fecha DESC);

-- ============================================================================
-- PARTE 2 — INDICES QUE SE ELIMINAN (medidos y descartados)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 2.1  idx_detalle_subtotal  — ya existia en la BD, se elimina.
-- MOTIVO: 13 MB de indice con 0 usos en toda la bateria de medicion.
-- No existe caso de uso: nadie busca un detalle por su importe exacto ni hace
-- rangos sobre subtotal. Ademas subtotal es una columna GENERATED, asi que el
-- indice se reescribe en cada INSERT y en cada UPDATE de cantidad o precio.
-- Costo permanente, beneficio nulo.
-- ---------------------------------------------------------------------------
DROP INDEX IF EXISTS idx_detalle_subtotal;

-- ---------------------------------------------------------------------------
-- 2.2  idx_log_modulo  — ya existia en la BD, se elimina.
-- MOTIVO: redundante. Es el prefijo izquierdo exacto de idx_log_modulo_fecha
-- (1.4), que lo reemplaza para toda consulta que filtre por modulo, con o sin
-- fecha. Mantener los dos duplica el costo de escritura sobre una tabla que
-- crece con CADA accion del sistema. Medido: 0 usos una vez creado el
-- compuesto.
-- ---------------------------------------------------------------------------
DROP INDEX IF EXISTS idx_log_modulo;

-- ---------------------------------------------------------------------------
-- 2.3  idx_pedido_cliente  — ya existia en la BD, se elimina.
-- MOTIVO: redundante. Prefijo izquierdo de idx_pedido_cliente_fecha (1.2).
-- Una consulta que solo filtra por id_cliente usa igual el compuesto.
-- ---------------------------------------------------------------------------
DROP INDEX IF EXISTS idx_pedido_cliente;

-- ============================================================================
-- PARTE 3 — CANDIDATOS EVALUADOS Y RECHAZADOS (NO se crean)
-- ----------------------------------------------------------------------------
-- Se dejan documentados para que nadie los vuelva a proponer sin datos nuevos.
--
-- 3.1  GIN de trigramas sobre producto.nombre  (para ILIKE '%patron%')
--      CREATE EXTENSION pg_trgm;
--      CREATE INDEX idx_producto_nombre_trgm
--          ON producto USING gin (nombre gin_trgm_ops);
--      RECHAZADO. El planificador lo ignoro por completo (0 usos): con 2.000
--      productos el Seq Scan cuesta 59 unidades y cabe en memoria. Forzando su
--      uso con enable_seqscan=off para comprobar si la decision era correcta:
--          Seq Scan .............. 0.092 ms
--          Bitmap + trigramas ... 0.296 ms   (3.2x MAS LENTO)
--      El planificador tenia razon. Reevaluar solo si el catalogo pasa de
--      ~100.000 productos.
--
-- 3.2  Indice CUBRIENTE sobre detalle_pedido
--      CREATE INDEX idx_detalle_pedido_cover ON detalle_pedido (id_pedido)
--          INCLUDE (id_producto, cantidad, subtotal);
--      RECHAZADO. Si logro el Index Only Scan que se buscaba, pero el tiempo
--      total no se movio (25.794 ms -> 25.796 ms): el cuello de botella de esa
--      consulta no estaba ahi. Y el costo es real y medido:
--          30.000 INSERT con el indice ..... 256.986 ms
--          30.000 INSERT sin el indice ..... 199.347 ms   (+28.9%)
--      mas 23 MB de almacenamiento. Mejora de lectura 0%, penalizacion de
--      escritura 29%: no se justifica.
--
-- 3.3  Indices sobre FKs poco consultadas (idx_pedido_usuario, idx_log_usuario)
--      SE MANTIENEN pese a registrar 0 usos en la bateria. No se eliminan
--      porque un indice sobre la columna que referencia acelera el chequeo de
--      integridad al borrar o actualizar la fila padre, y ese costo no aparece
--      en un EXPLAIN de SELECT. Eliminarlos por "0 usos en lectura" seria leer
--      mal la evidencia.
-- ============================================================================

-- ============================================================================
-- PARTE 4 — AUTOVERIFICACION
-- ============================================================================
DO $$
DECLARE
    v_faltantes text;
    v_sobrantes text;
BEGIN
    SELECT string_agg(i.nombre, ', ')
      INTO v_faltantes
    FROM (VALUES ('idx_pedido_estado_fecha'), ('idx_pedido_cliente_fecha'),
                 ('idx_inventario_stock_bajo'), ('idx_log_modulo_fecha')) AS i(nombre)
    WHERE NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = i.nombre);

    SELECT string_agg(i.nombre, ', ')
      INTO v_sobrantes
    FROM (VALUES ('idx_detalle_subtotal'), ('idx_log_modulo'),
                 ('idx_pedido_cliente')) AS i(nombre)
    WHERE EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = i.nombre);

    IF v_faltantes IS NOT NULL THEN
        RAISE EXCEPTION 'FASE33 incompleta: no se crearon los indices %', v_faltantes;
    END IF;
    IF v_sobrantes IS NOT NULL THEN
        RAISE EXCEPTION 'FASE33 incompleta: no se eliminaron los indices %', v_sobrantes;
    END IF;

    RAISE NOTICE 'FASE 33 OK: 4 indices creados, 3 eliminados, 2 candidatos rechazados con evidencia.';
END $$;

-- Estadisticas frescas para que el planificador conozca los indices nuevos
ANALYZE pedido;
ANALYZE inventario;
ANALYZE log_accion;
ANALYZE detalle_pedido;

SELECT indexname AS indice, tablename AS tabla,
       pg_size_pretty(pg_relation_size(('public.' || quote_ident(indexname))::regclass)) AS tamano
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN ('idx_pedido_estado_fecha', 'idx_pedido_cliente_fecha',
                    'idx_inventario_stock_bajo', 'idx_log_modulo_fecha')
ORDER BY indexname;
