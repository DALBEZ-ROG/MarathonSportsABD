-- ============================================================================
-- FASE 39 · ETAPA 1 — VOLUMEN COMPLEMENTARIO EN COMPRAS Y MANUFACTURA
-- ----------------------------------------------------------------------------
-- El millon de la F38 quedo concentrado en el modulo de ventas. Compras y
-- manufactura tienen entre 1 y 12 filas, asi que sus indices no son evaluables
-- y sus invariantes financieros se verificaban sobre 2 filas. Objetivo modesto:
-- ~40.000 filas.
--
-- EJECUTAR COMO postgres:
--     psql -U postgres -d mod_venta_inve -f fase39_volumen_compras.sql
--
-- REGLA HEREDADA DE LA F38, LA MAS IMPORTANTE DE ESTE ARCHIVO:
--   random() va SIEMPRE en el SELECT de una subconsulta sobre generate_series,
--   nunca en un CROSS JOIN LATERAL sin correlacionar. PostgreSQL trata ese
--   LATERAL como subconsulta no correlacionada y lo evalua UNA VEZ POR
--   SENTENCIA: el lote entero sale con el mismo valor. Ese bug costo una
--   redistribucion completa en la F38.
--
-- IDEMPOTENTE: cada bloque cuenta primero e inserta solo la diferencia.
-- ============================================================================

\set ON_ERROR_STOP on
\timing on

\echo ''
\echo '=== FASE 39 ETAPA 1: volumen en compras y manufactura ==='

DO $$
BEGIN
    IF current_user <> 'postgres' THEN
        RAISE EXCEPTION 'Ejecutar como postgres (actual: %)', current_user;
    END IF;
END $$;

-- ============================================================================
-- 0. TRIGGERS QUE ESTORBAN A LA CARGA
-- ----------------------------------------------------------------------------
-- Se dejan ACTIVOS a proposito trg_validar_op_producto_fabricado y
-- trg_validar_bom_producto_fabricado: comprueban que el producto sea fabricado
-- y son una verificacion gratuita de que la conversion del paso 2 funciono.
-- ============================================================================
ALTER TABLE pago_proveedor    DISABLE TRIGGER trg_cxp_pagado_insert;
ALTER TABLE cuenta_por_pagar  DISABLE TRIGGER trg_proteger_monto_pagado_cxp;
ALTER TABLE orden_produccion  DISABLE TRIGGER trg_proteger_costo_materia_prima_op;

-- ============================================================================
-- 1. MATERIA_PRIMA -> 300
-- ----------------------------------------------------------------------------
-- uq_materia_prima_nombre obliga a nombres unicos: se construyen con el offset
-- del MAX actual para que una reejecucion no colisione.
-- ============================================================================
DO $$
DECLARE
    v_obj int := 300; v_act int; v_falt int; v_off int;
    v_mat text[] := ARRAY['Cuero','Malla','Caucho','Espuma EVA','Nylon','Poliester','Algodon',
                          'Hilo','Pegamento','Cordon','Plantilla','Suela','Etiqueta','Ojal',
                          'Refuerzo','Forro','Velcro','Cremallera','Tinte','Elastico'];
    v_cal text[] := ARRAY['premium','estandar','reciclado','importado','nacional'];
BEGIN
    SELECT count(*) INTO v_act FROM materia_prima;
    v_falt := GREATEST(v_obj - v_act, 0);
    IF v_falt = 0 THEN RAISE NOTICE 'materia_prima: ya tiene % (obj %). Se salta.', v_act, v_obj; RETURN; END IF;
    SELECT COALESCE(MAX(id_materia_prima),0) INTO v_off FROM materia_prima;

    INSERT INTO materia_prima (nombre, descripcion, id_unidad_medida, estado,
                               stock_actual, stock_minimo, costo_unitario_promedio, created_at)
    SELECT v_mat[1 + (s.r1*19)::int] || ' ' || v_cal[1 + (s.r2*4)::int] || ' #' || (v_off + s.g),
           'Materia prima generada en la carga F39',
           1 + (s.r3*8)::int,
           CASE WHEN s.r4 < 0.93 THEN 'activo' ELSE 'inactivo' END,
           round((s.r5 * 900)::numeric, 2),
           round((10 + s.r6 * 90)::numeric, 2),
           round((0.5 + s.r7 * 120)::numeric, 2),
           now() - (power(s.r8, 1.3) * interval '730 days')
    FROM (SELECT g, random() r1, random() r2, random() r3, random() r4,
                 random() r5, random() r6, random() r7, random() r8
          FROM generate_series(1, v_falt) g) s;
    RAISE NOTICE 'materia_prima: +% filas', v_falt;
