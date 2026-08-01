---
inclusion: auto
---
# Deuda Técnica — puntero al documento maestro

> ⚠️ **La deuda técnica del proyecto se documenta en `DEUDA_TECNICA.md` en la raíz.**
> Ese archivo es el maestro: tiene una sección por fase (F21–F32) y un
> **INVENTARIO CONSOLIDADO** con la deuda clasificada por prioridad, lo resuelto y el
> trabajo futuro. Consúltalo ahí; este archivo solo conserva datos de entorno.
>
> Este archivo contenía antes una copia parcial y desactualizada (última edición previa:
> 2026-07-15, anterior a la Fase 21). Se vació en la **Fase 32** para eliminar la
> duplicación y evitar que el steering aportara información contradictoria.

## Corrección importante aplicada en F32

La versión anterior de este archivo advertía —con razón— que la tabla de funciones y
triggers de `.kiro/steering/database.md` no coincidía con la BD real. **Esa advertencia
era correcta y quedó resuelta en la Fase 32**: `database.md` se regeneró consultando
`pg_proc` y `pg_trigger`, y ahora refleja las **17 funciones y 24 triggers** reales.

Nombres que el steering antiguo listaba y que **NO existen** en la base:
`fn_generar_numero_pedido`, `fn_actualizar_total_pedido`, `fn_generar_numero_comprobante`,
`fn_registrar_historial_inventario`, `fn_aplicar_movimiento_inventario`,
`fn_validar_stock_pedido`.

Los nombres reales equivalentes son `fn_recalcular_total_pedido` (+ variantes `_stmt` y
`_delete`) y `fn_trg_historial_inventario`.

> ✅ **Verificado en F32:** `sql/administracion_usuarios_roles_privilegios.sql` **ya usa
> los nombres correctos** (`fn_trg_historial_inventario`, `fn_recalcular_total_pedido`,
> `fn_recalcular_total_pedido_delete`, `fn_recalcular_total_pedido_stmt`,
> `fn_recalcular_total_por_descuento`). Esa deuda estaba resuelta; solo este steering
> seguía reportándola. Único detalle menor pendiente: el script no otorga privilegios
> sobre las funciones nuevas de F21–F29, pero es un script opcional de administración
> de usuarios de BD que no afecta el funcionamiento de la aplicación.

## Información de Entorno (esta PC)

- **PostgreSQL 18** (aunque el proyecto apunta a 15 como mínimo). La contraseña del
  usuario `postgres` está en `.env` y `application-local.properties` (ambos gitignored).
- **Java**: OpenJDK 17.0.19 (Microsoft), instalado vía winget.
- **Maven**: 3.9.16 en `C:\Users\dbeni\OneDrive\Documentos\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin`
- **Node**: v24.15.0 · **npm**: 11.12.1
- **Arranque**: `mvn spring-boot:run` en `marathon-backend` y `npx ng serve` en `marathon-frontend`.
- **MCP**: servidor `postgres-marathon` configurado en `.kiro/settings/mcp.json`
  (gitignored porque contiene la contraseña). Da acceso de lectura/escritura a la BD.
- Si el puerto 8080 está ocupado, arrancar con
  `mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8085"`.
