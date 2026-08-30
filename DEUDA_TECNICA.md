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


## Setup / DDL base — hallazgo crítico (2026-07-23)

Al documentar el orden de arranque desde cero (`SETUP_COMPLETO.md`) se detectó lo siguiente:

### PENDIENTE CRÍTICO (resuelto) — no existía DDL base de las 20 tablas F1–F20
- El repositorio **no incluía ningún script** con el DDL de las 20 tablas base (F1–F20): tablas, secuencias, índices, constraints, funciones y triggers. Ese esquema solo existía en la BD viva, lo que hacía **imposible reconstruir la BD desde cero** siguiendo el repo (los scripts `fase21`–`fase27` asumen que las tablas base y `unidad_medida`, `producto`, `usuario`, etc. ya existen).
- **Resolución:** se generó `marathon-backend/sql/fase00_ddl_base.sql` con `pg_dump --schema-only` sobre las 20 tablas base. Como `pg_dump -t` **no exporta las funciones** de los triggers, se antepusieron al archivo las 8 funciones usadas por los triggers de las tablas base (obtenidas vía `pg_get_functiondef`), dejando el archivo autoejecutable sobre una BD vacía.
- **Validación:** se ejecutó la secuencia completa `fase00 → fase21 … → fase27` sobre una BD temporal vacía (`setup_test_f27`) y se confirmó que crea las **35 tablas** sin errores. La BD temporal se eliminó tras la prueba.
- **Deuda remanente:** `fase00_ddl_base.sql` es un *snapshot* del estado actual de la BD, por lo que la tabla base `producto` ya trae la columna `origen` y el trigger `trg_validar_cambio_origen_producto` (añadidos por F27, que ALTERó una tabla base). Es inofensivo (el cuerpo plpgsql no valida `lista_materiales` al crear la función, y `fase27` es idempotente), pero significa que `fase00` no es un F20 "puro". Si se quisiera un baseline estrictamente F20, habría que editar manualmente el snapshot para quitar los artefactos de F27 sobre `producto`.

### Discrepancia de nombre de archivo F27
- Algunas notas/instrucciones se refieren al script de F27 como `fase27_bom_origen.sql`, pero el **nombre real en el repo** es `fase27_origen_producto_bom.sql`. `SETUP_COMPLETO.md` usa el nombre real.

### seed de negocio — presente
- `marathon-backend/sql/seed_marathon_sports.sql` **sí existe** y contiene los datos de negocio (ciudades, categorías, productos, proveedores, bodegas, inventario, clientes y pedidos). Requiere que `admin@marathon.com` exista previamente (lo crea `DataInitializer` al arrancar el backend); el propio script aborta con `RAISE EXCEPTION` si no lo encuentra. **No es idempotente**: ejecutarlo dos veces duplica datos o viola constraints UNIQUE.


## Fase 28 — Órdenes de Producción (2026-07-23)

Corazón del módulo de Manufactura: fabricar un producto 'fabricado' consumiendo materia prima según su BOM, registrando mermas y dando de alta el producto terminado en inventario. Tablas `orden_produccion` y `orden_produccion_consumo`.

### DEUDA RESUELTA de F26 — FK de `movimiento_materia_prima.id_orden_produccion`
- En F26 la columna `id_orden_produccion` se creó como INTEGER simple **sin FK**, porque la tabla `orden_produccion` no existía todavía (quedó anotado como pendiente). **En esta fase se aplicó el retrofit**: `ALTER TABLE movimiento_materia_prima ADD CONSTRAINT fk_mmp_orden_produccion FOREIGN KEY (id_orden_produccion) REFERENCES orden_produccion(id_orden_produccion) ON UPDATE CASCADE ON DELETE SET NULL`. El script `fase28_ordenes_produccion.sql` lo aplica de forma idempotente (DO-block que verifica `pg_constraint`). **Deuda cerrada.**

### Decisiones de diseño
- **Consumo al INICIAR, no al crear.** Crear una OP (estado 'planificada') solo calcula el consumo teórico (bom.cantidad_necesaria × unidades) y verifica stock. El descuento real de materia prima ocurre al INICIAR (estado 'en_proceso'), registrando cada consumo en el kardex (`movimiento_materia_prima` tipo 'salida_produccion' con `id_orden_produccion`).
- **Re-verificación de disponibilidad al iniciar.** El stock pudo cambiar entre planificar e iniciar (otra OP pudo consumir la MP), por eso `iniciar` vuelve a verificar antes de consumir.
- **No se puede cancelar una OP en proceso.** Como la MP ya se consumió, solo se cancela desde 'planificada'. Una OP en proceso debe completarse (declarando lo realmente producido).
- **Merma es columna GENERATED** (`COALESCE(cantidad_real, cantidad_teorica) - cantidad_teorica`). En JPA se anotó con `@Generated(event = {INSERT, UPDATE})` para que Hibernate RE-LEA el valor calculado por la BD tras escribir; sin esa anotación, el DTO devolvía la merma stale (0.000) al leerla en la misma transacción del `completar`. **Nota:** las columnas GENERATED de fases anteriores (subtotal, total, saldo_pendiente) NO tienen `@Generated` porque siempre se leen en una petición posterior; aquí fue necesario por leer en la misma transacción.
- **Ajuste de stock al completar con consumo real:** exceso (real>teórico) descuenta stock adicional y registra 'merma'; sobrante (real<teórico) devuelve stock y registra 'ajuste'. Sin consumo real declarado, real=teórico y no se ajusta nada.
- **Alta de producto terminado:** al completar con producidas>0 se sube `inventario.stock_actual` en la bodega destino con `SET LOCAL app.current_user_id` + `movimiento_inventario` 'entrada' (mismo patrón que recepción F22).

### Simplificaciones / alcance limitado de esta fase
- **Sin costeo.** No se calcula el costo de producción (materia prima consumida + mano de obra + overhead) ni el costo unitario del producto terminado. La materia prima no tiene aún `costo_unitario_estimado` (deuda abierta desde F27). El costeo es la **Fase 29**.
- **Sin producción parcial/por lotes ni estaciones de trabajo.** Una OP es un único evento: se inicia (consume todo) y se completa (produce todo). No hay avance parcial, ni sub-lotes, ni ruteo por estaciones/operaciones. `cantidad_producida` puede ser menor a la planificada (mermas de producto), pero no se re-inicia ni se produce en tandas.
- **La MP consumida no se devuelve al cancelar** porque no se permite cancelar en_proceso (justamente para no tener que revertir consumos). Si se necesitara "abortar" una OP en proceso, hoy la vía es completarla con `cantidadProducida=0` (el producto no entra a inventario, pero la MP consumida NO se devuelve salvo lo declarado como sobrante en consumo real).
- **Stock de MP global (sin bodega).** Heredado de F22/F26: el consumo de materia prima descuenta del stock global de `materia_prima`, no de una bodega específica. El producto terminado sí entra a una bodega concreta (bodega destino).
- **Concurrencia optimista no forzada.** Dos OP planificadas podrían pasar la verificación y competir por el mismo stock al iniciar; la re-verificación al iniciar mitiga esto, pero no hay bloqueo pesimista (SELECT ... FOR UPDATE). Para el volumen académico es suficiente.

