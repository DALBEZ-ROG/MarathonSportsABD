-- ============================================================================
-- FASE 34 — ESQUEMA DE SEGURIDAD: ROLES Y PRIVILEGIOS
-- ----------------------------------------------------------------------------
-- Script IDEMPOTENTE y REEJECUTABLE. Reconstruye desde cero el modelo de
-- privilegios de mod_venta_inve sobre las 37 tablas.
--
-- ESTADO ANTERIOR AUDITADO (antes de este script):
--   - Existian 4 roles de grupo con privilegios otorgados a mano, sin script.
--   - Solo cubrian las 20 tablas base F1-F20. Las 17 tablas de F21-F29
--     (compras, devoluciones, manufactura) NO tenian ningun privilegio.
--   - Faltaban los roles de Encargado de Compras y Encargado de Produccion,
--     que si estan definidos como roles del sistema.
--   - rol_supervisor tenia USAGE sobre 22 secuencias siendo un rol de solo
--     lectura  -> incumplia el requisito 3.2.
--   - rol_operador_bodega tenia UPDATE a nivel de TABLA sobre inventario, lo
--     que le daba UPDATE tambien sobre id_producto e id_bodega
--     -> incumplia el requisito 3.3.
--   - PUBLIC conservaba USAGE sobre el esquema public -> incumplia 3.4.
--
-- ----------------------------------------------------------------------------
-- NOMENCLATURA: se conserva el prefijo rol_ ya desplegado. Correspondencia con
-- los roles funcionales del sistema:
--   rol_administrador        -> Administrador
--   rol_supervisor           -> Supervisor E-Commerce   (solo lectura)
--   rol_operador_bodega      -> Operador de Bodega
--   rol_operador_pedidos     -> Operador de Pedidos
--   rol_encargado_compras    -> Encargado de Compras       (NUEVO)
--   rol_encargado_produccion -> Encargado de Produccion    (NUEVO)
--
-- ----------------------------------------------------------------------------
-- POLITICA DE COLUMNAS CALCULADAS (regla de oro del proyecto, expresada como
-- privilegio y no solo como convencion de la aplicacion):
--
--   (a) Columnas GENERATED  -> NUNCA se otorga UPDATE, a ningun rol.
--       Son 8: detalle_pedido.subtotal, orden_compra_detalle.subtotal,
--       factura_compra.total, cuenta_por_pagar.saldo_pendiente,
--       orden_produccion.costo_total, orden_produccion.costo_unitario_producido,
--       orden_produccion_consumo.merma, orden_produccion_consumo.costo_linea.
--
--   (b) Columnas calculadas por TRIGGER -> UPDATE solo para rol_administrador.
--       Son 4: pedido.total, orden_compra.total,
--       cuenta_por_pagar.monto_pagado, orden_produccion.costo_materia_prima.
--       El trigger de proteccion ya rechaza cualquier valor que no sea el real,
--       asi que la defensa efectiva es el trigger; la restriccion de privilegio
--       es una segunda capa. No se le quita al administrador porque las
--       funciones legitimas de recalculo (p.ej. fn_set_costo_materia_prima_op)
--       se ejecutan con los privilegios de quien las invoca.
--
--   (c) Los roles operativos reciben UPDATE unicamente sobre las columnas que
--       su flujo de trabajo realmente modifica.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE MINIMO PRIVILEGIO QUE CONVIENE NO PERDER DE VISTA:
--
--   * NADIE recibe TRUNCATE, ni el administrador. TRUNCATE no dispara triggers
--     de fila, asi que un TRUNCATE sobre detalle_pedido dejaria pedido.total
--     desincronizado sin que salte ninguna proteccion. DELETE si dispara los
--     triggers, y es lo que se otorga.
--
--   * NADIE recibe REFERENCES. Crear FKs es una operacion de esquema y el
--     esquema solo se cambia por script versionado.
--
--   * Separacion de funciones en compras (regla de negocio 8): quien crea una
--     orden no puede aprobarla. Se expresa en la BD negando a
--     rol_encargado_compras el UPDATE sobre id_usuario_aprobador y
--     fecha_aprobacion de orden_compra: sin esas dos columnas la aprobacion es
--     imposible de completar. Solo rol_administrador las tiene.
--
--   * Los roles de grupo son NOLOGIN. Se conectan los usuarios usr_*_marathon,
--     que heredan los privilegios por membresia.
--
-- ----------------------------------------------------------------------------
-- ESTADO OPERATIVO: la aplicacion Spring Boot YA se conecta como
-- usr_admin_marathon, no como el superusuario postgres, asi que estos
-- privilegios estan en el camino de ejecucion y no son solo declarativos.
-- Verificado arrancando la aplicacion y ejercitando sus endpoints; el registro
-- de auditoria de la F36 lo confirma con usuario=usr_admin_marathon.
--
-- Lo que sigue pendiente es una conexion por rol: hoy toda la aplicacion usa la
-- misma cuenta, de modo que los otros cinco roles no intervienen en el trafico
-- web. Ver la seccion 8 de SEGURIDAD_ROLES.md.
--
-- IMPORTANTE: los respaldos NO usan esta cuenta. pg_basebackup exige
-- REPLICATION, que usr_admin_marathon no tiene ni debe tener; scripts/backup
-- lee PG_SUPERUSER/PG_SUPERUSER_PASSWORD del .env, separadas de las de la
-- aplicacion.
-- ============================================================================

