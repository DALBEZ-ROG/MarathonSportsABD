-- =============================================================================
-- Fase 92 — Control de respaldos y auditoría desde la web
-- =============================================================================
-- Añade lo que le falta a la base para que la aplicación pueda:
--
--   (1) crear un respaldo lógico bajo demanda o a las 02:00,
--   (2) borrar los datos de negocio simulando un desastre,
--   (3) restaurar la base desde uno de esos respaldos,
--
-- y que quede constancia de las tres cosas en un sitio que NO se lleve por
-- delante ninguna de las tres.
--
-- ---------------------------------------------------------------------------
-- POR QUE UN ESQUEMA APARTE Y NO DOS TABLAS EN public
-- ---------------------------------------------------------------------------
-- Este es el punto que decide el diseño entero. El diario de respaldos tiene
-- que sobrevivir justo a las operaciones que registra:
--
--   - El BORRADO vacía las tablas de negocio. Si el diario viviera entre ellas,
--     el borrado se borraría a sí mismo del registro.
--   - La RESTAURACION reemplaza public con el contenido del volcado. Un diario
--     en public volvería al estado que tenía cuando se tomó ese respaldo, y
--     perdería precisamente la fila que dice «fulano restauró hoy».
--
-- Con el diario en un esquema propio, `pg_dump --exclude-schema=control` lo
-- deja fuera del volcado, y `pg_restore --clean` —que solo borra lo que el
-- volcado contiene— no lo toca. El diario es entonces la única cosa del sistema
-- que atraviesa un desastre simulado, que es exactamente lo que se le pide a
-- una bitácora de recuperación.
--
-- ---------------------------------------------------------------------------
-- OJO CON fase48_matriz_permisos.sql
-- ---------------------------------------------------------------------------
-- Ese script hace `DELETE FROM rol_permiso` y la reconstruye entera desde su
-- matriz. Si se vuelve a ejecutar DESPUES de este, se lleva los cuatro permisos
-- de 'respaldos' que se conceden abajo y la pantalla deja de abrirse. En ese
-- caso, volver a correr este script (es idempotente) o añadir las cuatro filas
-- a la matriz de la F48.
--
-- REVERSION: fase92_control_respaldos_rollback.sql
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. El esquema del diario
-- ---------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS control AUTHORIZATION postgres;

COMMENT ON SCHEMA control IS
    'Diario de respaldos, borrados y restauraciones (F92). Queda FUERA de '
    'pg_dump --exclude-schema=control a propósito: es lo único que debe '
    'sobrevivir a un borrado total y a una restauración.';

-- ---------------------------------------------------------------------------
-- 2. control.respaldo — un punto de recuperación
-- ---------------------------------------------------------------------------
-- `ruta` apunta a un directorio, no a un fichero: los volcados se hacen en
-- formato directorio (-Fd) para poder paralelizarlos con -j. Sobre esta base
-- (12 GB) eso es la diferencia entre 29 segundos y varios minutos, y es lo que
-- hace que el botón de la pantalla sea usable.
CREATE TABLE IF NOT EXISTS control.respaldo (
    id_respaldo   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre        varchar(120) NOT NULL UNIQUE,
    ruta          text         NOT NULL,
    origen        varchar(12)  NOT NULL,
    estado        varchar(12)  NOT NULL,
    fecha_inicio  timestamp    NOT NULL DEFAULT now(),
    fecha_fin     timestamp,
    duracion_ms   bigint,
    tamano_bytes  bigint,
    filas         bigint,
    id_usuario    integer,
    usuario_nombre varchar(120),
    nota          varchar(200),
    mensaje       text,
    CONSTRAINT chk_respaldo_origen CHECK (origen IN ('MANUAL', 'AUTOMATICO')),
    CONSTRAINT chk_respaldo_estado CHECK (estado IN ('EN_CURSO', 'COMPLETADO', 'FALLIDO'))
);

-- `usuario_nombre` se guarda COPIADO, no por clave ajena. No hay FK contra
-- public.usuario a propósito: una FK a una tabla que el borrado vacía y la
-- restauración reemplaza convertiría el diario en rehén de los datos que
-- vigila. El id se conserva para poder cruzarlo cuando la tabla exista; el
-- nombre, para poder leer el diario cuando no exista.

