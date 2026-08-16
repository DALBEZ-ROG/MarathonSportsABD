-- ============================================================================
-- FASE 41 — PRUEBAS DEL CIFRADO DE DATOS PERSONALES
-- ----------------------------------------------------------------------------
-- Mismo formato de conteo X/N que fase34_pruebas_roles.sql y
-- fase40_pruebas_auditoria.sql.
--
-- USO (necesita la clave, asi que NO se lanza con psql -f a pelo):
--
--   powershell -File scripts\cifrado\gestionar_clave.ps1 -Accion Ejecutar `
--              -Script marathon-backend\sql\fase41_pruebas_cifrado.sql
--
-- NO MODIFICA DATOS, y esta vez de verdad. El script abre una transaccion
-- explicita con BEGIN en la primera linea y termina en ROLLBACK.
--
-- POR QUE ESE BEGIN NO ES DECORATIVO. El arnes de la F40 termina en ROLLBACK
-- pero NO abre transaccion, y psql trabaja en autocommit: cada sentencia de
-- nivel superior —incluido cada bloque DO— se confirma sola, asi que el
-- ROLLBACK final no revierte nada. El efecto medido: cada corrida de
-- fase40_pruebas_auditoria.sql desactivaba al administrador, le destrozaba el
-- hash de la contrasena y borraba una fila de rol_permiso, de forma permanente.
-- Con BEGIN al principio, el ROLLBACK del final si revierte. (El arnes de la
-- F40 se arregla igual, o se lanza con psql -1, que es equivalente.)
-- ============================================================================

BEGIN;

\set ON_ERROR_STOP on
\pset pager off

\getenv clave MARATHON_CRYPTO_KEY
SELECT set_config('app.crypto_key', :'clave', true) IS NOT NULL AS clave_publicada;

\echo ''
\echo '=== FASE 41: pruebas del cifrado de datos personales ==='

CREATE TEMP TABLE _res (n serial, descripcion text, esperado text, obtenido text, pasa text);

CREATE OR REPLACE FUNCTION pg_temp.probar(p_rol text, p_desc text, p_sql text, p_esperado text)
RETURNS void AS $$
DECLARE v_obt text;
BEGIN
    BEGIN
        EXECUTE format('SET LOCAL ROLE %I', p_rol);
        EXECUTE p_sql;
        RAISE EXCEPTION 'CENTINELA';          -- fuerza el rollback aunque funcione
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

CREATE OR REPLACE FUNCTION pg_temp.afirmar(p_desc text, p_cond boolean)
RETURNS void AS $$
BEGIN
    INSERT INTO _res (descripcion, esperado, obtenido, pasa)
    VALUES (p_desc, 'true', coalesce(p_cond::text,'null'),
            CASE WHEN p_cond THEN 'PASA' ELSE 'FALLA' END);
END;
$$ LANGUAGE plpgsql;


-- ============================================================================
-- 1. EL DATO EN CLARO YA NO EXISTE
-- ----------------------------------------------------------------------------
-- Cifrar y dejar la columna original al lado no cifra nada.
-- ============================================================================
DO $$
DECLARE c text;
BEGIN
    PERFORM pg_temp.afirmar('pgcrypto esta instalada',
        EXISTS (SELECT 1 FROM pg_extension WHERE extname='pgcrypto'));

    FOREACH c IN ARRAY ARRAY['correo','telefono','direccion'] LOOP
        PERFORM pg_temp.afirmar(format('cliente.%s en claro ya NO existe', c),
            NOT EXISTS (SELECT 1 FROM information_schema.columns
                         WHERE table_name='cliente' AND column_name=c));
    END LOOP;

    FOREACH c IN ARRAY ARRAY['correo','telefono','direccion','contacto'] LOOP
        PERFORM pg_temp.afirmar(format('proveedor.%s en claro ya NO existe', c),
            NOT EXISTS (SELECT 1 FROM information_schema.columns
                         WHERE table_name='proveedor' AND column_name=c));
    END LOOP;

    PERFORM pg_temp.afirmar('cliente.correo_enc es bytea',
        EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_name='cliente' AND column_name='correo_enc' AND data_type='bytea'));

    -- Ninguna fila se quedo sin cifrar por el camino.
    PERFORM pg_temp.afirmar('Las 5.000+ filas de cliente tienen correo cifrado',
        (SELECT count(*) FROM cliente WHERE correo_enc IS NULL) = 0);

    -- El texto cifrado no contiene el original: si quedara rastro legible, el
    -- bytea seria un disfraz y no un cifrado.
    --
    -- Se busca el correo COMPLETO dentro del bytea, no el caracter '@'. La
    -- primera version de esta prueba buscaba '@' y fallaba en las 5.003 filas,
    -- pero no por una fuga: en ~90 bytes aleatorios la probabilidad de que
    -- aparezca el byte 0x40 por azar ronda el 30 %, asi que sobre miles de
    -- filas es practicamente seguro. Encontrar un '@' suelto en texto cifrado
    -- no demuestra nada; encontrar el correo entero, si.
    PERFORM pg_temp.afirmar('El bytea cifrado NO contiene el correo original',
        NOT EXISTS (SELECT 1 FROM cliente
                     WHERE correo_enc IS NOT NULL
                       AND position(convert_to(fn_descifrar(correo_enc),'UTF8') in correo_enc) > 0));
END $$;


-- ============================================================================
-- 2. EXCLUSIONES DELIBERADAS
-- ----------------------------------------------------------------------------
-- Lo que NO se cifro se comprueba igual, para que la decision quede escrita en
-- una prueba y no solo en un documento.
-- ============================================================================
DO $$
BEGIN
    PERFORM pg_temp.afirmar('usuario.correo sigue en claro (es la credencial de acceso)',
        EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_name='usuario' AND column_name='correo'
                   AND data_type='character varying'));
    PERFORM pg_temp.afirmar('cliente.nombre sigue en claro (listado y orden alfabetico)',
        EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_name='cliente' AND column_name='nombre'
                   AND data_type='character varying'));
    PERFORM pg_temp.afirmar('cliente.apellido sigue en claro',
        EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_name='cliente' AND column_name='apellido'
                   AND data_type='character varying'));
    PERFORM pg_temp.afirmar('proveedor NO tiene columna hash (nunca tuvo UNIQUE ni busqueda por correo)',
        NOT EXISTS (SELECT 1 FROM information_schema.columns
                     WHERE table_name='proveedor' AND column_name='correo_hash'));
END $$;


-- ============================================================================
-- 3. EL CIFRADO FUNCIONA Y DEPENDE DE LA CLAVE
-- ============================================================================
DO $$
DECLARE v_a bytea; v_b bytea; v_txt text;
BEGIN
    PERFORM pg_temp.afirmar('Ida y vuelta: descifrar(cifrar(x)) = x',
        fn_descifrar(fn_cifrar('correo.de.prueba@marathon.com')) = 'correo.de.prueba@marathon.com');

    -- Dos cifrados del MISMO texto deben diferir: sal e IV aleatorios. Si
    -- coincidieran, se podria saber que dos clientes comparten correo sin
    -- descifrar nada.
    v_a := fn_cifrar('mismo@correo.com');
    v_b := fn_cifrar('mismo@correo.com');
    PERFORM pg_temp.afirmar('pgp_sym_encrypt NO es determinista (dos cifrados difieren)', v_a <> v_b);
    PERFORM pg_temp.afirmar('...y aun asi los dos descifran al mismo texto',
        fn_descifrar(v_a) = fn_descifrar(v_b));

    -- El hash SI debe ser determinista: es lo que sostiene la unicidad.
    PERFORM pg_temp.afirmar('El hash del correo SI es determinista',
        fn_hash_correo('mismo@correo.com') = fn_hash_correo('mismo@correo.com'));
    PERFORM pg_temp.afirmar('El hash normaliza mayusculas y espacios',
        fn_hash_correo('  Mismo@Correo.com ') = fn_hash_correo('mismo@correo.com'));

    -- El hash es HMAC, no un digest publico: cambiar la clave cambia el hash.
    -- Sin esto, sha256(correo) seria invertible con un diccionario.
    v_a := fn_hash_correo('mismo@correo.com');
    PERFORM set_config('app.crypto_key', 'otra-clave-distinta', true);
    v_b := fn_hash_correo('mismo@correo.com');
    PERFORM pg_temp.afirmar('El hash depende de la clave (es HMAC, no un digest publico)', v_a <> v_b);

    -- Sin clave: descifrar devuelve NULL (ilegible) y cifrar FALLA.
    PERFORM set_config('app.crypto_key', '', true);
    SELECT fn_descifrar(correo_enc) INTO v_txt FROM cliente ORDER BY id_cliente LIMIT 1;
    PERFORM pg_temp.afirmar('SIN clave, fn_descifrar devuelve NULL (dato ilegible)', v_txt IS NULL);

    BEGIN
        PERFORM fn_cifrar('algo');
        PERFORM pg_temp.afirmar('SIN clave, fn_cifrar FALLA en vez de guardar basura', false);
    EXCEPTION WHEN others THEN
        PERFORM pg_temp.afirmar('SIN clave, fn_cifrar FALLA en vez de guardar basura', true);
    END;
END $$;

-- Se repone la clave buena para el resto del arnes.
\getenv clave MARATHON_CRYPTO_KEY
SELECT set_config('app.crypto_key', :'clave', true) IS NOT NULL AS clave_repuesta;

DO $$
DECLARE v_txt text;
BEGIN
    SELECT fn_descifrar(correo_enc) INTO v_txt FROM cliente ORDER BY id_cliente LIMIT 1;
    PERFORM pg_temp.afirmar('CON la clave, el correo vuelve a ser legible', v_txt LIKE '%@%');
END $$;


-- ============================================================================
-- 4. LA UNICIDAD DEL CORREO SOBREVIVIO AL CIFRADO
-- ----------------------------------------------------------------------------
-- Era UNIQUE(correo). Con cifrado aleatorizado esa restriccion es imposible, y
-- se repuso sobre el hash determinista. Si esto falla, dos clientes pueden
-- registrarse con el mismo correo.
-- ============================================================================
DO $$
BEGIN
    PERFORM pg_temp.afirmar('Existe el indice unico sobre correo_hash',
        EXISTS (SELECT 1 FROM pg_indexes
                 WHERE tablename='cliente' AND indexname='uq_cliente_correo_hash'));
    PERFORM pg_temp.afirmar('El trigger que mantiene el hash esta activo',
        EXISTS (SELECT 1 FROM pg_trigger tg JOIN pg_class c ON c.oid=tg.tgrelid
                 WHERE c.relname='cliente' AND tg.tgname='trg_cliente_hash_correo'
                   AND tg.tgenabled='O'));
END $$;

-- Alta con un correo que YA existe: debe rechazarse.
SELECT pg_temp.probar('rol_administrador',
    'Un correo duplicado se sigue rechazando (unicidad viva)',
    'INSERT INTO cliente (id_ciudad, nombre, apellido, correo_enc, estado) '
    'VALUES (1, ''Duplicado'', ''Prueba'', fn_cifrar((SELECT fn_descifrar(correo_enc) FROM cliente WHERE correo_enc IS NOT NULL ORDER BY id_cliente LIMIT 1)), ''activo'')',
    'DENEGADO_REGLA_BD');


-- ============================================================================
-- 5. PRIVILEGIOS: LA MATRIZ DE LA F34 SE RESPETA SOBRE LAS COLUMNAS NUEVAS
-- ----------------------------------------------------------------------------
-- Cifrar no es excusa para relajar el acceso. Quien no podia leer el correo en
-- claro tampoco puede leer el cifrado.
-- ============================================================================
SELECT pg_temp.probar('rol_administrador', 'ADMIN puede leer cliente.correo_enc',
    'SELECT correo_enc FROM cliente LIMIT 1', 'PERMITIDO');
SELECT pg_temp.probar('rol_supervisor', 'SUPERVISOR puede leer cliente.correo_enc',
    'SELECT correo_enc FROM cliente LIMIT 1', 'PERMITIDO');
SELECT pg_temp.probar('rol_operador_bodega', 'BODEGA puede leer cliente.correo_enc (lo mismo que antes)',
    'SELECT correo_enc FROM cliente LIMIT 1', 'PERMITIDO');
SELECT pg_temp.probar('rol_operador_pedidos', 'PEDIDOS puede escribir cliente.correo_enc',
    'UPDATE cliente SET correo_enc = fn_cifrar(''x@y.com'') WHERE id_cliente=(SELECT min(id_cliente) FROM cliente)',
    'PERMITIDO');
SELECT pg_temp.probar('rol_encargado_compras', 'COMPRAS NO puede leer cliente.correo_enc',
    'SELECT correo_enc FROM cliente LIMIT 1', 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_encargado_produccion', 'PRODUCCION NO puede leer cliente.correo_enc',
    'SELECT correo_enc FROM cliente LIMIT 1', 'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_encargado_compras', 'COMPRAS SI puede leer proveedor.correo_enc',
    'SELECT correo_enc FROM proveedor LIMIT 1', 'PERMITIDO');
SELECT pg_temp.probar('rol_operador_bodega', 'BODEGA NO puede escribir cliente.correo_enc',
    'UPDATE cliente SET correo_enc = fn_cifrar(''x@y.com'') WHERE id_cliente=(SELECT min(id_cliente) FROM cliente)',
    'DENEGADO_PRIVILEGIO');
SELECT pg_temp.probar('rol_encargado_compras', 'COMPRAS NO puede escribir proveedor.correo_enc (solo lectura)',
    'UPDATE proveedor SET correo_enc = fn_cifrar(''x@y.com'') WHERE id_proveedor=(SELECT min(id_proveedor) FROM proveedor)',
    'DENEGADO_PRIVILEGIO');


-- ============================================================================
-- 6. VIAS LATERALES CERRADAS
-- ============================================================================
DO $$
DECLARE v_antes bigint; v_id int; v_fuente text; v_frontera bigint; v_legibles bigint;
BEGIN
    -- 6.1 La auditoria de la F40 enmascara los campos cifrados.
    SELECT prosrc INTO v_fuente FROM pg_proc WHERE proname='fn_auditoria_cambios';
    PERFORM pg_temp.afirmar('La auditoria enmascara correo_enc',    v_fuente LIKE '%correo_enc%');
    PERFORM pg_temp.afirmar('La auditoria enmascara telefono_enc',  v_fuente LIKE '%telefono_enc%');
    PERFORM pg_temp.afirmar('La auditoria enmascara direccion_enc', v_fuente LIKE '%direccion_enc%');
    PERFORM pg_temp.afirmar('La auditoria enmascara contacto_enc',  v_fuente LIKE '%contacto_enc%');

    -- Y se comprueba en ejecucion, no solo leyendo el codigo fuente.
    SELECT count(*), min(id_cliente) INTO v_antes, v_id FROM auditoria_cambios, cliente;
    SELECT count(*) INTO v_antes FROM auditoria_cambios;
    SELECT min(id_cliente) INTO v_id FROM cliente;

    UPDATE cliente SET correo_enc = fn_cifrar('rastro@prueba.com') WHERE id_cliente = v_id;

    PERFORM pg_temp.afirmar('Un cambio de correo deja rastro en la auditoria',
        EXISTS (SELECT 1 FROM auditoria_cambios
                 WHERE id > v_antes AND tabla='cliente' AND campo='correo_enc'));
    PERFORM pg_temp.afirmar('...pero enmascarado: el texto cifrado NO entra en la bitacora',
        EXISTS (SELECT 1 FROM auditoria_cambios
                 WHERE id > v_antes AND tabla='cliente' AND campo='correo_enc'
                   AND valor_anterior = '***' AND valor_nuevo = '*** (modificado)'));
    -- HALLAZGO DE LA FASE, convertido en invariante.
    --
    -- La bitacora conserva 3 filas con correos LEGIBLES, escritas por la prueba
    -- funcional de la F40 cuando la columna todavia estaba en claro. Cifrar la
    -- tabla no limpia retroactivamente lo que la auditoria ya habia guardado, y
    -- auditoria_cambios la leen rol_administrador y rol_supervisor.
    --
    -- No se borran: auditoria_cambios es append-only por diseno y reescribir la
    -- bitacora para tapar un hallazgo seria peor que el hallazgo. Son datos de
    -- prueba (@correo-demo.ec), no clientes reales.
    --
    -- Lo que si se exige es que ese conjunto NO CREZCA: desde el momento en que
    -- las columnas cifradas aparecen en la bitacora, ninguna fila nueva puede
    -- contener un correo legible. Si esta prueba falla, es que se abrio una via
    -- nueva, no que sigue la vieja.
    SELECT min(id) INTO v_frontera FROM auditoria_cambios
     WHERE campo = 'correo_enc' OR valor_nuevo LIKE '%correo_enc%';

    PERFORM pg_temp.afirmar('Ningun correo legible entro en la bitacora DESPUES del cifrado',
        NOT EXISTS (SELECT 1 FROM auditoria_cambios
                     WHERE tabla IN ('cliente','proveedor')
                       AND id >= v_frontera
                       AND (valor_anterior LIKE '%@%' OR valor_nuevo LIKE '%@%')));

    -- El hallazgo queda acotado y a la vista: se cuenta cuantas filas heredadas
    -- hay y se comprueba que todas caen antes de la frontera. Si algun dia
    -- aparece una despues, la prueba anterior falla; si el conteo cambia sin
    -- que nadie lo explique, esta deja constancia del numero.
    SELECT count(*) INTO v_legibles FROM auditoria_cambios
     WHERE tabla IN ('cliente','proveedor')
       AND (valor_anterior LIKE '%@%' OR valor_nuevo LIKE '%@%');
    RAISE NOTICE 'Filas heredadas con correo legible en la bitacora (anteriores al cifrado): %', v_legibles;

    PERFORM pg_temp.afirmar('Las filas con correo legible son un conjunto cerrado y anterior al cifrado',
        v_legibles = (SELECT count(*) FROM auditoria_cambios
                       WHERE tabla IN ('cliente','proveedor')
                         AND id < v_frontera
                         AND (valor_anterior LIKE '%@%' OR valor_nuevo LIKE '%@%')));

    -- 6.2 Ningun indice sobre datos personales legibles.
    PERFORM pg_temp.afirmar('No queda ningun indice sobre columnas en claro de contacto',
        NOT EXISTS (SELECT 1 FROM pg_indexes
                     WHERE tablename IN ('cliente','proveedor')
                       AND (indexdef LIKE '%(correo)%' OR indexdef LIKE '%(telefono)%'
                            OR indexdef LIKE '%(direccion)%' OR indexdef LIKE '%(contacto)%')));

    -- 6.3 Ninguna vista sigue leyendo lo viejo.
    PERFORM pg_temp.afirmar('Ninguna vista depende de cliente o proveedor',
        NOT EXISTS (SELECT 1 FROM pg_class c
                     JOIN pg_rewrite r ON r.ev_class = c.oid
                     JOIN pg_depend d  ON d.objid = r.oid
                     WHERE d.refobjid IN ('cliente'::regclass,'proveedor'::regclass)
                       AND c.relkind='v'));

    -- 6.4 La clave no esta guardada en ninguna parte de la base.
    PERFORM pg_temp.afirmar('La clave NO esta almacenada en ninguna tabla de configuracion',
        NOT EXISTS (SELECT 1 FROM information_schema.tables
                     WHERE table_schema='public'
                       AND table_name IN ('configuracion','crypto_key','clave_cifrado','secretos')));

    PERFORM pg_temp.afirmar('Todos los triggers del esquema siguen activos',
        NOT EXISTS (SELECT 1 FROM pg_trigger tg JOIN pg_class c ON c.oid=tg.tgrelid
                    JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE NOT tg.tgisinternal AND n.nspname='public' AND tg.tgenabled <> 'O'));
END $$;


-- ============================================================================
-- 7. LAS FUNCIONES DE CIFRADO NO SON UNA PUERTA TRASERA
-- ----------------------------------------------------------------------------
-- fn_descifrar NO debe ser SECURITY DEFINER: si lo fuera, cualquier rol con
-- SELECT sobre cliente descifraria sin conocer la clave, y el cifrado no
-- protegeria de nada.
-- ============================================================================
DO $$
BEGIN
    PERFORM pg_temp.afirmar('fn_descifrar NO es SECURITY DEFINER',
        (SELECT NOT prosecdef FROM pg_proc WHERE proname='fn_descifrar'));
    PERFORM pg_temp.afirmar('fn_cifrar NO es SECURITY DEFINER',
        (SELECT NOT prosecdef FROM pg_proc WHERE proname='fn_cifrar'));
    PERFORM pg_temp.afirmar('fn_hash_correo NO es SECURITY DEFINER',
        (SELECT NOT prosecdef FROM pg_proc WHERE proname='fn_hash_correo'));
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
    RAISE NOTICE '===== % de % pruebas de cifrado PASAN =====', v_pasan, v_total;
    IF v_pasan <> v_total THEN
        RAISE EXCEPTION 'Hay % pruebas de cifrado que fallan', v_total - v_pasan;
    END IF;
END $$;

-- Revierte de verdad: la transaccion se abrio con BEGIN en la cabecera.
ROLLBACK;
