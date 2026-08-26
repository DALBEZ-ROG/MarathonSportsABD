# Dashboard e interfaz — diagnóstico y propuesta

**Fecha:** 2026-08-25 · **Estado:** propuesta, pendiente de aprobación. Nada implementado.

---

# Parte 1 — Diagnóstico

Cada cifra del dashboard actual, contrastada contra una consulta directa a la base.

## 1.1 El contexto que lo explica casi todo

```sql
select min(fecha_pedido)::date, max(fecha_pedido)::date, current_date from pedido;
-- 2024-08-17  |  2026-08-17  |  2026-08-25
```

**El último pedido de la base es del 17 de agosto; hoy es 25.** Todo lo que el dashboard mide "hoy" sale en cero, y todo lo que mide "últimos 7 días" sale vacío. No es un defecto de cálculo — es que la pantalla no distingue *«no hubo ventas»* de *«no hay datos en este período»*, y las dos cosas se leen igual: un 0 grande y un gráfico en blanco.

## 1.2 Las 14 tarjetas, una por una

| # | Tarjeta | De dónde sale | Veredicto |
|---|---|---|---|
| 1 | Pedidos pendientes | `count(estado='pendiente')`, **histórico completo** | ❌ **No sirve para decidir.** Muestra 16.099, pero **10.389 (65%) tienen más de 6 meses**. Un pedido «pendiente» de 2024 no está pendiente: está abandonado |
| 2 | Pedidos procesados | idem | ❌ Igual: 19.058, de los que 12.284 superan los 6 meses |
| 3 | Pedidos enviados | idem | ❌ Igual: 24.370, con 15.746 de más de 6 meses |
| 4 | Pedidos entregados | idem | ❌ 162.367 acumulados desde 2024. Un contador que solo sube |
| 5 | Pedidos anulados | idem | ⚠️ 8.106 **sin denominador**. El dato útil es 3,5% de los creados, no el número suelto |
| 6 | Pedidos hoy | `cast(fecha_pedido as date)=current_date` | ⚠️ Correcto, pero hoy da **0** y se lee como «no vendimos» |
| 7 | **Ventas hoy** | `sum(total)` de pedidos **creados hoy** Y **`estado='entregado'`** | ❌ **Mal calculado.** Mezcla fecha de creación con estado de entrega: un pedido creado hoy no puede estar entregado hoy. La cifra tiende a 0 por construcción |
| 8 | **Ventas del mes** | idem, por mes de `fecha_pedido` | ❌ **Mal calculado, mismo motivo.** Los 2.481.323,59 que muestra son pedidos *creados* este mes que ya están entregados. Un pedido creado en julio y entregado en agosto **no cuenta en ningún mes correctamente** |
| 9 | Productos bajo mínimo | `stock_actual <= stock_minimo and stock_minimo > 0` | ⚠️ El cálculo es correcto (1.999 de 2.000 filas tienen mínimo). **Pero la pantalla de Inventario usa otra definición**: `stock <= 5` fijo (`InventarioService:90`). Dos números distintos para la misma pregunta |
| 10 | **Pedidos especiales activos** | `es_pedido_especial and estado <> 'anulado'` | ❌ **Mal calculado.** Muestra **11.056**; los realmente abiertos son **2.953**. Cuenta como «activos» los ya entregados. **3,7× inflado** |
| 11 | Picking pendiente | `procesado` con alguna línea sin recoger | ✅ Correcto y accionable |
| 12 | Productos fabricados | `count(origen='fabricado')` = 16 | ❌ **No es un indicador.** Es un dato de catálogo: no cambia solo, y nadie toma una decisión con él |
| 13 | OP en proceso | `count(estado='en_proceso')` | ⚠️ Correcto, sin denominador |
| 14 | Costo medio de producción | `avg(costo_total)` de OP completadas este mes | ⚠️ Correcto (5.125,37) pero **no dice sobre cuántas órdenes** (son 147). Un promedio sin su *n* no se puede interpretar |

