-- ============================================================================
-- FASE 40 · ETAPAS 2-3 — AUDITORIA DE CAMBIOS GENERICA
-- ----------------------------------------------------------------------------
-- Responde, para cualquier fila de las tablas criticas, a tres preguntas:
-- QUIEN la cambio, CUANDO, y QUE VALOR TENIA ANTES.
--
--     psql -U postgres -d mod_venta_inve -f fase40_auditoria_generica.sql
--
-- IDEMPOTENTE. Reejecutarlo no duplica triggers ni pierde filas ya auditadas.
--
-- QUE NO HACE
--   No toca historial_inventario ni su trigger: ese mecanismo funciona y es
--   evidencia de una fase cerrada. Este lo complementa para las cinco tablas
--   que no tenian auditoria de ningun tipo.
-- ============================================================================

\set ON_ERROR_STOP on
\timing on

\echo ''
\echo '=== FASE 40: auditoria de cambios generica ==='

DO $$
BEGIN
    IF current_user <> 'postgres' THEN
        RAISE EXCEPTION 'Ejecutar como postgres (actual: %): hace falta para SECURITY DEFINER y para los REVOKE', current_user;
    END IF;
END $$;

-- ============================================================================
-- 1. LA TABLA
-- ----------------------------------------------------------------------------
-- POR QUE DOS COLUMNAS DE USUARIO, Y POR QUE NO SON REDUNDANTES
--
--   usuario_bd  = session_user. Desde la F37 la aplicacion se conecta con una
--                 cuenta de PostgreSQL POR ROL, no por persona. Dice "un
--                 operador de bodega", no "cual".
--
--                 OJO: session_user, NO current_user. La funcion del trigger es
--                 SECURITY DEFINER, asi que dentro de ella current_user vale
--                 'postgres' (el dueno) y registrarlo seria inutil: todas las
--                 filas dirian lo mismo. session_user conserva la cuenta con la
--                 que se abrio la conexion, que es la que identifica al rol.
--                 Se detecto en la prueba funcional de la F40: las primeras
--                 filas se grabaron con usuario_bd='postgres'.
--   usuario_app = current_setting('app.current_user_id'). Dice la persona, pero
--                 solo si la aplicacion se molesto en fijarlo.
--
-- Ninguna de las dos responde sola a "quien". Juntas si, y ademas su
-- combinacion es informativa: usuario_app NULL con usuario_bd presente
-- significa UN CAMBIO HECHO FUERA DE LA APLICACION —por psql, por un script de
-- mantenimiento o por alguien con la credencial—, que es justo el caso que mas
-- interesa a una auditoria.
--
-- txid permite agrupar todos los cambios de una misma transaccion: un UPDATE
-- que toca cinco campos genera cinco filas, y sin el txid no habria forma de
-- saber que fueron el mismo acto.
-- ============================================================================
CREATE TABLE IF NOT EXISTS auditoria_cambios (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tabla           varchar(63)  NOT NULL,
    pk_valor        varchar(100) NOT NULL,
    operacion       varchar(10)  NOT NULL,
    campo           varchar(63),
    valor_anterior  text,
    valor_nuevo     text,
    usuario_bd      varchar(63)  NOT NULL,
    usuario_app     integer,
    fecha           timestamp    NOT NULL DEFAULT now(),
    txid            bigint       NOT NULL,
    CONSTRAINT chk_auditoria_operacion
        CHECK (operacion IN ('INSERT','UPDATE','DELETE'))
);

COMMENT ON TABLE auditoria_cambios IS
  'F40. Auditoria de cambios campo a campo. APPEND-ONLY: nadie salvo postgres puede modificarla o borrarla, ni siquiera rol_administrador.';
COMMENT ON COLUMN auditoria_cambios.usuario_bd IS
  'Cuenta de PostgreSQL (current_user). Identifica el ROL, no la persona.';
COMMENT ON COLUMN auditoria_cambios.usuario_app IS
  'Usuario de aplicacion (app.current_user_id). NULL = cambio hecho fuera de la aplicacion.';
COMMENT ON COLUMN auditoria_cambios.txid IS
  'Agrupa los cambios de una misma transaccion.';

-- ============================================================================
-- 2. INDICES — para las tres consultas reales de una auditoria
-- ============================================================================
-- "que le paso a esta fila"
CREATE INDEX IF NOT EXISTS idx_auditoria_tabla_pk ON auditoria_cambios (tabla, pk_valor);
-- "que paso entre estas dos fechas"
CREATE INDEX IF NOT EXISTS idx_auditoria_fecha    ON auditoria_cambios (fecha DESC);
-- "que hizo esta persona"
CREATE INDEX IF NOT EXISTS idx_auditoria_usuario  ON auditoria_cambios (usuario_app)
    WHERE usuario_app IS NOT NULL;

