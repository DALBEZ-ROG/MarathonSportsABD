# Deuda Técnica — Marathon Sports

> Registro incremental de simplificaciones, decisiones pendientes y riesgos detectados por fase.
> **Regla:** NUNCA sobrescribir entradas anteriores. Solo agregar nuevas secciones al final.

## Fase 21 — Órdenes de Compra (2026-07-22)

Inicio del BLOQUE 8 — Compras (ciclo Procure-to-Pay). Primer módulo del bloque, sumado a las 20 fases base ya completadas.

### Simplificaciones / alcance limitado de esta fase
- **Sin recepción de mercancía real.** El campo `orden_compra_detalle.cantidad_recibida` existe y se valida (0 ≤ recibida ≤ cantidad), pero todavía NO hay un flujo que lo actualice ni transiciones automáticas a `recibida_parcial` / `recibida_completa`. Esos estados están definidos en el CHECK del `estado` y en la UI (badges), pero solo se alcanzan manualmente vía cambio de estado. La recepción real es la **Fase 22**.
- **materia_prima es solo catálogo.** No tiene inventario propio, ni kardex, ni movimientos de stock. Se creó ahora únicamente para soportar la asociación polimórfica exclusiva de `orden_compra_detalle` sin tener que alterar esa tabla más adelante. El inventario/kardex de materia prima llega en la **Fase 26 (Manufactura)**.
- **No hay generación de número de orden secuencial** (tipo `OC-000001`) como sí tienen pedidos y comprobantes. Actualmente se identifica por `id_orden_compra`. Si se requiere un número formal de OC, se puede agregar en una fase posterior con el mismo patrón de trigger que `fn_generar_numero_pedido`.
- **Selección de producto/materia prima en la UI mediante `<select>`** poblado con hasta 1000 registros (no autocomplete con búsqueda incremental server-side). Suficiente para el volumen actual; si el catálogo crece mucho, conviene migrar a un autocomplete con búsqueda remota.
- **`observacion` del cambio de estado** (DTO `CambioEstadoOrdenCompraDTO`) se registra en el log de auditoría pero NO se persiste en una columna dedicada de historial de la orden. Si se requiere historial formal de transiciones con motivos (p. ej. razón de rechazo visible en UI), se necesitaría una tabla `orden_compra_historial`.

### Decisiones de diseño
- **Asociación polimórfica exclusiva** en `orden_compra_detalle` garantizada por CHECK constraint `chk_oc_detalle_item_exclusivo` (no por lógica de aplicación). La app también valida por UX y mensajes en español, pero la BD es la fuente de verdad.
- **`orden_compra.total`** calculado por trigger statement-level (`fn_recalcular_total_orden_compra_stmt`, 3 triggers INSERT/UPDATE/DELETE) + trigger de protección (`fn_proteger_total_orden_compra`) que impide UPDATE manual. Mismo patrón que `pedido.total`. En JPA: `@Column(insertable=false, updatable=false)`.
- **`orden_compra_detalle.subtotal`** es columna GENERATED (`cantidad * precio_unitario`). En JPA: `@Column(insertable=false, updatable=false)`.
- **Separación de funciones:** quien crea/envía a aprobación NO puede aprobar su propia orden. Solo el Administrador aprueba/rechaza, y adicionalmente se valida que el aprobador ≠ solicitante.

### Riesgos detectados
- **Estado `recibida_parcial` / `recibida_completa` alcanzables manualmente** vía `PUT /estado` por Admin/Encargado de Compras aunque no exista recepción real todavía. La máquina de estados actual solo bloquea transiciones desde estados finales; conviene endurecer estas transiciones cuando se implemente la Fase 22 (que debería ser la única vía de llegar a esos estados).
- **Budget de estilos en build de producción (Angular):** el proyecto tiene varios componentes que exceden el `anyComponentStyles budget` de 4 kB (pre-existente: login, dashboard, auditoría). No afecta el build de desarrollo ni la funcionalidad. Deuda de configuración a resolver globalmente (subir el budget en `angular.json` o extraer estilos a `styles.scss`).
- **Validación de FK `id_usuario_aprobador ON DELETE SET NULL`:** si se elimina un usuario aprobador, la orden queda sin aprobador registrado. Aceptable para trazabilidad mínima; el log de auditoría conserva el registro del cambio.