END $$;

-- ============================================================================
-- 2. PRODUCTOS FABRICADOS -> 15 %
-- ----------------------------------------------------------------------------
-- orden_produccion y lista_materiales exigen origen='fabricado' (lo imponen dos
-- triggers). Solo habia 3 de 108 productos, asi que 3.000 ordenes de produccion
-- se repartirian sobre 3 productos. El cambio comprado -> fabricado esta
-- permitido: fn_validar_cambio_origen_producto solo bloquea el sentido
-- contrario cuando hay BOM activo.
-- ============================================================================
DO $$
DECLARE v_obj int; v_act int;
BEGIN
    SELECT count(*) INTO v_act FROM producto WHERE origen='fabricado';
    v_obj := GREATEST((SELECT count(*) FROM producto) * 15 / 100, 16);
    IF v_act >= v_obj THEN
        RAISE NOTICE 'producto fabricado: ya hay % (obj %). Se salta.', v_act, v_obj; RETURN;
    END IF;
    UPDATE producto SET origen='fabricado'
    WHERE id_producto IN (
        SELECT id_producto FROM producto WHERE origen='comprado'
        ORDER BY id_producto LIMIT (v_obj - v_act));
    RAISE NOTICE 'producto: % pasan a fabricado (total %)', v_obj - v_act, v_obj;
END $$;

-- ============================================================================
-- 3. LISTA_MATERIALES -> 900
-- ----------------------------------------------------------------------------
-- DESVIACION DECLARADA: el encargo pide 900 BOM con "~3 componentes por
-- producto fabricado". Las dos cosas son incompatibles con este catalogo: 900
-- BOM a 3 componentes exigirian 300 productos fabricados, y solo hay 108
-- productos en total. Se prioriza el VOLUMEN (que es lo que esta fase necesita
-- para medir indices) y se reparte de forma NO uniforme entre los 16 productos
-- fabricados. El ratio resultante (~56 componentes por producto) no es
-- realista para calzado; queda anotado en OPTIMIZACION_CONSULTAS_V2.md.
-- uq_bom_producto_materia limita a 16 x 300 = 4.800 combinaciones: 900 cabe.
-- ============================================================================
DO $$
DECLARE v_obj int := 900; v_act int; v_falt int; v_libres int;
BEGIN
    SELECT count(*) INTO v_act FROM lista_materiales;
    v_falt := GREATEST(v_obj - v_act, 0);
    IF v_falt = 0 THEN RAISE NOTICE 'lista_materiales: ya tiene % (obj %). Se salta.', v_act, v_obj; RETURN; END IF;

    SELECT count(*) INTO v_libres
    FROM producto p CROSS JOIN materia_prima m
    WHERE p.origen='fabricado'
      AND NOT EXISTS (SELECT 1 FROM lista_materiales l
                      WHERE l.id_producto=p.id_producto AND l.id_materia_prima=m.id_materia_prima);
    v_falt := LEAST(v_falt, v_libres);

    INSERT INTO lista_materiales (id_producto, id_materia_prima, cantidad_necesaria, estado, created_at)
    SELECT c.id_producto, c.id_materia_prima,
           round((0.05 + c.r1 * 6)::numeric, 3),
           CASE WHEN c.r2 < 0.90 THEN 'activo' ELSE 'inactivo' END,
           now() - (power(c.r3, 1.3) * interval '730 days')
    FROM (
        SELECT p.id_producto, m.id_materia_prima,
               random() r1, random() r2, random() r3,
               -- reparto no uniforme: unos productos con muchos componentes
               row_number() OVER (PARTITION BY p.id_producto ORDER BY random()) AS rn,
               (20 + (abs(hashtext(p.id_producto::text)) % 90)) AS cupo
        FROM producto p CROSS JOIN materia_prima m
        WHERE p.origen='fabricado'
          AND NOT EXISTS (SELECT 1 FROM lista_materiales l
                          WHERE l.id_producto=p.id_producto AND l.id_materia_prima=m.id_materia_prima)
    ) c
    WHERE c.rn <= c.cupo
    LIMIT v_falt;
    RAISE NOTICE 'lista_materiales: +% filas', v_falt;
END $$;

