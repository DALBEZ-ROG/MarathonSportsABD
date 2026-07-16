-- ═══════════════════════════════════════════════════════════════════════
-- MARATHON SPORTS — Administración de Usuarios, Roles y Privilegios
-- Base de Datos: mod_venta_inve (PostgreSQL 15+)
-- ═══════════════════════════════════════════════════════════════════════
-- Este script implementa seguridad a nivel de base de datos (RLS)
-- complementando la seguridad a nivel de aplicación (Spring Security + JWT)
-- ═══════════════════════════════════════════════════════════════════════


-- ╔═══════════════════════════════════════════╗
-- ║  1. IDENTIFICACIÓN DE USUARIOS           ║
-- ╚═══════════════════════════════════════════╝

-- El sistema cuenta con 4 perfiles de usuario:
-- 1. Administrador       → Acceso total al sistema
-- 2. Supervisor E-Commerce → Dashboard, reportes, consulta de pedidos
-- 3. Operador de Bodega  → Inventario, picking, empaque, movimientos de stock
-- 4. Operador de Pedidos → Crear pedidos, gestionar clientes, comprobantes


-- ╔═══════════════════════════════════════════╗
-- ║  2. CREACIÓN DE ROLES EN PostgreSQL       ║
-- ╚═══════════════════════════════════════════╝

-- Primero eliminamos si existen (para poder re-ejecutar)
DROP ROLE IF EXISTS rol_administrador;
DROP ROLE IF EXISTS rol_supervisor;
DROP ROLE IF EXISTS rol_operador_bodega;
DROP ROLE IF EXISTS rol_operador_pedidos;

-- Crear roles (sin login — se asignarán a usuarios)
CREATE ROLE rol_administrador;
CREATE ROLE rol_supervisor;
CREATE ROLE rol_operador_bodega;
CREATE ROLE rol_operador_pedidos;

COMMENT ON ROLE rol_administrador IS 'Acceso total: CRUD en todas las tablas, funciones y backups';
COMMENT ON ROLE rol_supervisor IS 'Consulta de pedidos, dashboard, reportes. Solo SELECT';
COMMENT ON ROLE rol_operador_bodega IS 'Gestión de inventario, picking, empaque. SELECT + UPDATE en inventario';
COMMENT ON ROLE rol_operador_pedidos IS 'Creación de pedidos y clientes. SELECT + INSERT en pedidos/clientes';


-- ╔═══════════════════════════════════════════╗
-- ║  3. CREACIÓN DE USUARIOS EN PostgreSQL    ║
-- ╚═══════════════════════════════════════════╝

-- Eliminamos si existen
DROP USER IF EXISTS usr_admin_marathon;
DROP USER IF EXISTS usr_supervisor_marathon;
DROP USER IF EXISTS usr_bodega_marathon;
DROP USER IF EXISTS usr_pedidos_marathon;

-- Crear usuarios con login y contraseña
CREATE USER usr_admin_marathon WITH PASSWORD 'Admin2024!Mrt' LOGIN;
CREATE USER usr_supervisor_marathon WITH PASSWORD 'Super2024!Mrt' LOGIN;
CREATE USER usr_bodega_marathon WITH PASSWORD 'Bodega2024!Mrt' LOGIN;
CREATE USER usr_pedidos_marathon WITH PASSWORD 'Pedidos2024!Mrt' LOGIN;

-- Asignar roles a usuarios
GRANT rol_administrador TO usr_admin_marathon;
GRANT rol_supervisor TO usr_supervisor_marathon;
GRANT rol_operador_bodega TO usr_bodega_marathon;
GRANT rol_operador_pedidos TO usr_pedidos_marathon;


-- ╔═══════════════════════════════════════════╗
-- ║  4. PRIVILEGIOS DEL ADMINISTRADOR         ║
-- ╚═══════════════════════════════════════════╝

-- El administrador tiene acceso TOTAL a todas las tablas
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO rol_administrador;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO rol_administrador;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO rol_administrador;

-- Privilegio para crear roles y usuarios
ALTER ROLE usr_admin_marathon CREATEROLE;

