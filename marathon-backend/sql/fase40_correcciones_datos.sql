-- ============================================================================
-- FASE 40 · ETAPA 1 — CORRECCIONES DE REALISMO DE DATOS
-- ----------------------------------------------------------------------------
-- Dos defectos que destapo el estudio de planes de la F39. Ninguno rompia una
-- restriccion, y por eso ninguna verificacion los habia detectado: eran datos
-- validos pero inverosimiles, que es exactamente la clase de fallo que las
-- restricciones no pueden ver.
--
--     psql -U postgres -d mod_venta_inve -f fase40_correcciones_datos.sql
--
-- IDEMPOTENTE: comprueba el estado antes de actuar y se salta lo que ya cumple.
-- ============================================================================

\set ON_ERROR_STOP on
\timing on

\echo ''
\echo '=== FASE 40 ETAPA 1: correcciones de realismo ==='

DO $$
BEGIN
    IF current_user <> 'postgres' THEN
        RAISE EXCEPTION 'Ejecutar como postgres (actual: %)', current_user;
    END IF;
END $$;

-- ============================================================================
-- 1. lista_materiales -> 3 a 6 componentes por producto fabricado
-- ----------------------------------------------------------------------------
-- La F39 cargo 900 BOM sobre 16 productos fabricados: 60 componentes por
-- producto. Para calzado no se sostiene. El objetivo de 900 filas y el criterio
-- de "~3 componentes" eran incompatibles con un catalogo de 108 productos, y en
-- la F39 se priorizo el volumen; aqui se prioriza el realismo, porque el
-- volumen ya no depende de esta tabla.
--
-- COMPROBADO ANTES DE BORRAR: ninguna FK referencia lista_materiales
--   SELECT conname FROM pg_constraint
--   WHERE contype='f' AND confrelid='lista_materiales'::regclass;   -> 0 filas
-- orden_produccion_consumo apunta a materia_prima y a orden_produccion, no al
-- BOM, asi que depurar aqui no deja huerfanos.
-- ============================================================================
DO $$
DECLARE
    v_prod   int;
    v_antes  int;
    v_borrar int;
BEGIN
    SELECT count(*) INTO v_prod  FROM producto WHERE origen='fabricado';
    SELECT count(*) INTO v_antes FROM lista_materiales;

    IF v_antes <= v_prod * 6 THEN
        RAISE NOTICE 'lista_materiales: % filas para % productos ya esta en rango. Se salta.', v_antes, v_prod;
        RETURN;
    END IF;

    -- Se conservan entre 3 y 6 componentes por producto, con reparto NO uniforme
    -- (un producto simple lleva 3, uno complejo 6). El resto se elimina.
    WITH ranked AS (
        SELECT id_bom,
               row_number() OVER (PARTITION BY id_producto ORDER BY id_bom) AS rn,
               3 + (abs(hashtext(id_producto::text)) % 4) AS cupo
        FROM lista_materiales
    )
    DELETE FROM lista_materiales l
    USING ranked r
    WHERE r.id_bom = l.id_bom AND r.rn > r.cupo;

    GET DIAGNOSTICS v_borrar = ROW_COUNT;
    RAISE NOTICE 'lista_materiales: % -> % filas (% eliminadas) sobre % productos fabricados',
                 v_antes, v_antes - v_borrar, v_borrar, v_prod;
END $$;

-- ============================================================================
-- 2. pedido.fecha_empaque
-- ----------------------------------------------------------------------------
-- Estaba NULL en el 100 % de los pedidos, incluidos los ~118.000 entregados. Un
-- pedido entregado que nunca se empaco es incoherente, y ademas dejaba la
-- consulta Q06 del catalogo de la F39 (PedidoRepository.findDespachados) sin
-- poder evaluarse: filtraba por una columna vacia y devolvia 0 filas siempre.
--
-- QUE ESTADOS SE RELLENAN
--   entregado / enviado -> pasaron por empaque, llevan fecha
--   procesado           -> el picking esta hecho pero aun no se empaco: NULL
--   pendiente / anulado -> nunca llegaron a empaque: NULL
-- Dejar NULL en esos tres no es un hueco: es el dato correcto.
--
-- COHERENCIA: la fecha cae entre 1 y 5 dias despues del pedido, con sesgo a los
-- primeros dias, y nunca en el futuro.
-- ============================================================================
DO $$
DECLARE v_n int; v_ya int;
BEGIN
    SELECT count(*) INTO v_ya FROM pedido
    WHERE estado IN ('entregado','enviado') AND fecha_empaque IS NOT NULL;
    IF v_ya > 0 THEN
        RAISE NOTICE 'pedido.fecha_empaque: ya hay % pedidos con valor. Se salta.', v_ya;
        RETURN;
    END IF;

    UPDATE pedido p
    SET fecha_empaque = LEAST(
            p.fecha_pedido + (power(r.x, 1.6) * interval '4 days') + interval '1 day',
            now())
    FROM (SELECT id_pedido, random() AS x FROM pedido) r
    WHERE r.id_pedido = p.id_pedido
      AND p.estado IN ('entregado','enviado');

    GET DIAGNOSTICS v_n = ROW_COUNT;
    RAISE NOTICE 'pedido.fecha_empaque: % pedidos con fecha asignada', v_n;
END $$;

-- Comprobacion de coherencia: ninguna fecha de empaque puede ser anterior al
-- pedido ni posterior a hoy.
DO $$
DECLARE v_mal int;
BEGIN
    SELECT count(*) INTO v_mal FROM pedido
    WHERE fecha_empaque IS NOT NULL
      AND (fecha_empaque < fecha_pedido OR fecha_empaque > now());
    IF v_mal > 0 THEN
        RAISE EXCEPTION 'FALLO: % pedidos con fecha_empaque incoherente', v_mal;
    END IF;
    RAISE NOTICE 'Verificado: toda fecha_empaque cae entre su fecha_pedido y hoy';
END $$;

-- ============================================================================
-- 3. ANALYZE de lo tocado
-- ============================================================================
ANALYZE lista_materiales;
ANALYZE pedido;

\echo ''
\echo '--- Resultado ---'
SELECT 'lista_materiales' AS q,
       count(*)::text AS filas,
       round(avg(n),2)::text AS componentes_por_producto,
       min(n)::text AS minimo, max(n)::text AS maximo
FROM (SELECT id_producto, count(*) n FROM lista_materiales GROUP BY id_producto) s
CROSS JOIN LATERAL (SELECT 1) _;

SELECT estado,
       count(*) AS total,
       count(fecha_empaque) AS con_fecha,
       count(*) - count(fecha_empaque) AS en_null
FROM pedido GROUP BY estado ORDER BY 2 DESC;

\echo ''
\echo '=== ETAPA 1 COMPLETADA ==='