### Pendiente para próximas fases
- **F29 — Costeo de Producción:** agregar `materia_prima.costo_unitario_estimado`, calcular costo de la OP (suma de consumos reales × costo) y costo unitario del producto fabricado; completar `CostoProduccionEstimadoDTO` de F27.


## Fase 29 — Costeo de Producción (2026-07-23)

Calcula el costo real de fabricar cada orden de producción con **costo promedio ponderado** de la materia prima consumida, más mano de obra e indirectos. Permite comparar fabricar vs comprar.

### DEUDA RESUELTA de F27 — campo de costo en materia_prima
- F27 dejó anotado que `materia_prima` no tenía un campo de costo, por lo que `CostoProduccionEstimadoDTO.costoMateriaPrimaUnitario` retornaba **null**. **Resuelto en esta fase**: se agregó `materia_prima.costo_unitario_promedio NUMERIC(12,4)` (costo promedio ponderado) y `ListaMaterialesService.calcularCostoEstimado()` ya calcula el costo real del BOM, con precio de venta, margen bruto y porcentaje. El DTO se completó con `materiales`, `precioVenta`, `margenBruto`, `margenPorcentaje` y `advertencia`. **Deuda cerrada.**

### BUG CORREGIDO — columnas GENERATED sin `@Generated` (JPA leía valores stale)
- `DetallePedido.subtotal`, `OrdenCompraDetalle.subtotal`, `FacturaCompra.total` y `CuentaPorPagar.saldoPendiente` estaban mapeadas solo con `@Column(insertable=false, updatable=false)`, **sin** `@Generated`. Consecuencia: si en la MISMA transacción se escribe la fila y luego se lee la columna generada, JPA devuelve el valor viejo (o null) en vez del calculado por la BD. No se había manifestado porque esos flujos leen el valor en una petición posterior, pero era un bug latente. **Se agregó `@Generated(event = {INSERT, UPDATE})` a las cuatro.** Mismo fix que se aplicó a `merma` en F28 y ahora a `costo_linea`, `costo_total` y `costo_unitario_producido`.

### BUG DETECTADO (NO corregido — fase anterior) — `fn_proteger_total_pedido` nunca protege
- `fn_proteger_total_pedido` (de las fases base F1–F20) condiciona la excepción a `pg_trigger_depth() = 0`. **Dentro de una función de trigger `pg_trigger_depth()` vale 1, nunca 0**, por lo que esa condición JAMÁS se cumple y el trigger **no protege nada**: hoy se puede hacer `UPDATE pedido SET total = 9999` sin error. Se verificó empíricamente al implementar el trigger de F29 (que originalmente iba a usar el mismo patrón).
  - **No se corrigió** por la restricción de no modificar lógica de fases anteriores. Queda como deuda: cambiar la condición a `pg_trigger_depth() <= 1` o, mejor, adoptar el patrón de F21/F23 (comparar contra el valor real recalculado), que sí funciona.
  - Los triggers de F21 (`fn_proteger_total_orden_compra`) y F23 (`fn_proteger_monto_pagado_cxp`) **NO** tienen este problema porque comparan contra el valor real en vez de usar `pg_trigger_depth()`.

### Decisiones de diseño
- **Costo promedio ponderado** recalculado SOLO al recibir materia prima de una compra (retrofit en `RecepcionMercanciaService`, F22): `nuevo = ((stock_ant × costo_ant) + (cantidad_buena × precio_compra)) / (stock_ant + cantidad_buena)`, con escala 4 y HALF_UP. Si el total posterior es 0, el nuevo costo es el precio de compra. Se actualiza en el mismo `save` donde ya se sumaba el stock; no se tocó la lógica de stock ni de kardex.
- **Snapshot inmutable del costo al consumir.** `orden_produccion_consumo.costo_unitario_snapshot` se captura al INICIAR la OP (leyendo `costo_unitario_promedio` antes de descontar). Esto es contabilidad correcta: el costo histórico de una orden no se altera retroactivamente cuando el promedio cambia por compras posteriores.
- **`costo_linea` es GENERATED** = `COALESCE(cantidad_real, cantidad_teorica) * costo_unitario_snapshot`. Al declarar consumo real al completar, el costo de línea se recalcula solo.
- **`costo_materia_prima` NO es GENERATED** (PostgreSQL no permite subconsultas en columnas generadas). Lo calcula la función SQL dedicada `fn_set_costo_materia_prima_op(id)`, que suma los `costo_linea` y hace el UPDATE. El trigger `trg_proteger_costo_materia_prima_op` **permite el UPDATE solo si el valor coincide con la suma real** de los consumos (patrón de F21/F23), rechazando cualquier otro valor con mensaje en español. Se descartó el enfoque de `pg_trigger_depth() = 0` del enunciado por el bug descrito arriba (no habría protegido nada); esta es la vía documentada y verificada.
- **`costo_total` y `costo_unitario_producido` son GENERATED** en la BD (`MP + MO + IND` y `total / cantidad_producida`, con 0 si no hay producción). Nunca se escriben desde la app.

### Simplificaciones / alcance limitado de esta fase
- **Las órdenes de producción anteriores a esta fase quedan con costo 0.** No hay recálculo retroactivo, y **es lo contablemente correcto**: no existía snapshot de costo al momento en que se consumió esa materia prima, y aplicarle el promedio actual falsearía el histórico.
- **El costo de mano de obra es un monto global por orden, no por hora trabajada.** No hay tarifas por operario, ni registro de horas, ni asignación por estación de trabajo. Igual para los indirectos: un monto global capturado al completar, sin bases de asignación (ni por horas máquina, ni por unidades, ni prorrateo).
- **El análisis "fabricar vs comprar" usa una referencia aproximada.** `costoPromedioCompraCategoria` es el promedio de `producto_proveedor.precio_compra` de los productos con `origen='comprado'` de la MISMA categoría — una referencia de mercado, no el precio de un producto equivalente exacto. Conclusión válida como orientación, no como decisión de compra formal.
- **`costoPromedioFabricacion` es un promedio simple** de `costo_unitario_producido` de las OP completadas, no ponderado por cantidad producida. Con órdenes de tamaños muy distintos, el promedio puede sesgarse.
- **El costo estimado por BOM no incluye mano de obra ni indirectos** (son específicos de cada orden). El DTO lo indica explícitamente en `advertencia`, junto con el aviso si algún material aún no tiene costo (nunca se recibió por compra).
- **Sin variaciones de costo ni análisis de desviaciones** (costo estándar vs real, variación de precio vs variación de cantidad). Sería el siguiente paso natural de un módulo de costos maduro.

### Pendiente para próximas fases
- **F30 — Reportes de Manufactura:** dashboards y reportes consolidados del bloque de manufactura.
- Corregir `fn_proteger_total_pedido` (bug descrito arriba).
- Mano de obra por horas/tarifa y bases de asignación de indirectos.


## Fase 30 — Reportes de Manufactura y Dashboard de Producción (2026-07-23)

Cierra el bloque de Manufactura con la capa analítica: reportes de consumo de materia prima y eficiencia de producción (mermas), más un dashboard dedicado. **Fase de solo lectura**: no crea tablas ni escribe datos de negocio, solo consultas agregadas (SELECT), endpoints de consulta, componentes de visualización y exportables.

