# Sistema de Gestión de Pedidos — Marathon Sports

## Misión
Plataforma web interna para gestionar el ciclo completo de pedidos e-commerce: recepción, picking, empaque y despacho.

## Objetivos
- Automatizar la gestión de pedidos desde la recepción hasta el despacho
- Controlar inventario en tiempo real con trazabilidad completa
- Proveer dashboards operativos por rol
- Garantizar integridad de datos con validaciones en BD y aplicación

## Roles del Sistema
1. **Administrador** — Acceso total: usuarios, productos, inventario, reportes, configuración
2. **Supervisor E-Commerce** — Gestión de pedidos, asignación de picking, reportes operativos
3. **Operador de Bodega** — Inventario, movimientos de stock, comprobantes internos
4. **Operador de Pedidos** — Picking, empaque, actualización de estados de pedido
5. **Encargado de Compras** — Gestiona órdenes de compra, recepciones, facturas y cuentas por pagar (F21+)
6. **Encargado de Producción** — Gestiona materia prima, BOM y órdenes de producción (F26-28; en F21 solo catálogo de materia prima)

## Fases del Proyecto

### Bloque 1: Infraestructura
| Fase | Nombre | Estado |
|------|--------|--------|
| 1 | Infraestructura Base (Spring Boot + Angular + Docker) | ✅ Completada |
| 2 | Autenticación JWT + Roles | ✅ Completada |
| 3 | CRUD Maestros (Ciudad, Categoría, Unidad Medida, Proveedor) | ✅ Completada |

### Bloque 2: Núcleo de Negocio
| Fase | Nombre | Estado |
|------|--------|--------|
| 4 | Gestión de Usuarios + Roles + Permisos | ✅ Completada |
| 5 | Gestión de Bodegas | ✅ Completada |
| 6 | Gestión de Productos + Proveedores | ✅ Completada |
| 7 | Gestión de Inventario + Movimientos | ✅ Completada |
| 8 | Gestión de Clientes | ✅ Completada |

### Bloque 3: Pedidos y Operaciones
| Fase | Nombre | Estado |
|------|--------|--------|
| 9 | Crear Pedido + Detalle | ✅ Completada |
| 10 | Listado y Filtrado de Pedidos | ✅ Completada |
| 11 | Flujo de Estados de Pedido | ✅ Completada |
| 12 | Comprobantes Internos | ✅ Completada |
| 12.1 | Pedidos Especiales (personalizado/regalo/corporativo) | ✅ Completada |
| 13 | Historial de Inventario | ⏳ Pendiente |
| 13.1 | Comprobantes Internos + PDF descargable | ✅ Completada |

### Bloque 4: Operaciones Avanzadas
| Fase | Nombre | Estado |
|------|--------|--------|
| 14 | Módulo de Picking | ✅ Completada |
| 15 | Módulo de Empaque y Despacho | ✅ Completada |
| 16 | Dashboard Operativo | ✅ Completada |
| 17 | Reportes y Exportación | ✅ Completada |

### Bloque 5: Calidad y Entrega
| Fase | Nombre | Estado |
|------|--------|--------|
| 18 | Asistente IA (consultas en lenguaje natural) | ✅ Completada |
| 18b | Testing E2E + Validaciones | ⏳ Pendiente |
| 19 | Optimización + Documentación API | ⏳ Pendiente |
| 19b | Auditoría y Logs | ✅ Completada |
| 20 | Cierre del Proyecto (Verificación Integral + Demo) | ✅ Completada |

> **Nota F20 (2026-06-24):** Seed data cargado — 88 ciudades, 105 productos, 40 clientes, 25 pedidos, bodegas, proveedores, categorías, usuarios y roles. Verificación integral completada. Usuarios demo creados via DataInitializer. README.md y DEMO_CHECKLIST.md generados. **PROYECTO BASE COMPLETO (F1-F20 ✅ completadas).**

