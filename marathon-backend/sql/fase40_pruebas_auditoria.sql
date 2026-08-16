-- ============================================================================
-- FASE 40 — PRUEBAS DE LA AUDITORIA DE CAMBIOS
-- ----------------------------------------------------------------------------
-- Mismo formato de conteo X/N que fase34_pruebas_roles.sql.
--
--     psql -U postgres -d mod_venta_inve -f fase40_pruebas_auditoria.sql
--
-- NO MODIFICA DATOS. Hay DOS mecanismos, y hacen falta los dos:
--
--   1. Las pruebas de privilegios corren dentro de pg_temp.probar, que abre una
--      subtransaccion y la revierte SIEMPRE, incluso cuando la operacion tiene
--      exito (se fuerza un error centinela). Igual que el arnes de la F34.
--
--   2. El script entero va dentro de una transaccion explicita: BEGIN aqui
--      abajo, ROLLBACK en la ultima linea.
--
-- POR QUE HIZO FALTA EL SEGUNDO (corregido en la F41).
-- El archivo terminaba en ROLLBACK pero NO abria transaccion, y psql trabaja en
-- autocommit: cada sentencia de nivel superior —incluido cada bloque DO— se
-- confirmaba sola, asi que el ROLLBACK final no tenia nada que revertir. La
-- salvaguarda (1) cubre las pruebas de privilegios, pero NO los bloques DO que
-- hacen cambios reales para comprobar que la auditoria los registra.
--
-- Efecto medido, POR CADA EJECUCION del arnes:
--   - usuario #1 (el administrador) quedaba con estado='inactivo' y no podia
--     iniciar sesion;
--   - su contrasena quedaba en 61 caracteres terminados en 'AAA' (un hash
--     BCrypt son exactamente 60), con lo que el login era irrecuperable;
--   - se borraba una fila de rol_permiso, una distinta en cada corrida.
--
-- Se detecto en la F41 porque el login de administrador fallaba en la prueba
-- funcional, y se reparo con los datos que la propia auditoria_cambios habia
-- registrado. Detalle en CIFRADO.md, seccion 10.
--
-- NOTA: si ademas se lanza con `psql -1`, psql avisa de que ya hay una
-- transaccion en curso. Es inofensivo y redundante: con el BEGIN de abajo, el
-- -1 ya no hace falta.
-- ============================================================================

\set ON_ERROR_STOP on
\pset pager off

-- Todo el arnes en una sola transaccion. Sin este BEGIN, el ROLLBACK del final
-- es decorativo.
BEGIN;

\echo ''
\echo '=== FASE 40: pruebas de la auditoria de cambios ==='

DROP TABLE IF EXISTS _res;
CREATE TEMP TABLE _res (n serial, descripcion text, esperado text, obtenido text, pasa text);

-- ============================================================================
-- Utilidad: ejecuta una sentencia asumiendo un rol y revierte SIEMPRE
-- ============================================================================
CREATE OR REPLACE FUNCTION pg_temp.probar(p_rol text, p_desc text, p_sql text, p_esperado text)
RETURNS void AS $$
DECLARE v_obt text;
BEGIN
    BEGIN
        EXECUTE format('SET LOCAL ROLE %I', p_rol);
        EXECUTE p_sql;
        RAISE EXCEPTION 'CENTINELA';          -- fuerza el rollback aunque haya funcionado
    EXCEPTION
        WHEN insufficient_privilege THEN v_obt := 'DENEGADO_PRIVILEGIO';
        WHEN others THEN
            IF SQLERRM = 'CENTINELA' THEN v_obt := 'PERMITIDO';
            ELSE v_obt := 'DENEGADO_REGLA_BD';
            END IF;
    END;
    RESET ROLE;
    INSERT INTO _res (descripcion, esperado, obtenido, pasa)
    VALUES (p_desc, p_esperado, v_obt, CASE WHEN v_obt = p_esperado THEN 'PASA' ELSE 'FALLA' END);
END;
$$ LANGUAGE plpgsql;

-- Utilidad: comprueba una condicion booleana
CREATE OR REPLACE FUNCTION pg_temp.afirmar(p_desc text, p_cond boolean)
RETURNS void AS $$
BEGIN
    INSERT INTO _res (descripcion, esperado, obtenido, pasa)
    VALUES (p_desc, 'true', p_cond::text, CASE WHEN p_cond THEN 'PASA' ELSE 'FALLA' END);
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- 1. INMUTABILIDAD — la bitacora no la altera ni el auditado
-- ----------------------------------------------------------------------------
-- Una bitacora que el administrador puede editar no es una bitacora.
-- ============================================================================
SELECT pg_temp.probar('rol_administrador', 'ADMIN no puede UPDATE sobre auditoria_cambios',
    'UPDATE auditoria_cambios SET valor_nuevo=''falsificado'' WHERE id=(SELECT min(id) FROM auditoria_cambios)',
    'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_administrador', 'ADMIN no puede DELETE sobre auditoria_cambios',
    'DELETE FROM auditoria_cambios WHERE id=(SELECT min(id) FROM auditoria_cambios)',
    'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_administrador', 'ADMIN no puede TRUNCATE auditoria_cambios',
    'TRUNCATE auditoria_cambios', 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_administrador', 'ADMIN no puede INSERT directo en auditoria_cambios',
    'INSERT INTO auditoria_cambios (tabla,pk_valor,operacion,usuario_bd,txid) VALUES (''x'',''1'',''INSERT'',''x'',1)',
    'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_administrador', 'ADMIN si puede LEER la auditoria',
    'SELECT count(*) FROM auditoria_cambios', 'PERMITIDO');