### Decisiones de diseño
- **Reutilización total de la infraestructura existente.** Los exportables usan `ExcelService` y `PdfReporteService` de F17 (mismos estilos: encabezado verde, filas alternadas, totales, hoja Resumen, resaltado top-3) y el dashboard usa Chart.js de F16. No se duplicó infraestructura.
- **Consultas nativas agregadas** en `ReporteManufacturaService` en vez de JPQL, porque los reportes requieren `GROUP BY` con subconsultas correlacionadas y `LIMIT` que JPQL no expresa cómodamente. Todas son SELECT puros.
- **Valorización del consumo de materia prima:** se usa el `costo_unitario_snapshot` de `orden_produccion_consumo` cuando existe (costo histórico correcto, F29) y, como respaldo, el `costo_unitario_promedio` actual de la materia prima. Un `LEFT JOIN` cubre movimientos sin OP asociada.
- **El reporte de consumo agrupa 'salida_produccion' y 'merma'**, que es el consumo real total de materia prima por producción. Los 'ajuste' (devolución de sobrante) NO se incluyen para no restar del consumo.
- **Se agregaron `idMateriaPrima` e `idProducto` a `FiltroReporteDTO`** en lugar de reutilizar `idBodega` para otro propósito (que habría sido confuso). Solo añade campos; no rompe los 3 reportes de F17 ni el de F29.
- **Merma promedio del mes** = SUM(mermas positivas) / SUM(cantidad_teorica) × 100 sobre las OP completadas del mes. Semáforo en UI: verde <5%, amarillo 5–15%, rojo >15%.
- **Seguridad:** las reglas de `/api/reportes/manufactura/**` y `GET /api/dashboard/manufactura` se declararon **antes** de las reglas generales de `/api/reportes/**` y `/api/dashboard/**` (que son solo Admin + Supervisor), para que el Encargado de Producción tenga acceso. El orden importa en Spring Security (gana la primera coincidencia).

### Simplificaciones / alcance limitado de esta fase
- **`costoPromedioFabricacion` y la merma promedio son promedios simples**, no ponderados por cantidad producida. Con órdenes de tamaños muy dispares el promedio puede sesgarse (deuda ya anotada en F29 y que aplica también a los KPIs de este dashboard).
- **La distribución de OP por estado es histórica completa**, no filtrada por período. Es intencional (da la foto actual del taller), pero no permite ver la evolución mensual.
- **Sin tendencia temporal.** No hay series de tiempo (producción/costo/merma por mes). El dashboard muestra el mes en curso y el top-3; una gráfica de evolución sería el siguiente paso.
- **El "top 3 productos fabricados" usa `LIMIT 3` en SQL nativo.** Si se quisiera parametrizable (top N) habría que exponerlo como parámetro; hoy está fijo en 3 según el DTO acordado.
- **Reportes limitados a 1000 registros** (`getLimiteEfectivo()` de F17), consistente con los reportes existentes.
- **No hay caché.** Cada carga del dashboard ejecuta ~8 consultas agregadas. Para el volumen académico es irrelevante; con muchas OP conviene cachear el resumen o materializar una vista.

### Hallazgos
- **Discrepancia de numeración de bloque.** El enunciado de esta fase se refiere a "Bloque 10 (Manufactura)", pero en `project.md` el bloque de Manufactura (F27–F30) está documentado como **Bloque 9** desde la F27. Se mantuvo **Bloque 9** por coherencia con lo ya escrito en el steering; no se renumeró para no invalidar las notas de F27/F28/F29.

- **INCIDENTE de limpieza (corregido) — se borró un registro del seed.** El script de limpieza de las pruebas de F30 incluía `DELETE FROM inventario WHERE id_producto = 3 AND id_bodega = 1` asumiendo que ese registro lo había creado la prueba de producción. **Era incorrecto:** el seed sí lo trae (`INSERT INTO inventario ... VALUES (3, 1, 48, 9)`); la prueba solo le sumó stock. Se detectó al verificar el estado final (`inventario` = 264 en vez de 265) y **se restauró** con sus valores originales del seed (stock_actual=48, stock_minimo=9), quedando de nuevo en 265 filas. El INSERT de restauración dejó una fila adicional en `historial_inventario` (por el trigger `trg_historial_inventario`), que documenta la corrección.
  - **Lección aplicable a futuras fases:** antes de borrar filas de tablas que el seed puebla (`inventario`, `producto_proveedor`, etc.), verificar contra `seed_marathon_sports.sql` si el registro preexistía, en vez de inferirlo por el contexto de la prueba. En F28 y F29 la misma limpieza sí fue correcta porque `inventario(1,1)` e `inventario(2,1)` no están en el seed (verificado).

### Pendiente para próximas fases
- **F31 — Consolidación.**
- Series temporales (evolución mensual de producción, costo y merma) y KPIs ponderados por volumen.
- Caché o vista materializada del resumen de manufactura si crece el volumen de órdenes.


## Fase 31 — Consolidación (2026-07-23)

Integra los bloques nuevos (Compras, Devoluciones, Manufactura) al sistema como un todo: seed de demostración permanente, dashboard adaptado por rol y auditoría de navegación (ver `MATRIZ_ROLES.md`).

### Decisiones de diseño
- **Seed demo idempotente.** `fase31_seed_demo_bloques_nuevos.sql` usa `ON CONFLICT (nombre) DO NOTHING` para catálogos y un guard (`IF EXISTS ... orden_compra LIKE 'DEMO F31%' THEN RETURN`) para el bloque transaccional. Verificado ejecutándolo dos veces: la segunda no inserta nada.
- **Recepciones sembradas en SQL (Opción A) sin riesgo de descuadre.** Se replicó la lógica de `RecepcionMercanciaService`, pero cada materia prima parte de `stock 0 / costo 0` y recibe **una sola vez**, de modo que el promedio ponderado se reduce a `((0×0)+(cant×precio))/(0+cant) = precio`: exacto, sin redondeos. Verificado: los 7 materiales recibidos tienen `costo_unitario_promedio` idéntico a su `precio_unitario` de compra.
- **Productos fabricados nuevos, no conversión de existentes.** Se crearon 3 productos de marca propia en lugar de convertir productos del seed base, para no alterar su inventario ni su historial de pedidos.
- **Se respetaron todas las columnas calculadas:** no se escribieron `orden_compra.total`, `factura_compra.total`, `cuenta_por_pagar.monto_pagado/saldo_pendiente`, `merma`, `costo_linea`, `costo_total` ni `costo_unitario_producido`. `orden_produccion.costo_materia_prima` se fijó con `fn_set_costo_materia_prima_op()`. La cascada del trigger F23 marcó sola la factura 1 como `pagada`.
- **Dashboard segmentado por rol** con getters (`verKpisPedidos`, `verKpisVentas`, `verKpisCompras`, `verKpisProduccion`) en vez de condiciones sueltas repetidas, para que la intención quede explícita y sea fácil de auditar.

### Hallazgos corregidos en esta fase
Ocho desalineaciones entre navbar, guards de ruta y backend. El detalle completo está en `MATRIZ_ROLES.md`; en resumen: el dashboard mostraba KPIs comerciales a Compras y Producción; las tarjetas de OC/CxP excluían al Encargado de Compras pese a tener permiso; el costo de producción excluía al Encargado de Producción; los datos solo se cargaban para Administrador; faltaban accesos rápidos de los dos roles nuevos; el navbar no mostraba Materia Prima al Encargado de Compras; y `/picking` no tenía `rolGuard` (cualquier rol entraba y recibía 403).

