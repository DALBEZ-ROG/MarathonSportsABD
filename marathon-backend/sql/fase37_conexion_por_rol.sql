-- ============================================================================
-- FASE 37 — CONEXION POR ROL: privilegios que faltaban para poner los seis
--           roles de PostgreSQL en el camino de ejecucion de la aplicacion
-- ----------------------------------------------------------------------------
-- Script IDEMPOTENTE. Solo otorga privilegios; no crea ni borra roles (eso lo
-- hace fase34_seguridad_roles.sql, que sigue siendo la fuente de verdad del
-- modelo).
--
-- QUE CIERRA ESTA FASE
--   Hasta la F34 la aplicacion entera se conectaba como usr_admin_marathon.
--   El modelo de privilegios estaba construido y probado, pero solo distinguia
--   a la aplicacion del acceso directo por psql: NO distinguia un rol de otro.
--   Un operador de bodega llegaba a la base con privilegios de administrador y
--   lo unico que lo frenaba era SecurityConfig.
--
--   A partir de la F37 el backend abre UN POOL DE CONEXIONES POR ROL y elige el
--   pool segun el rol del usuario autenticado (RoleRoutingDataSource). Un
--   operador de bodega llega a la base como usr_bodega_marathon y queda sujeto
--   a los privilegios de rol_operador_bodega.
--
-- POR QUE HACE FALTA ESTE SCRIPT
--   Al enrutar por rol aparecio un privilegio que faltaba y que las pruebas de
--   la F34 no podian destapar, porque prueban sentencias sueltas y no el
--   trabajo real de la aplicacion: NINGUN rol operativo podia leer la tabla
--   usuario, y 16 servicios hacen usuarioRepository.findById(...) para
--   atribuir cada operacion a quien la ejecuta.
--
--   Es el mismo tipo de hallazgo que la seccion 6 de SEGURIDAD_ROLES.md (los
--   triggers corren con los privilegios de quien dispara la sentencia): un
--   privilegio que solo se revela cuando el rol hace el trabajo completo, no
--   una sentencia aislada.
-- ============================================================================

\echo '=== FASE 37: privilegios para la conexion por rol ==='

-- ============================================================================
-- 1. COMPROBACION PREVIA — los seis roles y sus seis usuarios deben existir
-- ============================================================================
-- Sin esto el script otorgaria privilegios a medias y fallaria mas adelante con
-- un error que no explica nada. fase34_seguridad_roles.sql debe haberse
-- ejecutado antes.
DO $$
DECLARE
    v_faltantes text;
BEGIN
    SELECT string_agg(r, ', ' ORDER BY r) INTO v_faltantes
    FROM unnest(ARRAY[
        'rol_administrador', 'rol_supervisor', 'rol_operador_bodega',
        'rol_operador_pedidos', 'rol_encargado_compras', 'rol_encargado_produccion',
        'usr_admin_marathon', 'usr_supervisor_marathon', 'usr_bodega_marathon',
        'usr_pedidos_marathon', 'usr_compras_marathon', 'usr_produccion_marathon'
    ]) AS r
    WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r);

    IF v_faltantes IS NOT NULL THEN
        RAISE EXCEPTION 'Faltan roles/usuarios: %. Ejecutar antes fase34_seguridad_roles.sql', v_faltantes;
    END IF;
END $$;

\echo '  [1/7] Los 6 roles y los 6 usuarios de login existen'

-- ============================================================================
-- 2. LECTURA DE LA TABLA usuario PARA LOS ROLES OPERATIVOS
-- ============================================================================
-- Los cuatro roles operativos reciben SELECT sobre usuario. NO reciben INSERT,
-- UPDATE ni DELETE: gestionar cuentas sigue siendo exclusivo del administrador.
--
-- POR QUE LA TABLA ENTERA Y NO SOLO LAS COLUMNAS NECESARIAS
--   La intencion era otorgar solo las columnas del flujo:
--       GRANT SELECT (id_usuario, nombre, apellido, correo, estado) ON usuario
--   que dejaria el hash de contrasena fuera del alcance de estos roles. No se
--   puede sin cambiar el mapeo de persistencia: la entidad Usuario mapea
--   password como columna normal, asi que Hibernate emite
--       SELECT id_usuario, nombre, apellido, correo, password, estado, ...
--   en CUALQUIER carga de la entidad, incluida la de los 36 puntos que solo
--   necesitan nombre y apellido para armar un DTO (listados de ordenes de
--   compra, ordenes de produccion, movimientos de inventario, pagos...).
--   Con privilegio por columna esos listados fallarian con SQLSTATE 42501.
--
--   La concesion esta acotada y es defendible: lo que queda expuesto es un
--   hash bcrypt, que esta disenado para resistir precisamente su exposicion, y
--   solo es legible por quien ya tenga la credencial de uno de estos usuarios
--   de base de datos. La aplicacion nunca lo incluye en ninguna respuesta.
--
--   MEJORA PENDIENTE: sacar password del mapeo de la entidad Usuario (campo
--   @Transient poblado por una consulta dedicada que solo use el pool de
--   autenticacion) permitiria volver al privilegio por columna. Toca el nucleo
--   de autenticacion, asi que no se hace en esta fase.
DO $$
DECLARE
    v_rol text;
