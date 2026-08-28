-- =============================================================================
-- Fase 49 — REVERSION
-- =============================================================================
-- Retira los cuatro privilegios de columna que concedio fase49.
--
-- ATENCION: revertir esto vuelve a dejar al Operador de Pedidos sin poder crear
-- pedidos y al Encargado de Produccion sin poder crear ordenes de produccion.
-- No es una reversion "inocua": deshace la correccion de D-39.
--
-- Las anotaciones @DynamicInsert del codigo Java hay que retirarlas aparte si se
-- quiere volver del todo al estado anterior — aunque conviene NO retirarlas:
-- sin ellas el problema reaparece tambien en orden_compra, que este script no
-- toca porque se resolvio entera por el lado de Java.
-- =============================================================================

BEGIN;

REVOKE INSERT (picking_completado) ON detalle_pedido FROM rol_operador_pedidos;
REVOKE INSERT (cantidad_recogida)  ON detalle_pedido FROM rol_operador_pedidos;

REVOKE INSERT (costo_mano_obra) ON orden_produccion FROM rol_encargado_produccion;
REVOKE INSERT (costo_indirecto) ON orden_produccion FROM rol_encargado_produccion;

COMMIT;

-- Verificacion
--   SELECT has_column_privilege('usr_pedidos_marathon',
--          'detalle_pedido', 'cantidad_recogida', 'INSERT');   -- esperado: f
