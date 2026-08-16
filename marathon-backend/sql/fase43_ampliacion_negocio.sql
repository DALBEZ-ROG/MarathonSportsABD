-- ============================================================================
-- FASE 43 — AMPLIACION DEL VOLUMEN DE NEGOCIO A 1.000.000 DE FILAS
-- ============================================================================
-- Contexto. La F38 dejo la base en 1.041.830 filas, pero 260.062 de ellas son
-- bitacoras y no negocio:
--
--     log_accion            200.062
--     historial_inventario   60.000
--     auditoria_cambios          35
--     ------------------------------
--     filas de negocio      781.733
--
-- Si el requisito 7 se lee como "1.000.000 de filas EN LAS TABLAS DE NEGOCIO",
-- faltan 218.267. Esta fase las carga donde corresponde —pedidos y sus lineas—
-- y no inflando una tabla de log, que es justo lo que el requisito excluye.
--
-- Objetivo:  pedido          165.000 -> 230.000   (+65.000)
--            detalle_pedido  450.000 -> ~614.000  (+~164.000, media 2,53/pedido)
--            filas de negocio 781.733 -> ~1.011.000
--
-- OJO CON LA MEDIA DE LINEAS POR PEDIDO. POBLADO_MASIVO.md dice "media ~2,78",
-- pero el generador que documenta produce 2,53, y la primera corrida de esta
-- fase lo confirmo: 151.731 lineas para 60.000 pedidos. El motivo es que el
-- CASE anidado vuelve a llamar a random() en CADA rama:
--
--     CASE WHEN random() < 0.10 THEN 1 WHEN random() < 0.45 THEN 2 ...
--
-- La segunda rama no se evalua sobre "el 90 % restante segun la MISMA tirada",
-- sino sobre una tirada nueva, asi que las probabilidades reales son
-- 0,100 / 0,405 / 0,371 / 0,114 / 0,010 -> esperanza 2,53, no 2,78. La F38
-- llegaba a 2,73 solo porque un bucle de relleno anadia lineas sueltas hasta
-- cuadrar los 450.000 exactos. Aqui no se corrige el generador (cambiarlo
-- deformaria la distribucion ya medida en la F39): se ajusta el numero de
-- pedidos usando la media REAL medida.
--
-- ----------------------------------------------------------------------------
-- REGLAS HEREDADAS DE LA F38 (POBLADO_MASIVO.md), TODAS RESPETADAS
-- ----------------------------------------------------------------------------
--  1. Cero DDL estructural. Los DISABLE/ENABLE TRIGGER son temporales.
--  2. Las PK son GENERATED ALWAYS AS IDENTITY: los hijos se enlazan con
--     INSERT ... SELECT ... FROM pedido, nunca con rangos de id inventados.
--  3. detalle_pedido.subtotal es GENERATED: se OMITE de la lista de columnas.
--  4. pedido.total NUNCA se escribe fila a fila. Se reconstruye con un unico
--     UPDATE agregado con la formula canonica:
--         total = GREATEST( SUM(detalle_pedido.subtotal) - pedido.descuento, 0 )
--  5. Las expresiones volatiles (random(), now()) van en el SELECT de una
--     subconsulta sobre generate_series, NUNCA en un CROSS JOIN LATERAL sin
--     correlacionar: PostgreSQL lo evalua UNA SOLA VEZ por sentencia y el lote
--     entero saldria con la misma fecha (el fallo de la primera pasada de F38).
--
-- ----------------------------------------------------------------------------
-- LO QUE ESTA FASE HACE DISTINTO, Y POR QUE
-- ----------------------------------------------------------------------------
-- La F38 recalculaba el total de los 165.000 pedidos de una vez. Aqui eso seria
-- un error: 30.000 de esos pedidos ya tienen comprobante_interno emitido, y
-- trg_validar_total_comprobante exige que el total del comprobante coincida con
-- el del pedido. Reescribir totales ya facturados descuadraria la facturacion.
--
-- Por eso todo el trabajo se acota a los pedidos NUEVOS, identificados como
-- "los que no tienen ninguna linea". Verificado antes de empezar: hoy hay
-- 0 pedidos sin lineas, asi que el conjunto es exacto y el script es
-- reejecutable sin tocar una sola fila preexistente.
--
-- Los pedidos nuevos NO se facturan: comprobante_interno se queda en 30.000.
-- Emitir comprobantes exigiria tocar numeracion fiscal correlativa, que no es
-- lo que pide el requisito de volumen.
--
-- Ejecucion:  psql -U postgres -d mod_venta_inve -f fase43_ampliacion_negocio.sql
-- No usar el MCP: envuelve todo en una transaccion y los COMMIT por lote fallan.
-- ============================================================================