-- ============================================================================
-- 3. PRIVILEGIOS — append-only, a prueba del propio auditado
-- ----------------------------------------------------------------------------
-- Una bitacora que el auditado puede editar no es una bitacora. rol_administrador
-- puede LEER su propia auditoria; no puede reescribirla. Solo postgres, que es
-- el dueno, puede purgar.
--
-- Los seis roles necesitan GENERAR filas sin tener INSERT: eso lo resuelve el
-- SECURITY DEFINER de la funcion del trigger (seccion 4), no un GRANT.
-- ============================================================================
REVOKE ALL ON auditoria_cambios FROM PUBLIC;
DO $$
DECLARE v_rol text;
BEGIN
    FOREACH v_rol IN ARRAY ARRAY['rol_administrador','rol_supervisor','rol_operador_bodega',
                                 'rol_operador_pedidos','rol_encargado_compras','rol_encargado_produccion']
    LOOP
        EXECUTE format('REVOKE ALL ON auditoria_cambios FROM %I', v_rol);
    END LOOP;

    -- Solo lectura, y solo para quien audita.
    EXECUTE 'GRANT SELECT ON auditoria_cambios TO rol_administrador';
    EXECUTE 'GRANT SELECT ON auditoria_cambios TO rol_supervisor';
    -- Los cuatro roles operativos no leen la auditoria: generan filas a traves
    -- del trigger, pero no ven lo que hicieron los demas.
END $$;

-- La secuencia de la IDENTITY no se otorga a nadie a proposito: nadie inserta
-- directamente, solo la funcion SECURITY DEFINER, que corre como postgres.

-- ============================================================================
-- 4. LA FUNCION DE TRIGGER — una sola, generica
-- ----------------------------------------------------------------------------
-- SECURITY DEFINER: corre con los privilegios de postgres (su dueno), asi que
-- puede insertar en auditoria_cambios aunque el rol que dispara la sentencia no
-- tenga INSERT. Es lo que permite que la tabla sea append-only para todos y aun
-- asi se llene.
--
-- search_path fijado DENTRO de la funcion: sin eso, un SECURITY DEFINER es una
-- via de escalada de privilegios clasica (quien pueda crear un esquema en el
-- search_path del llamante podria suplantar 'auditoria_cambios').
--
-- REGISTRA UNA FILA POR CAMPO QUE CAMBIO, no un volcado de la fila entera: una
-- auditoria en la que hay que diffear dos JSON a mano para saber que se toco no
-- responde "que valor tenia antes" sin trabajo extra.
-- ============================================================================
CREATE OR REPLACE FUNCTION fn_auditoria_cambios()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $function$
DECLARE
    v_old      jsonb;
    v_new      jsonb;
    v_pk_col   text;
    v_pk_val   text;
    v_campo    text;
    v_ant      text;
    v_nue      text;
    v_usuario  integer;
    -- Columnas que NO se auditan: si se registra updated_at en cada UPDATE, la
    -- bitacora se llena de ruido que no dice nada.
    v_ignorar  text[] := ARRAY['updated_at','created_at','fecha_actualizacion'];
    -- Columnas cuyo VALOR nunca se escribe. Un hash BCrypt en la bitacora es
    -- material para un ataque offline: se registra QUE cambio, no a que.
    v_ocultar  text[] := ARRAY['password','contrasena','contrasena_hash'];
BEGIN
    -- Salvaguarda: la tabla de auditoria no se audita a si misma.
    IF TG_TABLE_NAME = 'auditoria_cambios' THEN
        RETURN NULL;
    END IF;

    BEGIN
        v_usuario := current_setting('app.current_user_id', true)::integer;
    EXCEPTION WHEN others THEN
        v_usuario := NULL;
    END;

    -- PK del catalogo, no escrita a mano: la funcion es generica.
    SELECT a.attname INTO v_pk_col
    FROM pg_constraint c
    JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
    WHERE c.contype = 'p' AND c.conrelid = TG_RELID;

    IF TG_OP = 'DELETE' THEN
        v_old := to_jsonb(OLD);
        v_pk_val := v_old ->> v_pk_col;
        INSERT INTO auditoria_cambios (tabla, pk_valor, operacion, campo,
                                       valor_anterior, valor_nuevo,
                                       usuario_bd, usuario_app, txid)
        VALUES (TG_TABLE_NAME, v_pk_val, 'DELETE', NULL,
                -- en el borrado se guarda la fila entera, sin los campos ocultos
                (SELECT jsonb_object_agg(k, CASE WHEN k = ANY(v_ocultar) THEN '***' ELSE v END)::text
                   FROM jsonb_each_text(v_old) AS e(k, v)),
                NULL, current_user, v_usuario, txid_current());
        RETURN OLD;
    END IF;

    IF TG_OP = 'INSERT' THEN
        v_new := to_jsonb(NEW);
        v_pk_val := v_new ->> v_pk_col;
        INSERT INTO auditoria_cambios (tabla, pk_valor, operacion, campo,
                                       valor_anterior, valor_nuevo,
                                       usuario_bd, usuario_app, txid)
        VALUES (TG_TABLE_NAME, v_pk_val, 'INSERT', NULL, NULL,
                (SELECT jsonb_object_agg(k, CASE WHEN k = ANY(v_ocultar) THEN '***' ELSE v END)::text
                   FROM jsonb_each_text(v_new) AS e(k, v)),
                session_user, v_usuario, txid_current());
        RETURN NEW;
    END IF;

    -- UPDATE: una fila por cada campo que efectivamente cambio
    v_old := to_jsonb(OLD);
    v_new := to_jsonb(NEW);
    v_pk_val := v_new ->> v_pk_col;

    FOR v_campo IN SELECT jsonb_object_keys(v_new) LOOP
        CONTINUE WHEN v_campo = ANY(v_ignorar);

        v_ant := v_old ->> v_campo;
        v_nue := v_new ->> v_campo;

        -- IS DISTINCT FROM para que un cambio de NULL a valor cuente
        CONTINUE WHEN v_ant IS NOT DISTINCT FROM v_nue;

        IF v_campo = ANY(v_ocultar) THEN
            v_ant := '***';
            v_nue := '*** (modificado)';
        END IF;

        INSERT INTO auditoria_cambios (tabla, pk_valor, operacion, campo,
                                       valor_anterior, valor_nuevo,
                                       usuario_bd, usuario_app, txid)
        VALUES (TG_TABLE_NAME, v_pk_val, 'UPDATE', v_campo, v_ant, v_nue,
                session_user, v_usuario, txid_current());
    END LOOP;

    RETURN NEW;
