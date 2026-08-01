-- =====================================================================
-- Fase 31 — SEED DE DEMOSTRACIÓN de los bloques nuevos
--   Bloque 8 (Compras / Procure-to-Pay), Devoluciones y Bloque 9 (Manufactura)
-- =====================================================================
-- Datos PERMANENTES de demostración (no son datos de prueba a borrar).
--
-- IDEMPOTENTE: se puede ejecutar más de una vez sin duplicar.
--   - materia_prima y producto usan ON CONFLICT (nombre) DO NOTHING.
--   - El resto va dentro de un DO block con guard: si ya existe una orden
--     de compra marcada 'DEMO F31%', no vuelve a sembrar.
--
-- RESPETA LAS REGLAS DE NEGOCIO:
--   - NO escribe columnas GENERATED (subtotal, total, saldo_pendiente,
--     merma, costo_linea, costo_total, costo_unitario_producido).
--   - NO escribe orden_compra.total ni cuenta_por_pagar.monto_pagado
--     (los calculan triggers).
--   - orden_produccion.costo_materia_prima se fija con la función
--     fn_set_costo_materia_prima_op() (respeta el trigger de protección F29).
--   - SET LOCAL app.current_user_id antes de tocar inventario.stock_actual.
--
-- DECISIÓN — recepciones de mercancía (Opción A):
--   Las recepciones se insertan replicando en SQL lo que hace
--   RecepcionMercanciaService. Es seguro porque cada materia prima parte de
--   stock 0 / costo 0 y recibe UNA sola vez: el costo promedio ponderado
--   resulta exactamente igual al precio de compra
--   ( ((0*0) + (cant*precio)) / (0+cant) = precio ), sin redondeos.
-- =====================================================================

SET client_encoding = 'UTF8';

-- ---------------------------------------------------------------------
-- (a) MATERIA PRIMA — 10 items realistas para confección deportiva
--     Se crean con stock 0 y costo 0; el stock/costo real lo produce la
--     recepción de las órdenes de compra más abajo.
-- ---------------------------------------------------------------------
INSERT INTO materia_prima (nombre, descripcion, id_unidad_medida, estado, stock_actual, stock_minimo, costo_unitario_promedio) VALUES
  ('Tela dry-fit poliéster',   'Tela técnica transpirable para camisetas deportivas', 6, 'activo', 0, 50, 0),
  ('Tela algodón jersey',      'Algodón 100% para gorras y prendas casuales',         6, 'activo', 0, 40, 0),
  ('Tela mesh transpirable',   'Malla para paneles de ventilación',                   6, 'activo', 0, 30, 0),
  ('Hilo poliéster industrial','Cono de hilo para costura industrial',                1, 'activo', 0, 20, 0),
  ('Etiqueta bordada Marathon','Etiqueta de marca bordada para prendas',              1, 'activo', 0, 200, 0),
  ('Cinta elástica 2cm',       'Elástico para cinturas de shorts',                    6, 'activo', 0, 100, 0),
  ('Botón plástico 12mm',      'Botón para gorras y prendas',                         1, 'activo', 0, 500, 0),
  ('Cierre metálico 20cm',     'Cierre para chaquetas y bolsillos',                   1, 'activo', 0, 100, 0),
  ('Tinta de estampado negra', 'Tinta plastisol para serigrafía',                     4, 'activo', 0, 10, 0),
  ('Tinta de estampado blanca','Tinta plastisol para serigrafía',                     4, 'activo', 0, 10, 0)
ON CONFLICT (nombre) DO NOTHING;

-- ---------------------------------------------------------------------
-- (b) PRODUCTOS FABRICADOS — 3 productos de marca propia.
--     Se crean NUEVOS (no se convierten los del seed base, para no
--     alterar su inventario ni sus pedidos históricos).
-- ---------------------------------------------------------------------
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado, origen) VALUES
  (2, 1, 'Camiseta Marathon Sports Dry-Fit Hombre', 'Producción propia Marathon Sports', 34.99, 'activo', 'fabricado'),
  (2, 1, 'Short Deportivo Marathon Training',       'Producción propia Marathon Sports', 29.99, 'activo', 'fabricado'),
  (3, 1, 'Gorra Marathon Sports Clásica',           'Producción propia Marathon Sports', 19.99, 'activo', 'fabricado')
ON CONFLICT (nombre) DO NOTHING;

