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


## Fase 23 — Factura de Compra y Cuentas por Pagar (2026-07-23)

Cierra el ciclo Procure-to-Pay: registrar factura del proveedor, generar cuenta por pagar, registrar pagos hasta saldarla.

### Simplificaciones / alcance limitado de esta fase
- **Recálculo de cuentas vencidas es bajo demanda.** Cada vez que se listan las cuentas por pagar, se ejecuta `UPDATE cuenta_por_pagar SET estado='vencida' WHERE estado='vigente' AND fecha_vencimiento < CURRENT_DATE`. No hay un job programado (cron/scheduled task). Suficiente para el volumen académico actual; si el sistema crece, conviene un `@Scheduled` que corra una vez al día.
- **Una factura por número de proveedor por orden.** La UNIQUE constraint `(id_orden_compra, numero_factura_proveedor)` impide duplicados. Si un proveedor emitiese dos facturas con el mismo número para distintas órdenes, sí está permitido.
- **No hay anulación de pagos.** Una vez registrado un pago, no se puede revertir desde la aplicación. Corrección manual en BD si se registra erróneamente.
- **factura_compra.total es GENERATED** (`subtotal + impuesto`). No se soportan retenciones ni descuentos adicionales. Si en el futuro se necesitan, habría que agregar columnas y ajustar la expresión generada.
- **cuenta_por_pagar.saldo_pendiente es GENERATED** (`monto_total - monto_pagado`). No soporta notas de crédito ni ajustes parciales fuera de pagos.

### Decisiones de diseño
- **Trigger statement-level con REFERENCING** (`fn_recalcular_monto_pagado_cxp`) para recalcular `monto_pagado` y marcar automáticamente estado `pagada` cuando se alcanza el total. Mismo patrón que `fn_recalcular_total_orden_compra_stmt`.
- **Trigger de protección** (`fn_proteger_monto_pagado_cxp`) impide UPDATE manual del campo `monto_pagado`. Patrón idéntico a `fn_proteger_total_orden_compra`.
- **Cascada lógica factura→CxP:** cuando la cuenta queda `pagada`, el trigger también marca `factura_compra.estado = 'pagada'`.
- **Validación en servicio ANTES de insertar pago:** `dto.monto ≤ saldo_pendiente`. No se confía solo en el CHECK de BD para dar mensaje claro al usuario.

### Riesgos detectados
- Si se manipulara `pago_proveedor` directamente en BD (INSERT sin pasar por el servicio), el trigger igual recalcula, pero no se verifica que monto ≤ saldo_pendiente (la BD solo tiene `CHECK monto_pagado <= monto_total` en la tabla CxP, no en la tabla pago_proveedor). Aceptable: toda escritura pasa por el servicio.
- La anulación de una factura con monto_pagado=0 marca la CxP como `pagada` (cerrada sin deuda). Si se quisiera distinguir "anulada" vs "pagada" en reportes, se podría agregar un estado `anulada` al CHECK de cuenta_por_pagar en una fase futura.

### Pendiente para próximas fases
- **F24 — Devolución de Cliente (RMA).**
- **F25 — Devolución a Proveedor** (notas de crédito que reduzcan CxP).
- **Job programado** para marcar cuentas vencidas automáticamente (mejora opcional).


## Fase 24 — Devolucion de Cliente (RMA) (2026-07-23)

Permite al cliente solicitar devolucion de productos de pedidos entregados. Bodega inspecciona y decide destino de cada item.

