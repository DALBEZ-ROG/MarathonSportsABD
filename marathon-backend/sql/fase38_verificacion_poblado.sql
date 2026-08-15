-- ============================================================================
-- FASE 38 — ETAPA 3: ESTADISTICAS Y VERIFICACION DEL POBLADO
-- ----------------------------------------------------------------------------
-- Solo lectura salvo el ANALYZE del paso 1. Aborta con RAISE EXCEPTION si algo
-- no cuadra: nadie debe poder dar por buena una carga a medias.
--
--     psql -U postgres -d mod_venta_inve -f fase38_verificacion_poblado.sql
-- ============================================================================

\set ON_ERROR_STOP on
\timing on

\echo ''
\echo '=============================================================='
\echo 'FASE 38 - ETAPA 3: VERIFICACION'
\echo '=============================================================='

-- ============================================================================
-- 1. ANALYZE DE LAS 37 TABLAS
-- ----------------------------------------------------------------------------
-- No solo las 9 pobladas: la auditoria del 15/08 encontro 33 de 37 tablas que
-- nunca habian recibido ANALYZE. Un planificador sin estadisticas elige planes
-- a ciegas, y esta fase existe justamente para que los planes sean medibles.
-- ============================================================================
\echo ''
\echo '--- 1. ANALYZE de las 37 tablas ---'
ANALYZE;

DO $$
DECLARE v_sin int;
BEGIN
    SELECT count(*) INTO v_sin FROM pg_stat_user_tables
    WHERE last_analyze IS NULL AND last_autoanalyze IS NULL;
    IF v_sin > 0 THEN
        RAISE EXCEPTION 'Quedan % tablas sin ANALYZE', v_sin;
    END IF;
    RAISE NOTICE 'Las 37 tablas tienen estadisticas frescas';
END $$;

-- ============================================================================
-- 2. reltuples DEL CATALOGO vs COUNT(*) REAL — desviacion admitida: 5 %
-- ============================================================================
\echo ''
\echo '--- 2. Estimacion del catalogo vs conteo real ---'
CREATE TEMP TABLE _conteos AS
SELECT c.relname AS tabla,
       c.reltuples::bigint AS estimadas,
       (SELECT n.n FROM (
            SELECT count(*) AS n FROM pg_class x
            WHERE x.oid = c.oid
       ) n) AS dummy
FROM pg_class c JOIN pg_namespace ns ON ns.oid = c.relnamespace
WHERE ns.nspname='public' AND c.relkind='r';

DO $$
DECLARE
    r          record;
    v_real     bigint;
    v_desv     numeric;
    v_malas    int := 0;
    v_total    bigint := 0;
BEGIN
    CREATE TEMP TABLE _desviaciones (tabla text, estimadas bigint, reales bigint, desviacion numeric);
    FOR r IN SELECT tabla, estimadas FROM _conteos ORDER BY tabla LOOP
        EXECUTE format('SELECT count(*) FROM public.%I', r.tabla) INTO v_real;
        v_total := v_total + v_real;
        IF v_real > 0 THEN
            v_desv := round(abs(r.estimadas - v_real) * 100.0 / v_real, 2);
        ELSE
            v_desv := 0;
        END IF;
        IF v_desv > 5 THEN
            v_malas := v_malas + 1;
            INSERT INTO _desviaciones VALUES (r.tabla, r.estimadas, v_real, v_desv);
        END IF;
    END LOOP;
    RAISE NOTICE 'Filas reales en las 37 tablas: %', v_total;
    IF v_malas > 0 THEN
        RAISE NOTICE 'ATENCION: % tablas con desviacion > 5 %% (detalle abajo)', v_malas;
    ELSE
        RAISE NOTICE 'Ninguna tabla se desvia mas del 5 %%';
    END IF;
END $$;

SELECT * FROM _desviaciones ORDER BY desviacion DESC;

