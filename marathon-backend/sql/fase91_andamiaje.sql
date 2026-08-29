-- =============================================================================
-- FASE 91 · Andamiaje comun de la carga masiva
-- -----------------------------------------------------------------------------
-- Todo lo que necesitan las cinco etapas: el objetivo, la fuente unica de
-- fechas, el cifrado de volumen, los marcadores de rango y los catalogos.
-- Vive en su propio esquema `carga` para no ensuciar public; la etapa 5 lo
-- borra entero con DROP SCHEMA carga CASCADE.
--
-- Es idempotente: se puede volver a ejecutar sin efecto secundario.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS carga;

CREATE OR REPLACE FUNCTION carga.objetivo() RETURNS bigint
  LANGUAGE sql IMMUTABLE AS 'SELECT 1500000::bigint';

-- Fuente UNICA de fechas. Reparte sobre 943 dias a partir del 2024-01-01, y
-- 2024-01-01 + 942 dias = 2026-07-31. El tope vive en el modulo, no en un
-- comentario: por construccion no puede salir una fecha de agosto de 2026 ni
-- mucho menos de 2040.
CREATE OR REPLACE FUNCTION carga.fecha(g bigint) RETURNS timestamp
  LANGUAGE sql IMMUTABLE AS $fn$
  SELECT timestamp '2024-01-01 00:00:00'
       + (mod(g * 7919, 943)) * interval '1 day'
       + (mod(g * 37, 86400)) * interval '1 second';
$fn$;

-- Tope duro. Donde haya que sumar un intervalo a una fecha (un vencimiento, la
-- expiracion de un token, el fin de una orden), el resultado pasa por aqui y
-- no puede rebasar el 31/07/2026 aunque el intervalo lo empujase mas alla.
CREATE OR REPLACE FUNCTION carga.tope(t timestamp) RETURNS timestamp
  LANGUAGE sql IMMUTABLE AS $fn$
  SELECT LEAST(t, timestamp '2026-07-31 23:59:59');
$fn$;

-- Rango de identificadores nuevos de un padre, listo para cruzar en un FROM:
-- b = primer id, n = cuantos hay. Se usa como relacion de una fila para no
-- repetir una subconsulta correlacionada 1,5 millones de veces.
CREATE OR REPLACE VIEW carga.r AS
  SELECT tabla, id_min AS b, id_max - id_min + 1 AS n FROM carga.rango;

-- Cifrado de volumen. Mismo algoritmo, misma clave y mismo tamano de salida
-- (92 B) que fn_cifrar; lo unico que baja es el s2k-count, que protege contra
-- la fuerza bruta de una CONTRASENA debil y aqui no aplica porque la clave son
-- 32 bytes aleatorios. Medido en este equipo: 0,0077 ms/valor frente a 0,203.
-- Los parametros viajan dentro del propio mensaje PGP, asi que fn_descifrar lo
-- lee sin ningun cambio (comprobado antes de escribir una sola fila).
CREATE OR REPLACE FUNCTION carga.cifrar(p text) RETURNS bytea
  LANGUAGE sql AS $fn$
  SELECT pgp_sym_encrypt(p, fn_clave_cifrado(), 's2k-count=1024, compress-algo=0');
$fn$;

CREATE TABLE IF NOT EXISTS carga.marca (tabla text PRIMARY KEY, id_desde bigint NOT NULL);
CREATE TABLE IF NOT EXISTS carga.rango (tabla text PRIMARY KEY, id_min bigint, id_max bigint, filas bigint);

CREATE OR REPLACE PROCEDURE carga.marcar(p_tabla text, p_pk text)
  LANGUAGE plpgsql AS $pr$
DECLARE v bigint;
BEGIN
  EXECUTE format('SELECT COALESCE(max(%I),0) FROM public.%I', p_pk, p_tabla) INTO v;
  INSERT INTO carga.marca VALUES (p_tabla, v)
    ON CONFLICT (tabla) DO UPDATE SET id_desde = EXCLUDED.id_desde;
END $pr$;

CREATE OR REPLACE FUNCTION carga.desde(p_tabla text) RETURNS bigint
  LANGUAGE sql STABLE AS 'SELECT id_desde FROM carga.marca WHERE tabla = p_tabla';

-- Cierra el rango recien insertado y COMPRUEBA que es contiguo. Si un INSERT
-- fallido hubiera quemado valores de la secuencia, min..max incluiria
-- identificadores inexistentes y los hijos violarian la clave foranea mucho
-- despues, lejos de la causa. Mejor reventar aqui.
CREATE OR REPLACE PROCEDURE carga.cerrar(p_tabla text, p_pk text)
  LANGUAGE plpgsql AS $pr$
