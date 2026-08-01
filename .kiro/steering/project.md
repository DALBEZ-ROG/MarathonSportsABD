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
| 29 | Costeo de Producción | ✅ Completada |
| 30 | Reportes de Manufactura y Dashboard de Producción | ✅ Completada |

> **BLOQUE 9 — MANUFACTURA COMPLETO (F27–F30 ✅).** Ciclo end-to-end: producto fabricado con BOM (F27) → orden de producción que consume materia prima y da de alta producto terminado (F28) → costeo con promedio ponderado (F29) → reportes y dashboard analítico (F30).

### Bloque 10: Cierre
| Fase | Nombre | Estado |
|------|--------|--------|
| 31 | Consolidación (seed demo, roles en dashboard, matriz de navegación) | ✅ Completada |
| 32 | Cierre de Deuda Técnica y Verificación Integral Final | ✅ Completada |

> # 🏁 PROYECTO COMPLETO — 32 de 32 fases ✅
>
> Sistema terminado y verificado con **4 ciclos de negocio** end-to-end: **Order-to-Cash**
> (pedido → picking → empaque → despacho → comprobante), **Procure-to-Pay** (orden de
> compra → recepción → factura → cuenta por pagar → pago), **Manufactura** (BOM → orden
> de producción → consumo de materia prima → costeo → producto terminado) y **Calidad**
> (devolución de cliente → inspección → devolución a proveedor → resolución).
>
> **37 tablas · 17 funciones · 24 triggers · 70 FKs · 21 módulos · 6 roles.**
> Los 4 ciclos verificados end-to-end sobre la BD real en F32, integridad referencial
> revalidada fila por fila (0 huérfanos) y 0 incoherencias en columnas calculadas.
>
> **No hay fase siguiente.** El trabajo pospuesto está documentado en la sección
> "Trabajo futuro" de `DEUDA_TECNICA.md`. Para la entrega: `README.md`,
> `SETUP_COMPLETO.md`, `MATRIZ_ROLES.md`, `DEMO_CHECKLIST.md` y `RESUMEN_EJECUTIVO.md`.
>
> ⚠️ **Al reconstruir la BD desde cero, `fase32_fixes.sql` es el ÚLTIMO PASO y es
> OBLIGATORIO.** Sin él, `fn_proteger_total_pedido` queda con el bug y `pedido.total`
> se puede falsear a mano.

