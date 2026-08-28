-- =============================================================================
-- Fase 69 — La reposición del proveedor deja de ser una promesa verbal
-- =============================================================================
-- LO QUE FALTABA, dicho por el dueño del proyecto el 2026-08-28:
--
--   «le puse que el proveedor manda otra igual, pero dice que eso después se
--    recibe como otra compra más, ¿y yo no tendría que pagar eso, no? ¿y cómo sé
--    que me va a llegar?»
--
-- Tenia razon en las dos cosas, y eran dos agujeros distintos:
--
--   1. NO SE PAGA. La reposicion es el proveedor cumpliendo una reclamacion: ya
--      se pago cuando se compro la mercancia defectuosa. Facturarla otra vez es
--      pagar dos veces lo mismo.
--   2. NO HABIA RASTRO. La devolucion quedaba 'resuelta' y ahi moria. Nada decia
--      que habia mercancia en camino, ni cuanta, ni de que devolucion venia.
--
-- SU PROPUESTA Y POR QUE SE CAMBIA UNA COSA
-- Propuso crear una orden de compra con la prenda "gratis", a precio cero. La
-- idea de fondo es la correcta —reutilizar la orden de compra, que ya tiene todo
-- el circuito de recepcion montado— pero el precio cero NO se puede:
--
--   a) `chk_oc_detalle_precio` exige precio_unitario > 0, y PENDIENTE.md §5
--      prohibe tocar los CHECK: las pruebas de L1, L5, L6 y L13 dependen de que
--      se comporten igual.
--   b) La recepcion recalcula el COSTO PROMEDIO PONDERADO (F29). Entrar
--      mercancia a cero arrastraria ese promedio hacia abajo y falsearia el
--      costo real de lo que hay en bodega, que es una mentira contable peor que
--      el problema que se queria resolver.
--
-- LA SOLUCION: precio real, y la orden marcada como NO FACTURABLE.
-- La linea lleva el precio de compra que corresponde, asi que el costo promedio
-- sigue siendo verdad. Lo que impide pagarla no es un cero, es la marca
-- `es_reposicion`: FacturaCompraService se niega a facturar una orden marcada.
--
-- Y el aviso de que algo esta por llegar sale solo: la orden nace 'aprobada' y
-- aparece en el indicador «Aprobadas sin recibir» del tablero, que es
-- exactamente lo que el dueño preguntaba. Los indicadores de compras cuentan
-- ordenes, no suman importes, asi que esto no infla ninguna cifra de dinero.
--
-- POR QUE NACE 'aprobada' Y NO 'borrador'
-- Aprobar una orden es autorizar un GASTO. Aqui no se gasta nada: el proveedor
-- ya se comprometio al aceptar la reclamacion. Hacerla pasar por aprobacion
-- seria pedirle a un administrador que autorice un desembolso que no existe.
-- Queda en la bitacora quien la genero y de que devolucion salio.
-- =============================================================================

BEGIN;

ALTER TABLE orden_compra
    ADD COLUMN IF NOT EXISTS es_reposicion BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE orden_compra
    ADD COLUMN IF NOT EXISTS id_devolucion_prov INTEGER NULL;

COMMENT ON COLUMN orden_compra.es_reposicion IS
    'true = el proveedor repone mercancia defectuosa por una reclamacion (F69). '
    'NO se factura ni genera cuenta por pagar: ya se pago al comprar el original. '
    'La linea lleva precio real para no falsear el costo promedio.';

COMMENT ON COLUMN orden_compra.id_devolucion_prov IS
    'La devolucion a proveedor que origino esta reposicion. Solo se rellena '
    'cuando es_reposicion = true.';

-- La traza hacia la devolucion. ON DELETE SET NULL y no CASCADE: si algun dia se
-- borrara la devolucion, la mercancia recibida NO deja de haber entrado.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_oc_devolucion_prov') THEN
        ALTER TABLE orden_compra
            ADD CONSTRAINT fk_oc_devolucion_prov
            FOREIGN KEY (id_devolucion_prov)
            REFERENCES devolucion_proveedor (id_devolucion_prov)
            ON DELETE SET NULL;
    END IF;
END $$;

-- Solo se rellena la referencia cuando de verdad es una reposicion.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_oc_reposicion_coherente') THEN
        ALTER TABLE orden_compra
            ADD CONSTRAINT chk_oc_reposicion_coherente
            CHECK (es_reposicion = true OR id_devolucion_prov IS NULL);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_oc_reposicion
    ON orden_compra (es_reposicion) WHERE es_reposicion = true;

-- ---------------------------------------------------------------------------
-- Privilegios. Regla 4 de PENDIENTE.md: una COLUMNA nueva nace SIN PERMISOS, y
-- la F34 los concede columna por columna. Sin esto, el INSERT de la orden
-- fallaria con "permiso denegado" para todo el que no sea el administrador.
-- ---------------------------------------------------------------------------
-- Lectura para los seis: la marca hay que poder verla en la pantalla.
GRANT SELECT (es_reposicion, id_devolucion_prov) ON orden_compra TO rol_administrador;
GRANT SELECT (es_reposicion, id_devolucion_prov) ON orden_compra TO rol_supervisor;
GRANT SELECT (es_reposicion, id_devolucion_prov) ON orden_compra TO rol_operador_bodega;
GRANT SELECT (es_reposicion, id_devolucion_prov) ON orden_compra TO rol_operador_pedidos;
GRANT SELECT (es_reposicion, id_devolucion_prov) ON orden_compra TO rol_encargado_compras;
GRANT SELECT (es_reposicion, id_devolucion_prov) ON orden_compra TO rol_encargado_produccion;

-- Escritura solo para quien resuelve una devolucion: Compras y el administrador.
GRANT INSERT (es_reposicion, id_devolucion_prov) ON orden_compra TO rol_administrador;
GRANT INSERT (es_reposicion, id_devolucion_prov) ON orden_compra TO rol_encargado_compras;

-- Nadie puede convertir una orden normal en reposicion despues de creada: la
-- marca se pone al nacer o no se pone. Por eso NO se concede UPDATE.

COMMIT;

-- Verificacion
--   SELECT column_name, data_type, column_default
--     FROM information_schema.columns
--    WHERE table_name = 'orden_compra' AND column_name LIKE '%reposicion%'
--       OR column_name = 'id_devolucion_prov';
--
--   SELECT at.attname, a.privilege_type
--     FROM pg_class c
--     JOIN pg_attribute at ON at.attrelid = c.oid AND at.attnum > 0
--     CROSS JOIN LATERAL aclexplode(at.attacl) a
--    WHERE c.relname = 'orden_compra'
--      AND at.attname IN ('es_reposicion','id_devolucion_prov')
--      AND a.grantee::regrole::text = 'rol_encargado_compras';
--   -- esperado: SELECT e INSERT en las dos columnas, y NUNCA UPDATE.