-- Privilegio para realizar backups
GRANT pg_read_all_data TO rol_administrador;    -- Leer toda la BD (para backup)
GRANT pg_write_all_data TO rol_administrador;   -- Escribir toda la BD (para restore)

-- Privilegios específicos por tabla (para documentación)
-- SELECT, INSERT, UPDATE, DELETE en TODAS las tablas:
GRANT SELECT, INSERT, UPDATE, DELETE ON
    ciudad, categoria, unidad_medida, proveedor, bodega,
    cliente, usuario, rol, usuario_rol, permiso, rol_permiso,
    producto, producto_proveedor, inventario, historial_inventario,
    pedido, detalle_pedido, comprobante_interno, movimiento_inventario
TO rol_administrador;


-- ╔═══════════════════════════════════════════╗
-- ║  5. PRIVILEGIOS DEL SUPERVISOR            ║
-- ╚═══════════════════════════════════════════╝

-- Solo lectura (SELECT) en tablas de consulta
GRANT SELECT ON
    pedido,
    detalle_pedido,
    cliente,
    producto,
    categoria,
    inventario,
    bodega,
    comprobante_interno,
    movimiento_inventario,
    historial_inventario
TO rol_supervisor;

-- Acceso a secuencias (necesario para consultas con JOINs)
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO rol_supervisor;

-- NO tiene INSERT, UPDATE, DELETE en ninguna tabla
-- NO tiene acceso a: usuario, rol, permiso, usuario_rol, rol_permiso


-- ╔═══════════════════════════════════════════╗
-- ║  6. PRIVILEGIOS DEL OPERADOR DE BODEGA    ║
-- ╚═══════════════════════════════════════════╝

-- SELECT en tablas que necesita consultar
GRANT SELECT ON
    producto,
    categoria,
    bodega,
    inventario,
    historial_inventario,
    pedido,
    detalle_pedido,
    comprobante_interno,
    movimiento_inventario
TO rol_operador_bodega;

-- UPDATE en inventario (modificar stock)
GRANT UPDATE ON inventario TO rol_operador_bodega;

-- INSERT en movimientos y comprobantes (registrar movimientos de stock)
GRANT INSERT ON movimiento_inventario TO rol_operador_bodega;
GRANT INSERT ON comprobante_interno TO rol_operador_bodega;

-- UPDATE en detalle_pedido (marcar picking completado)
GRANT UPDATE (picking_completado, cantidad_recogida) ON detalle_pedido TO rol_operador_bodega;

-- UPDATE en pedido (cambiar estado a empacado/enviado, datos de empaque)
GRANT UPDATE (estado, numero_hu, transportista, region_destino, fecha_empaque, updated_at)
    ON pedido TO rol_operador_bodega;

-- Secuencias necesarias
GRANT USAGE ON SEQUENCE movimiento_inventario_id_movimiento_seq TO rol_operador_bodega;
GRANT USAGE ON SEQUENCE comprobante_interno_id_comprobante_seq TO rol_operador_bodega;

-- EXECUTE en funciones de inventario
GRANT EXECUTE ON FUNCTION fn_trg_historial_inventario() TO rol_operador_bodega;

-- NO tiene acceso a: usuario, rol, permiso, ciudad, proveedor, unidad_medida
-- NO puede DELETE en ninguna tabla


-- ╔═══════════════════════════════════════════╗
-- ║  7. PRIVILEGIOS DEL OPERADOR DE PEDIDOS   ║
-- ╚═══════════════════════════════════════════╝

-- SELECT en tablas que necesita consultar
GRANT SELECT ON
    cliente,
    producto,
    categoria,
    bodega,
    inventario,
    pedido,
    detalle_pedido,
    comprobante_interno
TO rol_operador_pedidos;

-- INSERT en pedidos y detalles (crear pedidos)
GRANT INSERT ON pedido TO rol_operador_pedidos;
GRANT INSERT ON detalle_pedido TO rol_operador_pedidos;

-- INSERT en clientes (registrar nuevos clientes)
GRANT INSERT ON cliente TO rol_operador_pedidos;

