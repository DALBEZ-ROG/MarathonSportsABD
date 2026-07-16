---
inclusion: auto
---
# Deuda Técnica — Marathon Sports

Este archivo documenta items pendientes que DEBEN ser considerados al trabajar en el proyecto.

## Prioridad Alta

### 1. SQL de privilegios — Actualizar nombres de funciones
- **Archivo**: `sql/administracion_usuarios_roles_privilegios.sql`
- **Problema**: Los nombres de funciones en el script SQL no coinciden con los reales en la BD restaurada.
- **Funciones reales en la BD** (verificado):
  - `fn_proteger_total_pedido()`
  - `fn_recalcular_total_pedido()`
  - `fn_recalcular_total_pedido_delete()`
  - `fn_recalcular_total_pedido_stmt()`
  - `fn_recalcular_total_por_descuento()`
  - `fn_set_updated_at()`
  - `fn_trg_historial_inventario()`
  - `fn_validar_total_comprobante()`
- **Funciones incorrectas en el script** (del steering antiguo, NO existen):
  - `fn_generar_numero_pedido()` → NO EXISTE
  - `fn_actualizar_total_pedido()` → Correcto es `fn_recalcular_total_pedido()`
  - `fn_generar_numero_comprobante()` → NO EXISTE
  - `fn_registrar_historial_inventario()` → Correcto es `fn_trg_historial_inventario()`
  - `fn_aplicar_movimiento_inventario()` → NO EXISTE
  - `fn_validar_stock_pedido()` → NO EXISTE
- **Acción**: Actualizar la sección 9 del SQL y la tabla de funciones/triggers en `.kiro/steering/database.md`

### 2. Steering `database.md` desactualizado
- **Problema**: El archivo `.kiro/steering/database.md` lista funciones y triggers que no coinciden con la BD real (probablemente fueron renombrados en una iteración posterior).
- **Acción**: Ejecutar `\df` y `\dy` en psql para obtener los nombres reales y actualizar el steering.

## Prioridad Media

### 3. Sidebar collapsed — margin-left no se ajusta
- **Problema**: Cuando el sidebar se colapsa (72px), el `main-content` mantiene `margin-left: 260px`.
- **Acción**: Implementar comunicación entre `NavbarComponent` y `AppComponent` (via servicio o output) para ajustar dinámicamente el margin.

### 4. Angular CLI analytics prompt
- **Problema**: Se agregó `"cli": {"analytics": false}` al inicio de `angular.json` para evitar prompts interactivos.
- **Nota**: Esto es inofensivo pero fue un workaround.

## Prioridad Baja

### 5. ViewEncapsulation en proveedores
- **Problema**: `proveedores.component.ts` tiene `ViewEncapsulation.None` importado pero solo como precaución. Los demás componentes CRUD no lo necesitan porque los estilos globales aplican correctamente.
- **Acción**: Puede removerse `ViewEncapsulation` del import si no causa problemas.

### 6. Estilos inline vaciados
- **Problema**: 20+ componentes tienen `styles: ['/* Inherits global dark theme from styles.scss */']`. Si algún componente necesita estilos únicos en el futuro, agregar nuevos estilos inline ahí (no en el global).
- **Nota**: El archivo `marathon-frontend/src/styles.scss` tiene ~2000 líneas de estilos globales que cubren todos los patrones CRUD, modales, tablas, badges, chat IA, reportes, etc.

## Información de Entorno

- PostgreSQL en esta PC: versión 18, password del user postgres: se encuentra en `.env` y `application-local.properties` (gitignored)
- Java: OpenJDK 17.0.19 (Microsoft), instalado via winget
- Maven: 3.9.16, ubicado en `C:\Users\dbeni\OneDrive\Documentos\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin`
- Node: v24.15.0, npm: 11.12.1
- El proyecto se corre con `mvn spring-boot:run` en marathon-backend y `npx ng serve` en marathon-frontend