### Bloque 8: Compras (Procure-to-Pay)
| Fase | Nombre | Estado |
|------|--------|--------|
| 21 | Órdenes de Compra (inicio ciclo Procure-to-Pay) | ✅ Completada |
| 22 | Recepción de Mercancía | ✅ Completada |
| 23 | Factura de Compra y Cuentas por Pagar | ✅ Completada |
| 24 | Devolución de Cliente (RMA) | ✅ Completada |
| 25 | Devolución a Proveedor | ✅ Completada |
| 26 | Materia Prima: Kardex de Movimientos | ✅ Completada |

### Bloque 9: Manufactura
| Fase | Nombre | Estado |
|------|--------|--------|
| 27 | Diferenciación de Origen del Producto + Lista de Materiales (BOM) | ✅ Completada |
| 28 | Órdenes de Producción (consume el BOM para fabricar) | ✅ Completada |
| 29 | Costeo de Producción | ⏳ Pendiente |

> **Nota F21 (2026-07-22):** Inicio del BLOQUE 8 — Compras. Primer módulo del ciclo Procure-to-Pay que se suma a las 20 fases base ya completadas. **Completada:** tablas `materia_prima`, `orden_compra`, `orden_compra_detalle` (con triggers de total y protección); roles Encargado de Compras y Encargado de Producción; permisos módulo `compras`; usuarios demo `compras@marathon.com` y `produccion@marathon.com`; backend (entidades, DTOs, repos, servicios, controladores, seguridad) y frontend (módulos `compras` y `materia-prima`, navbar, card de dashboard). **Fase siguiente: F22 — Recepción de mercancía.**

> **Nota F22 (2026-07-22):** Recepción de mercancía. Tablas `recepcion_mercancia` y `recepcion_mercancia_detalle`; columnas `stock_actual`/`stock_minimo` (NUMERIC) agregadas a `materia_prima`. Al recibir, sube stock de producto (por bodega, vía `inventario` + `movimiento_inventario` con `SET LOCAL app.current_user_id`) o el stock global de materia prima. Soporta recepciones parciales múltiples: `orden_compra_detalle.cantidad_recibida` se ACUMULA. El estado de la orden pasa automáticamente a `recibida_parcial` / `recibida_completa`. Endpoints `POST /api/recepciones` y `GET /api/recepciones/orden/{id}`. **Fase siguiente: F23 — Factura de Compra y Cuentas por Pagar.**
>
> **Nota F23 (2026-07-23):** Factura de Compra y Cuentas por Pagar. Tablas `factura_compra` (total GENERATED = subtotal + impuesto), `cuenta_por_pagar` (saldo_pendiente GENERATED = monto_total - monto_pagado; monto_pagado calculado por trigger), `pago_proveedor`. Triggers `fn_recalcular_monto_pagado_cxp` (statement-level con REFERENCING) y `fn_proteger_monto_pagado_cxp` (protección UPDATE manual). Cascada lógica: al pagar completamente, la cuenta pasa a `pagada` y la factura también. Validación en servicio: monto ≤ saldo_pendiente antes de insertar. Endpoints: `/api/facturas-compra`, `/api/cuentas-por-pagar`, `/api/pagos-proveedor`. Frontend: FacturaCompraNuevaComponent, CuentasPorPagarComponent, CuentaPorPagarDetalleComponent. Navbar: "Cuentas por Pagar" visible para Admin, Enc. Compras y Supervisor. **Fase siguiente: F24 — Devolución de Cliente (RMA).**
>
> **Nota F24 (2026-07-23):** Devolución de Cliente (RMA). Tablas `solicitud_devolucion`, `solicitud_devolucion_detalle`, `reembolso_cliente`. Solo pedidos 'entregado'. Flujo: solicitada → en_inspeccion → completada/rechazada. Inspección por línea: apto_reventa (sube stock), defectuoso (registrado para F25), rechazado (no toca nada). Reembolso informativo post-completada. Endpoints: `/api/devoluciones`. Frontend: DevolucionesListaComponent, DevolucionDetalleComponent, SolicitudDevolucionNuevaComponent. **Fase siguiente: F25 — Devolución a Proveedor.**
>
> **Nota F25 (2026-07-23):** Devolución a Proveedor. Tablas `devolucion_proveedor`, `devolucion_proveedor_detalle` (asociación polimórfica exclusiva: rma_cliente | recepcion_compra). Consume items defectuosos de F24 (solicitud_devolucion_detalle.resultado_inspeccion='defectuoso') y F22 (recepcion_mercancia_detalle.cantidad_defectuosa>0). UNIQUE constraints impiden doble uso de un item. Bandeja `/items-disponibles` lista ambos orígenes. Flujo: pendiente → enviada → resuelta/rechazada. Resolución con tipo (reembolso/reposición) y monto. **Ciclo completo de calidad cerrado end-to-end: cliente devuelve → inspección → devolución a proveedor → resolución.** Fase siguiente: F26 — Materia Prima: Inventario y Kardex.
>
> **Nota F28 (2026-07-23):** Órdenes de Producción — corazón de Manufactura. Fabricar un producto 'fabricado' consumiendo materia prima según su BOM, registrando mermas y dando de alta el producto terminado en inventario. Tablas `orden_produccion` y `orden_produccion_consumo` (merma GENERATED = COALESCE(real,teorica) - teorica; en JPA con `@Generated(INSERT,UPDATE)` para re-leer el valor calculado). Ciclo: **planificada** (calcula consumo teórico según BOM y verifica stock, sin consumir) → **en_proceso** (al iniciar RE-VERIFICA disponibilidad y consume MP del stock + kardex 'salida_produccion' con `id_orden_produccion`) → **completada** (declara producidas ≤ planificadas; si hay consumo real: exceso descuenta stock y registra 'merma', sobrante devuelve stock y registra 'ajuste'; da de alta el producto terminado en inventario con `SET LOCAL app.current_user_id` + movimiento_inventario 'entrada'). Cancelar solo desde 'planificada'. Retrofit aplicado de la FK `fk_mmp_orden_produccion` (pendiente desde F26). Trigger `trg_validar_op_producto_fabricado` (solo productos fabricados). Endpoints `/api/ordenes-produccion` (+ `/verificar-disponibilidad`, `/{id}/iniciar|completar|cancelar`). Frontend módulo `produccion` (lista, nueva con panel de disponibilidad en vivo, detalle con acciones por estado). Navbar sección "Manufactura", card dashboard "OP en proceso". **Ciclo probado end-to-end: crear → iniciar (MP baja + kardex) → completar (producto sube en inventario + mermas calculadas).** **Fase siguiente: F29 — Costeo de Producción.**