-- ============================================================================
-- 4. RECEPCION_MERCANCIA -> 2.400  y  RECEPCION_MERCANCIA_DETALLE -> 7.000
-- ============================================================================
DO $$
DECLARE v_obj int := 2400; v_act int; v_falt int; v_usr int[]; v_nusr int;
BEGIN
    SELECT count(*) INTO v_act FROM recepcion_mercancia;
    v_falt := GREATEST(v_obj - v_act, 0);
    IF v_falt = 0 THEN RAISE NOTICE 'recepcion_mercancia: ya tiene %. Se salta.', v_act; RETURN; END IF;
    v_usr := ARRAY(SELECT id_usuario FROM usuario ORDER BY id_usuario);
    v_nusr := array_length(v_usr,1);

    INSERT INTO recepcion_mercancia (id_orden_compra, id_usuario_receptor, id_bodega,
                                     fecha_recepcion, numero_guia_remision, observaciones, created_at)
    SELECT s.id_orden_compra,
           v_usr[1 + (s.r1*(v_nusr-1))::int],
           1 + (s.r2*19)::int,
           s.fecha,
           'GR-' || lpad((s.rn)::text, 8, '0'),
           NULL,
           s.fecha
    FROM (
        SELECT oc.id_orden_compra,
               row_number() OVER (ORDER BY oc.id_orden_compra) AS rn,
               random() r1, random() r2,
               oc.fecha_orden + (random() * interval '20 days') AS fecha
        FROM orden_compra oc
        WHERE oc.estado IN ('recibida_completa','recibida_parcial')
          AND NOT EXISTS (SELECT 1 FROM recepcion_mercancia r WHERE r.id_orden_compra = oc.id_orden_compra)
        ORDER BY oc.id_orden_compra
        LIMIT v_falt
    ) s;
    RAISE NOTICE 'recepcion_mercancia: +% filas', v_falt;
END $$;

DO $$
DECLARE v_obj int := 7000; v_act int; v_falt int;
BEGIN
    SELECT count(*) INTO v_act FROM recepcion_mercancia_detalle;
    v_falt := GREATEST(v_obj - v_act, 0);
    IF v_falt = 0 THEN RAISE NOTICE 'recepcion_mercancia_detalle: ya tiene %. Se salta.', v_act; RETURN; END IF;

    -- Cada linea de recepcion apunta a una linea de la MISMA orden de compra que
    -- la recepcion padre: sin esa correlacion el dato seria incoherente.
    INSERT INTO recepcion_mercancia_detalle (id_recepcion, id_detalle_oc,
                                             cantidad_recibida_ahora, cantidad_defectuosa, observacion)
    SELECT s.id_recepcion, s.id_detalle_oc,
           s.cant,
           CASE WHEN s.r1 < 0.12 THEN LEAST((s.r2 * s.cant)::int, s.cant) ELSE 0 END,
           NULL
    FROM (
        SELECT r.id_recepcion, d.id_detalle_oc,
               GREATEST(d.cantidad, 1) AS cant,
               random() r1, random() r2,
               row_number() OVER () AS rn
        FROM recepcion_mercancia r
        JOIN orden_compra_detalle d ON d.id_orden_compra = r.id_orden_compra
        WHERE NOT EXISTS (SELECT 1 FROM recepcion_mercancia_detalle x
                          WHERE x.id_recepcion = r.id_recepcion AND x.id_detalle_oc = d.id_detalle_oc)
        LIMIT v_falt
    ) s;
    RAISE NOTICE 'recepcion_mercancia_detalle: +% filas', v_falt;
END $$;

