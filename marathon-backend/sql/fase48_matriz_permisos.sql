-- =============================================================================
-- Fase 48 (D-13) — La matriz de permisos deja de ser decorativa
-- =============================================================================
-- Cierra el defecto D-13, que quedaba abierto en docs/PENDIENTE.md §3.
--
-- EL DEFECTO
-- Habia 49 permisos, la tabla rol_permiso, un PermisoController, un claim en el
-- JWT y un array en la respuesta de login. Ninguna decision de autorizacion los
-- consultaba. La prueba de que no servian para nada: el rol «Encargado de
-- Produccion» tenia 0 permisos de 49 asignados y funcionaba con normalidad.
--
-- POR QUE NO SE PUDO CERRAR ANTES
-- Las dos salidas evidentes eran inviables. Aplicar los permisos tal como
-- estaban dejaba a Produccion sin acceso a nada de un dia para otro. Retirarlos
-- destruia la pantalla de roles, que funciona y es el editor de esta matriz.
--
-- LA SALIDA, EN EL ORDEN QUE PIDE PENDIENTE.md §3 D-13
--   (1) decidir y cargar que puede hacer cada rol  <-- ESTE SCRIPT
--   (2) verificar que ningun rol queda en cero     <-- ESTE SCRIPT, al final
--   (3) encender la comprobacion                   <-- las anotaciones
--                                                      @PreAuthorize del codigo
--
-- DE DONDE SALE LA MATRIZ: NO ESTA INVENTADA
-- Cada fila de abajo esta copiada de una regla que YA se aplica hoy en
-- SecurityConfig.securityFilterChain(), que es la autorizacion real del sistema
-- y funciona. Por eso encender la comprobacion no cambia lo que puede hacer
-- nadie: solo hace que el modelo de permisos —que hasta ahora describia— pase a
-- decidir. A partir de aqui, quitarle 'pedidos:crear' al Operador de Pedidos en
-- la pantalla de roles se lo quita de verdad.
--
-- LO QUE SI CAMBIA RESPECTO DE LOS DATOS ANTERIORES
--   - Se anaden 8 modulos que no tenian ninguna fila y cuyos endpoints existen
--     desde las fases 21-30: empaque, devoluciones, recepciones, facturas de
--     compra, cuentas por pagar, pagos a proveedor, devoluciones a proveedor,
--     materia prima, produccion, BOM, analisis de costos, auditoria, logs e IA.
--     Sin ellos, Produccion no podia tener ningun permiso: no habia ninguno que
--     describiera su trabajo.
--   - Se corrigen filas que contradecian a SecurityConfig. Las mas visibles:
--       'productos:ver' estaba en 2 roles y GET /api/productos lo permite a los
--       seis; 'bodegas:ver' y 'categorias:ver' estaban solo en Administrador y
--       son igual de publicas; 'compras:aprobar' y 'compras:rechazar' estaban en
--       Encargado de Compras, pero OrdenCompraService.cambiarEstado() exige
--       Administrador y ademas prohibe aprobar la orden que uno mismo solicito.
--   - rol_permiso se reconstruye ENTERA. Es a proposito: dejar filas viejas
--     conviviendo con las nuevas seria conservar precisamente las que
--     contradicen a SecurityConfig.
--
-- OJO CON DataInitializer
-- ensureComprasFase21() volvia a asignar los cinco permisos de 'compras' al
-- Encargado de Compras en CADA arranque, incluidos aprobar y rechazar. Se ha
-- retirado esa asignacion (el metodo sigue creando los dos roles, que si hacen
-- falta). Si se revierte ese cambio en Java, este script queda deshecho en el
-- siguiente arranque.
--
-- REVERSION: fase48_matriz_permisos_rollback.sql
-- =============================================================================

BEGIN;

CREATE TEMP TABLE matriz (modulo TEXT, accion TEXT, rol TEXT) ON COMMIT DROP;