**Resumen: 4 mal calculadas, 5 sin denominador o período, 4 inútiles para decidir, 1 correcta.**

## 1.3 Los tres gráficos

| Gráfico | Problema |
|---|---|
| Ventas por día (7 días) | **0 días con datos** en la ventana. Lienzo vacío, sin explicación |
| Movimientos de hoy | **0 filas**. Gráfico vacío |
| Top productos | **Sin filtro de fecha.** Es el top de **dos años enteros** presentado como si fuera del período actual |

## 1.4 Lo que hace el navegador y no debería

- **`this.mpStockBajo = res.length`** (`dashboard.component.ts:692`) — se descarga la lista completa de materias primas bajo mínimo **solo para contarlas en el navegador**. Es la única cifra que se calcula en el cliente, y sobra.
- **`error: () => { }`** — **cinco veces**. Si falla la red, la tarjeta se queda en su valor inicial: **0**. El usuario ve un cero donde la verdad es «no se pudo cargar». Es exactamente la confusión que hay que evitar.
- **Un solo mensaje de estado vacío en 843 líneas.**

## 1.5 Interfaz

| Problema | Evidencia |
|---|---|
| **Los modales pierden lo escrito** | **14 pantallas** con `<div class="modal-overlay" (click)="cerrarModal()">`. Pulsar fuera cierra y borra el formulario. (El cuadro interior sí hace `stopPropagation`, así que el problema es solo el clic fuera) |
| **Las pantallas no ocupan el monitor** | `.crud-container { max-width: 1200px }` en `styles.scss:71` y `max-width: 1320px` en el dashboard. En un monitor de 1920 px sobran 600 px a los lados |
| **El menú no sigue el flujo del negocio** | Orden actual: Portal, Dashboard, Datos Maestros, Proveedores, Productos, Bodegas, Inventario, Clientes, Pedidos… Mezcla configuración con operación diaria, y el abastecimiento (Compras) aparece *después* de la venta |
| **El inicio no es el inicio** | El login lleva a `/portal`, que es un menú de accesos; el dashboard queda como una pantalla más |
| Datos Maestros está incompleto | Solo Ciudades, Categorías y Unidades. Productos, Bodegas y Proveedores son maestros y viven sueltos en el menú principal |

## 1.6 Qué le falta a cada rol

| Rol | Hoy tiene que salir a buscar |
|---|---|
| **Operador de Pedidos** | Cuántas devoluciones esperan inspección; cuál es *su* cola real de pedidos (ve el histórico de 16.099) |
| **Operador de Bodega** | Cuántos pedidos están listos para empacar (picking completo); cuántas líneas le faltan por recoger |
| **Encargado de Compras** | Cuántas OC aprobadas siguen sin recibirse; qué vence esta semana (solo ve lo ya vencido) |
| **Encargado de Producción** | Qué OP planificadas **no tienen material suficiente** — el dato existe y no se muestra |
| **Supervisor E-Commerce** | Ticket medio, tasa de anulación, tiempo de pedido a despacho |
| **Administrador** | Todo lo anterior en una sola pantalla, con períodos comparables |

## 1.7 Lo que la base NO permite calcular

Esto es importante y hay que decirlo en pantalla, no rellenarlo con ceros:

- **No existe `fecha_entrega`.** `pedido` tiene `fecha_pedido`, `created_at`, `updated_at`, `fecha_limite_entrega` y `fecha_empaque`. **No hay forma de saber cuándo se entregó un pedido.** Por eso «ventas entregadas del mes» es incalculable tal como se plantea hoy.
- **La facturación cubre el 13,5%.** Solo 21.879 de 162.367 pedidos entregados tienen comprobante emitido. Cualquier «ventas facturadas» sería un 86% incompleto.
- **`fecha_empaque` cubre el 72%** (134.733 de 186.737 despachados). Sirve como fecha de salida, pero con esa reserva declarada.

---

# Parte 2 — Propuesta

## 2.1 Principio de diseño: la cifra viaja con su significado