-- ============================================================================
-- 5. FACTURA_COMPRA -> 2.400   (total es GENERATED: se omite)
-- ============================================================================
DO $$
DECLARE v_obj int := 2400; v_act int; v_falt int; v_usr int[]; v_nusr int;
BEGIN
    SELECT count(*) INTO v_act FROM factura_compra;
    v_falt := GREATEST(v_obj - v_act, 0);
    IF v_falt = 0 THEN RAISE NOTICE 'factura_compra: ya tiene %. Se salta.', v_act; RETURN; END IF;
    v_usr := ARRAY(SELECT id_usuario FROM usuario ORDER BY id_usuario);
    v_nusr := array_length(v_usr,1);

    INSERT INTO factura_compra (id_orden_compra, id_usuario_registro, numero_factura_proveedor,
                                fecha_factura, fecha_vencimiento, subtotal, impuesto, estado, created_at)
    SELECT s.id_orden_compra,
           v_usr[1 + (s.r1*(v_nusr-1))::int],
           'FP-' || lpad(s.rn::text, 9, '0'),
           s.fecha::date,
           (s.fecha + interval '30 days')::date,     -- chk_fc_vencimiento: >= fecha_factura
           s.sub,
           round((s.sub * 0.15)::numeric, 2),
           CASE WHEN s.r2 < 0.55 THEN 'pagada' WHEN s.r2 < 0.95 THEN 'pendiente' ELSE 'anulada' END,
           s.fecha
    FROM (
        SELECT oc.id_orden_compra,
               row_number() OVER (ORDER BY oc.id_orden_compra) AS rn,
               random() r1, random() r2,
               oc.fecha_orden + (random() * interval '25 days') AS fecha,
               GREATEST(oc.total, 1.00) AS sub          -- chk_fc_subtotal: > 0
        FROM orden_compra oc
        WHERE NOT EXISTS (SELECT 1 FROM factura_compra f WHERE f.id_orden_compra = oc.id_orden_compra)
        ORDER BY oc.id_orden_compra
        LIMIT v_falt
    ) s;
    RAISE NOTICE 'factura_compra: +% filas', v_falt;
END $$;

-- ============================================================================
-- 6. CUENTA_POR_PAGAR -> 2.400   (UNIQUE por factura; saldo_pendiente GENERATED)
-- ============================================================================
DO $$
DECLARE v_obj int := 2400; v_act int; v_falt int;
BEGIN
    SELECT count(*) INTO v_act FROM cuenta_por_pagar;
    v_falt := GREATEST(v_obj - v_act, 0);
    IF v_falt = 0 THEN RAISE NOTICE 'cuenta_por_pagar: ya tiene %. Se salta.', v_act; RETURN; END IF;

    INSERT INTO cuenta_por_pagar (id_factura_compra, id_proveedor, monto_total, monto_pagado,
                                  fecha_vencimiento, estado, created_at)
    SELECT s.id_factura_compra, s.id_proveedor, s.monto, 0,
           s.venc,
           CASE WHEN s.venc < current_date THEN 'vencida' ELSE 'vigente' END,
           s.creado
    FROM (
        SELECT f.id_factura_compra, oc.id_proveedor,
               f.total AS monto, f.fecha_vencimiento AS venc, f.created_at AS creado
        FROM factura_compra f
        JOIN orden_compra oc ON oc.id_orden_compra = f.id_orden_compra
        WHERE f.estado <> 'anulada'
          AND NOT EXISTS (SELECT 1 FROM cuenta_por_pagar c WHERE c.id_factura_compra = f.id_factura_compra)
        ORDER BY f.id_factura_compra
        LIMIT v_falt
    ) s;
    RAISE NOTICE 'cuenta_por_pagar: +% filas', v_falt;
END $$;

-- ============================================================================
-- 7. PAGO_PROVEEDOR -> 2.000   (varios pagos parciales por cuenta)
-- ----------------------------------------------------------------------------
-- chk_cxp_montos exige monto_pagado <= monto_total, asi que cada pago se acota
-- para que la suma por cuenta no supere el total. Se reparte en 1 a 3 pagos.
-- ============================================================================
DO $$
DECLARE v_obj int := 2000; v_act int; v_falt int; v_usr int[]; v_nusr int;
BEGIN
    SELECT count(*) INTO v_act FROM pago_proveedor;
    v_falt := GREATEST(v_obj - v_act, 0);
    IF v_falt = 0 THEN RAISE NOTICE 'pago_proveedor: ya tiene %. Se salta.', v_act; RETURN; END IF;
    v_usr := ARRAY(SELECT id_usuario FROM usuario ORDER BY id_usuario);
    v_nusr := array_length(v_usr,1);

    INSERT INTO pago_proveedor (id_cuenta_pagar, id_usuario_registro, monto, fecha_pago,
                                metodo_pago, referencia, observaciones)
    SELECT s.id_cuenta_pagar,
           v_usr[1 + (s.r1*(v_nusr-1))::int],
           s.monto_pago,
           s.fecha,
           (ARRAY['transferencia','cheque','efectivo','tarjeta'])[1 + (s.r2*3)::int],
           'REF-' || lpad(s.rn::text, 9, '0'),
           NULL
    FROM (
        SELECT c.id_cuenta_pagar,
               row_number() OVER () AS rn,
               random() r1, random() r2,
               -- Cada cuenta recibe entre 1 y 3 pagos y el importe se divide.
               -- TRUNC, NO ROUND: chk_cxp_montos exige monto_pagado <= monto_total,
               -- y n * round(total/n, 2) puede SUPERAR el total por redondeo hacia
               -- arriba (10/3 -> 3.34 x 3 = 10.02 > 10). Con trunc la suma nunca
               -- excede, y la cuenta queda parcialmente pagada, que ademas es un
               -- dato mas realista. Esto reventó en la primera pasada de la F39:
               -- 300 cuentas sobrepagadas.
               GREATEST(trunc(c.monto_total / k.n, 2), 0.01) AS monto_pago,
               c.created_at + (random() * interval '60 days') AS fecha
        FROM cuenta_por_pagar c
        CROSS JOIN LATERAL (SELECT 1 + (abs(hashtext(c.id_cuenta_pagar::text)) % 3) AS n) k
        CROSS JOIN LATERAL generate_series(1, k.n) q
        WHERE c.monto_total >= 1.00     -- garantiza trunc(total/3, 2) > 0
          AND NOT EXISTS (SELECT 1 FROM pago_proveedor p WHERE p.id_cuenta_pagar = c.id_cuenta_pagar)
        LIMIT v_falt
    ) s;
    RAISE NOTICE 'pago_proveedor: +% filas', v_falt;
