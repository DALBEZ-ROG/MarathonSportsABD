-- =============================================================================
-- Fase 65 — Compras y Produccion pueden terminar su parte del flujo
-- =============================================================================
-- EL SINTOMA
-- El Encargado de Compras aprobaba la orden, entraba a registrar la recepcion,
-- y la pantalla contestaba:
--
--     "Tu rol no tiene permisos sobre estos datos"    (HTTP 403)
--
-- El mensaje enganaba: apunta a la matriz de permisos, y la matriz estaba bien
-- —'recepciones:registrar' lo tiene desde la F48—. Quien denegaba era
-- PostgreSQL, por dos GRANT que nunca se concedieron.
--
-- POR QUE NO SE HABIA VISTO
-- Es la misma trampa que escondio D-39 desde la F37, y vuelve a aparecer por el
-- mismo motivo: **el barrido de la F63 recorrio 128 pantallas, pero solo con
-- GET**. Cargar la pantalla de recepcion funciona; lo que falla es enviarla.
-- Salio en cuanto el dueno del proyecto uso el sistema de verdad.
--
-- =============================================================================
-- HUECO 1 — movimiento_inventario, sin SELECT (Compras y Produccion)
-- =============================================================================
--     ERROR: permiso denegado a la tabla movimiento_inventario
--
-- Los dos roles tenian INSERT pero NO SELECT. La clave primaria es IDENTITY,
-- asi que Hibernate escribe
--
--     INSERT INTO movimiento_inventario (...) VALUES (...) RETURNING id_movimiento
--
-- y **RETURNING exige SELECT sobre la tabla**. Con INSERT a secas, PostgreSQL
-- rechaza la sentencia entera. Es exactamente la trampa ya explicada en
-- `LogService.registrar`, donde se resolvio al reves —con un INSERT nativo sin
-- RETURNING— porque alli se queria que dos roles pudieran ESCRIBIR en la
-- bitacora sin poder LEERLA.
--
-- Aqui la respuesta correcta es la contraria: conceder SELECT. Quien recibe
-- mercancia o fabrica necesita ver los movimientos que el mismo genera, y los
-- dos roles ya leen `inventario` desde la F34.
--
-- NO ES SOLO COMPRAS. `OrdenProduccionService.completar()` mete el producto
-- terminado en inventario y escribe su movimiento igual, asi que Produccion
-- habria chocado con el mismo 403. No se habia visto porque nadie habia
-- llegado a completar una orden siendo Produccion.
--
-- =============================================================================
-- HUECO 2 — orden_produccion_consumo.costo_unitario_snapshot, sin UPDATE
-- =============================================================================
--     ERROR: permiso denegado a la tabla orden_produccion_consumo
--     [update orden_produccion_consumo set costo_unitario_snapshot=? ...]
--
-- Produccion tenia INSERT sobre esa columna y UPDATE sobre `cantidad_real`,
-- pero no UPDATE sobre el snapshot. Y ese UPDATE es DELIBERADO, no un descuido
-- del codigo: la F29 fotografia el costo promedio de la materia prima **al
-- iniciar** la orden, no al planificarla, porque lo que cuesta producir es lo
-- que valia el insumo cuando se consumio. La fila se crea en `crear()` y esa
-- columna se rellena en `iniciar()`.
--
-- Es el caso de manual de la regla 4 de PENDIENTE.md —privilegios columna por
-- columna, y una tabla que se llena POR ETAPAS necesita el UPDATE de las
-- columnas de cada etapa posterior—. La F34 concedio la etapa 1 y la 3
-- (cantidad_real, al completar) y se dejo la 2.
--
-- Se concede SOLO esa columna. Produccion sigue sin poder tocar
-- `cantidad_teorica` ni `id_materia_prima`: lo que se planifico no se reescribe.
-- =============================================================================

BEGIN;

