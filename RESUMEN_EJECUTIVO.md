# Resumen Ejecutivo — Marathon Sports

Sistema de Gestión de Pedidos, Compras, Manufactura y Calidad
Administración de Bases de Datos · UTEQ · 6.º Semestre

**Estado: proyecto completo — 32 de 32 fases.**

---

## El sistema en una frase

Plataforma web interna que cubre **cuatro ciclos de negocio completos** sobre una sola base de datos PostgreSQL, donde la integridad de los datos la garantiza la base de datos —no la aplicación— mediante columnas generadas, constraints y triggers de protección.

---

## Cifras

| Métrica | Valor |
|---|---|
| Fases completadas | **32 / 32** |
| Tablas | **37** |
| Funciones PL/pgSQL | **17** |
| Triggers | **24** |
| Claves foráneas | **70** (todas validadas, 0 huérfanos) |
| Columnas GENERATED | 8 |
| Triggers de protección de columnas calculadas | 4 |
| Módulos frontend | **21** |
| Endpoints REST | ~40 controladores bajo `/api/**` |
| Roles | **6** |
| Ciclos de negocio | **4** |
| Reportes exportables (Excel + PDF) | 6 |
| Dashboards | 2 (operativo segmentado por rol + manufactura) |

### Volumen de datos de demostración

| Tabla | Filas |
|---|---|
| ciudad | 88 |
| producto | 108 (105 comprados + 3 fabricados) |
| cliente | 40 |
| bodega | 20 |
| pedido | 25 |
| inventario | 267 |
| materia_prima | 10 |
| lista_materiales (BOM) | 12 |
| orden_compra | 4 |
| orden_produccion | 2 |
| solicitud_devolucion (RMA) | 3 |
| log_accion (auditoría) | 82 |

> Cifras tras la limpieza de los datos de prueba de la Fase 32. Corresponden al
> seed base más el seed demo permanente de F31.

---

## Los 4 ciclos de negocio

| Ciclo | Flujo | Fases |
|---|---|---|
| **Order-to-Cash** | pedido → procesado → picking → empaque → enviado → entregado → comprobante PDF | F9–F15 |
| **Procure-to-Pay** | orden de compra → aprobación → recepción → factura → cuenta por pagar → pago | F21–F23 |
| **Manufactura** | BOM → orden de producción → consumo de materia prima → costeo → producto terminado | F26–F30 |
| **Calidad** | RMA de cliente → inspección → devolución a proveedor → resolución | F24–F25 |

Los cuatro fueron **verificados end-to-end sobre la base de datos real** en la Fase 32, con evidencia registrada.

---

## Los 6 roles

| Rol | Alcance |
|---|---|
| Administrador | Acceso total: usuarios, catálogos, aprobación de órdenes de compra, configuración |
| Supervisor E-Commerce | Pedidos, asignación de picking, reportes operativos |
| Operador de Bodega | Inventario, recepción, picking, empaque, inspección de devoluciones |
| Operador de Pedidos | Creación de pedidos, clientes, solicitudes de devolución |
| Encargado de Compras | Órdenes de compra, recepciones, facturas, cuentas por pagar, devoluciones a proveedor |
| Encargado de Producción | Materia prima, BOM, órdenes de producción, costos, dashboard de manufactura |

La correspondencia entre lo que cada rol **ve** (navbar), a dónde **puede navegar** (guard de ruta) y qué **puede ejecutar** (backend) está auditada rol por rol en `MATRIZ_ROLES.md`.

---

## Qué distingue a este proyecto

### 1. La base de datos hace cumplir las reglas de negocio, no la aplicación

No es una capa de persistencia pasiva. Los valores derivados jamás se escriben desde Java:

- **8 columnas GENERATED**: `detalle_pedido.subtotal`, `orden_compra_detalle.subtotal`, `factura_compra.total`, `cuenta_por_pagar.saldo_pendiente`, `orden_produccion_consumo.merma`, `orden_produccion_consumo.costo_linea`, `orden_produccion.costo_total`, `orden_produccion.costo_unitario_producido`.
- **Valores calculados por trigger**: `pedido.total`, `orden_compra.total`, `cuenta_por_pagar.monto_pagado`.
- **4 triggers de protección** que rechazan cualquier intento de escribir esos campos a mano, comparando contra el valor real recalculado.