> **Nota F27 (2026-07-23):** Diferenciación de Origen del Producto + Lista de Materiales (BOM). Inicio del BLOQUE 9 — Manufactura. Se agrega `producto.origen` ('comprado'|'fabricado', DEFAULT 'comprado' — no rompe los 105 productos seed) con CHECK `chk_producto_origen`. Nueva tabla `lista_materiales` (receta: por producto fabricado, qué materias primas y cantidad para producir 1 unidad; UNIQUE por producto+materia). Dos triggers de integridad en BD (defensa en profundidad): `fn_validar_bom_producto_fabricado`/`trg_validar_bom_producto_fabricado` impide BOM sobre producto no fabricado; `fn_validar_cambio_origen_producto`/`trg_validar_cambio_origen_producto` impide cambiar a 'comprado' un producto con BOM activo. Backend: entidad `ListaMateriales`, DTOs (`dto/bom`), `ListaMaterialesRepository`, `ListaMaterialesService` (definirBom hace upsert por el UNIQUE), `BomController` (`GET`/`PUT /api/productos/{id}/bom`, `PUT /api/productos/{id}/origen`), filtro `origen` en listado de productos, `tieneBom` en ProductoResponseDTO. Dashboard: KPI "Productos fabricados" (Admin/Enc. Producción). Frontend: dropdown Origen + sección BOM en modal de Productos, columna y filtro Origen. **Nota de entorno:** la BD estaba en estado F20; se aplicaron los scripts idempotentes fase21–fase26 antes del fase27 para alinear la BD con el estado documentado. **Fase siguiente: F28 — Órdenes de Producción** (consume el BOM definido aquí para fabricar productos reales).
>
> **Decisión de diseño — asociación polimórfica exclusiva:** `orden_compra_detalle` usa un patrón de asociación polimórfica exclusiva: cada línea de una orden de compra es O un producto (para reventa, ej. comprar zapatos Nike) O una materia prima (para fabricar, ej. tela), nunca ambos ni ninguno. Esto se garantiza con un CHECK constraint (`chk_oc_detalle_item_exclusivo`), NO con lógica de aplicación. Por eso en la F21 se crea también un catálogo mínimo de `materia_prima` (solo catálogo, sin inventario ni kardex todavía — eso llega en la Fase 26 de Manufactura). Decisión deliberada para no tener que alterar `orden_compra_detalle` más adelante.