END $$;

-- Reconstruccion de cuenta_por_pagar.monto_pagado: UPDATE agregado unico.
-- Formula literal de fn_recalcular_monto_pagado_cxp: COALESCE(SUM(monto), 0).
-- No hace falta acotar con LEAST porque los pagos ya se generaron con trunc.
\echo '--- Reconstruyendo cuenta_por_pagar.monto_pagado ---'
UPDATE cuenta_por_pagar c
SET monto_pagado = COALESCE(p.s, 0),
    estado = CASE WHEN COALESCE(p.s,0) >= c.monto_total THEN 'pagada'
                  WHEN c.fecha_vencimiento < current_date THEN 'vencida'
                  ELSE 'vigente' END
FROM (SELECT id_cuenta_pagar, SUM(monto) AS s FROM pago_proveedor GROUP BY id_cuenta_pagar) p
WHERE p.id_cuenta_pagar = c.id_cuenta_pagar;

-- ============================================================================
-- 8. ORDEN_PRODUCCION -> 3.000   (costo_total y costo_unitario son GENERATED)
-- ============================================================================
DO $$
DECLARE v_obj int := 3000; v_act int; v_falt int; v_usr int[]; v_nusr int; v_prod int[]; v_nprod int;
BEGIN
    SELECT count(*) INTO v_act FROM orden_produccion;
    v_falt := GREATEST(v_obj - v_act, 0);
    IF v_falt = 0 THEN RAISE NOTICE 'orden_produccion: ya tiene %. Se salta.', v_act; RETURN; END IF;
    v_usr := ARRAY(SELECT id_usuario FROM usuario ORDER BY id_usuario);
    v_nusr := array_length(v_usr,1);
    v_prod := ARRAY(SELECT id_producto FROM producto WHERE origen='fabricado' ORDER BY id_producto);
    v_nprod := array_length(v_prod,1);

    INSERT INTO orden_produccion (id_producto, id_bodega_destino, id_usuario_registro, id_usuario_completa,
                                  cantidad_planificada, cantidad_producida, estado, fecha_creacion,
                                  fecha_inicio, fecha_fin, observaciones,
                                  costo_materia_prima, costo_mano_obra, costo_indirecto)
    SELECT v_prod[1 + (s.r1*(v_nprod-1))::int],
           1 + (s.r2*19)::int,
           v_usr[1 + (s.r3*(v_nusr-1))::int],
           CASE WHEN s.est='completada' THEN v_usr[1] ELSE NULL END,
           s.plan,
           CASE WHEN s.est='completada' THEN s.plan - (s.r4*3)::int ELSE NULL END,
           s.est,
           s.fecha,
           CASE WHEN s.est IN ('en_proceso','completada') THEN s.fecha + interval '1 day' ELSE NULL END,
           CASE WHEN s.est='completada' THEN s.fecha + interval '5 days' ELSE NULL END,
           NULL,
           0,                                             -- se reconstruye abajo
           round((50 + s.r5 * 400)::numeric, 2),
           round((20 + s.r6 * 150)::numeric, 2)
    FROM (
        SELECT random() r1, random() r2, random() r3, random() r4, random() r5, random() r6,
               10 + (random()*190)::int AS plan,
               now() - (power(random(),1.4) * interval '730 days') AS fecha,
               CASE WHEN random() < 0.62 THEN 'completada'
                    WHEN random() < 0.80 THEN 'en_proceso'
                    WHEN random() < 0.94 THEN 'planificada'
                    ELSE 'cancelada' END AS est
        FROM generate_series(1, v_falt) g
    ) s;
    RAISE NOTICE 'orden_produccion: +% filas', v_falt;
