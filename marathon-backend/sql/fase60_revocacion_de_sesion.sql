-- =============================================================================
-- Fase 60 — Revocacion real de sesion (cierra D-23)
-- =============================================================================
-- POR QUE
-- `POST /api/auth/logout` devolvia {"message":"Sesion cerrada correctamente"} y
-- NO HACIA NADA. El token seguia siendo valido hasta su expiracion: cerrar
-- sesion en un ordenador prestado no cerraba nada. La F-anterior redujo la
-- ventana de 24 h a 2 h, que es una mitigacion, no un cierre.
--
-- COMO SE CIERRA
-- Un JWT no se puede "borrar": esta firmado y el servidor no guarda estado. La
-- unica revocacion posible es una LISTA DE DENEGACION que se consulte en cada
-- peticion. Para poder nombrar un token concreto sin guardarlo entero, la F60
-- le anade un identificador unico (claim `jti`) y aqui se guardan los jti
-- revocados.
--
-- SE GUARDA EL jti, NO EL TOKEN. El token es la credencial; si esta tabla se
-- filtra, un jti no sirve para entrar en ningun sitio.
--
-- EL COSTO, DICHO CLARO: una consulta por clave primaria en cada peticion
-- autenticada. Es el precio de la revocacion real y por eso estaba descartada.
-- La tabla se mantiene pequena purgando lo expirado (ver mas abajo).
-- =============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS token_revocado (
    jti               VARCHAR(36)  PRIMARY KEY,
    correo            VARCHAR(150) NOT NULL,
    tipo              VARCHAR(10)  NOT NULL,
    fecha_revocacion  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_expiracion  TIMESTAMP    NOT NULL,
    CONSTRAINT chk_token_revocado_tipo CHECK (tipo IN ('acceso', 'refresco'))
);

COMMENT ON TABLE token_revocado IS
    'Lista de denegacion de sesiones (F60, D-23). Una fila por token revocado. '
    'Se guarda el identificador jti, nunca el token. Las filas con '
    'fecha_expiracion pasada se pueden borrar sin consecuencia: ese token ya '
    'lo rechaza la comprobacion de expiracion.';

COMMENT ON COLUMN token_revocado.fecha_expiracion IS
    'Cuando habria caducado el token por si mismo. Marca desde cuando esta fila '
    'sobra: a partir de ahi el token se rechaza por expirado, revocado o no.';

-- La purga barre por fecha_expiracion, no por la clave primaria.
CREATE INDEX IF NOT EXISTS idx_token_revocado_expiracion
    ON token_revocado (fecha_expiracion);

-- ---------------------------------------------------------------------------
-- Privilegios. Regla 4 de PENDIENTE.md: una tabla nueva nace SIN NADA.
-- ---------------------------------------------------------------------------
-- SELECT para los seis: la comprobacion corre en cada peticion, con el rol de
-- quien la hace.
GRANT SELECT ON token_revocado TO rol_administrador;
GRANT SELECT ON token_revocado TO rol_supervisor;
GRANT SELECT ON token_revocado TO rol_operador_bodega;
GRANT SELECT ON token_revocado TO rol_operador_pedidos;
GRANT SELECT ON token_revocado TO rol_encargado_compras;
GRANT SELECT ON token_revocado TO rol_encargado_produccion;

-- INSERT para los seis: cualquiera puede cerrar SU sesion, y el cierre corre
-- bajo su propio rol (el filtro ya lo autentico cuando llega a /logout).
GRANT INSERT ON token_revocado TO rol_administrador;
GRANT INSERT ON token_revocado TO rol_supervisor;
GRANT INSERT ON token_revocado TO rol_operador_bodega;
GRANT INSERT ON token_revocado TO rol_operador_pedidos;
GRANT INSERT ON token_revocado TO rol_encargado_compras;
GRANT INSERT ON token_revocado TO rol_encargado_produccion;

-- DELETE para los seis, y conviene explicar por que no es un agujero:
-- la aplicacion solo borra filas YA EXPIRADAS
-- (TokenRevocadoRepository.purgarExpirados). Borrar una fila expirada no
-- devuelve la vida a nada: ese token lo rechaza igualmente la comprobacion de
-- expiracion, que es anterior e independiente de esta tabla. Sin DELETE, la
-- purga tendria que correr solo bajo el administrador y la tabla creceria sin
-- limite en los demas casos.
GRANT DELETE ON token_revocado TO rol_administrador;
GRANT DELETE ON token_revocado TO rol_supervisor;
GRANT DELETE ON token_revocado TO rol_operador_bodega;
GRANT DELETE ON token_revocado TO rol_operador_pedidos;
GRANT DELETE ON token_revocado TO rol_encargado_compras;
GRANT DELETE ON token_revocado TO rol_encargado_produccion;

-- No hay secuencia que conceder: la clave primaria es el jti, que lo genera la
-- aplicacion. Es la primera tabla del proyecto sin PK IDENTITY, y es a
-- proposito: el identificador tiene que ser el mismo que viaja dentro del JWT.

COMMIT;

-- Verificacion
--   SELECT grantee, privilege_type
--     FROM information_schema.role_table_grants
--    WHERE table_name = 'token_revocado' ORDER BY 1, 2;
--   -- OJO: para usr_admin_marathon esta vista devuelve vacio (regla 7 de
--   -- PENDIENTE.md). La lectura fiable es:
--   --   SELECT a.grantee::regrole::text, a.privilege_type
--   --     FROM pg_class c, aclexplode(c.relacl) a
--   --    WHERE c.relname = 'token_revocado';
--   -- esperado: SELECT, INSERT y DELETE para los seis rol_*.

-- Purga manual, si alguna vez hace falta a mano (la aplicacion ya la hace en
-- cada cierre de sesion):
--   DELETE FROM token_revocado WHERE fecha_expiracion < now();