\set ON_ERROR_STOP on
\timing on

\echo ''
\echo '============================================================'
\echo 'FASE 43 - AMPLIACION DEL VOLUMEN DE NEGOCIO'
\echo '============================================================'

-- ============================================================================
-- 1. ESTADO INICIAL  (se guarda para comparar al final)
-- ============================================================================
\echo ''
\echo '--- 1. Estado inicial ---'

-- TEMP a proposito, no una tabla en public: una tabla real aqui seria DDL
-- estructural (prohibido por la regla 1) y ademas se contaria a si misma en el
-- recuento final del paso 8.5. La TEMP sobrevive a los COMMIT de los lotes.
--
-- Se cuenta con COUNT(*), NO con pg_stat_user_tables.n_live_tup. n_live_tup es
-- un ESTIMADOR que el recolector de estadisticas actualiza de forma asincrona:
-- en la primera corrida de esta fase declaro 235.000 pedidos y 627.009 lineas
-- cuando las cifras reales eran 230.000 y 614.370. Un requisito que se mide en
-- numero de filas no puede verificarse con una estimacion.
DROP TABLE IF EXISTS _f43_antes;
CREATE TEMP TABLE _f43_antes AS
SELECT relname,
       (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM public.%I', relname),
                                            false, true, '')))[1]::text::bigint AS filas
FROM pg_stat_user_tables WHERE schemaname = 'public';

SELECT 'pedido'          AS tabla, count(*) AS filas FROM pedido
UNION ALL SELECT 'detalle_pedido', count(*) FROM detalle_pedido
UNION ALL SELECT 'comprobante_interno', count(*) FROM comprobante_interno
UNION ALL SELECT 'pedidos sin lineas (debe ser 0)', count(*)
    FROM pedido p WHERE NOT EXISTS (SELECT 1 FROM detalle_pedido d WHERE d.id_pedido = p.id_pedido);

-- Comprobacion del supuesto: el criterio "pedido nuevo = pedido sin lineas"
-- solo es exacto si hoy no hay ninguno, y verificado antes de escribir el
-- script hay 0. Si apareciera alguno, seria de una corrida anterior
-- interrumpida entre el paso 3 y el 4, y completarle las lineas es
-- exactamente la reparacion correcta. Por eso avisa en vez de abortar:
-- abortar impediria reanudar una carga a medias.
DO $$
DECLARE v_sin_lineas int;
BEGIN
    SELECT count(*) INTO v_sin_lineas
    FROM pedido p WHERE NOT EXISTS (SELECT 1 FROM detalle_pedido d WHERE d.id_pedido = p.id_pedido);
    IF v_sin_lineas > 0 THEN
        RAISE WARNING 'Hay % pedidos sin lineas al empezar. Se tratan como pedidos nuevos (corrida anterior interrumpida) y se les completaran las lineas.', v_sin_lineas;
    ELSE
        RAISE NOTICE 'Supuesto verificado: 0 pedidos sin lineas. El conjunto de pedidos nuevos sera exacto.';
    END IF;
END $$;