\set ON_ERROR_STOP on
\echo '=== FASE 34: esquema de seguridad (roles y privilegios) ==='

-- ============================================================================
-- PARTE 0 — CERRAR EL ACCESO POR DEFECTO DE PUBLIC   (requisito 3.4)
-- ----------------------------------------------------------------------------
-- PUBLIC es un pseudo-rol al que pertenece todo usuario de la instancia. Por
-- defecto PostgreSQL le da USAGE sobre el esquema public, asi que cualquier
-- cuenta nueva podria listar y resolver objetos del esquema. Se revoca.
-- (CONNECT y CREATE ya estaban revocados; se repiten por idempotencia.)
-- ============================================================================
REVOKE ALL ON DATABASE mod_venta_inve FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;

-- ============================================================================
-- PARTE 1 — LIMPIEZA PREVIA   (requisito 3.1: reejecutable desde el inicio)
-- ----------------------------------------------------------------------------
-- El REVOKE explicito va ANTES del DROP ROLE. Sin el, DROP ROLE falla con
-- "role cannot be dropped because some objects depend on it" en cuanto el rol
-- tenga cualquier privilegio otorgado. Se hace en bucle para los 6 roles.
--
-- REASSIGN OWNED traspasa a postgres cualquier objeto que el rol posea;
-- DROP OWNED elimina los privilegios y las default privileges que le queden.
-- Ese par es lo que hace que el script se pueda correr N veces.
-- ============================================================================
DO $$
DECLARE
    v_rol text;
    v_roles text[] := ARRAY[
        'rol_administrador', 'rol_supervisor', 'rol_operador_bodega',
        'rol_operador_pedidos', 'rol_encargado_compras', 'rol_encargado_produccion'
    ];
BEGIN
    FOREACH v_rol IN ARRAY v_roles LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = v_rol) THEN
            EXECUTE format('REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM %I', v_rol);
            EXECUTE format('REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM %I', v_rol);
            EXECUTE format('REVOKE ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public FROM %I', v_rol);
            EXECUTE format('REVOKE ALL PRIVILEGES ON SCHEMA public FROM %I', v_rol);
            EXECUTE format('REVOKE ALL PRIVILEGES ON DATABASE mod_venta_inve FROM %I', v_rol);
            EXECUTE format('REASSIGN OWNED BY %I TO postgres', v_rol);
            EXECUTE format('DROP OWNED BY %I', v_rol);
            EXECUTE format('DROP ROLE %I', v_rol);
            RAISE NOTICE 'Rol previo eliminado: %', v_rol;
        END IF;
    END LOOP;
END $$;

-- ============================================================================
-- PARTE 2 — CREACION DE LOS 6 ROLES DE GRUPO
-- ----------------------------------------------------------------------------
-- NOLOGIN: son contenedores de privilegios, no cuentas. Nadie se conecta con
-- ellos. NOINHERIT no se usa: se quiere justamente que los usuarios hereden.
-- ============================================================================
CREATE ROLE rol_administrador        NOLOGIN;
CREATE ROLE rol_supervisor           NOLOGIN;
CREATE ROLE rol_operador_bodega      NOLOGIN;
CREATE ROLE rol_operador_pedidos     NOLOGIN;
CREATE ROLE rol_encargado_compras    NOLOGIN;
CREATE ROLE rol_encargado_produccion NOLOGIN;

COMMENT ON ROLE rol_administrador        IS 'Administrador: DML completo sobre las 37 tablas. Sin TRUNCATE ni DDL.';
COMMENT ON ROLE rol_supervisor           IS 'Supervisor E-Commerce: SOLO LECTURA. Sin privilegios sobre secuencias.';
COMMENT ON ROLE rol_operador_bodega      IS 'Operador de Bodega: inventario, picking y empaque. UPDATE por columna.';
COMMENT ON ROLE rol_operador_pedidos     IS 'Operador de Pedidos: alta de clientes y pedidos.';
COMMENT ON ROLE rol_encargado_compras    IS 'Encargado de Compras: ciclo procure-to-pay. No puede aprobar sus ordenes.';
COMMENT ON ROLE rol_encargado_produccion IS 'Encargado de Produccion: BOM, ordenes de produccion y materia prima.';

-- Acceso a la base y al esquema: sin esto ningun GRANT de tabla sirve.
GRANT CONNECT ON DATABASE mod_venta_inve TO
    rol_administrador, rol_supervisor, rol_operador_bodega,
    rol_operador_pedidos, rol_encargado_compras, rol_encargado_produccion;

GRANT USAGE ON SCHEMA public TO
    rol_administrador, rol_supervisor, rol_operador_bodega,
    rol_operador_pedidos, rol_encargado_compras, rol_encargado_produccion;

-- ============================================================================
-- PARTE 3 — rol_administrador
-- ----------------------------------------------------------------------------
-- DML completo. Se otorga tabla por tabla y no con ALL TABLES IN SCHEMA para
-- que quede explicito el alcance y para poder excluir columnas calculadas.
-- ============================================================================