SELECT pg_temp.probar('rol_supervisor', 'SUPERVISOR si puede LEER la auditoria',
    'SELECT count(*) FROM auditoria_cambios', 'PERMITIDO');
SELECT pg_temp.probar('rol_operador_bodega', 'BODEGA no puede leer la auditoria',
    'SELECT count(*) FROM auditoria_cambios', 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_operador_pedidos', 'PEDIDOS no puede leer la auditoria',
    'SELECT count(*) FROM auditoria_cambios', 'DENEGADO_PRIVILEGIO');

-- ============================================================================
-- 2. EL TRIGGER GENERA FILAS AUNQUE EL ROL NO TENGA INSERT
-- ----------------------------------------------------------------------------
-- Es el papel del SECURITY DEFINER: los seis roles generan auditoria sin poder
-- escribir en la tabla directamente (lo que se acaba de comprobar arriba).
-- ============================================================================
DO $$
DECLARE v_antes bigint; v_despues bigint; v_id int;
BEGIN
    SELECT count(*) INTO v_antes FROM auditoria_cambios;
    SELECT min(id_producto) INTO v_id FROM producto;

    PERFORM set_config('app.current_user_id', '1', true);
    UPDATE producto SET precio = precio + 1 WHERE id_producto = v_id;
    SELECT count(*) INTO v_despues FROM auditoria_cambios;
    PERFORM pg_temp.afirmar('Un UPDATE en producto genera fila de auditoria', v_despues > v_antes);

    PERFORM pg_temp.afirmar('La fila registra el campo que cambio (precio)',
        EXISTS (SELECT 1 FROM auditoria_cambios WHERE tabla='producto'
                  AND pk_valor=v_id::text AND campo='precio' AND id > v_antes));
    PERFORM pg_temp.afirmar('La fila registra el valor anterior',
        EXISTS (SELECT 1 FROM auditoria_cambios WHERE tabla='producto'
                  AND campo='precio' AND valor_anterior IS NOT NULL AND id > v_antes));
    PERFORM pg_temp.afirmar('La fila captura usuario_app desde app.current_user_id',
        EXISTS (SELECT 1 FROM auditoria_cambios WHERE id > v_antes AND usuario_app = 1));
    PERFORM pg_temp.afirmar('La fila captura usuario_bd (session_user, no postgres por SECURITY DEFINER)',
        EXISTS (SELECT 1 FROM auditoria_cambios WHERE id > v_antes AND usuario_bd = session_user));
    PERFORM pg_temp.afirmar('La fila agrupa por txid',
        EXISTS (SELECT 1 FROM auditoria_cambios WHERE id > v_antes AND txid = txid_current()));
END $$;

-- ============================================================================
-- 3. RUIDO Y SECRETOS — que se registra y que NO
-- ============================================================================
DO $$
DECLARE v_antes bigint; v_id int;
BEGIN
    SELECT count(*) INTO v_antes FROM auditoria_cambios;
    SELECT min(id_usuario) INTO v_id FROM usuario;

    -- updated_at NO debe auditarse: llenaria la bitacora de nada
    UPDATE usuario SET updated_at = now() WHERE id_usuario = v_id;
    PERFORM pg_temp.afirmar('updated_at NO genera fila de auditoria',
        NOT EXISTS (SELECT 1 FROM auditoria_cambios WHERE id > v_antes AND campo = 'updated_at'));

    -- El hash de la contrasena NUNCA se escribe en claro
    SELECT count(*) INTO v_antes FROM auditoria_cambios;
    UPDATE usuario SET password = '$2a$10$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'
    WHERE id_usuario = v_id;
    PERFORM pg_temp.afirmar('Un cambio de password SI se registra',
        EXISTS (SELECT 1 FROM auditoria_cambios WHERE id > v_antes AND campo = 'password'));
    PERFORM pg_temp.afirmar('...pero el hash NUNCA aparece en la bitacora',
        NOT EXISTS (SELECT 1 FROM auditoria_cambios
                    WHERE id > v_antes AND (valor_anterior LIKE '$2a$%' OR valor_nuevo LIKE '$2a$%')));
END $$;