-- ============================================================================
-- 2. DESACTIVACION TEMPORAL DE TRIGGERS  — los 3 minimos
-- ----------------------------------------------------------------------------
-- trg_recalcular_total_pedido_insert  AFTER INSERT ... FOR EACH STATEMENT sobre
--     detalle_pedido: con 50.000 filas por lote recalcularia el total de todos
--     los pedidos del lote en cada sentencia.
-- trg_proteger_total_pedido           BEFORE UPDATE FOR EACH ROW sobre pedido:
--     BLOQUEARIA el UPDATE agregado de reconstruccion del paso 5.
-- trg_recalcular_total_por_descuento  BEFORE UPDATE OF descuento: recalcularia
--     fila a fila durante ese mismo UPDATE.
--
-- Se dejan ENCENDIDOS a proposito trg_pedido_updated_at (no se dispara en
-- INSERT) y los de DELETE/UPDATE de detalle_pedido (esta fase no borra nada).
-- ============================================================================
\echo ''
\echo '--- 2. Desactivando 3 triggers de recalculo (temporal) ---'

ALTER TABLE detalle_pedido DISABLE TRIGGER trg_recalcular_total_pedido_insert;
ALTER TABLE pedido         DISABLE TRIGGER trg_proteger_total_pedido;
ALTER TABLE pedido         DISABLE TRIGGER trg_recalcular_total_por_descuento;

-- ============================================================================
-- 3. PEDIDO  -> objetivo 230.000  (+65.000)
-- ----------------------------------------------------------------------------
-- Mismas distribuciones que la F38 para no deformar el dataset ya medido:
--   estado    entregado 70 / enviado 10 / procesado 8 / pendiente 8 / anulado 4
--   fecha     730 dias con sesgo a lo reciente, power(random(), 1.4)
--   cliente   power(random(), 2): pocos clientes concentran muchos pedidos
--   descuento 15 % de los pedidos, entre 1 y 20
-- total = 0 a proposito: se reconstruye en el paso 5.
-- ============================================================================
\echo ''
\echo '--- 3. Cargando pedidos (objetivo 230.000) ---'

DO $$
DECLARE
    v_objetivo int := 230000;
    v_actual   int;
    v_faltan   int;
    v_lote     int;
    v_hechas   int := 0;
    v_cli      int[];
    v_ncli     int;
    v_usr      int[];
    v_nusr     int;
BEGIN
    SELECT count(*) INTO v_actual FROM pedido;
    v_faltan := GREATEST(v_objetivo - v_actual, 0);
    IF v_faltan = 0 THEN
        RAISE NOTICE 'pedido: ya tiene % filas (objetivo %). Se salta.', v_actual, v_objetivo;
        RETURN;
    END IF;

    -- IDs reales del catalogo: las PK son IDENTITY y puede haber huecos
    v_cli  := ARRAY(SELECT id_cliente FROM cliente ORDER BY id_cliente);
    v_ncli := array_length(v_cli, 1);
    v_usr  := ARRAY(SELECT id_usuario FROM usuario ORDER BY id_usuario);
    v_nusr := array_length(v_usr, 1);
    RAISE NOTICE 'pedido: % actuales, faltan % (sobre % clientes, % usuarios)',
                 v_actual, v_faltan, v_ncli, v_nusr;

    WHILE v_hechas < v_faltan LOOP
        v_lote := LEAST(30000, v_faltan - v_hechas);

        INSERT INTO pedido (id_cliente, id_usuario, fecha_pedido, total, descuento, estado,
                            created_at, es_pedido_especial, tipo_especial, nota_especial)
        SELECT
            v_cli[1 + (power(s.r_cli, 2) * (v_ncli - 1))::int],
            v_usr[1 + (s.r_usr * (v_nusr - 1))::int],
            s.fecha,
            0,
            CASE WHEN s.r_desc < 0.85 THEN 0 ELSE round((1 + s.r_desc2 * 19)::numeric, 2) END,
            CASE WHEN s.r_est < 0.70 THEN 'entregado'
                 WHEN s.r_est < 0.80 THEN 'enviado'
                 WHEN s.r_est < 0.88 THEN 'procesado'
                 WHEN s.r_est < 0.96 THEN 'pendiente'
                 ELSE 'anulado' END,
            s.fecha,
            (s.r_esp < 0.05),
            CASE WHEN s.r_esp < 0.05
                 THEN (ARRAY['personalizado','regalo','corporativo'])[1 + (s.r_tipo * 2)::int]
                 ELSE NULL END,
            NULL
        FROM (
            -- volatiles por fila: van aqui, sobre generate_series
            SELECT now() - (power(random(), 1.4) * interval '730 days') AS fecha,
                   random() AS r_cli,  random() AS r_usr,   random() AS r_est,
                   random() AS r_desc, random() AS r_desc2, random() AS r_esp,
                   random() AS r_tipo
            FROM generate_series(1, v_lote) g
        ) s;

        v_hechas := v_hechas + v_lote;
        COMMIT;
        RAISE NOTICE '  pedido: % / %', v_hechas, v_faltan;
    END LOOP;