BEGIN
    FOREACH v_rol IN ARRAY ARRAY[
        'rol_operador_bodega', 'rol_operador_pedidos',
        'rol_encargado_compras', 'rol_encargado_produccion'
    ] LOOP
        EXECUTE format('GRANT SELECT ON public.usuario TO %I', v_rol);
    END LOOP;
END $$;

\echo '  [2/7] SELECT sobre usuario otorgado a los 4 roles operativos'

-- ============================================================================
-- 3. VERIFICACION — que ningun rol operativo haya ganado mas de lo previsto
-- ============================================================================
-- Otorgar lectura sobre usuario no debe abrir la puerta a gestionar cuentas.
-- Se comprueba las dos mitades: que la lectura funciona y que la escritura
-- sigue cerrada. El script aborta si cualquiera de las dos falla, para que
-- nadie pueda dar por bueno un despliegue a medias.
DO $$
DECLARE
    v_rol   text;
    v_priv  text;
BEGIN
    FOREACH v_rol IN ARRAY ARRAY[
        'rol_operador_bodega', 'rol_operador_pedidos',
        'rol_encargado_compras', 'rol_encargado_produccion'
    ] LOOP
        IF NOT has_table_privilege(v_rol, 'public.usuario', 'SELECT') THEN
            RAISE EXCEPTION '% no puede leer la tabla usuario: la atribucion de operaciones fallara', v_rol;
        END IF;

        FOREACH v_priv IN ARRAY ARRAY['INSERT', 'UPDATE', 'DELETE', 'TRUNCATE'] LOOP
            IF has_table_privilege(v_rol, 'public.usuario', v_priv) THEN
                RAISE EXCEPTION '% tiene % sobre usuario: gestionar cuentas es exclusivo del administrador', v_rol, v_priv;
            END IF;
        END LOOP;
    END LOOP;

    -- El administrador conserva la gestion completa de cuentas.
    IF NOT has_table_privilege('rol_administrador', 'public.usuario', 'INSERT, UPDATE, DELETE') THEN
        RAISE EXCEPTION 'rol_administrador perdio la gestion de usuarios';
    END IF;

    -- El supervisor es de solo lectura: leer usuario si, tocarlo no.
    IF has_table_privilege('rol_supervisor', 'public.usuario', 'INSERT')
       OR has_table_privilege('rol_supervisor', 'public.usuario', 'UPDATE')
       OR has_table_privilege('rol_supervisor', 'public.usuario', 'DELETE') THEN
        RAISE EXCEPTION 'rol_supervisor puede escribir en usuario: deja de ser un rol de solo lectura';
    END IF;
END $$;

\echo '  [3/7] Verificado: lectura si, gestion de cuentas solo el administrador'

-- ============================================================================
-- 4. VERIFICACION — cada usuario de login llega a los privilegios de su rol
-- ============================================================================
-- El pool por rol se conecta con el usuario usr_*, no con el rol de grupo. Si
-- la membresia se rompiera, el pool arrancaria y fallaria en la primera
-- consulta. Se comprueba aqui, que es donde se puede explicar.
DO $$
DECLARE
    v_par record;
BEGIN
    FOR v_par IN
        SELECT * FROM (VALUES
            ('usr_admin_marathon',      'rol_administrador'),
            ('usr_supervisor_marathon', 'rol_supervisor'),
            ('usr_bodega_marathon',     'rol_operador_bodega'),
            ('usr_pedidos_marathon',    'rol_operador_pedidos'),
            ('usr_compras_marathon',    'rol_encargado_compras'),
            ('usr_produccion_marathon', 'rol_encargado_produccion')
        ) AS t(usuario, rol)
    LOOP
        IF NOT pg_has_role(v_par.usuario, v_par.rol, 'USAGE') THEN
            RAISE EXCEPTION 'El usuario % no hereda %: su pool no tendria ningun privilegio',
                v_par.usuario, v_par.rol;
        END IF;

        IF NOT (SELECT rolcanlogin FROM pg_roles WHERE rolname = v_par.usuario) THEN
            RAISE EXCEPTION 'El usuario % no puede iniciar sesion: su pool no podra conectarse', v_par.usuario;
        END IF;
    END LOOP;