INSERT INTO matriz (modulo, accion, rol) VALUES
-- ---------------------------------------------------------------------------
-- Administracion del propio sistema. Solo el Administrador. (SecurityConfig:
-- /api/usuarios/**, /api/roles/**, /api/permisos/** -> hasAuthority ADMIN)
-- ---------------------------------------------------------------------------
('usuarios','ver','Administrador'),
('usuarios','crear','Administrador'),
('usuarios','editar','Administrador'),
('usuarios','eliminar','Administrador'),
('roles','ver','Administrador'),
('roles','crear','Administrador'),
('roles','editar','Administrador'),
('roles','eliminar','Administrador'),
('logs','ver','Administrador'),
('auditoria','ver','Administrador'),

-- ---------------------------------------------------------------------------
-- Catalogos. LEER puede cualquiera con sesion —asi lo dice el bloque
-- "Catalogos e inventario: los seis roles tienen SELECT" de SecurityConfig—;
-- ESCRIBIR, solo el Administrador.
-- ---------------------------------------------------------------------------
('productos','ver','Administrador'),
('productos','ver','Supervisor E-Commerce'),
('productos','ver','Operador de Bodega'),
('productos','ver','Operador de Pedidos'),
('productos','ver','Encargado de Compras'),
('productos','ver','Encargado de Producción'),
('productos','crear','Administrador'),
('productos','editar','Administrador'),
('productos','eliminar','Administrador'),

('categorias','ver','Administrador'),
('categorias','ver','Supervisor E-Commerce'),
('categorias','ver','Operador de Bodega'),
('categorias','ver','Operador de Pedidos'),
('categorias','ver','Encargado de Compras'),
('categorias','ver','Encargado de Producción'),
('categorias','crear','Administrador'),
('categorias','editar','Administrador'),
('categorias','eliminar','Administrador'),

('ciudades','ver','Administrador'),
('ciudades','ver','Supervisor E-Commerce'),
('ciudades','ver','Operador de Bodega'),
('ciudades','ver','Operador de Pedidos'),
('ciudades','ver','Encargado de Compras'),
('ciudades','ver','Encargado de Producción'),
('ciudades','crear','Administrador'),
('ciudades','editar','Administrador'),
('ciudades','eliminar','Administrador'),

('unidades_medida','ver','Administrador'),
('unidades_medida','ver','Supervisor E-Commerce'),
('unidades_medida','ver','Operador de Bodega'),
('unidades_medida','ver','Operador de Pedidos'),
('unidades_medida','ver','Encargado de Compras'),
('unidades_medida','ver','Encargado de Producción'),
('unidades_medida','crear','Administrador'),
('unidades_medida','editar','Administrador'),
('unidades_medida','eliminar','Administrador'),

('bodegas','ver','Administrador'),
('bodegas','ver','Supervisor E-Commerce'),
('bodegas','ver','Operador de Bodega'),
('bodegas','ver','Operador de Pedidos'),
('bodegas','ver','Encargado de Compras'),
('bodegas','ver','Encargado de Producción'),
('bodegas','crear','Administrador'),
('bodegas','editar','Administrador'),
('bodegas','eliminar','Administrador'),

-- Proveedores es la excepcion del bloque de catalogos: ni Bodega, ni Pedidos,
-- ni Produccion tienen SELECT sobre la tabla proveedor en la base (F34).
('proveedores','ver','Administrador'),
('proveedores','ver','Supervisor E-Commerce'),
('proveedores','ver','Encargado de Compras'),
('proveedores','crear','Administrador'),
('proveedores','editar','Administrador'),
('proveedores','eliminar','Administrador'),

-- ---------------------------------------------------------------------------
-- Inventario. Ver, todos. Mover stock, solo quien lo mueve.
-- ---------------------------------------------------------------------------
('inventario','ver','Administrador'),
('inventario','ver','Supervisor E-Commerce'),
('inventario','ver','Operador de Bodega'),
('inventario','ver','Operador de Pedidos'),
('inventario','ver','Encargado de Compras'),
('inventario','ver','Encargado de Producción'),
('inventario','crear','Administrador'),
('inventario','editar','Administrador'),
('inventario','editar','Operador de Bodega'),
('inventario','eliminar','Administrador'),

-- ---------------------------------------------------------------------------
-- Circuito de venta. Compras y Produccion quedan fuera entero: no tienen
-- SELECT sobre cliente, pedido, detalle_pedido ni comprobante_interno, y sin
-- el pedido la respuesta ni siquiera se puede construir (nota de la F37).
-- ---------------------------------------------------------------------------
('clientes','ver','Administrador'),
('clientes','ver','Supervisor E-Commerce'),
('clientes','ver','Operador de Bodega'),
('clientes','ver','Operador de Pedidos'),
('clientes','crear','Administrador'),
('clientes','crear','Operador de Pedidos'),
('clientes','editar','Administrador'),
('clientes','editar','Operador de Pedidos'),
('clientes','eliminar','Administrador'),
('clientes','eliminar','Operador de Pedidos'),

('pedidos','ver','Administrador'),
('pedidos','ver','Supervisor E-Commerce'),
('pedidos','ver','Operador de Bodega'),
('pedidos','ver','Operador de Pedidos'),
('pedidos','crear','Administrador'),
('pedidos','crear','Operador de Pedidos'),
-- 'editar' y 'anular' son la misma llamada: PUT /api/pedidos/{id}/estado, que
-- SecurityConfig abre a Administrador, Bodega y Pedidos.
('pedidos','editar','Administrador'),
('pedidos','editar','Operador de Bodega'),
('pedidos','editar','Operador de Pedidos'),
('pedidos','anular','Administrador'),
('pedidos','anular','Operador de Bodega'),
('pedidos','anular','Operador de Pedidos'),
('pedidos','eliminar','Administrador'),

('picking','ver','Administrador'),
('picking','ver','Operador de Bodega'),
('picking','ejecutar','Administrador'),
('picking','ejecutar','Operador de Bodega'),
('picking','confirmar','Administrador'),
('picking','confirmar','Operador de Bodega'),

('empaque','ver','Administrador'),
('empaque','ver','Supervisor E-Commerce'),
('empaque','ver','Operador de Bodega'),
('empaque','ver','Operador de Pedidos'),
('empaque','confirmar','Administrador'),
('empaque','confirmar','Operador de Bodega'),

('comprobantes','ver','Administrador'),
('comprobantes','ver','Supervisor E-Commerce'),
('comprobantes','ver','Operador de Bodega'),
('comprobantes','ver','Operador de Pedidos'),
('comprobantes','emitir','Administrador'),
('comprobantes','emitir','Operador de Pedidos'),
('comprobantes','anular','Administrador'),

('devoluciones','ver','Administrador'),
('devoluciones','ver','Supervisor E-Commerce'),
('devoluciones','ver','Operador de Bodega'),
('devoluciones','ver','Operador de Pedidos'),
('devoluciones','crear','Administrador'),
('devoluciones','crear','Operador de Pedidos'),
('devoluciones','inspeccionar','Administrador'),
('devoluciones','inspeccionar','Operador de Bodega'),
('devoluciones','reembolsar','Administrador'),
('devoluciones','reembolsar','Operador de Pedidos'),

-- ---------------------------------------------------------------------------
-- Circuito de abastecimiento.
-- ---------------------------------------------------------------------------
('compras','ver','Administrador'),
('compras','ver','Encargado de Compras'),
('compras','crear','Administrador'),
('compras','crear','Encargado de Compras'),
-- Aprobar y rechazar son SOLO del Administrador. No es un endurecimiento nuevo:
-- OrdenCompraService.cambiarEstado() ya lo exigia ("Solo el Administrador puede
-- aprobar o rechazar ordenes de compra") y ademas impide aprobar la orden que
-- uno mismo solicito. Los datos anteriores decian lo contrario que el codigo.
('compras','aprobar','Administrador'),
('compras','rechazar','Administrador'),
('compras','cancelar','Administrador'),
('compras','cancelar','Encargado de Compras'),

('recepciones','ver','Administrador'),
('recepciones','ver','Encargado de Compras'),
('recepciones','registrar','Administrador'),
('recepciones','registrar','Encargado de Compras'),

('facturas_compra','ver','Administrador'),
('facturas_compra','ver','Encargado de Compras'),
('facturas_compra','registrar','Administrador'),
('facturas_compra','registrar','Encargado de Compras'),
('facturas_compra','anular','Administrador'),
('facturas_compra','anular','Encargado de Compras'),

('cuentas_por_pagar','ver','Administrador'),
('cuentas_por_pagar','ver','Supervisor E-Commerce'),
('cuentas_por_pagar','ver','Encargado de Compras'),
('cuentas_por_pagar','gestionar','Administrador'),
('cuentas_por_pagar','gestionar','Encargado de Compras'),

('pagos_proveedor','ver','Administrador'),
('pagos_proveedor','ver','Encargado de Compras'),
('pagos_proveedor','registrar','Administrador'),
('pagos_proveedor','registrar','Encargado de Compras'),

('devoluciones_proveedor','ver','Administrador'),
('devoluciones_proveedor','ver','Encargado de Compras'),
('devoluciones_proveedor','crear','Administrador'),
('devoluciones_proveedor','crear','Encargado de Compras'),
('devoluciones_proveedor','resolver','Administrador'),
('devoluciones_proveedor','resolver','Encargado de Compras'),

-- ---------------------------------------------------------------------------
-- Manufactura. Es el bloque que no existia, y por eso Produccion tenia cero.
-- ---------------------------------------------------------------------------
('materia_prima','ver','Administrador'),
('materia_prima','ver','Encargado de Compras'),
('materia_prima','ver','Encargado de Producción'),
('materia_prima','crear','Administrador'),
('materia_prima','crear','Encargado de Producción'),
('materia_prima','editar','Administrador'),
('materia_prima','editar','Encargado de Producción'),
('materia_prima','eliminar','Administrador'),
('materia_prima','eliminar','Encargado de Producción'),
('materia_prima','movimiento','Administrador'),
('materia_prima','movimiento','Encargado de Producción'),

('produccion','ver','Administrador'),
('produccion','ver','Supervisor E-Commerce'),
('produccion','ver','Encargado de Producción'),
('produccion','crear','Administrador'),
('produccion','crear','Encargado de Producción'),
('produccion','iniciar','Administrador'),
('produccion','iniciar','Encargado de Producción'),
('produccion','completar','Administrador'),
('produccion','completar','Encargado de Producción'),
('produccion','cancelar','Administrador'),
('produccion','cancelar','Encargado de Producción'),

('bom','ver','Administrador'),
('bom','ver','Encargado de Compras'),
('bom','ver','Encargado de Producción'),
('bom','editar','Administrador'),
('bom','editar','Encargado de Producción'),

('analisis_costos','ver','Administrador'),
('analisis_costos','ver','Supervisor E-Commerce'),
('analisis_costos','ver','Encargado de Producción'),

-- ---------------------------------------------------------------------------
-- Analitica.
-- ---------------------------------------------------------------------------
('dashboard','ver','Administrador'),
('dashboard','ver','Supervisor E-Commerce'),
('reportes','ver','Administrador'),
('reportes','ver','Supervisor E-Commerce'),
('reportes','exportar','Administrador'),
('reportes','exportar','Supervisor E-Commerce'),
-- Los informes de manufactura tienen su propia fila porque su reparto es otro:
-- SecurityConfig los abre tambien al Encargado de Produccion.
('reportes','manufactura','Administrador'),
('reportes','manufactura','Supervisor E-Commerce'),
('reportes','manufactura','Encargado de Producción'),
('ia','consultar','Administrador'),
('ia','consultar','Supervisor E-Commerce');

-- ---------------------------------------------------------------------------
-- 0. Los seis roles tienen que existir. Si falta alguno, la matriz saldria
--    incompleta EN SILENCIO, que es peor que no aplicarla.
-- ---------------------------------------------------------------------------
DO $$
DECLARE faltan TEXT;
BEGIN
    SELECT string_agg(m.rol, ', ') INTO faltan
    FROM (SELECT DISTINCT rol FROM matriz) m
    WHERE NOT EXISTS (SELECT 1 FROM rol r WHERE r.nombre = m.rol);

    IF faltan IS NOT NULL THEN
        RAISE EXCEPTION 'Faltan roles en la tabla rol: %. Los crea DataInitializer '
                        'en el primer arranque de la aplicacion; arrancala antes '
                        'de ejecutar este script.', faltan;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 1. Permisos que faltan. Los 49 que ya existian conservan su id_permiso.
-- ---------------------------------------------------------------------------
INSERT INTO permiso (modulo, accion, descripcion)
SELECT DISTINCT m.modulo, m.accion, m.modulo || ':' || m.accion
FROM matriz m
WHERE NOT EXISTS (
    SELECT 1 FROM permiso p WHERE p.modulo = m.modulo AND p.accion = m.accion
);

-- ---------------------------------------------------------------------------
-- 2. La matriz, reconstruida entera.
-- ---------------------------------------------------------------------------
DELETE FROM rol_permiso;

INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT DISTINCT r.id_rol, p.id_permiso
FROM matriz m
JOIN rol r     ON r.nombre = m.rol
JOIN permiso p ON p.modulo = m.modulo AND p.accion = m.accion;

-- ---------------------------------------------------------------------------
-- 3. Verificacion, DENTRO de la transaccion: si algun rol se queda sin
--    permisos, no se aplica nada. Es el paso (2) que pide PENDIENTE.md, y
--    ponerlo aqui es lo que impide repetir el problema que dejo a Produccion en
--    cero — esta vez con la comprobacion encendida, o sea sin acceso a nada.
-- ---------------------------------------------------------------------------
DO $$
DECLARE vacios TEXT;
BEGIN
    SELECT string_agg(r.nombre, ', ') INTO vacios
    FROM rol r
    WHERE NOT EXISTS (SELECT 1 FROM rol_permiso rp WHERE rp.id_rol = r.id_rol);

    IF vacios IS NOT NULL THEN
        RAISE EXCEPTION 'Estos roles quedarian con CERO permisos: %. '
                        'Con la comprobacion encendida se quedarian sin acceso a '
                        'nada. No se aplica la matriz.', vacios;
    END IF;
END $$;

COMMIT;

-- Verificacion
--   SELECT r.nombre, count(*) FROM rol r
--     JOIN rol_permiso rp ON rp.id_rol = r.id_rol
--    GROUP BY r.nombre ORDER BY 2 DESC;
--   -- esperado: ninguno en cero, y Encargado de Produccion con 24.
--   SELECT count(*) FROM permiso;   -- esperado: 94
