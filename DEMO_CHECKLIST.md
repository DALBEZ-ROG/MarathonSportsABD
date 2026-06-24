# Guion de Demostración — Marathon Sports

Sistema de Gestión de Pedidos E-Commerce | Proyecto Semestral

---

## Preparación Previa

- [ ] Backend corriendo en `http://localhost:8080`
- [ ] Frontend corriendo en `http://localhost:4200`
- [ ] Base de datos con seed data cargado
- [ ] Navegador abierto en modo limpio (sin sesión previa)
- [ ] Tener a mano las credenciales de los 4 usuarios

---

## Escena 1 — Login con cada rol (5 min)

**Objetivo:** mostrar que el sistema reconoce roles y adapta la UI.

- [ ] Iniciar sesión como `admin@marathon.com` / `Admin1234!`
  - Mostrar menú completo: todos los módulos visibles
  - Señalar el nombre del usuario y rol en el header
- [ ] Cerrar sesión — iniciar como `supervisor@marathon.com` / `Demo1234!`
  - Mostrar que solo ve Dashboard, Pedidos y Reportes
- [ ] Cerrar sesión — iniciar como `bodega@marathon.com` / `Demo1234!`
  - Mostrar que ve Inventario, Picking y Empaque
- [ ] Cerrar sesión — iniciar como `pedidos@marathon.com` / `Demo1234!`
  - Mostrar que ve Pedidos y Clientes
- [ ] Volver a iniciar como `admin@marathon.com` para el resto de la demo

---

## Escena 2 — Crear un pedido completo desde cero (5 min)

**Objetivo:** demostrar el ciclo de creación con validaciones.

- [ ] Ir a **Pedidos → Nuevo Pedido**
- [ ] Seleccionar un cliente existente (ej. buscar por nombre)
- [ ] Agregar 2 o 3 productos del catálogo
  - Observar que el subtotal se calcula automáticamente
  - Ingresar un descuento (ej. $10.00) y observar cómo baja el total
- [ ] Marcar como **Pedido Especial** → tipo "regalo" → agregar nota
- [ ] Guardar el pedido
- [ ] Verificar que el pedido aparece en la lista con estado **pendiente**
- [ ] Señalar que el `total` fue calculado por trigger en BD (no por la app)

---

## Escena 3 — Flujo completo de estados (5 min)

**Objetivo:** mostrar la transición de estados con validaciones de negocio.

- [ ] Abrir el pedido recién creado
- [ ] Cambiar estado a **Procesado** — observar la confirmación
- [ ] Ir a **Picking → Lista de Pedidos**
  - Localizar el pedido y hacer clic en **Ejecutar Picking**
  - Confirmar las líneas de productos una a una (o todas)
  - Guardar el picking completado
- [ ] Ir a **Empaque → Lista para Empacar**
  - Localizar el pedido
  - Ingresar número HU, transportista y región destino
  - Confirmar empaque → el estado cambia a **Enviado**
- [ ] Volver al pedido → cambiar a **Entregado**
- [ ] Mostrar que no se puede hacer más transiciones (estado final)

---

## Escena 4 — Generar y descargar comprobante PDF (3 min)

**Objetivo:** mostrar la generación de comprobante con total neto.

- [ ] Desde el detalle del pedido, hacer clic en **Generar Comprobante**
- [ ] Observar el número de comprobante generado (ej. COMP-2026-000001)
- [ ] Hacer clic en **Descargar PDF**
- [ ] Abrir el PDF y señalar:
  - Datos del cliente
  - Tabla de productos con subtotales
  - Línea de descuento aplicado
  - **TOTAL NETO** = subtotal − descuento
- [ ] Ir a **Comprobantes** para ver el listado general

---

## Escena 5 — Dashboard con KPIs reales (3 min)

**Objetivo:** mostrar el valor analítico del sistema.

- [ ] Ir a **Dashboard**
- [ ] Señalar los KPIs en tiempo real:
  - Total de pedidos del día / semana
  - Pedidos por estado (gráfico de dona)
  - Top productos más vendidos
  - Ventas por día (gráfico de línea)
- [ ] Cambiar el rango de días del gráfico de ventas
- [ ] Destacar que los datos son de la BD real (seed data + pedido recién creado)

---

## Escena 6 — Generar reporte y exportar a Excel (3 min)

**Objetivo:** mostrar capacidades de reporting y exportación.

- [ ] Ir a **Reportes**
- [ ] Seleccionar tipo de reporte: **Pedidos**
- [ ] Aplicar filtros de fecha (ej. mes actual)
- [ ] Hacer clic en **Vista Previa** — mostrar la tabla de resultados
- [ ] Hacer clic en **Exportar Excel** — verificar descarga del archivo `.xlsx`
- [ ] Opcionalmente exportar en PDF
- [ ] Cambiar a reporte de **Ventas por Producto** y mostrar otro filtro

---

## Escena 7 — Consulta al Asistente IA (3 min)

**Objetivo:** mostrar la integración con Claude AI para consultas en lenguaje natural.

- [ ] Ir a **Asistente IA**
- [ ] Escribir una consulta en lenguaje natural, por ejemplo:
  - "¿Cuáles son los 5 productos más vendidos este mes?"
  - "¿Cuántos pedidos están pendientes?"
  - "¿Qué clientes tienen más pedidos?"
- [ ] Mostrar la respuesta del asistente con los datos reales
- [ ] Señalar que el asistente solo ejecuta SELECT (no puede modificar datos)

> **Nota:** requiere API key de Anthropic configurada en `application-local.properties`

---

## Escena 8 — Auditoría de cambio de stock (2 min)

**Objetivo:** demostrar la trazabilidad completa de movimientos.

- [ ] Ir a **Inventario**
- [ ] Seleccionar un producto y registrar un movimiento de **entrada** (ej. 10 unidades)
- [ ] Ir a **Auditoría**
- [ ] Seleccionar la pestaña **Historial de Inventario**
- [ ] Filtrar por el producto o la bodega usada
- [ ] Mostrar el registro del cambio: stock anterior, stock nuevo, usuario y fecha
- [ ] Señalar que el historial lo genera el trigger `fn_trg_historial_inventario` en PostgreSQL

---

## Puntos Clave a Destacar

1. **Integridad en BD:** `pedido.total` es GENERADO por trigger, la app no puede modificarlo directamente
2. **Seguridad JWT:** cada request lleva el token; roles limitan el acceso por endpoint
3. **Trazabilidad:** todos los cambios de inventario quedan auditados con usuario y timestamp
4. **PDF profesional:** comprobantes generados server-side con iText, sin dependencias de cliente
5. **IA integrada:** consultas en SQL generado por Claude AI, con validación SELECT-only

---

## Tiempo Total Estimado: 25-30 minutos