CREATE INDEX IF NOT EXISTS idx_respaldo_fecha  ON control.respaldo (fecha_inicio DESC);
CREATE INDEX IF NOT EXISTS idx_respaldo_estado ON control.respaldo (estado);

-- ---------------------------------------------------------------------------
-- 3. control.operacion — quién borró y quién restauró
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS control.operacion (
    id_operacion   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo           varchar(20) NOT NULL,
    id_respaldo    bigint REFERENCES control.respaldo (id_respaldo),
    estado         varchar(12) NOT NULL,
    fecha_inicio   timestamp   NOT NULL DEFAULT now(),
    fecha_fin      timestamp,
    duracion_ms    bigint,
    id_usuario     integer,
    usuario_nombre varchar(120),
    ip             varchar(45),
    filas_afectadas bigint,
    detalle        text,
    CONSTRAINT chk_operacion_tipo   CHECK (tipo IN ('BORRADO_TOTAL', 'RESTAURACION')),
    CONSTRAINT chk_operacion_estado CHECK (estado IN ('EN_CURSO', 'COMPLETADO', 'FALLIDO'))
);

CREATE INDEX IF NOT EXISTS idx_operacion_fecha ON control.operacion (fecha_inicio DESC);

-- ---------------------------------------------------------------------------
-- 4. Privilegios
-- ---------------------------------------------------------------------------
-- Solo el administrador. Los cinco roles operativos no entran al esquema: no
-- tienen por qué saber cuándo se respalda ni desde dónde se restaura.
--
-- El supervisor LEE el diario pero no lo escribe, igual que ya pasa con
-- auditoria_cambios (ver AUDITORIA.md §2): puede auditar la recuperación sin
-- poder dispararla.
REVOKE ALL ON SCHEMA control FROM PUBLIC;

GRANT USAGE ON SCHEMA control TO rol_administrador;
GRANT USAGE ON SCHEMA control TO rol_supervisor;

GRANT SELECT, INSERT, UPDATE ON control.respaldo  TO rol_administrador;
GRANT SELECT, INSERT, UPDATE ON control.operacion TO rol_administrador;
GRANT SELECT                 ON control.respaldo  TO rol_supervisor;
GRANT SELECT                 ON control.operacion TO rol_supervisor;

-- Sin DELETE para nadie, ni siquiera para el administrador. Es la misma regla
-- que auditoria_cambios: una bitácora que el auditado puede depurar no prueba
-- nada. Purgar es cosa de `postgres`.

-- Las secuencias de las columnas IDENTITY necesitan su propio permiso o el
-- INSERT falla con «permission denied for sequence».
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA control TO rol_administrador;

-- ---------------------------------------------------------------------------
-- 5. Los permisos de la aplicación
-- ---------------------------------------------------------------------------
-- Cuatro, y separados a propósito. 'ver' es leer el diario; 'crear' es tomar un
-- respaldo (no destruye nada); 'restaurar' y 'borrar' sí destruyen, y tenerlos
-- aparte permite dar el botón de respaldar a alguien sin darle el de borrar.
INSERT INTO permiso (modulo, accion, descripcion)
SELECT v.modulo, v.accion, v.descripcion
FROM (VALUES
    ('respaldos', 'ver',       'respaldos:ver'),
    ('respaldos', 'crear',     'respaldos:crear'),
    ('respaldos', 'restaurar', 'respaldos:restaurar'),
    ('respaldos', 'borrar',    'respaldos:borrar')
) AS v(modulo, accion, descripcion)
WHERE NOT EXISTS (
    SELECT 1 FROM permiso p WHERE p.modulo = v.modulo AND p.accion = v.accion
);

INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM rol r
JOIN permiso p ON p.modulo = 'respaldos'
WHERE r.nombre = 'Administrador'
  AND NOT EXISTS (
      SELECT 1 FROM rol_permiso rp
      WHERE rp.id_rol = r.id_rol AND rp.id_permiso = p.id_permiso
  );