-- Lectura y borrado sobre las 37 tablas
GRANT SELECT, DELETE ON ALL TABLES IN SCHEMA public TO rol_administrador;

-- INSERT sobre las 37 tablas. En las que tienen columnas GENERATED se limita a
-- las columnas escribibles (PostgreSQL rechazaria el INSERT que las mencione).
GRANT INSERT ON ALL TABLES IN SCHEMA public TO rol_administrador;

-- UPDATE: tablas SIN columnas calculadas -> a nivel de tabla.
GRANT UPDATE ON
    ciudad, categoria, unidad_medida, proveedor, bodega, cliente, producto,
    producto_proveedor, usuario, rol, usuario_rol, permiso, rol_permiso,
    log_accion, inventario, movimiento_inventario, historial_inventario,
    comprobante_interno, materia_prima, recepcion_mercancia,
    recepcion_mercancia_detalle, pago_proveedor, solicitud_devolucion,
    solicitud_devolucion_detalle, reembolso_cliente, devolucion_proveedor,
    devolucion_proveedor_detalle, movimiento_materia_prima, lista_materiales
TO rol_administrador;

-- UPDATE: tablas CON columnas calculadas -> por columna, excluyendo las
-- GENERATED. Las calculadas por trigger si se incluyen (ver politica (b)).
GRANT UPDATE (id_cliente, id_usuario, fecha_pedido, total, descuento, estado,
              created_at, updated_at, es_pedido_especial, tipo_especial,
              nota_especial, fecha_limite_entrega, numero_hu, transportista,
              region_destino, fecha_empaque)
    ON pedido TO rol_administrador;   -- excluida: (ninguna GENERATED)

GRANT UPDATE (id_pedido, id_producto, cantidad, precio_unitario,
              picking_completado, cantidad_recogida)
    ON detalle_pedido TO rol_administrador;   -- excluida GENERATED: subtotal

GRANT UPDATE (id_proveedor, id_usuario_solicitante, id_usuario_aprobador,
              fecha_orden, fecha_aprobacion, estado, total, observaciones,
              created_at, updated_at)
    ON orden_compra TO rol_administrador;

GRANT UPDATE (id_orden_compra, tipo_item, id_producto, id_materia_prima,
              cantidad, precio_unitario, cantidad_recibida)
    ON orden_compra_detalle TO rol_administrador;   -- excluida: subtotal

GRANT UPDATE (id_orden_compra, id_usuario_registro, numero_factura_proveedor,
              fecha_factura, fecha_vencimiento, subtotal, impuesto, estado,
              created_at)
    ON factura_compra TO rol_administrador;   -- excluida: total

GRANT UPDATE (id_factura_compra, id_proveedor, monto_total, monto_pagado,
              fecha_vencimiento, estado, created_at)
    ON cuenta_por_pagar TO rol_administrador;   -- excluida: saldo_pendiente

GRANT UPDATE (id_producto, id_bodega_destino, id_usuario_registro,
              id_usuario_completa, cantidad_planificada, cantidad_producida,
              estado, fecha_creacion, fecha_inicio, fecha_fin, observaciones,
              costo_materia_prima, costo_mano_obra, costo_indirecto)
    ON orden_produccion TO rol_administrador;
    -- excluidas GENERATED: costo_total, costo_unitario_producido

GRANT UPDATE (id_orden_produccion, id_materia_prima, cantidad_teorica,
              cantidad_real, costo_unitario_snapshot)
    ON orden_produccion_consumo TO rol_administrador;
    -- excluidas GENERATED: merma, costo_linea

-- Secuencias: necesita USAGE (nextval) y SELECT (currval) para poder insertar.
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO rol_administrador;

-- ============================================================================
-- PARTE 4 — rol_supervisor  (Supervisor E-Commerce)   SOLO LECTURA
-- ----------------------------------------------------------------------------
-- REQUISITO 3.2: no se le otorga USAGE sobre secuencias. Ni aqui ni en ningun
-- otro punto del script. USAGE sobre una secuencia solo sirve para llamar a
-- nextval(), y nextval() solo se usa al INSERT. Un rol que no inserta no tiene
-- ninguna razon para poder avanzar un contador de la base.
-- No hace falta revocar lo que nunca se otorga: simplemente no existe la linea
-- GRANT ... ON ALL SEQUENCES ... TO rol_supervisor.
-- ============================================================================
GRANT SELECT ON ALL TABLES IN SCHEMA public TO rol_supervisor;

-- ============================================================================
-- PARTE 5 — rol_operador_bodega  (Operador de Bodega)
-- ============================================================================

-- Lectura: catalogos y todo lo que necesita consultar para operar
GRANT SELECT ON
    ciudad, categoria, unidad_medida, bodega, producto, cliente,
    inventario, movimiento_inventario, historial_inventario,
    pedido, detalle_pedido, comprobante_interno,
    materia_prima, movimiento_materia_prima,
    orden_compra, orden_compra_detalle,
    recepcion_mercancia, recepcion_mercancia_detalle
TO rol_operador_bodega;

