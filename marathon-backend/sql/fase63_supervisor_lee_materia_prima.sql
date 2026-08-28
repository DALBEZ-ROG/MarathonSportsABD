-- =============================================================================
-- Fase 63 — El Supervisor E-Commerce puede leer materia prima
-- =============================================================================
-- POR QUE
-- Recorriendo el flujo con CADA rol —no solo con el administrador— la pantalla
-- de Reportes del Supervisor E-Commerce se comia un 403 en cada carga:
--
--     GET /api/materia-prima?page=0&size=1000&estado=activo  ->  403
--
-- La consecuencia era visible, aunque callada: el supervisor PUEDE ejecutar el
-- informe de consumo de materia prima, pero su filtro de materias primas salia
-- VACIO. Podia pedir el informe y no podia acotarlo.
--
-- LO IMPORTANTE: ESTO NO ES UN PERMISO NUEVO
-- `rol_supervisor` YA tenia SELECT sobre `materia_prima` en la base desde la
-- F34. Es decir, las dos capas se contradecian:
--
--     base de datos      -> el supervisor puede leer materia_prima
--     SecurityConfig     -> no
--     matriz de la F48   -> no
--
-- Y ganaba la de aplicacion. Se alinea la aplicacion con la base, que es la
-- capa que este proyecto trata como la mas deliberada, y no al reves: no se
-- toca ningun GRANT.
--
-- LO QUE NO CAMBIA
-- Solo LECTURA. Crear, editar, borrar y registrar movimientos de materia prima
-- siguen siendo exclusivos de Encargado de Produccion y Administrador. El
-- supervisor mira; no toca.
-- =============================================================================

BEGIN;

INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
  FROM rol r, permiso p
 WHERE r.nombre = 'Supervisor E-Commerce'
   AND p.modulo = 'materia_prima'
   AND p.accion = 'ver'
   AND NOT EXISTS (SELECT 1 FROM rol_permiso rp
                    WHERE rp.id_rol = r.id_rol AND rp.id_permiso = p.id_permiso);

-- Comprobacion dentro de la transaccion: o queda, o no se aplica nada.
DO $$
DECLARE tiene INT;
BEGIN
    SELECT count(*) INTO tiene
      FROM rol_permiso rp
      JOIN rol r     ON r.id_rol = rp.id_rol
      JOIN permiso p ON p.id_permiso = rp.id_permiso
     WHERE r.nombre = 'Supervisor E-Commerce'
       AND p.modulo = 'materia_prima' AND p.accion = 'ver';

    IF tiene <> 1 THEN
        RAISE EXCEPTION 'El Supervisor E-Commerce deberia tener materia_prima:ver '
                        'exactamente una vez, y tiene %.', tiene;
    END IF;

    -- Y que no se haya colado nada de escritura por el camino.
    SELECT count(*) INTO tiene
      FROM rol_permiso rp
      JOIN rol r     ON r.id_rol = rp.id_rol
      JOIN permiso p ON p.id_permiso = rp.id_permiso
     WHERE r.nombre = 'Supervisor E-Commerce'
       AND p.modulo = 'materia_prima' AND p.accion <> 'ver';

    IF tiene > 0 THEN
        RAISE EXCEPTION 'El Supervisor E-Commerce ha acabado con % permisos de '
                        'ESCRITURA sobre materia prima. Solo debe leer.', tiene;
    END IF;
END $$;

COMMIT;

-- Verificacion
--   SELECT p.modulo||':'||p.accion
--     FROM rol_permiso rp
--     JOIN rol r     ON r.id_rol = rp.id_rol
--     JOIN permiso p ON p.id_permiso = rp.id_permiso
--    WHERE r.nombre = 'Supervisor E-Commerce' AND p.modulo = 'materia_prima';
--   -- esperado: una sola fila, materia_prima:ver
