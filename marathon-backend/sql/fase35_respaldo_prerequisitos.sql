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
    v_sw   text;
    v_keep text;
    v_wl   text;
BEGIN
    -- pg_reload_conf() es asincrono: se le da un momento al postmaster para
    -- procesar la senal antes de leer el valor efectivo.
    PERFORM pg_sleep(2);

    SELECT setting INTO v_sw   FROM pg_settings WHERE name = 'summarize_wal';
    SELECT setting INTO v_keep FROM pg_settings WHERE name = 'wal_summary_keep_time';
    SELECT setting INTO v_wl   FROM pg_settings WHERE name = 'wal_level';

    IF v_sw <> 'on' THEN
        RAISE EXCEPTION 'summarize_wal quedo en "%" y debe estar en "on". El respaldo diferencial no funcionara.', v_sw;
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