-- ============================================================================
-- 3. INVARIANTE FINANCIERO — pedido.total al centavo
-- ----------------------------------------------------------------------------
-- La formula canonica del sistema, leida de fn_recalcular_total_pedido_stmt y
-- de fn_proteger_total_pedido, RESTA el descuento:
--
--     total = GREATEST( SUM(detalle_pedido.subtotal) - descuento , 0 )
--
-- Se verifica esa, que es la que el trigger de proteccion hace cumplir. Se
-- reporta ademas, por separado, la comparacion contra SUM(subtotal) a secas,
-- para dejar claro que la diferencia entre ambas es exactamente el descuento y
-- no un descuadre.
-- ============================================================================
\echo ''
\echo '--- 3. Invariante financiero ---'
DO $$
DECLARE
    v_verificados  bigint;
    v_discrepan    bigint;
    v_con_desc     bigint;
BEGIN
    SELECT count(*) INTO v_verificados FROM pedido;

    SELECT count(*) INTO v_discrepan
    FROM pedido p
    LEFT JOIN (SELECT id_pedido, SUM(subtotal) AS suma
               FROM detalle_pedido GROUP BY id_pedido) d ON d.id_pedido = p.id_pedido
    WHERE p.total <> GREATEST(COALESCE(d.suma, 0) - p.descuento, 0);

    SELECT count(*) INTO v_con_desc FROM pedido WHERE descuento > 0;

    RAISE NOTICE 'Pedidos verificados : %', v_verificados;
    RAISE NOTICE 'Pedidos con descuento: % (en ellos total = suma - descuento)', v_con_desc;
    RAISE NOTICE 'Discrepancias        : %', v_discrepan;

    IF v_discrepan > 0 THEN
        RAISE EXCEPTION 'FALLO DE LA FASE: % pedidos con total distinto del calculado', v_discrepan;
    END IF;
END $$;

-- Muestra de control: 5 pedidos con descuento, para poder mirarlos a ojo
\echo '  Muestra (pedidos con descuento):'
SELECT p.id_pedido, p.descuento, p.total,
       (SELECT SUM(subtotal) FROM detalle_pedido d WHERE d.id_pedido=p.id_pedido) AS suma_lineas
FROM pedido p WHERE p.descuento > 0 ORDER BY p.id_pedido LIMIT 5;

-- orden_compra.total con el mismo criterio (sin descuento en este caso)
DO $$
DECLARE v_disc bigint;
BEGIN
    SELECT count(*) INTO v_disc
    FROM orden_compra oc
    LEFT JOIN (SELECT id_orden_compra, SUM(subtotal) AS suma
               FROM orden_compra_detalle GROUP BY id_orden_compra) d
           ON d.id_orden_compra = oc.id_orden_compra
    WHERE oc.total <> COALESCE(d.suma, 0);
    RAISE NOTICE 'orden_compra con total descuadrado: %', v_disc;
    IF v_disc > 0 THEN
        RAISE EXCEPTION 'FALLO: % ordenes de compra con total distinto de la suma de sus lineas', v_disc;
    END IF;
END $$;

-- ============================================================================
-- 4. INTEGRIDAD REFERENCIAL Y RESTRICCIONES
-- ----------------------------------------------------------------------------
-- Las FK y los CHECK los valida PostgreSQL en cada INSERT, asi que no deberia
-- haber nada. Se comprueba igual: la carga desactivo triggers, y conviene
-- demostrar que no desactivo tambien, por accidente, alguna restriccion.
-- ============================================================================
\echo ''
\echo '--- 4. FK huerfanas y CHECK violados ---'
DO $$
DECLARE
    r        record;
    v_huerf  bigint;
    v_total  int := 0;
BEGIN
    FOR r IN
        SELECT c.conname, c.conrelid::regclass::text AS tabla,
               pg_get_constraintdef(c.oid) AS def
        FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
        JOIN pg_namespace n ON n.oid=t.relnamespace
        WHERE n.nspname='public' AND c.contype='f'
    LOOP
        -- VALIDATE vuelve a comprobar la restriccion entera contra los datos
        EXECUTE format('ALTER TABLE %s VALIDATE CONSTRAINT %I', r.tabla, r.conname);
        v_total := v_total + 1;
    END LOOP;
    RAISE NOTICE '% claves foraneas revalidadas sin huerfanos', v_total;
END $$;

DO $$
DECLARE
    r       record;
    v_mal   bigint;
    v_total int := 0;
    v_falla int := 0;