El backend deja de devolver números sueltos y devuelve **indicadores**:

```json
{
  "clave": "pedidos_creados",
  "titulo": "Pedidos creados",
  "valor": 1338,
  "denominador": null,
  "unidad": "pedidos",
  "periodo": "Últimos 30 días (26 jul – 25 ago)",
  "base": "pedido.fecha_pedido, todos los estados",
  "comparacion": { "valor": 1512, "etiqueta": "30 días previos", "variacion": -11.5 },
  "estado": "ok",
  "enlace": "/pedidos"
}
```

`estado` es el campo que resuelve la confusión que pide el usuario:

| `estado` | Qué pinta la tarjeta |
|---|---|
| `ok` | El valor |
| `vacio` | «Sin pedidos entre el 26 de julio y el 25 de agosto» — **no un 0** |
| `sin_dato` | «Sin dato: la base no registra fecha de entrega» — **no un 0** |
| `parcial` | El valor + «calculado sobre el 72% de los pedidos: el resto no tiene fecha de empaque» |
| `error` | «No se pudo calcular: <motivo>» + botón reintentar — **no un 0** |

Una sola tarjeta en Angular consume ese contrato. No hay lógica de negocio en el navegador.

## 2.2 Un endpoint, calculado en el servidor

```
GET /api/dashboard/resumen?periodo=30d
```

Devuelve **solo los indicadores del rol del token**. Todo con SQL agregado (`count`, `sum`, `avg` con filtros de fecha) — ninguna consulta devuelve filas de detalle. El listado que hoy se descarga entero para contarlo (`materia-prima/stock-bajo`) pasa a ser un `count(*)`.

## 2.3 Los seis dashboards

Cada tarjeta lleva **período** y **base de cálculo** visibles, y enlaza a la pantalla donde se actúa.

### Administrador — 6 indicadores
| Indicador | Período | Base |
|---|---|---|
| Pedidos creados | 30 días, vs 30 previos | `pedido.fecha_pedido` (cobertura 100%) |
| Valor de lo pedido | 30 días | `sum(total)` de no anulados **/ nº de pedidos = ticket medio** |
| Tasa de anulación | 30 días | anulados / creados, en % |
| Pedidos atascados | ahora | en `procesado` con más de 7 días → enlaza a Picking |
| Referencias bajo mínimo | ahora | `n / total con mínimo definido` → Inventario |
| CxP vencidas | ahora | nº **e importe** (hoy: 1.476 por 7.957.099) → Cuentas por Pagar |

### Supervisor E-Commerce — 5
Pedidos creados · Ticket medio · Tasa de anulación · **Tiempo medio de pedido a despacho** (marcado `parcial`, 72%) · **Top 5 productos del período** (no histórico).

### Operador de Pedidos — 4
Pedidos que creé (hoy / 7 días) · Mi cola: pendientes de los últimos 30 días · Devoluciones esperando inspección · Pedidos especiales realmente abiertos (**2.953, no 11.056**).

### Operador de Bodega — 5
Pedidos esperando picking · Líneas por recoger **/ total de líneas** · Pedidos listos para empacar · Referencias bajo mínimo · Movimientos de los últimos 7 días (no «hoy», que siempre da 0).

### Encargado de Compras — 5
OC pendientes de aprobación · OC aprobadas sin recibir · CxP vencidas (nº + importe) · **CxP que vencen en 7 días** · Devoluciones a proveedor sin resolver.

### Encargado de Producción — 5
OP en proceso · **OP planificadas sin material suficiente** · Materia prima bajo mínimo · Costo medio por OP **con su n** (5.125,37 sobre 147) · Merma media del mes con su n.

## 2.4 Lo que se elimina

Productos fabricados (catálogo, no indicador) · Los cuatro contadores históricos de estado · «Ventas hoy» y «Ventas del mes» tal como están (se sustituyen por métricas con base declarada) · Movimientos de hoy (pasa a 7 días).

## 2.5 Interfaz

**Navegación por flujo de negocio**, en este orden:

```
1. Inicio            → el dashboard del rol (destino del login)
2. Datos maestros    → Productos · Categorías · Unidades · Ciudades · Bodegas · Proveedores
3. Abastecimiento    → Órdenes de compra · Recepciones · Facturas · Cuentas por pagar
                        · Devoluciones a proveedor · Materia prima
4. Producción        → Órdenes · Análisis de costos
5. Venta             → Clientes · Pedidos · Pedidos especiales · Comprobantes
6. Almacén y salida  → Picking · Empaque · Despachos
7. Posventa          → Devoluciones de cliente
8. Análisis          → Reportes · Auditoría · Asistente IA
9. Administración    → Usuarios · Roles
10. Mi cuenta
```

**Ancho:** fuera los `max-width` fijos. Contenedor fluido con `width: 100%` y padding `clamp(1rem, 3vw, 2.5rem)`, con un tope alto (`1800px`) solo para que el texto no se estire en pantallas ultra-anchas. Las rejillas de tarjetas pasan a `repeat(auto-fit, minmax(260px, 1fr))`: se adaptan solas sin *media queries* por tamaño.

**Modales (las 14 pantallas):** se retira el `(click)="cerrarModal()"` del fondo. Se cierra con el botón Cancelar, con la ✕ o con `Escape`; y si hay cambios sin guardar, `Escape` y Cancelar piden confirmación. Se añade `role="dialog"`, foco atrapado dentro y foco devuelto al abrir/cerrar.

**Datos Maestros:** se le añaden como pestañas Productos, Bodegas y Proveedores, que hoy cuelgan sueltos del menú principal. Las seis pestañas comparten una tabla, un buscador y un modal común, para que se comporten igual.

**Estados en todas las pantallas de lista:** esqueleto mientras carga; mensaje concreto cuando está vacía; y error con motivo y reintento. Hoy varias pantallas muestran una tabla en blanco en los tres casos.

## 2.6 Sin librerías nuevas

`chart.js` ya está en el proyecto y se queda. No entra nada más: las tarjetas, los esqueletos y los modales son CSS y Angular.

---

# Parte 3 — Alcance y orden de trabajo

| # | Bloque | Qué incluye |
|---|---|---|
| **D1** | Indicadores en el servidor | DTO con período/base/estado, `GET /api/dashboard/resumen` por rol, SQL agregado, pruebas que **contrastan cada indicador contra su consulta** |
| **D2** | Dashboard por rol | Componente de tarjeta reutilizable, los 6 dashboards, los cuatro estados explícitos |
| **D3** | Navegación y ancho | Menú por flujo, login → Inicio, contenedor fluido, rejillas adaptables |
| **D4** | Modales | Las 14 pantallas: no cerrar al pulsar fuera, Escape con confirmación, accesibilidad |
| **D5** | Datos maestros | Seis pestañas con tabla, buscador y modal comunes |
| **D6** | Estados en listados | Esqueleto / vacío / error en las pantallas de lista |

**Lo que no se hace:** no se inventa `fecha_entrega` ni se rellena con `updated_at` — se declara en pantalla que el dato no existe. Si se quiere «ventas por fecha de entrega», hace falta una columna nueva y su migración, y eso es una decisión aparte.

---

# Parte 4 — Ejecución

## D1 — Indicadores en el servidor ✅

**Fecha:** 2026-08-26

### Qué se creó

