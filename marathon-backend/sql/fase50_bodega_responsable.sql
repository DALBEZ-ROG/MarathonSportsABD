-- =============================================================================
-- Fase 50 (D-40) — La bodega guarda de verdad su responsable
-- =============================================================================
-- POR QUE
-- La pantalla de Bodegas tiene un campo «Responsable», una columna
-- «RESPONSABLE» en la tabla, y al guardar dice «Bodega guardada correctamente».
-- El valor NO se guardaba en ninguna parte:
--
--   marathon-frontend/.../bodegas.component.ts  -> lo pide y lo envia
--   dto/bodega/BodegaRequestDTO                 -> lo acepta
--   service/BodegaService                       -> NO LO MIRA
--   model/Bodega                                -> no tiene el campo
--   tabla bodega                                -> no tenia la columna
--   dto/bodega/BodegaResponseDTO                -> tiene el campo, siempre null
--
-- Resultado: se escribe el nombre, sale el aviso verde, y la columna sigue
-- vacia. Es la misma familia que D-34 (el precio del pedido, que se aceptaba y
-- se ignoraba) y que D-13 (una pantalla que aparentaba controlar accesos sin
-- controlarlos): el sistema dice que hizo algo que no hizo.
--
-- POR QUE SE ANADE LA COLUMNA Y NO SE QUITA EL CAMPO
-- Las dos salidas cerraban el defecto, pero no valen lo mismo. La pantalla lleva
-- el campo, la cabecera de la tabla y el sitio donde pintarlo: todo el trabajo
-- de interfaz esta hecho y el dato es util —saber quien responde de una bodega
-- es una pregunta razonable de almacen—. Quitarlo seria tirar eso para que el
-- sistema deje de mentir; anadir la columna hace que la promesa sea cierta, y es
-- una linea de esquema.
--
-- PRIVILEGIOS: no hacen falta.
-- Es la excepcion a la regla del §2.4 de PENDIENTE.md. La fase 34 concede
-- privilegios columna por columna sobre las tablas del circuito operativo, pero
-- sobre 'bodega' los concedio A NIVEL DE TABLA (SELECT para los seis roles;
-- INSERT/UPDATE/DELETE solo para rol_administrador). Un GRANT de tabla cubre
-- las columnas futuras, asi que la columna nueva nace con los privilegios
-- correctos. Se comprueba igualmente al final, porque dar esto por supuesto es
-- justo lo que ha fallado dos veces en este proyecto.
--
-- REVERSION: fase50_bodega_responsable_rollback.sql
-- =============================================================================

BEGIN;

ALTER TABLE bodega ADD COLUMN responsable VARCHAR(120);

COMMENT ON COLUMN bodega.responsable IS
    'Persona que responde de la bodega (F50, D-40). Texto libre a proposito: no '
    'es una FK a usuario porque el responsable de un almacen no tiene por que '
    'tener cuenta en el sistema, y atarlo a usuario impediria registrar al '
    'encargado real mientras no se le cree una.';

-- Sin NOT NULL y sin valor por defecto: hay 20 bodegas cargadas y no se sabe
-- quien responde de ninguna. Un NOT NULL obligaria a inventarse 20 nombres, y
-- rellenarlas con '' o 'Sin asignar' seria decir que hay un dato donde no lo
-- hay. NULL significa "no se ha informado", que es la verdad.

COMMIT;

-- Verificacion
--   SELECT column_name, data_type, is_nullable
--     FROM information_schema.columns
--    WHERE table_name = 'bodega' AND column_name = 'responsable';
--   -- esperado: responsable | character varying | YES
--
--   -- Los privilegios heredados del GRANT de tabla:
--   SELECT has_column_privilege('usr_admin_marathon','bodega','responsable','UPDATE');  -- t
--   SELECT has_column_privilege('usr_bodega_marathon','bodega','responsable','SELECT'); -- t
--   SELECT has_column_privilege('usr_bodega_marathon','bodega','responsable','UPDATE'); -- f