-- El supervisor solo mira.
INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM rol r
JOIN permiso p ON p.modulo = 'respaldos' AND p.accion = 'ver'
WHERE r.nombre = 'Supervisor E-Commerce'
  AND NOT EXISTS (
      SELECT 1 FROM rol_permiso rp
      WHERE rp.id_rol = r.id_rol AND rp.id_permiso = p.id_permiso
  );

-- ---------------------------------------------------------------------------
-- 6. auditoria_cambios se lee desde la web
-- ---------------------------------------------------------------------------
-- El GRANT ya lo dio la F40 (SELECT a rol_administrador y rol_supervisor), así
-- que aquí no hay nada que conceder. Se deja la comprobación porque la pantalla
-- nueva depende de ello y fallar aquí es más barato que fallar en un 403 sin
-- explicación.
DO $$
BEGIN
    IF NOT has_table_privilege('rol_administrador', 'public.auditoria_cambios', 'SELECT') THEN
        RAISE EXCEPTION 'rol_administrador no puede leer auditoria_cambios. '
                        'Debería tenerlo desde la F40 (ver AUDITORIA.md §2). '
                        'Sin eso la pestaña «Cambios en datos» devuelve 403.';
    END IF;
END $$;

COMMIT;

-- ---------------------------------------------------------------------------
-- 7. Los índices que las pantallas de auditoría necesitan
-- ---------------------------------------------------------------------------
-- ESTO NO ES UN EXTRA. Medido en esta base antes de tocar nada:
--
--   SELECT ... FROM log_accion ORDER BY fecha DESC, id_log DESC LIMIT 20
--       -> 9,8 SEGUNDOS
--   SELECT count(*) FROM log_accion WHERE modulo = 'pedidos'
--       -> 191 ms
--
-- `log_accion` tenía UN solo índice, el de la clave primaria. Con 200.000 filas
-- eso no se notaba; con el 1,5 M de la F91, la primera página de la pestaña
-- «Log de acciones» ordena la tabla entera cada vez que alguien la abre. Añadir
-- filtros por usuario y por módulo encima de eso habría multiplicado el
-- problema en lugar de resolverlo.
--
-- Los índices van FUERA de la transacción de arriba: son CONCURRENTLY para no
-- bloquear la escritura de la bitácora mientras se construyen, y CONCURRENTLY
-- no puede ejecutarse dentro de un bloque de transacción.
--
-- El orden de las columnas no es cosmético: (id_usuario, fecha DESC) sirve para
-- «lo que hizo esta persona, lo más reciente primero», que es la consulta de la
-- pantalla; al revés no serviría.

-- La lista sin filtros, que es como se abre la pantalla.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_log_accion_fecha
    ON log_accion (fecha DESC, id_log DESC);

-- «¿Qué hizo esta persona?»
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_log_accion_usuario
    ON log_accion (id_usuario, fecha DESC);

-- «¿Qué pasó en este módulo?»
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_log_accion_modulo
    ON log_accion (modulo, fecha DESC);

-- auditoria_cambios ya traía de la F40 los índices por fecha, por (tabla,
-- pk_valor) y por usuario_app. Faltaban los dos que estrena esta fase: filtrar
-- por tabla dentro de un rango de fechas, y agrupar por transacción para ver
-- «todo lo que se cambió en un mismo acto».
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_auditoria_tabla_fecha
    ON auditoria_cambios (tabla, fecha DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_auditoria_txid
    ON auditoria_cambios (txid);

-- El rastro por usuario cruza también el historial de inventario, que solo
-- tenía índice por fecha y por inventario.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_historial_usuario
    ON historial_inventario (id_usuario, fecha DESC);

-- Verificación
--   SELECT count(*) FROM permiso WHERE modulo = 'respaldos';          -- 4
--   SELECT r.nombre, count(*) FROM rol r
--     JOIN rol_permiso rp ON rp.id_rol = r.id_rol
--     JOIN permiso p ON p.id_permiso = rp.id_permiso
--    WHERE p.modulo = 'respaldos' GROUP BY 1;   -- Administrador 4, Supervisor 1
--   \dn control
--   \dp control.*