| Archivo | Qué es |
|---|---|
| `dto/dashboard/IndicadorDTO.java` | El contrato: valor + **período** + **base de cálculo** + `estado` + enlace. Fábricas `ok` / `sobre` / `porcentaje` / `sinDato` / `parcial` / `error` |
| `dto/dashboard/ComparacionDTO.java` | El período anterior y la variación. `variacion = null` cuando el previo fue cero: dividir entre cero no es «infinito por ciento» |
| `dto/dashboard/DashboardResumenDTO.java` | La respuesta: rol, período, `desde`/`hasta`, `generadoEn`, indicadores y top de productos |
| `dto/dashboard/TopProductoPeriodoDTO.java` | El ranking **del período**, no el acumulado de dos años |
| `repository/DashboardConsultas.java` | 22 consultas agregadas. Ninguna devuelve filas de detalle |
| `service/DashboardResumenService.java` | Los seis tableros. Cada cifra dentro de `intentar(...)`: una consulta rota da una tarjeta en `error`, no un tablero caído |
| `controller/DashboardController.java` | `GET /api/dashboard/resumen?periodo=30d\|7d\|90d` |
| `config/SecurityConfig.java` | La ruta se abre a cualquier usuario **autenticado**, antes de la regla general de `/api/dashboard/**` |

### Las cifras, contrastadas contra la base

Cada número servido por el endpoint, junto a la consulta directa que lo verifica:

| Indicador | Endpoint | Consulta directa |
|---|---|---|
| Pedidos creados (30 d) | 18.114 | 18.114 |
| …período previo | 16.218 | 16.218 |
| Valor de lo pedido | 3.931.438,06 sobre 17.460 | idem |
| Tasa de anulación | 3,6% (654 / 18.114) | idem |
| Pedidos atascados | 19.058 | 19.058 |
| Referencias bajo mínimo | 220 / 1.999 | idem |
| CxP vencidas | 7.981.391,17 en 1.479 | idem |
| Esperando picking | 10.057 | 10.057 |
| Listos para empacar | 9.001 | 9.001 |
| Líneas por recoger | 22.482 / 50.935 | idem |
| Movimientos (30 d) | 6.384 | 6.384 |
| Días a despacho | 2,16 · **parcial**, 10.545 de 14.646 | idem |
| Devoluciones esperando inspección | 328 | 328 |
| Especiales abiertos | **1.754** (antes se mostraban 11.056) | 1.754 |
| OC pendientes / aprobadas sin recibir | 268 / 449 | idem |
| CxP que vencen en 7 días | 146.203,95 en 25 | idem |
| Devoluciones a proveedor abiertas | 180 | 180 |
| OP en proceso | 932 / 1.149 | idem |
| OP sin material | 17 | 17 |
| Materia prima bajo mínimo | 20 / 300 | idem |
| Costo medio por OP | 5.104,37 sobre **164 órdenes** | idem |
| Merma media | 2,43% sobre 164 | idem |

### Correcciones de cálculo respecto al tablero anterior

- **Pedidos especiales activos: 11.056 → 1.754.** Contaba «distinto de anulado», que incluye los ya entregados.
- **Ventas hoy / Ventas del mes: eliminadas.** Mezclaban fecha de creación con estado de entrega. En su lugar, «Valor de lo pedido» con su base declarada, y una tarjeta `sin_dato` que dice que **la base no guarda fecha de entrega**.
- **CxP vencidas por fecha, no por etiqueta:** 1.479 frente a las 1.444 que tienen `estado = 'vencida'`. La fecha es el hecho; la etiqueta es una copia que puede quedarse atrás.
- **Top de productos, del período** y no del histórico completo.
- **Los cuatro contadores históricos de estado y «productos fabricados»: fuera.** No cambian lo que nadie hace hoy.

### Verificación

- **103 pruebas verdes** (eran 80). 23 nuevas: `DashboardVentanaTest` (11, sin base) y `DashboardResumenTest` (12, contra la base de pruebas).
- Las pruebas **no reescriben el SQL del servicio** — eso no comprobaría nada. Contrastan por **diferencias** (crear N filas y exigir que el indicador suba N), por **partición** (esperando picking + listos para empacar = todos los pedidos en `procesado`) y por **coherencia entre indicadores** (el denominador de la tasa de anulación tiene que ser el mismo número que «pedidos creados»).
- **Los seis tableros, probados por HTTP contra la base real con el enrutado por rol activo** (`app.datasource.roles.enabled=true`, puerto 18080). Cada rol calcula sus cifras con **su propia conexión de PostgreSQL**: ningún indicador salió en `error`, lo que demuestra que el reparto por rol cabe dentro de los permisos de F34/F37.