-- ---------------------------------------------------------------------
-- (b.2) BOM — lista de materiales de cada producto fabricado.
--       El trigger trg_validar_bom_producto_fabricado exige origen='fabricado'.
-- ---------------------------------------------------------------------
INSERT INTO lista_materiales (id_producto, id_materia_prima, cantidad_necesaria, estado)
SELECT p.id_producto, mp.id_materia_prima, v.cant, 'activo'
FROM (VALUES
    ('Camiseta Marathon Sports Dry-Fit Hombre', 'Tela dry-fit poliéster',    1.200),
    ('Camiseta Marathon Sports Dry-Fit Hombre', 'Hilo poliéster industrial', 0.020),
    ('Camiseta Marathon Sports Dry-Fit Hombre', 'Etiqueta bordada Marathon', 1.000),
    ('Camiseta Marathon Sports Dry-Fit Hombre', 'Tinta de estampado negra',  0.015),
    ('Short Deportivo Marathon Training',       'Tela dry-fit poliéster',    0.800),
    ('Short Deportivo Marathon Training',       'Cinta elástica 2cm',        0.900),
    ('Short Deportivo Marathon Training',       'Hilo poliéster industrial', 0.015),
    ('Short Deportivo Marathon Training',       'Etiqueta bordada Marathon', 1.000),
    ('Gorra Marathon Sports Clásica',           'Tela algodón jersey',       0.400),
    ('Gorra Marathon Sports Clásica',           'Hilo poliéster industrial', 0.010),
    ('Gorra Marathon Sports Clásica',           'Etiqueta bordada Marathon', 1.000),
    ('Gorra Marathon Sports Clásica',           'Botón plástico 12mm',       1.000)
  ) AS v(producto, materia, cant)
JOIN producto p       ON p.nombre = v.producto
JOIN materia_prima mp ON mp.nombre = v.materia
ON CONFLICT (id_producto, id_materia_prima) DO NOTHING;

-- =====================================================================
-- Bloque transaccional: compras, recepciones, facturas, devoluciones y
-- producción. Guard de idempotencia al inicio.
-- =====================================================================
DO $$
DECLARE
    -- usuarios demo (seed base): 1 admin, 5 compras, 6 producción, 3 bodega
    v_admin      INTEGER := 1;
    v_compras    INTEGER := 5;
    v_produccion INTEGER := 6;
    v_bodega_op  INTEGER := 3;
    v_bodega     INTEGER;
    -- materias primas
    v_mp_dryfit  INTEGER; v_mp_algodon INTEGER; v_mp_mesh   INTEGER;
    v_mp_hilo    INTEGER; v_mp_etiq    INTEGER; v_mp_cinta  INTEGER;
    v_mp_boton   INTEGER; v_mp_cierre  INTEGER; v_mp_tintan INTEGER;
    v_mp_tintab  INTEGER;
    -- productos fabricados
    v_p_camiseta INTEGER; v_p_short INTEGER;
    -- ids de trabajo
    v_oc1 INTEGER; v_oc2 INTEGER; v_oc3 INTEGER; v_oc4 INTEGER;
    v_rec INTEGER; v_det INTEGER;
    v_fc1 INTEGER; v_fc2 INTEGER; v_cxp1 INTEGER; v_cxp2 INTEGER;
    v_sd1 INTEGER; v_sd2 INTEGER; v_sd3 INTEGER; v_sdd_def INTEGER;
    v_dp INTEGER; v_op1 INTEGER; v_op2 INTEGER;
    v_inv INTEGER; v_stock_ant NUMERIC; v_stock_new NUMERIC;
    r RECORD;