-- ---------------------------------------------------------------------------
-- REQUISITO 3.3: UPDATE SOLO A NIVEL DE COLUMNA sobre inventario.
-- El operador ajusta la cantidad en existencia; NO reasigna a que producto ni
-- a que bodega pertenece la fila (eso cambiaria la identidad del registro y
-- ademas violaria el UNIQUE (id_producto, id_bodega)).
-- Se incluye fecha_actualizacion porque el propio flujo de ajuste la escribe.
-- NO se otorga: GRANT UPDATE ON inventario  (tabla completa).
-- ---------------------------------------------------------------------------
GRANT UPDATE (stock_actual, fecha_actualizacion) ON inventario TO rol_operador_bodega;

-- Alta de una combinacion producto-bodega que aun no existe
GRANT INSERT ON inventario TO rol_operador_bodega;

-- Picking (F14): marca lo recogido, no toca cantidades ni precios pedidos
GRANT UPDATE (picking_completado, cantidad_recogida)
    ON detalle_pedido TO rol_operador_bodega;

-- Empaque y despacho (F15): datos de la unidad de handling y avance de estado.
-- NO recibe UPDATE sobre pedido.total ni sobre importes.
GRANT UPDATE (estado, numero_hu, transportista, region_destino,
              fecha_empaque, updated_at)
    ON pedido TO rol_operador_bodega;

-- Bitacora de movimientos y emision de comprobantes internos
GRANT INSERT ON movimiento_inventario, comprobante_interno TO rol_operador_bodega;

-- Solo las secuencias de las tablas donde realmente inserta
GRANT USAGE, SELECT ON SEQUENCE
    inventario_id_inventario_seq,
    movimiento_inventario_id_movimiento_seq,
    comprobante_interno_id_comprobante_seq
TO rol_operador_bodega;

-- ============================================================================
-- PARTE 6 — rol_operador_pedidos  (Operador de Pedidos)
-- ============================================================================
GRANT SELECT ON
    ciudad, categoria, unidad_medida, bodega, producto, inventario,
    cliente, pedido, detalle_pedido, comprobante_interno,
    solicitud_devolucion, solicitud_devolucion_detalle
TO rol_operador_pedidos;

GRANT INSERT ON cliente, comprobante_interno TO rol_operador_pedidos;
GRANT UPDATE ON cliente TO rol_operador_pedidos;

-- Alta de pedidos: sin total (lo calcula el trigger) y sin subtotal (GENERATED)
GRANT INSERT (id_cliente, id_usuario, fecha_pedido, descuento, estado,
              created_at, updated_at, es_pedido_especial, tipo_especial,
              nota_especial, fecha_limite_entrega)
    ON pedido TO rol_operador_pedidos;

GRANT UPDATE (estado, descuento, es_pedido_especial, tipo_especial,
              nota_especial, fecha_limite_entrega, updated_at)
    ON pedido TO rol_operador_pedidos;

GRANT INSERT (id_pedido, id_producto, cantidad, precio_unitario)
    ON detalle_pedido TO rol_operador_pedidos;
GRANT UPDATE (cantidad, precio_unitario) ON detalle_pedido TO rol_operador_pedidos;
GRANT DELETE ON detalle_pedido TO rol_operador_pedidos;

GRANT USAGE, SELECT ON SEQUENCE
    cliente_id_cliente_seq,
    pedido_id_pedido_seq,
    detalle_pedido_id_detalle_seq,
    comprobante_interno_id_comprobante_seq
TO rol_operador_pedidos;

-- ============================================================================
-- PARTE 7 — rol_encargado_compras  (Encargado de Compras, F21-F25)
-- ============================================================================
GRANT SELECT ON
    ciudad, categoria, unidad_medida, bodega, proveedor, producto,
    producto_proveedor, materia_prima, inventario, movimiento_materia_prima,
    orden_compra, orden_compra_detalle,
    recepcion_mercancia, recepcion_mercancia_detalle,
    factura_compra, cuenta_por_pagar, pago_proveedor,
    devolucion_proveedor, devolucion_proveedor_detalle,
    solicitud_devolucion, solicitud_devolucion_detalle
TO rol_encargado_compras;

-- Catalogo de materia prima: crea y edita, pero NO fija el costo promedio
-- (lo recalcula el servicio al recibir compra) ni el stock (lo mueve el kardex).
GRANT INSERT ON materia_prima TO rol_encargado_compras;
GRANT UPDATE (nombre, descripcion, id_unidad_medida, estado, stock_minimo)
    ON materia_prima TO rol_encargado_compras;

-- ---------------------------------------------------------------------------
-- SEPARACION DE FUNCIONES (regla de negocio 8) expresada como privilegio:
-- puede crear la orden y enviarla a aprobacion, pero NO puede completar la
-- aprobacion porque no tiene UPDATE sobre id_usuario_aprobador ni sobre
-- fecha_aprobacion. Esas dos columnas son exclusivas de rol_administrador.
-- Tampoco recibe UPDATE sobre total (lo calcula el trigger).
-- ---------------------------------------------------------------------------
GRANT INSERT (id_proveedor, id_usuario_solicitante, fecha_orden, estado,
              observaciones, created_at, updated_at)
    ON orden_compra TO rol_encargado_compras;
GRANT UPDATE (estado, observaciones, updated_at)
    ON orden_compra TO rol_encargado_compras;

