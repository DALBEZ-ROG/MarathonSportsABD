-- ============================================================================
-- FASE 38.1 — CIERRE Y VERIFICACION COMPLETA DEL POBLADO MASIVO
-- ----------------------------------------------------------------------------
-- IDEMPOTENTE. No carga datos: mide, y solo corrige totales que esten mal.
-- Reejecutarlo sobre una base sana no modifica una sola fila.
--
--     psql -U postgres -d mod_venta_inve -f fase38_1_cierre_verificacion.sql
--
-- POR QUE EXISTE
--   En la F38 una carga termino sin un solo error, paso todas las
--   verificaciones de integridad, y aun asi dejo 165.000 pedidos repartidos en
--   5 fechas. La leccion: las restricciones garantizan que los datos sean
--   validos, no que sean correctos ni utilizables. Aqui no se declara nada
--   correcto sin medirlo.
--
-- ORDEN DELIBERADO
--   El ANALYZE va en la etapa 4, DESPUES de corregir totales y de verificar
--   integridad. Cualquier estadistica recogida antes de la ultima escritura es
--   basura, y son justo 'fecha' y 'estado' las columnas que mas cambiaron.
-- ============================================================================

\set ON_ERROR_STOP on
\timing on

\echo ''
\echo '=============================================================='
\echo 'FASE 38.1 - CIERRE Y VERIFICACION'
\echo '=============================================================='

DO $$
BEGIN
    IF current_user <> 'postgres' THEN
        RAISE EXCEPTION 'Ejecutar como postgres (actual: %): la etapa 2 necesita DISABLE TRIGGER', current_user;
    END IF;
END $$;

-- ============================================================================
-- ETAPA 1 — BARRIDO DE INVARIANTES DE RECALCULO
-- ----------------------------------------------------------------------------
-- Las cuatro formulas salen de leer pg_get_functiondef() de cada funcion de
-- trigger, no de suponer SUM(columna). Son DISTINTAS entre si, y esa es la
-- razon de leerlas una por una:
--
--   pedido.total                       GREATEST(COALESCE(SUM(dp.subtotal),0) - descuento, 0)
--   orden_compra.total                 COALESCE(SUM(ocd.subtotal), 0)
--   cuenta_por_pagar.monto_pagado      COALESCE(SUM(pp.monto), 0)
--   orden_produccion.costo_materia_prima  ROUND(COALESCE(SUM(opc.costo_linea),0), 2)
--
-- pedido lleva descuento y suelo en 0; orden_compra no lleva ninguno de los
-- dos; orden_produccion redondea a 2 decimales dentro de la formula. Aplicar
-- la de pedido a orden_compra habria dado 2.668 falsas discrepancias, y
-- aplicar la de orden_compra a pedido habria dado 24.427.
-- ============================================================================
\echo ''
\echo '--- ETAPA 1: invariantes de recalculo ---'

DROP TABLE IF EXISTS _invariantes;
CREATE TEMP TABLE _invariantes (
    par            text,
    formula        text,
    verificadas    bigint,
    discrepancias  bigint,
    momento        text
);

CREATE OR REPLACE FUNCTION pg_temp.barrer_invariantes(p_momento text) RETURNS void AS $$
DECLARE
    v_ver bigint;
    v_dis bigint;
