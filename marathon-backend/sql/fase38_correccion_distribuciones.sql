-- ============================================================================
-- FASE 38 — CORRECCION DE DISTRIBUCIONES
-- ----------------------------------------------------------------------------
-- POR QUE EXISTE ESTE SCRIPT
--   La primera pasada de fase38_poblado_masivo.sql cargo el volumen correcto y
--   paso todas las verificaciones de integridad, pero produjo datos PLANOS en
--   varias columnas:
--
--       pedido.fecha_pedido            5 fechas distintas en 165.000 filas
--       pedido.estado                  solo 'entregado' y 'pendiente'
--       orden_compra.fecha_orden       2 fechas distintas
--       historial_inventario.motivo    1 valor
--       movimiento_inventario.tipo     2 valores
--       inventario.fecha_actualizacion 1 fecha
--
--   CAUSA: un CROSS JOIN LATERAL cuya subconsulta no se correlaciona con la fila
--   externa —del tipo `CROSS JOIN LATERAL (SELECT random() AS x) r`— es una
--   subconsulta NO correlacionada, y PostgreSQL la evalua UNA SOLA VEZ por
--   sentencia. Cada lote de 50.000 filas recibia el mismo valor. Los bloques que
--   pusieron las expresiones volatiles en el SELECT de una subconsulta sobre
--   `generate_series` (cliente, y las fechas de log_accion, movimiento_inventario
--   e historial_inventario) salieron bien: ahi si se evalua por fila.
--
--   El script de carga ya esta corregido para futuras ejecuciones. Este script
--   arregla los datos que la primera pasada dejo, sin borrar y recargar el
--   millon de filas.
--
-- COHERENCIA QUE SE PRESERVA
--   Al redistribuir pedido.estado se respetan dos hechos ya escritos:
--     - los 30.000 pedidos con comprobante emitido deben quedar en un estado
--       facturable (entregado / enviado / procesado);
--     - los pedidos referenciados por un movimiento de salida deben quedar
--       despachados.
--   Sin eso quedarian comprobantes de pedidos 'pendiente', que es un dato
--   incoherente aunque ninguna restriccion lo impida.
--
--     psql -U postgres -d mod_venta_inve -f fase38_correccion_distribuciones.sql
-- ============================================================================

\set ON_ERROR_STOP on
\timing on

\echo ''
\echo '=== FASE 38 - CORRECCION DE DISTRIBUCIONES ==='

DO $$
BEGIN
    IF current_user <> 'postgres' THEN
        RAISE EXCEPTION 'Ejecutar como postgres (actual: %)', current_user;
    END IF;
END $$;

-- ============================================================================
-- 1. Triggers de pedido: fuera durante el UPDATE masivo
-- ----------------------------------------------------------------------------
-- trg_recalcular_total_por_descuento es BEFORE UPDATE FOR EACH ROW y recalcula
-- NEW.total en CUALQUIER update de pedido, aunque no se toque el total: con
-- 165.000 filas serian 165.000 subconsultas de agregacion para llegar al mismo
-- valor que ya esta guardado.
-- ============================================================================
ALTER TABLE pedido DISABLE TRIGGER trg_proteger_total_pedido;
ALTER TABLE pedido DISABLE TRIGGER trg_recalcular_total_por_descuento;

-- ============================================================================
-- 2. pedido.fecha_pedido y created_at -> 24 meses de dispersion real
-- ============================================================================
\echo '--- 2. Redistribuyendo fechas de pedido ---'
UPDATE pedido p
SET fecha_pedido = f.nueva,
    created_at   = f.nueva
FROM (
    SELECT id_pedido,
           now() - (power(random(), 1.4) * interval '730 days') AS nueva
    FROM pedido
) f
WHERE f.id_pedido = p.id_pedido;

-- ============================================================================
-- 3. pedido.estado -> 70 / 10 / 8 / 8 / 4 conservando la coherencia
-- ============================================================================
\echo '--- 3. Redistribuyendo estado de pedido ---'

-- 3a. Pedidos con comprobante o con movimiento de salida: estados despachables
UPDATE pedido p
SET estado = CASE WHEN r.x < 0.75 THEN 'entregado'
                  WHEN r.x < 0.90 THEN 'enviado'
                  ELSE 'procesado' END