### Decisión aplicada

**No se añade `fecha_entrega`.** «Ventas entregadas» viaja como `sin_dato` con el motivo escrito en la tarjeta. Rellenarlo con `updated_at` daría un número que nadie podría auditar.

### Corrección de un lote anterior

Al compilar salió a la luz que `OrdenCompraController.actualizar()` (L13, D-22) usaba `SecurityContextHolder` **sin importarlo**: el módulo no compilaba. Se ha corregido usando el parámetro `Authentication`, que es el modismo del resto del archivo. Las 80 pruebas anteriores se habían ejecutado antes de esa edición.

---

## D2 — Dashboard por rol ✅

| Archivo | Qué es |
|---|---|
| `core/services/dashboard.service.ts` | El contrato tipado del endpoint. El navegador **no calcula ninguna cifra** |
| `shared/components/indicador-card/…` | La tarjeta. Los cinco estados se pintan **distintos a propósito** |
| `modules/dashboard/dashboard.component.ts` | Reescrito. 843 líneas de KPIs sueltos → tablero del rol con selector de período (7/30/90 días), esqueleto de carga, panel de error con reintento y pie que explica de dónde salen las cifras |

**Los cinco estados, en pantalla:** `ok` (valor + denominador + comparación) · `vacio` («Ninguno en este período», no un 0) · `sin_dato` (borde discontinuo, insignia «sin dato», el motivo en lugar del número) · `parcial` (insignia ámbar + la cobertura escrita) · `error` (borde rojo, motivo, botón Reintentar).

**La variación no se colorea de verde ni de rojo.** Que la tasa de anulación suba es malo y que los pedidos suban es bueno; el color mentiría en la mitad de los casos. Se muestra la dirección y el porcentaje.

**El gráfico se rehízo.** Ya no filtra por `estado='entregado'` —que hacía que los días recientes salieran bajos por construcción y la curva pareciera una caída de ventas inexistente—: cuenta todos los pedidos no anulados por `fecha_pedido`, y **rellena los días sin actividad con cero**, porque un hueco en el eje se lee como «no se midió».

**Los cinco `error: () => { }` desaparecieron.** Si la red falla, la pantalla lo dice con el motivo; no se queda en los ceros de inicialización.

## D3 — Navegación y ancho ✅

**El menú sigue el flujo del negocio**, y ahora recorre lo mismo que la mercancía:

`Inicio → Datos maestros → Abastecimiento → Producción → Venta → Almacén y salida → Posventa → Análisis → Administración → Mi cuenta`

Antes: `Portal, Dashboard, Datos Maestros, Proveedores, Productos, Bodegas, Inventario, Clientes, Pedidos…` — configuración mezclada con operación diaria, y el abastecimiento **después** de la venta.

- **El login lleva a `/inicio`**, que es el tablero del rol. `/portal` y `/dashboard` siguen respondiendo para no romper enlaces guardados.
- **Fuera los `max-width` fijos.** `1200px` → `width:100%` con tope alto de `1800px` y padding `clamp(1rem, 3vw, 2.5rem)`. En un monitor de 1920 ya no sobran 600 px.
- **Dos defectos de maquetación que salieron a la luz al hacerlo:**
  - `.main-content` tenía `margin-left: 260px` **y** `width: 100%` (de `styles.scss`): 260 px de desbordamiento horizontal. No se veía porque el contenido estaba topado a 1200 px. Ahora el hueco de la barra se hace con `padding-left`, que con `border-box` sí se descuenta.
  - `.main-content` tenía `z-index: 1`, creando un contexto de apilamiento donde el `z-index: 1000` de los modales no valía nada frente al `300` de la barra lateral: **el borde izquierdo de todos los modales quedaba tapado por el menú.** Se quitó el `z-index`.
- **El navbar mostraba menos de lo que las rutas permiten:** Inventario le faltaba a Supervisor y a Encargado de Compras, y Clientes a Supervisor. Alineado con las rutas.