BEGIN
    -- 1. pedido / detalle_pedido
    SELECT count(*) INTO v_ver FROM pedido;
    SELECT count(*) INTO v_dis
    FROM pedido p
    LEFT JOIN (SELECT id_pedido, SUM(subtotal) AS s FROM detalle_pedido GROUP BY id_pedido) d
           ON d.id_pedido = p.id_pedido
    WHERE p.total IS DISTINCT FROM GREATEST(COALESCE(d.s, 0) - p.descuento, 0);
    INSERT INTO _invariantes VALUES
      ('pedido / detalle_pedido', 'GREATEST(COALESCE(SUM(subtotal),0) - descuento, 0)', v_ver, v_dis, p_momento);

    -- 2. orden_compra / orden_compra_detalle
    SELECT count(*) INTO v_ver FROM orden_compra;
    SELECT count(*) INTO v_dis
    FROM orden_compra oc
    LEFT JOIN (SELECT id_orden_compra, SUM(subtotal) AS s FROM orden_compra_detalle GROUP BY id_orden_compra) d
           ON d.id_orden_compra = oc.id_orden_compra
    WHERE oc.total IS DISTINCT FROM COALESCE(d.s, 0);
    INSERT INTO _invariantes VALUES
      ('orden_compra / orden_compra_detalle', 'COALESCE(SUM(subtotal), 0)', v_ver, v_dis, p_momento);

    -- 3. cuenta_por_pagar / pago_proveedor
    SELECT count(*) INTO v_ver FROM cuenta_por_pagar;
    SELECT count(*) INTO v_dis
    FROM cuenta_por_pagar c
    LEFT JOIN (SELECT id_cuenta_pagar, SUM(monto) AS s FROM pago_proveedor GROUP BY id_cuenta_pagar) p
           ON p.id_cuenta_pagar = c.id_cuenta_pagar
    WHERE c.monto_pagado IS DISTINCT FROM COALESCE(p.s, 0);
    INSERT INTO _invariantes VALUES
      ('cuenta_por_pagar / pago_proveedor', 'COALESCE(SUM(monto), 0)', v_ver, v_dis, p_momento);

    -- 4. orden_produccion / orden_produccion_consumo
    SELECT count(*) INTO v_ver FROM orden_produccion;
    SELECT count(*) INTO v_dis
    FROM orden_produccion o
    LEFT JOIN (SELECT id_orden_produccion, SUM(costo_linea) AS s
               FROM orden_produccion_consumo GROUP BY id_orden_produccion) c
           ON c.id_orden_produccion = o.id_orden_produccion
    WHERE o.costo_materia_prima IS DISTINCT FROM ROUND(COALESCE(c.s, 0), 2);
    INSERT INTO _invariantes VALUES
      ('orden_produccion / orden_produccion_consumo', 'ROUND(COALESCE(SUM(costo_linea),0), 2)', v_ver, v_dis, p_momento);

    -- 5. comprobante_interno / pedido  (lo impone fn_validar_total_comprobante)
    SELECT count(*) INTO v_ver FROM comprobante_interno;
    SELECT count(*) INTO v_dis
    FROM comprobante_interno ci JOIN pedido p ON p.id_pedido = ci.id_pedido
    WHERE ci.total IS DISTINCT FROM p.total;
    INSERT INTO _invariantes VALUES
      ('comprobante_interno / pedido', 'comprobante.total = pedido.total', v_ver, v_dis, p_momento);

    -- 6. cuenta_por_pagar.saldo_pendiente es GENERATED: se comprueba que la
    --    columna generada sigue coherente (no deberia poder fallar, pero es
    --    barato demostrarlo)
    SELECT count(*) INTO v_ver FROM cuenta_por_pagar;
    SELECT count(*) INTO v_dis FROM cuenta_por_pagar
    WHERE saldo_pendiente IS DISTINCT FROM (monto_total - monto_pagado);
    INSERT INTO _invariantes VALUES
      ('cuenta_por_pagar.saldo_pendiente (GENERATED)', 'monto_total - monto_pagado', v_ver, v_dis, p_momento);
END;
$$ LANGUAGE plpgsql;

SELECT pg_temp.barrer_invariantes('ANTES');
SELECT par, formula, verificadas, discrepancias FROM _invariantes WHERE momento='ANTES' ORDER BY par;

-- ============================================================================
-- ETAPA 2 — RECONSTRUCCION DE LOS TOTALES QUE ESTEN MAL
-- ----------------------------------------------------------------------------
-- Solo actua sobre los pares con discrepancias. Un UPDATE agregado UNICO por
-- par, nunca fila a fila, y con WHERE sobre la condicion de discrepancia: si no
-- hay nada mal, afecta 0 filas y la base queda intacta.
-- ============================================================================
\echo ''
\echo '--- ETAPA 2: reconstruccion (solo si hay discrepancias) ---'