END $$;

-- ============================================================================
-- 4. DETALLE_PEDIDO  -> una linea o mas para CADA pedido nuevo
-- ----------------------------------------------------------------------------
-- No hay cifra objetivo: se generan las lineas que pida la distribucion
-- (media 2,53 medida, 1..5 sesgada a 2-3). Poner un LIMIT dejaria a los
-- ultimos pedidos con cero lineas, y un pedido sin lineas no es un dato de
-- negocio realista: es basura que ademas rompe el criterio de reejecucion.
--
-- El filtro NOT EXISTS es lo que acota el trabajo a los pedidos nuevos y hace
-- el bloque reejecutable: un pedido que ya tiene lineas no se toca jamas.
-- El precio sale del producto real con +-12 % de variacion, no de un literal.
-- ============================================================================
\echo ''
\echo '--- 4. Cargando lineas de detalle de los pedidos nuevos ---'

DO $$
DECLARE
    v_lo      int;
    v_hi      int;
    v_max     int;
    v_paso    int := 15000;      -- ~15.000 pedidos x 2,78 ~= 42.000 filas/lote
    v_ins     int;
    v_hechas  int := 0;
    v_pid     int[];             -- catalogo en memoria: sortear con OFFSET por
    v_ppre    numeric[];         -- fila costaria un escaneo por cada linea
    v_nprod   int;
BEGIN
    SELECT MIN(id_pedido), MAX(id_pedido) INTO v_lo, v_max
    FROM pedido p
    WHERE NOT EXISTS (SELECT 1 FROM detalle_pedido d WHERE d.id_pedido = p.id_pedido);

    IF v_lo IS NULL THEN
        RAISE NOTICE 'detalle_pedido: no hay pedidos sin lineas. Se salta.';
        RETURN;
    END IF;

    SELECT array_agg(id_producto ORDER BY id_producto), array_agg(precio ORDER BY id_producto)
      INTO v_pid, v_ppre FROM producto;
    v_nprod := array_length(v_pid, 1);
    RAISE NOTICE 'detalle_pedido: pedidos nuevos en el rango % - % (sobre % productos)',
                 v_lo, v_max, v_nprod;

    WHILE v_lo <= v_max LOOP
        v_hi := v_lo + v_paso - 1;

        INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario,
                                    picking_completado, cantidad_recogida)
        SELECT d.id_pedido, d.id_producto, d.cantidad,
               round((d.precio * (0.88 + random() * 0.24))::numeric, 2),
               d.recogido,
               CASE WHEN d.recogido THEN d.cantidad ELSE 0 END
        FROM (
            SELECT p.id_pedido,
                   v_pid[k.i]  AS id_producto,
                   v_ppre[k.i] AS precio,
                   -- cantidad 1..5 sesgada a 1-2
                   CASE WHEN random() < 0.45 THEN 1
                        WHEN random() < 0.75 THEN 2
                        WHEN random() < 0.90 THEN 3
                        WHEN random() < 0.97 THEN 4
                        ELSE 5 END AS cantidad,
                   -- lo recogido solo tiene sentido en lo que ya salio
                   (p.estado IN ('entregado','enviado')) AS recogido
            FROM (
                SELECT id_pedido, estado,
                       -- lineas por pedido: 1..5, media real medida 2,53
                       CASE WHEN random() < 0.10 THEN 1
                            WHEN random() < 0.45 THEN 2
                            WHEN random() < 0.75 THEN 3
                            WHEN random() < 0.92 THEN 4
                            ELSE 5 END AS lineas
                FROM pedido p2
                WHERE p2.id_pedido BETWEEN v_lo AND v_hi
                  AND NOT EXISTS (SELECT 1 FROM detalle_pedido d2 WHERE d2.id_pedido = p2.id_pedido)
            ) p
            CROSS JOIN LATERAL generate_series(1, p.lineas) s
            -- producto sesgado: el catalogo real no rota plano
            CROSS JOIN LATERAL (
                SELECT 1 + (power(random(), 1.6) * (v_nprod - 1))::int AS i
            ) k
        ) d;

        GET DIAGNOSTICS v_ins = ROW_COUNT;
        v_hechas := v_hechas + v_ins;
        COMMIT;
        RAISE NOTICE '  detalle_pedido: % lineas (pedidos % - %)', v_hechas, v_lo, v_hi;
        v_lo := v_hi + 1;
    END LOOP;

    RAISE NOTICE 'detalle_pedido: % lineas nuevas', v_hechas;
