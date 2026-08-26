-- =============================================================================
-- Fase 45 (lote L4) — El picking registra de que bodega se recogio
-- =============================================================================
-- Cierra la parte 1 del defecto D-01 y el defecto D-14.
--
-- POR QUE
-- El picking solo guardaba cuanto se habia recogido, nunca de donde. Sin ese
-- dato, EmpaqueService no podia hacer otra cosa que adivinar la bodega: hasta
-- la L1 la elegia con findFirst() sobre una lista sin ordenar, y desde la L1
-- reparte en orden estable. Ninguna de las dos es "la bodega correcta"; para
-- eso hace falta esta columna.
--
-- REVERSION: fase45_picking_bodega_rollback.sql
-- =============================================================================

BEGIN;

ALTER TABLE detalle_pedido
    ADD COLUMN id_bodega_picking INTEGER;

ALTER TABLE detalle_pedido
    ADD CONSTRAINT fk_detalle_bodega_picking
    FOREIGN KEY (id_bodega_picking) REFERENCES bodega(id_bodega)
    ON UPDATE CASCADE ON DELETE RESTRICT;

COMMENT ON COLUMN detalle_pedido.id_bodega_picking IS
    'Bodega de la que se recogio fisicamente la linea. La rellena PickingService '
    'al marcar la linea como recogida, y EmpaqueService descuenta de ella. '
    'NULL en las lineas anteriores a la fase 45: para esas, el despacho vuelve al '
    'reparto por orden estable de bodega que introdujo la L1.';

-- Sin CHECK que obligue a informarla cuando picking_completado = true.
--
-- No es un olvido: hay 229.999 pedidos con lineas ya marcadas como recogidas y
-- sin bodega, asi que un CHECK normal fallaria al aplicarse y este script no
-- pasaria de aqui. La regla la aplica PickingService sobre las lineas nuevas.
--
-- Si algun dia se quiere en el motor, el camino es ADD CONSTRAINT ... NOT VALID
-- y despues una campana de datos sobre el historico, que es un trabajo aparte.

-- Solo indexa las filas que tienen bodega: hoy son cero, y el indice parcial
-- ocupa lo que ocupen las que se vayan creando, no las 500.000 lineas.
CREATE INDEX idx_detalle_pedido_bodega_picking
    ON detalle_pedido (id_bodega_picking)
    WHERE id_bodega_picking IS NOT NULL;

-- -----------------------------------------------------------------------------
-- PRIVILEGIOS DE LA COLUMNA NUEVA  (imprescindible; ver nota)
-- -----------------------------------------------------------------------------
-- La fase 34 no concede privilegios sobre la TABLA sino COLUMNA POR COLUMNA, asi
-- que una columna nueva nace sin ningun permiso y solo el propietario puede
-- tocarla. Sin este bloque, el picking falla con
--     ERROR: permiso denegado a la tabla detalle_pedido
-- en cuanto un operador de bodega intenta guardar la linea. Lo detecto la prueba
-- PickingBodegaTest antes de que llegara a ninguna parte.
--
-- Toda futura columna de este proyecto necesita su GRANT explicito.

-- Lectura: los mismos cinco roles que ya leen el resto de detalle_pedido.
GRANT SELECT (id_bodega_picking) ON detalle_pedido TO rol_administrador;
GRANT SELECT (id_bodega_picking) ON detalle_pedido TO rol_supervisor;
GRANT SELECT (id_bodega_picking) ON detalle_pedido TO rol_operador_bodega;
GRANT SELECT (id_bodega_picking) ON detalle_pedido TO rol_operador_pedidos;
GRANT SELECT (id_bodega_picking) ON detalle_pedido TO rol_encargado_compras;

-- Escritura: solo quien hace picking. Es una columna del mismo grupo que
-- cantidad_recogida y picking_completado, y hereda su mismo reparto.
-- rol_operador_pedidos queda fuera a proposito: no recoge mercancia.
GRANT UPDATE (id_bodega_picking) ON detalle_pedido TO rol_administrador;
GRANT UPDATE (id_bodega_picking) ON detalle_pedido TO rol_operador_bodega;

COMMIT;

-- Verificacion
--   SELECT column_name, data_type, is_nullable
--     FROM information_schema.columns
--    WHERE table_name = 'detalle_pedido' AND column_name = 'id_bodega_picking';
--   -- esperado: id_bodega_picking | integer | YES
