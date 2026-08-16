-- ============================================================================
-- FASE 41 · ETAPAS 2-5 — CIFRADO DE DATOS PERSONALES
-- ----------------------------------------------------------------------------
-- Cifra los datos de contacto de cliente y proveedor con pgcrypto
-- (pgp_sym_encrypt), migra los ~5.400 valores existentes, VERIFICA el
-- descifrado del 100 % antes de borrar nada, y cierra las vias por las que el
-- dato cifrado podria volver a aparecer en claro.
--
-- LA CLAVE NO ESTA EN ESTE ARCHIVO Y NO PUEDE ESTARLO. Se recoge de la
-- variable de entorno del PROCESO con \getenv, que la pone ahi
-- scripts\cifrado\gestionar_clave.ps1 tras descifrar el blob DPAPI. Este script
-- NO se ejecuta con psql -f a pelo; se ejecuta asi:
--
--   powershell -File scripts\cifrado\gestionar_clave.ps1 -Accion Ejecutar `
--              -Script marathon-backend\sql\fase41_cifrado.sql
--
-- ES IDEMPOTENTE: se puede volver a correr. Las filas ya cifradas se saltan y
-- las columnas ya creadas no se recrean.
-- ============================================================================

\timing off
\pset pager off
\set ON_ERROR_STOP on

-- La clave entra por variable de entorno del proceso, NUNCA por -v ni por la
-- linea de comandos: los argumentos de un proceso los ve cualquiera que liste
-- procesos; el entorno de un proceso ajeno, no.
\getenv clave MARATHON_CRYPTO_KEY
SELECT set_config('app.crypto_key', :'clave', false) IS NOT NULL AS clave_publicada_en_la_sesion;

\echo ''
\echo '############ ETAPA 2 — pgcrypto y funciones de cifrado #############'

CREATE EXTENSION IF NOT EXISTS pgcrypto;

SELECT extname, extversion FROM pg_extension WHERE extname = 'pgcrypto';


-- ----------------------------------------------------------------------------
-- Funciones de apoyo
--
-- NO son SECURITY DEFINER, a diferencia de la de auditoria de la F40, y la
-- diferencia es deliberada: aqui la capacidad de descifrar debe depender de que
-- el llamante tenga la clave en SU sesion, no de quien sea el dueno de la
-- funcion. Con SECURITY DEFINER cualquier rol con SELECT sobre cliente podria
-- descifrar sin conocer la clave, y el cifrado no protegeria de nada.
--
-- LANGUAGE sql y no plpgsql: fn_descifrar se evalua una vez POR FILA en cada
-- listado. Una funcion plpgsql con bloque EXCEPTION abre una subtransaccion en
-- cada llamada, y sobre 5.000 filas eso se nota. En SQL puro el planificador
-- ademas puede alinearla.
-- ----------------------------------------------------------------------------

-- Clave de la sesion, o NULL si no se ha publicado.
CREATE OR REPLACE FUNCTION fn_clave_cifrado() RETURNS text
LANGUAGE sql STABLE PARALLEL SAFE AS
$$ SELECT nullif(current_setting('app.crypto_key', true), '') $$;

-- Cifrar. Sin clave FALLA, y falla a proposito: si devolviera NULL en silencio
-- se guardarian filas vacias creyendo haber cifrado.
CREATE OR REPLACE FUNCTION fn_cifrar(p_valor text) RETURNS bytea
LANGUAGE plpgsql STABLE AS $$
DECLARE v_clave text := fn_clave_cifrado();
BEGIN
    IF p_valor IS NULL THEN RETURN NULL; END IF;
    IF v_clave IS NULL THEN
        RAISE EXCEPTION 'app.crypto_key no esta fijada en la sesion: no se puede cifrar'
            USING HINT = 'La aplicacion la publica al tomar la conexion; por psql, usar gestionar_clave.ps1 -Accion Ejecutar';
    END IF;
    RETURN pgp_sym_encrypt(p_valor, v_clave);
END $$;

-- Descifrar. Sin clave devuelve NULL, no falla: es justo lo que hace que una
-- consulta por psql sin la clave vea el dato ilegible en lugar de romperse.
CREATE OR REPLACE FUNCTION fn_descifrar(p_valor bytea) RETURNS text
LANGUAGE sql STABLE PARALLEL SAFE AS $$
    SELECT CASE
             WHEN p_valor IS NULL THEN NULL
             WHEN fn_clave_cifrado() IS NULL THEN NULL
             ELSE pgp_sym_decrypt(p_valor, fn_clave_cifrado())
           END
$$;

-- Hash DETERMINISTA del correo.
--
-- POR QUE HACE FALTA: pgp_sym_encrypt lleva sal e IV aleatorios, asi que cifrar
-- dos veces el mismo correo da dos bytea distintos. Eso es exactamente lo que
-- se quiere de un cifrado — y exactamente lo que rompe el UNIQUE(correo) que
-- cliente tenia desde el principio. Sin una columna determinista, dos clientes
-- podrian registrarse con el mismo correo.
--
-- HMAC y no un digest a secas: sha256('juan@correo.com') es una constante
-- publica; con un diccionario de correos se invierte en segundos. Con HMAC hace
-- falta la clave para calcular el hash, asi que quien robe la tabla no puede ni
-- confirmar si un correo concreto esta en ella.
CREATE OR REPLACE FUNCTION fn_hash_correo(p_valor text) RETURNS bytea
LANGUAGE plpgsql STABLE AS $$
DECLARE v_clave text := fn_clave_cifrado();
BEGIN
    IF p_valor IS NULL OR btrim(p_valor) = '' THEN RETURN NULL; END IF;
    IF v_clave IS NULL THEN
        RAISE EXCEPTION 'app.crypto_key no esta fijada en la sesion: no se puede calcular el hash';
    END IF;
    -- lower+btrim: 'Juan@X.com' y 'juan@x.com ' son el mismo correo para un
    -- humano, y el UNIQUE original no lo distinguia porque comparaba texto tal
    -- cual. Aqui se normaliza, que es mas estricto, no menos.
    RETURN hmac(lower(btrim(p_valor)), v_clave, 'sha256');
END $$;


\echo ''
\echo '############ ETAPA 4 — columnas nuevas #############################'

-- Columnas cifradas. Nullable: los datos aun no estan migrados.
ALTER TABLE cliente
    ADD COLUMN IF NOT EXISTS correo_enc    bytea,
    ADD COLUMN IF NOT EXISTS correo_hash   bytea,
    ADD COLUMN IF NOT EXISTS telefono_enc  bytea,
    ADD COLUMN IF NOT EXISTS direccion_enc bytea;

ALTER TABLE proveedor
    ADD COLUMN IF NOT EXISTS correo_enc    bytea,
    ADD COLUMN IF NOT EXISTS telefono_enc  bytea,
    ADD COLUMN IF NOT EXISTS direccion_enc bytea,
    ADD COLUMN IF NOT EXISTS contacto_enc  bytea;

COMMENT ON COLUMN cliente.correo_enc  IS 'F41: correo cifrado con pgp_sym_encrypt. Leer con fn_descifrar().';
COMMENT ON COLUMN cliente.correo_hash IS 'F41: HMAC-SHA256 del correo normalizado. Sustituye a UNIQUE(correo), que el cifrado aleatorizado hacia imposible.';
COMMENT ON COLUMN proveedor.correo_enc IS 'F41: correo cifrado con pgp_sym_encrypt. Leer con fn_descifrar().';


\echo ''
\echo '--- Migracion por lotes (idempotente) ---'

-- El trigger de auditoria de la F40 se apaga SOLO durante la migracion. Cifrar
-- 5.400 filas generaria ~20.000 filas de auditoria que dirian todas lo mismo:
-- "un campo enmascarado cambio". Eso no es trazabilidad, es ruido que sepulta
-- los cambios reales. Se vuelve a encender al final del script y se verifica.
ALTER TABLE cliente   DISABLE TRIGGER trg_auditoria_cliente;
ALTER TABLE proveedor DISABLE TRIGGER trg_auditoria_proveedor;

DO $$
DECLARE
    v_lote      integer := 1000;
    v_afectadas integer;
    v_total     integer := 0;
BEGIN
    LOOP
        UPDATE cliente c
           SET correo_enc    = CASE WHEN c.correo    IS NOT NULL THEN fn_cifrar(c.correo)      ELSE NULL END,
               correo_hash   = CASE WHEN c.correo    IS NOT NULL THEN fn_hash_correo(c.correo) ELSE NULL END,
               telefono_enc  = CASE WHEN c.telefono  IS NOT NULL THEN fn_cifrar(c.telefono)    ELSE NULL END,
               direccion_enc = CASE WHEN c.direccion IS NOT NULL THEN fn_cifrar(c.direccion)   ELSE NULL END
         WHERE c.id_cliente IN (
                 SELECT id_cliente FROM cliente
                  WHERE (correo    IS NOT NULL AND correo_enc    IS NULL)
                     OR (telefono  IS NOT NULL AND telefono_enc  IS NULL)
                     OR (direccion IS NOT NULL AND direccion_enc IS NULL)
                  LIMIT v_lote);
        GET DIAGNOSTICS v_afectadas = ROW_COUNT;
        v_total := v_total + v_afectadas;
        EXIT WHEN v_afectadas = 0;
        RAISE NOTICE 'cliente: % filas cifradas (acumulado %)', v_afectadas, v_total;
    END LOOP;
    RAISE NOTICE 'cliente: migracion terminada, % filas', v_total;

    v_total := 0;
    LOOP
        UPDATE proveedor p
           SET correo_enc    = CASE WHEN p.correo    IS NOT NULL THEN fn_cifrar(p.correo)    ELSE NULL END,
               telefono_enc  = CASE WHEN p.telefono  IS NOT NULL THEN fn_cifrar(p.telefono)  ELSE NULL END,
               direccion_enc = CASE WHEN p.direccion IS NOT NULL THEN fn_cifrar(p.direccion) ELSE NULL END,
               contacto_enc  = CASE WHEN p.contacto  IS NOT NULL THEN fn_cifrar(p.contacto)  ELSE NULL END
         WHERE p.id_proveedor IN (
                 SELECT id_proveedor FROM proveedor
                  WHERE (correo    IS NOT NULL AND correo_enc    IS NULL)
                     OR (telefono  IS NOT NULL AND telefono_enc  IS NULL)
                     OR (direccion IS NOT NULL AND direccion_enc IS NULL)
                     OR (contacto  IS NOT NULL AND contacto_enc  IS NULL)
                  LIMIT v_lote);
        GET DIAGNOSTICS v_afectadas = ROW_COUNT;
        v_total := v_total + v_afectadas;
        EXIT WHEN v_afectadas = 0;
        RAISE NOTICE 'proveedor: % filas cifradas (acumulado %)', v_afectadas, v_total;
    END LOOP;
    RAISE NOTICE 'proveedor: migracion terminada, % filas', v_total;
END $$;

ALTER TABLE cliente   ENABLE TRIGGER trg_auditoria_cliente;
ALTER TABLE proveedor ENABLE TRIGGER trg_auditoria_proveedor;


\echo ''
\echo '--- Verificacion del 100 % ANTES de borrar el original ---'

-- Se descifra CADA fila migrada y se compara contra el valor en claro que
-- todavia esta al lado. Un COUNT que cuadra no demuestra que el contenido sea
-- correcto: demuestra que hay el mismo numero de filas. Aqui se compara el
-- contenido, campo a campo, en las 5.400.
SELECT 'cliente' AS tabla,
       count(*)                                                                        AS filas,
       count(*) FILTER (WHERE correo    IS DISTINCT FROM fn_descifrar(correo_enc))     AS dif_correo,
       count(*) FILTER (WHERE telefono  IS DISTINCT FROM fn_descifrar(telefono_enc))   AS dif_telefono,
       count(*) FILTER (WHERE direccion IS DISTINCT FROM fn_descifrar(direccion_enc))  AS dif_direccion,
       count(*) FILTER (WHERE correo IS NOT NULL
                          AND correo_hash IS DISTINCT FROM fn_hash_correo(correo))     AS dif_hash
FROM cliente
UNION ALL
SELECT 'proveedor',
       count(*),
       count(*) FILTER (WHERE correo    IS DISTINCT FROM fn_descifrar(correo_enc)),
       count(*) FILTER (WHERE telefono  IS DISTINCT FROM fn_descifrar(telefono_enc)),
       count(*) FILTER (WHERE direccion IS DISTINCT FROM fn_descifrar(direccion_enc)),
       count(*) FILTER (WHERE contacto  IS DISTINCT FROM fn_descifrar(contacto_enc))
FROM proveedor;


\echo ''
\echo '--- Baja de las columnas en claro (solo si la verificacion da 0) ---'

-- La comprobacion se repite AQUI DENTRO y aborta con excepcion si algo no
-- cuadra. Dejarlo a la vista del operador en la consulta de arriba no basta:
-- un script desatendido seguiria adelante y borraria el original. Esto lo
-- convierte en imposible, no en desaconsejado.
DO $$
DECLARE v_mal integer;
BEGIN
    SELECT count(*) INTO v_mal FROM cliente
     WHERE correo    IS DISTINCT FROM fn_descifrar(correo_enc)
        OR telefono  IS DISTINCT FROM fn_descifrar(telefono_enc)
        OR direccion IS DISTINCT FROM fn_descifrar(direccion_enc)
        OR (correo IS NOT NULL AND correo_hash IS DISTINCT FROM fn_hash_correo(correo));
    IF v_mal > 0 THEN
        RAISE EXCEPTION 'ABORTADO: % filas de cliente no descifran al valor original. NO se borra nada.', v_mal;
    END IF;

    SELECT count(*) INTO v_mal FROM proveedor
     WHERE correo    IS DISTINCT FROM fn_descifrar(correo_enc)
        OR telefono  IS DISTINCT FROM fn_descifrar(telefono_enc)
        OR direccion IS DISTINCT FROM fn_descifrar(direccion_enc)
        OR contacto  IS DISTINCT FROM fn_descifrar(contacto_enc);
    IF v_mal > 0 THEN
        RAISE EXCEPTION 'ABORTADO: % filas de proveedor no descifran al valor original. NO se borra nada.', v_mal;
    END IF;

    RAISE NOTICE 'Verificacion superada: el 100 %% de las filas descifra al valor original.';

    -- Al caer la columna caen con ella UNIQUE(correo) y el CHECK del formato de
    -- correo. El UNIQUE se repone abajo sobre correo_hash. El CHECK de formato
    -- NO tiene sustituto posible en la base: no se puede validar con una
    -- expresion regular un dato que la base no puede leer. La validacion pasa a
    -- ser responsabilidad exclusiva de @Email en los DTO de la aplicacion, y
    -- queda anotado en CIFRADO.md como lo que es: una garantia que baja de
    -- nivel, de la base a la aplicacion.
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name='cliente' AND column_name='correo') THEN
        ALTER TABLE cliente DROP COLUMN correo;
        ALTER TABLE cliente DROP COLUMN telefono;
        ALTER TABLE cliente DROP COLUMN direccion;
        RAISE NOTICE 'cliente: columnas en claro eliminadas';
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name='proveedor' AND column_name='correo') THEN
        ALTER TABLE proveedor DROP COLUMN correo;
        ALTER TABLE proveedor DROP COLUMN telefono;
        ALTER TABLE proveedor DROP COLUMN direccion;
        ALTER TABLE proveedor DROP COLUMN contacto;
        RAISE NOTICE 'proveedor: columnas en claro eliminadas';
    END IF;
END $$;


\echo ''
\echo '--- Reposicion de la unicidad del correo ---'

CREATE UNIQUE INDEX IF NOT EXISTS uq_cliente_correo_hash ON cliente (correo_hash);

-- El hash se calcula en la base, no en la aplicacion, y por eso NO PUEDE
-- desincronizarse del texto cifrado: no hay ninguna ruta de escritura que
-- actualice correo_enc y se olvide del hash.
CREATE OR REPLACE FUNCTION fn_cliente_sincronizar_hash() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    -- Sin clave en la sesion no se toca el hash. Lo contrario seria peor que no
    -- hacer nada: fn_descifrar devolveria NULL y el trigger BORRARIA un hash
    -- valido, tumbando la unicidad justo cuando alguien opera sin la clave.
    IF fn_clave_cifrado() IS NULL THEN
        RETURN NEW;
    END IF;
    IF TG_OP = 'INSERT' OR NEW.correo_enc IS DISTINCT FROM OLD.correo_enc THEN
        NEW.correo_hash := fn_hash_correo(fn_descifrar(NEW.correo_enc));
    END IF;
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_cliente_hash_correo ON cliente;
CREATE TRIGGER trg_cliente_hash_correo
    BEFORE INSERT OR UPDATE OF correo_enc ON cliente
    FOR EACH ROW EXECUTE FUNCTION fn_cliente_sincronizar_hash();


\echo ''
\echo '############ ETAPA 4.4 — privilegios de columna ####################'

-- Se replica EXACTAMENTE la matriz que tenian las columnas originales, tomada
-- de information_schema.column_privileges antes de la migracion:
--
--   cliente    rol_administrador    INSERT SELECT UPDATE
--              rol_operador_pedidos INSERT SELECT UPDATE
--              rol_operador_bodega  SELECT
--              rol_supervisor       SELECT
--   proveedor  rol_administrador    INSERT SELECT UPDATE
--              rol_encargado_compras SELECT
--              rol_supervisor        SELECT
--
-- Cifrar no es excusa para relajar el acceso: quien no podia leer el correo en
-- claro tampoco puede leer el cifrado.

GRANT SELECT (correo_enc, correo_hash, telefono_enc, direccion_enc),
      INSERT (correo_enc, correo_hash, telefono_enc, direccion_enc),
      UPDATE (correo_enc, correo_hash, telefono_enc, direccion_enc)
   ON cliente TO rol_administrador, rol_operador_pedidos;

GRANT SELECT (correo_enc, correo_hash, telefono_enc, direccion_enc)
   ON cliente TO rol_operador_bodega, rol_supervisor;

GRANT SELECT (correo_enc, telefono_enc, direccion_enc, contacto_enc),
      INSERT (correo_enc, telefono_enc, direccion_enc, contacto_enc),
      UPDATE (correo_enc, telefono_enc, direccion_enc, contacto_enc)
   ON proveedor TO rol_administrador;

GRANT SELECT (correo_enc, telefono_enc, direccion_enc, contacto_enc)
   ON proveedor TO rol_encargado_compras, rol_supervisor;


\echo ''
\echo '############ ETAPA 5 — cierre de vias laterales ####################'

-- ----------------------------------------------------------------------------
-- VIA 1: la auditoria de la F40.
--
-- cliente y proveedor son dos de las cinco tablas con trigger generico. Ese
-- trigger escribe valor_anterior y valor_nuevo en auditoria_cambios, que leen
-- rol_administrador y rol_supervisor. Sin tocar nada, un UPDATE de correo
-- dejaria el texto cifrado en la bitacora — y en el INSERT, que vuelca la fila
-- entera, tambien. No es texto en claro, pero es material cifrado duplicado en
-- una tabla con otras reglas de acceso y sin la proteccion de los privilegios
-- por columna. Se enmascara.
--
-- UNICO CAMBIO respecto de la funcion de la F40: la lista v_ocultar. El resto
-- del cuerpo es identico, salvo un arreglo que se declara abajo.
-- ----------------------------------------------------------------------------
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
    v_ignorar  text[] := ARRAY['updated_at','created_at','fecha_actualizacion'];
    -- F41: a los hashes de contrasena se suman los datos personales cifrados.
    -- Se registra QUE cambiaron, nunca a que.
    v_ocultar  text[] := ARRAY['password','contrasena','contrasena_hash',
                               'correo_enc','telefono_enc','direccion_enc',
                               'contacto_enc','correo_hash'];
BEGIN
    IF TG_TABLE_NAME = 'auditoria_cambios' THEN
        RETURN NULL;
    END IF;

    BEGIN
        v_usuario := current_setting('app.current_user_id', true)::integer;
    EXCEPTION WHEN others THEN
        v_usuario := NULL;
    END;

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
                (SELECT jsonb_object_agg(k, CASE WHEN k = ANY(v_ocultar) THEN '***' ELSE v END)::text
                   FROM jsonb_each_text(v_old) AS e(k, v)),
                -- F41, ARREGLO: la rama DELETE de la F40 registraba current_user,
                -- que dentro de un SECURITY DEFINER vale siempre 'postgres'. El
                -- efecto era que TODO borrado quedaba atribuido a postgres, con
                -- lo que la columna no respondia a "quien" justo en la operacion
                -- mas destructiva. Las ramas INSERT y UPDATE ya usaban
                -- session_user; esta se habia quedado atras.
                NULL, session_user, v_usuario, txid_current());
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

    v_old := to_jsonb(OLD);
    v_new := to_jsonb(NEW);
    v_pk_val := v_new ->> v_pk_col;

    FOR v_campo IN SELECT jsonb_object_keys(v_new) LOOP
        CONTINUE WHEN v_campo = ANY(v_ignorar);

        v_ant := v_old ->> v_campo;
        v_nue := v_new ->> v_campo;

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


\echo ''
\echo '--- VIA 2: indices sobre las columnas eliminadas ---'

-- Un indice btree guarda el VALOR indexado en sus paginas. Un indice sobre la
-- columna en claro habria sobrevivido al cifrado guardando los correos
-- legibles en disco. Al caer la columna cae su indice, pero se comprueba en vez
-- de suponerlo.
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename IN ('cliente','proveedor')
ORDER BY tablename, indexname;


\echo ''
\echo '--- VIA 3: vistas o restricciones que sigan leyendo lo viejo ---'

SELECT c.relname AS objeto, c.relkind
FROM pg_class c
JOIN pg_rewrite r ON r.ev_class = c.oid
JOIN pg_depend d  ON d.objid = r.oid
WHERE d.refobjid IN ('cliente'::regclass, 'proveedor'::regclass)
  AND c.relkind = 'v'
GROUP BY 1,2;

SELECT conrelid::regclass AS tabla, conname, pg_get_constraintdef(oid) AS definicion
FROM pg_constraint
WHERE conrelid IN ('cliente'::regclass,'proveedor'::regclass)
ORDER BY 1, 2;


\echo ''
\echo '############ Cierre ################################################'

ANALYZE cliente;
ANALYZE proveedor;

-- Todos los triggers deben quedar activos: la migracion apago dos y los volvio
-- a encender. Si esto no sale en 0, la auditoria esta ciega sobre esas tablas.
SELECT count(*) AS triggers_no_activos
FROM pg_trigger WHERE NOT tgisinternal AND tgenabled <> 'O';

SELECT count(*) AS triggers_totales
FROM pg_trigger WHERE NOT tgisinternal;

\echo 'FASE 41 — cifrado aplicado.'