DO $$
DECLARE
    v_ped bigint;
    v_oc  bigint;
    v_cxp bigint;
    v_op  bigint;
    v_n   bigint;
BEGIN
    SELECT discrepancias INTO v_ped FROM _invariantes WHERE momento='ANTES' AND par LIKE 'pedido%';
    SELECT discrepancias INTO v_oc  FROM _invariantes WHERE momento='ANTES' AND par LIKE 'orden_compra /%';
    SELECT discrepancias INTO v_cxp FROM _invariantes WHERE momento='ANTES' AND par LIKE 'cuenta_por_pagar /%';
    SELECT discrepancias INTO v_op  FROM _invariantes WHERE momento='ANTES' AND par LIKE 'orden_produccion /%';

    IF v_ped = 0 AND v_oc = 0 AND v_cxp = 0 AND v_op = 0 THEN
        RAISE NOTICE 'ETAPA 2 OMITIDA: los cuatro pares ya cuadran. No se modifica nada.';
        RETURN;
    END IF;

    -- --- pedido.total ---
    IF v_ped > 0 THEN
        RAISE NOTICE 'Reconstruyendo pedido.total (% discrepancias)', v_ped;
        ALTER TABLE pedido DISABLE TRIGGER trg_proteger_total_pedido;
        ALTER TABLE pedido DISABLE TRIGGER trg_recalcular_total_por_descuento;
        UPDATE pedido p
        SET total = GREATEST(COALESCE(d.s, 0) - p.descuento, 0)
        FROM (SELECT pp.id_pedido,
                     (SELECT SUM(subtotal) FROM detalle_pedido dd WHERE dd.id_pedido = pp.id_pedido) AS s
              FROM pedido pp) d
        WHERE d.id_pedido = p.id_pedido
          AND p.total IS DISTINCT FROM GREATEST(COALESCE(d.s, 0) - p.descuento, 0);
        GET DIAGNOSTICS v_n = ROW_COUNT;
        ALTER TABLE pedido ENABLE TRIGGER trg_proteger_total_pedido;
        ALTER TABLE pedido ENABLE TRIGGER trg_recalcular_total_por_descuento;
        RAISE NOTICE '  pedido.total: % filas corregidas', v_n;
    END IF;

    -- --- orden_compra.total ---
    IF v_oc > 0 THEN
        RAISE NOTICE 'Reconstruyendo orden_compra.total (% discrepancias)', v_oc;
        ALTER TABLE orden_compra DISABLE TRIGGER trg_proteger_total_oc;
        UPDATE orden_compra oc
        SET total = COALESCE(d.s, 0)
        FROM (SELECT o.id_orden_compra,
                     (SELECT SUM(subtotal) FROM orden_compra_detalle dd
                       WHERE dd.id_orden_compra = o.id_orden_compra) AS s
              FROM orden_compra o) d
        WHERE d.id_orden_compra = oc.id_orden_compra
          AND oc.total IS DISTINCT FROM COALESCE(d.s, 0);
        GET DIAGNOSTICS v_n = ROW_COUNT;
        ALTER TABLE orden_compra ENABLE TRIGGER trg_proteger_total_oc;
        RAISE NOTICE '  orden_compra.total: % filas corregidas', v_n;
    END IF;

    -- --- cuenta_por_pagar.monto_pagado ---
    IF v_cxp > 0 THEN
        RAISE NOTICE 'Reconstruyendo cuenta_por_pagar.monto_pagado (% discrepancias)', v_cxp;
        ALTER TABLE cuenta_por_pagar DISABLE TRIGGER trg_proteger_monto_pagado_cxp;
        UPDATE cuenta_por_pagar c
        SET monto_pagado = COALESCE(p.s, 0)
        FROM (SELECT cc.id_cuenta_pagar,
                     (SELECT SUM(monto) FROM pago_proveedor pp
                       WHERE pp.id_cuenta_pagar = cc.id_cuenta_pagar) AS s
              FROM cuenta_por_pagar cc) p
        WHERE p.id_cuenta_pagar = c.id_cuenta_pagar
          AND c.monto_pagado IS DISTINCT FROM COALESCE(p.s, 0);
        GET DIAGNOSTICS v_n = ROW_COUNT;
        ALTER TABLE cuenta_por_pagar ENABLE TRIGGER trg_proteger_monto_pagado_cxp;
        RAISE NOTICE '  cuenta_por_pagar.monto_pagado: % filas corregidas', v_n;
    END IF;

    -- --- orden_produccion.costo_materia_prima ---
    IF v_op > 0 THEN
        RAISE NOTICE 'Reconstruyendo orden_produccion.costo_materia_prima (% discrepancias)', v_op;
        ALTER TABLE orden_produccion DISABLE TRIGGER trg_proteger_costo_materia_prima_op;
        UPDATE orden_produccion o
        SET costo_materia_prima = ROUND(COALESCE(c.s, 0), 2)
        FROM (SELECT oo.id_orden_produccion,
                     (SELECT SUM(costo_linea) FROM orden_produccion_consumo cc
                       WHERE cc.id_orden_produccion = oo.id_orden_produccion) AS s
              FROM orden_produccion oo) c
        WHERE c.id_orden_produccion = o.id_orden_produccion
          AND o.costo_materia_prima IS DISTINCT FROM ROUND(COALESCE(c.s, 0), 2);
        GET DIAGNOSTICS v_n = ROW_COUNT;
        ALTER TABLE orden_produccion ENABLE TRIGGER trg_proteger_costo_materia_prima_op;
        RAISE NOTICE '  orden_produccion.costo_materia_prima: % filas corregidas', v_n;
    END IF;