### Simplificaciones / alcance limitado de esta fase
- **Items 'defectuoso' quedan registrados pero sin flujo de devolucion a proveedor.** La inspeccion que marca un producto como defectuoso NO genera automaticamente una devolucion al proveedor. Eso es la **Fase 25 — Devolucion a Proveedor**, que consumira estos registros (leyendo `solicitud_devolucion_detalle` con `resultado_inspeccion = 'defectuoso'`) para agruparlos y generar la nota de devolucion.
- **No se valida que la cantidad devuelta no exceda la cantidad ya devuelta previamente.** Si un cliente hace dos solicitudes de devolucion sobre la misma linea de detalle_pedido, ambas se aceptan. Esto es aceptable para el alcance academico; en produccion se deberia validar contra un acumulado de devoluciones previas.
- **Inspeccion parcial permitida.** No es obligatorio inspeccionar todas las lineas en una sola llamada. Se pueden hacer multiples llamadas PUT /inspeccionar. El estado solo cambia a 'completada'/'rechazada' cuando TODAS tienen resultado.
- **Reembolso es un registro simple.** No genera nota de credito contable ni afecta el total del pedido. Es solo un registro informativo para trazabilidad.

### Decisiones de diseno
- **Solo pedidos 'entregado'** pueden tener solicitud de devolucion. Validado en servicio.
- **SET LOCAL app.current_user_id** antes de UPDATE a inventario.stock_actual (misma regla de siempre para trigger de historial).
- **Entrada de stock por 'apto_reventa':** crea/busca inventario en la bodega indicada y registra movimiento_inventario tipo 'entrada' con observacion descriptiva.
- **Estado final automatico:** si todas las lineas son 'rechazado' → estado solicitud = 'rechazada'; si al menos una no es rechazada → 'completada'.

### Riesgos detectados
- La validacion de cantidad_devuelta solo compara contra cantidad original de detalle_pedido, no contra un acumulado de devoluciones previas para esa linea. Corregir si se detecta abuso.
- No hay workflow de notificacion al cliente sobre el resultado de la inspeccion.

### Pendiente para proximas fases
- **F25 — Devolucion a Proveedor:** agrupa items 'defectuoso' de esta fase + los de recepcion (F22) para generar nota de devolucion al proveedor.


## Fase 25 — Devolucion a Proveedor (2026-07-23)

Agrupa productos defectuosos de dos origenes (RMA cliente F24 + recepcion F22) y los devuelve al proveedor.

### Simplificaciones / alcance limitado de esta fase
- **La resolucion por reembolso NO se aplica automaticamente como credito a cuenta_por_pagar.** El monto_reembolso queda registrado en `devolucion_proveedor.monto_reembolso` pero no genera una nota de credito ni reduce el saldo de ninguna CxP. Mejora futura: al resolver con reembolso, generar un abono contra la cuenta_por_pagar vigente de ese proveedor.
- **La bandeja de items disponibles usa findAll() + filtro en memoria.** Para volumenes grandes, deberia usar queries nativas con NOT IN subquery. Suficiente para el volumen academico actual.
- **Solo productos (no materia prima) se devuelven a proveedor.** Los items de recepcion con `tipo_item='materia_prima'` defectuosos no se incluyen en la bandeja (simplificacion deliberada — la materia prima defectuosa se gestionaria diferente en manufactura F26+).

### Decisiones de diseno
- **Asociacion polimorfica exclusiva** en `devolucion_proveedor_detalle`: cada linea es O de origen RMA (via `id_solicitud_devolucion_detalle`) O de recepcion (via `id_recepcion_detalle`), nunca ambos. CHECK `chk_dpd_origen_exclusivo`.
- **UNIQUE constraints** en `id_solicitud_devolucion_detalle` e `id_recepcion_detalle` garantizan que un mismo item defectuoso no se incluya en dos devoluciones distintas.
- **Proveedor sugerido:** para items RMA se busca `producto_proveedor.es_proveedor_principal=true`; para items de recepcion se usa directamente `orden_compra.id_proveedor`.
- **El ciclo de calidad queda cerrado end-to-end:** cliente devuelve (F24) -> inspeccion detecta defectuoso -> se agrupa y devuelve a proveedor (F25) -> proveedor resuelve con reembolso o reposicion.