FROM (SELECT id_pedido, random() AS x FROM pedido) r
WHERE r.id_pedido = p.id_pedido
  AND (EXISTS (SELECT 1 FROM comprobante_interno c WHERE c.id_pedido = p.id_pedido)
       OR EXISTS (SELECT 1 FROM movimiento_inventario m WHERE m.id_pedido = p.id_pedido));

-- 3b. El resto: reparto que lleva el global a ~70/10/8/8/4
UPDATE pedido p
SET estado = CASE WHEN r.x < 0.689 THEN 'entregado'
                  WHEN r.x < 0.778 THEN 'enviado'
                  WHEN r.x < 0.854 THEN 'procesado'
                  WHEN r.x < 0.952 THEN 'pendiente'
                  ELSE 'anulado' END
FROM (SELECT id_pedido, random() AS x FROM pedido) r
WHERE r.id_pedido = p.id_pedido
  AND NOT EXISTS (SELECT 1 FROM comprobante_interno c WHERE c.id_pedido = p.id_pedido)
  AND NOT EXISTS (SELECT 1 FROM movimiento_inventario m WHERE m.id_pedido = p.id_pedido);

-- ============================================================================
-- 4. comprobante_interno: la fecha de emision sigue a la del pedido
-- ============================================================================
\echo '--- 4. Realineando fechas de comprobante ---'
UPDATE comprobante_interno c
SET fecha_emision = p.fecha_pedido + interval '1 hour',
    created_at    = p.fecha_pedido + interval '1 hour'
FROM pedido p
WHERE p.id_pedido = c.id_pedido;

-- ============================================================================
-- 5. Reactivar los triggers de pedido
-- ============================================================================
ALTER TABLE pedido ENABLE TRIGGER trg_proteger_total_pedido;
ALTER TABLE pedido ENABLE TRIGGER trg_recalcular_total_por_descuento;

-- ============================================================================
-- 6. movimiento_inventario.tipo_movimiento
-- ----------------------------------------------------------------------------
-- Los dos CHECK de traslado obligan a tratar por separado las filas que tienen
-- destino: una fila con id_inventario_destino solo puede ser 'traslado', y una
-- sin destino no puede serlo.
-- ============================================================================
\echo '--- 6. Redistribuyendo tipo de movimiento ---'
UPDATE movimiento_inventario m
SET tipo_movimiento = CASE WHEN r.x < 0.45 THEN 'salida'
                           WHEN r.x < 0.82 THEN 'entrada'
                           ELSE 'ajuste' END
FROM (SELECT id_movimiento, random() AS x FROM movimiento_inventario) r
WHERE r.id_movimiento = m.id_movimiento
  AND m.id_inventario_destino IS NULL;

UPDATE movimiento_inventario SET tipo_movimiento = 'traslado'
WHERE id_inventario_destino IS NOT NULL AND tipo_movimiento <> 'traslado';

-- Un movimiento de salida referido a un pedido debe seguir siendo salida
UPDATE movimiento_inventario SET tipo_movimiento = 'salida'
WHERE id_pedido IS NOT NULL AND id_inventario_destino IS NULL AND tipo_movimiento <> 'salida';

-- ============================================================================
-- 7. historial_inventario.motivo -> los 5 valores del CHECK
-- ----------------------------------------------------------------------------
-- Se respetan las filas que produjo el trigger real: esas deben conservar
-- 'actualizacion_stock', que es lo unico que el trigger escribe. Se distinguen
-- por ser las que tienen id_usuario = 1 y fecha del dia de la carga.
-- ============================================================================
\echo '--- 7. Redistribuyendo motivo del historial ---'
UPDATE historial_inventario h
SET motivo = CASE WHEN r.x < 0.70 THEN 'actualizacion_stock'
                  WHEN r.x < 0.85 THEN 'ajuste_manual'
                  WHEN r.x < 0.93 THEN 'traslado'
                  WHEN r.x < 0.98 THEN 'correccion'
                  ELSE 'importacion' END
FROM (SELECT id_historial, random() AS x FROM historial_inventario) r
WHERE r.id_historial = h.id_historial
  AND h.fecha < current_date;    -- no toca lo que genero el trigger hoy