END $$;

-- --- Verificacion de triggers: NO ES OPCIONAL ---
-- Un trigger de recalculo apagado no rompe nada visible: la base responde, los
-- tests de roles pasan, y el primer registro creado desde la aplicacion sale
-- con total en cero.
DO $$
DECLARE v_apagados text; v_n int;
BEGIN
    SELECT string_agg(c.relname||'.'||t.tgname, ', '), count(*)
      INTO v_apagados, v_n
    FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
    JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE NOT t.tgisinternal AND n.nspname='public' AND t.tgenabled <> 'O';
    IF v_apagados IS NOT NULL THEN
        RAISE EXCEPTION 'FALLO: % triggers apagados: %', v_n, v_apagados;
    END IF;
    SELECT count(*) INTO v_n FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
    JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE NOT t.tgisinternal AND n.nspname='public';
    RAISE NOTICE 'Triggers verificados: % de % en tgenabled = O', v_n, v_n;
END $$;

-- --- Rebarrido: los mismos invariantes, despues ---
SELECT pg_temp.barrer_invariantes('DESPUES');

\echo ''
\echo '--- Invariantes: antes vs despues ---'
SELECT a.par, a.formula, a.verificadas,
       a.discrepancias AS disc_antes,
       d.discrepancias AS disc_despues
FROM _invariantes a
JOIN _invariantes d ON d.par = a.par AND d.momento='DESPUES'
WHERE a.momento='ANTES'
ORDER BY a.par;

DO $$
DECLARE v_mal bigint;
BEGIN
    SELECT COALESCE(SUM(discrepancias),0) INTO v_mal FROM _invariantes WHERE momento='DESPUES';
    IF v_mal > 0 THEN
        RAISE EXCEPTION 'FALLO: quedan % discrepancias tras la reconstruccion', v_mal;
    END IF;
    RAISE NOTICE 'Los 6 invariantes cuadran al centavo';
END $$;

-- ============================================================================
-- ETAPA 3 — INTEGRIDAD ESTRUCTURAL DE LAS 37 TABLAS
-- ----------------------------------------------------------------------------
-- Todo se genera desde pg_constraint. Los anti-join se construyen con las
-- columnas reales de conkey/confkey, no con nombres escritos a mano.
-- ============================================================================
\echo ''
\echo '--- ETAPA 3: integridad estructural ---'