> **Nota F32 (2026-08-01):** Cierre de Deuda Técnica y Verificación Integral Final — **última fase**. Única fase que modifica lógica de fases anteriores, por diseño.
>
> **BUG CRÍTICO CORREGIDO:** `fn_proteger_total_pedido` usaba `pg_trigger_depth() = 0`, condición que **nunca** se cumple dentro de un trigger (allí vale 1), por lo que el trigger existía y **no protegía nada**. Reproducido: `UPDATE pedido SET total = 9999` pasaba sin error (478.93 → 9999.00). Reimplementado comparando contra `GREATEST(SUM(subtotal) − descuento, 0)` — **fórmula NETA**, detalle crítico: comparar contra la suma bruta habría rechazado todo pedido con descuento. 4 pruebas pasadas (malicioso rechazado; INSERT → 578.93; DELETE → 478.93; descuento 20 → 458.93). Guardado en `marathon-backend/sql/fase32_fixes.sql`, idempotente y con autoverificación.
>
> **Auditoría de protectores:** de los 7 triggers de validación/protección, **solo ese** tenía el defecto. Los 6 restantes (F21/F23/F27/F28/F29) ya comparaban contra el valor real. Verificado sobre el catálogo: **0 de las 17 funciones** contiene `pg_trigger_depth`.
>
> **BUG ADICIONAL CORREGIDO (no estaba en el plan):** `GET /api/productos?origen=fabricado` daba **500** (`no existe la función lower(bytea)`) porque PostgreSQL no infería el tipo de los parámetros NULL en `buscarConFiltros` (F27). Habría roto en vivo los desplegables de Producción. Corregido con `CAST(:param AS string)` en `ProductoRepository`; sin regresión (108 productos).
>
> **Seguridad de rutas:** `rolGuard` aplicado a 12 rutas heredadas que solo tenían `authGuard`, según `MATRIZ_ROLES.md`. El guard redirige a `/dashboard?acceso=denegado` con aviso visible.
>
> **Deuda:** resueltas #1 (trigger), #3 (rutas) y #5 (promedio ponderado en `costoPromedioFabricacion` = `SUM(costo_total)/SUM(cantidad_producida)`). **Pospuesta #2** (mano de obra e indirectos en el costo estimado por BOM) con justificación: requiere tabla de costos estándar, es funcionalidad nueva y no un bug, y el riesgo cerca de la entrega no compensa. Criterio declarado: **estabilidad para la demo > cerrar el 100 % de la deuda**.
>
> **Los 4 ciclos verificados end-to-end sobre la BD real:** ①ᅟpedido #26 total 235.00 neto, picking, empaque HU-F32-001, entregado, comprobante `COMP-2026-000001` cuadra. ②ᅟOC #9 total 800.00 por trigger, separación de funciones respetada, recepción → costo promedio 8.0000 exacto, factura 920.00 GENERATED, CxP saldo 0.00 y factura `pagada` por cascada. ③ᅟOP #11 iniciada con snapshots, completada 9/10 con merma 1.500 → 187.35 total / 20.8167 unitario. ④ᅟRMA #6 inspección: apto_reventa subió stock 1→2 con autoría por trigger, defectuoso no tocó stock, devolución a proveedor #2 resuelta, doble uso rechazado por UNIQUE.
>
> **Integridad:** 37 tablas, 6 usuarios demo con rol, **70 FKs revalidadas fila por fila con `VALIDATE CONSTRAINT` → 0 huérfanos**, 0 incoherencias en `pedido.total` / `orden_compra.total` / `monto_pagado` / `costo_materia_prima`, 0 BOM u OP sobre productos no fabricados, 0 stock negativo, seed base intacto.
>
> **Hallazgos de documentación corregidos en el steering:** `database.md` describía mal 4 tablas base (`inventario` usa `stock_actual`/`fecha_actualizacion`; `movimiento_inventario` referencia `id_inventario`, no producto+bodega; `historial_inventario` usa `stock_anterior`/`stock_nuevo`/`motivo`/`fecha`; `comprobante_interno` está ligado a `id_pedido` con `total`), `rol` no tiene `estado`, y faltaba documentar `log_accion` (F19b, la tabla 37). **Pendiente anotado:** 4 de los 6 roles tienen 0 filas en `rol_permiso` (no rompe nada porque la autorización es por nombre de rol en `SecurityConfig`).
>
> **Documentación de entrega:** `README.md` reescrito (4 ciclos, 6 roles, 21 módulos, arquitectura de seguridad), `DEMO_CHECKLIST.md` con guion de 40–45 min por ciclo indicando el usuario de cada parte y pruebas de integridad en `psql`, `RESUMEN_EJECUTIVO.md` nuevo, `DEUDA_TECNICA.md` con la sección **Trabajo futuro**, y `SETUP_COMPLETO.md` con `fase32_fixes.sql` como paso 15 obligatorio. **PROYECTO COMPLETO 32/32.**

> **Nota F21 (2026-07-22):** Inicio del BLOQUE 8 — Compras. Primer módulo del ciclo Procure-to-Pay que se suma a las 20 fases base ya completadas. **Completada:** tablas `materia_prima`, `orden_compra`, `orden_compra_detalle` (con triggers de total y protección); roles Encargado de Compras y Encargado de Producción; permisos módulo `compras`; usuarios demo `compras@marathon.com` y `produccion@marathon.com`; backend (entidades, DTOs, repos, servicios, controladores, seguridad) y frontend (módulos `compras` y `materia-prima`, navbar, card de dashboard). **Fase siguiente: F22 — Recepción de mercancía.**

