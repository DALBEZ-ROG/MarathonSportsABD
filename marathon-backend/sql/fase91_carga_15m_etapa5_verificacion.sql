-- =============================================================================
-- FASE 91 · Etapa 5 — Reactivacion y verificacion
-- -----------------------------------------------------------------------------
-- Reactiva los once triggers que las etapas 1, 3 y 4 apagaron, y COMPRUEBA
-- contra pg_trigger que los 30 quedan activos: no basta con haber escrito los
-- ALTER, hay que mirar el catalogo.
--
-- Despues verifica, en este orden:
--   1. que los 30 triggers estan encendidos
--   2. que las 34 tablas objetivo llegan a 1.500.000 filas (las tres tablas de
--      LINEA quedan por encima a proposito: ver la etapa 3b)
--   3. que las 8 tablas de catalogo y seguridad NO se han tocado
--   4. que ninguna fecha generada pasa del 31/07/2026
--   5. que los totales reconstruidos cuadran con sus lineas
--
-- El esquema `carga` NO se borra: su tabla `rango` es el registro de que
-- identificadores son datos generados y cuales son datos reales previos. Es la
-- unica forma de poder deshacer esta carga mas adelante sin tocar lo de antes.
-- =============================================================================

\set ON_ERROR_STOP on
\timing on

-- -----------------------------------------------------------------------------
-- 1. Reactivacion
-- -----------------------------------------------------------------------------
ALTER TABLE usuario              ENABLE TRIGGER trg_auditoria_usuario;
ALTER TABLE producto             ENABLE TRIGGER trg_auditoria_producto;
ALTER TABLE proveedor            ENABLE TRIGGER trg_auditoria_proveedor;
ALTER TABLE cliente              ENABLE TRIGGER trg_auditoria_cliente;
ALTER TABLE cliente              ENABLE TRIGGER trg_cliente_hash_correo;
ALTER TABLE detalle_pedido       ENABLE TRIGGER trg_recalcular_total_pedido_insert;
ALTER TABLE pedido               ENABLE TRIGGER trg_proteger_total_pedido;
ALTER TABLE pedido               ENABLE TRIGGER trg_recalcular_total_por_descuento;
ALTER TABLE pedido               ENABLE TRIGGER trg_pedido_updated_at;
ALTER TABLE orden_compra_detalle ENABLE TRIGGER trg_oc_total_insert;
ALTER TABLE orden_compra         ENABLE TRIGGER trg_proteger_total_oc;
ALTER TABLE pago_proveedor       ENABLE TRIGGER trg_cxp_pagado_insert;
ALTER TABLE cuenta_por_pagar     ENABLE TRIGGER trg_proteger_monto_pagado_cxp;

COMMENT ON SCHEMA carga IS
  'Fase 91. Andamiaje de la carga masiva a 1,5M. carga.rango guarda, por tabla, '
  'el primer y ultimo identificador GENERADO: todo lo que queda por debajo de '
  'id_min son datos reales anteriores a la carga.';

-- -----------------------------------------------------------------------------
-- 2. Los 30 triggers, leidos del catalogo
-- -----------------------------------------------------------------------------
SELECT count(*) FILTER (WHERE tgenabled = 'O') AS activos,
       count(*) FILTER (WHERE tgenabled <> 'O') AS apagados,
       count(*) AS total
FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid
WHERE NOT t.tgisinternal AND c.relnamespace = 'public'::regnamespace;

SELECT c.relname AS tabla, t.tgname AS trigger_apagado
FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid
WHERE NOT t.tgisinternal AND c.relnamespace = 'public'::regnamespace
  AND t.tgenabled <> 'O';

-- -----------------------------------------------------------------------------
-- 3. Las 34 tablas objetivo: 1.500.000 o mas
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION carga.conteo(p_tabla text) RETURNS bigint
  LANGUAGE plpgsql AS $fn$
DECLARE v bigint;
BEGIN
  EXECUTE format('SELECT count(*) FROM public.%I', p_tabla) INTO v;
  RETURN v;
END $fn$;

DROP TABLE IF EXISTS carga.objetivos;
CREATE TABLE carga.objetivos (tabla text PRIMARY KEY);
INSERT INTO carga.objetivos VALUES
 ('usuario'),('proveedor'),('transportista'),('materia_prima'),('cliente'),('producto'),
 ('producto_proveedor'),('inventario'),('lista_materiales'),('transportista_cobertura'),
 ('token_revocado'),('log_accion'),('auditoria_cambios'),('orden_compra'),('pedido'),
 ('orden_produccion'),('detalle_pedido'),('orden_compra_detalle'),('reserva_stock'),
 ('comprobante_interno'),('solicitud_devolucion'),('historial_inventario'),
 ('movimiento_inventario'),('orden_produccion_consumo'),('recepcion_mercancia'),
 ('factura_compra'),('solicitud_devolucion_detalle'),('recepcion_mercancia_detalle'),
 ('cuenta_por_pagar'),('reembolso_cliente'),('devolucion_proveedor'),('pago_proveedor'),
 ('devolucion_proveedor_detalle'),('movimiento_materia_prima');

