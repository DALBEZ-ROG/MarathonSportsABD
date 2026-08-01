# Matriz de Roles y Navegación — Marathon Sports

> Generada en la **Fase 31 (Consolidación)** cruzando tres capas: enlaces del
> **navbar** (`navbar.component.ts`), **guards de ruta** (`app.routes.ts`) y
> **autorización del backend** (`SecurityConfig.java`).

## Los 6 roles

| # | Rol | Usuario demo |
|---|-----|--------------|
| 1 | Administrador | `admin@marathon.com` |
| 2 | Supervisor E-Commerce | `supervisor@marathon.com` |
| 3 | Operador de Bodega | `bodega@marathon.com` |
| 4 | Operador de Pedidos | `pedidos@marathon.com` |
| 5 | Encargado de Compras | `compras@marathon.com` |
| 6 | Encargado de Producción | `produccion@marathon.com` |

Leyenda: ✅ acceso por navbar · 🔓 accesible por ruta pero sin enlace en navbar · ❌ sin acceso

---

## Matriz de módulos del frontend

| Módulo | Ruta | Admin | Supervisor | Op. Bodega | Op. Pedidos | Enc. Compras | Enc. Producción |
|--------|------|:-----:|:----------:|:----------:|:-----------:|:------------:|:---------------:|
| Dashboard | `/dashboard` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Mi Perfil | `/perfil` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Gestión** | | | | | | | |
| Datos Maestros | `/datos-maestros` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Proveedores | `/proveedores` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Productos | `/productos` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Bodegas | `/bodegas` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Inventario | `/inventario` | ✅ | 🔓 | ✅ | 🔓 | 🔓 | 🔓 |
| Clientes | `/clientes` | ✅ | 🔓 | 🔓 | ✅ | 🔓 | 🔓 |
| **Pedidos** | | | | | | | |
| Pedidos | `/pedidos` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Pedidos Especiales | `/pedidos/especiales` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Comprobantes | `/comprobantes` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Operaciones** | | | | | | | |
| Picking | `/picking` | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ |
| Empaque | `/empaque` | ✅ | 🔓 | ✅ | 🔓 | 🔓 | 🔓 |
| Despachos | `/despachos` | ✅ | ✅ | ✅ | 🔓 | 🔓 | 🔓 |
| Devoluciones (RMA) | `/devoluciones` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Compras** | | | | | | | |
| Órdenes de Compra | `/compras` | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ |
| Cuentas por Pagar | `/cuentas-por-pagar` | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| Dev. a Proveedor | `/devoluciones-proveedor` | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ |
| **Manufactura** | | | | | | | |
| Materia Prima | `/materia-prima` | ✅ | ❌ | ❌ | ❌ | ✅ | ✅ |
| Producción (OP) | `/produccion` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Dashboard Producción | `/produccion/dashboard` | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ |
| Análisis de Costos | `/produccion/costos` | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ |
| **Análisis** | | | | | | | |
| Reportes | `/reportes` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Asistente IA | `/ia` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Auditoría | `/auditoria` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Administración** | | | | | | | |
| Usuarios | `/usuarios` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Roles | `/roles` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |

---

## Dashboard principal: qué ve cada rol (F31)

El dashboard se adapta por rol. Los roles de Compras y Producción **no** ven KPIs
comerciales, y los importes de ventas quedan restringidos.

| Bloque del dashboard | Admin | Supervisor | Op. Bodega | Op. Pedidos | Enc. Compras | Enc. Producción |
|---|:--:|:--:|:--:|:--:|:--:|:--:|
| KPIs de pedidos (hoy, entregados, enviados, pendientes) | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| Importes de ventas (hoy / mes) | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Gráficos comerciales + Top productos | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| Stock bajo | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Pedidos especiales | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| Devoluciones pendientes de inspección | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ |
| OC por aprobar | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| CxP vencidas | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| Productos fabricados | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Materia prima bajo mínimo | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ |
| OP en proceso | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Costo promedio de producción (mes) | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ |
| Tira compacta de Producción | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ |
| Asistente IA | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Accesos rápidos propios del rol | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

---

## Desalineaciones detectadas y corregidas en F31

| # | Hallazgo | Corrección |
|---|----------|------------|
| 1 | El dashboard mostraba **KPIs comerciales (pedidos y ventas) a todos los roles**, incluidos Compras y Producción, para quienes no son competencia. | Se agregaron los getters `verKpisPedidos` / `verKpisVentas` y se condicionaron las tarjetas, los gráficos y el Top de productos. |
| 2 | La tarjeta **"OC por aprobar" solo la veía el Administrador**, aunque es el módulo propio del Encargado de Compras (y el backend ya lo autoriza). | Ahora usa `verKpisCompras` (Admin, Enc. Compras, Supervisor). |
| 3 | **"CxP vencidas"** excluía al Encargado de Compras pese a que el backend le permite el módulo. | Ahora usa `verKpisCompras`. |
| 4 | **"Costo promedio de producción"** excluía al Encargado de Producción, aunque el backend le permite `/api/analisis-costos`. | Se añadió `isProduccion`. |
| 5 | Los datos de OC pendientes / CxP / materia prima **solo se cargaban si el usuario era Administrador**, así que los roles nuevos veían las tarjetas en 0. | La carga ahora depende de `verKpisCompras` / `verKpisProduccion`. |
| 6 | **Compras y Producción no tenían accesos rápidos** en el dashboard. | Se añadieron bloques de accesos rápidos para ambos roles. |
| 7 | **`/materia-prima`**: la ruta y el backend permitían al Encargado de Compras, pero **el navbar no le mostraba el enlace**. | Se añadió `Encargado de Compras` al ítem del navbar. |
| 8 | **`/picking`**: la ruta solo exigía `authGuard`, así que cualquier rol podía entrar y el backend respondía **403** (pantalla rota). | Se añadió `rolGuard` con Admin + Operador de Bodega, alineado con navbar y backend. |

---

## Nota de diseño: rutas con solo `authGuard`

Varias rutas heredadas de las fases 1–20 (`/inventario`, `/clientes`, `/pedidos`,
`/comprobantes`, `/empaque`, `/despachos`, `/devoluciones`) protegen únicamente con
`authGuard`, sin `rolGuard`. En la matriz aparecen como 🔓 para los roles que no
tienen enlace en el navbar.

Esto es **deliberado y no es un agujero de seguridad**: el backend es la defensa
efectiva y sí restringe las operaciones sensibles (por ejemplo, crear pedidos es
solo Admin/Operador de Pedidos; los movimientos de inventario son solo
Admin/Operador de Bodega). El efecto práctico de entrar por URL es una pantalla de
consulta con datos que el backend permite leer, o un 403 al intentar escribir.

Queda anotado en `DEUDA_TECNICA.md` como mejora de defensa en profundidad para la
Fase 32: añadir `rolGuard` a esas rutas para que la navegación no ofrezca pantallas
que el backend luego rechaza.