-- ============================================================================
-- 4. LAS CINCO TABLAS CRITICAS ESTAN CUBIERTAS
-- ----------------------------------------------------------------------------
-- usuario y rol_permiso se comprueban aqui por SQL y no por la API porque la
-- ruta de la aplicacion esta bloqueada por un defecto PREEXISTENTE (RolService
-- y UsuarioService borran y reinsertan sus filas hijas sin flush intermedio y
-- violan uq_rol_permiso / uq_usuario_rol). Ese defecto es anterior a esta fase
-- y queda anotado; no impide comprobar que la auditoria funciona.
-- ============================================================================
DO $$
DECLARE t text; v_ok boolean;
BEGIN
    FOREACH t IN ARRAY ARRAY['usuario','rol_permiso','producto','cliente','proveedor'] LOOP
        SELECT EXISTS (SELECT 1 FROM pg_trigger tg JOIN pg_class c ON c.oid=tg.tgrelid
                       WHERE NOT tg.tgisinternal AND c.relname=t
                         AND tg.tgname='trg_auditoria_'||t AND tg.tgenabled='O')
          INTO v_ok;
        PERFORM pg_temp.afirmar(format('Tabla %s tiene su trigger de auditoria activo', t), v_ok);
    END LOOP;
END $$;

-- Cambios reales sobre usuario y rol_permiso, revertidos
DO $$
DECLARE v_antes bigint; v_id int; v_rp int;
BEGIN
    SELECT count(*) INTO v_antes FROM auditoria_cambios;
    SELECT min(id_usuario) INTO v_id FROM usuario;
    PERFORM set_config('app.current_user_id', '1', true);

    UPDATE usuario SET estado = 'inactivo' WHERE id_usuario = v_id;
    PERFORM pg_temp.afirmar('Desactivar un usuario deja rastro con valor anterior',
        EXISTS (SELECT 1 FROM auditoria_cambios WHERE id > v_antes AND tabla='usuario'
                  AND campo='estado' AND valor_anterior='activo' AND valor_nuevo='inactivo'));

    SELECT count(*) INTO v_antes FROM auditoria_cambios;
    SELECT min(id_rol_permiso) INTO v_rp FROM rol_permiso;
    DELETE FROM rol_permiso WHERE id_rol_permiso = v_rp;
    PERFORM pg_temp.afirmar('Quitar un permiso a un rol deja rastro',
        EXISTS (SELECT 1 FROM auditoria_cambios WHERE id > v_antes AND tabla='rol_permiso'
                  AND operacion='DELETE'));
END $$;

-- ============================================================================
-- 5. NO HAY RECURSION NI AUTO-AUDITORIA
-- ============================================================================
DO $$
BEGIN
    PERFORM pg_temp.afirmar('auditoria_cambios no se audita a si misma',
        NOT EXISTS (SELECT 1 FROM pg_trigger tg JOIN pg_class c ON c.oid=tg.tgrelid
                    WHERE NOT tg.tgisinternal AND c.relname='auditoria_cambios'));
    PERFORM pg_temp.afirmar('Los indices de consulta de auditoria existen',
        (SELECT count(*) FROM pg_indexes WHERE tablename='auditoria_cambios'
           AND indexname IN ('idx_auditoria_tabla_pk','idx_auditoria_fecha','idx_auditoria_usuario')) = 3);
    PERFORM pg_temp.afirmar('La funcion del trigger es SECURITY DEFINER',
        (SELECT prosecdef FROM pg_proc WHERE proname='fn_auditoria_cambios'));
    PERFORM pg_temp.afirmar('La funcion fija su search_path (evita escalada por SECURITY DEFINER)',
        (SELECT proconfig::text LIKE '%search_path%' FROM pg_proc WHERE proname='fn_auditoria_cambios'));
    PERFORM pg_temp.afirmar('Todos los triggers del esquema estan activos',
        NOT EXISTS (SELECT 1 FROM pg_trigger tg JOIN pg_class c ON c.oid=tg.tgrelid
                    JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE NOT tg.tgisinternal AND n.nspname='public' AND tg.tgenabled <> 'O'));
END $$;

-- ============================================================================
-- RESULTADOS
-- ============================================================================
\echo ''
SELECT n, descripcion, esperado, obtenido, pasa FROM _res ORDER BY n;

\echo ''
\echo '--- Pruebas que no cumplieron la expectativa (debe estar vacio) ---'
SELECT n, descripcion, esperado, obtenido FROM _res WHERE pasa='FALLA' ORDER BY n;

DO $$
DECLARE v_total int; v_pasan int;
BEGIN
    SELECT count(*), count(*) FILTER (WHERE pasa='PASA') INTO v_total, v_pasan FROM _res;
    RAISE NOTICE '===== % de % pruebas de auditoria PASAN =====', v_pasan, v_total;
    IF v_pasan <> v_total THEN
        RAISE EXCEPTION 'Hay % pruebas de auditoria que fallan', v_total - v_pasan;
    END IF;
END $$;

-- Revierte la transaccion abierta con el BEGIN de la cabecera: las filas de
-- auditoria generadas por las pruebas, el usuario desactivado y la fila de
-- rol_permiso borrada vuelven a su sitio.
--
-- Si alguna prueba fallo, el RAISE de arriba ya aborto la transaccion y psql
-- salio por ON_ERROR_STOP; la reversion ocurre igual al cerrar la conexion.
ROLLBACK;
