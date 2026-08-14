-- ============================================================================
-- FASE 36 — AUDITORIA A NIVEL DE BASE DE DATOS (alternativa nativa a pgAudit)
-- ----------------------------------------------------------------------------
-- Script IDEMPOTENTE. Todos los parametros que toca son de contexto 'sighup' o
-- 'superuser': se aplican con recarga de configuracion, SIN reiniciar.
--
-- POR QUE NO SE USA pgAudit
--   Comprobado contra el catalogo de esta instalacion:
--       SELECT count(*) FROM pg_available_extensions WHERE name='pgaudit';  -> 0
--   La extension NO esta disponible. No es que no este instalada: el binario no
--   viene con la distribucion de PostgreSQL para Windows. CREATE EXTENSION
--   pgaudit falla con "extension no disponible".
--   Ademas pgAudit exige cargarse en shared_preload_libraries, que es de
--   contexto 'postmaster', asi que incluso teniendo el binario haria falta
--   DETENER Y ARRANCAR el servicio de base de datos.
--   Compilarlo desde fuente con MSVC para este proyecto no se justifica.
--   Analisis completo en DECISION_PGAUDIT.md.
--
-- QUE SE CONSIGUE CON EL REGISTRO NATIVO
--   Cubre lo esencial de lo que se le pedia a pgAudit: quien ejecuto que
--   sentencia, cuando, desde donde y con que resultado. Lo que NO cubre es el
--   filtrado fino por objeto que pgAudit permite (auditar solo ciertas tablas),
--   ni el formato estructurado de sus entradas. Esa diferencia queda declarada.
-- ============================================================================

\echo '=== FASE 36: auditoria nativa a nivel de base de datos ==='

-- ============================================================================
-- 1. QUE SE REGISTRA
-- ============================================================================
-- 'mod' registra toda sentencia que MODIFICA datos o esquema:
--   INSERT, UPDATE, DELETE, TRUNCATE, COPY FROM  +  todo el DDL.
-- Equivale a lo que se pedia con pgaudit.log = 'ddl, write'.
-- NO registra los SELECT, y es deliberado: los reportes del sistema generan
-- miles de consultas de lectura y ahogarian el registro, dejando lo que
-- importa (las modificaciones) sepultado entre ruido.
ALTER SYSTEM SET log_statement = 'mod';

-- ============================================================================
-- 2. QUIEN Y DESDE DONDE  — el prefijo es lo que hace util el registro
-- ============================================================================
-- El valor por defecto de esta instalacion era '%t ', es decir SOLO la fecha.
-- Un registro que dice "se borro una fila" sin decir quien la borro no sirve
-- para auditar nada. Se agregan usuario, base, host de origen, PID y
-- aplicacion.
--   %m  fecha y hora con milisegundos
--   %u  usuario de base de datos     <- la atribucion
--   %d  base de datos
--   %r  host y puerto del cliente    <- el origen
--   %p  PID del proceso servidor
--   %a  nombre de la aplicacion      <- distingue el backend de un psql manual
--   %x  id de transaccion
ALTER SYSTEM SET log_line_prefix = '%m [%p] usuario=%u base=%d origen=%r app=%a xid=%x ';

-- ============================================================================
-- 3. SESIONES
-- ============================================================================
-- Conexiones y desconexiones: permite reconstruir quien estuvo conectado y
-- cuanto duro su sesion. Es la mitad del rastro que falta cuando solo se
-- registran sentencias.
ALTER SYSTEM SET log_connections    = 'all';
ALTER SYSTEM SET log_disconnections = 'on';

-- ============================================================================
-- 4. SENTENCIAS QUE FALLAN Y SENTENCIAS LENTAS
-- ============================================================================
-- Registrar la sentencia completa cuando hay un error de cualquier nivel:
-- los intentos RECHAZADOS son justamente lo mas interesante de una auditoria
-- de seguridad. Un "permiso denegado" o un trigger de proteccion disparandose
-- deja rastro con la sentencia que lo provoco.
ALTER SYSTEM SET log_min_error_statement = 'warning';

-- Toda sentencia de mas de 1 segundo. Sirve a la vez para auditoria y para
-- detectar regresiones de rendimiento sobre los indices de la F33.
ALTER SYSTEM SET log_min_duration_statement = '1s';

-- Esperas por bloqueo: sintoma tipico de un proceso que se quedo con una
-- transaccion abierta o de contencion real.
ALTER SYSTEM SET log_lock_waits = 'on';

-- ============================================================================
-- 5. DONDE QUEDAN LOS REGISTROS Y CUANTO SE CONSERVAN
-- ============================================================================
-- logging_collector ya estaba en 'on' (es de contexto postmaster; si estuviera
-- apagado haria falta reiniciar, asi que conviene que ya lo este).
-- Los archivos van a  <directorio de datos>\log
--   C:\Program Files\PostgreSQL\18\data\log
ALTER SYSTEM SET log_directory = 'log';

