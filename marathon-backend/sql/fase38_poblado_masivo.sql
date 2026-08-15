-- ============================================================================
-- FASE 38 — POBLADO MASIVO A ~1.000.000 DE FILAS
-- ----------------------------------------------------------------------------
-- Script IDEMPOTENTE y por LOTES. Cada lote va en su propia transaccion.
--
-- EJECUTAR COMO postgres (no como usr_admin_marathon): ALTER TABLE ... DISABLE
-- TRIGGER exige ser dueno de la tabla, y rol_administrador no lo es.
--
--     psql -U postgres -d mod_venta_inve -f fase38_poblado_masivo.sql
--
-- IDEMPOTENCIA: cada bloque cuenta las filas existentes y solo inserta la
-- diferencia hasta su objetivo. Si una tabla ya llego, se salta y lo registra.
-- Si el script se interrumpe, se reejecuta y continua donde quedo.
--
-- El inventario del esquema en el que se apoya este script (columnas GENERATED,
-- triggers, FK, CHECK, UNIQUE) esta en fase38_reconocimiento.md. Nada de lo que
-- hay aqui se supuso: todo salio del catalogo.
-- ============================================================================

\set ON_ERROR_STOP on
\timing on

\echo ''
\echo '=============================================================='
\echo 'FASE 38 - POBLADO MASIVO'
\echo '=============================================================='

-- ============================================================================
-- 0. COMPROBACION PREVIA
-- ============================================================================
DO $$
BEGIN
    IF current_user <> 'postgres' THEN
        RAISE EXCEPTION 'Este script debe ejecutarse como postgres (actual: %). DISABLE TRIGGER exige ser dueno de la tabla.', current_user;
    END IF;
    RAISE NOTICE 'Usuario correcto: %', current_user;
END $$;

\echo '--- Conteo ANTES ---'
SELECT 'cliente' AS tabla, count(*) AS filas FROM cliente
UNION ALL SELECT 'inventario', count(*) FROM inventario
UNION ALL SELECT 'pedido', count(*) FROM pedido
UNION ALL SELECT 'detalle_pedido', count(*) FROM detalle_pedido
UNION ALL SELECT 'comprobante_interno', count(*) FROM comprobante_interno
UNION ALL SELECT 'movimiento_inventario', count(*) FROM movimiento_inventario
UNION ALL SELECT 'historial_inventario', count(*) FROM historial_inventario
UNION ALL SELECT 'orden_compra', count(*) FROM orden_compra
UNION ALL SELECT 'orden_compra_detalle', count(*) FROM orden_compra_detalle
UNION ALL SELECT 'log_accion', count(*) FROM log_accion
ORDER BY 1;

-- ============================================================================
-- 1. DESACTIVAR LOS 5 TRIGGERS DE RECALCULO Y PROTECCION
-- ----------------------------------------------------------------------------
-- Justificacion de cada uno en fase38_reconocimiento.md seccion 8.
-- Los de recalculo son FOR EACH STATEMENT: un lote de 50.000 filas dispara UNA
-- ejecucion, pero esa ejecucion actualiza el total de todos los pedidos del
-- lote. Es justo el trabajo que sobra durante una carga masiva.
-- Los de proteccion BLOQUEARIAN el UPDATE agregado del paso 6.
-- ============================================================================
\echo ''
\echo '--- Desactivando 5 triggers (se reactivan en el paso 8) ---'
ALTER TABLE detalle_pedido       DISABLE TRIGGER trg_recalcular_total_pedido_insert;
ALTER TABLE pedido               DISABLE TRIGGER trg_proteger_total_pedido;
ALTER TABLE pedido               DISABLE TRIGGER trg_recalcular_total_por_descuento;
ALTER TABLE orden_compra_detalle DISABLE TRIGGER trg_oc_total_insert;
ALTER TABLE orden_compra         DISABLE TRIGGER trg_proteger_total_oc;

SELECT c.relname AS tabla, t.tgname AS trigger_desactivado, t.tgenabled
FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
WHERE NOT t.tgisinternal AND t.tgenabled = 'D' ORDER BY 1,2;

-- ============================================================================
-- 2. CLIENTE  -> objetivo 5.000
-- ----------------------------------------------------------------------------
-- Correo: unico por construccion (lleva el offset del MAX actual) y valido
-- frente a chk_cliente_correo. Telefono: formato ecuatoriano 09XXXXXXXX.
-- Ciudad sesgada con power(random(),2): unas pocas ciudades concentran la
-- clientela, como ocurre de verdad. Un reparto plano haria que
-- idx_cliente_ciudad pareciera igual de util para cualquier ciudad.
-- ============================================================================
DO $$
DECLARE
    v_objetivo  int := 5000;
    v_actual    int;
    v_faltan    int;
    v_lote      int;
    v_off       int;
    v_hechas    int := 0;
    v_nombres   text[] := ARRAY['Ana','Luis','Maria','Carlos','Sofia','Diego','Valeria','Andres',
                                'Camila','Jorge','Daniela','Fernando','Paola','Ricardo','Gabriela',
                                'Mateo','Lucia','Sebastian','Martina','Javier'];
    v_apellidos text[] := ARRAY['Perez','Gomez','Rodriguez','Lopez','Martinez','Sanchez','Ramirez',
                                'Torres','Flores','Vargas','Castillo','Jimenez','Morales','Ortiz',
                                'Guerrero','Mendoza','Silva','Rojas','Cabrera','Paredes'];
    v_calles    text[] := ARRAY['Av. Amazonas','Av. Naciones Unidas','Calle Rocafuerte','Av. 6 de Diciembre',
                                'Calle Bolivar','Av. Republica','Calle Sucre','Av. Eloy Alfaro'];
