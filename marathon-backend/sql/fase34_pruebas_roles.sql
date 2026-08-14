-- ============================================================================
-- FASE 34 — PRUEBAS DE ACCESO AUTORIZADO Y DENEGADO POR ROL
-- ----------------------------------------------------------------------------
-- No modifica datos: cada intento corre en una subtransaccion que SIEMPRE se
-- revierte, incluso cuando la operacion tiene exito.
--
-- METODO: SET LOCAL ROLE. Al cambiar de rol desde una sesion de superusuario,
-- PostgreSQL evalua los privilegios con el rol asumido y NO hereda la condicion
-- de superusuario, asi que la prueba es real. Se usa esto en lugar de conectarse
-- con cada usuario para no necesitar sus contrasenas.
--
-- TRES RESULTADOS POSIBLES, porque importa DONDE se detiene cada intento:
--   PERMITIDO             la sentencia se ejecuto
--   DENEGADO_PRIVILEGIO   la corto el sistema de privilegios (SQLSTATE 42501)
--   DENEGADO_REGLA_BD     la corto una regla de la base: columna GENERATED
--                         (SQLSTATE 428C9) o un trigger de proteccion (P0001)
--
-- La distincion no es cosmetica: un mismo ataque puede estar cubierto por una
-- capa y no por la otra, y conviene saber por cual. Cualquier otro error se
-- reporta como ERROR_OTRO para que un test mal escrito no pase por bueno.
-- ============================================================================

\set ON_ERROR_STOP on
\echo '=== PRUEBAS DE PRIVILEGIOS POR ROL ==='

-- IDs reales resueltos como superusuario ANTES de asumir cualquier rol, para
-- que las sentencias de prueba no dependan de leer tablas que el rol no ve.
SELECT (SELECT min(id_usuario)      FROM usuario)        AS v_usuario,
       (SELECT min(id_cliente)      FROM cliente)        AS v_cliente,
       (SELECT min(id_ciudad)       FROM ciudad)         AS v_ciudad,
       (SELECT min(id_proveedor)    FROM proveedor)      AS v_proveedor,
       (SELECT min(id_bodega)       FROM bodega)         AS v_bodega,
       -- Cuenta por pagar que AUN tiene saldo: pagar una ya saldada viola el
       -- CHECK chk_cxp_montos, que es correcto y no lo que se quiere probar.
       (SELECT min(id_cuenta_pagar) FROM cuenta_por_pagar WHERE saldo_pendiente > 0) AS v_cxp_saldo,
       (SELECT min(id_producto)     FROM producto WHERE origen='fabricado') AS v_prod_fab,
       (SELECT min(id_materia_prima) FROM materia_prima) AS v_materia,
       (SELECT min(id_inventario)   FROM inventario)     AS v_inventario,
       (SELECT min(id_detalle)      FROM detalle_pedido) AS v_detalle,
       (SELECT min(id_pedido)       FROM pedido)         AS v_pedido,
       (SELECT min(id_orden_compra) FROM orden_compra)   AS v_oc,
       (SELECT min(id_cuenta_pagar) FROM cuenta_por_pagar) AS v_cxp,
       (SELECT min(id_factura_compra) FROM factura_compra) AS v_factura,
       (SELECT min(id_consumo)      FROM orden_produccion_consumo) AS v_consumo,
       (SELECT min(id_orden_produccion) FROM orden_produccion) AS v_op
\gset

DROP TABLE IF EXISTS pg_temp.resultado_pruebas;
CREATE TEMP TABLE resultado_pruebas (
    n           serial,
    rol         text,
    descripcion text,
    esperado    text,
    obtenido    text,
    veredicto   text,
    detalle     text
);

CREATE OR REPLACE FUNCTION pg_temp.probar(
    p_rol         text,
    p_descripcion text,
    p_sql         text,
    p_esperado    text
) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    v_obtenido text;
    v_detalle  text := '';