-- Un archivo por dia con el dia de la semana en el nombre: al reutilizarse cada
-- 7 dias, la retencion queda acotada sin necesidad de un proceso de limpieza.
-- RETENCION EFECTIVA: 7 dias.
ALTER SYSTEM SET log_filename = 'postgresql-%a.log';
ALTER SYSTEM SET log_truncate_on_rotation = 'on';
ALTER SYSTEM SET log_rotation_age = '1d';

-- Sin limite por tamano: con rotacion por dia de la semana, cortar por tamano
-- provocaria perder lo mas reciente del dia al truncar.
ALTER SYSTEM SET log_rotation_size = 0;

SELECT pg_reload_conf();

-- ============================================================================
-- 6. AUTOVERIFICACION
-- ----------------------------------------------------------------------------
-- SE VERIFICA CONTRA pg_file_settings, NO CONTRA pg_settings.
-- Motivo: pg_settings muestra lo que tiene cargado ESTA sesion, y la senal de
-- pg_reload_conf() la reparte el postmaster entre sus procesos hijos; el
-- backend que esta corriendo este script no la procesa hasta despues de
-- terminar la sentencia en curso. Comprobar pg_settings aqui daba un fallo
-- falso: los parametros ya estaban escritos y aplicados en el servidor, pero
-- la propia sesion todavia leia los valores viejos.
-- pg_file_settings lee postgresql.auto.conf desde disco y su columna 'applied'
-- dice si el servidor acepto cada linea, que es justo lo que hay que verificar.
-- ============================================================================
DO $$
DECLARE
    v_esperado record;
    v_real     text;
    v_aplicado boolean;
    v_fallos   text := '';
BEGIN
    FOR v_esperado IN
        SELECT * FROM (VALUES
            ('log_statement',           'mod'),
            ('log_connections',         'all'),
            ('log_disconnections',      'on'),
            ('log_min_error_statement', 'warning'),
            ('log_lock_waits',          'on'),
            ('log_truncate_on_rotation','on')
        ) AS t(param, valor)
    LOOP
        SELECT setting, applied INTO v_real, v_aplicado
        FROM pg_file_settings
        WHERE name = v_esperado.param AND sourcefile LIKE '%postgresql.auto.conf';

        IF v_real IS DISTINCT FROM v_esperado.valor THEN
            v_fallos := v_fallos || format('%s=%s (esperado %s); ',
                                           v_esperado.param, coalesce(v_real, 'ausente'),
                                           v_esperado.valor);
        ELSIF NOT v_aplicado THEN
            v_fallos := v_fallos || format('%s escrito pero NO aplicado por el servidor; ',
                                           v_esperado.param);
        END IF;
    END LOOP;

    IF v_fallos <> '' THEN
        RAISE EXCEPTION 'FASE 36 incompleta: %', v_fallos;
    END IF;

    -- El prefijo debe incluir la atribucion; sin %u el registro no sirve
    SELECT setting INTO v_real
    FROM pg_file_settings
    WHERE name = 'log_line_prefix' AND sourcefile LIKE '%postgresql.auto.conf';
    IF v_real IS NULL OR v_real NOT LIKE '%\%u%' THEN
        RAISE EXCEPTION 'log_line_prefix no incluye %%u: el registro no permitiria atribuir las acciones a un usuario';
    END IF;

    IF NOT (SELECT setting::bool FROM pg_settings WHERE name = 'logging_collector') THEN
        RAISE WARNING 'logging_collector esta apagado. Es de contexto postmaster: para activarlo hay que REINICIAR el servicio. Sin el, los registros van a stderr y se pierden.';
    END IF;

    RAISE NOTICE 'FASE 36 OK: auditoria nativa activa (DDL + escrituras + sesiones + errores + lentas).';
    RAISE NOTICE 'Registros en: %\log', (SELECT setting FROM pg_settings WHERE name='data_directory');
END $$;

-- ============================================================================
-- 7. QUE SE AUDITA DE CADA CAPA — resumen operativo
-- ============================================================================
-- CAPA DE BASE DE DATOS  (esta fase)
--   Toda modificacion de datos y de esquema, con usuario, origen y aplicacion.
--   Cubre el acceso DIRECTO por psql o por cualquier cliente, que es
--   precisamente lo que la aplicacion no puede registrar porque no pasa por
--   ella.
--
-- CAPA DE APLICACION  (tabla log_accion, ya existente desde F19b)
--   Acciones de negocio con el usuario de NEGOCIO (usuario.id_usuario), no el
--   de base de datos: modulo, accion, descripcion, IP. La base no puede
--   registrar esto porque todas las sesiones llegan con el mismo usuario de
--   conexion; solo la aplicacion sabe que persona autenticada esta detras.
--
-- Las dos capas son complementarias y ninguna sustituye a la otra:
--   - log_accion sin auditoria de BD: no ve a quien entra por psql.
--   - Auditoria de BD sin log_accion: ve la sentencia pero no que persona la
--     origino, porque la aplicacion multiplexa todas las sesiones.
-- ============================================================================

SELECT name, setting, context
FROM pg_settings
WHERE name IN ('log_statement','log_line_prefix','log_connections','log_disconnections',
               'log_min_error_statement','log_min_duration_statement','log_lock_waits',
               'logging_collector','log_directory','log_filename','log_truncate_on_rotation')
ORDER BY name;