-- ============================================================================
-- 8. orden_compra: fecha y estado
-- ============================================================================
\echo '--- 8. Redistribuyendo ordenes de compra ---'
ALTER TABLE orden_compra DISABLE TRIGGER trg_proteger_total_oc;

UPDATE orden_compra oc
SET fecha_orden = r.nueva,
    created_at  = r.nueva,
    estado      = CASE WHEN r.x < 0.45 THEN 'recibida_completa'
                       WHEN r.x < 0.60 THEN 'recibida_parcial'
                       WHEN r.x < 0.78 THEN 'aprobada'
                       WHEN r.x < 0.88 THEN 'pendiente_aprobacion'
                       WHEN r.x < 0.95 THEN 'borrador'
                       WHEN r.x < 0.98 THEN 'cancelada'
                       ELSE 'rechazada' END,
    fecha_aprobacion = CASE WHEN r.x < 0.78 THEN r.nueva + interval '2 days' ELSE NULL END,
    id_usuario_aprobador = CASE WHEN r.x < 0.78 THEN 1 ELSE NULL END
FROM (SELECT id_orden_compra,
             random() AS x,
             now() - (power(random(),1.4) * interval '730 days') AS nueva
      FROM orden_compra) r
WHERE r.id_orden_compra = oc.id_orden_compra;

ALTER TABLE orden_compra ENABLE TRIGGER trg_proteger_total_oc;

-- ============================================================================
-- 9. inventario.fecha_actualizacion
-- ----------------------------------------------------------------------------
-- La aplasto el UPDATE del paso 9 de la carga (el que generaba historial por el
-- trigger real, que ponia fecha_actualizacion = now() en las 2.000 filas).
-- El trigger de historial se DESACTIVA aqui a proposito: esta correccion es
-- cosmetica sobre la fecha y no debe generar 2.000 entradas de auditoria nuevas
-- que dirian que el stock cambio cuando no cambio.
-- ============================================================================
\echo '--- 9. Redistribuyendo fecha_actualizacion de inventario ---'
ALTER TABLE inventario DISABLE TRIGGER trg_historial_inventario;

UPDATE inventario i
SET fecha_actualizacion = r.nueva
FROM (SELECT id_inventario,
             now() - (power(random(),1.4) * interval '730 days') AS nueva
      FROM inventario) r
WHERE r.id_inventario = i.id_inventario;

ALTER TABLE inventario ENABLE TRIGGER trg_historial_inventario;

-- ============================================================================
-- 10. movimiento_inventario: el tipo arrastra sus claves foraneas
-- ----------------------------------------------------------------------------
-- El paso 6 dejaba el 100 % de las filas en 'salida'. Motivo: en la carga
-- original id_pedido solo se asignaba a los movimientos de tipo 'salida', y
-- como el tipo era constante por lote, casi todas las filas acabaron con
-- id_pedido; despues la regla "si tiene pedido, es salida" las arrastro a todas.
-- La redistribucion correcta tiene que mover tambien las FK: una salida va
-- contra un pedido, una entrada contra un proveedor, un ajuste contra ninguno.
-- ============================================================================
\echo '--- 10. Redistribuyendo tipo de movimiento con sus FK ---'
WITH s AS (
  SELECT id_movimiento, random() AS x,
         row_number() OVER (ORDER BY id_movimiento) AS rn
  FROM movimiento_inventario
  WHERE id_inventario_destino IS NULL
)
UPDATE movimiento_inventario m
SET tipo_movimiento = CASE WHEN s.x < 0.45 THEN 'salida'
                           WHEN s.x < 0.82 THEN 'entrada'
                           ELSE 'ajuste' END,
    id_pedido    = CASE WHEN s.x < 0.45 THEN m.id_pedido ELSE NULL END,
    id_proveedor = CASE WHEN s.x >= 0.45 AND s.x < 0.82 THEN 1 + (s.rn % 6) ELSE NULL END
FROM s
WHERE s.id_movimiento = m.id_movimiento;

-- Un 4 % pasa a traslado con destino valido y distinto del origen: sin esto los
-- CHECK chk_traslado_requiere_destino y chk_traslado_origen_distinto_destino
-- quedan sin ejercitar por los datos.
WITH inv AS (SELECT array_agg(id_inventario ORDER BY id_inventario) AS a FROM inventario),
     s AS (
       SELECT m.id_movimiento,
              (SELECT a FROM inv) AS arr,
              (random() * (array_length((SELECT a FROM inv),1) - 1))::int AS k
       FROM movimiento_inventario m
       WHERE m.tipo_movimiento = 'ajuste' AND random() < 0.23
     )