BEGIN
    BEGIN
        EXECUTE format('SET LOCAL ROLE %I', p_rol);
        BEGIN
            EXECUTE p_sql;
            RAISE EXCEPTION 'CENTINELA_OK';   -- fuerza el rollback de la subtransaccion
        EXCEPTION
            WHEN insufficient_privilege THEN            -- 42501
                v_obtenido := 'DENEGADO_PRIVILEGIO';
                v_detalle  := SQLERRM;
            WHEN generated_always THEN                  -- 428C9
                v_obtenido := 'DENEGADO_REGLA_BD';
                v_detalle  := 'columna GENERATED: ' || SQLERRM;
            WHEN raise_exception THEN                   -- P0001
                IF SQLERRM = 'CENTINELA_OK' THEN
                    v_obtenido := 'PERMITIDO';
                ELSE
                    v_obtenido := 'DENEGADO_REGLA_BD';
                    v_detalle  := 'trigger: ' || SQLERRM;
                END IF;
            WHEN OTHERS THEN
                v_obtenido := 'ERROR_OTRO';
                v_detalle  := SQLSTATE || ' ' || SQLERRM;
        END;
        RESET ROLE;
    EXCEPTION WHEN OTHERS THEN
        RESET ROLE;
        v_obtenido := COALESCE(v_obtenido, 'ERROR_PRUEBA');
        v_detalle  := COALESCE(NULLIF(v_detalle, ''), SQLERRM);
    END;

    INSERT INTO resultado_pruebas (rol, descripcion, esperado, obtenido, veredicto, detalle)
    VALUES (p_rol, p_descripcion, p_esperado, v_obtenido,
            CASE WHEN v_obtenido = p_esperado THEN 'PASA' ELSE 'FALLA' END,
            left(v_detalle, 100));
END $$;

-- ============================================================================
-- rol_supervisor  (Supervisor E-Commerce) — lee TODO, escribe NADA
-- ============================================================================
SELECT pg_temp.probar('rol_supervisor', 'Leer pedidos',
    'SELECT count(*) FROM pedido', 'PERMITIDO');
SELECT pg_temp.probar('rol_supervisor', 'Leer inventario',
    'SELECT count(*) FROM inventario', 'PERMITIDO');
SELECT pg_temp.probar('rol_supervisor', 'Leer compras (F21+)',
    'SELECT count(*) FROM orden_compra', 'PERMITIDO');
SELECT pg_temp.probar('rol_supervisor', 'Leer costeo de produccion (F29)',
    'SELECT count(*) FROM orden_produccion_consumo', 'PERMITIDO');
SELECT pg_temp.probar('rol_supervisor', 'Leer la tabla de usuarios',
    'SELECT count(*) FROM usuario', 'PERMITIDO');
SELECT pg_temp.probar('rol_supervisor', 'Insertar un cliente',
    format('INSERT INTO cliente (nombre,apellido,id_ciudad) VALUES (''X'',''Y'',%s)', :v_ciudad), 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_supervisor', 'Anular un pedido',
    'UPDATE pedido SET estado=''anulado'' WHERE id_pedido=' || :v_pedido, 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_supervisor', 'Borrar un pedido',
    'DELETE FROM pedido WHERE id_pedido=' || :v_pedido, 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_supervisor', 'Modificar stock',
    'UPDATE inventario SET stock_actual=0 WHERE id_inventario=' || :v_inventario, 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_supervisor', 'REQ 3.2 - nextval() sobre una secuencia',
    'SELECT nextval(''pedido_id_pedido_seq'')', 'DENEGADO_PRIVILEGIO');

-- ============================================================================
-- rol_operador_bodega  (Operador de Bodega)
-- ============================================================================
SELECT pg_temp.probar('rol_operador_bodega', 'Leer inventario',
    'SELECT count(*) FROM inventario', 'PERMITIDO');
SELECT pg_temp.probar('rol_operador_bodega', 'REQ 3.3 - Ajustar stock_actual',
    'UPDATE inventario SET stock_actual=stock_actual WHERE id_inventario=' || :v_inventario, 'PERMITIDO');
SELECT pg_temp.probar('rol_operador_bodega', 'REQ 3.3 - Reasignar id_producto',
    'UPDATE inventario SET id_producto=id_producto WHERE id_inventario=' || :v_inventario, 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_operador_bodega', 'REQ 3.3 - Reasignar id_bodega',
    'UPDATE inventario SET id_bodega=id_bodega WHERE id_inventario=' || :v_inventario, 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_operador_bodega', 'REQ 3.3 - Cambiar stock_minimo',
    'UPDATE inventario SET stock_minimo=99 WHERE id_inventario=' || :v_inventario, 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_operador_bodega', 'Marcar picking en un detalle',
    'UPDATE detalle_pedido SET picking_completado=true WHERE id_detalle=' || :v_detalle, 'PERMITIDO');