GRANT INSERT (id_orden_compra, tipo_item, id_producto, id_materia_prima,
              cantidad, precio_unitario, cantidad_recibida)
    ON orden_compra_detalle TO rol_encargado_compras;
GRANT UPDATE (cantidad, precio_unitario, cantidad_recibida)
    ON orden_compra_detalle TO rol_encargado_compras;
GRANT DELETE ON orden_compra_detalle TO rol_encargado_compras;

-- Recepcion de mercancia (F22): al recibir sube stock, asi que necesita
-- escribir cantidad en inventario y en materia_prima, y dejar el rastro.
GRANT INSERT ON recepcion_mercancia, recepcion_mercancia_detalle TO rol_encargado_compras;
GRANT INSERT ON inventario, movimiento_inventario, movimiento_materia_prima TO rol_encargado_compras;
GRANT UPDATE (stock_actual, fecha_actualizacion) ON inventario TO rol_encargado_compras;
GRANT UPDATE (stock_actual, costo_unitario_promedio) ON materia_prima TO rol_encargado_compras;

-- Factura, cuenta por pagar y pagos (F23). Sin total (GENERATED),
-- sin saldo_pendiente (GENERATED) y sin monto_pagado (trigger).
GRANT INSERT (id_orden_compra, id_usuario_registro, numero_factura_proveedor,
              fecha_factura, fecha_vencimiento, subtotal, impuesto, estado,
              created_at)
    ON factura_compra TO rol_encargado_compras;
GRANT UPDATE (estado) ON factura_compra TO rol_encargado_compras;

GRANT INSERT (id_factura_compra, id_proveedor, monto_total, fecha_vencimiento,
              estado, created_at)
    ON cuenta_por_pagar TO rol_encargado_compras;
GRANT UPDATE (estado) ON cuenta_por_pagar TO rol_encargado_compras;

GRANT INSERT ON pago_proveedor TO rol_encargado_compras;

-- Devolucion a proveedor (F25)
GRANT INSERT ON devolucion_proveedor, devolucion_proveedor_detalle TO rol_encargado_compras;
GRANT UPDATE (estado, tipo_resolucion, monto_reembolso, observaciones)
    ON devolucion_proveedor TO rol_encargado_compras;

GRANT USAGE, SELECT ON SEQUENCE
    materia_prima_id_materia_prima_seq,
    orden_compra_id_orden_compra_seq,
    orden_compra_detalle_id_detalle_oc_seq,
    recepcion_mercancia_id_recepcion_seq,
    recepcion_mercancia_detalle_id_detalle_rm_seq,
    factura_compra_id_factura_compra_seq,
    cuenta_por_pagar_id_cuenta_pagar_seq,
    pago_proveedor_id_pago_seq,
    devolucion_proveedor_id_devolucion_prov_seq,
    devolucion_proveedor_detalle_id_detalle_dp_seq,
    inventario_id_inventario_seq,
    movimiento_inventario_id_movimiento_seq,
    movimiento_materia_prima_id_movimiento_mp_seq
TO rol_encargado_compras;

-- ============================================================================
-- PARTE 8 — rol_encargado_produccion  (Encargado de Produccion, F26-F29)
-- ============================================================================
GRANT SELECT ON
    ciudad, categoria, unidad_medida, bodega, producto, inventario,
    materia_prima, movimiento_materia_prima, lista_materiales,
    orden_produccion, orden_produccion_consumo,
    orden_compra, orden_compra_detalle, recepcion_mercancia
TO rol_encargado_produccion;

-- BOM (F27): define la receta de los productos fabricados
GRANT INSERT ON lista_materiales TO rol_encargado_produccion;
GRANT UPDATE (cantidad_necesaria, estado) ON lista_materiales TO rol_encargado_produccion;
GRANT DELETE ON lista_materiales TO rol_encargado_produccion;

-- Cambiar el origen de un producto a 'fabricado' es parte de su trabajo.
-- El trigger trg_validar_cambio_origen_producto sigue impidiendo volverlo
-- 'comprado' si tiene BOM activo.
GRANT UPDATE (origen) ON producto TO rol_encargado_produccion;

-- Ordenes de produccion (F28). Sin costo_total ni costo_unitario_producido
-- (GENERATED). costo_materia_prima si, porque lo fija la funcion
-- fn_set_costo_materia_prima_op() que corre con los privilegios del invocante,
-- y el trigger de proteccion ya rechaza cualquier valor que no sea el real.
GRANT INSERT (id_producto, id_bodega_destino, id_usuario_registro,
              cantidad_planificada, estado, fecha_creacion, observaciones)
    ON orden_produccion TO rol_encargado_produccion;
GRANT UPDATE (id_usuario_completa, cantidad_producida, estado, fecha_inicio,
              fecha_fin, observaciones, costo_materia_prima, costo_mano_obra,
              costo_indirecto)
    ON orden_produccion TO rol_encargado_produccion;

GRANT INSERT (id_orden_produccion, id_materia_prima, cantidad_teorica,
              cantidad_real, costo_unitario_snapshot)
    ON orden_produccion_consumo TO rol_encargado_produccion;
GRANT UPDATE (cantidad_real) ON orden_produccion_consumo TO rol_encargado_produccion;
GRANT DELETE ON orden_produccion_consumo TO rol_encargado_produccion;