-- Hueco 1
GRANT SELECT ON movimiento_inventario TO rol_encargado_compras;
GRANT SELECT ON movimiento_inventario TO rol_encargado_produccion;

-- Hueco 2
GRANT UPDATE (costo_unitario_snapshot) ON orden_produccion_consumo TO rol_encargado_produccion;

-- ---------------------------------------------------------------------------
-- Comprobaciones dentro de la transaccion.
-- ---------------------------------------------------------------------------
DO $$
DECLARE faltan TEXT;
BEGIN
    -- (a) Los tres roles que escriben movimientos pueden leerlos.
    SELECT string_agg(r, ', ') INTO faltan
      FROM (VALUES ('rol_encargado_compras'), ('rol_encargado_produccion'),
                   ('rol_operador_bodega')) AS v(r)
     WHERE NOT EXISTS (
        SELECT 1 FROM pg_class c, aclexplode(c.relacl) a
         WHERE c.relname = 'movimiento_inventario'
           AND a.grantee::regrole::text = v.r
           AND a.privilege_type = 'SELECT');

    IF faltan IS NOT NULL THEN
        RAISE EXCEPTION 'Sin SELECT sobre movimiento_inventario: %. El '
                        'INSERT ... RETURNING de Hibernate fallara.', faltan;
    END IF;

    -- (b) Nadie nuevo puede CORREGIR un movimiento: es un asiento.
    SELECT string_agg(a.grantee::regrole::text, ', ') INTO faltan
      FROM pg_class c, aclexplode(c.relacl) a
     WHERE c.relname = 'movimiento_inventario'
       AND a.privilege_type IN ('UPDATE', 'DELETE')
       AND a.grantee::regrole::text LIKE 'rol_%'
       AND a.grantee::regrole::text <> 'rol_administrador';

    IF faltan IS NOT NULL THEN
        RAISE EXCEPTION 'Estos roles han ganado UPDATE o DELETE sobre '
                        'movimiento_inventario y no debian: %', faltan;
    END IF;

    -- (c) Produccion puede escribir el snapshot...
    IF NOT EXISTS (
        SELECT 1 FROM pg_class c
          JOIN pg_attribute at ON at.attrelid = c.oid AND at.attnum > 0
          CROSS JOIN LATERAL aclexplode(at.attacl) a
         WHERE c.relname = 'orden_produccion_consumo'
           AND at.attname = 'costo_unitario_snapshot'
           AND a.grantee::regrole::text = 'rol_encargado_produccion'
           AND a.privilege_type = 'UPDATE') THEN
        RAISE EXCEPTION 'Produccion sigue sin UPDATE sobre costo_unitario_snapshot.';
    END IF;

    -- (d) ...y sigue sin poder reescribir lo planificado.
    SELECT string_agg(at.attname, ', ') INTO faltan
      FROM pg_class c
      JOIN pg_attribute at ON at.attrelid = c.oid AND at.attnum > 0
      CROSS JOIN LATERAL aclexplode(at.attacl) a
     WHERE c.relname = 'orden_produccion_consumo'
       AND at.attname IN ('cantidad_teorica', 'id_materia_prima', 'id_orden_produccion')
       AND a.grantee::regrole::text = 'rol_encargado_produccion'
       AND a.privilege_type = 'UPDATE';

    IF faltan IS NOT NULL THEN
        RAISE EXCEPTION 'Produccion ha ganado UPDATE sobre columnas que no debe '
                        'poder reescribir: %. Lo planificado no se reescribe.', faltan;
    END IF;
END $$;

COMMIT;

-- Verificacion
--   SELECT a.grantee::regrole::text, a.privilege_type
--     FROM pg_class c, aclexplode(c.relacl) a
--    WHERE c.relname = 'movimiento_inventario' AND a.grantee::regrole::text LIKE 'rol_%'
--    ORDER BY 1, 2;
--   -- esperado: compras y produccion con INSERT y SELECT.