## D4 — Modales ✅

`shared/directives/modal-seguro.directive.ts`, aplicada a **los 27 modales de las 14 pantallas**.

Antes: `<div class="modal-overlay" (click)="cerrarModal()">`. Un clic fuera —o un clic que se escapa al arrastrar para seleccionar texto— cerraba el modal y borraba el formulario entero sin avisar.

- **Pulsar fuera ya no cierra.** Se cierra con Cancelar, con la ✕ o con Escape.
- **Escape pregunta si hay algo escrito.** Lo «sucio» se detecta comparando los campos con lo que había al abrir, así que funciona igual en un alta que en una edición.
- **El foco arranca en el primer campo del formulario**, no en la ✕ (empezar con el foco en «cerrar» invita a pulsar Intro y perderlo todo).
- **El foco se queda dentro** tabulando, y **vuelve a donde estaba** al cerrar.
- `role="dialog"` + `aria-modal`.

**Comprobado en el navegador:** con «PRUEBA MODAL SEGURO» escrito, dos clics fuera del cuadro — el modal sigue abierto y el texto intacto.

## D5 — Datos maestros ✅

Seis pestañas, en el orden en que se dan de alta: **Productos · Categorías · Unidades · Ciudades · Bodegas · Proveedores**. Cada pestaña lleva una pista de qué se define en ella («Dónde se guarda el stock»), porque el nombre solo no basta.

Productos, Bodegas y Proveedores colgaban sueltos del menú principal entre pantallas de operación diaria, siendo maestros igual que los otros tres. Sus rutas antiguas (`/productos`, `/bodegas`, `/proveedores`) siguen vivas.

## D6 — Estados en listados ✅

`shared/components/estado-lista/estado-lista.component.ts`, aplicado a **11 pantallas de lista**: bodegas, clientes, proveedores, productos, materia prima, categorías, ciudades, unidades de medida, usuarios, roles e inventario.

Antes las tres situaciones se veían igual —tabla en blanco— y la fila de relleno decía literalmente «No hay registros» **incluso cuando el servidor no había contestado**: la pantalla afirmaba algo sobre los datos que nadie había comprobado.

- **Cargando:** esqueleto con la forma de la tabla.
- **Vacío:** «Aún no hay bodegas» + qué hacer. Y si hay filtros puestos, dice «Nada coincide con la búsqueda», porque «no hay» y «no hay con este filtro» son cosas distintas y la segunda tiene arreglo.
- **Error:** el motivo (`No hay conexión con el servidor` / `Tu rol no tiene permiso` / `Tu sesión ha caducado`) y un botón de reintentar.

**Comprobado en el navegador con el backend parado:** tanto el tablero como el listado de bodegas muestran el motivo y el botón; ninguno enseña ceros ni «no hay registros». Al levantar el backend, Reintentar recupera la pantalla.

## Corrección adicional: dos respuestas a la misma pregunta

`InventarioService.stockBajo()` usaba `findStockBajo(5)` —un umbral fijo de cinco unidades para todo el catálogo— mientras que el contador del tablero usaba `stock_actual <= stock_minimo`. La misma pregunta, «¿cuántas referencias hay que reponer?», tenía **dos respuestas según dónde se mirara: 116 en Inventario y 220 en el tablero**. Ahora las dos usan el mínimo de cada fila y ambas dicen **220**.

---

## Estado final

- **105 pruebas de backend en verde** (eran 80 al empezar la fase, 0 al empezar el proyecto).
- **Compilación de producción del frontend limpia**, dentro de los presupuestos de tamaño.
- **Los seis tableros verificados por HTTP contra la base real** con el enrutado de conexiones por rol activo.
- **La interfaz verificada en el navegador** en 1920×1080: tablero de Administrador y de Operador de Bodega, las seis pestañas de Datos maestros, un modal con datos sin guardar, los estados de vacío y de error, y el selector de período de 7 / 30 / 90 días.