DECLARE v_min bigint; v_max bigint; v_n bigint;
BEGIN
  EXECUTE format('SELECT min(%I), max(%I), count(*) FROM public.%I WHERE %I > %s',
                 p_pk, p_pk, p_tabla, p_pk, carga.desde(p_tabla))
    INTO v_min, v_max, v_n;
  -- Sin filas nuevas se SALE sin tocar carga.rango. Sobrescribirlo con un
  -- rango vacio borraria el que anoto la ejecucion anterior, y las tablas que
  -- cuelgan de el (movimiento_materia_prima de recepcion_mercancia, por
  -- ejemplo) se quedarian sin padre al reanudar tras un fallo.
  IF v_n = 0 THEN
    RAISE NOTICE '%: sin filas nuevas; se conserva el rango ya anotado', p_tabla;
    RETURN;
  ELSIF v_max - v_min + 1 <> v_n THEN
    RAISE EXCEPTION '%: rango no contiguo (min=% max=% filas=%). Secuencia quemada por un intento previo.',
      p_tabla, v_min, v_max, v_n;
  END IF;
  INSERT INTO carga.rango VALUES (p_tabla, v_min, v_max, v_n)
    ON CONFLICT (tabla) DO UPDATE
      SET id_min = EXCLUDED.id_min, id_max = EXCLUDED.id_max, filas = EXCLUDED.filas;
END $pr$;

-- Elige un id del rango nuevo de un padre, determinista y bien repartido.
-- 2654435761 es el multiplicador de Knuth: primo con cualquier tamano de rango
-- que no sea multiplo suyo, asi que no degenera en un puñado de valores.
CREATE OR REPLACE FUNCTION carga.ref(p_tabla text, g bigint) RETURNS bigint
  LANGUAGE sql STABLE AS $fn$
  SELECT r.id_min + mod(g * 2654435761, r.id_max - r.id_min + 1)
  FROM carga.rango r WHERE r.tabla = p_tabla;
$fn$;

CREATE OR REPLACE FUNCTION carga.faltan(p_tabla text) RETURNS bigint
  LANGUAGE plpgsql AS $fn$
DECLARE v bigint;
BEGIN
  EXECUTE format('SELECT count(*) FROM public.%I', p_tabla) INTO v;
  RETURN GREATEST(carga.objetivo() - v, 0);
END $fn$;

-- Igual, pero contra un objetivo propio. Lo usan las tablas de LINEA, que
-- tienen que quedar POR ENCIMA de 1,5M para que su cabecera no se quede sin
-- hijos: 1,5M era un suelo, no un techo.
CREATE OR REPLACE FUNCTION carga.faltan_hasta(p_tabla text, p_obj bigint) RETURNS bigint
  LANGUAGE plpgsql AS $fn$
DECLARE v bigint;
BEGIN
  EXECUTE format('SELECT count(*) FROM public.%I', p_tabla) INTO v;
  RETURN GREATEST(p_obj - v, 0);
END $fn$;

-- Catalogos intactos, en arrays: evita una subconsulta correlacionada por fila.
DROP TABLE IF EXISTS carga.cat;
CREATE TABLE carga.cat AS
SELECT (SELECT array_agg(id_ciudad        ORDER BY 1) FROM ciudad)        AS ciudad,
       (SELECT array_agg(id_bodega        ORDER BY 1) FROM bodega)        AS bodega,
       (SELECT array_agg(id_categoria     ORDER BY 1) FROM categoria)     AS categoria,
       (SELECT array_agg(id_unidad_medida ORDER BY 1) FROM unidad_medida) AS um;

DROP TABLE IF EXISTS carga.voc;
CREATE TABLE carga.voc AS SELECT
  ARRAY['Ana','Luis','Maria','Carlos','Sofia','Diego','Valeria','Andres','Camila','Jorge',
        'Daniela','Pablo','Gabriela','Mateo','Lucia','Fernando','Paula','Ricardo','Elena','Javier'] AS nom,
  ARRAY['Perez','Gomez','Vaca','Andrade','Moreno','Zambrano','Cedeno','Villacis','Paredes','Naranjo',
        'Salazar','Jaramillo','Ortega','Guerrero','Loor','Bermeo','Yepez','Cabrera','Tapia','Merino'] AS ape,
  ARRAY['Nike','Adidas','Puma','Reebok','Umbro','Joma','Kappa','Asics','Mizuno','Diadora'] AS marca,
  ARRAY['Camiseta','Pantaloneta','Chompa','Zapatilla','Medias','Gorra','Mochila','Buzo','Licra','Canilleras'] AS art,
  ARRAY['Deportiva','Running','Training','Futbol','Basquet','Outdoor','Clasica','Pro','Elite','Urbana'] AS lin,
  ARRAY['Costa','Sierra','Oriente','Insular'] AS region;

-- Un hash bcrypt valido en formato pero de un secreto aleatorio que nadie
-- conoce: los usuarios de relleno no pueden iniciar sesion. Ademas no reciben
-- ninguna fila en usuario_rol, asi que tampoco tendrian permiso para nada.
DROP TABLE IF EXISTS carga.secreto;
CREATE TABLE carga.secreto AS
SELECT crypt(gen_random_uuid()::text || gen_random_uuid()::text, gen_salt('bf', 10)) AS inerte;