DROP TABLE IF EXISTS _integridad;
CREATE TEMP TABLE _integridad (tipo text, objeto text, tabla text, violaciones bigint);

-- 3.1 FK huerfanas (anti-join con NOT EXISTS)
DO $$
DECLARE r record; v_n bigint; v_sql text;
BEGIN
    FOR r IN
        SELECT c.conname,
               src.relname AS t_origen,
               tgt.relname AS t_destino,
               (SELECT string_agg(quote_ident(a.attname), ',' ORDER BY x.ord)
                  FROM unnest(c.conkey) WITH ORDINALITY AS x(attnum, ord)
                  JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=x.attnum) AS cols_o,
               (SELECT string_agg(quote_ident(a.attname), ',' ORDER BY x.ord)
                  FROM unnest(c.confkey) WITH ORDINALITY AS x(attnum, ord)
                  JOIN pg_attribute a ON a.attrelid=c.confrelid AND a.attnum=x.attnum) AS cols_d
        FROM pg_constraint c
        JOIN pg_class src ON src.oid = c.conrelid
        JOIN pg_class tgt ON tgt.oid = c.confrelid
        JOIN pg_namespace n ON n.oid = src.relnamespace
        WHERE c.contype='f' AND n.nspname='public'
        ORDER BY src.relname, c.conname
    LOOP
        -- MATCH SIMPLE: si alguna columna de la FK es NULL, no se comprueba
        v_sql := format(
            'SELECT count(*) FROM public.%I o WHERE (%s) IS NOT NULL
               AND NOT EXISTS (SELECT 1 FROM public.%I d WHERE (%s) = (%s))',
            r.t_origen,
            (SELECT string_agg('o.'||col, ',') FROM unnest(string_to_array(r.cols_o, ',')) col),
            r.t_destino,
            (SELECT string_agg('d.'||col, ',') FROM unnest(string_to_array(r.cols_d, ',')) col),
            (SELECT string_agg('o.'||col, ',') FROM unnest(string_to_array(r.cols_o, ',')) col)
        );
        EXECUTE v_sql INTO v_n;
        INSERT INTO _integridad VALUES ('FK', r.conname, r.t_origen, v_n);
    END LOOP;
END $$;

-- 3.2 CHECK violados
DO $$
DECLARE r record; v_n bigint;
BEGIN
    FOR r IN
        SELECT c.conname, t.relname AS tabla, pg_get_expr(c.conbin, c.conrelid) AS expr
        FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
        JOIN pg_namespace n ON n.oid=t.relnamespace
        WHERE c.contype='c' AND n.nspname='public'
        ORDER BY t.relname, c.conname
    LOOP
        EXECUTE format('SELECT count(*) FROM public.%I WHERE NOT (%s)', r.tabla, r.expr) INTO v_n;
        INSERT INTO _integridad VALUES ('CHECK', r.conname, r.tabla, v_n);
    END LOOP;
END $$;

-- 3.3 UNIQUE y PK: duplicados por agrupacion
DO $$
DECLARE r record; v_n bigint; v_cols text;
BEGIN
    FOR r IN
        SELECT c.conname, t.relname AS tabla, c.contype,
               (SELECT string_agg(quote_ident(a.attname), ',' ORDER BY x.ord)
                  FROM unnest(c.conkey) WITH ORDINALITY AS x(attnum, ord)
                  JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=x.attnum) AS cols
        FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
        JOIN pg_namespace n ON n.oid=t.relnamespace
        WHERE c.contype IN ('p','u') AND n.nspname='public'
        ORDER BY t.relname, c.conname
    LOOP
        EXECUTE format(
            'SELECT count(*) FROM (SELECT %s FROM public.%I GROUP BY %s HAVING count(*) > 1) x',
            r.cols, r.tabla, r.cols) INTO v_n;
        INSERT INTO _integridad VALUES (
            CASE WHEN r.contype='p' THEN 'PK' ELSE 'UNIQUE' END, r.conname, r.tabla, v_n);
    END LOOP;
END $$;