BEGIN
    -- ---------------- GUARD DE IDEMPOTENCIA ----------------
    IF EXISTS (SELECT 1 FROM orden_compra WHERE observaciones LIKE 'DEMO F31%') THEN
        RAISE NOTICE 'Seed demo F31 ya aplicado previamente. No se vuelve a sembrar.';
        RETURN;
    END IF;

    SELECT id_bodega INTO v_bodega FROM bodega ORDER BY id_bodega LIMIT 1;

    SELECT id_materia_prima INTO v_mp_dryfit  FROM materia_prima WHERE nombre = 'Tela dry-fit poliéster';
    SELECT id_materia_prima INTO v_mp_algodon FROM materia_prima WHERE nombre = 'Tela algodón jersey';
    SELECT id_materia_prima INTO v_mp_mesh    FROM materia_prima WHERE nombre = 'Tela mesh transpirable';
    SELECT id_materia_prima INTO v_mp_hilo    FROM materia_prima WHERE nombre = 'Hilo poliéster industrial';
    SELECT id_materia_prima INTO v_mp_etiq    FROM materia_prima WHERE nombre = 'Etiqueta bordada Marathon';
    SELECT id_materia_prima INTO v_mp_cinta   FROM materia_prima WHERE nombre = 'Cinta elástica 2cm';
    SELECT id_materia_prima INTO v_mp_boton   FROM materia_prima WHERE nombre = 'Botón plástico 12mm';
    SELECT id_materia_prima INTO v_mp_cierre  FROM materia_prima WHERE nombre = 'Cierre metálico 20cm';
    SELECT id_materia_prima INTO v_mp_tintan  FROM materia_prima WHERE nombre = 'Tinta de estampado negra';
    SELECT id_materia_prima INTO v_mp_tintab  FROM materia_prima WHERE nombre = 'Tinta de estampado blanca';

    SELECT id_producto INTO v_p_camiseta FROM producto WHERE nombre = 'Camiseta Marathon Sports Dry-Fit Hombre';
    SELECT id_producto INTO v_p_short    FROM producto WHERE nombre = 'Short Deportivo Marathon Training';

    -- Fija el usuario para el trigger de historial de inventario
    PERFORM set_config('app.current_user_id', v_admin::text, true);

    -- =================================================================
    -- (c) ÓRDENES DE COMPRA
    -- =================================================================
    -- OC1 — recibida_completa (materiales base de camiseta)
    INSERT INTO orden_compra (id_proveedor, id_usuario_solicitante, id_usuario_aprobador,
                              estado, fecha_aprobacion, observaciones)
    VALUES (5, v_compras, v_admin, 'aprobada', CURRENT_TIMESTAMP - INTERVAL '20 days',
            'DEMO F31 - Compra de insumos para línea de camisetas')
    RETURNING id_orden_compra INTO v_oc1;

    INSERT INTO orden_compra_detalle (id_orden_compra, tipo_item, id_materia_prima, cantidad, precio_unitario) VALUES
      (v_oc1, 'materia_prima', v_mp_dryfit, 500,  4.50),
      (v_oc1, 'materia_prima', v_mp_hilo,    40, 12.00),
      (v_oc1, 'materia_prima', v_mp_etiq,  2000,  0.15),
      (v_oc1, 'materia_prima', v_mp_tintan,  20, 18.00);

    -- OC2 — recibida_completa (materiales de gorra y short)
    INSERT INTO orden_compra (id_proveedor, id_usuario_solicitante, id_usuario_aprobador,
                              estado, fecha_aprobacion, observaciones)
    VALUES (5, v_compras, v_admin, 'aprobada', CURRENT_TIMESTAMP - INTERVAL '12 days',
            'DEMO F31 - Compra de insumos para gorras y shorts')
    RETURNING id_orden_compra INTO v_oc2;

    INSERT INTO orden_compra_detalle (id_orden_compra, tipo_item, id_materia_prima, cantidad, precio_unitario) VALUES
      (v_oc2, 'materia_prima', v_mp_algodon, 300, 3.80),
      (v_oc2, 'materia_prima', v_mp_cinta,   500, 0.90),
      (v_oc2, 'materia_prima', v_mp_boton,  3000, 0.05);

    -- OC3 — aprobada, PENDIENTE de recibir
    INSERT INTO orden_compra (id_proveedor, id_usuario_solicitante, id_usuario_aprobador,
                              estado, fecha_aprobacion, observaciones)
    VALUES (5, v_compras, v_admin, 'aprobada', CURRENT_TIMESTAMP - INTERVAL '2 days',
            'DEMO F31 - Pendiente de recepción (malla y cierres)')
    RETURNING id_orden_compra INTO v_oc3;

    INSERT INTO orden_compra_detalle (id_orden_compra, tipo_item, id_materia_prima, cantidad, precio_unitario) VALUES
      (v_oc3, 'materia_prima', v_mp_mesh,   200, 5.20),
      (v_oc3, 'materia_prima', v_mp_cierre, 300, 0.75);

    -- OC4 — borrador
    INSERT INTO orden_compra (id_proveedor, id_usuario_solicitante, estado, observaciones)
    VALUES (5, v_compras, 'borrador', 'DEMO F31 - Borrador (reposición de tinta blanca)')
    RETURNING id_orden_compra INTO v_oc4;

    INSERT INTO orden_compra_detalle (id_orden_compra, tipo_item, id_materia_prima, cantidad, precio_unitario) VALUES
      (v_oc4, 'materia_prima', v_mp_tintab, 15, 18.50);

    -- =================================================================
    -- (c.2) RECEPCIONES de OC1 y OC2 (Opción A — replica el servicio)
    --   Por cada línea: detalle de recepción, acumula cantidad_recibida,
    --   recalcula costo promedio ponderado, sube stock y registra kardex.
    -- =================================================================
    FOR r IN SELECT v_oc1 AS oc, 'DEMO F31 - Recepción completa OC insumos camisetas' AS obs,
                    CURRENT_TIMESTAMP - INTERVAL '18 days' AS fecha, 'GR-2026-0101' AS guia
             UNION ALL
             SELECT v_oc2, 'DEMO F31 - Recepción completa OC gorras y shorts',
                    CURRENT_TIMESTAMP - INTERVAL '10 days', 'GR-2026-0147'
    LOOP
        INSERT INTO recepcion_mercancia (id_orden_compra, id_usuario_receptor, id_bodega,
                                         fecha_recepcion, numero_guia_remision, observaciones)
        VALUES (r.oc, v_bodega_op, v_bodega, r.fecha, r.guia, r.obs)
        RETURNING id_recepcion INTO v_rec;

        FOR v_det IN SELECT id_detalle_oc FROM orden_compra_detalle WHERE id_orden_compra = r.oc ORDER BY id_detalle_oc
        LOOP
            DECLARE
                v_cant   INTEGER;
                v_precio NUMERIC(10,2);
                v_idmp   INTEGER;
                v_costo_ant NUMERIC(12,4);
                v_costo_new NUMERIC(12,4);
            BEGIN
                SELECT cantidad, precio_unitario, id_materia_prima
                  INTO v_cant, v_precio, v_idmp
                FROM orden_compra_detalle WHERE id_detalle_oc = v_det;

                -- línea de recepción (sin defectuosos en estas dos OC)
                INSERT INTO recepcion_mercancia_detalle (id_recepcion, id_detalle_oc,
                                                         cantidad_recibida_ahora, cantidad_defectuosa, observacion)
                VALUES (v_rec, v_det, v_cant, 0, 'Recibido conforme');

                -- acumula lo recibido en la línea de la OC
                UPDATE orden_compra_detalle
                   SET cantidad_recibida = cantidad_recibida + v_cant
                 WHERE id_detalle_oc = v_det;

                -- costo promedio ponderado + stock (materia prima)
                SELECT stock_actual, costo_unitario_promedio
                  INTO v_stock_ant, v_costo_ant
                FROM materia_prima WHERE id_materia_prima = v_idmp;

                v_stock_new := v_stock_ant + v_cant;
                IF v_stock_new > 0 THEN
                    v_costo_new := ROUND(((v_stock_ant * v_costo_ant) + (v_cant * v_precio)) / v_stock_new, 4);
                ELSE
                    v_costo_new := v_precio;
                END IF;

                UPDATE materia_prima
                   SET stock_actual = v_stock_new,
                       costo_unitario_promedio = v_costo_new
                 WHERE id_materia_prima = v_idmp;

                -- kardex (F26)
                INSERT INTO movimiento_materia_prima (id_materia_prima, id_usuario, tipo_movimiento,
                                                      cantidad, stock_anterior, stock_nuevo,
                                                      id_recepcion, observacion, fecha)
                VALUES (v_idmp, v_bodega_op, 'entrada_compra', v_cant, v_stock_ant, v_stock_new,
                        v_rec, 'Recepcion OC #' || r.oc, r.fecha);
            END;
        END LOOP;

        -- estado de la orden: todas las líneas quedaron completas
        UPDATE orden_compra SET estado = 'recibida_completa', updated_at = CURRENT_TIMESTAMP
         WHERE id_orden_compra = r.oc;
    END LOOP;

    -- =================================================================
    -- (d) FACTURAS DE COMPRA + CUENTAS POR PAGAR + PAGOS
    --     total y saldo_pendiente son GENERATED; monto_pagado lo calcula
    --     el trigger a partir de pago_proveedor. No se escriben aquí.
    -- =================================================================
    -- Factura 1 sobre OC1 — quedará PAGADA por completo
    INSERT INTO factura_compra (id_orden_compra, id_usuario_registro, numero_factura_proveedor,
                                fecha_factura, fecha_vencimiento, subtotal, impuesto, estado)
    VALUES (v_oc1, v_compras, 'FAC-001-2026-45871',
            (CURRENT_DATE - 18), (CURRENT_DATE - 18) + 30, 3390.00, 508.50, 'pendiente')
    RETURNING id_factura_compra INTO v_fc1;

    INSERT INTO cuenta_por_pagar (id_factura_compra, id_proveedor, monto_total, fecha_vencimiento, estado)
    VALUES (v_fc1, 5, 3898.50, (CURRENT_DATE - 18) + 30, 'vigente')
    RETURNING id_cuenta_pagar INTO v_cxp1;

    -- Pago total (el trigger recalcula monto_pagado y marca pagada la CxP y la factura)
    INSERT INTO pago_proveedor (id_cuenta_pagar, id_usuario_registro, monto, fecha_pago,
                                metodo_pago, referencia, observaciones)
    VALUES (v_cxp1, v_compras, 3898.50, CURRENT_TIMESTAMP - INTERVAL '5 days',
            'transferencia', 'TRF-99120345', 'DEMO F31 - Pago total de factura');

    -- Factura 2 sobre OC2 — quedará con PAGO PARCIAL
    INSERT INTO factura_compra (id_orden_compra, id_usuario_registro, numero_factura_proveedor,
                                fecha_factura, fecha_vencimiento, subtotal, impuesto, estado)
    VALUES (v_oc2, v_compras, 'FAC-001-2026-46022',
            (CURRENT_DATE - 10), (CURRENT_DATE - 10) + 30, 1740.00, 261.00, 'pendiente')
    RETURNING id_factura_compra INTO v_fc2;

    INSERT INTO cuenta_por_pagar (id_factura_compra, id_proveedor, monto_total, fecha_vencimiento, estado)
    VALUES (v_fc2, 5, 2001.00, (CURRENT_DATE - 10) + 30, 'vigente')
    RETURNING id_cuenta_pagar INTO v_cxp2;

    INSERT INTO pago_proveedor (id_cuenta_pagar, id_usuario_registro, monto, fecha_pago,
                                metodo_pago, referencia, observaciones)
    VALUES (v_cxp2, v_compras, 800.00, CURRENT_TIMESTAMP - INTERVAL '3 days',
            'cheque', 'CHQ-004512', 'DEMO F31 - Abono parcial');

    -- =================================================================
    -- (e) DEVOLUCIONES DE CLIENTE (RMA) sobre pedidos 'entregado'
    -- =================================================================
    -- SD1 — completada con item APTO_REVENTA (sube stock del producto)
    INSERT INTO solicitud_devolucion (id_pedido, id_usuario_registro, motivo, descripcion, estado,
                                      fecha_solicitud, fecha_inspeccion, id_usuario_inspector)
    SELECT d.id_pedido, 4, 'talla_incorrecta',
           'DEMO F31 - Cliente solicita cambio por talla', 'completada',
           CURRENT_TIMESTAMP - INTERVAL '9 days', CURRENT_TIMESTAMP - INTERVAL '8 days', v_bodega_op
    FROM detalle_pedido d WHERE d.id_detalle = 1
    RETURNING id_solicitud INTO v_sd1;

    INSERT INTO solicitud_devolucion_detalle (id_solicitud, id_detalle_pedido, cantidad_devuelta,
                                              resultado_inspeccion, observacion_inspeccion)
    VALUES (v_sd1, 1, 1, 'apto_reventa', 'Producto sin uso, reingresa a inventario');

    -- El item apto_reventa reingresa al stock (replica el servicio F24)
    DECLARE
        v_prod INTEGER;
        v_stk  INTEGER;
    BEGIN
        SELECT id_producto INTO v_prod FROM detalle_pedido WHERE id_detalle = 1;
        SELECT id_inventario, stock_actual INTO v_inv, v_stk
          FROM inventario WHERE id_producto = v_prod AND id_bodega = v_bodega;
        IF v_inv IS NULL THEN
            INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo)
            VALUES (v_prod, v_bodega, 1, 0) RETURNING id_inventario INTO v_inv;
        ELSE
            UPDATE inventario SET stock_actual = stock_actual + 1 WHERE id_inventario = v_inv;
        END IF;
        INSERT INTO movimiento_inventario (id_inventario, id_usuario, tipo_movimiento, cantidad, observacion)
        VALUES (v_inv, v_bodega_op, 'entrada', 1, 'DEMO F31 - Devolución apto reventa RMA #' || v_sd1);
    END;

    -- Reembolso informativo de SD1
    INSERT INTO reembolso_cliente (id_solicitud, id_usuario_registro, monto, metodo, observaciones)
    VALUES (v_sd1, 4, 113.25, 'nota_credito', 'DEMO F31 - Nota de crédito emitida');

    -- SD2 — completada con item DEFECTUOSO (queda disponible para F25)
    INSERT INTO solicitud_devolucion (id_pedido, id_usuario_registro, motivo, descripcion, estado,
                                      fecha_solicitud, fecha_inspeccion, id_usuario_inspector)
    SELECT d.id_pedido, 4, 'producto_defectuoso',
           'DEMO F31 - Costura defectuosa reportada por el cliente', 'completada',
           CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP - INTERVAL '6 days', v_bodega_op
    FROM detalle_pedido d WHERE d.id_detalle = 10
    RETURNING id_solicitud INTO v_sd2;

    INSERT INTO solicitud_devolucion_detalle (id_solicitud, id_detalle_pedido, cantidad_devuelta,
                                              resultado_inspeccion, observacion_inspeccion)
    VALUES (v_sd2, 10, 1, 'defectuoso', 'Falla de fábrica: se devuelve al proveedor')
    RETURNING id_detalle_sd INTO v_sdd_def;

    -- SD3 — EN INSPECCIÓN (estado intermedio, sin resultado aún)
    INSERT INTO solicitud_devolucion (id_pedido, id_usuario_registro, motivo, descripcion, estado,
                                      fecha_solicitud, fecha_inspeccion, id_usuario_inspector)
    SELECT d.id_pedido, 4, 'no_esperado',
           'DEMO F31 - Cliente indica que el color no corresponde', 'en_inspeccion',
           CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '1 day', v_bodega_op
    FROM detalle_pedido d WHERE d.id_detalle = 22
    RETURNING id_solicitud INTO v_sd3;

    INSERT INTO solicitud_devolucion_detalle (id_solicitud, id_detalle_pedido, cantidad_devuelta)
    VALUES (v_sd3, 22, 1);

    -- =================================================================
    -- (f) DEVOLUCIÓN A PROVEEDOR — consume el item defectuoso de SD2
    -- =================================================================
    INSERT INTO devolucion_proveedor (id_proveedor, id_usuario_registro, fecha_devolucion,
                                      estado, tipo_resolucion, monto_reembolso, observaciones)
    VALUES (1, v_compras, CURRENT_TIMESTAMP - INTERVAL '4 days',
            'resuelta', 'reposicion', NULL, 'DEMO F31 - Proveedor acepta reposición del producto')
    RETURNING id_devolucion_prov INTO v_dp;

    INSERT INTO devolucion_proveedor_detalle (id_devolucion_prov, origen,
                                              id_solicitud_devolucion_detalle, id_producto, cantidad, motivo)
    SELECT v_dp, 'rma_cliente', v_sdd_def, dp.id_producto, 1, 'Falla de fábrica detectada en inspección'
    FROM detalle_pedido dp WHERE dp.id_detalle = 10;

    -- =================================================================
    -- (g) ÓRDENES DE PRODUCCIÓN
    --   OP1: COMPLETADA — consume materia prima (kardex), registra merma,
    --        cuesta con snapshot del costo promedio y da de alta el
    --        producto terminado en inventario.
    --   OP2: PLANIFICADA — solo consumos teóricos (snapshot 0, real NULL).
    -- =================================================================
    -- ---------- OP1: 50 camisetas, completada ----------
    INSERT INTO orden_produccion (id_producto, id_bodega_destino, id_usuario_registro, id_usuario_completa,
                                  cantidad_planificada, cantidad_producida, estado,
                                  fecha_creacion, fecha_inicio, fecha_fin,
                                  costo_mano_obra, costo_indirecto, observaciones)
    VALUES (v_p_camiseta, v_bodega, v_produccion, v_produccion,
            50, 50, 'completada',
            CURRENT_TIMESTAMP - INTERVAL '6 days', CURRENT_TIMESTAMP - INTERVAL '5 days',
            CURRENT_TIMESTAMP - INTERVAL '4 days',
            180.00, 60.00, 'DEMO F31 - Lote de camisetas dry-fit')
    RETURNING id_orden_produccion INTO v_op1;

    -- Consumos: teórico = BOM x 50. La tela real es 62 (merma +2), el resto igual.
    -- El snapshot toma el costo promedio actual de cada materia prima.
    FOR r IN
        SELECT lm.id_materia_prima,
               ROUND(lm.cantidad_necesaria * 50, 3) AS teorica,
               CASE WHEN lm.id_materia_prima = v_mp_dryfit
                    THEN ROUND(lm.cantidad_necesaria * 50, 3) + 2.000
                    ELSE ROUND(lm.cantidad_necesaria * 50, 3) END AS real_cant,
               mp.costo_unitario_promedio AS costo
        FROM lista_materiales lm
        JOIN materia_prima mp ON mp.id_materia_prima = lm.id_materia_prima
        WHERE lm.id_producto = v_p_camiseta AND lm.estado = 'activo'
        ORDER BY lm.id_materia_prima
    LOOP
        INSERT INTO orden_produccion_consumo (id_orden_produccion, id_materia_prima,
                                              cantidad_teorica, cantidad_real, costo_unitario_snapshot)
        VALUES (v_op1, r.id_materia_prima, r.teorica, r.real_cant, r.costo);

        -- Descuenta del stock lo realmente consumido y registra el kardex
        SELECT stock_actual INTO v_stock_ant FROM materia_prima WHERE id_materia_prima = r.id_materia_prima;
        v_stock_new := v_stock_ant - r.real_cant;

        UPDATE materia_prima SET stock_actual = v_stock_new WHERE id_materia_prima = r.id_materia_prima;

        INSERT INTO movimiento_materia_prima (id_materia_prima, id_usuario, tipo_movimiento,
                                              cantidad, stock_anterior, stock_nuevo,
                                              id_orden_produccion, observacion, fecha)
        VALUES (r.id_materia_prima, v_produccion, 'salida_produccion', r.real_cant,
                v_stock_ant, v_stock_new, v_op1,
                'Consumo OP #' || v_op1 || ' - Camiseta Marathon Sports Dry-Fit Hombre',
                CURRENT_TIMESTAMP - INTERVAL '5 days');
    END LOOP;

    -- costo_materia_prima: se fija con la función dedicada (respeta el trigger F29)
    PERFORM fn_set_costo_materia_prima_op(v_op1);

    -- Alta del producto terminado en inventario (50 uds)
    SELECT id_inventario INTO v_inv FROM inventario
     WHERE id_producto = v_p_camiseta AND id_bodega = v_bodega;
    IF v_inv IS NULL THEN
        INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo)
        VALUES (v_p_camiseta, v_bodega, 50, 10) RETURNING id_inventario INTO v_inv;
    ELSE
        UPDATE inventario SET stock_actual = stock_actual + 50 WHERE id_inventario = v_inv;
    END IF;

    INSERT INTO movimiento_inventario (id_inventario, id_usuario, tipo_movimiento, cantidad, observacion)
    VALUES (v_inv, v_produccion, 'entrada', 50, 'Producción OP #' || v_op1);

    -- ---------- OP2: 30 shorts, planificada ----------
    INSERT INTO orden_produccion (id_producto, id_bodega_destino, id_usuario_registro,
                                  cantidad_planificada, estado, fecha_creacion, observaciones)
    VALUES (v_p_short, v_bodega, v_produccion, 30, 'planificada',
            CURRENT_TIMESTAMP - INTERVAL '1 day', 'DEMO F31 - Lote de shorts programado')
    RETURNING id_orden_produccion INTO v_op2;

    -- Solo consumo teórico; snapshot 0 y real NULL hasta que se inicie
    INSERT INTO orden_produccion_consumo (id_orden_produccion, id_materia_prima, cantidad_teorica)
    SELECT v_op2, lm.id_materia_prima, ROUND(lm.cantidad_necesaria * 30, 3)
    FROM lista_materiales lm
    WHERE lm.id_producto = v_p_short AND lm.estado = 'activo';

    RAISE NOTICE 'Seed demo F31 aplicado: OC=% % % %, OP=% %', v_oc1, v_oc2, v_oc3, v_oc4, v_op1, v_op2;
END $$;