### Riesgos detectados
- Si un producto no tiene `producto_proveedor` con `es_proveedor_principal=true`, el campo `idProveedorSugerido` queda null en la bandeja. El frontend deberia validar que no se seleccionen items sin proveedor sugerido.
- La validacion de que todos los items seleccionados sean del mismo proveedor es solo en frontend; el backend acepta la creacion sin validar coherencia con el proveedor (el proveedor se setea explicitamente en el DTO).

### Pendiente para proximas fases
- **Credito automatico contra CxP** al resolver con reembolso.
- **F26 — Materia Prima: Inventario y Kardex** (inicio Bloque Manufactura).


## Fase 26 — Materia Prima: Kardex de Movimientos (2026-07-23)

Agrega historial de movimientos (kardex) para materia prima, mismo patron que movimiento_inventario tiene para producto terminado.

### Simplificaciones / alcance limitado de esta fase
- **`id_orden_produccion` queda sin FK hasta F28.** La columna existe como INTEGER simple, pero la tabla `orden_produccion` no existe todavia. Se agregara `ALTER TABLE ... ADD CONSTRAINT fk_mmp_orden_produccion ...` en la Fase 28 cuando se cree esa tabla.
- **`listarStockBajo()` usa findAll() + filtro en memoria.** Para volumenes grandes, deberia usar una query nativa. Suficiente para el alcance academico.
- **No hay trigger automatico de kardex en BD.** A diferencia de `historial_inventario` (que usa trigger), el kardex de materia prima se registra explicitamente en el servicio Java. Esto es deliberado: el trigger de historial de inventario fue una decision de F6 que precede al proyecto; aqui se usa el patron explicito que da mas control.

### Decisiones de diseno
- **Retrofit de RecepcionMercanciaService (F22):** ahora al sumar stock de materia prima, tambien inserta un registro en `movimiento_materia_prima` tipo `entrada_compra` con stock_anterior y stock_nuevo reales. No se modifico la tabla ni logica de recepcion, solo se agrego el registro del kardex.
- **tipos `entrada_compra` y `salida_produccion` son SOLO automaticos** — nunca se exponen como opcion en el endpoint manual. El endpoint POST /movimiento solo acepta `ajuste` y `merma`.
- **`ajuste` puede ser incremento o decremento** — controlado por el campo `esIncremento` en el DTO. `merma` siempre resta.
- **Validacion de stock negativo:** si el movimiento dejaria stock_actual < 0, se rechaza con ValidationException.

### Pendiente para proximas fases
- **F27 — Lista de Materiales (BOM).**
- **F28 — Ordenes de Produccion:** consumo automatico de materia prima con tipo `salida_produccion`, y FK de `id_orden_produccion`.


## Fase 27 — Diferenciacion de Origen del Producto + Lista de Materiales (BOM) (2026-07-23)

Inicio del BLOQUE 9 — Manufactura. Un producto ahora es 'comprado' (se adquiere terminado via orden de compra) o 'fabricado' (Marathon lo produce consumiendo materia prima segun una receta/BOM). Esta fase SOLO define la receta; el consumo real ocurre en F28.

### Deuda técnica principal — falta campo de costo en materia_prima
- **`materia_prima` no tiene un campo `costo_unitario_estimado`.** Por eso `CostoProduccionEstimadoDTO.costoMateriaPrimaUnitario` se retorna **null** por ahora. El calculo correcto seria `SUM(lista_materiales.cantidad_necesaria * materia_prima.costo_unitario_estimado)` para producir 1 unidad. Se resolvera en la **Fase 29 (Costeo)**, que agregara dicho campo (probablemente un costo promedio ponderado alimentado por las recepciones/facturas de compra) y completara el calculo. Mientras tanto el DTO y el metodo `ListaMaterialesService.obtenerCostoProduccionEstimado()` existen pero devuelven costo null (no hay endpoint expuesto aun).