---

# INVENTARIO DE DEUDA TÉCNICA — estado para la Fase 32

Panorama consolidado de toda la deuda acumulada (F21–F31), separando lo resuelto de lo pendiente.

## 🔴 PENDIENTE — PRIORIDAD ALTA (para F32)

| # | Deuda | Origen | Detalle |
|---|-------|--------|---------|
| 1 | **`fn_proteger_total_pedido` no protege nada** | F29 (detectado) | Usa `pg_trigger_depth() = 0`, condición que **nunca** se cumple dentro de un trigger (allí vale 1). Hoy `UPDATE pedido SET total = 9999` pasa sin error, violando la regla de negocio #1 del proyecto. **Corrección propuesta:** adoptar el patrón de F21/F23/F29 (comparar contra el total real recalculado) o usar `pg_trigger_depth() <= 1`. No se corrigió antes por la restricción de no tocar lógica de fases anteriores. |
| 2 | **Falta `costo_unitario_estimado` real en el costeo estimado** | F27 → resuelto parcialmente en F29 | Resuelto con `costo_unitario_promedio`, pero el estimado por BOM **no incluye mano de obra ni indirectos** (son por orden). Documentado en el campo `advertencia` del DTO. Mejora: costos estándar por producto. |
| 3 | **Rutas frontend con solo `authGuard`** | F1–F20 | `/inventario`, `/clientes`, `/pedidos`, `/comprobantes`, `/empaque`, `/despachos`, `/devoluciones` no tienen `rolGuard`. El backend sí restringe (no es agujero de seguridad), pero la navegación ofrece pantallas que luego fallan. En F31 se corrigió `/picking` como referencia; replicar el patrón. |

## 🟡 PENDIENTE — PRIORIDAD MEDIA

| # | Deuda | Origen | Detalle |
|---|-------|--------|---------|
| 4 | `fase00_ddl_base.sql` no es un baseline F20 "puro" | Setup | Es un snapshot con `producto.origen` y el trigger de F27 ya incluidos. Inofensivo (F27 es idempotente), pero no representa el estado histórico exacto de la F20. |
| 5 | Promedios simples, no ponderados | F29, F30 | `costoPromedioFabricacion` y la merma promedio son promedios aritméticos de órdenes, no ponderados por cantidad producida. Con lotes de tamaños dispares el indicador se sesga. |
| 6 | Recálculo de CxP vencidas bajo demanda | F23 | Se actualizan al listar, no con un job programado (`@Scheduled`). |
| 7 | Sin anulación de pagos ni de recepciones | F22, F23 | Una vez registrados no se pueden revertir desde la aplicación; corrección manual en BD. |
| 8 | Reembolso por devolución a proveedor no acredita la CxP | F25 | `monto_reembolso` se registra pero no genera nota de crédito ni reduce el saldo de ninguna cuenta por pagar. |
| 9 | Sin series temporales en dashboards de manufactura | F30 | Solo mes en curso y top-3; falta evolución mensual de producción, costo y merma. |
| 10 | Concurrencia optimista no forzada en producción | F28 | Dos OP planificadas pueden competir por el mismo stock; mitigado con re-verificación al iniciar, pero sin bloqueo pesimista. |

## 🟢 PENDIENTE — PRIORIDAD BAJA

| # | Deuda | Origen |
|---|-------|--------|
| 11 | Sin número secuencial formal de OC (tipo `OC-000001`) | F21 |
| 12 | `<select>` con hasta 1000 registros en vez de autocomplete remoto | F21, F27 |
| 13 | Consultas con `findAll()` + filtro en memoria (stock bajo, bandeja de items defectuosos) | F25, F26 |
| 14 | Budget de estilos de Angular excedido en varios componentes | F21 |
| 15 | Sin caché del resumen de manufactura (~8 consultas por carga) | F30 |
| 16 | Sin producción parcial / por lotes ni estaciones de trabajo | F28 |
| 17 | Mano de obra como monto global por orden, sin horas ni tarifas | F29 |
| 18 | Validación de `cantidad_devuelta` sin acumulado de devoluciones previas | F24 |

## ✅ RESUELTAS

| Deuda | Origen | Resuelta en |
|-------|--------|-------------|
| FK faltante de `movimiento_materia_prima.id_orden_produccion` | F26 | **F28** (retrofit aplicado) |
| Falta de campo de costo en `materia_prima` | F27 | **F29** (`costo_unitario_promedio` + costeo estimado real) |
| Columnas GENERATED sin `@Generated` (valores stale en JPA) | F9, F21, F23 | **F29** (`DetallePedido.subtotal`, `OrdenCompraDetalle.subtotal`, `FacturaCompra.total`, `CuentaPorPagar.saldoPendiente`) |
| No existía DDL base de las 20 tablas F1–F20 | Setup | **F27/Setup** (`fase00_ddl_base.sql` generado y validado en BD vacía) |
| Módulos nuevos sin datos de demostración | F21–F30 | **F31** (seed demo permanente e idempotente) |
| Dashboard sin integración de los roles nuevos | F21 | **F31** (segmentación por rol + accesos rápidos) |
| Desalineaciones navbar / guard / backend | F1–F30 | **F31** (8 correcciones; ver `MATRIZ_ROLES.md`) |
| Limpieza que borró un registro del seed (`inventario 3,1`) | F30 | **F30** (restaurado a 48/9; lección documentada) |

