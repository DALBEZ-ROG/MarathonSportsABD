-- =============================================================================
-- Fase 84 - ROLLBACK
-- =============================================================================
-- Deshace la normalizacion de la F84 y deja el esquema como estaba: con las
-- tres dependencias transitivas y la lista dentro de la columna.
--
-- LO QUE SE RECUPERA Y LO QUE NO
-- Los tres datos que se quitaron eran DEDUCIBLES, asi que se vuelven a
-- calcular de donde salian y no se pierde nada:
--   pedido.transportista   <- transportista.nombre
--   pedido.region_destino  <- ciudad.region del cliente
--   cuenta_por_pagar.id_proveedor <- orden_compra.id_proveedor
--
-- Lo que SI se pierde es la nota del transportista, si alguien la edito: la
-- columna `cobertura` se reconstruye con las frases originales de la F77, no
-- con lo que hubiera despues. Se avisa aqui porque es lo unico que no vuelve.
--
-- Ejecutar como superusuario, con la aplicacion parada.
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. cuenta_por_pagar.id_proveedor vuelve
-- ---------------------------------------------------------------------------
ALTER TABLE cuenta_por_pagar
    ADD COLUMN IF NOT EXISTS id_proveedor INTEGER NULL;

UPDATE cuenta_por_pagar cp
   SET id_proveedor = o.id_proveedor
  FROM factura_compra f
  JOIN orden_compra o ON o.id_orden_compra = f.id_orden_compra
 WHERE f.id_factura_compra = cp.id_factura_compra;

ALTER TABLE cuenta_por_pagar ALTER COLUMN id_proveedor SET NOT NULL;

ALTER TABLE cuenta_por_pagar
    ADD CONSTRAINT fk_cxp_proveedor FOREIGN KEY (id_proveedor)
        REFERENCES proveedor (id_proveedor)
        ON UPDATE CASCADE ON DELETE RESTRICT;

GRANT SELECT (id_proveedor) ON cuenta_por_pagar TO rol_administrador;
GRANT SELECT (id_proveedor) ON cuenta_por_pagar TO rol_supervisor;
GRANT SELECT (id_proveedor) ON cuenta_por_pagar TO rol_encargado_compras;
GRANT INSERT (id_proveedor), UPDATE (id_proveedor) ON cuenta_por_pagar TO rol_administrador;
GRANT INSERT (id_proveedor) ON cuenta_por_pagar TO rol_encargado_compras;

DROP INDEX IF EXISTS idx_factura_compra_orden;
DROP INDEX IF EXISTS idx_orden_compra_proveedor;

-- ---------------------------------------------------------------------------
-- 2. pedido.region_destino vuelve
-- ---------------------------------------------------------------------------
ALTER TABLE pedido
    ADD COLUMN IF NOT EXISTS region_destino VARCHAR(100) NULL;

-- Solo se rellena donde el empaque la habria puesto: en lo ya empacado.
UPDATE pedido p
   SET region_destino = ci.region
  FROM cliente c
  JOIN ciudad ci ON ci.id_ciudad = c.id_ciudad
 WHERE c.id_cliente = p.id_cliente
   AND p.fecha_empaque IS NOT NULL;

GRANT SELECT (region_destino) ON pedido TO rol_administrador;
GRANT SELECT (region_destino) ON pedido TO rol_supervisor;
GRANT SELECT (region_destino) ON pedido TO rol_operador_bodega;
GRANT SELECT (region_destino) ON pedido TO rol_operador_pedidos;
GRANT INSERT (region_destino), UPDATE (region_destino) ON pedido TO rol_administrador;
GRANT UPDATE (region_destino) ON pedido TO rol_operador_bodega;

-- ---------------------------------------------------------------------------
-- 3. pedido.transportista vuelve a ser texto
-- ---------------------------------------------------------------------------
ALTER TABLE pedido
    ADD COLUMN IF NOT EXISTS transportista VARCHAR(100) NULL;

UPDATE pedido p
   SET transportista = t.nombre
  FROM transportista t
 WHERE t.id_transportista = p.id_transportista;

GRANT SELECT (transportista) ON pedido TO rol_administrador;
GRANT SELECT (transportista) ON pedido TO rol_supervisor;
GRANT SELECT (transportista) ON pedido TO rol_operador_bodega;
GRANT SELECT (transportista) ON pedido TO rol_operador_pedidos;
GRANT INSERT (transportista), UPDATE (transportista) ON pedido TO rol_administrador;
GRANT UPDATE (transportista) ON pedido TO rol_operador_bodega;

DROP INDEX IF EXISTS idx_pedido_transportista;
ALTER TABLE pedido DROP CONSTRAINT IF EXISTS fk_pedido_transportista;
ALTER TABLE pedido DROP COLUMN IF EXISTS id_transportista;

-- ---------------------------------------------------------------------------
-- 4. La cobertura vuelve a ser una frase
-- ---------------------------------------------------------------------------
ALTER TABLE transportista
    ADD COLUMN IF NOT EXISTS cobertura VARCHAR(100) NULL;

UPDATE transportista SET cobertura = v.cobertura
  FROM (VALUES
        ('Servientrega',        'Nacional, incluye Oriente'),
        ('Laar Courier',        'Nacional'),
        ('Urbano Express',      'Nacional, fuerte en Costa y Sierra'),
        ('Tramaco Express',     'Nacional'),
        ('Correos del Ecuador', 'Nacional, incluye Galapagos'),
        ('Speed Express',       'Costa y Sierra'),
        ('Entrega propia',      'Quito y Guayaquil, flota propia')
       ) AS v(nombre, cobertura)
 WHERE v.nombre = transportista.nombre;

GRANT SELECT (cobertura) ON transportista TO rol_administrador;
GRANT SELECT (cobertura) ON transportista TO rol_supervisor;
GRANT SELECT (cobertura) ON transportista TO rol_operador_bodega;
GRANT SELECT (cobertura) ON transportista TO rol_operador_pedidos;

DROP TABLE IF EXISTS transportista_cobertura;
ALTER TABLE transportista DROP COLUMN IF EXISTS nota;

COMMIT;
