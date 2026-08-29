-- =============================================================================
-- Fase 77 — De donde sale la region, y quien es el transportista
-- =============================================================================
-- LAS TRES PREGUNTAS DEL DUEÑO, Y LO QUE SE ENCONTRO AL MIRARLAS
--
-- 1. «¿que es numero HU?»
--    Handling Unit: la etiqueta del BULTO fisico que sale del almacen. No es el
--    pedido —un pedido podria ir en varios bultos—, es la caja. Ningun sitio lo
--    decia. Eso no se arregla aqui, se arregla en la pantalla, pero conviene
--    dejarlo escrito.
--
-- 2. «¿que los transportistas se pueda escribir en el filtro y seleccionar, o
--    no hay transportista en la bd?»
--    NO LO HABIA. `pedido.transportista` es un VARCHAR(100) libre, escrito a
--    mano en cada empaque. En 19.000 pedidos habia exactamente UN valor,
--    'Servientrega', de una prueba. Texto libre significa que «Servientrega»,
--    «servientrega» y «Servi entrega» son tres transportistas distintos para
--    cualquier consulta, y que no se puede listar «cuanto mandamos por cada
--    uno» sin adivinar. Se crea el catalogo.
--
-- 3. «¿region de destino que es? ¿no seria la ciudad del cliente que lo pidio?»
--    Tiene razon, y era el fallo de modelado mas gordo de los tres. La region
--    se TECLEABA en cada empaque, cuando ya se sabe: el pedido tiene cliente, el
--    cliente tiene ciudad, y la ciudad esta en una region. Se estaba pidiendo a
--    mano un dato deducible, que es la forma segu
--    ra de que acabe mal escrito.
--
--    La region NO es del pedido: es de la CIUDAD. Por eso la columna va en
--    `ciudad`, se rellena una vez, y el empaque la propone en lugar de
--    preguntarla. Se deja editable porque un bulto puede mandarse a otro sitio
--    —una oficina, un familiar—, pero el caso normal deja de teclearse.
--
-- SOBRE CLASIFICAR LAS 88 CIUDADES
-- No es inventar un dato: la region natural de una ciudad ecuatoriana es un
-- hecho geografico. El criterio es la PROVINCIA a la que pertenece el canton,
-- para que la zona de reparto sea coherente con la division administrativa.
-- Por eso Puerto Quito y La Concordia, que estan en tierras bajas, quedan en la
-- region de su provincia (Pichincha -> Sierra; Sto. Domingo -> Costa).
-- Insular no aparece: no hay ninguna ciudad de Galapagos en el catalogo.
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. La region vive en la ciudad
-- ---------------------------------------------------------------------------
ALTER TABLE ciudad
    ADD COLUMN IF NOT EXISTS region VARCHAR(20) NULL;

COMMENT ON COLUMN ciudad.region IS
    'Region natural del Ecuador a la que pertenece la ciudad (F77): Costa, '
    'Sierra, Oriente o Insular. El criterio es la provincia del canton, para '
    'que la zona de reparto cuadre con la division administrativa. De aqui sale '
    'la region de destino del empaque, que antes se tecleaba a mano.';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_ciudad_region') THEN
        ALTER TABLE ciudad
            ADD CONSTRAINT chk_ciudad_region
            CHECK (region IS NULL OR region IN ('Costa', 'Sierra', 'Oriente', 'Insular'));
    END IF;
END $$;

-- Sierra: Pichincha, Azuay, Tungurahua, Chimborazo, Imbabura, Loja, Cañar,
-- Cotopaxi, Bolivar y Carchi.
UPDATE ciudad SET region = 'Sierra' WHERE nombre IN (
    'Amaguaña', 'Ambato', 'Atuntaqui', 'Baños De Agua Santa', 'Biblian',
    'Calderon', 'Cañar', 'Cayambe', 'Chimbo', 'Conocoto', 'Cuenca', 'Cumbaya',
    'Giron', 'Guaranda', 'Ibarra', 'Latacunga', 'Loja', 'Machachi', 'Otavalo',
    'Pomasqui', 'Puembo', 'Puerto Quito', 'Quito', 'Riobamba',
    'San Miguel De Bolivar', 'San Rafael', 'Tababela', 'Tabacundo', 'Tulcan',
    'Tumbaco');