-- 3.4 NOT NULL: un solo scan por tabla, contando todas sus columnas obligatorias
DO $$
DECLARE r record; v_n bigint; v_expr text;
BEGIN
    FOR r IN
        SELECT t.relname AS tabla,
               string_agg(format('count(*) FILTER (WHERE %I IS NULL)', a.attname), ' + ') AS expr,
               count(*) AS n_cols
        FROM pg_attribute a
        JOIN pg_class t ON t.oid = a.attrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname='public' AND t.relkind='r'
          AND a.attnum > 0 AND NOT a.attisdropped AND a.attnotnull
        GROUP BY t.relname ORDER BY t.relname
    LOOP
        EXECUTE format('SELECT %s FROM public.%I', r.expr, r.tabla) INTO v_n;
        INSERT INTO _integridad VALUES ('NOT NULL', r.n_cols || ' columnas', r.tabla, v_n);
    END LOOP;
END $$;

\echo '  Resumen por tipo:'
SELECT tipo, count(*) AS comprobaciones, SUM(violaciones) AS violaciones_totales
FROM _integridad GROUP BY tipo ORDER BY tipo;

\echo '  Detalle de lo que NO esta en cero (debe estar vacio):'
SELECT * FROM _integridad WHERE violaciones > 0 ORDER BY tipo, tabla;

DO $$
DECLARE v_mal bigint;
BEGIN
    SELECT COALESCE(SUM(violaciones),0) INTO v_mal FROM _integridad;
    IF v_mal > 0 THEN
        RAISE EXCEPTION 'FALLO DE INTEGRIDAD: % violaciones. No se continua a la etapa 4.', v_mal;
    END IF;
    RAISE NOTICE 'Integridad estructural: 0 violaciones en % comprobaciones',
                 (SELECT count(*) FROM _integridad);
END $$;

-- ============================================================================
-- ETAPA 4 — ESTADISTICAS, AL FINAL Y NO ANTES
-- ============================================================================
\echo ''
\echo '--- ETAPA 4: estadisticas ---'
ANALYZE;

DROP TABLE IF EXISTS _stats;
CREATE TEMP TABLE _stats (tabla text, estimadas bigint, reales bigint, desviacion_pct numeric);

DO $$
DECLARE r record; v_real bigint;
BEGIN
    FOR r IN
        SELECT c.relname AS tabla, c.reltuples::bigint AS est
        FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relkind='r' ORDER BY c.relname
    LOOP
        EXECUTE format('SELECT count(*) FROM public.%I', r.tabla) INTO v_real;
        INSERT INTO _stats VALUES (r.tabla, r.est, v_real,
            CASE WHEN v_real = 0 THEN 0
                 ELSE round(abs(r.est - v_real) * 100.0 / v_real, 3) END);
    END LOOP;
END $$;

\echo '  Tablas con desviacion > 5 % (debe estar vacio):'
SELECT * FROM _stats WHERE desviacion_pct > 5 ORDER BY desviacion_pct DESC;

\echo '  reltuples vs COUNT(*) - las 37 tablas:'
SELECT tabla, estimadas, reales, desviacion_pct FROM _stats ORDER BY reales DESC;

-- IMPORTANTE: hay que filtrar por schemaname='public'.
-- pg_stat_user_tables incluye TODAS las tablas de usuario, y las TEMP que este
-- mismo script crea (_invariantes, _integridad, _stats) viven en pg_temp_N y
-- son tablas de usuario a todos los efectos. A una tabla temporal nadie le hace
-- ANALYZE, asi que su last_analyze es NULL para siempre y la comprobacion
-- fallaba por culpa de su propio andamiaje. No se veia desde otra sesion,
-- porque las temp de una sesion son invisibles para las demas.
-- Tambien se limpia el snapshot: las vistas pg_stat_* se congelan por
-- transaccion y se alimentan de forma asincrona.
DO $$
DECLARE v_sin int; v_tot bigint; v_intentos int := 0; v_cuales text;
BEGIN
    LOOP
        PERFORM pg_stat_clear_snapshot();
        SELECT count(*), string_agg(relname, ', ')
          INTO v_sin, v_cuales
        FROM pg_stat_user_tables
        WHERE schemaname = 'public'
          AND last_analyze IS NULL AND last_autoanalyze IS NULL;
        EXIT WHEN v_sin = 0 OR v_intentos >= 10;
        v_intentos := v_intentos + 1;
        PERFORM pg_sleep(0.5);
    END LOOP;

    IF v_sin > 0 THEN
        RAISE EXCEPTION 'FALLO: % tablas de public sin estadisticas tras % reintentos: %',
                        v_sin, v_intentos, v_cuales;
    END IF;
    SELECT SUM(reales) INTO v_tot FROM _stats;
    RAISE NOTICE 'Estadisticas presentes en las 37 tablas de public. Total de filas: %', v_tot;