BEGIN
    SELECT count(*) INTO v_actual FROM cliente;
    v_faltan := GREATEST(v_objetivo - v_actual, 0);
    IF v_faltan = 0 THEN
        RAISE NOTICE 'cliente: ya tiene % filas (objetivo %). Se salta.', v_actual, v_objetivo;
        RETURN;
    END IF;
    RAISE NOTICE 'cliente: % actuales, faltan %', v_actual, v_faltan;

    WHILE v_hechas < v_faltan LOOP
        v_lote := LEAST(50000, v_faltan - v_hechas);
        SELECT COALESCE(MAX(id_cliente),0) INTO v_off FROM cliente;

        INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado, created_at)
        SELECT
            1 + (power(random(), 2) * 87)::int,
            v_nombres[1 + (random()*19)::int],
            v_apellidos[1 + (random()*19)::int],
            'cliente' || (v_off + g) || '@correo-demo.ec',
            '09' || lpad((random()*99999999)::bigint::text, 8, '0'),
            v_calles[1 + (random()*7)::int] || ' N' || (10 + (random()*890)::int)::text,
            CASE WHEN random() < 0.92 THEN 'activo' ELSE 'inactivo' END,
            now() - (power(random(), 1.3) * interval '730 days')
        FROM generate_series(1, v_lote) g;

        v_hechas := v_hechas + v_lote;
        COMMIT;
        RAISE NOTICE '  cliente: % / %', v_hechas, v_faltan;
    END LOOP;
END $$;

-- ============================================================================
-- 3. INVENTARIO  -> objetivo 2.000
-- ----------------------------------------------------------------------------
-- TECHO DURO: uq_inventario_producto_bodega limita a 108 x 20 = 2.160 filas.
-- El objetivo de 2.000 cabe, pero por poco. Se toman combinaciones libres.
-- 8 % con stock_actual <= stock_minimo, construido de forma explicita para que
-- el porcentaje sea exacto y no dependa del solape de dos rangos aleatorios:
-- es lo que hace evaluable el indice parcial idx_inventario_stock_bajo.
-- ============================================================================
DO $$
DECLARE
    v_objetivo int := 2000;
    v_actual   int;
    v_faltan   int;
    v_libres   int;
BEGIN
    SELECT count(*) INTO v_actual FROM inventario;
    v_faltan := GREATEST(v_objetivo - v_actual, 0);
    IF v_faltan = 0 THEN
        RAISE NOTICE 'inventario: ya tiene % filas (objetivo %). Se salta.', v_actual, v_objetivo;
        RETURN;
    END IF;

    SELECT count(*) INTO v_libres
    FROM producto p CROSS JOIN bodega b
    WHERE NOT EXISTS (SELECT 1 FROM inventario i
                      WHERE i.id_producto=p.id_producto AND i.id_bodega=b.id_bodega);

    IF v_libres < v_faltan THEN
        RAISE NOTICE 'inventario: solo quedan % combinaciones producto x bodega libres (se piden %). Se insertan las que hay.', v_libres, v_faltan;
        v_faltan := v_libres;
    END IF;
    RAISE NOTICE 'inventario: % actuales, se insertan %', v_actual, v_faltan;

    INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo, fecha_actualizacion)
    SELECT c.id_producto, c.id_bodega,
           CASE WHEN c.bajo THEN (random() * c.minimo)::int
                ELSE c.minimo + 15 + (random() * 400)::int END,
           c.minimo,
           now() - (random() * interval '730 days')
    FROM (
        SELECT p.id_producto, b.id_bodega,
               (random() < 0.08)            AS bajo,
               10 + (random() * 25)::int    AS minimo
        FROM producto p CROSS JOIN bodega b
        WHERE NOT EXISTS (SELECT 1 FROM inventario i
                          WHERE i.id_producto=p.id_producto AND i.id_bodega=b.id_bodega)
        ORDER BY random()
        LIMIT v_faltan
    ) c;

    COMMIT;
    RAISE NOTICE '  inventario: listo';
END $$;