### Simplificaciones / alcance limitado de esta fase
- **El BOM define la receta pero NO consume stock.** Definir/editar la lista de materiales no descuenta materia prima. El consumo real (movimiento `salida_produccion` en el kardex F26) ocurre al fabricar en la **Fase 28 — Ordenes de Produccion**.
- **Reemplazo completo del BOM con "upsert", no insert puro.** El UNIQUE `(id_producto, id_materia_prima)` impide conservar una linea inactiva y a la vez insertar una nueva activa para la MISMA materia prima. Por eso `definirBom` desactiva todas las lineas actuales y, para las materias primas que reaparecen, REACTIVA el mismo registro con la nueva cantidad (en vez de insertar un duplicado). El historial se preserva solo para materias primas que salen del BOM (quedan inactivas). Es decir, no hay historial fino de "cantidades anteriores" de una misma materia prima reutilizada — si se requiere, habria que quitar el UNIQUE y versionar por fecha.
- **Edicion del BOM en la UI vive dentro del modal de producto (modulo Productos).** Como crear/editar producto (`PUT /api/productos/{id}`) es solo Administrador, el flujo integrado producto+BOM del frontend funciona plenamente para el Administrador. El **Encargado de Produccion** puede gestionar el BOM via el endpoint dedicado `PUT /api/productos/{id}/bom` (que si tiene permiso), pero no dispone aun de una pantalla propia de solo-BOM en el frontend. Mejora futura: un componente de gestion de BOM independiente del alta/edicion de producto.
- **Autocomplete de materia prima simplificado a `<select>`** poblado con hasta 1000 registros activos (mismo criterio que productos/proveedores en F21). Suficiente para el volumen actual.

### Decisiones de diseno
- **Dos triggers de integridad en BD (defensa en profundidad):**
  - `trg_validar_bom_producto_fabricado` (BEFORE INSERT OR UPDATE en `lista_materiales`): impide guardar una linea de BOM si el producto asociado no tiene `origen='fabricado'`.
  - `trg_validar_cambio_origen_producto` (BEFORE UPDATE OF origen en `producto`): impide cambiar un producto a `'comprado'` si tiene BOM activo.
  Aunque el backend valida en `ListaMaterialesService`/`ProductoService`, la BD es la fuente de verdad. El servicio atrapa la excepcion del trigger y la traduce a `ValidationException` con mensaje en español.
- **`producto.origen` VARCHAR(20) NOT NULL DEFAULT 'comprado'** + CHECK `chk_producto_origen`. El DEFAULT no rompe los 105 productos del seed original (todos de marcas → comprado).
- **Filtro por origen** implementado con un query JPQL unificado (`buscarConFiltros`) que cubre cualquier combinacion de filtros cuando `origen` esta presente, dejando intactas las ramas de filtrado previas.

### Hallazgo importante detectado durante la fase
- **La base de datos estaba en el estado F20 (solo 20 tablas base).** Los scripts SQL de las fases F21–F26 existian en `marathon-backend/sql/` pero NUNCA se habian aplicado a la BD `mod_venta_inve`, a pesar de que el steering las documentaba como completadas. Como `lista_materiales` tiene FK a `materia_prima` (creada en F21), se aplicaron primero, en orden, los scripts idempotentes `fase21`…`fase26` para alinear la BD con el estado documentado, y luego el `fase27_origen_producto_bom.sql`. Riesgo: si el entorno se recrea desde cero, debe aplicarse la secuencia completa de scripts SQL en orden. No existe un orquestador de migraciones (Flyway/Liquibase esta prohibido por las restricciones del proyecto), por lo que el orden de aplicacion manual es responsabilidad del operador.

### Pendiente para proximas fases
- **F28 — Ordenes de Produccion:** consume el BOM definido aqui para fabricar productos reales (movimientos `salida_produccion` de materia prima + `entrada` de producto terminado).
- **F29 — Costeo:** agregar `materia_prima.costo_unitario_estimado` y completar `CostoProduccionEstimadoDTO`.