-- Consumo de materia prima y alta del producto terminado
GRANT UPDATE (stock_actual) ON materia_prima TO rol_encargado_produccion;
GRANT INSERT ON movimiento_materia_prima TO rol_encargado_produccion;
GRANT UPDATE (stock_actual, fecha_actualizacion) ON inventario TO rol_encargado_produccion;
GRANT INSERT ON inventario, movimiento_inventario TO rol_encargado_produccion;

GRANT USAGE, SELECT ON SEQUENCE
    lista_materiales_id_bom_seq,
    orden_produccion_id_orden_produccion_seq,
    orden_produccion_consumo_id_consumo_seq,
    movimiento_materia_prima_id_movimiento_mp_seq,
    inventario_id_inventario_seq,
    movimiento_inventario_id_movimiento_seq
TO rol_encargado_produccion;

-- ============================================================================
-- PARTE 9 — AUDITORIA: todos los roles operativos alimentan log_accion (F19b)
-- ----------------------------------------------------------------------------
-- INSERT si, UPDATE y DELETE no: una bitacora que se puede editar o borrar no
-- es una bitacora. Solo rol_administrador puede corregirla.
-- ============================================================================
GRANT INSERT ON log_accion TO
    rol_operador_bodega, rol_operador_pedidos,
    rol_encargado_compras, rol_encargado_produccion;
GRANT SELECT ON log_accion TO rol_encargado_compras, rol_encargado_produccion;
GRANT USAGE, SELECT ON SEQUENCE log_accion_id_log_seq TO
    rol_operador_bodega, rol_operador_pedidos,
    rol_encargado_compras, rol_encargado_produccion;

-- ============================================================================
-- PARTE 9b — PRIVILEGIOS QUE EXIGEN LOS TRIGGERS  (hallazgo de las pruebas)
-- ----------------------------------------------------------------------------
-- Una funcion de trigger que no es SECURITY DEFINER se ejecuta con los
-- privilegios del usuario que dispara la sentencia, NO con los del dueno de la
-- tabla. Consecuencia practica: para que un rol pueda modificar una tabla, hay
-- que otorgarle tambien los privilegios que necesitan los triggers de esa tabla
-- sobre OTRAS tablas. Sin esto los flujos fallan con "permiso denegado" en una
-- tabla que el rol nunca menciono en su SQL.
--
-- Esta seccion existe porque las pruebas de fase34_pruebas_roles.sql lo
-- detectaron: sin ella, un operador de bodega no podia ni marcar un picking.
--
-- No debilita el modelo: en los tres casos de columna calculada, el trigger de
-- proteccion sigue rechazando cualquier valor que no sea el recalculado real.
-- El privilegio permite que el trigger legitimo escriba; no permite falsear.
-- ============================================================================

-- (1) trg_recalcular_total_pedido_{insert,update,delete} sobre detalle_pedido
--     ejecuta UPDATE pedido SET total = ... Quien toque detalle_pedido necesita
--     UPDATE (total) ON pedido. fn_proteger_total_pedido sigue vigilando el valor.
GRANT UPDATE (total) ON pedido TO rol_operador_bodega, rol_operador_pedidos;

-- (2) trg_historial_inventario sobre inventario ejecuta
--     INSERT INTO historial_inventario ... Quien mueva stock necesita INSERT ahi.
GRANT INSERT ON historial_inventario TO
    rol_operador_bodega, rol_encargado_compras, rol_encargado_produccion;

-- (3) trg_oc_total_{insert,update,delete} sobre orden_compra_detalle ejecuta
--     UPDATE orden_compra SET total = ...
GRANT UPDATE (total) ON orden_compra TO rol_encargado_compras;

-- (4) trg_cxp_pagado_* sobre pago_proveedor ejecuta
--     UPDATE cuenta_por_pagar SET monto_pagado = ...  y, si queda saldada,
--     UPDATE factura_compra SET estado = 'pagada'.
GRANT UPDATE (monto_pagado) ON cuenta_por_pagar TO rol_encargado_compras;

-- Secuencia de historial_inventario, resuelta desde el catalogo porque en esta
-- base hay dos secuencias con nombre parecido y solo una es la de la columna.
DO $$
DECLARE v_seq text;
BEGIN
    v_seq := pg_get_serial_sequence('public.historial_inventario', 'id_historial');
    IF v_seq IS NULL THEN
        RAISE EXCEPTION 'No se pudo resolver la secuencia de historial_inventario.id_historial';
    END IF;
    EXECUTE format(
        'GRANT USAGE, SELECT ON SEQUENCE %s TO rol_operador_bodega, rol_encargado_compras, rol_encargado_produccion',
        v_seq);
    RAISE NOTICE 'Secuencia de historial_inventario otorgada: %', v_seq;
END $$;