-- UPDATE en clientes (editar datos del cliente)
GRANT UPDATE ON cliente TO rol_operador_pedidos;

-- UPDATE en pedido (cambiar estado, anular)
GRANT UPDATE (estado, updated_at, es_pedido_especial, tipo_especial, nota_especial, fecha_limite_entrega)
    ON pedido TO rol_operador_pedidos;

-- INSERT en comprobantes (generar comprobante de pedido)
GRANT INSERT ON comprobante_interno TO rol_operador_pedidos;

-- Secuencias necesarias para INSERT
GRANT USAGE ON SEQUENCE pedido_id_pedido_seq TO rol_operador_pedidos;
GRANT USAGE ON SEQUENCE detalle_pedido_id_detalle_seq TO rol_operador_pedidos;
GRANT USAGE ON SEQUENCE cliente_id_cliente_seq TO rol_operador_pedidos;
GRANT USAGE ON SEQUENCE comprobante_interno_id_comprobante_seq TO rol_operador_pedidos;

-- EXECUTE en funciones de pedidos
GRANT EXECUTE ON FUNCTION fn_recalcular_total_pedido() TO rol_operador_pedidos;
GRANT EXECUTE ON FUNCTION fn_recalcular_total_pedido_delete() TO rol_operador_pedidos;
GRANT EXECUTE ON FUNCTION fn_proteger_total_pedido() TO rol_operador_pedidos;
GRANT EXECUTE ON FUNCTION fn_validar_total_comprobante() TO rol_operador_pedidos;
GRANT EXECUTE ON FUNCTION fn_set_updated_at() TO rol_operador_pedidos;

-- NO tiene acceso a: usuario, rol, permiso, inventario (UPDATE), historial
-- NO puede DELETE en ninguna tabla (solo anular con UPDATE estado)


-- ╔═══════════════════════════════════════════╗
-- ║  8. PRIVILEGIOS SOBRE OBJETOS (RESUMEN)   ║
-- ╚═══════════════════════════════════════════╝

/*
┌──────────────────────────┬───────────────┬─────────────┬─────────────────┬─────────────────┐
│ TABLA                    │ ADMINISTRADOR │ SUPERVISOR  │ OP. BODEGA      │ OP. PEDIDOS     │
├──────────────────────────┼───────────────┼─────────────┼─────────────────┼─────────────────┤
│ ciudad                   │ ALL           │ -           │ -               │ -               │
│ categoria                │ ALL           │ SELECT      │ SELECT          │ SELECT          │
│ unidad_medida            │ ALL           │ -           │ -               │ -               │
│ proveedor                │ ALL           │ -           │ -               │ -               │
│ producto                 │ ALL           │ SELECT      │ SELECT          │ SELECT          │
│ producto_proveedor       │ ALL           │ -           │ -               │ -               │
│ bodega                   │ ALL           │ SELECT      │ SELECT          │ SELECT          │
│ inventario               │ ALL           │ SELECT      │ SELECT, UPDATE  │ SELECT          │
│ historial_inventario     │ ALL           │ SELECT      │ SELECT          │ -               │
│ movimiento_inventario    │ ALL           │ SELECT      │ SELECT, INSERT  │ -               │
│ comprobante_interno      │ ALL           │ SELECT      │ SELECT, INSERT  │ SELECT, INSERT  │
│ cliente                  │ ALL           │ SELECT      │ -               │ SELECT,INS,UPD  │
│ pedido                   │ ALL           │ SELECT      │ SELECT, UPDATE* │ SELECT,INS,UPD* │
│ detalle_pedido           │ ALL           │ SELECT      │ SELECT, UPDATE* │ SELECT, INSERT  │
│ usuario                  │ ALL           │ -           │ -               │ -               │
│ rol                      │ ALL           │ -           │ -               │ -               │
│ usuario_rol              │ ALL           │ -           │ -               │ -               │
│ permiso                  │ ALL           │ -           │ -               │ -               │
│ rol_permiso              │ ALL           │ -           │ -               │ -               │
└──────────────────────────┴───────────────┴─────────────┴─────────────────┴─────────────────┘
* UPDATE limitado a columnas específicas
*/