END $$;

-- ============================================================================
-- 5. RECONSTRUCCION DE pedido.total  — UN UNICO UPDATE AGREGADO
-- ----------------------------------------------------------------------------
--     total = GREATEST( SUM(detalle_pedido.subtotal) - pedido.descuento , 0 )
--
-- EL DESCUENTO SE RESTA (fn_recalcular_total_pedido_stmt / fn_proteger_total_pedido).
--
-- El filtro NO es "total = 0": hay 54 pedidos preexistentes cuyo total legitimo
-- es 0 porque su descuento se come el subtotal, y ese filtro los reescribiria
-- (mismo valor, pero moviendo su updated_at). Se filtra por "el total guardado
-- no coincide con la formula", que es la definicion exacta de fila a reparar:
--   - los 165.000 previos ya cumplen el invariante -> ninguno se toca;
--   - los nuevos entraron con total 0 y ya tienen lineas -> todos se corrigen;
--   - reejecutar el script no encuentra nada que hacer.
-- Los triggers de pedido siguen desactivados en este punto.
-- ============================================================================
\echo ''
\echo '--- 5. Reconstruyendo pedido.total de los pedidos nuevos ---'

UPDATE pedido p
SET total = GREATEST(d.suma - p.descuento, 0)
FROM (
    SELECT id_pedido, SUM(subtotal) AS suma
    FROM detalle_pedido GROUP BY id_pedido
) d
WHERE d.id_pedido = p.id_pedido
  AND p.total <> GREATEST(d.suma - p.descuento, 0);

-- ============================================================================
-- 6. REACTIVACION DE LOS TRIGGERS
-- ============================================================================
\echo ''
\echo '--- 6. Reactivando triggers ---'

ALTER TABLE detalle_pedido ENABLE TRIGGER trg_recalcular_total_pedido_insert;
ALTER TABLE pedido         ENABLE TRIGGER trg_proteger_total_pedido;
ALTER TABLE pedido         ENABLE TRIGGER trg_recalcular_total_por_descuento;

SELECT count(*) FILTER (WHERE tgenabled = 'O')  AS triggers_activos,
       count(*) FILTER (WHERE tgenabled <> 'O') AS triggers_apagados
FROM pg_trigger WHERE NOT tgisinternal;

-- ============================================================================
-- 7. ESTADISTICAS
-- ============================================================================
\echo ''
\echo '--- 7. VACUUM ANALYZE de las tablas tocadas ---'

VACUUM ANALYZE pedido;
VACUUM ANALYZE detalle_pedido;

-- ============================================================================
-- 8. VERIFICACION
-- ----------------------------------------------------------------------------
-- No basta con que las filas existan (leccion de la F38.1): se comprueban el
-- invariante financiero, la integridad y las DISTRIBUCIONES, que ninguna
-- restriccion de la base puede vigilar.
-- ============================================================================
\echo ''
\echo '============================================================'
\echo '8. VERIFICACION'
\echo '============================================================'

\echo ''
\echo '--- 8.1 Invariante financiero (toda la tabla) ---'
SELECT count(*) AS discrepancias_total
FROM pedido p
LEFT JOIN (SELECT id_pedido, SUM(subtotal) s FROM detalle_pedido GROUP BY id_pedido) d
       ON d.id_pedido = p.id_pedido