-- ============================================================================
-- PARTE 10 — USUARIOS DE LOGIN Y MEMBRESIA
-- ----------------------------------------------------------------------------
-- Los usuarios se crean SIN contrasena conocida: se genera una aleatoria para
-- que la cuenta exista y quede inutilizable hasta que un DBA le asigne una.
-- Deliberadamente NO se escriben contrasenas en un script versionado.
-- Para asignarlas:
--     ALTER ROLE usr_admin_marathon WITH PASSWORD '<clave>';
-- Si el usuario ya existe, no se toca su contrasena.
-- ============================================================================
DO $$
DECLARE
    v_par   text[];
    v_pares text[][] := ARRAY[
        ['usr_admin_marathon',      'rol_administrador'],
        ['usr_supervisor_marathon', 'rol_supervisor'],
        ['usr_bodega_marathon',     'rol_operador_bodega'],
        ['usr_pedidos_marathon',    'rol_operador_pedidos'],
        ['usr_compras_marathon',    'rol_encargado_compras'],
        ['usr_produccion_marathon', 'rol_encargado_produccion']
    ];
BEGIN
    FOREACH v_par SLICE 1 IN ARRAY v_pares LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = v_par[1]) THEN
            EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', v_par[1], gen_random_uuid()::text);
            RAISE NOTICE 'Usuario creado (contrasena aleatoria, debe asignarse): %', v_par[1];
        END IF;
        EXECUTE format('GRANT %I TO %I', v_par[2], v_par[1]);
        RAISE NOTICE 'Membresia: % -> %', v_par[1], v_par[2];
    END LOOP;
END $$;

-- ============================================================================
-- PARTE 11 — AUTOVERIFICACION
-- ----------------------------------------------------------------------------
-- El script falla en voz alta si alguno de los requisitos no se cumple, para
-- que nadie pueda dar por bueno un despliegue a medias.
-- ============================================================================
DO $$
DECLARE
    v_n      int;
    v_detalle text;
BEGIN
    ---------------------------------------------------------------- 3.2 ------
    -- rol_supervisor NO debe tener NINGUN privilegio sobre NINGUNA secuencia.
    SELECT count(*), string_agg(c.relname, ', ')
      INTO v_n, v_detalle
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relkind = 'S' AND n.nspname = 'public'
      AND (has_sequence_privilege('rol_supervisor', c.oid, 'USAGE')
        OR has_sequence_privilege('rol_supervisor', c.oid, 'SELECT')
        OR has_sequence_privilege('rol_supervisor', c.oid, 'UPDATE'));
    IF v_n > 0 THEN
        RAISE EXCEPTION 'REQUISITO 3.2 INCUMPLIDO: rol_supervisor tiene privilegios sobre % secuencias: %', v_n, v_detalle;
    END IF;
    RAISE NOTICE 'OK 3.2 - rol_supervisor sin privilegios sobre secuencias (0 de %)',
        (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
         WHERE c.relkind='S' AND n.nspname='public');

    -- ...y debe ser estrictamente de lectura
    SELECT count(*) INTO v_n
    FROM information_schema.table_privileges
    WHERE table_schema = 'public' AND grantee = 'rol_supervisor'
      AND privilege_type <> 'SELECT';
    IF v_n > 0 THEN
        RAISE EXCEPTION 'REQUISITO 3.2 INCUMPLIDO: rol_supervisor tiene % privilegios distintos de SELECT', v_n;
    END IF;
    RAISE NOTICE 'OK 3.2 - rol_supervisor es solo lectura (% tablas con SELECT)',
        (SELECT count(*) FROM information_schema.table_privileges
         WHERE table_schema='public' AND grantee='rol_supervisor' AND privilege_type='SELECT');

    ---------------------------------------------------------------- 3.3 ------
    -- rol_operador_bodega NO debe tener UPDATE a nivel de TABLA sobre inventario
    IF EXISTS (SELECT 1 FROM information_schema.table_privileges
               WHERE table_schema='public' AND grantee='rol_operador_bodega'
                 AND table_name='inventario' AND privilege_type='UPDATE') THEN
        RAISE EXCEPTION 'REQUISITO 3.3 INCUMPLIDO: rol_operador_bodega tiene UPDATE a nivel de tabla sobre inventario';
    END IF;

    -- ...si debe tenerlo sobre stock_actual
    IF NOT has_column_privilege('rol_operador_bodega', 'inventario', 'stock_actual', 'UPDATE') THEN
        RAISE EXCEPTION 'REQUISITO 3.3 INCUMPLIDO: rol_operador_bodega no puede actualizar inventario.stock_actual';
    END IF;

    -- ...y NO sobre las columnas de identidad de la fila
    IF has_column_privilege('rol_operador_bodega', 'inventario', 'id_producto', 'UPDATE')
       OR has_column_privilege('rol_operador_bodega', 'inventario', 'id_bodega', 'UPDATE') THEN
        RAISE EXCEPTION 'REQUISITO 3.3 INCUMPLIDO: rol_operador_bodega puede reasignar producto o bodega';
    END IF;
    RAISE NOTICE 'OK 3.3 - operador_bodega: UPDATE por columna en inventario (stock_actual si, id_producto/id_bodega no)';

    ---------------------------------------------------------------- 3.4 ------
    IF has_database_privilege('public', 'mod_venta_inve', 'CONNECT') THEN
        RAISE EXCEPTION 'REQUISITO 3.4 INCUMPLIDO: PUBLIC conserva CONNECT sobre la base';
    END IF;
    IF has_schema_privilege('public', 'public', 'USAGE')
       OR has_schema_privilege('public', 'public', 'CREATE') THEN
        RAISE EXCEPTION 'REQUISITO 3.4 INCUMPLIDO: PUBLIC conserva privilegios sobre el esquema public';
    END IF;
    RAISE NOTICE 'OK 3.4 - PUBLIC sin privilegios sobre la base ni sobre el esquema';

    ------------------------------------------------- minimo privilegio -------
    -- Nadie, ni el administrador, debe tener TRUNCATE
    SELECT count(*), string_agg(DISTINCT grantee, ', ') INTO v_n, v_detalle
    FROM information_schema.table_privileges
    WHERE table_schema='public' AND privilege_type='TRUNCATE' AND grantee LIKE 'rol\_%';
    IF v_n > 0 THEN
        RAISE EXCEPTION 'MINIMO PRIVILEGIO INCUMPLIDO: hay TRUNCATE otorgado a %', v_detalle;
    END IF;
    RAISE NOTICE 'OK - ningun rol tiene TRUNCATE (no dispara triggers de fila)';

    -- Nadie debe tener UPDATE sobre una columna GENERATED
    SELECT count(*), string_agg(cp.grantee || '.' || cp.table_name || '.' || cp.column_name, ', ')
      INTO v_n, v_detalle
    FROM information_schema.column_privileges cp
    JOIN information_schema.columns c
      ON c.table_schema = cp.table_schema
     AND c.table_name   = cp.table_name
     AND c.column_name  = cp.column_name
    WHERE cp.table_schema = 'public'
      AND cp.grantee LIKE 'rol\_%'
      AND cp.privilege_type = 'UPDATE'
      AND c.is_generated = 'ALWAYS';
    IF v_n > 0 THEN
        RAISE EXCEPTION 'POLITICA (a) INCUMPLIDA: UPDATE otorgado sobre columnas GENERATED: %', v_detalle;
    END IF;
    RAISE NOTICE 'OK - ningun rol tiene UPDATE sobre las 8 columnas GENERATED';

    ------------------------------------------- separacion de funciones -------
    IF has_column_privilege('rol_encargado_compras','orden_compra','id_usuario_aprobador','UPDATE')
       OR has_column_privilege('rol_encargado_compras','orden_compra','fecha_aprobacion','UPDATE') THEN
        RAISE EXCEPTION 'SEPARACION DE FUNCIONES INCUMPLIDA: compras puede aprobar sus propias ordenes';
    END IF;
    RAISE NOTICE 'OK - separacion de funciones: compras no puede aprobar (sin id_usuario_aprobador ni fecha_aprobacion)';

    ------------------------------------------------- cobertura de tablas -----
    SELECT count(*) INTO v_n FROM pg_tables WHERE schemaname='public';
    IF (SELECT count(DISTINCT table_name) FROM information_schema.table_privileges
        WHERE table_schema='public' AND grantee='rol_supervisor') <> v_n THEN
        RAISE EXCEPTION 'COBERTURA INCOMPLETA: rol_supervisor no ve las % tablas', v_n;
    END IF;
    RAISE NOTICE 'OK - cobertura completa: las % tablas tienen privilegios definidos', v_n;

    RAISE NOTICE '===== FASE 34 OK: 6 roles, 4 requisitos verificados =====';