SELECT tabla, carga.conteo(tabla) AS filas,
       CASE WHEN carga.conteo(tabla) >= 1500000 THEN 'OK' ELSE 'FALTA' END AS estado
FROM carga.objetivos ORDER BY 3 DESC, 1;

SELECT count(*) AS tablas_en_objetivo
FROM carga.objetivos WHERE carga.conteo(tabla) >= 1500000;

-- -----------------------------------------------------------------------------
-- 4. Los catalogos, intactos
-- -----------------------------------------------------------------------------
SELECT 'rol' AS catalogo, count(*) FROM rol
UNION ALL SELECT 'permiso',       count(*) FROM permiso
UNION ALL SELECT 'rol_permiso',   count(*) FROM rol_permiso
UNION ALL SELECT 'usuario_rol',   count(*) FROM usuario_rol
UNION ALL SELECT 'unidad_medida', count(*) FROM unidad_medida
UNION ALL SELECT 'categoria',     count(*) FROM categoria
UNION ALL SELECT 'ciudad',        count(*) FROM ciudad
UNION ALL SELECT 'bodega',        count(*) FROM bodega
ORDER BY 1;

-- -----------------------------------------------------------------------------
-- 5. Ninguna fecha generada pasa del 31/07/2026
--    Se recorre TODA columna de tipo fecha de las 34 tablas y se mira su maximo
--    solo en el tramo generado (id >= id_min de carga.rango, y la tabla entera
--    cuando no hay rango anotado porque se cargo por antijoin).
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS carga.fechas;
CREATE TABLE carga.fechas (tabla text, columna text, maximo timestamp);

DO $$
DECLARE r record; v timestamp; v_pk text; v_desde bigint; v_filtro text;
BEGIN
  FOR r IN
    SELECT c.relname AS tabla, a.attname AS col
    FROM pg_attribute a
    JOIN pg_class c ON c.oid = a.attrelid
    JOIN carga.objetivos o ON o.tabla = c.relname
    WHERE c.relnamespace = 'public'::regnamespace
      AND a.attnum > 0 AND NOT a.attisdropped
      AND format_type(a.atttypid, a.atttypmod) IN ('timestamp without time zone','date')
  LOOP
    SELECT a.attname INTO v_pk
    FROM pg_constraint k
    JOIN pg_attribute a ON a.attrelid = k.conrelid AND a.attnum = k.conkey[1]
    WHERE k.contype = 'p' AND k.conrelid = ('public.' || quote_ident(r.tabla))::regclass;

    SELECT id_min INTO v_desde FROM carga.rango WHERE tabla = r.tabla;

    IF v_desde IS NOT NULL AND v_pk IS NOT NULL THEN
      v_filtro := format('WHERE %I >= %s', v_pk, v_desde);
    ELSE
      v_filtro := '';
    END IF;

    EXECUTE format('SELECT max(%I)::timestamp FROM public.%I %s', r.col, r.tabla, v_filtro) INTO v;
    INSERT INTO carga.fechas VALUES (r.tabla, r.col, v);
  END LOOP;
END $$;

SELECT count(*) AS columnas_de_fecha_revisadas FROM carga.fechas;

SELECT tabla, columna, maximo AS pasa_de_julio_2026
FROM carga.fechas
WHERE maximo > timestamp '2026-07-31 23:59:59'
ORDER BY maximo DESC;

SELECT max(maximo) AS fecha_mas_alta_de_toda_la_carga FROM carga.fechas;

-- -----------------------------------------------------------------------------
-- 6. Los totales reconstruidos cuadran
-- -----------------------------------------------------------------------------
SELECT count(*) AS pedidos_con_total_descuadrado
FROM pedido p
JOIN (SELECT id_pedido, sum(subtotal) AS suma FROM detalle_pedido GROUP BY id_pedido) d
  ON d.id_pedido = p.id_pedido
WHERE p.id_pedido >= (SELECT id_min FROM carga.rango WHERE tabla = 'pedido')
  AND p.total <> GREATEST(d.suma - p.descuento, 0);

SELECT count(*) AS ordenes_con_total_descuadrado
FROM orden_compra o
JOIN (SELECT id_orden_compra, sum(subtotal) AS suma FROM orden_compra_detalle GROUP BY id_orden_compra) d
  ON d.id_orden_compra = o.id_orden_compra
WHERE o.id_orden_compra >= (SELECT id_min FROM carga.rango WHERE tabla = 'orden_compra')
  AND o.total <> d.suma;

SELECT count(*) AS comprobantes_que_no_cuadran_con_su_pedido
FROM comprobante_interno ci JOIN pedido p ON p.id_pedido = ci.id_pedido
WHERE ci.total <> p.total;

SELECT count(*) AS cuentas_pagadas_de_mas
FROM cuenta_por_pagar WHERE monto_pagado > monto_total;

-- -----------------------------------------------------------------------------
-- 7. Tamano final
-- -----------------------------------------------------------------------------
SELECT pg_size_pretty(pg_database_size('mod_venta_inve')) AS tamano_base;

SELECT sum(carga.conteo(tabla)) AS filas_en_las_34_tablas FROM carga.objetivos;