END $$;

-- ============================================================================
-- ETAPA 4b — REPRESENTATIVIDAD
-- ----------------------------------------------------------------------------
-- Los datos pueden ser validos y aun asi inservibles. Esto es lo que la F38 no
-- midio a tiempo.
-- ============================================================================
\echo ''
\echo '--- ETAPA 4b: representatividad ---'

\echo '  pedido: dispersion temporal'
SELECT count(DISTINCT fecha_pedido::date) AS dias_distintos,
       (max(fecha_pedido)::date - min(fecha_pedido)::date) AS rango_dias,
       min(fecha_pedido)::date AS desde, max(fecha_pedido)::date AS hasta
FROM pedido;

\echo '  pedido.estado'
SELECT estado, count(*), round(count(*)*100.0/SUM(count(*)) OVER (),2) AS pct
FROM pedido GROUP BY estado ORDER BY 2 DESC;

\echo '  historial_inventario.motivo'
SELECT motivo, count(*), round(count(*)*100.0/SUM(count(*)) OVER (),2) AS pct
FROM historial_inventario GROUP BY motivo ORDER BY 2 DESC;

\echo '  movimiento_inventario.tipo_movimiento'
SELECT tipo_movimiento, count(*), round(count(*)*100.0/SUM(count(*)) OVER (),2) AS pct,
       count(*) FILTER (WHERE id_inventario_destino IS NOT NULL) AS con_destino
FROM movimiento_inventario GROUP BY tipo_movimiento ORDER BY 2 DESC;

\echo '  lineas por pedido: media y desviacion'
SELECT round(avg(n),3) AS media, round(stddev_samp(n),3) AS desv_est,
       min(n) AS minimo, max(n) AS maximo, count(*) AS pedidos_con_lineas
FROM (SELECT id_pedido, count(*) AS n FROM detalle_pedido GROUP BY id_pedido) s;

\echo '  inventario en stock bajo'
SELECT count(*) FILTER (WHERE stock_actual <= stock_minimo) AS bajo, count(*) AS total,
       round(count(*) FILTER (WHERE stock_actual <= stock_minimo)*100.0/count(*),2) AS pct
FROM inventario;

-- --- El punto que el encargo marca como sospechoso ---
-- Las 57.991 filas de historial_inventario insertadas directamente frente a las
-- 2.000 que produjo el trigger real. Si las directas fueran un bloque uniforme,
-- la tabla seria inutil para medir idx_historial_fecha.
\echo '  historial_inventario: directas vs generadas por el trigger'
SELECT CASE WHEN fecha::date = current_date AND motivo = 'actualizacion_stock'
                 AND id_usuario = 1 THEN 'trigger real (aprox)'
            ELSE 'insercion directa' END AS origen,
       count(*),
       count(DISTINCT fecha::date) AS dias_distintos,
       count(DISTINCT motivo)      AS motivos_distintos,
       count(DISTINCT id_inventario) AS inventarios_distintos,
       min(fecha)::date AS desde, max(fecha)::date AS hasta
FROM historial_inventario
GROUP BY 1 ORDER BY 2 DESC;

\echo ''
\echo '=== FASE 38.1 - CIERRE COMPLETADO SIN FALLOS ==='