-- ============================================================================
-- 4. PEDIDO  -> objetivo 165.000
-- ----------------------------------------------------------------------------
-- total = 0 a proposito: se reconstruye en el paso 6 con un UNICO UPDATE
-- agregado. Nunca se escribe fila a fila.
-- 70 % entregado. Fechas repartidas en 24 meses con sesgo a lo reciente
-- (power(random(),1.4)), que es como se comporta un negocio que crece: sin esa
-- dispersion idx_pedido_estado_fecha e idx_pedido_cliente_fecha no son
-- evaluables.
-- Cliente sesgado con power(random(),2): pocos clientes con muchos pedidos.
-- ============================================================================
DO $$
DECLARE
    v_objetivo int := 165000;
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

    -- IDs reales: no se inventan rangos (las PK son IDENTITY y puede haber huecos)
    v_cli := ARRAY(SELECT id_cliente FROM cliente ORDER BY id_cliente);
    v_ncli := array_length(v_cli, 1);
    v_usr := ARRAY(SELECT id_usuario FROM usuario ORDER BY id_usuario);
    v_nusr := array_length(v_usr, 1);
    RAISE NOTICE 'pedido: % actuales, faltan % (sobre % clientes)', v_actual, v_faltan, v_ncli;

    WHILE v_hechas < v_faltan LOOP
        v_lote := LEAST(50000, v_faltan - v_hechas);

        -- CUIDADO: las expresiones volatiles (random(), now()) van en el SELECT
        -- de una subconsulta con FROM generate_series, NUNCA en un
        -- CROSS JOIN LATERAL sin correlacionar con la fila externa. PostgreSQL
        -- trata ese LATERAL como subconsulta no correlacionada y lo evalua UNA
        -- SOLA VEZ por sentencia: el lote entero saldria con la misma fecha y el
        -- mismo estado. Se detecto asi en la primera pasada de esta fase
        -- (165.000 pedidos repartidos en 5 fechas).
        INSERT INTO pedido (id_cliente, id_usuario, fecha_pedido, total, descuento, estado,
                            created_at, es_pedido_especial, tipo_especial, nota_especial)
        SELECT
            v_cli[1 + (power(s.r_cli, 2) * (v_ncli - 1))::int],
            v_usr[1 + (s.r_usr * (v_nusr - 1))::int],
            s.fecha,
            0,
            CASE WHEN s.r_desc < 0.85 THEN 0 ELSE round((1 + s.r_desc2*19)::numeric, 2) END,
            CASE WHEN s.r_est < 0.70 THEN 'entregado'
                 WHEN s.r_est < 0.80 THEN 'enviado'
                 WHEN s.r_est < 0.88 THEN 'procesado'
                 WHEN s.r_est < 0.96 THEN 'pendiente'
                 ELSE 'anulado' END,
            s.fecha,
            (s.r_esp < 0.05),
            CASE WHEN s.r_esp < 0.05
                 THEN (ARRAY['personalizado','regalo','corporativo'])[1 + (s.r_tipo*2)::int]
                 ELSE NULL END,
            NULL
        FROM (
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
-- 5. DETALLE_PEDIDO  -> objetivo 450.000
-- ----------------------------------------------------------------------------
-- subtotal es GENERATED: se OMITE de la lista de columnas.
-- ~2,7 lineas por pedido con distribucion 1..5 sesgada a 2-3.
-- Se recorre por RANGOS de id_pedido para que cada lote toque pedidos distintos
-- y el numero de lineas por pedido quede acotado.
-- El precio sale del producto real con variacion de +-12 %, no de un literal:
-- precios constantes harian que cualquier agregacion diera el mismo resultado.
-- ============================================================================
DO $$
DECLARE
    v_objetivo int := 450000;
    v_actual   int;
    v_faltan   int;
    v_hechas   int := 0;
    v_lo       int;
    v_hi       int;
    v_max_ped  int;
    v_paso     int := 18000;   -- ~18.000 pedidos x 2,78 lineas ~= 50.000 filas
    v_ins      int;
    v_pid      int[];          -- productos en memoria: sortear con OFFSET por
    v_ppre     numeric[];      -- fila costaria un escaneo por cada una de las
    v_nprod    int;            -- 450.000 lineas
BEGIN
    SELECT count(*) INTO v_actual FROM detalle_pedido;
    v_faltan := GREATEST(v_objetivo - v_actual, 0);
    IF v_faltan = 0 THEN
        RAISE NOTICE 'detalle_pedido: ya tiene % filas (objetivo %). Se salta.', v_actual, v_objetivo;
        RETURN;
    END IF;
    SELECT COALESCE(MAX(id_pedido),0) INTO v_max_ped FROM pedido;
    SELECT array_agg(id_producto ORDER BY id_producto), array_agg(precio ORDER BY id_producto)
      INTO v_pid, v_ppre FROM producto;
    v_nprod := array_length(v_pid, 1);
    RAISE NOTICE 'detalle_pedido: % actuales, faltan % (sobre % productos)', v_actual, v_faltan, v_nprod;

    v_lo := 1;
    WHILE v_hechas < v_faltan AND v_lo <= v_max_ped LOOP
        v_hi := v_lo + v_paso - 1;

        INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario,
                                    picking_completado, cantidad_recogida)
        SELECT d.id_pedido, d.id_producto, d.cantidad,
               round((d.precio * (0.88 + random()*0.24))::numeric, 2),
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
                   (p.estado IN ('entregado','enviado')) AS recogido
            FROM (
                SELECT id_pedido, estado,
                       -- lineas por pedido: media ~2,78
                       CASE WHEN random() < 0.10 THEN 1
                            WHEN random() < 0.45 THEN 2
                            WHEN random() < 0.75 THEN 3
                            WHEN random() < 0.92 THEN 4
                            ELSE 5 END AS lineas
                FROM pedido
                WHERE id_pedido BETWEEN v_lo AND v_hi
            ) p
            CROSS JOIN LATERAL generate_series(1, p.lineas) s
            -- producto sesgado: el catalogo real no se vende plano
            CROSS JOIN LATERAL (
                SELECT 1 + (power(random(), 1.6) * (v_nprod - 1))::int AS i
            ) k
            LIMIT (v_faltan - v_hechas)
        ) d;

        GET DIAGNOSTICS v_ins = ROW_COUNT;
        v_hechas := v_hechas + v_ins;
        COMMIT;
        RAISE NOTICE '  detalle_pedido: % / % (pedidos % - %)', v_hechas, v_faltan, v_lo, v_hi;
        v_lo := v_hi + 1;
    END LOOP;

    -- Si el recorrido por rangos se quedo corto, se completa con lineas extra
    -- repartidas al azar (no hay UNIQUE sobre (id_pedido, id_producto)).
    WHILE v_hechas < v_faltan LOOP
        INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario,
                                    picking_completado, cantidad_recogida)
        SELECT p.id_pedido, v_pid[k.i], 1 + (random()*2)::int,
               round((v_ppre[k.i] * (0.88 + random()*0.24))::numeric, 2), false, 0
        FROM (SELECT id_pedido FROM pedido ORDER BY random()
              LIMIT LEAST(50000, v_faltan - v_hechas)) p
        CROSS JOIN LATERAL (
            SELECT 1 + (power(random(), 1.6) * (v_nprod - 1))::int AS i
        ) k;
        GET DIAGNOSTICS v_ins = ROW_COUNT;
        v_hechas := v_hechas + v_ins;
        COMMIT;
        RAISE NOTICE '  detalle_pedido (relleno): % / %', v_hechas, v_faltan;
        EXIT WHEN v_ins = 0;
    END LOOP;
