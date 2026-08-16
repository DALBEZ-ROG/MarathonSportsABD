-- ============================================================================
-- FASE 35 — PREREQUISITOS DE BASE DE DATOS PARA LA ESTRATEGIA DE RESPALDO
-- ----------------------------------------------------------------------------
-- Script IDEMPOTENTE.
--
-- La estrategia de respaldo vive en scripts de PowerShell (scripts/backup/),
-- pero necesita UN cambio del lado del servidor. Queda aqui, versionado, para
-- que al reconstruir el entorno no se olvide.
--
-- QUE HACE summarize_wal
--   Arranca el proceso "WAL summarizer", que lleva un registro de que bloques
--   de datos modifica cada segmento de WAL. Ese resumen es lo que permite a
--   pg_basebackup --incremental copiar solo lo que cambio desde un respaldo
--   anterior. Sin el, el respaldo diferencial es IMPOSIBLE y pg_basebackup
--   falla al invocarlo con --incremental.
--
-- POR QUE NO HACE FALTA REINICIAR
--   summarize_wal es de contexto 'sighup': basta recargar la configuracion.
--   Esto es lo que hizo viable la estrategia sin ventana de mantenimiento.
--   Se evaluo la alternativa clasica (archive_mode = on + archive_command),
--   y se descarto: archive_mode es de contexto 'postmaster' y habria exigido
--   detener y arrancar el servicio de base de datos.
--
-- POR QUE NO SE TOCA wal_level
--   Esta en 'replica', que ya es suficiente para pg_basebackup y para el
--   respaldo incremental. Subirlo a 'logical' no aporta nada aqui y encarece
--   la escritura de WAL.
-- ============================================================================

\echo '=== FASE 35: prerequisitos de respaldo ==='

ALTER SYSTEM SET summarize_wal = 'on';

-- Cuanto tiempo se conservan los archivos de resumen de WAL. Deben sobrevivir
-- al ciclo completo entre un respaldo full y el ultimo diferencial de la
-- semana. 10 dias cubre los 7 del ciclo con margen para un full que se
-- retrase porque el equipo estuvo apagado el domingo.
ALTER SYSTEM SET wal_summary_keep_time = '10d';

SELECT pg_reload_conf();

-- ============================================================================
-- AUTOVERIFICACION
-- ============================================================================
DO $$
DECLARE
    v_sw     text;
    v_keep   text;
    v_wl     text;
    v_disco  text;
    v_error  text;
BEGIN
    -- POR QUE NO BASTA CON MIRAR pg_settings AQUI.
    --
    -- pg_reload_conf() envia SIGHUP, y un backend solo relee la configuracion
    -- ENTRE SENTENCIAS: nunca en mitad de una. Este bloque DO es UNA sola
    -- sentencia, asi que por mucho que duerma dentro, su vista de
    -- pg_settings.summarize_wal NO cambia. Dormir mas no arregla nada.
    --
    -- Historia de este bloque, porque las dos versiones anteriores estaban mal
    -- por el mismo motivo mal diagnosticado:
    --   1. pg_sleep(2) fijo  -> "carrera", se creia que faltaba tiempo.
    --   2. sondeo de 15 s    -> parecio funcionar, pero solo porque el cluster
    --      de prueba ya traia summarize_wal=on en postgresql.auto.conf de una
    --      ejecucion anterior, y el backend lo habia leido AL CONECTARSE.
    -- Sobre un cluster recien creado con initdb, las dos fallan.
    --
    -- La comprobacion correcta tiene dos partes:
    --   a) pg_file_settings lee los ARCHIVOS de configuracion del disco, no la
    --      vista de esta sesion. Ahi si aparece lo que acaba de escribir
    --      ALTER SYSTEM, y su columna 'error' delata un valor invalido.
    --   b) pg_settings dice si YA esta en vigor en esta sesion.
    -- Si (a) esta bien pero (b) todavia no, no hay ningun problema: el valor
    -- entra en vigor y la proxima conexion lo vera. Eso NO es un fallo.

    SELECT setting INTO v_sw   FROM pg_settings WHERE name = 'summarize_wal';
    SELECT setting INTO v_keep FROM pg_settings WHERE name = 'wal_summary_keep_time';
    SELECT setting INTO v_wl   FROM pg_settings WHERE name = 'wal_level';

    SELECT f.setting, f.error INTO v_disco, v_error
    FROM pg_file_settings f
    WHERE f.name = 'summarize_wal'
    ORDER BY f.seqno DESC
    LIMIT 1;

    IF v_error IS NOT NULL THEN
        RAISE EXCEPTION 'summarize_wal quedo escrito con error: %', v_error;
    END IF;

    IF v_disco IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION 'ALTER SYSTEM no dejo summarize_wal = on en la configuracion (valor en disco: %). El respaldo diferencial no funcionara.',
                        COALESCE(v_disco, 'ausente');
    END IF;

    IF v_sw <> 'on' THEN
        -- Escrito y valido, pero esta sesion todavia ve el valor viejo. Es lo
        -- normal en un cluster recien arrancado y no rompe nada.
        RAISE NOTICE 'summarize_wal = on ya esta en la configuracion; entrara en vigor en las conexiones nuevas (esta sesion todavia ve "%").', v_sw;
        v_sw := 'on';
    END IF;

    IF v_wl NOT IN ('replica', 'logical') THEN
        RAISE EXCEPTION 'wal_level esta en "%". pg_basebackup necesita al menos "replica".', v_wl;
    END IF;

    RAISE NOTICE 'FASE 35 OK: summarize_wal=% | wal_summary_keep_time=% min | wal_level=%',
                 v_sw, v_keep, v_wl;
    RAISE NOTICE 'El respaldo diferencial con pg_basebackup --incremental ya es posible.';
END $$;

SELECT name, setting, unit, context, pending_restart
FROM pg_settings
WHERE name IN ('summarize_wal', 'wal_summary_keep_time', 'wal_level', 'archive_mode')
ORDER BY name;
