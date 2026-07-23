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
| 23 | Factura de Compra y Cuentas por Pagar | ⏳ Pendiente (siguiente) |

> **Nota F21 (2026-07-22):** Inicio del BLOQUE 8 — Compras. Primer módulo del ciclo Procure-to-Pay que se suma a las 20 fases base ya completadas. **Completada:** tablas `materia_prima`, `orden_compra`, `orden_compra_detalle` (con triggers de total y protección); roles Encargado de Compras y Encargado de Producción; permisos módulo `compras`; usuarios demo `compras@marathon.com` y `produccion@marathon.com`; backend (entidades, DTOs, repos, servicios, controladores, seguridad) y frontend (módulos `compras` y `materia-prima`, navbar, card de dashboard). **Fase siguiente: F22 — Recepción de mercancía.**

> **Nota F22 (2026-07-22):** Recepción de mercancía. Tablas `recepcion_mercancia` y `recepcion_mercancia_detalle`; columnas `stock_actual`/`stock_minimo` (NUMERIC) agregadas a `materia_prima`. Al recibir, sube stock de producto (por bodega, vía `inventario` + `movimiento_inventario` con `SET LOCAL app.current_user_id`) o el stock global de materia prima. Soporta recepciones parciales múltiples: `orden_compra_detalle.cantidad_recibida` se ACUMULA. El estado de la orden pasa automáticamente a `recibida_parcial` / `recibida_completa`. Endpoints `POST /api/recepciones` y `GET /api/recepciones/orden/{id}`. **Fase siguiente: F23 — Factura de Compra y Cuentas por Pagar.**
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

## Notas de Seguridad — Fase 18 (Asistente IA)
- La API key de Anthropic va en `application-local.properties` (gitignored), NUNCA en `application.properties` ni en el repo.
- El IAService solo ejecuta queries SELECT (valida contra INSERT/UPDATE/DELETE/DROP/TRUNCATE/ALTER/CREATE).
- Límite de 500 resultados por consulta.
- Riesgo conocido: se ejecuta SQL generado por IA. La validación SELECT-only es la principal mitigación.