END $$;

-- ============================================================================
-- 6. RECONSTRUCCION DE pedido.total  — UN UNICO UPDATE AGREGADO
-- ----------------------------------------------------------------------------
-- Formula canonica, leida de fn_recalcular_total_pedido_stmt y confirmada en
-- fn_proteger_total_pedido (fase38_reconocimiento.md seccion 3):
--
--     total = GREATEST( SUM(detalle_pedido.subtotal) - pedido.descuento , 0 )
--
-- EL DESCUENTO SE RESTA. Calcularlo sin el descuento dejaria a 1 de cada 7
-- pedidos con un total que el trigger de proteccion rechazaria en la primera
-- modificacion que hiciera la aplicacion.
-- Se hace en un solo UPDATE, con los triggers de pedido aun desactivados.
-- ============================================================================
\echo ''
\echo '--- Reconstruyendo pedido.total (UPDATE agregado unico) ---'
UPDATE pedido p
SET total = GREATEST(COALESCE(d.suma, 0) - p.descuento, 0)
FROM (
    SELECT id_pedido, SUM(subtotal) AS suma
    FROM detalle_pedido GROUP BY id_pedido
) d
WHERE d.id_pedido = p.id_pedido;

-- Pedidos sin lineas: total 0
UPDATE pedido p SET total = 0
WHERE NOT EXISTS (SELECT 1 FROM detalle_pedido d WHERE d.id_pedido = p.id_pedido)
  AND p.total <> 0;

-- ============================================================================
-- 7. COMPROBANTE_INTERNO  -> objetivo 30.000
-- ----------------------------------------------------------------------------
-- trg_validar_total_comprobante se deja ACTIVO a proposito: compara el total
-- del comprobante con el del pedido y aborta si difieren. Cargar 30.000
-- comprobantes con el trigger encendido es una verificacion gratuita de que el
-- UPDATE agregado del paso 6 quedo bien. Si el paso 6 se hubiera equivocado,
-- esta carga falla aqui y lo delata.
-- Solo se facturan pedidos con total > 0 y estado facturable.
-- ============================================================================
DO $$
DECLARE
    v_objetivo int := 30000;
    v_actual   int;
    v_faltan   int;
    v_hechas   int := 0;
    v_lote     int;
    v_off      int;
    v_ins      int;
BEGIN
    SELECT count(*) INTO v_actual FROM comprobante_interno;
    v_faltan := GREATEST(v_objetivo - v_actual, 0);
    IF v_faltan = 0 THEN
        RAISE NOTICE 'comprobante_interno: ya tiene % filas (objetivo %). Se salta.', v_actual, v_objetivo;
        RETURN;
    END IF;
    RAISE NOTICE 'comprobante_interno: % actuales, faltan %', v_actual, v_faltan;

    WHILE v_hechas < v_faltan LOOP
        v_lote := LEAST(50000, v_faltan - v_hechas);
        SELECT COALESCE(MAX(id_comprobante),0) INTO v_off FROM comprobante_interno;

        INSERT INTO comprobante_interno (id_pedido, id_usuario, numero_comprobante,
                                         fecha_emision, total, estado, created_at)
        SELECT s.id_pedido,
               s.id_usuario,
               'CI-' || lpad((v_off + s.rn)::text, 9, '0'),
               s.fecha_pedido + interval '1 hour',
               s.total,
               CASE WHEN random() < 0.03 THEN 'anulado' ELSE 'emitido' END,
               s.fecha_pedido + interval '1 hour'
        FROM (
            SELECT p.id_pedido, p.id_usuario, p.fecha_pedido, p.total,
                   row_number() OVER (ORDER BY p.id_pedido) AS rn
            FROM pedido p
            WHERE p.total > 0
              AND p.estado IN ('entregado','enviado','procesado')
              AND NOT EXISTS (SELECT 1 FROM comprobante_interno c WHERE c.id_pedido = p.id_pedido)
            ORDER BY p.id_pedido
            LIMIT v_lote
        ) s;

        GET DIAGNOSTICS v_ins = ROW_COUNT;
        v_hechas := v_hechas + v_ins;
        COMMIT;
        RAISE NOTICE '  comprobante_interno: % / %', v_hechas, v_faltan;
        EXIT WHEN v_ins = 0;
    END LOOP;