-- Costa: Guayas, Manabi, Los Rios, El Oro, Esmeraldas, Santa Elena y Santo
-- Domingo de los Tsachilas.
UPDATE ciudad SET region = 'Costa' WHERE nombre IN (
    '24 De Mayo', 'Arenillas', 'Atacames', 'Baba', 'Babahoyo',
    'Bahia De Caraquez', 'Bucay', 'Calceta', 'Chone', 'Daule', 'Duran',
    'El Carmen', 'El Empalme', 'El Triunfo', 'Esmeraldas', 'Flavio Alfaro',
    'Guayaquil', 'Huaquillas', 'Isidro Ayora', 'Jama', 'Jipijapa', 'Junin',
    'La Concordia', 'La Libertad', 'Machala', 'Manta', 'Milagro', 'Naranjal',
    'Naranjito', 'Nobol', 'Pedernales', 'Pedro Carbo', 'Pichincha Manabi',
    'Piñas', 'Playas', 'Portoviejo', 'Quevedo', 'Rocafuerte', 'Salinas',
    'Samborondon', 'Santa Rosa', 'Santo Domingo', 'Tosagua', 'Valdivia',
    'Ventanas', 'Vinces', 'Zaruma');

-- Oriente: Sucumbios, Orellana, Napo, Pastaza, Morona Santiago y Zamora
-- Chinchipe.
UPDATE ciudad SET region = 'Oriente' WHERE nombre IN (
    'El Coca', 'Francisco De Orellana', 'Gualaquiza', 'La Joya De Los Sachas',
    'Lago Agrio', 'Loreto', 'Macas', 'Puyo', 'Shushufindi', 'Tena', 'Zamora');

-- El indice sirve al listado de despachos, que filtra por region.
CREATE INDEX IF NOT EXISTS idx_ciudad_region ON ciudad (region) WHERE region IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 2. El catalogo de transportistas
-- ---------------------------------------------------------------------------
-- Es un catalogo de consulta, como ciudad o unidad de medida: la aplicacion lo
-- LEE para ofrecerlo en el empaque. No se le dan INSERT/UPDATE a nadie a
-- proposito — dar de alta un transportista es una decision de negocio, no una
-- casilla de la pantalla de almacen. Cuando haga falta administrarlo desde la
-- interfaz sera otra fase, con su permiso y sus privilegios.
CREATE TABLE IF NOT EXISTS transportista (
    id_transportista SERIAL PRIMARY KEY,
    nombre           VARCHAR(100) NOT NULL,
    cobertura        VARCHAR(100) NULL,
    estado           VARCHAR(20)  NOT NULL DEFAULT 'activo',
    CONSTRAINT uq_transportista_nombre UNIQUE (nombre),
    CONSTRAINT chk_transportista_estado CHECK (estado IN ('activo', 'inactivo'))
);

COMMENT ON TABLE transportista IS
    'Catalogo de transportistas (F77). Antes el transportista era texto libre '
    'en pedido.transportista: 19.000 pedidos y un unico valor escrito a mano, '
    'de una prueba. Con texto libre, "Servientrega" y "servientrega" son dos '
    'transportistas para cualquier consulta.';

COMMENT ON COLUMN transportista.cobertura IS
    'Donde llega, en una linea. Es lo que se enseña al lado del nombre para '
    'elegir sin tener que saberselo.';

-- Semilla del proyecto: empresas de mensajeria que operan en Ecuador. Es dato
-- de demostracion, como el resto de la semilla; no hay contratos detras.
INSERT INTO transportista (nombre, cobertura, estado) VALUES
    ('Servientrega',        'Nacional, incluye Oriente',        'activo'),
    ('Laar Courier',        'Nacional',                          'activo'),
    ('Urbano Express',      'Nacional, fuerte en Costa y Sierra','activo'),
    ('Tramaco Express',     'Nacional',                          'activo'),
    ('Correos del Ecuador', 'Nacional, incluye Galapagos',       'activo'),
    ('Speed Express',       'Costa y Sierra',                    'activo'),
    ('Entrega propia',      'Quito y Guayaquil, flota propia',   'activo')