END $$;

\echo '  [4/7] Verificado: los 6 usuarios de login heredan su rol y pueden conectarse'

-- ============================================================================
-- 5. HUECO DE LA F34: el modulo de DEVOLUCIONES DE CLIENTE (F24) solo tenia
--    privilegios de escritura para el administrador
-- ============================================================================
-- Al cruzar los GRANT con SecurityConfig aparecio una incoherencia que no era
-- una decision de mínimo privilegio, sino un olvido: la F34 dio a los roles
-- SELECT sobre solicitud_devolucion / solicitud_devolucion_detalle, pero no dio
-- a NADIE (salvo al administrador) con que escribirlas. SecurityConfig si
-- reparte esas acciones por rol, y de forma explicita:
--
--   POST /api/devoluciones                        -> Operador de Pedidos
--   PUT  /api/devoluciones/*/iniciar-inspeccion   -> Operador de Bodega
--   PUT  /api/devoluciones/*/inspeccionar         -> Operador de Bodega
--   POST /api/devoluciones/*/reembolso            -> Operador de Pedidos
--
-- El criterio para distinguir un hueco de una restriccion deliberada es ese:
-- si SecurityConfig NOMBRA al rol, la responsabilidad esta asignada a
-- proposito y lo que falta es el privilegio; si el endpoint solo pide
-- .authenticated(), lo que sobra es la laxitud del backend y se corrige alli.
-- Sin este bloque, "manda la base de datos" dejaria el modulo de devoluciones
-- utilizable unicamente por el administrador.

-- --- Operador de Pedidos: abre la solicitud y liquida el reembolso ---------
GRANT INSERT ON solicitud_devolucion, solicitud_devolucion_detalle TO rol_operador_pedidos;
GRANT INSERT, SELECT ON reembolso_cliente TO rol_operador_pedidos;

-- --- Operador de Bodega: inspecciona la mercancia que vuelve --------------
-- Lectura de la solicitud y escritura SOLO de lo que produce una inspeccion.
-- No puede cambiar a que pedido pertenece la devolucion, ni su motivo, ni
-- quien la registro: eso cambiaria la identidad del caso, no su resultado.
GRANT SELECT ON solicitud_devolucion, solicitud_devolucion_detalle TO rol_operador_bodega;
GRANT UPDATE (estado, fecha_inspeccion, id_usuario_inspector)
    ON solicitud_devolucion TO rol_operador_bodega;
GRANT UPDATE (resultado_inspeccion, observacion_inspeccion)
    ON solicitud_devolucion_detalle TO rol_operador_bodega;

-- --- Secuencias de las tablas que ahora se insertan ------------------------
-- Se resuelven con pg_get_serial_sequence y no por nombre: en esta base hay
-- secuencias con nombres parecidos (el caso de historial_inventario en la F34)
-- y solo una es la de la columna.
DO $$
DECLARE
    v_tabla text;
    v_col   text;
    v_seq   text;
BEGIN
    FOR v_tabla, v_col IN
        SELECT * FROM (VALUES
            ('solicitud_devolucion',          'id_solicitud'),
            ('solicitud_devolucion_detalle',  'id_detalle_sd'),
            ('reembolso_cliente',             'id_reembolso')
        ) AS t(tabla, col)
    LOOP
        v_seq := pg_get_serial_sequence('public.' || v_tabla, v_col);
        IF v_seq IS NULL THEN
            RAISE EXCEPTION 'No se encontro la secuencia de %.%: el INSERT fallaria con "permiso denegado a la secuencia"',
                v_tabla, v_col;
        END IF;
        EXECUTE format('GRANT USAGE ON SEQUENCE %s TO rol_operador_pedidos', v_seq);
    END LOOP;
END $$;

\echo '  [5/7] Modulo de devoluciones de cliente (F24) repartido entre pedidos y bodega'

-- ============================================================================
-- 6. VERIFICACION — el reparto de la F24 quedo como lo declara SecurityConfig
-- ============================================================================
-- Incluye las dos mitades: lo que cada rol GANA y lo que sigue sin poder hacer.
-- La separacion de funciones del flujo (quien devuelve no inspecciona, quien
-- inspecciona no reembolsa) es el objeto de la comprobacion.
DO $$
BEGIN
    -- Operador de Pedidos: abre y reembolsa, pero NO dictamina la inspeccion.
    IF NOT has_table_privilege('rol_operador_pedidos', 'public.solicitud_devolucion', 'INSERT') THEN
        RAISE EXCEPTION 'rol_operador_pedidos no puede registrar una devolucion';
    END IF;
    IF NOT has_table_privilege('rol_operador_pedidos', 'public.reembolso_cliente', 'INSERT') THEN
        RAISE EXCEPTION 'rol_operador_pedidos no puede emitir un reembolso';
    END IF;
    IF has_column_privilege('rol_operador_pedidos', 'public.solicitud_devolucion_detalle',
                            'resultado_inspeccion', 'UPDATE') THEN
        RAISE EXCEPTION 'rol_operador_pedidos puede dictaminar una inspeccion: quien registra la devolucion no la inspecciona';
    END IF;

    -- Operador de Bodega: inspecciona, pero NO abre el caso ni paga.
    IF NOT has_column_privilege('rol_operador_bodega', 'public.solicitud_devolucion_detalle',
                                'resultado_inspeccion', 'UPDATE') THEN
        RAISE EXCEPTION 'rol_operador_bodega no puede registrar el resultado de una inspeccion';
    END IF;
    IF has_table_privilege('rol_operador_bodega', 'public.solicitud_devolucion', 'INSERT') THEN
        RAISE EXCEPTION 'rol_operador_bodega puede abrir devoluciones: no es su funcion';
    END IF;
    IF has_table_privilege('rol_operador_bodega', 'public.reembolso_cliente', 'INSERT') THEN
        RAISE EXCEPTION 'rol_operador_bodega puede emitir reembolsos: quien inspecciona no paga';
    END IF;
    IF has_column_privilege('rol_operador_bodega', 'public.solicitud_devolucion', 'motivo', 'UPDATE') THEN
        RAISE EXCEPTION 'rol_operador_bodega puede reescribir el motivo de una devolucion: solo debe registrar su resultado';
    END IF;
END $$;

\echo '  [6/7] Verificado: quien registra la devolucion no la inspecciona, y quien la inspecciona no la reembolsa'

-- ============================================================================
-- 7. LO QUE DESTAPO EJERCITAR LA APLICACION REAL CON CADA ROL
-- ============================================================================
-- Los dos bloques anteriores salieron de leer el codigo. Este salio de
-- ejecutarlo: se hizo login con los seis usuarios de demostracion y se pidieron
-- once endpoints con cada uno. Tres respuestas fueron denegaciones de la BASE,
-- no del backend, y ninguna era intencionada.
--
-- Es la misma leccion de la seccion 6 de SEGURIDAD_ROLES.md llevada un paso mas
-- alla: no basta con probar sentencias sueltas ni con leer los servicios. Un
-- privilegio que falta se manifiesta cuando el rol arma una respuesta entera,
-- porque el que falta casi nunca es el de la tabla principal, sino el de alguna
-- tabla secundaria que el DTO necesita.

-- --- (a) El catalogo de productos consultaba la lista de materiales ---------
-- GET /api/productos devolvia 403 a Bodega, Pedidos y Compras:
--     permiso denegado a la tabla lista_materiales
-- ProductoService pone en cada producto una bandera tieneBom, y para eso hace
-- un existsBy... sobre lista_materiales. El catalogo de productos es
-- indispensable para esos tres roles (picking, alta de pedidos, compras), asi
-- que no se les puede cerrar.
--
-- Se otorgan SOLO las columnas de la comprobacion. La receta —que materia
-- prima lleva el producto y en que cantidad— sigue fuera de su alcance, que es
-- lo que de verdad hay que proteger aqui. Saber QUE un producto se fabrica no
-- es saber COMO se fabrica.
--
-- id_bom entra en la lista aunque el codigo no la pida: Hibernate resuelve un
-- existsBy proyectando la clave primaria, no con count(*). El SQL real es
--     select lm1_0.id_bom from lista_materiales lm1_0 ... fetch first 1 rows
-- Es la contrapartida del privilegio por columna: hay que otorgar las columnas
-- que emite el ORM, no las que aparecen en el codigo Java. Se comprobo mirando
-- la sentencia registrada, no suponiendola.
GRANT SELECT (id_bom, id_producto, estado) ON lista_materiales
    TO rol_operador_bodega, rol_operador_pedidos, rol_encargado_compras;

-- --- (b) El listado de devoluciones incluye el reembolso -------------------
-- GET /api/devoluciones devolvia 403 a Bodega:
--     permiso denegado a la tabla reembolso_cliente
-- Bodega tiene asignada la inspeccion de las devoluciones (seccion 5), asi que
-- necesita el listado para trabajar. El DTO trae el reembolso asociado.
-- Lectura si; emitirlo sigue siendo de Pedidos, como se verifica en la
-- seccion 6.
GRANT SELECT ON reembolso_cliente TO rol_operador_bodega;

-- --- (c) El tercer caso NO se resolvio otorgando ---------------------------
-- GET /api/devoluciones devolvia 403 a Compras:
--     permiso denegado a la tabla pedido
-- Aqui la base tenia razon y el backend no. Una devolucion de cliente pertenece
-- al circuito de venta, y Compras no ve pedidos ni clientes por decision de la
-- F34. Sin el pedido no puede siquiera armarse la respuesta. Se corrigio
-- quitando a Compras de ese endpoint en SecurityConfig, no ampliando el
-- privilegio: cuando las dos capas discrepan y la restrictiva es coherente, la
-- que cede es la aplicacion.

-- --- (d) Las devoluciones a proveedor llegan hasta la linea del pedido ------
-- GET /api/devoluciones-proveedor devolvia 403 a Compras:
--     permiso denegado a la tabla detalle_pedido
-- El modulo F25 es suyo, y una devolucion a proveedor nace de un articulo que
-- se declaro defectuoso al inspeccionar una devolucion de cliente. El modelo
-- enlaza ese articulo por solicitud_devolucion_detalle -> detalle_pedido, asi
-- que sin la linea del pedido no se sabe QUE producto se esta devolviendo.
--
-- Esto NO reabre el circuito de venta para Compras: sigue sin SELECT sobre
-- pedido y sobre cliente, de modo que ve la linea (producto, cantidad, precio)
-- pero no puede unirla a un pedido ni saber de que cliente era. Es lo que
-- necesita para su trabajo y nada mas.
GRANT SELECT ON detalle_pedido TO rol_encargado_compras;

DO $$
BEGIN
    IF has_table_privilege('rol_encargado_compras', 'public.pedido', 'SELECT')
       OR has_table_privilege('rol_encargado_compras', 'public.cliente', 'SELECT') THEN
        RAISE EXCEPTION 'rol_encargado_compras puede unir una linea de pedido con su cliente: la lectura del detalle debia quedar aislada del circuito de venta';
    END IF;
    IF NOT has_column_privilege('rol_operador_bodega', 'public.lista_materiales', 'id_producto', 'SELECT') THEN
        RAISE EXCEPTION 'rol_operador_bodega no puede resolver la bandera tieneBom: el catalogo de productos le dara 403';
    END IF;
    IF has_column_privilege('rol_operador_bodega', 'public.lista_materiales', 'cantidad_necesaria', 'SELECT') THEN
        RAISE EXCEPTION 'rol_operador_bodega puede leer la receta de fabricacion: solo debe saber si el producto tiene BOM';
    END IF;
    IF has_column_privilege('rol_operador_pedidos', 'public.lista_materiales', 'id_materia_prima', 'SELECT') THEN
        RAISE EXCEPTION 'rol_operador_pedidos puede leer que materia prima lleva un producto';
    END IF;
    IF NOT has_table_privilege('rol_operador_bodega', 'public.reembolso_cliente', 'SELECT') THEN
        RAISE EXCEPTION 'rol_operador_bodega no puede listar devoluciones: el DTO incluye el reembolso';
    END IF;
END $$;

\echo '  [7/7] Verificado: catalogo de productos y listado de devoluciones accesibles sin exponer la receta'

-- ============================================================================
-- NOTA SOBRE LAS CONTRASENAS
-- ============================================================================
-- Deliberadamente NO se escriben aqui. Cada usuario de login necesita una
-- contrasena conocida para que su pool pueda conectarse:
--     ALTER ROLE usr_bodega_marathon WITH PASSWORD '<clave>';
-- y esa clave se guarda en application-local.properties y en .env, que estan
-- los dos en .gitignore. Ninguna credencial entra al repositorio.
-- ============================================================================

\echo '=== FASE 37 COMPLETA ==='