END $$;

-- ============================================================================
-- 8. MOVIMIENTO_INVENTARIO  -> objetivo 80.000
-- ----------------------------------------------------------------------------
-- Movimientos asociados a pedidos despachados. Se incluye un 4 % de traslados
-- con destino valido y distinto del origen, para que los dos CHECK de traslado
-- (chk_traslado_requiere_destino y chk_traslado_origen_distinto_destino) queden
-- ejercitados en lugar de esquivados.
-- ============================================================================
DO $$
DECLARE
    v_objetivo int := 80000;
    v_actual   int;
    v_faltan   int;
    v_hechas   int := 0;
    v_lote     int;
    v_inv      int[];
    v_ninv     int;
    v_usr      int[];
    v_nusr     int;
    v_ped      int[];
    v_nped     int;
    v_ins      int;
BEGIN
    SELECT count(*) INTO v_actual FROM movimiento_inventario;
    v_faltan := GREATEST(v_objetivo - v_actual, 0);
    IF v_faltan = 0 THEN
        RAISE NOTICE 'movimiento_inventario: ya tiene % filas (objetivo %). Se salta.', v_actual, v_objetivo;
        RETURN;
    END IF;

    v_inv := ARRAY(SELECT id_inventario FROM inventario ORDER BY id_inventario);
    v_ninv := array_length(v_inv, 1);
    v_usr := ARRAY(SELECT id_usuario FROM usuario ORDER BY id_usuario);
    v_nusr := array_length(v_usr, 1);
    v_ped := ARRAY(SELECT id_pedido FROM pedido WHERE estado IN ('entregado','enviado')
                   ORDER BY id_pedido LIMIT 120000);
    v_nped := COALESCE(array_length(v_ped, 1), 0);
    RAISE NOTICE 'movimiento_inventario: % actuales, faltan % (% inventarios, % pedidos despachados)',
                 v_actual, v_faltan, v_ninv, v_nped;

    WHILE v_hechas < v_faltan LOOP
        v_lote := LEAST(50000, v_faltan - v_hechas);

        INSERT INTO movimiento_inventario (id_inventario, id_usuario, id_proveedor, id_pedido,
                                           tipo_movimiento, cantidad, fecha, observacion,
                                           id_inventario_destino, created_at)
        SELECT m.origen,
               v_usr[1 + (random()*(v_nusr-1))::int],
               CASE WHEN m.tipo = 'entrada' THEN 1 + (random()*5)::int ELSE NULL END,
               CASE WHEN m.tipo = 'salida' AND v_nped > 0
                    THEN v_ped[1 + (random()*(v_nped-1))::int] ELSE NULL END,
               m.tipo,
               1 + (random()*30)::int,
               m.fecha,
               'Movimiento generado en la carga masiva F38',
               CASE WHEN m.tipo = 'traslado' THEN m.destino ELSE NULL END,
               m.fecha
        FROM (
            SELECT CASE WHEN x.r_tipo < 0.42 THEN 'salida'
                        WHEN x.r_tipo < 0.78 THEN 'entrada'
                        WHEN x.r_tipo < 0.96 THEN 'ajuste'
                        ELSE 'traslado' END AS tipo,
                   x.origen, x.destino, x.fecha
            FROM (
                -- volatiles en el SELECT de la subconsulta, no en un LATERAL
                SELECT random() AS r_tipo,
                       v_inv[1 + (random()*(v_ninv-1))::int] AS origen,
                       -- destino distinto del origen: se desplaza el indice
                       v_inv[1 + ((random()*(v_ninv-2))::int + 1) % v_ninv] AS destino,
                       now() - (power(random(), 1.4) * interval '730 days') AS fecha
                FROM generate_series(1, v_lote) g
            ) x
        ) m
        WHERE m.origen <> m.destino OR m.tipo <> 'traslado';

        -- ROW_COUNT y no v_lote: el WHERE de arriba descarta los traslados cuyo
        -- destino sorteado coincidio con el origen, asi que se insertan algo
        -- menos filas de las pedidas y hay que contar las reales.
        GET DIAGNOSTICS v_ins = ROW_COUNT;
        v_hechas := v_hechas + v_ins;
        COMMIT;
        RAISE NOTICE '  movimiento_inventario: % / %', v_hechas, v_faltan;
        EXIT WHEN v_ins = 0;
    END LOOP;
END $$;