SELECT pg_temp.probar('rol_operador_bodega', 'Cambiar el precio de un detalle',
    'UPDATE detalle_pedido SET precio_unitario=1 WHERE id_detalle=' || :v_detalle, 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_operador_bodega', 'Registrar empaque (numero_hu)',
    'UPDATE pedido SET numero_hu=''HU-TEST'' WHERE id_pedido=' || :v_pedido, 'PERMITIDO');
SELECT pg_temp.probar('rol_operador_bodega', 'Falsear el descuento de un pedido',
    'UPDATE pedido SET descuento=999 WHERE id_pedido=' || :v_pedido, 'DENEGADO_PRIVILEGIO');
-- Tiene UPDATE(total) por el trigger de recalculo, pero el protector lo frena
SELECT pg_temp.probar('rol_operador_bodega', 'Falsear pedido.total (frena el trigger, no el privilegio)',
    'UPDATE pedido SET total=99999 WHERE id_pedido=' || :v_pedido, 'DENEGADO_REGLA_BD');
SELECT pg_temp.probar('rol_operador_bodega', 'Crear un usuario del sistema',
    'INSERT INTO usuario (nombre,apellido,correo,password,estado,created_at) VALUES (''a'',''b'',''z@z.com'',repeat(''x'',60),''activo'',now())', 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_operador_bodega', 'Leer la tabla de usuarios',
    'SELECT count(*) FROM usuario', 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_operador_bodega', 'Aprobar una orden de compra',
    'UPDATE orden_compra SET estado=''aprobada'' WHERE id_orden_compra=' || :v_oc, 'DENEGADO_PRIVILEGIO');

-- ============================================================================
-- rol_operador_pedidos  (Operador de Pedidos)
-- ============================================================================
SELECT pg_temp.probar('rol_operador_pedidos', 'Dar de alta un cliente',
    format('INSERT INTO cliente (nombre,apellido,id_ciudad,estado,created_at) VALUES (''Test'',''Test'',%s,''activo'',now())', :v_ciudad), 'PERMITIDO');
SELECT pg_temp.probar('rol_operador_pedidos', 'Crear un pedido',
    format('INSERT INTO pedido (id_cliente,id_usuario,fecha_pedido,estado) VALUES (%s,%s,now(),''pendiente'')', :v_cliente, :v_usuario), 'PERMITIDO');
SELECT pg_temp.probar('rol_operador_pedidos', 'Agregar una linea al pedido (dispara el trigger de total)',
    format('INSERT INTO detalle_pedido (id_pedido,id_producto,cantidad,precio_unitario) VALUES (%s,(SELECT min(id_producto) FROM producto),1,10)', :v_pedido), 'PERMITIDO');
SELECT pg_temp.probar('rol_operador_pedidos', 'Falsear pedido.total (frena el trigger)',
    'UPDATE pedido SET total=99999 WHERE id_pedido=' || :v_pedido, 'DENEGADO_REGLA_BD');
SELECT pg_temp.probar('rol_operador_pedidos', 'Escribir subtotal (columna GENERATED)',
    'UPDATE detalle_pedido SET subtotal=1 WHERE id_detalle=' || :v_detalle, 'DENEGADO_REGLA_BD');
SELECT pg_temp.probar('rol_operador_pedidos', 'Modificar stock de inventario',
    'UPDATE inventario SET stock_actual=0 WHERE id_inventario=' || :v_inventario, 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_operador_pedidos', 'Crear un producto del catalogo',
    'INSERT INTO producto (nombre,precio,estado) VALUES (''p'',1,''activo'')', 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_operador_pedidos', 'Leer facturas de compra',
    'SELECT count(*) FROM factura_compra', 'DENEGADO_PRIVILEGIO');

-- ============================================================================
-- rol_encargado_compras  (Encargado de Compras)
-- ============================================================================
SELECT pg_temp.probar('rol_encargado_compras', 'Crear una orden de compra',
    format('INSERT INTO orden_compra (id_proveedor,id_usuario_solicitante,fecha_orden,estado) VALUES (%s,%s,now(),''borrador'')', :v_proveedor, :v_usuario), 'PERMITIDO');