END;
$function$;

ALTER FUNCTION fn_auditoria_cambios() OWNER TO postgres;
REVOKE ALL ON FUNCTION fn_auditoria_cambios() FROM PUBLIC;

-- ============================================================================
-- 5. LOS TRIGGERS — cinco tablas que no tenian auditoria de ningun tipo
-- ============================================================================
DO $$
DECLARE v_tabla text;
BEGIN
    FOREACH v_tabla IN ARRAY ARRAY['usuario','rol_permiso','producto','cliente','proveedor']
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_auditoria_%s ON %I', v_tabla, v_tabla);
        EXECUTE format(
            'CREATE TRIGGER trg_auditoria_%s AFTER INSERT OR UPDATE OR DELETE ON %I
             FOR EACH ROW EXECUTE FUNCTION fn_auditoria_cambios()', v_tabla, v_tabla);
        RAISE NOTICE 'trigger de auditoria instalado en %', v_tabla;
    END LOOP;
END $$;

-- ============================================================================
-- 6. VERIFICACIONES
-- ============================================================================
DO $$
DECLARE v_n int; v_apagados text; v_audit int;
BEGIN
    SELECT count(*) INTO v_n FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
    JOIN pg_namespace n ON n.oid=c.relnamespace WHERE NOT t.tgisinternal AND n.nspname='public';

    SELECT string_agg(c.relname||'.'||t.tgname, ', ') INTO v_apagados
    FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
    JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE NOT t.tgisinternal AND n.nspname='public' AND t.tgenabled<>'O';
    IF v_apagados IS NOT NULL THEN
        RAISE EXCEPTION 'Triggers apagados: %', v_apagados;
    END IF;

    -- La tabla de auditoria no puede tener trigger de auditoria (recursion)
    SELECT count(*) INTO v_audit FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
    WHERE NOT t.tgisinternal AND c.relname='auditoria_cambios';
    IF v_audit > 0 THEN
        RAISE EXCEPTION 'auditoria_cambios tiene triggers: hay riesgo de recursion';
    END IF;

    RAISE NOTICE 'Triggers totales: %, todos en tgenabled=O, sin recursion', v_n;
END $$;

-- Que el administrador NO pueda alterar la bitacora
DO $$
DECLARE p text;
BEGIN
    FOREACH p IN ARRAY ARRAY['UPDATE','DELETE','TRUNCATE'] LOOP
        IF has_table_privilege('rol_administrador', 'public.auditoria_cambios', p) THEN
            RAISE EXCEPTION 'rol_administrador tiene % sobre auditoria_cambios: la bitacora seria alterable por el auditado', p;
        END IF;
    END LOOP;
    IF NOT has_table_privilege('rol_administrador', 'public.auditoria_cambios', 'SELECT') THEN
        RAISE EXCEPTION 'rol_administrador no puede leer la auditoria';
    END IF;
    IF has_table_privilege('rol_operador_bodega', 'public.auditoria_cambios', 'SELECT') THEN
        RAISE EXCEPTION 'rol_operador_bodega puede leer la auditoria: no le corresponde';
    END IF;
    RAISE NOTICE 'Privilegios verificados: append-only incluso para rol_administrador';
END $$;

ANALYZE auditoria_cambios;

\echo ''
\echo '=== AUDITORIA GENERICA INSTALADA ==='