-- ============================================================================
-- 9. HISTORIAL_INVENTARIO  -> objetivo 60.000
-- ----------------------------------------------------------------------------
-- Decision documentada (fase38_reconocimiento.md seccion 5): VIA (b), insercion
-- directa, porque trg_historial_inventario es AFTER *UPDATE* y la carga de
-- inventario fue un INSERT: el trigger genero 0 filas, y 0 no se acerca a
-- 60.000.
--
-- Antes de la insercion directa se hace UNA PASADA REAL DE UPDATE sobre
-- inventario con el trigger ACTIVO y app.current_user_id fijado, para que una
-- parte del historial la produzca el mecanismo de verdad y quede demostrado que
-- sigue vivo. El resto se inserta directamente, lo que ademas permite repartir
-- 'motivo' entre los 5 valores del CHECK (el trigger siempre escribe
-- 'actualizacion_stock').
-- ============================================================================
DO $$
DECLARE
    v_objetivo int := 60000;
    v_actual   int;
    v_faltan   int;
    v_hechas   int := 0;
    v_lote     int;
    v_por_trg  int;
    v_antes    int;
    v_inv      int[];
    v_ninv     int;
    v_usr      int[];
    v_nusr     int;
BEGIN
    SELECT count(*) INTO v_actual FROM historial_inventario;
    v_faltan := GREATEST(v_objetivo - v_actual, 0);
    IF v_faltan = 0 THEN
        RAISE NOTICE 'historial_inventario: ya tiene % filas (objetivo %). Se salta.', v_actual, v_objetivo;
        RETURN;
    END IF;

    -- (1) Pasada real por el trigger: app.current_user_id obligatorio para que el
    --     trigger pueda atribuir la fila a un usuario. set_config(..., true) es
    --     la forma de SET LOCAL dentro de PL/pgSQL: el efecto muere en el COMMIT
    --     de abajo, que es justo lo que se quiere.
    v_antes := v_actual;
    PERFORM set_config('app.current_user_id', '1', true);
    UPDATE inventario
    SET stock_actual = stock_actual + 1,
        fecha_actualizacion = now()
    WHERE stock_actual < 100000;
    SELECT count(*) INTO v_por_trg FROM historial_inventario;
    v_por_trg := v_por_trg - v_antes;
    COMMIT;
    RAISE NOTICE 'historial_inventario: % filas generadas por el trigger real', v_por_trg;

    SELECT count(*) INTO v_actual FROM historial_inventario;
    v_faltan := GREATEST(v_objetivo - v_actual, 0);

    v_inv := ARRAY(SELECT id_inventario FROM inventario ORDER BY id_inventario);
    v_ninv := array_length(v_inv, 1);
    v_usr := ARRAY(SELECT id_usuario FROM usuario ORDER BY id_usuario);
    v_nusr := array_length(v_usr, 1);

    -- (2) Resto por insercion directa
    WHILE v_hechas < v_faltan LOOP
        v_lote := LEAST(50000, v_faltan - v_hechas);

        INSERT INTO historial_inventario (id_inventario, id_usuario, stock_anterior, stock_nuevo, motivo, fecha)
        SELECT h.inv,
               v_usr[1 + (random()*(v_nusr-1))::int],
               h.anterior,
               GREATEST(h.anterior + h.delta, 0),
               CASE WHEN r.x < 0.70 THEN 'actualizacion_stock'
                    WHEN r.x < 0.85 THEN 'ajuste_manual'
                    WHEN r.x < 0.93 THEN 'traslado'
                    WHEN r.x < 0.98 THEN 'correccion'
                    ELSE 'importacion' END,
               now() - (power(random(), 1.4) * interval '730 days')
        FROM (
            SELECT v_inv[1 + (random()*(v_ninv-1))::int] AS inv,
                   (random()*500)::int  AS anterior,
                   (random()*60)::int - 30 AS delta,
                   random() AS r_motivo
            FROM generate_series(1, v_lote) g
        ) h
        CROSS JOIN LATERAL (SELECT h.r_motivo AS x) r;

        v_hechas := v_hechas + v_lote;
        COMMIT;
        RAISE NOTICE '  historial_inventario: % / %', v_hechas, v_faltan;
    END LOOP;
END $$;

-- ============================================================================
-- 10. ORDEN_COMPRA (padres) + ORDEN_COMPRA_DETALLE  -> objetivo 8.000 lineas
-- ----------------------------------------------------------------------------
-- DESVIACION DELIBERADA DEL ENCARGO, documentada en POBLADO_MASIVO.md:
-- solo existian 4 ordenes de compra. Repartir 8.000 lineas entre 4 ordenes daria
-- 2.000 lineas por orden, que no es un dato realista sino un artefacto. Se crean
-- ~2.700 ordenes padre (~3 lineas cada una).
--
-- chk_oc_detalle_item_exclusivo: producto XOR materia_prima. Se respeta con un
-- CASE que anula la columna que no toca.
-- materia_prima tiene IDs 7..16 (no arranca en 1): se toman de la tabla real.
-- ============================================================================
DO $$
DECLARE
    v_obj_det  int := 8000;
    v_act_det  int;
    v_faltan   int;
    v_oc_nec   int;
    v_usr      int[];
    v_nusr     int;
    v_mp       int[];
    v_nmp      int;
    v_oc       int[];
    v_noc      int;
    v_hechas   int := 0;
    v_lote     int;
