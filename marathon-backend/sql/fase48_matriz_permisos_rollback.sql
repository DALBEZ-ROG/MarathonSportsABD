-- =============================================================================
-- Fase 48 — REVERSION
-- =============================================================================
-- Deshace fase48_matriz_permisos.sql: vacia rol_permiso y borra los 45 permisos
-- que la F48 anadio, dejando los 49 originales.
--
-- ATENCION, EL ORDEN IMPORTA
-- Revertir SOLO esto deja el sistema PEOR que antes de la F48: las anotaciones
-- @PreAuthorize del codigo seguirian comprobando permisos que ya no existiria
-- nadie que tuviera, y todos los roles menos el Administrador perderian el
-- acceso a casi todo. Antes de ejecutar este script hay que retirar las
-- anotaciones (o poner app.permisos.aplicar=false, si se conserva ese
-- interruptor).
--
-- Este script NO restaura las asignaciones que habia antes de la F48. No se
-- puede: contradecian a SecurityConfig en varios sitios, y reconstruirlas seria
-- reconstruir el defecto. Lo que hace es dejar la matriz vacia para que
-- DataInitializer o la pantalla de roles la vuelvan a cargar.
-- =============================================================================

BEGIN;

DELETE FROM rol_permiso;

DELETE FROM permiso WHERE (modulo, accion) IN (
    ('ciudades','ver'),('ciudades','crear'),('ciudades','editar'),('ciudades','eliminar'),
    ('unidades_medida','ver'),('unidades_medida','crear'),('unidades_medida','editar'),('unidades_medida','eliminar'),
    ('clientes','editar'),('clientes','eliminar'),
    ('empaque','ver'),('empaque','confirmar'),
    ('devoluciones','ver'),('devoluciones','crear'),('devoluciones','inspeccionar'),('devoluciones','reembolsar'),
    ('recepciones','ver'),('recepciones','registrar'),
    ('facturas_compra','ver'),('facturas_compra','registrar'),('facturas_compra','anular'),
    ('cuentas_por_pagar','ver'),('cuentas_por_pagar','gestionar'),
    ('pagos_proveedor','ver'),('pagos_proveedor','registrar'),
    ('devoluciones_proveedor','ver'),('devoluciones_proveedor','crear'),('devoluciones_proveedor','resolver'),
    ('materia_prima','ver'),('materia_prima','crear'),('materia_prima','editar'),
    ('materia_prima','eliminar'),('materia_prima','movimiento'),
    ('produccion','ver'),('produccion','crear'),('produccion','iniciar'),
    ('produccion','completar'),('produccion','cancelar'),
    ('bom','ver'),('bom','editar'),
    ('analisis_costos','ver'),
    ('reportes','manufactura'),
    ('auditoria','ver'),
    ('logs','ver'),
    ('ia','consultar')
);

COMMIT;

-- Verificacion
--   SELECT count(*) FROM permiso;       -- esperado: 49
--   SELECT count(*) FROM rol_permiso;   -- esperado: 0