SELECT pg_temp.probar('rol_encargado_compras', 'Agregar linea a la orden (dispara el trigger de total)',
    format('INSERT INTO orden_compra_detalle (id_orden_compra,tipo_item,id_materia_prima,cantidad,precio_unitario) VALUES (%s,''materia_prima'',%s,1,10)', :v_oc, :v_materia), 'PERMITIDO');
SELECT pg_temp.probar('rol_encargado_compras', 'Enviar la orden a aprobacion',
    'UPDATE orden_compra SET estado=''pendiente_aprobacion'' WHERE id_orden_compra=' || :v_oc, 'PERMITIDO');
SELECT pg_temp.probar('rol_encargado_compras', 'SEP.FUNCIONES - Firmar la aprobacion',
    format('UPDATE orden_compra SET id_usuario_aprobador=%s WHERE id_orden_compra=%s', :v_usuario, :v_oc), 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_encargado_compras', 'SEP.FUNCIONES - Fechar la aprobacion',
    'UPDATE orden_compra SET fecha_aprobacion=now() WHERE id_orden_compra=' || :v_oc, 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_encargado_compras', 'Falsear orden_compra.total (frena el trigger)',
    'UPDATE orden_compra SET total=1 WHERE id_orden_compra=' || :v_oc, 'DENEGADO_REGLA_BD');
SELECT pg_temp.probar('rol_encargado_compras', 'Escribir factura_compra.total (GENERATED)',
    'UPDATE factura_compra SET total=1 WHERE id_factura_compra=' || :v_factura, 'DENEGADO_REGLA_BD');
SELECT pg_temp.probar('rol_encargado_compras', 'Escribir saldo_pendiente (GENERATED)',
    'UPDATE cuenta_por_pagar SET saldo_pendiente=0 WHERE id_cuenta_pagar=' || :v_cxp, 'DENEGADO_REGLA_BD');
SELECT pg_temp.probar('rol_encargado_compras', 'Falsear monto_pagado (frena el trigger)',
    'UPDATE cuenta_por_pagar SET monto_pagado=0 WHERE id_cuenta_pagar=' || :v_cxp, 'DENEGADO_REGLA_BD');
SELECT pg_temp.probar('rol_encargado_compras', 'Registrar un pago a proveedor (dispara el trigger de monto_pagado)',
    format('INSERT INTO pago_proveedor (id_cuenta_pagar,id_usuario_registro,monto,metodo_pago) VALUES (%s,%s,0.01,''efectivo'')', :v_cxp_saldo, :v_usuario), 'PERMITIDO');
SELECT pg_temp.probar('rol_encargado_compras', 'Crear un pedido de venta',
    format('INSERT INTO pedido (id_cliente,id_usuario,fecha_pedido) VALUES (%s,%s,now())', :v_cliente, :v_usuario), 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_encargado_compras', 'Definir un BOM (es de produccion)',
    format('INSERT INTO lista_materiales (id_producto,id_materia_prima,cantidad_necesaria) VALUES (%s,%s,1)', :v_prod_fab, :v_materia), 'DENEGADO_PRIVILEGIO');

-- ============================================================================
-- rol_encargado_produccion  (Encargado de Produccion)
-- ============================================================================
SELECT pg_temp.probar('rol_encargado_produccion', 'Leer el BOM',
    'SELECT count(*) FROM lista_materiales', 'PERMITIDO');
SELECT pg_temp.probar('rol_encargado_produccion', 'Crear una orden de produccion',
    format('INSERT INTO orden_produccion (id_producto,id_bodega_destino,id_usuario_registro,cantidad_planificada) VALUES (%s,%s,%s,1)', :v_prod_fab, :v_bodega, :v_usuario), 'PERMITIDO');
SELECT pg_temp.probar('rol_encargado_produccion', 'Consumir materia prima (stock_actual)',
    'UPDATE materia_prima SET stock_actual=stock_actual WHERE id_materia_prima=' || :v_materia, 'PERMITIDO');
SELECT pg_temp.probar('rol_encargado_produccion', 'Dar de alta producto terminado (stock inventario)',
    'UPDATE inventario SET stock_actual=stock_actual WHERE id_inventario=' || :v_inventario, 'PERMITIDO');
SELECT pg_temp.probar('rol_encargado_produccion', 'Alterar el costo promedio de materia prima',
    'UPDATE materia_prima SET costo_unitario_promedio=1 WHERE id_materia_prima=' || :v_materia, 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_encargado_produccion', 'Escribir merma (GENERATED)',
    'UPDATE orden_produccion_consumo SET merma=5 WHERE id_consumo=' || :v_consumo, 'DENEGADO_REGLA_BD');