WHERE p.total <> GREATEST(COALESCE(d.s, 0) - p.descuento, 0);

\echo ''
\echo '--- 8.2 Coherencia estructural ---'
SELECT 'pedidos sin lineas'                       AS comprobacion, count(*) AS valor
  FROM pedido p WHERE NOT EXISTS (SELECT 1 FROM detalle_pedido d WHERE d.id_pedido = p.id_pedido)
UNION ALL
SELECT 'lineas huerfanas (pedido inexistente)', count(*)
  FROM detalle_pedido d WHERE NOT EXISTS (SELECT 1 FROM pedido p WHERE p.id_pedido = d.id_pedido)
UNION ALL
SELECT 'lineas con producto inexistente', count(*)
  FROM detalle_pedido d WHERE NOT EXISTS (SELECT 1 FROM producto x WHERE x.id_producto = d.id_producto)
UNION ALL
SELECT 'pedidos con total negativo', count(*) FROM pedido WHERE total < 0
UNION ALL
SELECT 'cantidad_recogida > cantidad', count(*) FROM detalle_pedido WHERE cantidad_recogida > cantidad;

\echo ''
\echo '--- 8.3 Facturacion intacta (no debia moverse una sola fila) ---'
SELECT count(*) AS comprobantes,
       count(*) FILTER (WHERE c.total <> p.total) AS descuadres_con_su_pedido
FROM comprobante_interno c JOIN pedido p ON p.id_pedido = c.id_pedido;

\echo ''
\echo '--- 8.4 Distribuciones ---'
SELECT estado, count(*) AS pedidos,
       round(100.0 * count(*) / SUM(count(*)) OVER (), 1) AS pct
FROM pedido GROUP BY estado ORDER BY pedidos DESC;

SELECT count(DISTINCT fecha_pedido::date) AS fechas_distintas,
       min(fecha_pedido)::date            AS desde,
       max(fecha_pedido)::date            AS hasta
FROM pedido;

SELECT round(avg(n), 2) AS lineas_por_pedido_media, min(n) AS minimo, max(n) AS maximo
FROM (SELECT id_pedido, count(*) AS n FROM detalle_pedido GROUP BY id_pedido) t;

\echo ''
\echo '--- 8.5 Recuento final: negocio frente a bitacoras (COUNT(*) exacto) ---'
WITH exactos AS (
    SELECT relname,
           (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM public.%I', relname),
                                                false, true, '')))[1]::text::bigint AS filas
    FROM pg_stat_user_tables WHERE schemaname = 'public'
)
SELECT
    sum(filas)                                                                        AS filas_totales,
    sum(filas) FILTER (WHERE relname IN ('log_accion','historial_inventario','auditoria_cambios'))     AS bitacoras,
    sum(filas) FILTER (WHERE relname NOT IN ('log_accion','historial_inventario','auditoria_cambios')) AS filas_de_negocio,
    CASE WHEN sum(filas) FILTER (WHERE relname NOT IN ('log_accion','historial_inventario','auditoria_cambios')) >= 1000000
         THEN 'OK - requisito 7 cumplido en tablas de negocio'
         ELSE 'INSUFICIENTE - subir el objetivo del paso 3' END                       AS veredicto,
    pg_size_pretty(pg_database_size(current_database()))                               AS tamano
FROM exactos;

\echo ''
\echo '--- 8.6 Crecimiento por tabla ---'
WITH exactos AS (
    SELECT relname,
           (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM public.%I', relname),
                                                false, true, '')))[1]::text::bigint AS filas
    FROM pg_stat_user_tables WHERE schemaname = 'public'
)
SELECT a.relname AS tabla, a.filas AS antes, h.filas AS ahora, h.filas - a.filas AS delta
FROM _f43_antes a JOIN exactos h ON h.relname = a.relname
WHERE h.filas <> a.filas
ORDER BY delta DESC;

DROP TABLE _f43_antes;

\echo ''
\echo '============================================================'
\echo 'FASE 43 COMPLETADA'
\echo '============================================================'