ON CONFLICT (nombre) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. Privilegios. Regla 4 de PENDIENTE.md: lo nuevo nace SIN NADA, y una
--    COLUMNA nueva tampoco hereda los permisos de su tabla.
-- ---------------------------------------------------------------------------
GRANT SELECT (region) ON ciudad TO rol_administrador;
GRANT SELECT (region) ON ciudad TO rol_supervisor;
GRANT SELECT (region) ON ciudad TO rol_operador_bodega;
GRANT SELECT (region) ON ciudad TO rol_operador_pedidos;
GRANT SELECT (region) ON ciudad TO rol_encargado_compras;
GRANT SELECT (region) ON ciudad TO rol_encargado_produccion;

-- Editar la region de una ciudad es mantenimiento del catalogo: solo el
-- administrador, que es quien ya podia editar ciudades.
GRANT INSERT (region), UPDATE (region) ON ciudad TO rol_administrador;

-- El catalogo lo lee quien empaca y quien mira despachos.
GRANT SELECT ON transportista TO rol_administrador;
GRANT SELECT ON transportista TO rol_supervisor;
GRANT SELECT ON transportista TO rol_operador_bodega;
GRANT SELECT ON transportista TO rol_operador_pedidos;

-- ---------------------------------------------------------------------------
-- 4. El permiso de la aplicacion (distinto del privilegio de la base)
-- ---------------------------------------------------------------------------
INSERT INTO permiso (modulo, accion, descripcion)
SELECT 'transportistas', 'ver', 'transportistas:ver'
 WHERE NOT EXISTS (SELECT 1 FROM permiso WHERE modulo = 'transportistas' AND accion = 'ver');

INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
  FROM rol r
  CROSS JOIN permiso p
 WHERE p.modulo = 'transportistas' AND p.accion = 'ver'
   AND r.nombre IN ('Administrador', 'Operador de Bodega', 'Supervisor E-Commerce')
   AND NOT EXISTS (SELECT 1 FROM rol_permiso rp
                    WHERE rp.id_rol = r.id_rol AND rp.id_permiso = p.id_permiso);

-- ---------------------------------------------------------------------------
-- 5. Comprobaciones, dentro de la transaccion
-- ---------------------------------------------------------------------------
DO $$
DECLARE sin_region INT; total INT; faltan TEXT;
BEGIN
    SELECT count(*) FILTER (WHERE region IS NULL), count(*) INTO sin_region, total FROM ciudad;
    IF sin_region > 0 THEN
        RAISE EXCEPTION 'Quedan % ciudades de % sin region. Cada una que falte '
                        'deja un empaque teniendo que teclearla a mano.', sin_region, total;
    END IF;

    SELECT string_agg(r, ', ') INTO faltan
      FROM (VALUES ('rol_operador_bodega'), ('rol_administrador')) AS v(r)
     WHERE NOT EXISTS (
        SELECT 1 FROM pg_class c CROSS JOIN LATERAL aclexplode(c.relacl) a
         WHERE c.relname = 'transportista'
           AND a.grantee::regrole::text = v.r AND a.privilege_type = 'SELECT');
    IF faltan IS NOT NULL THEN
        RAISE EXCEPTION 'Sin SELECT sobre transportista: %. El desplegable del '
                        'empaque saldria vacio.', faltan;
    END IF;
END $$;

COMMIT;

-- Verificacion
--   SELECT region, count(*) FROM ciudad GROUP BY region ORDER BY 2 DESC;
--   -- esperado: Costa 47, Sierra 30, Oriente 11, ninguna nula
--   SELECT nombre, cobertura FROM transportista WHERE estado = 'activo';