-- ╔═══════════════════════════════════════════╗
-- ║  9. PRIVILEGIOS SOBRE FUNCIONES (EXECUTE) ║
-- ╚═══════════════════════════════════════════╝

/*
┌─────────────────────────────────────────┬───────────────┬─────────────┬──────────────┬──────────────┐
│ FUNCIÓN                                 │ ADMINISTRADOR │ SUPERVISOR  │ OP. BODEGA   │ OP. PEDIDOS  │
├─────────────────────────────────────────┼───────────────┼─────────────┼──────────────┼──────────────┤
│ fn_proteger_total_pedido()              │ EXECUTE       │ -           │ -            │ EXECUTE      │
│ fn_recalcular_total_pedido()            │ EXECUTE       │ -           │ -            │ EXECUTE      │
│ fn_recalcular_total_pedido_delete()     │ EXECUTE       │ -           │ -            │ EXECUTE      │
│ fn_recalcular_total_pedido_stmt()       │ EXECUTE       │ -           │ -            │ -            │
│ fn_recalcular_total_por_descuento()     │ EXECUTE       │ -           │ -            │ -            │
│ fn_set_updated_at()                     │ EXECUTE       │ -           │ -            │ EXECUTE      │
│ fn_trg_historial_inventario()           │ EXECUTE       │ -           │ EXECUTE      │ -            │
│ fn_validar_total_comprobante()          │ EXECUTE       │ -           │ -            │ EXECUTE      │
└─────────────────────────────────────────┴───────────────┴─────────────┴──────────────┴──────────────┘
*/


-- ╔═══════════════════════════════════════════╗
-- ║  10. REVOCAR ACCESO PÚBLICO              ║
-- ╚═══════════════════════════════════════════╝

-- Revocar permisos por defecto del esquema public
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC;

-- Permitir conexión a la BD solo a los roles definidos
REVOKE CONNECT ON DATABASE mod_venta_inve FROM PUBLIC;
GRANT CONNECT ON DATABASE mod_venta_inve TO rol_administrador;
GRANT CONNECT ON DATABASE mod_venta_inve TO rol_supervisor;
GRANT CONNECT ON DATABASE mod_venta_inve TO rol_operador_bodega;
GRANT CONNECT ON DATABASE mod_venta_inve TO rol_operador_pedidos;

-- Permitir uso del esquema public
GRANT USAGE ON SCHEMA public TO rol_administrador;
GRANT USAGE ON SCHEMA public TO rol_supervisor;
GRANT USAGE ON SCHEMA public TO rol_operador_bodega;
GRANT USAGE ON SCHEMA public TO rol_operador_pedidos;


-- ╔═══════════════════════════════════════════╗
-- ║  11. VERIFICACIÓN DE PRIVILEGIOS          ║
-- ╚═══════════════════════════════════════════╝

-- Consultar privilegios de un usuario sobre tablas
-- SELECT grantee, table_name, privilege_type
-- FROM information_schema.table_privileges
-- WHERE grantee = 'rol_operador_bodega';

-- Consultar roles asignados a un usuario
-- SELECT r.rolname AS rol, m.rolname AS miembro
-- FROM pg_auth_members am
-- JOIN pg_roles r ON r.oid = am.roleid
-- JOIN pg_roles m ON m.oid = am.member;


-- ╔═══════════════════════════════════════════╗
-- ║  12. BACKUP Y RESTORE                    ║
-- ╚═══════════════════════════════════════════╝

-- Backup completo (solo el administrador puede ejecutar):
-- pg_dump -U usr_admin_marathon -d mod_venta_inve -F c -f backup_marathon.dump

-- Backup solo datos:
-- pg_dump -U usr_admin_marathon -d mod_venta_inve --data-only -F c -f datos_marathon.dump

-- Restore:
-- pg_restore -U usr_admin_marathon -d mod_venta_inve --clean --if-exists backup_marathon.dump

-- El supervisor y operadores NO pueden ejecutar pg_dump porque no tienen pg_read_all_data