SELECT pg_temp.probar('rol_encargado_produccion', 'Escribir costo_total (GENERATED)',
    'UPDATE orden_produccion SET costo_total=1 WHERE id_orden_produccion=' || :v_op, 'DENEGADO_REGLA_BD');
SELECT pg_temp.probar('rol_encargado_produccion', 'Falsear costo_materia_prima (frena el trigger)',
    'UPDATE orden_produccion SET costo_materia_prima=99999 WHERE id_orden_produccion=' || :v_op, 'DENEGADO_REGLA_BD');
SELECT pg_temp.probar('rol_encargado_produccion', 'Registrar una factura de compra',
    'UPDATE factura_compra SET estado=''pagada'' WHERE id_factura_compra=' || :v_factura, 'DENEGADO_PRIVILEGIO');

-- ============================================================================
-- rol_administrador
-- ============================================================================
SELECT pg_temp.probar('rol_administrador', 'Gestionar usuarios',
    'INSERT INTO usuario (nombre,apellido,correo,password,estado,created_at) VALUES (''a'',''b'',''adm@test.com'',repeat(''x'',60),''activo'',now())', 'PERMITIDO');
SELECT pg_temp.probar('rol_administrador', 'Aprobar una orden de compra (firma)',
    format('UPDATE orden_compra SET id_usuario_aprobador=%s, fecha_aprobacion=now() WHERE id_orden_compra=%s', :v_usuario, :v_oc), 'PERMITIDO');
SELECT pg_temp.probar('rol_administrador', 'MIN.PRIV - TRUNCATE sobre detalle_pedido',
    'TRUNCATE detalle_pedido', 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_administrador', 'MIN.PRIV - Escribir subtotal (GENERATED)',
    'UPDATE detalle_pedido SET subtotal=1 WHERE id_detalle=' || :v_detalle, 'DENEGADO_REGLA_BD');
SELECT pg_temp.probar('rol_administrador', 'MIN.PRIV - Falsear pedido.total (frena el trigger)',
    'UPDATE pedido SET total=99999 WHERE id_pedido=' || :v_pedido, 'DENEGADO_REGLA_BD');
SELECT pg_temp.probar('rol_administrador', 'MIN.PRIV - Crear una tabla (DDL)',
    'CREATE TABLE prueba_ddl (x int)', 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_administrador', 'MIN.PRIV - Crear un rol',
    'CREATE ROLE rol_intruso', 'DENEGADO_PRIVILEGIO');

-- ============================================================================
-- RESULTADOS
-- ============================================================================
\echo ''
\echo '--- Detalle de las pruebas ---'
SELECT n, rol, descripcion, esperado, obtenido, veredicto
FROM resultado_pruebas ORDER BY n;

\echo ''
\echo '--- Resumen por rol ---'
SELECT rol, count(*) AS pruebas,
       count(*) FILTER (WHERE veredicto='PASA')  AS pasan,
       count(*) FILTER (WHERE veredicto='FALLA') AS fallan
FROM resultado_pruebas GROUP BY rol ORDER BY rol;

\echo ''
\echo '--- Que capa detuvo cada intento denegado ---'
SELECT obtenido AS capa, count(*) AS casos
FROM resultado_pruebas WHERE obtenido LIKE 'DENEGADO%'
GROUP BY obtenido ORDER BY obtenido;

\echo ''
\echo '--- Pruebas que no cumplieron la expectativa (debe estar vacio) ---'
SELECT n, rol, descripcion, esperado, obtenido, detalle
FROM resultado_pruebas WHERE veredicto='FALLA' ORDER BY n;

DO $$
DECLARE v_fallan int; v_total int;
BEGIN
    SELECT count(*) FILTER (WHERE veredicto='FALLA'), count(*)
      INTO v_fallan, v_total FROM resultado_pruebas;
    IF v_fallan > 0 THEN
        RAISE EXCEPTION 'PRUEBAS DE PRIVILEGIOS: % de % no cumplieron la expectativa', v_fallan, v_total;
    END IF;
    RAISE NOTICE '===== % de % pruebas de privilegios PASAN =====', v_total, v_total;
END $$;
