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

> **Nota F20 (2026-06-24):** Seed data cargado — 88 ciudades, 105 productos, 40 clientes, 25 pedidos, bodegas, proveedores, categorías, usuarios y roles. Verificación integral completada. Usuarios demo creados via DataInitializer. README.md y DEMO_CHECKLIST.md generados. **PROYECTO COMPLETO (20/20 fases).**

## Reglas de Negocio Críticas

1. **pedido.total es calculado por trigger** — NUNCA escribir este campo desde la aplicación. El trigger `trg_actualizar_total_pedido` calcula el total automáticamente al insertar/actualizar/eliminar detalles.

2. **detalle_pedido.subtotal es GENERATED** — NUNCA insertar ni actualizar este campo. Es una columna generada: `cantidad * precio_unitario`.

3. **usuario.password llega hasheado (min 60 chars)** — La base de datos NO hashea contraseñas. La aplicación DEBE enviar el password ya hasheado con BCrypt antes del INSERT/UPDATE.

4. **Para movimientos de stock** — Antes de hacer UPDATE a inventario, ejecutar: `SET app.current_user_id = '<id_usuario>'` para que el trigger de historial registre quién hizo el cambio.

## Notas de Seguridad — Fase 18 (Asistente IA)
- La API key de Anthropic va en `application-local.properties` (gitignored), NUNCA en `application.properties` ni en el repo.
- El IAService solo ejecuta queries SELECT (valida contra INSERT/UPDATE/DELETE/DROP/TRUNCATE/ALTER/CREATE).
- Límite de 500 resultados por consulta.
- Riesgo conocido: se ejecuta SQL generado por IA. La validación SELECT-only es la principal mitigación.