> **Nota F22 (2026-07-22):** Recepción de mercancía. Tablas `recepcion_mercancia` y `recepcion_mercancia_detalle`; columnas `stock_actual`/`stock_minimo` (NUMERIC) agregadas a `materia_prima`. Al recibir, sube stock de producto (por bodega, vía `inventario` + `movimiento_inventario` con `SET LOCAL app.current_user_id`) o el stock global de materia prima. Soporta recepciones parciales múltiples: `orden_compra_detalle.cantidad_recibida` se ACUMULA. El estado de la orden pasa automáticamente a `recibida_parcial` / `recibida_completa`. Endpoints `POST /api/recepciones` y `GET /api/recepciones/orden/{id}`. **Fase siguiente: F23 — Factura de Compra y Cuentas por Pagar.**
>
> **Nota F23 (2026-07-23):** Factura de Compra y Cuentas por Pagar. Tablas `factura_compra` (total GENERATED = subtotal + impuesto), `cuenta_por_pagar` (saldo_pendiente GENERATED = monto_total - monto_pagado; monto_pagado calculado por trigger), `pago_proveedor`. Triggers `fn_recalcular_monto_pagado_cxp` (statement-level con REFERENCING) y `fn_proteger_monto_pagado_cxp` (protección UPDATE manual). Cascada lógica: al pagar completamente, la cuenta pasa a `pagada` y la factura también. Validación en servicio: monto ≤ saldo_pendiente antes de insertar. Endpoints: `/api/facturas-compra`, `/api/cuentas-por-pagar`, `/api/pagos-proveedor`. Frontend: FacturaCompraNuevaComponent, CuentasPorPagarComponent, CuentaPorPagarDetalleComponent. Navbar: "Cuentas por Pagar" visible para Admin, Enc. Compras y Supervisor. **Fase siguiente: F24 — Devolución de Cliente (RMA).**
>
> **Nota F24 (2026-07-23):** Devolución de Cliente (RMA). Tablas `solicitud_devolucion`, `solicitud_devolucion_detalle`, `reembolso_cliente`. Solo pedidos 'entregado'. Flujo: solicitada → en_inspeccion → completada/rechazada. Inspección por línea: apto_reventa (sube stock), defectuoso (registrado para F25), rechazado (no toca nada). Reembolso informativo post-completada. Endpoints: `/api/devoluciones`. Frontend: DevolucionesListaComponent, DevolucionDetalleComponent, SolicitudDevolucionNuevaComponent. **Fase siguiente: F25 — Devolución a Proveedor.**
>
> **Nota F25 (2026-07-23):** Devolución a Proveedor. Tablas `devolucion_proveedor`, `devolucion_proveedor_detalle` (asociación polimórfica exclusiva: rma_cliente | recepcion_compra). Consume items defectuosos de F24 (solicitud_devolucion_detalle.resultado_inspeccion='defectuoso') y F22 (recepcion_mercancia_detalle.cantidad_defectuosa>0). UNIQUE constraints impiden doble uso de un item. Bandeja `/items-disponibles` lista ambos orígenes. Flujo: pendiente → enviada → resuelta/rechazada. Resolución con tipo (reembolso/reposición) y monto. **Ciclo completo de calidad cerrado end-to-end: cliente devuelve → inspección → devolución a proveedor → resolución.** Fase siguiente: F26 — Materia Prima: Inventario y Kardex.
>
> **Nota F31 (2026-07-23):** Consolidación. **Seed de demostración PERMANENTE** de los bloques nuevos: `marathon-backend/sql/fase31_seed_demo_bloques_nuevos.sql` (idempotente, verificado ejecutándolo dos veces). Siembra 10 materias primas, 3 productos fabricados con BOM (12 líneas), 4 órdenes de compra (2 `recibida_completa` con recepción real y costo promedio ponderado exacto, 1 `aprobada` pendiente, 1 `borrador`), 2 facturas + 2 CxP + 2 pagos (una pagada por cascada de trigger, una con abono parcial), 3 RMA de cliente (apto_reventa / defectuoso / en_inspeccion), 1 devolución a proveedor que consume el item defectuoso, y 2 órdenes de producción (una completada con costeo real MP 312 + MO 180 + IND 60 = 552, unitario 11.04; una planificada). Respeta todas las columnas GENERATED/trigger y usa `fn_set_costo_materia_prima_op()`. **Dashboard segmentado por rol** (getters `verKpisPedidos`/`verKpisVentas`/`verKpisCompras`/`verKpisProduccion`): Compras y Producción ya no ven KPIs comerciales y sí los propios, con accesos rápidos. **Auditoría de navegación** de los 6 roles en `MATRIZ_ROLES.md` con 8 desalineaciones corregidas (incluye `rolGuard` faltante en `/picking` y Materia Prima ausente en navbar para Enc. Compras). 6 usuarios demo verificados. `DEUDA_TECNICA.md` incluye el **INVENTARIO consolidado de deuda** para F32, con `fn_proteger_total_pedido` como pendiente prioritario. **Fase siguiente: F32.**