## Reglas de Negocio Críticas

1. **pedido.total es calculado por trigger** — NUNCA escribir este campo desde la aplicación. El trigger `trg_actualizar_total_pedido` calcula el total automáticamente al insertar/actualizar/eliminar detalles.

2. **detalle_pedido.subtotal es GENERATED** — NUNCA insertar ni actualizar este campo. Es una columna generada: `cantidad * precio_unitario`.

3. **usuario.password llega hasheado (min 60 chars)** — La base de datos NO hashea contraseñas. La aplicación DEBE enviar el password ya hasheado con BCrypt antes del INSERT/UPDATE.

4. **Para movimientos de stock** — Antes de hacer UPDATE a inventario, ejecutar: `SET app.current_user_id = '<id_usuario>'` para que el trigger de historial registre quién hizo el cambio.

5. **orden_compra.total es calculado por trigger** (F21) — NUNCA escribir este campo desde la aplicación (`@Column(insertable=false, updatable=false)`). El trigger `fn_recalcular_total_orden_compra_stmt()` lo recalcula al insertar/actualizar/eliminar detalles. Un trigger de protección impide el UPDATE manual.

6. **orden_compra_detalle.subtotal es GENERATED** (F21) — NUNCA insertar ni actualizar. Columna generada: `cantidad * precio_unitario`.

7. **orden_compra_detalle — asociación polimórfica exclusiva** (F21) — Cada línea es O `id_producto` O `id_materia_prima`, nunca ambos ni ninguno, según `tipo_item`. Garantizado por CHECK `chk_oc_detalle_item_exclusivo`.

8. **Separación de funciones en Órdenes de Compra** (F21) — Quien crea/envía a aprobación una orden NO puede aprobarla; solo el Administrador aprueba o rechaza.

9. **orden_compra_detalle.cantidad_recibida se ACUMULA** (F22) — Cada recepción suma (`+=`), nunca sobreescribe. Una orden admite varias entregas parciales. El estado `recibida_parcial`/`recibida_completa` se calcula automáticamente tras cada recepción (UPDATE directo, sin validación de roles).

10. **Entrada de stock por recepción** (F22) — Producto: `inventario.stock_actual` por bodega + `movimiento_inventario` tipo 'entrada' (requiere `SET LOCAL app.current_user_id` antes del UPDATE). Materia prima: `materia_prima.stock_actual` global (sin bodega, NUMERIC). Solo entra la `cantidad_buena = recibida - defectuosa`.

11. **factura_compra.total es GENERATED** (F23) — `subtotal + impuesto`. NUNCA insertar ni actualizar. `@Column(insertable=false, updatable=false)`.

12. **cuenta_por_pagar.saldo_pendiente es GENERATED** (F23) — `monto_total - monto_pagado`. NUNCA insertar ni actualizar.

13. **cuenta_por_pagar.monto_pagado solo lo modifica el trigger** (F23) — `fn_recalcular_monto_pagado_cxp` lo recalcula tras INSERT/UPDATE/DELETE en `pago_proveedor`. Protegido contra UPDATE manual por `fn_proteger_monto_pagado_cxp`. En JPA: `@Column(insertable=false, updatable=false)`.