UPDATE movimiento_inventario m
SET tipo_movimiento = 'traslado',
    id_inventario_destino = CASE
        WHEN s.arr[1 + s.k] = m.id_inventario
          THEN s.arr[1 + ((s.k + 1) % array_length(s.arr,1))]
        ELSE s.arr[1 + s.k] END,
    id_pedido = NULL, id_proveedor = NULL
FROM s
WHERE s.id_movimiento = m.id_movimiento;

-- ============================================================================
-- 11. log_accion: modulo y accion
-- ----------------------------------------------------------------------------
-- Mismo defecto del LATERAL: los 200.000 registros salieron repartidos en tres
-- modulos a partes casi iguales. Se restaura la proporcion relativa que tenian
-- los 131 registros originales.
-- ============================================================================
\echo '--- 11. Redistribuyendo modulo y accion de log_accion ---'
WITH s AS (SELECT id_log, random() AS x, random() AS y FROM log_accion)
UPDATE log_accion l
SET modulo = CASE WHEN s.x < 0.626 THEN 'auth'
                  WHEN s.x < 0.794 THEN 'produccion'
                  WHEN s.x < 0.878 THEN 'compras'
                  WHEN s.x < 0.931 THEN 'devoluciones'
                  WHEN s.x < 0.954 THEN 'devoluciones_proveedor'
                  WHEN s.x < 0.977 THEN 'pedidos'
                  WHEN s.x < 0.985 THEN 'respaldos'
                  WHEN s.x < 0.992 THEN 'comprobantes'
                  ELSE 'empaque' END,
    accion = CASE WHEN s.x < 0.626 THEN 'login'
                  WHEN s.x < 0.794 THEN (ARRAY['crear','iniciar','completar'])[1+(s.y*2)::int]
                  WHEN s.x < 0.878 THEN (ARRAY['crear','cambio_estado','factura_crear','pago_registrar','recepcion'])[1+(s.y*4)::int]
                  WHEN s.x < 0.931 THEN (ARRAY['crear','iniciar_inspeccion','inspeccionar'])[1+(s.y*2)::int]
                  WHEN s.x < 0.954 THEN (ARRAY['crear','cambio_estado','resolver'])[1+(s.y*2)::int]
                  WHEN s.x < 0.977 THEN (ARRAY['crear','cambio_estado'])[1+(s.y*1)::int]
                  WHEN s.x < 0.985 THEN 'prueba_rto'
                  WHEN s.x < 0.992 THEN 'generar'
                  ELSE 'confirmar' END
FROM s WHERE s.id_log = l.id_log;

-- ============================================================================
-- 12. Verificacion: ningun trigger apagado + el invariante sigue en pie
-- ============================================================================
\echo ''
\echo '--- 10. Verificacion ---'
DO $$
DECLARE
    v_apagados text;
    v_disc     bigint;
BEGIN
    SELECT string_agg(c.relname||'.'||t.tgname, ', ') INTO v_apagados
    FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
    JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE NOT t.tgisinternal AND n.nspname='public' AND t.tgenabled<>'O';
    IF v_apagados IS NOT NULL THEN
        RAISE EXCEPTION 'Quedaron triggers apagados: %', v_apagados;
    END IF;
    RAISE NOTICE 'Todos los triggers en tgenabled = O';

    SELECT count(*) INTO v_disc
    FROM pedido p
    LEFT JOIN (SELECT id_pedido, SUM(subtotal) AS suma
               FROM detalle_pedido GROUP BY id_pedido) d ON d.id_pedido = p.id_pedido
    WHERE p.total <> GREATEST(COALESCE(d.suma,0) - p.descuento, 0);
    IF v_disc > 0 THEN
        RAISE EXCEPTION 'La correccion rompio el invariante financiero: % pedidos', v_disc;
    END IF;
    RAISE NOTICE 'Invariante financiero intacto tras la correccion';
END $$;

ANALYZE;

\echo ''
\echo '=== CORRECCION COMPLETADA ==='