END $$;

-- ============================================================================
-- 9. ORDEN_PRODUCCION_CONSUMO -> 9.000  (merma y costo_linea son GENERATED)
-- ----------------------------------------------------------------------------
-- uq_opc_orden_materia: una materia por orden como maximo.
-- ============================================================================
DO $$
DECLARE v_obj int := 9000; v_act int; v_falt int;
BEGIN
    SELECT count(*) INTO v_act FROM orden_produccion_consumo;
    v_falt := GREATEST(v_obj - v_act, 0);
    IF v_falt = 0 THEN RAISE NOTICE 'orden_produccion_consumo: ya tiene %. Se salta.', v_act; RETURN; END IF;

    INSERT INTO orden_produccion_consumo (id_orden_produccion, id_materia_prima, cantidad_teorica,
                                          cantidad_real, costo_unitario_snapshot)
    SELECT s.id_orden_produccion, s.id_materia_prima,
           s.teorica,
           CASE WHEN s.r1 < 0.85 THEN round((s.teorica * (0.95 + s.r2*0.15))::numeric, 3) ELSE NULL END,
           s.costo
    FROM (
        SELECT o.id_orden_produccion, m.id_materia_prima,
               round((1 + random()*40)::numeric, 3) AS teorica,
               random() r1, random() r2,
               m.costo_unitario_promedio AS costo,
               row_number() OVER (PARTITION BY o.id_orden_produccion ORDER BY random()) AS rn
        FROM orden_produccion o
        JOIN LATERAL (SELECT id_materia_prima, costo_unitario_promedio FROM materia_prima
                      ORDER BY random() LIMIT 3) m ON true
        WHERE NOT EXISTS (SELECT 1 FROM orden_produccion_consumo c
                          WHERE c.id_orden_produccion = o.id_orden_produccion
                            AND c.id_materia_prima = m.id_materia_prima)
        LIMIT v_falt
    ) s;
    RAISE NOTICE 'orden_produccion_consumo: +% filas', v_falt;
END $$;

-- Reconstruccion de orden_produccion.costo_materia_prima: UPDATE agregado unico.
-- Formula literal de fn_proteger_costo_materia_prima_op:
--     ROUND(COALESCE(SUM(costo_linea), 0), 2)
\echo '--- Reconstruyendo orden_produccion.costo_materia_prima ---'
UPDATE orden_produccion o
SET costo_materia_prima = ROUND(COALESCE(c.s, 0), 2)
FROM (SELECT id_orden_produccion, SUM(costo_linea) AS s
      FROM orden_produccion_consumo GROUP BY id_orden_produccion) c
WHERE c.id_orden_produccion = o.id_orden_produccion;

UPDATE orden_produccion o SET costo_materia_prima = 0
WHERE NOT EXISTS (SELECT 1 FROM orden_produccion_consumo c
                  WHERE c.id_orden_produccion = o.id_orden_produccion)
  AND o.costo_materia_prima <> 0;

-- ============================================================================
-- 10. MOVIMIENTO_MATERIA_PRIMA -> 12.000
-- ============================================================================
DO $$
DECLARE v_obj int := 12000; v_act int; v_falt int; v_lote int; v_hechas int := 0;
        v_usr int[]; v_nusr int; v_mp int[]; v_nmp int; v_op int[]; v_nop int;