14. **Un pago nunca puede exceder el saldo pendiente** (F23) — Validar en servicio ANTES de insertar (`dto.monto ≤ cuenta.saldoPendiente`). La BD garantiza `monto_pagado ≤ monto_total` via CHECK, pero el servicio da mensaje claro.

15. **Factura requiere al menos una recepción** (F23) — No se puede registrar factura sobre una orden que no tenga ninguna recepción. Validado en FacturaCompraService.

16. **Solo pedidos 'entregado' admiten devolución** (F24) — No se puede solicitar devolución de un pedido en cualquier otro estado. Validado en SolicitudDevolucionService.

17. **cantidad_devuelta no puede exceder cantidad comprada** (F24) — Cada línea de devolución valida que `cantidadDevuelta ≤ detalle_pedido.cantidad`.

18. **Inspección apto_reventa sube stock** (F24) — Usa SET LOCAL + UPDATE inventario + movimiento_inventario entrada. Defectuoso NO toca stock (queda para F25). Rechazado no hace nada.

19. **Solo productos con origen='fabricado' pueden tener BOM** (F27) — Garantizado por el trigger `trg_validar_bom_producto_fabricado` (BEFORE INSERT/UPDATE en `lista_materiales`). Un producto 'comprado' JAMÁS puede tener líneas de lista de materiales. El servicio también valida y traduce el error a mensaje en español.

20. **No cambiar un producto a 'comprado' si tiene BOM activo** (F27) — El trigger `trg_validar_cambio_origen_producto` (BEFORE UPDATE OF origen en `producto`) lo impide. Primero se debe eliminar/desactivar el BOM. `producto.origen` es NUEVO (DEFAULT 'comprado', CHECK comprado/fabricado).

21. **definirBom reemplaza el BOM completo con upsert** (F27) — Por el UNIQUE `(id_producto, id_materia_prima)`, `ListaMaterialesService.definirBom` desactiva las líneas actuales y reactiva-en-sitio las materias primas que reaparecen (nueva cantidad), en vez de insertar duplicados. El BOM define la receta; NO consume stock (eso es F28).

22. **Solo productos 'fabricado' con BOM activo pueden producirse** (F28) — Trigger `trg_validar_op_producto_fabricado` + validación en servicio. `verificarDisponibilidad` exige BOM activo.

23. **El consumo de materia prima ocurre al INICIAR, no al crear** (F28) — Crear una OP solo calcula el consumo teórico y verifica stock. Al iniciar se descuenta el stock (kardex 'salida_produccion'). Por eso una OP en_proceso NO se puede cancelar (la MP ya se consumió); hay que completarla.

24. **Re-verificar disponibilidad al iniciar** (F28) — El stock pudo cambiar entre planificar e iniciar (otra OP pudo consumir MP), así que `iniciar` vuelve a verificar antes de consumir.

25. **cantidad_producida ≤ cantidad_planificada** (F28) — No se puede producir más de lo planificado sin más materia prima.

26. **Merma = real − teórico** (F28) — `orden_produccion_consumo.merma` es GENERATED. Al completar con consumo real: exceso (real>teórico) descuenta stock adicional y registra 'merma'; sobrante (real<teórico) devuelve stock y registra 'ajuste'. Sin consumo real declarado, real=teórico (merma 0).

27. **Alta de producto terminado por producción** (F28) — Al completar con producidas>0 se sube `inventario.stock_actual` en la bodega destino, con `SET LOCAL app.current_user_id` antes del UPDATE + movimiento_inventario 'entrada'.

## Notas de Seguridad — Fase 18 (Asistente IA)
- La API key de Anthropic va en `application-local.properties` (gitignored), NUNCA en `application.properties` ni en el repo.
- El IAService solo ejecuta queries SELECT (valida contra INSERT/UPDATE/DELETE/DROP/TRUNCATE/ALTER/CREATE).
- Límite de 500 resultados por consulta.
- Riesgo conocido: se ejecuta SQL generado por IA. La validación SELECT-only es la principal mitigación.