BEGIN
    SELECT count(*) INTO v_act_det FROM orden_compra_detalle;
    v_faltan := GREATEST(v_obj_det - v_act_det, 0);
    IF v_faltan = 0 THEN
        RAISE NOTICE 'orden_compra_detalle: ya tiene % filas (objetivo %). Se salta.', v_act_det, v_obj_det;
        RETURN;
    END IF;

    v_usr := ARRAY(SELECT id_usuario FROM usuario ORDER BY id_usuario);
    v_nusr := array_length(v_usr, 1);

    -- Padres: ~3 lineas por orden
    v_oc_nec := CEIL(v_faltan / 3.0)::int;
    RAISE NOTICE 'orden_compra: creando % ordenes padre para % lineas', v_oc_nec, v_faltan;

    INSERT INTO orden_compra (id_proveedor, id_usuario_solicitante, id_usuario_aprobador,
                              fecha_orden, fecha_aprobacion, estado, total, observaciones, created_at)
    -- Estados tomados de chk_oc_estado, NO inventados: la restriccion admite
    -- borrador, pendiente_aprobacion, aprobada, rechazada, recibida_parcial,
    -- recibida_completa y cancelada.
    SELECT 1 + (random()*5)::int,
           v_usr[1 + (random()*(v_nusr-1))::int],
           CASE WHEN e.st IN ('aprobada','recibida_parcial','recibida_completa')
                THEN v_usr[1] ELSE NULL END,
           e.fecha,
           CASE WHEN e.st IN ('aprobada','recibida_parcial','recibida_completa')
                THEN e.fecha + interval '2 days' ELSE NULL END,
           e.st,
           0,
           'Orden generada en la carga masiva F38',
           e.fecha
    FROM (
        -- volatiles en el SELECT sobre generate_series, no en un LATERAL suelto
        SELECT x.fecha,
               CASE WHEN x.r < 0.45 THEN 'recibida_completa'
                    WHEN x.r < 0.60 THEN 'recibida_parcial'
                    WHEN x.r < 0.78 THEN 'aprobada'
                    WHEN x.r < 0.88 THEN 'pendiente_aprobacion'
                    WHEN x.r < 0.95 THEN 'borrador'
                    WHEN x.r < 0.98 THEN 'cancelada'
                    ELSE 'rechazada' END AS st
        FROM (SELECT now() - (power(random(),1.4) * interval '730 days') AS fecha,
                     random() AS r
              FROM generate_series(1, v_oc_nec) g) x
    ) e;
    COMMIT;

    v_oc := ARRAY(SELECT id_orden_compra FROM orden_compra ORDER BY id_orden_compra);
    v_noc := array_length(v_oc, 1);
    v_mp  := ARRAY(SELECT id_materia_prima FROM materia_prima ORDER BY id_materia_prima);
    v_nmp := array_length(v_mp, 1);

    WHILE v_hechas < v_faltan LOOP
        v_lote := LEAST(50000, v_faltan - v_hechas);

        INSERT INTO orden_compra_detalle (id_orden_compra, tipo_item, id_producto, id_materia_prima,
                                          cantidad, precio_unitario, cantidad_recibida)
        SELECT d.oc,
               d.tipo,
               CASE WHEN d.tipo = 'producto'      THEN 1 + (random()*107)::int ELSE NULL END,
               CASE WHEN d.tipo = 'materia_prima' THEN v_mp[1 + (random()*(v_nmp-1))::int] ELSE NULL END,
               d.cant,
               round((5 + random()*180)::numeric, 2),
               CASE WHEN random() < 0.6 THEN d.cant ELSE (random()*d.cant)::int END
        FROM (
            SELECT v_oc[1 + (random()*(v_noc-1))::int] AS oc,
                   CASE WHEN random() < 0.65 THEN 'producto' ELSE 'materia_prima' END AS tipo,
                   1 + (random()*40)::int AS cant
            FROM generate_series(1, v_lote) g
        ) d;

        v_hechas := v_hechas + v_lote;
        COMMIT;
        RAISE NOTICE '  orden_compra_detalle: % / %', v_hechas, v_faltan;
    END LOOP;
END $$;

-- Reconstruccion de orden_compra.total: mismo patron que pedido.total
\echo '--- Reconstruyendo orden_compra.total (UPDATE agregado unico) ---'
UPDATE orden_compra oc
SET total = COALESCE(d.suma, 0)
FROM (SELECT id_orden_compra, SUM(subtotal) AS suma
      FROM orden_compra_detalle GROUP BY id_orden_compra) d
WHERE d.id_orden_compra = oc.id_orden_compra;

-- ============================================================================
-- 11. LOG_ACCION  -> objetivo 200.000
-- ----------------------------------------------------------------------------
-- Reparto entre los 9 modulos existentes conservando su proporcion relativa
-- actual (auth 62,6 % / produccion 16,8 % / compras 8,4 % / devoluciones 5,3 % /
-- devoluciones_proveedor 2,3 % / pedidos 2,3 % / respaldos, comprobantes y
-- empaque 0,76 % cada uno). Las acciones de cada modulo son las que ya existen
-- en la tabla, no inventadas.
-- ============================================================================
DO $$
DECLARE
    v_objetivo int := 200000;
    v_actual   int;
    v_faltan   int;
    v_hechas   int := 0;
    v_lote     int;
    v_usr      int[];
    v_nusr     int;