BEGIN
    SELECT count(*) INTO v_act FROM movimiento_materia_prima;
    v_falt := GREATEST(v_obj - v_act, 0);
    IF v_falt = 0 THEN RAISE NOTICE 'movimiento_materia_prima: ya tiene %. Se salta.', v_act; RETURN; END IF;
    v_usr := ARRAY(SELECT id_usuario FROM usuario ORDER BY id_usuario);   v_nusr := array_length(v_usr,1);
    v_mp  := ARRAY(SELECT id_materia_prima FROM materia_prima ORDER BY 1); v_nmp := array_length(v_mp,1);
    v_op  := ARRAY(SELECT id_orden_produccion FROM orden_produccion ORDER BY 1 LIMIT 5000);
    v_nop := COALESCE(array_length(v_op,1),0);

    WHILE v_hechas < v_falt LOOP
        v_lote := LEAST(50000, v_falt - v_hechas);
        INSERT INTO movimiento_materia_prima (id_materia_prima, id_usuario, tipo_movimiento, cantidad,
                                              stock_anterior, stock_nuevo, id_recepcion,
                                              id_orden_produccion, observacion, fecha)
        SELECT v_mp[1 + (s.r1*(v_nmp-1))::int],
               v_usr[1 + (s.r2*(v_nusr-1))::int],
               s.tipo,
               s.cant,
               s.ant,
               GREATEST(s.ant + CASE WHEN s.tipo='entrada_compra' THEN s.cant ELSE -s.cant END, 0),
               NULL,
               CASE WHEN s.tipo='salida_produccion' AND v_nop > 0
                    THEN v_op[1 + (s.r3*(v_nop-1))::int] ELSE NULL END,
               'Movimiento MP generado en la carga F39',
               s.fecha
        FROM (
            SELECT random() r1, random() r2, random() r3,
                   round((1 + random()*90)::numeric, 3) AS cant,
                   round((random()*800)::numeric, 3)    AS ant,
                   now() - (power(random(),1.4) * interval '730 days') AS fecha,
                   CASE WHEN random() < 0.42 THEN 'salida_produccion'
                        WHEN random() < 0.78 THEN 'entrada_compra'
                        WHEN random() < 0.93 THEN 'ajuste'
                        ELSE 'merma' END AS tipo
            FROM generate_series(1, v_lote) g
        ) s;
        v_hechas := v_hechas + v_lote;
        COMMIT;
        RAISE NOTICE '  movimiento_materia_prima: % / %', v_hechas, v_falt;
    END LOOP;
END $$;

-- ============================================================================
-- 11. SOLICITUD_DEVOLUCION -> 800   y   DEVOLUCION_PROVEEDOR -> 400
-- ============================================================================
DO $$
DECLARE v_obj int := 800; v_act int; v_falt int; v_usr int[]; v_nusr int;
BEGIN
    SELECT count(*) INTO v_act FROM solicitud_devolucion;
    v_falt := GREATEST(v_obj - v_act, 0);
    IF v_falt = 0 THEN RAISE NOTICE 'solicitud_devolucion: ya tiene %. Se salta.', v_act; RETURN; END IF;
    v_usr := ARRAY(SELECT id_usuario FROM usuario ORDER BY id_usuario); v_nusr := array_length(v_usr,1);

    INSERT INTO solicitud_devolucion (id_pedido, id_usuario_registro, motivo, descripcion, estado,
                                      fecha_solicitud, fecha_inspeccion, id_usuario_inspector, created_at)
    SELECT s.id_pedido,
           v_usr[1 + (s.r1*(v_nusr-1))::int],
           (ARRAY['producto_defectuoso','talla_incorrecta','no_esperado','cambio_opinion',
                  'producto_incompleto','otro'])[1 + (s.r2*5)::int],
           'Solicitud generada en la carga F39',
           s.est,
           s.fecha,
           CASE WHEN s.est IN ('completada','rechazada') THEN s.fecha + interval '3 days' ELSE NULL END,
           CASE WHEN s.est IN ('completada','rechazada') THEN v_usr[1] ELSE NULL END,
           s.fecha
    FROM (
        SELECT p.id_pedido, random() r1, random() r2,
               p.fecha_pedido + (random() * interval '15 days') AS fecha,
               CASE WHEN random() < 0.55 THEN 'completada'
                    WHEN random() < 0.72 THEN 'en_inspeccion'
                    WHEN random() < 0.88 THEN 'solicitada'
                    ELSE 'rechazada' END AS est
        FROM pedido p
        WHERE p.estado = 'entregado'
          AND NOT EXISTS (SELECT 1 FROM solicitud_devolucion d WHERE d.id_pedido = p.id_pedido)
        ORDER BY p.id_pedido
        LIMIT v_falt
    ) s;
    RAISE NOTICE 'solicitud_devolucion: +% filas', v_falt;
END $$;

