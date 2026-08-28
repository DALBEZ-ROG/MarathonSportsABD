-- =============================================================================
-- Fase 63 — REVERSION
-- =============================================================================
-- Le quita al Supervisor E-Commerce la lectura de materia prima.
--
-- OJO: revertir devuelve el problema. La pantalla de Reportes del supervisor
-- volvera a comerse un 403 en cada carga y su filtro de materias primas volvera
-- a salir vacio, con el informe de consumo pidiendose pero sin poder acotarse.
--
-- Y hay que revertir TAMBIEN el codigo: si `SecurityConfig` sigue siendo el de
-- la F63, seguira dejando pasar la peticion por URL y el 403 vendra entonces
-- del @PreAuthorize. El sintoma es el mismo; el motivo, otro.
--
-- Esto NO toca ningun GRANT de PostgreSQL, ni al aplicarse ni al revertirse:
-- `rol_supervisor` tiene SELECT sobre `materia_prima` desde la F34 y lo
-- conserva en ambos casos.
-- =============================================================================

BEGIN;

DELETE FROM rol_permiso rp
 USING rol r, permiso p
 WHERE rp.id_rol = r.id_rol
   AND rp.id_permiso = p.id_permiso
   AND r.nombre = 'Supervisor E-Commerce'
   AND p.modulo = 'materia_prima'
   AND p.accion = 'ver';

COMMIT;

-- Verificacion
--   SELECT count(*) FROM rol_permiso rp
--     JOIN rol r     ON r.id_rol = rp.id_rol
--     JOIN permiso p ON p.id_permiso = rp.id_permiso
--    WHERE r.nombre = 'Supervisor E-Commerce' AND p.modulo = 'materia_prima';
--   -- esperado: 0