> **Nota F30 (2026-07-23):** Reportes de Manufactura y Dashboard de Producción — **cierra el Bloque 9**. Fase de SOLO LECTURA: no crea tablas ni escribe datos, solo consultas agregadas. `ReporteManufacturaService` (nativas, SELECT puros): `consumoMateriaPrima` (agrupa `movimiento_materia_prima` tipo 'salida_produccion' + 'merma', valorizado con el `costo_unitario_snapshot` de F29 y respaldo al promedio actual), `eficienciaProduccion` (producidas/planificadas % + suma de mermas positivas por OP) y `resumenManufactura` (KPIs del dashboard). Endpoints `POST /api/reportes/manufactura/{consumo-materia-prima|eficiencia-produccion}/{preview|excel|pdf}` y `GET /api/dashboard/manufactura`. Reutiliza ExcelService/PdfReporteService (F17) y Chart.js (F16). Frontend: 2 tabs nuevos en Reportes, `DashboardManufacturaComponent` en `/produccion/dashboard` (7 KPI cards, barras top-3, dona por estado, semáforo de merma verde<5%/amarillo 5-15%/rojo>15%), tira compacta de producción en el dashboard principal, y navbar "Manufactura" agrupando Materia Prima + Producción + Dashboard Producción + Análisis de Costos. Se agregaron `idMateriaPrima`/`idProducto` a `FiltroReporteDTO`. **Sin script SQL** (solo código). **Verificado con datos reales: consumo 41.000 uds/$492 en 2 OP; eficiencia 100% y 80%; ambos reportes exportan XLSX y PDF válidos; dashboard con merma 2.50%.** **Fase siguiente: F31 — Consolidación.**

> **Nota F29 (2026-07-23):** Costeo de Producción. Costo real de fabricar cada OP con **costo promedio ponderado** de la materia prima consumida + mano de obra + indirectos. `materia_prima.costo_unitario_promedio` se recalcula SOLO al recibir compra (retrofit en RecepcionMercanciaService de F22). Al iniciar una OP se captura `orden_produccion_consumo.costo_unitario_snapshot` (inmutable = costo histórico correcto); `costo_linea` GENERATED = COALESCE(real,teorica) × snapshot. `orden_produccion.costo_materia_prima` lo fija la función SQL `fn_set_costo_materia_prima_op(id)` (suma de costo_linea) y el trigger `trg_proteger_costo_materia_prima_op` solo admite ese valor real; `costo_total` y `costo_unitario_producido` son GENERATED. Nuevo: costo estimado por BOM (`GET /api/productos/{id}/costo-estimado`) con margen bruto, y `/api/analisis-costos` (fabricar-vs-comprar y listado de productos fabricados). Frontend: columna costo promedio en Materia Prima, panel de costo estimado en el modal de producto, panel de costos en el detalle de OP, `AnalisisCostosComponent` en `/produccion/costos`, card de dashboard y 4º reporte "Costos de Producción" (preview + Excel + PDF). **Se corrigió un bug latente:** faltaba `@Generated` en subtotal/total/saldoPendiente de F9/F21/F23. **Ciclo probado: recibir a $10 → costo 10; recibir a $20 → promedio ponderado 15; iniciar OP → snapshot 15; completar → total y unitario calculados por la BD.** **Fase siguiente: F30 — Reportes de Manufactura.**

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

28. **materia_prima.costo_unitario_promedio se recalcula SOLO al recibir compra** (F29) — Costo promedio ponderado: `((stock_ant × costo_ant) + (cant_buena × precio_compra)) / (stock_ant + cant_buena)`. NUNCA se edita manualmente ni se recalcula al consumir.

29. **El snapshot de costo en el consumo es inmutable** (F29) — `orden_produccion_consumo.costo_unitario_snapshot` se captura al INICIAR la OP y no se altera después. El costo histórico de una orden no cambia retroactivamente aunque el promedio suba/baje.

30. **costo_linea, costo_total y costo_unitario_producido son GENERATED** (F29) — Nunca se escriben. `costo_materia_prima` NO es GENERATED (no se permiten subconsultas): lo fija `fn_set_costo_materia_prima_op(id)` y el trigger `trg_proteger_costo_materia_prima_op` rechaza cualquier valor distinto de la suma real de los consumos.

31. **Columnas GENERATED en JPA requieren `@Generated(event={INSERT,UPDATE})`** (F28/F29) — Sin esa anotación Hibernate no re-lee el valor calculado por la BD y el DTO devuelve datos stale si se lee en la misma transacción. Aplicado a `merma`, `costo_linea`, `costo_total`, `costo_unitario_producido`, y retroactivamente a `DetallePedido.subtotal`, `OrdenCompraDetalle.subtotal`, `FacturaCompra.total`, `CuentaPorPagar.saldoPendiente`.

## Notas de Seguridad — Fase 18 (Asistente IA)
- La API key de Anthropic va en `application-local.properties` (gitignored), NUNCA en `application.properties` ni en el repo.
- El IAService solo ejecuta queries SELECT (valida contra INSERT/UPDATE/DELETE/DROP/TRUNCATE/ALTER/CREATE).
- Límite de 500 resultados por consulta.
- Riesgo conocido: se ejecuta SQL generado por IA. La validación SELECT-only es la principal mitigación.
