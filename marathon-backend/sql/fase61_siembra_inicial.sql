-- =============================================================================
-- Fase 61 — Siembra inicial: roles y primer administrador (cierra D-26)
-- =============================================================================
-- POR QUE EXISTE ESTE SCRIPT
-- `app.datos-demo.enabled` estaba en `true` y no se podia apagar, porque
-- `DataInitializer` no solo crea los cinco usuarios de demostracion: tambien
-- crea LOS ROLES y el primer administrador. Apagarlo dejaba una base sin roles,
-- sin usuarios y sin forma de entrar. Ese era el nudo de D-26.
--
-- La siembra estructural se saca de la aplicacion y se pone aqui, que es donde
-- ya vive todo lo demas que toca el esquema (regla 3 de PENDIENTE.md: la
-- aplicacion NUNCA migra, migran los scripts).
--
-- HAY UNA SEGUNDA RAZON, Y ES MAS GRAVE QUE D-26
-- En su primera ejecucion —base vacia— `DataInitializer` no se limitaba a crear
-- roles: repartia 49 permisos con un criterio propio. Desde la F48 el reparto
-- bueno son 94 permisos derivados de `SecurityConfig`. Es decir: una
-- instalacion NUEVA nacia con la matriz vieja y contradictoria, mientras que
-- este equipo, que venia de antes, tenia la buena. Nadie lo habria notado hasta
-- montar el sistema en otra maquina.
--
-- ORDEN DE INSTALACION EN UNA BASE NUEVA
--   1. esquema  2. fase61 (esta)  3. resto de fases  4. fase48 (la matriz)
-- La F48 ABORTA si falta algun rol, asi que esta tiene que ir antes.
--
-- COMO SE CREA EL PRIMER ADMINISTRADOR
-- No se crea aqui con una contrasena fija: una contrasena en un fichero que va
-- a Git no es una contrasena. Se pasa el hash BCrypt por variable de psql:
--
--   psql ... -v hash_admin="'$2a$10$...'" -f fase61_siembra_inicial.sql
--
-- Si no se pasa la variable, el script siembra SOLO los roles y lo dice. Eso es
-- lo correcto en este equipo, donde el administrador ya existe.
--
-- Para generar el hash sin instalar nada:
--   htpasswd -bnBC 10 "" 'LaContrasenaQueSea' | tr -d ':\n' | sed 's/^\$2y/\$2a/'
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Los seis roles. Idempotente: repetir el script no duplica nada.
--    Los nombres son EXACTOS: `SecurityConfig` compara contra ellos
--    (`ROLE_ENCARGADO DE PRODUCCIÓN`, con tilde) y la F48 aborta si no cuadran.
-- ---------------------------------------------------------------------------
INSERT INTO rol (nombre, descripcion) VALUES
    ('Administrador',           'Gestión total del sistema'),
    ('Supervisor E-Commerce',   'Dashboard, KPIs y reportes'),
    ('Operador de Bodega',      'Picking, empaque y stock'),
    ('Operador de Pedidos',     'Registro y seguimiento de pedidos'),
    ('Encargado de Compras',    'Órdenes de compra, recepciones y cuentas por pagar'),
    ('Encargado de Producción', 'Órdenes de producción, materia prima y costos')
ON CONFLICT (nombre) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. El primer administrador, solo si se paso el hash y solo si no hay ninguno.
-- ---------------------------------------------------------------------------
\if :{?hash_admin}
DO $$
DECLARE
    id_admin INT;
    id_rol_admin INT;
BEGIN
    IF EXISTS (SELECT 1 FROM usuario) THEN
        RAISE NOTICE 'Ya hay usuarios: no se crea ningun administrador.';
        RETURN;
    END IF;

    SELECT r.id_rol INTO id_rol_admin FROM rol r WHERE r.nombre = 'Administrador';

    INSERT INTO usuario (nombre, apellido, correo, password, estado)
    VALUES ('Admin', 'Marathon', 'admin@marathon.com', :hash_admin, 'activo')
    RETURNING id_usuario INTO id_admin;

    INSERT INTO usuario_rol (id_usuario, id_rol) VALUES (id_admin, id_rol_admin);

    RAISE NOTICE 'Administrador creado: admin@marathon.com';
END $$;
\else
\echo '  (sin hash_admin: se siembran solo los roles, no se crea administrador)'
\endif

-- ---------------------------------------------------------------------------
-- 3. Comprobacion dentro de la transaccion: los seis o ninguno.
-- ---------------------------------------------------------------------------
DO $$
DECLARE faltan TEXT;
BEGIN
    SELECT string_agg(n, ', ') INTO faltan
      FROM (VALUES ('Administrador'), ('Supervisor E-Commerce'), ('Operador de Bodega'),
                   ('Operador de Pedidos'), ('Encargado de Compras'),
                   ('Encargado de Producción')) AS v(n)
     WHERE NOT EXISTS (SELECT 1 FROM rol r WHERE r.nombre = v.n);

    IF faltan IS NOT NULL THEN
        RAISE EXCEPTION 'Faltan roles tras la siembra: %. La F48 abortaria despues.', faltan;
    END IF;
END $$;

COMMIT;

-- Verificacion
--   SELECT nombre FROM rol ORDER BY nombre;     -- esperado: los seis
--   SELECT count(*) FROM usuario;               -- 1 en instalacion nueva