DO $$
DECLARE v_obj int := 400; v_act int; v_falt int; v_usr int[]; v_nusr int;
BEGIN
    SELECT count(*) INTO v_act FROM devolucion_proveedor;
    v_falt := GREATEST(v_obj - v_act, 0);
    IF v_falt = 0 THEN RAISE NOTICE 'devolucion_proveedor: ya tiene %. Se salta.', v_act; RETURN; END IF;
    v_usr := ARRAY(SELECT id_usuario FROM usuario ORDER BY id_usuario); v_nusr := array_length(v_usr,1);

    INSERT INTO devolucion_proveedor (id_proveedor, id_usuario_registro, fecha_devolucion, estado,
                                      tipo_resolucion, monto_reembolso, observaciones, created_at)
    SELECT 1 + (s.r1*5)::int,
           v_usr[1 + (s.r2*(v_nusr-1))::int],
           s.fecha,
           s.est,
           CASE WHEN s.est='resuelta' THEN (ARRAY['reembolso','reposicion'])[1 + (s.r3*1)::int] ELSE NULL END,
           CASE WHEN s.est='resuelta' AND s.r3 < 0.5 THEN round((10 + s.r4*900)::numeric,2) ELSE NULL END,
           'Devolucion generada en la carga F39',
           s.fecha
    FROM (
        SELECT random() r1, random() r2, random() r3, random() r4,
               now() - (power(random(),1.4) * interval '730 days') AS fecha,
               CASE WHEN random() < 0.50 THEN 'resuelta'
                    WHEN random() < 0.72 THEN 'enviada'
                    WHEN random() < 0.90 THEN 'pendiente'
                    ELSE 'rechazada' END AS est
        FROM generate_series(1, v_falt) g
    ) s;
    RAISE NOTICE 'devolucion_proveedor: +% filas', v_falt;
END $$;

-- ============================================================================
-- 12. REACTIVACION Y VERIFICACION DE LOS 24 TRIGGERS
-- ============================================================================
\echo ''
\echo '--- Reactivando triggers ---'
ALTER TABLE pago_proveedor    ENABLE TRIGGER trg_cxp_pagado_insert;
ALTER TABLE cuenta_por_pagar  ENABLE TRIGGER trg_proteger_monto_pagado_cxp;
ALTER TABLE orden_produccion  ENABLE TRIGGER trg_proteger_costo_materia_prima_op;

DO $$
DECLARE v_apagados text; v_n int;
BEGIN
    SELECT string_agg(c.relname||'.'||t.tgname, ', ') INTO v_apagados
    FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
    JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE NOT t.tgisinternal AND n.nspname='public' AND t.tgenabled<>'O';
    IF v_apagados IS NOT NULL THEN
        RAISE EXCEPTION 'FALLO: triggers apagados: %', v_apagados;
    END IF;
    SELECT count(*) INTO v_n FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
    JOIN pg_namespace n ON n.oid=c.relnamespace WHERE NOT t.tgisinternal AND n.nspname='public';
    RAISE NOTICE 'Triggers: % de % en tgenabled = O', v_n, v_n;
END $$;

ANALYZE;

\echo ''
\echo '--- Conteo final ---'
SELECT 'materia_prima' t, count(*) n FROM materia_prima
UNION ALL SELECT 'lista_materiales', count(*) FROM lista_materiales
UNION ALL SELECT 'recepcion_mercancia', count(*) FROM recepcion_mercancia
UNION ALL SELECT 'recepcion_mercancia_detalle', count(*) FROM recepcion_mercancia_detalle
UNION ALL SELECT 'factura_compra', count(*) FROM factura_compra
UNION ALL SELECT 'cuenta_por_pagar', count(*) FROM cuenta_por_pagar
UNION ALL SELECT 'pago_proveedor', count(*) FROM pago_proveedor
UNION ALL SELECT 'orden_produccion', count(*) FROM orden_produccion
UNION ALL SELECT 'orden_produccion_consumo', count(*) FROM orden_produccion_consumo
UNION ALL SELECT 'movimiento_materia_prima', count(*) FROM movimiento_materia_prima
UNION ALL SELECT 'solicitud_devolucion', count(*) FROM solicitud_devolucion
UNION ALL SELECT 'devolucion_proveedor', count(*) FROM devolucion_proveedor
ORDER BY 1;

\echo ''
\echo '=== FASE 39 ETAPA 1 COMPLETADA ==='
