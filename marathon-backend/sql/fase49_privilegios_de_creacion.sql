-- =============================================================================
-- Fase 49 (D-39) — Tres roles no podian crear el documento central de su trabajo
-- =============================================================================
-- POR QUE
-- Desde la F37 cada rol se conecta a PostgreSQL con su propio usuario y queda
-- sujeto a sus GRANT. Y desde la F34 esos GRANT son COLUMNA POR COLUMNA.
--
-- Hibernate, por omision, escribe un INSERT ESTATICO: nombra TODAS las columnas
-- mapeadas de la entidad, tengan valor o no. En un documento que atraviesa
-- varias etapas, eso significa nombrar columnas que solo se rellenan MAS TARDE
-- —y que el rol que ARRANCA el flujo, correctamente, no puede escribir—.
-- PostgreSQL no rechaza esas columnas: rechaza el INSERT entero.
--
-- Resultado, comprobado contra la aplicacion en marcha el 2026-08-27:
--
--   Operador de Pedidos     -> POST /api/pedidos             403
--   Encargado de Compras    -> POST /api/ordenes-compra      403
--   Encargado de Produccion -> POST /api/ordenes-produccion  403
--
-- Cada uno de los tres es EL documento central del trabajo de ese rol. La
-- aplicacion devolvia 403 y en el registro quedaba "permiso denegado a la tabla
-- pedido / orden_compra / orden_produccion".
--
-- POR QUE NO SE HABIA VISTO
-- scripts/fase37_pruebas_endpoints.ps1 dio 66 de 66, pero todas sus pruebas son
-- GET: comprueban quien PUEDE VER cada pantalla, no quien puede escribir en
-- ella. El unico camino de escritura que se probaba de verdad era el del
-- Administrador, que se conecta con el pool por defecto y tiene INSERT sobre la
-- tabla entera. Con el administrador todo funcionaba; con los otros tres roles,
-- nada.
--
-- LA CORRECCION, EN DOS MITADES
--
--   1. @DynamicInsert en Pedido, DetallePedido, OrdenCompra y OrdenProduccion
--      (codigo Java). Hibernate pasa a nombrar solo las columnas CON VALOR, asi
--      que las que se rellenan mas tarde dejan de aparecer en el INSERT. Esto
--      resuelve las columnas que estan a NULL al crear, que son la mayoria:
--        pedido            -> numero_hu, transportista, region_destino, fecha_empaque
--        detalle_pedido    -> id_bodega_picking
--        orden_compra      -> id_usuario_aprobador, fecha_aprobacion
--        orden_produccion  -> id_usuario_completa, fecha_inicio, fecha_fin,
--                             cantidad_producida
--      Ninguna de ellas necesita GRANT: simplemente dejan de escribirse.
--
--   2. Este script, para las cuatro que SI llevan valor al crear porque la
--      entidad les da un valor por defecto en Java. @DynamicInsert no las puede
--      omitir —tienen valor—, asi que hace falta el privilegio.
--
-- POR QUE ESTOS CUATRO GRANT NO ABREN NADA
-- En los cuatro casos el valor que se inserta es EXACTAMENTE el DEFAULT que ya
-- tiene la columna en la base:
--
--   detalle_pedido.picking_completado   DEFAULT false  <- se inserta false
--   detalle_pedido.cantidad_recogida    DEFAULT 0      <- se inserta 0
--   orden_produccion.costo_mano_obra    DEFAULT 0.00   <- se inserta 0
--   orden_produccion.costo_indirecto    DEFAULT 0.00   <- se inserta 0
--
-- Se concede INSERT y NO UPDATE, y esa distincion es la que conserva la
-- separacion de funciones: el Operador de Pedidos puede crear una linea con el
-- picking sin empezar, pero **no puede marcarla como recogida** — eso sigue
-- siendo exclusivo del Operador de Bodega, que es quien tiene el UPDATE.
--
-- REVERSION: fase49_privilegios_de_creacion_rollback.sql
-- =============================================================================

BEGIN;

-- --- El Operador de Pedidos crea la linea con el picking sin empezar ---------
GRANT INSERT (picking_completado) ON detalle_pedido TO rol_operador_pedidos;
GRANT INSERT (cantidad_recogida)  ON detalle_pedido TO rol_operador_pedidos;

-- --- El Encargado de Produccion crea la orden con los costes a cero ----------
-- Los costes reales los pone el cierre de la orden, y para eso ya tiene UPDATE.
GRANT INSERT (costo_mano_obra) ON orden_produccion TO rol_encargado_produccion;
GRANT INSERT (costo_indirecto) ON orden_produccion TO rol_encargado_produccion;

COMMIT;

-- Verificacion — los tres roles deben poder crear su documento.
-- Con la aplicacion en marcha:
--   POST /api/pedidos            como pedidos@marathon.com     -> 201
--   POST /api/ordenes-compra     como compras@marathon.com     -> 201
--   POST /api/ordenes-produccion como produccion@marathon.com  -> 201
--
-- Y la separacion de funciones debe seguir en pie: el Operador de Pedidos NO
-- tiene UPDATE sobre picking_completado, asi que no puede marcar una linea como
-- recogida.
--   SELECT has_column_privilege('usr_pedidos_marathon',
--          'detalle_pedido', 'picking_completado', 'UPDATE');   -- esperado: f
--   SELECT has_column_privilege('usr_pedidos_marathon',
--          'detalle_pedido', 'picking_completado', 'INSERT');   -- esperado: t