En JPA esas columnas llevan `@Column(insertable=false, updatable=false)` y, cuando se leen en la misma transacción en que se escribió la fila, además `@Generated(event={INSERT,UPDATE})` para que Hibernate relea el valor que calculó la base de datos.

### 2. Un trigger que parecía proteger y no protegía — detectado, demostrado y corregido

`fn_proteger_total_pedido` (heredado de las fases base) condicionaba su excepción a `pg_trigger_depth() = 0`. **Dentro de una función de trigger esa función devuelve 1, nunca 0**, así que la condición jamás se cumplía: el trigger existía, aparecía en el catálogo y no protegía nada. Se comprobó explotable (`UPDATE pedido SET total = 9999` pasaba sin error) y se corrigió en la Fase 32 comparando contra `GREATEST(SUM(subtotal) − descuento, 0)`.

El detalle fino importa: había que comparar contra el **total neto**, no contra la suma bruta de subtotales, porque hacerlo contra el bruto habría roto todos los pedidos con descuento. Se auditaron los otros 5 triggers de protección y ninguno tenía el defecto.

### 3. Manufactura con BOM y costeo contablemente correcto

- Un producto es `comprado` o `fabricado`. Solo los fabricados admiten lista de materiales y órdenes de producción, y eso lo impone la base de datos con dos triggers, no la aplicación.
- El **costo promedio ponderado** de cada materia prima se recalcula **solo al recibir una compra**: `((stock_ant × costo_ant) + (cant_buena × precio)) / (stock_ant + cant_buena)`.
- Al iniciar una orden se captura un **snapshot inmutable** del costo de cada material. Si el promedio se mueve después, el costo histórico de esa orden **no cambia**. Esa es la decisión contablemente correcta y es deliberada.
- El consumo ocurre al **iniciar**, no al crear, y se re-verifica la disponibilidad en ese momento porque otra orden pudo haber consumido stock mientras tanto.
- Las mermas se calculan solas: `merma = real − teórico`, columna GENERATED.

### 4. Ciclo de calidad cerrado de punta a punta

Pocos proyectos académicos cierran el círculo. Aquí el defecto viaja: el cliente devuelve, bodega inspecciona línea por línea, lo apto reingresa al stock, lo defectuoso alimenta una bandeja que **reúne dos orígenes distintos** (devoluciones de cliente y unidades defectuosas de recepciones de compra), compras lo devuelve al proveedor y el proveedor resuelve con reembolso o reposición. Constraints UNIQUE impiden que un mismo item defectuoso se devuelva dos veces.

### 5. Asociaciones polimórficas exclusivas resueltas en la base de datos

Dos casos donde una fila referencia **exactamente una** de dos entidades posibles:

- `orden_compra_detalle`: cada línea es un producto **o** una materia prima, según `tipo_item`, nunca ambos ni ninguno. CHECK `chk_oc_detalle_item_exclusivo`.
- `devolucion_proveedor_detalle`: cada línea viene de un RMA de cliente **o** de una recepción de compra. CHECK `chk_dpd_origen_exclusivo`.

Se resolvió con constraints, no con validación en Java, precisamente para que la garantía no dependa de que todo el mundo pase por el servicio.

### 6. Trazabilidad de inventario con autoría, vía contexto de sesión

Todo cambio de `inventario.stock_actual` queda registrado en `historial_inventario` con stock anterior, stock nuevo, usuario y timestamp. El trigger obtiene el usuario leyendo la variable de sesión `app.current_user_id`, que el servicio fija con `SET LOCAL` antes de cada UPDATE. Es una convención obligatoria del proyecto y está verificada en los cuatro ciclos.

### 7. Cuádruple capa de seguridad

1. **Frontend** — `authGuard` (sesión) + `rolGuard` (rol por ruta). Si un rol llega a una ruta prohibida se le redirige a su dashboard con aviso.
2. **Backend** — `SecurityConfig` restringe cada endpoint por rol. El **orden de las reglas importa**: las específicas van antes de las generales porque gana la primera coincidencia.
3. **Integridad de la base** — constraints, columnas generadas y triggers, efectivos incluso ante escrituras que se salten la aplicación.
4. **Privilegios de la base** — seis roles de PostgreSQL y **un usuario de conexión por rol** (F34 + F37): el backend elige el pool según el rol autenticado, así que un operador de bodega llega a la base como `usr_bodega_marathon` y no como el administrador.

