-- =============================================================================
-- Fase 65 — REVERSION
-- =============================================================================
-- Retira los dos privilegios que la F65 concedio.
--
-- OJO: revertir devuelve el defecto entero, y de la peor manera posible — con
-- un mensaje que miente. El Encargado de Compras volvera a no poder registrar
-- una recepcion y el de Produccion a no poder iniciar ni completar una orden, y
-- los dos leeran "Tu rol no tiene permisos sobre estos datos", que apunta a la
-- matriz de permisos cuando el problema esta en un GRANT. Se pierden dos de los
-- ocho pasos del flujo.
--
-- El primer REVOKE solo tiene sentido si antes se cambia el codigo para que las
-- inserciones en movimiento_inventario NO usen RETURNING —un INSERT nativo,
-- como hace `LogService.registrar`—.
--
-- El segundo no tiene arreglo por codigo: el snapshot de costo se escribe en
-- `iniciar()` a proposito (F29), asi que sin ese UPDATE la orden no arranca.
-- =============================================================================

BEGIN;

REVOKE SELECT ON movimiento_inventario FROM rol_encargado_compras;
REVOKE SELECT ON movimiento_inventario FROM rol_encargado_produccion;

REVOKE UPDATE (costo_unitario_snapshot) ON orden_produccion_consumo
    FROM rol_encargado_produccion;

COMMIT;

-- Verificacion
--   SELECT a.grantee::regrole::text, a.privilege_type
--     FROM pg_class c, aclexplode(c.relacl) a
--    WHERE c.relname = 'movimiento_inventario'
--      AND a.grantee::regrole::text IN ('rol_encargado_compras','rol_encargado_produccion');
--   -- esperado: solo INSERT para los dos.