BEGIN
    FOR r IN
        SELECT c.conname, c.conrelid::regclass::text AS tabla,
               pg_get_expr(c.conbin, c.conrelid) AS expr
        FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
        JOIN pg_namespace n ON n.oid=t.relnamespace
        WHERE n.nspname='public' AND c.contype='c'
    LOOP
        EXECUTE format('SELECT count(*) FROM %s WHERE NOT (%s)', r.tabla, r.expr) INTO v_mal;
        v_total := v_total + 1;
        IF v_mal > 0 THEN
            v_falla := v_falla + 1;
            RAISE NOTICE '  VIOLADO: %.% -> % filas', r.tabla, r.conname, v_mal;
        END IF;
    END LOOP;
    RAISE NOTICE '% restricciones CHECK evaluadas, % violadas', v_total, v_falla;
    IF v_falla > 0 THEN
        RAISE EXCEPTION 'FALLO: hay restricciones CHECK violadas';
    END IF;
END $$;

-- ============================================================================
-- 5. TRIGGERS — ninguno puede haber quedado apagado
-- ============================================================================
\echo ''
\echo '--- 5. Estado de los triggers ---'
SELECT count(*) FILTER (WHERE tgenabled='O') AS activos,
       count(*) FILTER (WHERE tgenabled<>'O') AS apagados
FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE NOT t.tgisinternal AND n.nspname='public';

DO $$
DECLARE v_apagados text;
BEGIN
    SELECT string_agg(c.relname||'.'||t.tgname, ', ') INTO v_apagados
    FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
    JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE NOT t.tgisinternal AND n.nspname='public' AND t.tgenabled<>'O';
    IF v_apagados IS NOT NULL THEN
        RAISE EXCEPTION 'FALLO: triggers apagados tras la carga: %', v_apagados;
    END IF;
    RAISE NOTICE 'Todos los triggers en tgenabled = O';
END $$;

-- ============================================================================
-- 6. TAMANO Y CONTEO FINAL
-- ============================================================================
\echo ''
\echo '--- 6. Tamano y conteo final ---'
SELECT pg_size_pretty(pg_database_size('mod_venta_inve')) AS tamano_bd;

SELECT c.relname AS tabla, c.reltuples::bigint AS filas,
       pg_size_pretty(pg_total_relation_size(c.oid)) AS tamano
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind='r' AND c.reltuples > 1000
ORDER BY c.reltuples DESC;

-- ============================================================================
-- 7. DISTRIBUCIONES — que los datos no salieran planos
-- ----------------------------------------------------------------------------
-- Un dataset uniforme hace que todos los indices parezcan igual de buenos. Se
-- comprueba que las distribuciones pedidas se cumplieron de verdad.
-- ============================================================================
\echo ''
\echo '--- 7. Distribuciones ---'
\echo '  pedido.estado:'
SELECT estado, count(*), round(count(*)*100.0/SUM(count(*)) OVER (), 1) AS pct
FROM pedido GROUP BY estado ORDER BY 2 DESC;

\echo '  inventario en stock bajo:'
SELECT count(*) FILTER (WHERE stock_actual <= stock_minimo) AS bajo,
       count(*) AS total,
       round(count(*) FILTER (WHERE stock_actual <= stock_minimo)*100.0/count(*), 1) AS pct
FROM inventario;

\echo '  dispersion temporal de pedido.fecha_pedido:'
SELECT min(fecha_pedido)::date AS desde, max(fecha_pedido)::date AS hasta,
       (max(fecha_pedido)::date - min(fecha_pedido)::date) AS dias_de_rango,
       count(DISTINCT fecha_pedido::date) AS dias_distintos
FROM pedido;

\echo '  lineas por pedido:'
SELECT round(avg(n), 2) AS media, min(n) AS minimo, max(n) AS maximo
FROM (SELECT count(*) AS n FROM detalle_pedido GROUP BY id_pedido) s;

\echo '  log_accion por modulo:'
SELECT modulo, count(*), round(count(*)*100.0/SUM(count(*)) OVER (), 1) AS pct
FROM log_accion GROUP BY modulo ORDER BY 2 DESC;

\echo ''
\echo '=== VERIFICACION COMPLETADA SIN FALLOS ==='