El frontend no es la defensa: evita ofrecer pantallas que fallarían. Y desde la F37 la defensa última tampoco es el backend, sino la base: si `SecurityConfig` dejara pasar algo, los `GRANT` lo siguen negando.

### 8. Esquema versionado sin ORM que lo gestione

`spring.jpa.hibernate.ddl-auto=none`. Hibernate nunca toca el esquema. Toda evolución pasa por **scripts SQL idempotentes versionados por fase** (`fase00_ddl_base.sql`, `fase21_*` … `fase32_fixes.sql`), aplicables en orden sobre una base vacía. La secuencia completa se validó ejecutándola en una BD temporal. Sin Flyway ni Liquibase, por restricción del proyecto: el control de versiones del esquema es explícito y legible.

### 9. Deuda técnica registrada por fase, con lo pospuesto justificado

`DEUDA_TECNICA.md` es un registro incremental que nunca sobrescribe entradas anteriores: cada fase documenta sus simplificaciones, decisiones de diseño, riesgos y pendientes. La Fase 32 consolidó el inventario, resolvió lo prioritario y dejó explícito **qué se pospuso y por qué**. El criterio declarado en la fase final fue estabilidad para la entrega antes que cerrar el 100 % de la deuda.

---

## Verificación de integridad (Fase 32)

Ejecutada sobre la base de datos real, con resultados:

| Verificación | Resultado |
|---|---|
| Tablas en `public` | 37 ✅ |
| Usuarios demo activos con rol | 6 / 6 ✅ |
| Hash de contraseñas | BCrypt, 60 caracteres ✅ |
| FKs revalidadas fila por fila (`VALIDATE CONSTRAINT`) | 70 / 70, **0 huérfanos** ✅ |
| `pedido.total` coherente con neto recalculado | 0 incoherencias ✅ |
| `orden_compra.total` coherente con sus detalles | 0 incoherencias ✅ |
| `cuenta_por_pagar.monto_pagado` coherente con sus pagos | 0 incoherencias ✅ |
| `orden_produccion.costo_materia_prima` coherente con sus consumos | 0 incoherencias ✅ |
| BOM u órdenes de producción sobre productos no fabricados | 0 ✅ |
| Stock negativo (inventario y materia prima) | 0 ✅ |
| Funciones que aún usan `pg_trigger_depth` | **0** ✅ |
| Seed base intacto | 88 ciudades, 108 productos, 40 clientes, 20 bodegas ✅ |
| Los 4 ciclos de negocio end-to-end | 4 / 4 ✅ |

Tras la verificación se limpiaron **solo las filas de prueba** de la Fase 32,
devolviendo el stock a sus valores previos a partir de los propios registros de
movimiento (no de valores estimados). El seed base y el seed demo permanente de F31
quedaron intactos, verificado fila por fila antes de borrar.

---

## Bloques de desarrollo

| Bloque | Fases | Contenido |
|---|---|---|
| 1. Infraestructura | F1–F3 | Spring Boot + Angular + Docker, JWT, catálogos maestros |
| 2. Núcleo de negocio | F4–F8 | Usuarios y permisos, bodegas, productos, inventario, clientes |
| 3. Pedidos y operaciones | F9–F13.1 | Pedidos, estados, comprobantes, pedidos especiales |
| 4. Operaciones avanzadas | F14–F17 | Picking, empaque, dashboard, reportes |
| 5. Calidad y entrega | F18–F20 | Asistente IA, auditoría, cierre del proyecto base |
| 8. Compras | F21–F26 | Procure-to-Pay y ciclo de calidad, kardex de materia prima |
| 9. Manufactura | F27–F30 | Origen y BOM, órdenes de producción, costeo, reportes |
| 10. Cierre | F31–F32 | Consolidación, cierre de deuda técnica y verificación integral |

---

## Documentación de respaldo

| Documento | Para qué sirve |
|---|---|
| `README.md` | Instalación, credenciales, módulos, arquitectura de seguridad |
| `SETUP_COMPLETO.md` | Reconstruir la BD desde cero, script por script, con verificación |
| `MATRIZ_ROLES.md` | Auditoría de los 6 roles × 3 capas de seguridad |
| `DEMO_CHECKLIST.md` | Guion de sustentación recorriendo los 4 ciclos |
| `DEUDA_TECNICA.md` | Deuda por fase, inventario consolidado y trabajo futuro |
| `.kiro/steering/` | Contexto del proyecto, stack y esquema de BD verificado contra el catálogo |