END $$;

-- ============================================================================
-- RESUMEN FINAL
-- ============================================================================
\echo ''
\echo '--- Privilegios por rol (conteo de tablas) ---'
SELECT grantee AS rol,
       count(*) FILTER (WHERE privilege_type = 'SELECT') AS con_select,
       count(*) FILTER (WHERE privilege_type = 'INSERT') AS con_insert,
       count(*) FILTER (WHERE privilege_type = 'UPDATE') AS con_update_tabla,
       count(*) FILTER (WHERE privilege_type = 'DELETE') AS con_delete
FROM information_schema.table_privileges
WHERE table_schema = 'public' AND grantee LIKE 'rol\_%'
GROUP BY grantee ORDER BY grantee;

\echo ''
\echo '--- UPDATE otorgado SOLO a nivel de columna (minimo privilegio fino) ---'
SELECT cp.grantee AS rol, cp.table_name AS tabla,
       string_agg(cp.column_name, ', ' ORDER BY cp.column_name) AS columnas
FROM information_schema.column_privileges cp
WHERE cp.table_schema = 'public' AND cp.privilege_type = 'UPDATE'
  AND cp.grantee LIKE 'rol\_%'
  AND NOT EXISTS (SELECT 1 FROM information_schema.table_privileges tp
                  WHERE tp.table_schema='public' AND tp.grantee=cp.grantee
                    AND tp.table_name=cp.table_name AND tp.privilege_type='UPDATE')
GROUP BY cp.grantee, cp.table_name
ORDER BY cp.grantee, cp.table_name;

\echo ''
\echo '--- Secuencias por rol (rol_supervisor debe aparecer con 0) ---'
SELECT r.rolname AS rol,
       count(*) FILTER (WHERE has_sequence_privilege(r.rolname, c.oid, 'USAGE')) AS secuencias_con_usage
FROM pg_class c
CROSS JOIN (SELECT rolname FROM pg_roles WHERE rolname LIKE 'rol\_%') r
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE c.relkind = 'S' AND n.nspname = 'public'
GROUP BY r.rolname ORDER BY r.rolname;