BEGIN
    SELECT count(*) INTO v_actual FROM log_accion;
    v_faltan := GREATEST(v_objetivo - v_actual, 0);
    IF v_faltan = 0 THEN
        RAISE NOTICE 'log_accion: ya tiene % filas (objetivo %). Se salta.', v_actual, v_objetivo;
        RETURN;
    END IF;

    v_usr := ARRAY(SELECT id_usuario FROM usuario ORDER BY id_usuario);
    v_nusr := array_length(v_usr, 1);
    RAISE NOTICE 'log_accion: % actuales, faltan %', v_actual, v_faltan;

    WHILE v_hechas < v_faltan LOOP
        v_lote := LEAST(50000, v_faltan - v_hechas);

        INSERT INTO log_accion (id_usuario, modulo, accion, descripcion, ip_address, fecha)
        SELECT v_usr[1 + (random()*(v_nusr-1))::int],
               m.modulo, m.accion,
               m.modulo || ': ' || m.accion || ' generado en la carga masiva F38',
               '192.168.' || (random()*255)::int || '.' || (random()*255)::int,
               now() - (power(random(), 1.4) * interval '730 days')
        FROM (
            SELECT
                CASE
                WHEN x.r < 0.626 THEN 'auth'
                WHEN x.r < 0.794 THEN 'produccion'
                WHEN x.r < 0.878 THEN 'compras'
                WHEN x.r < 0.931 THEN 'devoluciones'
                WHEN x.r < 0.954 THEN 'devoluciones_proveedor'
                WHEN x.r < 0.977 THEN 'pedidos'
                WHEN x.r < 0.985 THEN 'respaldos'
                WHEN x.r < 0.992 THEN 'comprobantes'
                ELSE 'empaque' END AS modulo,
                CASE
                WHEN x.r < 0.626 THEN 'login'
                WHEN x.r < 0.794 THEN (ARRAY['crear','iniciar','completar'])[1+(x.r2*2)::int]
                WHEN x.r < 0.878 THEN (ARRAY['crear','cambio_estado','factura_crear','pago_registrar','recepcion'])[1+(x.r2*4)::int]
                WHEN x.r < 0.931 THEN (ARRAY['crear','iniciar_inspeccion','inspeccionar'])[1+(x.r2*2)::int]
                WHEN x.r < 0.954 THEN (ARRAY['crear','cambio_estado','resolver'])[1+(x.r2*2)::int]
                WHEN x.r < 0.977 THEN (ARRAY['crear','cambio_estado'])[1+(x.r2*1)::int]
                WHEN x.r < 0.985 THEN 'prueba_rto'
                WHEN x.r < 0.992 THEN 'generar'
                ELSE 'confirmar' END AS accion
            FROM (SELECT random() AS r, random() AS r2
                  FROM generate_series(1, v_lote) g) x
        ) m;

        v_hechas := v_hechas + v_lote;
        COMMIT;
        RAISE NOTICE '  log_accion: % / %', v_hechas, v_faltan;
    END LOOP;
END $$;

-- ============================================================================
-- 12. REACTIVACION DE LOS 5 TRIGGERS  — NO ES OPCIONAL
-- ----------------------------------------------------------------------------
-- Una tabla que queda con el trigger de recalculo apagado rompe la aplicacion
-- de forma silenciosa: los totales dejan de mantenerse solos y nadie se entera
-- hasta que alguien compara. Se reactivan y se comprueba contra el catalogo.
-- ============================================================================
\echo ''
\echo '--- Reactivando los 5 triggers ---'
ALTER TABLE detalle_pedido       ENABLE TRIGGER trg_recalcular_total_pedido_insert;
ALTER TABLE pedido               ENABLE TRIGGER trg_proteger_total_pedido;
ALTER TABLE pedido               ENABLE TRIGGER trg_recalcular_total_por_descuento;
ALTER TABLE orden_compra_detalle ENABLE TRIGGER trg_oc_total_insert;
ALTER TABLE orden_compra         ENABLE TRIGGER trg_proteger_total_oc;

DO $$
DECLARE v_apagados text;
BEGIN
    SELECT string_agg(c.relname || '.' || t.tgname, ', ')
      INTO v_apagados
    FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE NOT t.tgisinternal AND n.nspname = 'public' AND t.tgenabled <> 'O';

    IF v_apagados IS NOT NULL THEN
        RAISE EXCEPTION 'Quedaron triggers desactivados: %', v_apagados;
    END IF;
    RAISE NOTICE 'Verificado: los 24 triggers estan en tgenabled = O';
END $$;

\echo ''
\echo '--- Conteo DESPUES ---'
SELECT 'cliente' AS tabla, count(*) AS filas FROM cliente
UNION ALL SELECT 'inventario', count(*) FROM inventario
UNION ALL SELECT 'pedido', count(*) FROM pedido
UNION ALL SELECT 'detalle_pedido', count(*) FROM detalle_pedido
UNION ALL SELECT 'comprobante_interno', count(*) FROM comprobante_interno
UNION ALL SELECT 'movimiento_inventario', count(*) FROM movimiento_inventario
UNION ALL SELECT 'historial_inventario', count(*) FROM historial_inventario
UNION ALL SELECT 'orden_compra', count(*) FROM orden_compra
UNION ALL SELECT 'orden_compra_detalle', count(*) FROM orden_compra_detalle
UNION ALL SELECT 'log_accion', count(*) FROM log_accion
ORDER BY 1;

\echo ''
\echo '=== FASE 38 - CARGA COMPLETADA ==='
