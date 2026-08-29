-- =============================================================================
-- Fase 73 — El cliente tiene documento, y no solo cedula
-- =============================================================================
-- LO QUE ESTABA MAL, Y ES PEOR DE LO QUE PARECIA
-- El formulario de clientes pedia la cedula, la marcaba como OBLIGATORIA, y el
-- DTO la llevaba de ida y vuelta... pero **la tabla `cliente` no tiene ninguna
-- columna de documento**. Nadie la guardaba y nadie la leia: se escribia, se
-- enviaba, y se perdia por el camino. Por eso la columna "Cedula" del listado
-- salia vacia en los 5.000 clientes.
--
-- No era una funcion a medias: era un campo que mentia. Se pedia un dato
-- obligatorio para tirarlo.
--
-- LO QUE PIDIO EL DUEÑO
-- «lo de cliente no solo tiene cedula sino tambien ruc». Tiene razon, y en
-- Ecuador son tres documentos distintos con reglas distintas:
--
--     cedula     10 digitos          persona natural
--     RUC        13 digitos          empresa o persona con actividad economica
--     pasaporte  alfanumerico        extranjero sin cedula
--
-- Por eso no basta con anadir una columna «ruc» al lado de «cedula»: un cliente
-- tiene UN documento, de un TIPO. Dos columnas obligarian a decidir en cada
-- consulta cual mirar, y admitirian el estado imposible de tener las dos.
--
-- LO QUE SE ANADE
--     tipo_documento    cedula | ruc | pasaporte
--     numero_documento  el numero, sin puntos ni guiones
--
-- Las dos ADMITEN NULO, y es deliberado: los 5.000 clientes que ya existen no
-- tienen documento y **no se lo vamos a inventar** (regla de PENDIENTE.md §5:
-- no se reparan datos historicos). Quedan sin documento hasta que alguien los
-- edite, y la pantalla lo enseña como «sin documento» en vez de como un hueco.
--
-- EL NUMERO ES UNICO, pero solo entre los que lo tienen: un indice parcial deja
-- convivir los 5.000 nulos con la garantia de que no se repita un documento de
-- verdad. Dos clientes con la misma cedula son el mismo cliente dos veces.
--
-- NO SE CIFRA, a diferencia de correo, telefono y direccion. Es una decision:
-- el documento hay que poder BUSCARLO —es como se identifica a un cliente en
-- mostrador— y un valor cifrado no se puede buscar por prefijo ni indexar para
-- unicidad. Correo y telefono se cifran porque solo se leen; este se consulta.
-- =============================================================================

BEGIN;

ALTER TABLE cliente
    ADD COLUMN IF NOT EXISTS tipo_documento VARCHAR(10) NULL;

ALTER TABLE cliente
    ADD COLUMN IF NOT EXISTS numero_documento VARCHAR(20) NULL;

COMMENT ON COLUMN cliente.tipo_documento IS
    'cedula | ruc | pasaporte. NULL en los clientes anteriores a la F73, que no '
    'tenian documento: no se les inventa uno.';

COMMENT ON COLUMN cliente.numero_documento IS
    'El numero, sin puntos ni guiones. Unico entre los que lo tienen. NO se '
    'cifra a proposito: hay que poder buscar por el, y lo cifrado no se busca.';

-- Los tres tipos, y nada mas.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_cliente_tipo_documento') THEN
        ALTER TABLE cliente
            ADD CONSTRAINT chk_cliente_tipo_documento
            CHECK (tipo_documento IS NULL
                   OR tipo_documento IN ('cedula', 'ruc', 'pasaporte'));
    END IF;
END $$;

-- Los dos campos van juntos o no van: un numero sin tipo no se sabe leer, y un
-- tipo sin numero no identifica a nadie.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_cliente_documento_completo') THEN
        ALTER TABLE cliente
            ADD CONSTRAINT chk_cliente_documento_completo
            CHECK ((tipo_documento IS NULL AND numero_documento IS NULL)
                OR (tipo_documento IS NOT NULL AND numero_documento IS NOT NULL));
    END IF;
END $$;

-- La longitud que corresponde a cada tipo. El pasaporte queda libre porque cada
-- pais tiene el suyo, pero al menos ha de tener algo.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_cliente_documento_formato') THEN
        ALTER TABLE cliente
            ADD CONSTRAINT chk_cliente_documento_formato
            CHECK (numero_documento IS NULL
                OR (tipo_documento = 'cedula'    AND numero_documento ~ '^[0-9]{10}$')
                OR (tipo_documento = 'ruc'       AND numero_documento ~ '^[0-9]{13}$')
                OR (tipo_documento = 'pasaporte' AND char_length(numero_documento) BETWEEN 5 AND 20));
    END IF;
END $$;

-- Unico, pero solo entre los que lo tienen: los 5.000 nulos conviven.
CREATE UNIQUE INDEX IF NOT EXISTS uq_cliente_documento
    ON cliente (numero_documento) WHERE numero_documento IS NOT NULL;

-- Se busca por documento en mostrador, asi que el prefijo tambien indexa.
CREATE INDEX IF NOT EXISTS idx_cliente_documento_busqueda
    ON cliente (numero_documento varchar_pattern_ops) WHERE numero_documento IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Privilegios. Regla 4 de PENDIENTE.md: una COLUMNA nueva nace SIN PERMISOS.
-- Se copia el reparto que ya tiene la tabla: leen los cuatro roles que leen
-- clientes; escriben el administrador y el Operador de Pedidos, que son los
-- que dan de alta.
-- ---------------------------------------------------------------------------
GRANT SELECT (tipo_documento, numero_documento) ON cliente TO rol_administrador;
GRANT SELECT (tipo_documento, numero_documento) ON cliente TO rol_supervisor;
GRANT SELECT (tipo_documento, numero_documento) ON cliente TO rol_operador_bodega;
GRANT SELECT (tipo_documento, numero_documento) ON cliente TO rol_operador_pedidos;

GRANT INSERT (tipo_documento, numero_documento) ON cliente TO rol_administrador;
GRANT INSERT (tipo_documento, numero_documento) ON cliente TO rol_operador_pedidos;

GRANT UPDATE (tipo_documento, numero_documento) ON cliente TO rol_administrador;
GRANT UPDATE (tipo_documento, numero_documento) ON cliente TO rol_operador_pedidos;

-- Comprobacion dentro de la transaccion.
DO $$
DECLARE faltan TEXT;
BEGIN
    SELECT string_agg(r, ', ') INTO faltan
      FROM (VALUES ('rol_administrador'), ('rol_operador_pedidos')) AS v(r)
     WHERE NOT EXISTS (
        SELECT 1 FROM pg_class c
          JOIN pg_attribute at ON at.attrelid = c.oid AND at.attnum > 0
          CROSS JOIN LATERAL aclexplode(at.attacl) a
         WHERE c.relname = 'cliente' AND at.attname = 'numero_documento'
           AND a.grantee::regrole::text = v.r AND a.privilege_type = 'INSERT');

    IF faltan IS NOT NULL THEN
        RAISE EXCEPTION 'Sin INSERT sobre numero_documento: %. Dar de alta un '
                        'cliente fallaria con permiso denegado.', faltan;
    END IF;
END $$;

COMMIT;

-- Verificacion
--   SELECT count(*) FILTER (WHERE numero_documento IS NOT NULL) AS con_documento,
--          count(*) AS total
--     FROM cliente;
--   -- esperado justo despues: 0 de 5000. No se inventa ninguno.
