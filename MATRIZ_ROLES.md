# Matriz de Roles y Navegación — Marathon Sports

> Generada en la **Fase 31 (Consolidación)** cruzando tres capas: enlaces del
> **navbar** (`navbar.component.ts`), **guards de ruta** (`app.routes.ts`) y
> **autorización del backend** (`SecurityConfig.java`).
>
> **Actualizada en la Fase 37**, que añadió una cuarta capa: los **privilegios de
> PostgreSQL**. Desde esa fase cada rol se conecta a la base con su propio
> usuario, así que la base ya no es un espectador de la navegación sino la última
> palabra. Ver la sección «La cuarta capa» al final y la sección 9 de
> `SEGURIDAD_ROLES.md`.

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
| Inventario | `/inventario` | ✅ | 🔓 | ✅ | ❌ | 🔓 | ❌ |
| Clientes | `/clientes` | ✅ | 🔓 | ❌ | ✅ | ❌ | ❌ |
| **Pedidos** | | | | | | | |
| Pedidos | `/pedidos` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| Pedidos Especiales | `/pedidos/especiales` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| Comprobantes | `/comprobantes` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| **Operaciones** | | | | | | | |
| Picking | `/picking` | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ |
| Empaque | `/empaque` | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ |
| Despachos | `/despachos` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Devoluciones (RMA) | `/devoluciones` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
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

## Nota de diseño: las rutas que solo tenían `authGuard`

Hasta la F31, varias rutas heredadas de las fases 1–20 (`/inventario`,
`/clientes`, `/pedidos`, `/comprobantes`, `/empaque`, `/despachos`,
`/devoluciones`) protegían únicamente con `authGuard`, y en la matriz aparecían
como 🔓 para casi todos los roles. Se anotó en `DEUDA_TECNICA.md` como mejora de
defensa en profundidad.

**Ya está hecho:** todas esas rutas llevan hoy `rolGuard`, y por eso la matriz de
arriba tiene ❌ donde antes había 🔓. Los pocos 🔓 que quedan son rutas
alcanzables por URL pero sin enlace en el navbar, y en todos los casos la base de
datos permite lo que esa pantalla consulta.

---

## La cuarta capa: los privilegios de PostgreSQL (F37)

Desde la Fase 37 cada rol se conecta a `mod_venta_inve` con su propio usuario
(`RoleRoutingDataSource`), así que un enlace del menú que la base no permita ya
no da una pantalla a medias: da un 403. La matriz de navegación y los `GRANT`
tienen que decir lo mismo, y esta es la correspondencia:

| Rol | Usuario de base de datos | Lo que la base le permite leer |
|---|---|---|
| Administrador | `usr_admin_marathon` | Todo, sin DDL ni `TRUNCATE` |
| Supervisor E-Commerce | `usr_supervisor_marathon` | Las 37 tablas, **solo** lectura |
| Operador de Bodega | `usr_bodega_marathon` | Inventario, catálogo, pedidos, clientes, devoluciones |
| Operador de Pedidos | `usr_pedidos_marathon` | Catálogo, clientes, pedidos, comprobantes, devoluciones |
| Encargado de Compras | `usr_compras_marathon` | Catálogo, proveedores, compras, CxP, materia prima |
| Encargado de Producción | `usr_produccion_marathon` | Catálogo, materia prima, órdenes de producción, BOM |

Lo que **ningún** rol operativo puede hacer, por mucho que llegue a la pantalla:
modificar cuentas de usuario, escribir columnas calculadas, hacer `TRUNCATE` o
tocar el esquema.

**Cambios de navegación que introdujo la F37**, todos por alinear el backend con
lo que la base ya decía:

| Rol | Dejó de ver | Porque la base no le da |
|---|---|---|
| Encargado de Compras | Pedidos, Clientes, Comprobantes, Devoluciones de cliente | `SELECT` sobre `pedido`, `cliente`, `comprobante_interno` |
| Encargado de Producción | Pedidos, Clientes, Comprobantes, Devoluciones, Proveedores | `SELECT` sobre esas tablas |
| Op. Bodega y Op. Pedidos | Proveedores | `SELECT` sobre `proveedor` |