### Pendiente para próximas fases
- **F22 — Recepción de mercancía:** actualizar `cantidad_recibida`, transiciones `aprobada → recibida_parcial → recibida_completa`, y afectación de inventario de productos.
- **F26 — Manufactura:** inventario/kardex de materia prima, BOM, órdenes de producción.
- **Facturas y cuentas por pagar** (rol Encargado de Compras) — fases posteriores del ciclo Procure-to-Pay.


## Fase 22 — Recepción de Mercancía (2026-07-22)

Conecta una orden de compra aprobada con el inventario real: al recibir, sube stock de producto (por bodega) o el stock global de materia prima. Soporta recepciones parciales múltiples.

### Simplificaciones / alcance limitado de esta fase
- **`cantidad_defectuosa` se registra pero no genera flujo de devolución.** Cada línea de recepción guarda cuánto llegó defectuoso, pero NO se descuenta al proveedor ni se genera nota de devolución/crédito. La cantidad defectuosa simplemente NO entra al stock (solo entra `cantidad_buena = recibida - defectuosa`). El flujo de devolución a proveedor es la **Fase 25**.
- **Stock de materia prima es GLOBAL, sin bodega.** `materia_prima.stock_actual` es un único acumulador (NUMERIC, admite decimales para metros/kg/litros). No hay tabla `inventario_materia_prima` por bodega como sí existe para producto terminado. Simplificación deliberada; si en el futuro se requieren múltiples bodegas de materiales, habrá que introducir esa tabla (probablemente en Fase 26 Manufactura).
- **Materia prima no registra movimiento ni historial de kardex.** A diferencia del producto (que genera `movimiento_inventario` + trigger de `historial_inventario`), la entrada de materia prima solo hace `UPDATE materia_prima.stock_actual`. No queda trazabilidad de movimientos de materia prima todavía; la recepción (`recepcion_mercancia_detalle`) es el único rastro. Kardex de materia prima: Fase 26.
- **Recepción no editable ni anulable.** Una vez registrada una recepción, no hay endpoint para corregirla o revertirla (afectaría stock y `cantidad_recibida` acumulada). Si se registra una recepción errónea, corrección manual en BD por ahora.

### Decisiones de diseño
- **`orden_compra_detalle.cantidad_recibida` se ACUMULA** (`+= cantidadRecibidaAhora`), nunca se sobreescribe — una orden admite varias entregas parciales.
- **Cambio de estado de la orden es automático** tras cada recepción: si todas las líneas alcanzan su cantidad total → `recibida_completa`; si alguna tiene recibido > 0 pero no todas completas → `recibida_parcial`. Se hace con UPDATE directo (vía `save` de la entidad OrdenCompra), NO pasa por `OrdenCompraService.cambiarEstado()` para evitar la validación de roles de F21 (es un cambio del sistema, no de un usuario).
- **`SET LOCAL app.current_user_id`** se ejecuta antes de tocar `inventario.stock_actual`, para que el trigger `trg_historial_inventario` registre quién hizo el cambio (mismo patrón que `InventarioService` de F6-F8).
- **No existe `trg_aplicar_movimiento`** en la BD real (a pesar de lo que sugería el steering antiguo): el stock se actualiza manualmente en el servicio y `movimiento_inventario` es solo bitácora. Verificado antes de programar para evitar doble conteo.
- **`recepcion_mercancia.id_bodega` es obligatorio** aunque solo aplica a líneas de producto. Las líneas de materia prima ignoran la bodega (stock global). Se pide siempre por consistencia y para el caso común (órdenes con productos).

### Riesgos detectados
- La validación de "cantidad recibida ≤ pendiente" se hace en la aplicación (no hay CHECK en BD que compare contra `orden_compra_detalle`). Si se insertara directo en BD saltándose el servicio, se podría exceder. Aceptable: toda escritura pasa por el servicio.
- El estado `recibida_completa` es terminal en la práctica pero la máquina de estados de F21 aún permite `cancelada` desde `aprobada` (no desde recibida_*). Revisar reglas de cancelación cuando existan devoluciones (F25).

### Pendiente para próximas fases
- **F23 — Factura de Compra y Cuentas por Pagar.**
- **F25 — Devolución a proveedor** (consumir `cantidad_defectuosa`).
- **F26 — Manufactura:** kardex/movimientos de materia prima, posible inventario de materia prima por bodega.