### Recomendación de orden para F32
1. Corregir `fn_proteger_total_pedido` (#1) — es una regla de negocio crítica hoy sin protección efectiva.
2. Añadir `rolGuard` a las rutas heredadas (#3) — mejora la coherencia de navegación.
3. Verificación integral final: los 3 ciclos de negocio end-to-end con los 6 roles.


## Fase 32 — Cierre de Deuda Técnica y Verificación Integral Final (2026-08-01)

Última fase. A diferencia de todas las anteriores, aquí **sí** se modificó lógica de fases previas — es su propósito. Cada cambio fue quirúrgico y verificado con prueba de que rechaza lo inválido **y** permite lo legítimo.

### BUG CRÍTICO RESUELTO — `fn_proteger_total_pedido` no protegía nada (deuda #1)

**Antes fallaba:** la función condicionaba su excepción a `pg_trigger_depth() = 0`. Dentro de una función de trigger `pg_trigger_depth()` devuelve 1, nunca 0, así que la condición **jamás se cumplía**. El trigger existía, aparecía en `pg_trigger` y no protegía nada. Reproducido empíricamente: `UPDATE pedido SET total = 9999 WHERE id_pedido = 26` pasó sin error, llevando el total de 478.93 a 9999.00 y violando la regla de negocio #1 del proyecto.

**Ahora funciona:** reimplementada con el patrón validado en F21/F23/F29 — comparar el valor entrante contra el valor real recalculado y rechazar si difiere.

**Detalle crítico de la corrección:** el valor de referencia es el **total NETO**, `GREATEST(SUM(detalle_pedido.subtotal) − pedido.descuento, 0)`, no la suma bruta de subtotales. Comparar contra el bruto habría hecho que **todo pedido con descuento fuera rechazado** por el propio trigger de recálculo legítimo, rompiendo el sistema entero. Se detectó al diseñar el fix, antes de aplicarlo.

**Pruebas ejecutadas sobre el pedido #26:**

| Prueba | Resultado esperado | Resultado real |
|---|---|---|
| `UPDATE pedido SET total = 9999` | rechazado | ✅ rechazado con mensaje en español |
| INSERT de un `detalle_pedido` | total recalculado a 578.93 | ✅ 578.93 |
| DELETE de ese detalle | total vuelve a 478.93 | ✅ 478.93 |
| `UPDATE pedido SET descuento = 20` | total neto 458.93 | ✅ 458.93 |

Corrección guardada en `marathon-backend/sql/fase32_fixes.sql` (idempotente, `CREATE OR REPLACE FUNCTION`).

### AUDITORÍA de los demás triggers de protección (deuda #1, alcance ampliado)

Se revisaron **todos** los triggers de protección de la base de datos buscando el mismo defecto:

| Función | Origen | Patrón usado | Estado |
|---|---|---|---|
| `fn_proteger_total_pedido` | F1–F20 | `pg_trigger_depth() = 0` | 🔴 **tenía el bug** → corregido |
| `fn_proteger_total_orden_compra` | F21 | compara contra suma real | ✅ correcto |
| `fn_proteger_monto_pagado_cxp` | F23 | compara contra suma real | ✅ correcto |
| `fn_proteger_costo_materia_prima_op` | F29 | compara contra suma real | ✅ correcto |
| `fn_validar_bom_producto_fabricado` | F27 | valida origen del producto | ✅ correcto |
| `fn_validar_cambio_origen_producto` | F27 | valida existencia de BOM activo | ✅ correcto |
| `fn_validar_op_producto_fabricado` | F28 | valida origen del producto | ✅ correcto |

**Solo uno tenía el defecto.** Verificación final sobre el catálogo: de las 17 funciones de `public`, **ninguna** contiene `pg_trigger_depth` en su definición. Cada protector se probó rechazando un valor falso y aceptando el recálculo legítimo.

### RESUELTO — rutas frontend sin `rolGuard` (deuda #3)

12 rutas heredadas tenían solo `authGuard`, de modo que cualquier rol autenticado podía navegar a pantallas que luego fallaban con 403 del backend. No era un agujero de seguridad (el backend sí restringía) sino una incoherencia de navegación que producía errores inexplicables para el usuario.

Se aplicó `rolGuard` con los roles de `MATRIZ_ROLES.md` a las 12 rutas, usando `/picking` (corregida en F31) como patrón. Además se mejoró el guard: al bloquear redirige a `/dashboard?acceso=denegado` y el dashboard muestra el aviso "No tienes acceso a esta sección". El navbar quedó alineado (solo Dashboard y Mi Perfil visibles a todos los roles). Verificado ruta por ruta contra la matriz; el frontend compila.

**Incidente durante la aplicación:** un primer intento con variables interpoladas en un comando PowerShell inline expandió cadenas vacías y dejó `canActivate: ,` en `app.routes.ts`, rompiendo el build. Se reparó ejecutando el reemplazo desde un archivo `.ps1` en lugar de inline. Lección: para ediciones masivas de código, script en archivo, nunca comando inline con interpolación.

### BUG CRÍTICO ADICIONAL encontrado durante la verificación — `?origen=fabricado` daba error 500

No estaba en el plan de la fase; apareció al ejecutar el ciclo 3.

**Antes fallaba:** `GET /api/productos?origen=fabricado` devolvía **500** con `ERROR: no existe la función lower(bytea)`. PostgreSQL no lograba inferir el tipo de los parámetros que llegaban NULL en el query JPQL unificado `buscarConFiltros` (introducido en F27) y los trataba como `bytea`.

**Impacto real:** habría roto en vivo los desplegables del módulo de Producción durante la demo, porque son precisamente los que filtran por `origen=fabricado`. Es el tipo de bug que no aparece en pruebas parciales porque solo se manifiesta cuando ese filtro llega solo, sin acompañantes.

**Ahora funciona:** se envolvieron los parámetros con `CAST(:param AS string)` en `ProductoRepository.buscarConFiltros`. Verificado: devuelve los 3 productos fabricados, los filtros combinados siguen operando y no hay regresión (los 108 productos se listan igual que antes).

### RESUELTO — promedio ponderado en KPI de fabricación (deuda #5, parcial)

`costoPromedioFabricacion` era un promedio aritmético de `costo_unitario_producido` entre órdenes completadas, sesgado cuando los lotes son de tamaños dispares. Pasó a **promedio ponderado**: `SUM(costo_total) / SUM(cantidad_producida)`. Es el mismo dato que se muestra en el análisis fabricar-vs-comprar, así que el sesgo afectaba una decisión visible en pantalla.

La merma promedio del dashboard de manufactura **ya estaba ponderada** correctamente (`SUM(mermas positivas) / SUM(cantidad_teorica)`), así que la deuda #5 queda cerrada en su parte de costos y no aplicaba en la de mermas.

### VERIFICACIÓN — deuda que el documento reportaba y ya estaba resuelta

`administracion_usuarios_roles_privilegios.sql` figuraba como pendiente por usar nombres de objetos desactualizados. Al revisarlo, **ya usaba los nombres correctos**: la deuda estaba resuelta y solo el registro seguía marcándola. Corregido el registro.

### POSPUESTO con justificación — mano de obra e indirectos en el costo estimado por BOM (deuda #2)

El costo estimado por BOM (`GET /api/productos/{id}/costo-estimado`) calcula correctamente el costo de materia prima con el promedio ponderado real, pero **no incluye mano de obra ni indirectos**, porque hoy esos costos se capturan por orden de producción, no por producto.

**Por qué se pospone:**
- Requiere una **tabla nueva de costos estándar por producto** (tarifa de mano de obra y tasa de indirectos), es decir un cambio de esquema con su script de fase, entidad, servicio, endpoints y UI.
- Es **funcionalidad nueva, no un bug**. El comportamiento actual es correcto y honesto: el DTO ya lo declara explícitamente en su campo `advertencia`.
- El riesgo de introducir un cambio de esquema y de lógica de costeo a pocos días de la entrega no compensa la ganancia.

**Criterio aplicado:** la estabilidad para la demo pesa más que cerrar el 100 % de la deuda. Queda en trabajo futuro con el diseño ya esbozado.

### VERIFICACIÓN INTEGRAL de los 4 ciclos de negocio

Ejecutados end-to-end contra el backend real vía HTTP con los usuarios de cada rol, sobre la BD con datos del seed, usando **datos nuevos de prueba** (el seed no se tocó).

| Ciclo | Evidencia | Resultado |
|---|---|---|
| **1. Order-to-Cash** | Pedido #26 total **235.00** (250 − 15 de descuento, neto correcto), picking 2/2 líneas, empaque HU-F32-001 → estado `enviado`, entregado, comprobante `COMP-2026-000001` con total 235.00 que **cuadra** con el pedido | ✅ |
| **2. Procure-to-Pay** | OC #9 total **800.00** puesto por trigger; separación de funciones respetada (compras crea → admin aprueba, autoaprobación rechazada); recepción #6 → stock MP 100.000 y `costo_unitario_promedio` = **8.0000** exacto, orden a `recibida_completa`; factura #3 total **920.00** (GENERATED = 800 + 120); CxP #3 pagada con `saldo_pendiente` **0.00** y factura marcada `pagada` por cascada del trigger | ✅ |
| **3. Manufactura** | OP #11 sobre producto fabricado con BOM; disponibilidad máxima calculada 365 uds; iniciada con `costo_materia_prima` 60.60 y snapshots capturados; completada 9 de 10 con merma de tela **1.500** → MP 67.35 + MO 90 + IND 30 = **187.35** total, unitario **20.8167**; producto terminado dado de alta en la bodega destino | ✅ |
| **4. Calidad** | RMA #6 sobre el pedido entregado del ciclo 1 → inspección con `idBodega` en la raíz: línea `apto_reventa` subió stock del producto 10 en bodega 1 de **1 → 2** (movimiento #12 tipo `entrada`, historial con `id_usuario=3` puesto por el trigger vía `SET LOCAL`), línea `defectuoso` **no** tocó stock; la bandeja `/items-disponibles` mostró el item; devolución a proveedor #2 creada, `enviada` y `resuelta` con reembolso 100.00; **segundo intento con el mismo item rechazado** por la constraint UNIQUE | ✅ |

**Hallazgos de contrato de API detectados al verificar** (útiles para quien consuma la API): en `PUT /api/devoluciones/{id}/inspeccionar` el campo `idBodega` va en la **raíz** del cuerpo, no dentro de cada item; el detalle de pedido se expone como `productoId`, no `idProducto`; y en `POST /api/devoluciones-proveedor` los items usan `origen` + `idOrigenDetalle` (sin `idProducto`, que el servicio resuelve solo).

**Precaución de limpieza aplicada:** antes de revertir el stock se verificó el origen de `inventario(producto 10, bodega 1)`. Resultó provenir del **seed demo de F31** (movimiento #7, "DEMO F31 - Devolución apto reventa RMA #1"), no de las pruebas de esta fase. Se ajustó solo el incremento causado por la prueba, sin borrar la fila. Es exactamente el error que se cometió en F30 y esta vez se evitó aplicando la lección documentada entonces.

### VERIFICACIÓN de integridad de la base de datos

| Verificación | Resultado |
|---|---|
| Tablas en `public` | **37** ✅ |
| Usuarios demo activos, cada uno con su rol | **6 / 6** ✅ |
| Hash de contraseñas (BCrypt) | 60 caracteres en los 6 ✅ |
| FKs revalidadas fila por fila con `VALIDATE CONSTRAINT` | **70 / 70, 0 huérfanos** ✅ |
| `pedido.total` = neto recalculado | 0 incoherencias ✅ |
| `orden_compra.total` = suma de detalles | 0 incoherencias ✅ |
| `cuenta_por_pagar.monto_pagado` = suma de pagos | 0 incoherencias ✅ |
| `orden_produccion.costo_materia_prima` = suma de `costo_linea` | 0 incoherencias ✅ |
| BOM sobre productos no fabricados | 0 ✅ |
| Órdenes de producción sobre productos no fabricados | 0 ✅ |
| Stock negativo en `inventario` y `materia_prima` | 0 ✅ |
| Funciones PL/pgSQL | **17** ✅ |
| Triggers | **24** ✅ |
| Funciones que aún usan `pg_trigger_depth` | **0** ✅ |
| Seed base intacto | 88 ciudades, 108 productos, 40 clientes, 20 bodegas ✅ |

La validación de integridad referencial no fue un conteo de huérfanos por consulta, sino un `ALTER TABLE ... VALIDATE CONSTRAINT` sobre las 70 FKs dentro de un bloque `DO`, que hace a PostgreSQL revalidar cada fila y aborta si encuentra una referencia rota. Es una comprobación más fuerte que un `LEFT JOIN ... IS NULL` por tabla.

### HALLAZGOS NUEVOS de esta fase (deuda de documentación)

Al verificar contra el catálogo real aparecieron discrepancias en `.kiro/steering/database.md` que venían arrastrándose:

1. **Cuatro tablas base documentadas con columnas que no existen.** Lo real es:
   - `inventario`: `stock_actual`, `stock_minimo`, `fecha_actualizacion` — el doc decía `cantidad` y `updated_at`.
   - `movimiento_inventario`: referencia `id_inventario` (no `id_producto` + `id_bodega`) y tiene además `id_proveedor`, `id_pedido`, `observacion`, `id_inventario_destino`, `created_at`.
   - `historial_inventario`: `stock_anterior`, `stock_nuevo`, `motivo`, `fecha` — el doc decía `cantidad_anterior`, `cantidad_nueva`, `fecha_cambio`, `tipo_operacion`.
   - `comprobante_interno`: ligado a `id_pedido`, con `total` y `fecha_emision` — el doc describía bodegas origen/destino, `tipo` y `observaciones`.
   - `rol` **no tiene** columna `estado`.
2. **`log_accion` (F19b, 82 filas) no estaba documentada.** Era la tabla 37 que faltaba en el inventario del esquema.
3. **Cuatro de los seis roles tienen 0 filas en `rol_permiso`** (solo Administrador con 49 y Encargado de Compras con 5). No rompe nada porque la autorización efectiva es por nombre de rol en `SecurityConfig`, pero la tabla de permisos granulares está incompleta y es inconsistente con lo que el modelo de datos sugiere.

Los puntos 1 y 2 se corrigieron en el steering en esta misma fase. El punto 3 queda en trabajo futuro.

### Cómo quedó el registro de deuda

| # | Deuda | Prioridad | Estado |
|---|---|---|---|
| 1 | `fn_proteger_total_pedido` no protege | 🔴 Alta | ✅ **Resuelta en F32** |
| 2 | Costo estimado por BOM sin mano de obra ni indirectos | 🔴 Alta | ⏸️ **Pospuesta con justificación** |
| 3 | Rutas frontend con solo `authGuard` | 🔴 Alta | ✅ **Resuelta en F32** |
| 4 | `fase00_ddl_base.sql` no es baseline F20 puro | 🟡 Media | ⏸️ Trabajo futuro |
| 5 | Promedios simples, no ponderados | 🟡 Media | ✅ **Resuelta en F32** |
| 6 | CxP vencidas recalculadas bajo demanda | 🟡 Media | ⏸️ Trabajo futuro |
| 7 | Sin anulación de pagos ni recepciones | 🟡 Media | ⏸️ Trabajo futuro |
| 8 | Reembolso a proveedor no acredita la CxP | 🟡 Media | ⏸️ Trabajo futuro |
| 9 | Sin series temporales en dashboards | 🟡 Media | ⏸️ Trabajo futuro |
| 10 | Concurrencia optimista no forzada en producción | 🟡 Media | ⏸️ Trabajo futuro |
| 11–18 | Ver tabla de prioridad baja del inventario | 🟢 Baja | ⏸️ Trabajo futuro |
| — | `?origen=fabricado` daba error 500 (`lower(bytea)`) | 🔴 Crítica (nueva) | ✅ **Resuelta en F32** |
| — | Nombres desactualizados en script de privilegios | 🟡 Media | ✅ Ya estaba resuelta; registro corregido |
| — | Esquema mal documentado de 4 tablas base + `log_accion` | 🟡 Media (nueva) | ✅ **Resuelta en F32** |
| — | 4 de 6 roles sin filas en `rol_permiso` | 🟡 Media (nueva) | ⏸️ Trabajo futuro |

---

## Construcción desde cero — la clase de defecto que una BD preexistente no puede revelar (2026-08-16)

El 16/08/2026 se construyó el entorno **completo** sobre un clúster de PostgreSQL
recién creado con `initdb`, y aparecieron cuatro fallos. Ninguno era nuevo:
llevaban meses en el repositorio. Lo que los hacía invisibles es que **todo el
desarrollo ocurrió sobre una base que ya existía**.

### El patrón: dependencias que solo vivían en la BD

Este es el **segundo** hallazgo del mismo tipo, y por eso deja de ser un
accidente y pasa a ser un patrón que conviene nombrar.

| # | Fecha | Qué faltaba | Cómo se detectó |
|---|---|---|---|
| 1 | 2026-07-23 | **El DDL de las 20 tablas base.** Los scripts `fase21`–`fase27` asumían que existían | Al escribir `SETUP_COMPLETO.md` |
| 2 | 2026-08-16 | **Seis filas de `unidad_medida`.** `fase31` referencia las unidades 4 y 6 *por número*; el seed solo crea tres | Al construir sobre un clúster limpio |

En los dos casos, algo que el sistema necesitaba **existía en la base de
desarrollo pero en ningún archivo versionado**. En el primero fue un esquema
entero; en el segundo, seis filas de un catálogo dadas de alta por la aplicación
durante el desarrollo. La diferencia de tamaño no importa: el efecto es idéntico
—el repositorio no basta para reconstruir el sistema— y el síntoma es igual de
desconcertante, porque falla un script que «siempre había funcionado».

El segundo caso tiene un agravante que conviene entender. La corrección
(`fase31_0_unidades_faltantes.sql`) inserta los ids **explícitamente**, con
`OVERRIDING SYSTEM VALUE`. Si se dejara elegir a la secuencia, la unidad 6 podría
acabar siendo «Litro» en vez de «Metro», y las materias primas quedarían medidas
en unidades absurdas **satisfaciendo todas las claves foráneas**. Es la misma
lección que dejó la F38 con las cinco fechas: *las restricciones garantizan que
los datos sean válidos, no que sean correctos.*

### La otra mitad: código que solo se ejercita en otro entorno

Los otros dos fallos no son dependencias ocultas sino rutas de código que un solo
entorno nunca recorre:

- **`gestionar_clave.ps1 -Accion Ejecutar` tenía `-h localhost -p 5432` fijo.** El
  parámetro `-Base` prometía algo que no cumplía: pedir otra base en otro clúster
  ejecutaba igualmente contra el servidor de producción. Con un único servidor
  nunca se nota, porque el valor fijo *siempre* coincide con el correcto.
- **`fase35` comprobaba `summarize_wal` tras un `pg_sleep(2)` fijo**, y
  `pg_reload_conf()` es asíncrono. Sobre un clúster recién creado dos segundos no
  bastan y abortaba diciendo que el parámetro seguía en `off` cuando se activaba
  un instante después. Un fallo que depende del reloj no se manifiesta hasta que
  la máquina va más lenta.

### Coste de no haberlo hecho antes

Los cuatro se arreglaron en una sesión. Pero cualquiera que hubiera seguido
`SETUP_COMPLETO.md` en una máquina limpia —un compañero de grupo, un evaluador,
el propio autor tras formatear— se habría estrellado en el paso 12 y otra vez en
el 14, sin ninguna pista de por qué una guía «validada» no funciona.

**La única forma de detectar esta clase de defecto es construir desde cero,
periódicamente y sobre un entorno limpio de verdad.** No basta con borrar la base
y recrearla en el mismo servidor: `fase34_seguridad_roles.sql` hace `DROP ROLE` de
los seis roles, que son objetos del **clúster**, y lleva `mod_venta_inve` escrito
a mano en un `REVOKE ... ON DATABASE`. Hace falta un clúster aparte, y de eso se
encarga un script:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\migracion\verificar_construccion_limpia.ps1
```

Levanta el clúster temporal, construye las dos etapas, arranca y para el backend,
corre los cuatro arneses y lo destruye todo. **93 segundos.** Al terminar: 38
tablas, ~1.011.000 filas de negocio y los cuatro arneses en verde (**61/61**
privilegios, **29/29** auditoría, **51/51** cifrado, 0 violaciones en 238
comprobaciones).

Detalle por síntoma en `SETUP_COMPLETO.md` §*Fallos que solo aparecen en un equipo
limpio*, y el procedimiento completo en `GUIA_REPLICACION.md` §12.

---

# TRABAJO FUTURO

Lo que se pospuso conscientemente, con el motivo. Un proyecto honesto documenta sus límites.

## Alta prioridad si el sistema siguiera evolucionando

**Reconstruir desde cero cada vez que se cierre una fase.** Es la única prueba
que detecta dependencias que solo viven en la base de desarrollo, y ya ha
encontrado dos (el DDL base y las unidades de medida) más dos fallos de entorno.
Hacerlo al cerrar cada fase lo mantiene en «un fallo como mucho» en vez de
acumular cuatro.

**Ya está automatizado:** `scripts\migracion\verificar_construccion_limpia.ps1`
levanta un clúster temporal con `initdb`, ejecuta las dos etapas, corre los
cuatro arneses y lo destruye. **93 segundos**, un solo comando, sin intervención.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\migracion\verificar_construccion_limpia.ps1
```

El paso 12 —arrancar el backend para que el `DataInitializer` cree los roles de
aplicación— parecía el obstáculo, porque exige un proceso Spring vivo y pararlo
en el momento justo. Se resuelve arrancando Maven en segundo plano, **sondeando
la tabla `rol`** hasta que se puebla y matando el árbol de procesos con
`taskkill /T`. La clave es esperar al **efecto observable** y no a un tiempo
fijo: la compilación tarda lo que tarde según la máquina y según si el
repositorio local de Maven está caliente. (`/T` es necesario porque `mvn.cmd`
lanza un `java` hijo; matar solo al padre deja el puerto y una conexión a la base
abiertos, y el `pg_ctl stop` del final se queda esperando.)

**Costos estándar por producto.** Tabla nueva con tarifa de mano de obra y tasa de indirectos por producto, para que el costo estimado por BOM sea comparable con el costo real de una orden. Hoy el estimado solo cubre materia prima y lo declara en el campo `advertencia`. Pospuesto por ser cambio de esquema + funcionalidad nueva a pocos días de la entrega.

**Completar `rol_permiso` para los seis roles.** La autorización efectiva es por nombre de rol en `SecurityConfig`, así que el sistema funciona, pero cuatro roles tienen cero permisos granulares registrados. Si se quisiera autorización por permiso (`modulo:accion`) en vez de por rol, habría que poblar la tabla y cambiar `SecurityConfig` para consultarla. Es un refactor de seguridad: exactamente lo que no conviene tocar antes de una entrega.

**Nota de crédito automática al resolver una devolución a proveedor con reembolso.** Hoy `monto_reembolso` se registra pero no abona ninguna cuenta por pagar. Cerrar ese lazo conectaría el ciclo de calidad con el contable.

## Media prioridad

- **Job programado (`@Scheduled`)** para marcar cuentas por pagar vencidas, en vez del recálculo bajo demanda al listar.
- **Anulación / reversión** de pagos y de recepciones de mercancía. Hoy son irreversibles desde la aplicación porque revertir implica deshacer stock, `cantidad_recibida` acumulada y costo promedio ponderado; hacerlo bien requiere un modelo de asientos de reversión, no un DELETE.
- **Series temporales** en los dashboards de manufactura (evolución mensual de producción, costo y merma). Hoy se muestra el mes en curso y el top-3.
- **Bloqueo pesimista** (`SELECT ... FOR UPDATE`) en el consumo de materia prima. Dos órdenes planificadas pueden competir por el mismo stock; hoy se mitiga re-verificando al iniciar, suficiente para el volumen actual.
- **Baseline F20 puro.** `fase00_ddl_base.sql` es un snapshot que ya incluye `producto.origen` y el trigger de F27. Es inofensivo porque F27 es idempotente, pero no representa el estado histórico exacto de la F20.
- **Validar `cantidad_devuelta` contra el acumulado** de devoluciones previas de la misma línea de pedido, no solo contra la cantidad original.

## Baja prioridad

- Número secuencial formal de orden de compra (`OC-000001`), como ya tienen pedidos y comprobantes.
- Reemplazar los `<select>` de hasta 1000 registros por autocomplete con búsqueda remota.
- Sustituir los `findAll()` + filtro en memoria por consultas nativas (stock bajo de materia prima, bandeja de items defectuosos).
- Subir el budget de estilos de Angular o extraer estilos a `styles.scss` (varios componentes lo exceden en build de producción).
- Caché o vista materializada del resumen de manufactura (~8 consultas agregadas por carga del dashboard).
- Producción parcial / por lotes y estaciones de trabajo con ruteo de operaciones.
- Mano de obra por horas y tarifas en vez de monto global por orden.
- Análisis de desviaciones de costo (estándar vs real, variación de precio vs variación de cantidad).

## Riesgo conocido que se asume

**El asistente IA ejecuta SQL generado por un modelo de lenguaje.** La mitigación es la validación SELECT-only (rechaza INSERT/UPDATE/DELETE/DROP/TRUNCATE/ALTER/CREATE) más el límite de 500 filas. Es una mitigación razonable, no una garantía formal: un endurecimiento real pasaría por ejecutar con un rol de PostgreSQL de solo lectura sobre vistas específicas, en lugar de filtrar la cadena SQL. Queda documentado como riesgo aceptado y no como problema resuelto.

---

## F93 — Selectores que se traen catálogos enteros al navegador

**Cerrado en `/pedidos/nuevo`. Sigue abierto en otras pantallas**, y conviene
saber dónde antes de que vuelva a morder.

### El patrón

Una pantalla precarga un catálogo completo y el buscador filtra en el navegador.
Con miles de filas es razonable; con las tablas al millón y medio de la F91 deja
de serlo de dos formas distintas:

1. **Se cuelga**, si el endpoint no tiene tope. Era el caso de
   `/clientes/activos`: **299 MB y 1.439.823 filas en una respuesta**.
2. **Miente**, si el endpoint tiene tope. Un `size=1000` sobre 1.500.000
   productos deja el 99,93 % del catálogo fuera del desplegable, y no hay ningún
   aviso: la lista simplemente no los contiene.

El segundo es peor, porque no se nota. Nadie reporta «no me deja elegir este
producto» — se asume que no existe.

### Lo que queda con `size=1000` sobre tablas grandes

| Pantalla | Carga | Tabla | Riesgo |
|---|---|---|---|
| `inventario` | `productos?size=1000` | 1,5 M | sólo se pueden filtrar 1.000 |
| `auditoria` (historial) | `productos?size=1000` | 1,5 M | ídem, es un filtro |
| `compras/orden-compra-nueva` | `productos?size=1000` por proveedor | 1,5 M | **no se puede comprar fuera de esas 1.000** |
| `compras/orden-compra-nueva` | `proveedores?size=1000` | 1,5 M | ídem |
| `compras/ordenes-compra` | `proveedores?size=1000` | 1,5 M | filtro |
| `productos` | `proveedores?size=1000` | 1,5 M | al asignar proveedor |
| `produccion/*` | `productos?origen=fabricado&size=1000` | acotado | bajo |

Los catálogos pequeños (`ciudades`, `categorias`, `unidades-medida`, `bodegas`,
`transportistas`) no entran aquí: son decenas de filas y `size=1000` los cubre
de sobra.

### Cómo se arregla

> Al hacerlo, dos cosas que ya costaron una corrección en la F93 y volverán a
> aparecer: buscar por **palabras** y no por la frase entera (ver abajo), y el
> `LIMIT` dentro con el `ORDER BY` fuera.


La pieza ya está hecha: `app-searchable-select` acepta `[remoto]="true"` y
`(buscar)`. Filtra la base, aplica un respiro de 250 ms y pinta lo que llegue.
Para cada pantalla de la tabla hace falta:

1. un endpoint de búsqueda con tope (como `/api/clientes/buscar`), o reutilizar
   el filtro `nombre` que ya tienen `/productos` y `/proveedores`;
2. cambiar la precarga por una llamada al `(buscar)`;
3. **índices de trigramas** sobre la columna que se busca — sin ellos la
   búsqueda por `%texto%` es un barrido secuencial. Los de `cliente` y
   `producto` los crea `fase93_buscadores_del_pedido.sql`; `proveedor` y
   `materia_prima` no los tienen todavía.

**Prioridad:** `orden-compra-nueva` es la siguiente, y es la más grave de las que
quedan — ahí el tope no limita una búsqueda, limita **qué se puede comprar**.

### La trampa del ORDER BY, que hay que recordar al hacer las demás

El índice de trigramas se pierde si la consulta lleva `ORDER BY` sobre otra
columna con `LIMIT`: el planificador prefiere recorrer el índice ordenado y
filtrar fila a fila. Medido sobre `cliente` con `%mar%`:

| Consulta | Tiempo |
|---|--:|
| `WHERE ... ORDER BY apellido LIMIT 20` | 3.746 ms |
| `SELECT * FROM (WHERE ... LIMIT 20) ORDER BY apellido` | **23 ms** |

Ordenar las veinte que salen, no el millón y medio del que salen.

### Buscar por palabras, no por la frase

Un `ILIKE '%lo que se escribio%'` exige que las palabras estén **juntas, en ese
orden y en la misma columna**. Eso hace que la búsqueda falle en los dos casos
más normales:

| Se escribe | Qué pasaba | Por qué |
|---|---|---|
| `maria cedeno` | 0 resultados | el nombre está en `nombre` y el apellido en `apellido`; ninguna columna contiene la frase |
| `force air` | 0 resultados | el producto se llama «… AIR FORCE …», con las palabras al revés |

La forma correcta es partir lo escrito en palabras y exigir que **todas**
aparezcan, cada una en cualquiera de las columnas buscables. Así `maria cedeno`,
`cedeno maria` y `ced mar` encuentran a la misma persona.

Está implementado en `ClienteService.buscarParaSelector` y en
`ProductoService.buscarParaSelector`. Cualquier buscador nuevo debería copiarlo
de ahí en vez de volver a escribir un `LIKE` de una sola pieza.