Verificado con `scripts/fase37_pruebas_endpoints.ps1` (66 de 66) y
`scripts/fase37_pruebas_navbar.ps1` (20 de 20): **todo enlace que el menú ofrece a
un rol abre, y todo lo que no le corresponde devuelve 403.**

---

## La quinta capa — los permisos, desde la F48 (2026-08-27)

Hasta la F48 este documento describía **cuatro** capas: navbar, guard de ruta,
`SecurityConfig` y los `GRANT` de PostgreSQL. Había una quinta construida —49
permisos, `rol_permiso`, `PermisoController`, la pantalla de roles— que **no
decidía nada**: la prueba era que el Encargado de Producción tenía 0 permisos de
49 y trabajaba con normalidad (defecto D-13).

Desde la F48 esa capa **decide**. El reparto vive en
`marathon-backend/sql/fase48_matriz_permisos.sql` y lo aplican **153 anotaciones**
`@PreAuthorize("hasAuthority('modulo:accion')")`, una por método de controlador.

**No es una capa nueva de restricciones**, y esto importa: la matriz está
**derivada de las reglas por rol de `SecurityConfig`** que esta tabla ya
documenta. Encenderla no le quitó el acceso a nadie. Lo que cambia es quién manda:

> Antes, cambiar quién puede hacer qué era editar `SecurityConfig` y recompilar.
> Ahora es **editar la matriz en la pantalla de roles**, y surte efecto en la
> petición siguiente — las authorities se releen de `rol_permiso` en cada
> petición, no del claim del token, así que ni siquiera hace falta volver a
> entrar.

### Permisos por rol

| Rol | Permisos | Módulos que cubre |
|---|---|---|
| Administrador | 94 | todos |
| Encargado de Compras | 24 | compras, recepciones, facturas, cuentas por pagar, pagos, devoluciones a proveedor, materia prima (ver), BOM (ver), proveedores (ver), catálogos (ver) |
| Operador de Pedidos | 20 | pedidos, clientes, comprobantes, devoluciones de cliente, empaque (ver), catálogos (ver) |
| Encargado de Producción | **20** | producción, materia prima, BOM, análisis de costos, informes de manufactura, catálogos (ver) |
| Supervisor E-Commerce | 20 | tablero, informes, IA, y lectura del circuito de venta, compras (CxP) y producción |
| Operador de Bodega | 19 | picking, empaque, inventario (movimientos), pedidos (estado), devoluciones (inspección), catálogos (ver) |

**Ningún rol puede quedarse en cero**: lo impide el propio script —falla dentro de
la transacción y no aplica nada— y lo vigila `MatrizPermisosTest`, que además
comprueba que no haya ningún `@PreAuthorize` con un permiso inexistente, ninguno
huérfano, y ningún método de controlador sin cubrir.

### Los dos endpoints sin permiso, a propósito

`GET /api/dashboard/resumen` y `PUT /api/usuarios/{id}/password` son de los seis
roles: cada uno ve sus indicadores y cambia su propia contraseña. Darles un
permiso obligaría a concedérselo a los seis, que es una forma retorcida de
escribir «cualquiera con sesión».

### Los cuatro que se comprueban en el servicio

`PUT /api/pedidos/{id}/estado` y `PUT /api/ordenes-compra/{id}/estado` hacen
varias cosas con repartos distintos en una sola llamada, así que la comprobación
no cabe en una anotación y vive en `config/Permisos`:

| Llamada | Permiso que exige |
|---|---|
| Pedido → cualquier estado | `pedidos:editar` |
| Pedido → `anulado` | `pedidos:anular` |
| OC → `pendiente_aprobacion` | `compras:crear` |
| OC → `aprobada` | `compras:aprobar` (**solo Administrador**) |
| OC → `rechazada` | `compras:rechazar` (**solo Administrador**) |
| OC → `cancelada` | `compras:cancelar` |

La separación de funciones —quien solicita una orden no puede aprobarla— **no**
se convirtió en permiso y sigue donde estaba: no depende de quién seas sino de
qué orden sea, y eso ningún permiso lo puede expresar.
