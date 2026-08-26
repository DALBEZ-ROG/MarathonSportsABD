# Auditoría del sistema — MarathonSportsABD

**Fecha:** 2026-08-25
**Alcance:** solo lectura. No se modificó ningún archivo de código, no se ejecutaron migraciones ni semillas, no se escribió en la base de datos.
**Método:** lectura del código fuente + consultas `SELECT` contra el motor PostgreSQL en ejecución (catálogo del sistema y tablas de negocio). El esquema de este documento está leído del motor, **no** del README ni de los `.sql`.

> **Convención de este documento.** Todo lo que aparece aquí sin la etiqueta `[NO VERIFICADO]` fue comprobado leyendo el código o consultando el motor. Lo que no se pudo comprobar lleva esa etiqueta explícita y dice por qué.

---

## 1. Mapa real del sistema

### 1.1 Stack

| Capa | Tecnología | Versión | Evidencia |
|---|---|---|---|
| Backend | Java + Spring Boot | Java 17, Spring Boot 3.2.2 | `marathon-backend/pom.xml:12,21` |
| Gestor de paquetes (back) | Maven (`spring-boot-maven-plugin`) | — | `marathon-backend/pom.xml` |
| Persistencia | Spring Data JPA / Hibernate | `ddl-auto=validate` | `application.properties:31` |
| Seguridad | Spring Security + JWT (jjwt 0.11.5) | — | `pom.xml:23`, `config/SecurityConfig.java` |
| Motor de BD | **PostgreSQL 18.3** (x86_64-windows) | base `mod_venta_inve` | `select version()` contra el motor |
| Frontend | Angular standalone | 17.3 | `marathon-frontend/package.json` |
| Gestor de paquetes (front) | npm | — | `package.json` |
| Extras back | springdoc-openapi 2.3.0, iText 7.2.5, Apache POI 5.2.3, WebFlux (cliente Anthropic) | — | `pom.xml` |
| Extras front | chart.js 4.5.1, xlsx 0.18.5 | — | `package.json` |

**Cómo se corre.** Hay tres caminos y **no coinciden entre sí**:

1. `docker-compose.yml` — levanta Postgres 15 + backend + frontend. Usa `POSTGRES_USER: postgres` y `DB_PASSWORD`. **Contradice** al resto del proyecto, que se conecta como `usr_admin_marathon` contra Postgres 18 en el puerto **5433**.
2. `application.properties` (versionado) — apunta a `localhost:5432`, usuario por defecto `postgres`, contraseña por defecto `1234`, `sslmode=verify-full` con certificado en `C:/ProgramData/MarathonSports/tls/server.crt`.
3. `application-local.properties` (ignorado por git, presente en este equipo) — es el que manda: `localhost:5433`, `usr_admin_marathon`, seis pools por rol activados (`app.datasource.roles.enabled=true`).

El arranque real documentado pasa por `scripts/cifrado/iniciar_backend.ps1`, que fija `MARATHON_CRYPTO_KEY` descifrando un almacén DPAPI antes de lanzar la aplicación.

`spring.jpa.hibernate.ddl-auto=validate`: **la aplicación no crea ni migra el esquema**. El esquema lo construyen los scripts de `marathon-backend/sql/` a mano, en orden de fase. No hay Flyway ni Liquibase.

### 1.2 Estructura de carpetas

```
MarathonSportsABD/
├── marathon-backend/
│   ├── pom.xml
│   ├── sql/                 39 scripts fase00…fase43 (DDL, seeds, roles, cifrado, índices)
│   └── src/main/
│       ├── java/com/marathon/
│       │   ├── config/      SecurityConfig, JwtUtils, JwtAuthenticationFilter,
│       │   │                DataInitializer, RoleRoutingDataSource (un pool por rol),
│       │   │                ClaveCifradoDataSource
│       │   ├── controller/  33 controladores REST
│       │   ├── dto/         ~130 DTOs agrupados por módulo
│       │   ├── exception/   GlobalExceptionHandler + ErrorResponse + 2 excepciones
│       │   ├── model/       36 entidades JPA
│       │   ├── repository/  37 repositorios Spring Data
│       │   └── service/     38 servicios
│       └── resources/       application{,.-dev,-local}.properties
├── marathon-frontend/
│   └── src/app/
│       ├── core/            guards (auth, rol, permiso), interceptor, api/auth/crud services
│       ├── modules/         ~45 componentes standalone, uno por pantalla
│       └── shared/          navbar, icon, searchable-select
├── sql/                     1 script de administración de usuarios/roles
├── scripts/                 backup/, cifrado/, migracion/  (PowerShell)
├── docs/                    ← esta auditoría
└── *.md (17 documentos en la raíz)
```

**Observación estructural:** no existe `marathon-backend/src/test`. **El proyecto no tiene ni una sola prueba automatizada**, pese a que `spring-boot-starter-test` está declarado en el `pom.xml`.

### 1.3 Esquema de base de datos real

38 tablas en `public`. Leídas del catálogo del motor.

**Catálogos y maestros:** `ciudad`, `categoria`, `unidad_medida`, `bodega`, `producto`, `materia_prima`, `proveedor`, `cliente`, `producto_proveedor`

**Seguridad:** `usuario`, `rol`, `permiso`, `usuario_rol`, `rol_permiso`

**Inventario:** `inventario`, `movimiento_inventario`, `historial_inventario`, `movimiento_materia_prima`

**Venta:** `pedido`, `detalle_pedido`, `comprobante_interno`

**Compra:** `orden_compra`, `orden_compra_detalle`, `recepcion_mercancia`, `recepcion_mercancia_detalle`, `factura_compra`, `cuenta_por_pagar`, `pago_proveedor`

**Devoluciones:** `solicitud_devolucion`, `solicitud_devolucion_detalle`, `reembolso_cliente`, `devolucion_proveedor`, `devolucion_proveedor_detalle`

**Manufactura:** `lista_materiales` (BOM), `orden_produccion`, `orden_produccion_consumo`

**Bitácora:** `log_accion`, `auditoria_cambios`

#### Integridad declarada en el motor

Esta es la parte **más sólida** del proyecto. El motor tiene defensas reales:

- **~60 constraints CHECK.** Cubren estados (`chk_pedido_estado`, `chk_oc_estado`, `chk_op_estado`, `chk_sd_estado`, `chk_dp_estado`, `chk_fc_estado`, `chk_cxp_estado`), signos de dinero y cantidades (`chk_detalle_precio > 0`, `chk_inventario_stock_actual >= 0`, `chk_cxp_montos: monto_pagado <= monto_total`), exclusividad de origen (`chk_oc_detalle_item_exclusivo`, `chk_dpd_origen_exclusivo`), coherencia de fechas (`chk_fc_vencimiento`), formato de correo (`chk_usuario_correo`) y longitud de hash bcrypt (`chk_usuario_password_longitud >= 60`).
- **UNIQUE de negocio:** `uq_inventario_producto_bodega`, `uq_comprobante_numero`, `uq_fc_numero_proveedor`, `uq_bom_producto_materia`, `uq_opc_orden_materia`, `uq_dpd_sdd`, `uq_dpd_recepcion`, `uq_usuario_rol`, `uq_rol_permiso`.
- **30 triggers.** Tres familias:
  - *Recálculo de totales* (a nivel de sentencia, con tablas de transición): `fn_recalcular_total_pedido_stmt`, `fn_recalcular_total_orden_compra_stmt`, `fn_recalcular_monto_pagado_cxp`.
  - *Protección de campos calculados*: `fn_proteger_total_pedido`, `fn_proteger_total_orden_compra`, `fn_proteger_monto_pagado_cxp`, `fn_proteger_costo_materia_prima_op`. Levantan excepción si alguien intenta escribir un total a mano con un valor distinto del calculado.
  - *Auditoría y trazabilidad*: `fn_auditoria_cambios` (sobre `cliente`, `producto`, `proveedor`, `usuario`, `rol_permiso`), `fn_trg_historial_inventario`, `fn_set_updated_at`.
- **Claves foráneas con política explícita.** Predomina `ON DELETE RESTRICT` (protege `producto`, `cliente`, `rol`, `categoria`, `unidad_medida`). `ON DELETE CASCADE` solo en `lista_materiales`, `producto_proveedor` y `rol_permiso`.
- **Cifrado en reposo** de datos de contacto (`cliente.correo_enc`, columnas equivalentes en `proveedor`), con trigger `fn_cliente_sincronizar_hash` que mantiene un hash para búsqueda.

**Conclusión sobre la BD: el modelo de datos está bastante por encima del código que lo usa.** Buena parte de los errores del backend no llegan a corromper la base porque un CHECK los frena — pero llegan al usuario como un HTTP 500, no como un mensaje útil.

#### Volumen actual

| Tabla | Filas |
|---|---|
| `pedido` | 229 999 |
| `log_accion` | 200 000 |
| `historial_inventario` | 60 000 |
| `movimiento_inventario` | 80 000 |
| `comprobante_interno` | 30 000 |
| `inventario` | 2 000 |
| `pago_proveedor` | 2 000 |
| `producto` | 108 |
| `usuario` / `rol` | 6 / 6 |
| `permiso` | 49 |
| **`auditoria_cambios`** | **0** |
| `reembolso_cliente` | 1 |

Los volúmenes altos vienen de los scripts de poblado masivo (fase38/39/43), no de uso real de la aplicación. Esto importa para leer los datos: **hay invariantes que la aplicación viola y que los datos no delatan, porque los datos no los produjo la aplicación** (ver §2.2).

### 1.4 Endpoints y quién los protege

**La autorización está centralizada en `config/SecurityConfig.java`.** No hay ni un solo `@PreAuthorize` en los 33 controladores (verificado: `grep -c "@PreAuthorize" controller/*.java` = 0 en todos). Todo se decide por `requestMatchers` en la cadena de filtros, con `.anyRequest().authenticated()` como red final.

Hay además una **segunda capa real**: desde la fase 37 cada rol de aplicación abre su propio pool contra PostgreSQL con un usuario distinto (`usr_bodega_marathon`, `usr_pedidos_marathon`, …), de modo que los `GRANT` del motor tienen la última palabra. `GlobalExceptionHandler` traduce el `SQLSTATE 42501` a un 403.

| Base | Endpoints | Protección declarada en `SecurityConfig` |
|---|---|---|
| `/api/auth/**` | login, refresh, logout | **público** (`permitAll`) |
| `/swagger-ui/**`, `/v3/api-docs/**` | — | **público** |
| `/api/ciudades`, `/categorias`, `/unidades-medida` | 5 c/u | GET autenticado; POST/PUT/DELETE solo Administrador |
| `/api/productos` | 5 | GET autenticado; escritura solo Administrador |
| `/api/productos/{id}/bom`, `/costo-estimado`, `/origen` | 4 (`BomController`) | GET Admin+Producción+Compras; PUT Admin+Producción |
| `/api/bodegas` | 6 | GET autenticado; escritura solo Administrador |
| `/api/inventario` | 6 | GET autenticado; `POST /movimiento` Admin+Bodega |
| `/api/proveedores` | 5 | GET Admin+Supervisor+Compras; escritura solo Administrador |
| `/api/clientes` | 6 | GET Admin+Supervisor+Bodega+Pedidos; escritura Admin+Pedidos |
| `/api/pedidos` | 5 | GET los 4 del circuito de venta; POST Admin+Pedidos; `PUT /{id}/estado` Admin+Bodega+Pedidos |
| `/api/comprobantes` | 6 | GET circuito de venta; generar Admin+Pedidos; anular solo Administrador |
| `/api/picking/**` | 4 | Admin+Bodega |
| `/api/empaque/**` | 2 | confirmar Admin+Bodega; GET circuito de venta |
| `/api/usuarios/**`, `/roles/**`, `/permisos/**` | 6/5/1 | solo Administrador — **salvo `PUT /api/usuarios/*/password`, declarado `.authenticated()`** |
| `/api/logs/**`, `/api/auditoria/**` | 2 + 2 | solo Administrador |
| `/api/ordenes-compra/**` | 4 | Admin+Compras |
| `/api/recepciones/**` | 2 | Admin+Compras |
| `/api/facturas-compra/**` | 4 | Admin+Compras |
| `/api/cuentas-por-pagar/**` | 3 | GET Admin+Compras+Supervisor; resto Admin+Compras |
| `/api/pagos-proveedor/**` | 2 | Admin+Compras |
| `/api/devoluciones/**` | 6 | GET circuito de venta; crear Admin+Pedidos; inspección Admin+Bodega; reembolso Admin+Pedidos |
| `/api/devoluciones-proveedor/**` | 6 | Admin+Compras |
| `/api/materia-prima/**` | 9 | GET Admin+Compras+Producción; escritura Admin+Producción |
| `/api/ordenes-produccion/**` | 7 | GET Admin+Producción+Supervisor; escritura Admin+Producción |
| `/api/analisis-costos/**` | 2 | Admin+Supervisor+Producción |
| `/api/reportes/manufactura/**` | 6 | Admin+Supervisor+Producción |
| `/api/reportes/**` | 12 | Admin+Supervisor |
| `/api/dashboard/**` | 6 | Admin+Supervisor (salvo `/manufactura`, que suma Producción) |
| `/api/ia/**` | 2 | Admin+Supervisor |

**El reparto por rol está bien pensado y el orden de las reglas es correcto** (las específicas anteceden a las generales). Los agujeros concretos están en `docs/DEFECTOS.md` — el más relevante es `PUT /api/usuarios/{id}/password` (D-09).

**El modelo de permisos no se usa.** Existen 49 filas en `permiso`, la tabla `rol_permiso`, un `PermisoController`, un claim `permisos` en el JWT, un array `permisos` en la respuesta de login, un `permisoGuard` en el frontend y un `hasPermiso()` en `AuthService`. **Ninguna decisión de autorización los consulta**: `SecurityConfig` solo usa `hasAuthority("ROLE_…")`, y `permisoGuard` no está referenciado por ninguna ruta (verificado: `grep -rn "permisoGuard" src/` solo encuentra su propia definición). Es un subsistema completo construido y desconectado (D-13).

### 1.5 Pantallas del frontend y a qué llaman

45 componentes standalone, todos con carga diferida. Todas las rutas salvo `login` pasan por `authGuard`; casi todas suman `rolGuard`.

| Pantalla | Ruta | Endpoint principal | Roles (guard) |
|---|---|---|---|
| Login | `/login` | `POST /api/auth/login` | — |
| Portal / Dashboard | `/portal`, `/dashboard` | `/api/dashboard/kpis`, `/ventas-por-dia`, `/pedidos-por-estado`, `/top-productos`, `/movimientos-hoy` | autenticado |
| Perfil | `/perfil` | `PUT /api/usuarios/{id}/password` | autenticado |
| Datos maestros (3 pestañas) | `/datos-maestros/*` | `/api/ciudades`, `/categorias`, `/unidades-medida` | Administrador |
| Usuarios / Roles | `/usuarios`, `/roles` | `/api/usuarios`, `/api/roles` | Administrador |
| Productos / Proveedores / Bodegas | `/productos`, `/proveedores`, `/bodegas` | catálogos respectivos | Administrador |
| Inventario | `/inventario` | `/api/inventario`, `POST /movimiento` | Admin, Bodega, Supervisor, Compras |
| Clientes | `/clientes` | `/api/clientes` | Admin, Pedidos, Supervisor |
| Pedidos (lista / nuevo / detalle / especiales) | `/pedidos*` | `/api/pedidos` | Admin, Supervisor, Pedidos, Bodega |
| Comprobantes | `/comprobantes` | `/api/comprobantes`, `/{id}/pdf` | circuito de venta |
| Picking (lista / ejecución) | `/picking*` | `/api/picking/pedidos`, `PUT /lineas` | Admin, Bodega |
| Empaque / Despachos | `/empaque`, `/despachos` | `/api/empaque/pedidos`, `POST /confirmar` | Admin, Bodega (+Supervisor en despachos) |
| Compras (lista / nueva / detalle / recepción / factura) | `/compras*` | `/api/ordenes-compra`, `/recepciones`, `/facturas-compra` | Admin, Compras |
| Cuentas por pagar | `/cuentas-por-pagar*` | `/api/cuentas-por-pagar`, `/api/pagos-proveedor` | Admin, Compras, Supervisor |
| Materia prima / Kardex | `/materia-prima*` | `/api/materia-prima`, `/{id}/movimientos` | Admin, Producción, Compras |
| Devoluciones cliente (3) | `/devoluciones*` | `/api/devoluciones` | circuito de venta |
| Devoluciones proveedor (3) | `/devoluciones-proveedor*` | `/api/devoluciones-proveedor` | Admin, Compras |
| Producción (lista/nueva/detalle/costos/dashboard) | `/produccion*` | `/api/ordenes-produccion`, `/analisis-costos`, `/dashboard/manufactura` | Admin, Producción (+Supervisor en costos/dashboard) |
| Reportes | `/reportes` | `POST /api/reportes/*/preview|excel|pdf` | Admin, Supervisor |
| Asistente IA | `/ia` | `POST /api/ia/consultar` | Admin, Supervisor |
| Auditoría | `/auditoria` | `/api/auditoria/inventario`, `/inventario/resumen` | Administrador |

El interceptor `auth.interceptor.ts` inyecta el `Bearer` en todas las llamadas salvo login/refresh, y ante un 401 intenta un refresh automático una sola vez.

---

## 2. Flujos de negocio, de punta a punta

Leyenda: **completo** = se puede recorrer entero y hace lo que debe · **a medias** = se recorre pero deja algo mal, o le falta un tramo · **roto** = no se puede completar.

### 2.1 Resumen

| # | Flujo | Estado | Dónde se corta |
|---|---|---|---|
| 1 | Catálogo / producto | **completo** | — (baja lógica correcta) |
| 2 | Inventario / stock — movimientos manuales | **a medias** | `traslado` **roto**; `ajuste` desvirtúa el kardex |
| 3 | Compra a proveedor (OC → recepción) | **completo** | única laguna: no se puede editar una OC en borrador |
| 4 | Factura de compra → CxP → pago | **completo** | — |
| 5 | Venta / pedido | **a medias** | precio lo pone el cliente; no valida ni reserva stock |
| 6 | Picking | **a medias** | es una lista de verificación, no un picking: no registra bodega ni valida stock |
| 7 | Empaque / despacho (descuento de stock) | **roto** (corrompe datos) | `EmpaqueService.java:99-124` |
| 8 | Facturación (comprobante interno) | **a medias** | sin control de estado; anular deja el pedido sin poder facturarse nunca más |
| 9 | Devolución de cliente (RMA) | **a medias** | el monto del reembolso no se valida contra nada |
| 10 | Devolución a proveedor | **completo** | — |
| 11 | Producción (BOM → OP → costeo) | **completo** | — |
| 12 | Usuarios / roles / permisos | **a medias** | desactivar un usuario no lo desconecta; los permisos no se aplican |
| 13 | Auditoría / bitácora | **a medias** | `auditoria_cambios` vacía; el usuario se pierde en varios caminos |

### 2.2 Detalle por flujo

---

#### 1. Catálogo / producto — **completo**

Alta, edición, listado paginado con filtros y baja **lógica** (`ProductoService.java:207`, `estado='inactivo'`). Respeta el `ON DELETE RESTRICT` del motor. `BomController` añade el origen (`comprado`/`fabricado`) y la lista de materiales, con un trigger que impide asignar BOM a un producto comprado.

Laguna menor: al añadir una línea de pedido **no se comprueba que el producto esté activo** (`PedidoService.java:160-165`), así que un producto dado de baja se sigue pudiendo vender (D-24).

---

#### 2. Inventario / stock (movimientos manuales) — **a medias**

`InventarioService.registrarMovimiento` (`:95-180`) es de lo mejor escrito del backend: valida existencia de producto/bodega/usuario, **comprueba stock antes de una salida y lanza `ValidationException`** en vez de dejar el saldo en negativo, y fija `app.current_user_id` para que el trigger de historial registre a la persona.

Dónde se corta:

- **`traslado` está roto de raíz.** El código descuenta del origen y suma al destino (`:128-155`), pero al construir el `MovimientoInventario` **nunca asigna `inventarioDestino`**. Verificado: `setInventarioDestino` no se invoca en ninguna parte del proyecto. La tabla tiene `chk_traslado_requiere_destino` (`tipo_movimiento <> 'traslado' OR id_inventario_destino IS NOT NULL`), así que el `INSERT` revienta. **Qué pasa hoy si un usuario lo intenta:** la transacción hace rollback y recibe un HTTP 500 con el texto crudo de la excepción de PostgreSQL. El traslado nunca ocurre. Las 6 134 filas de traslado que hay en la base salieron de los scripts de poblado, no de la aplicación — por eso el defecto no se nota mirando los datos (D-35).
- **`ajuste` desvirtúa el kardex.** El caso `ajuste` (`:125-127`) fija el stock a un valor **absoluto**, pero el movimiento se graba con `mov.setCantidad(dto.getCantidad())` (`:167`), es decir, el mismo número interpretado como si fuera un **delta**. Sumar los movimientos ya no reconstruye el saldo.
- Lectura y escritura de `stock_actual` en dos pasos, sin bloqueo pesimista: dos movimientos concurrentes sobre el mismo `inventario` se pisan.

---

#### 3. Compra a proveedor: OC → recepción — **completo**

El mejor flujo del sistema.

- `OrdenCompraService.cambiarEstado` (`:193-255`) implementa una máquina de estados real: `borrador → pendiente_aprobacion → aprobada|rechazada`, con `cancelada` alcanzable solo desde los tres primeros. Cada transición exige el rol adecuado **y** aplica **separación de funciones**: quien solicitó la orden no puede aprobarla (`:225-229`). Cancelar valida que no haya recepciones previas (`:262-270`).
- `RecepcionMercanciaService.crear` (`:81-…`) valida **todas** las líneas antes de persistir nada, acumula `cantidad_recibida` sin sobrescribir, separa cantidad buena de defectuosa, y recalcula el **costo unitario promedio ponderado** de la materia prima antes de sumar el stock. Fija `app.current_user_id` una vez por transacción, como debe.

Única laguna: **no hay endpoint para modificar una OC**. `OrdenCompraController` expone `GET`, `GET /{id}`, `POST` y `PUT /{id}/estado`. Una orden creada en `borrador` con una línea mal puesta solo se puede cancelar y rehacer.

---

#### 4. Factura de compra → cuenta por pagar → pago — **completo**

`FacturaCompraService` crea la factura contra la OC recibida; la CxP se genera con `UNIQUE (id_factura_compra)`; `PagoProveedorService` registra pagos y el trigger `fn_recalcular_monto_pagado_cxp` recalcula `monto_pagado` a nivel de sentencia, con `fn_proteger_monto_pagado_cxp` impidiendo escribirlo a mano y el CHECK `monto_pagado <= monto_total` cerrando la puerta al sobrepago. El dinero aquí lo custodia la base, y lo hace bien.

---

#### 5. Venta / pedido — **a medias**

`PedidoService.crear` (`:131-…`) valida cliente existente y activo, exige al menos un detalle (`@NotEmpty`), cantidad `>= 1` y precio `>= 0.01`. El total lo calcula el trigger `fn_recalcular_total_pedido_stmt` desde los subtotales, y `fn_proteger_total_pedido` impide falsearlo.

Dónde se corta:

- **El precio lo pone quien llama.** `detalle.setPrecioUnitario(item.getPrecioUnitario())` (`:165`) toma el precio del cuerpo de la petición y **nunca consulta `producto.precio`**. El trigger calculará fielmente el total a partir de un precio inventado. **Qué pasa hoy:** un `POST /api/pedidos` con `precioUnitario: 0.01` sobre un producto de $200 crea un pedido válido de $0.01, con su comprobante y su descuento de stock. No hay ninguna capa que lo impida (D-34).
- **No se comprueba ni se reserva stock.** Crear un pedido no mira el inventario. Se pueden crear 100 pedidos de un producto con 3 unidades. El choque con la realidad se pospone hasta el despacho, donde (ver flujo 7) tampoco se detiene (D-02).
- `descuento` no tiene cota superior ni validación. Un descuento mayor que el subtotal no da error: el trigger aplica `GREATEST(..., 0)` y el pedido queda en total 0 (D-19).
- `cambiarEstado` (`:207`) escribe en la bitácora con **usuario `null`**: la transición de estado queda registrada sin autor.

La máquina de estados (`:213-240`) sí está bien: `pendiente → procesado → enviado → entregado`, con `anulado` alcanzable solo desde los dos primeros, sin saltos, y con la exigencia añadida de picking completo para pasar a `enviado`.

---

#### 6. Picking — **a medias**

`PickingService.actualizarLinea` (`:68-104`) valida que la línea pertenezca al pedido, que el pedido esté en `procesado`, que la cantidad recogida no supere la pedida, y —bien hecho— **no se fía del booleano que manda el cliente**: recalcula `pickingCompletado` comparando cantidades.

Dónde se corta: **el picking no registra de qué bodega salió la mercancía y no consulta el stock.** El modelo solo guarda `cantidad_recogida` y `picking_completado` en `detalle_pedido`. Es una lista de verificación, no un picking contra ubicaciones. Esa ausencia es la que hace imposible que el paso siguiente descuente bien (ver flujo 7).

---

#### 7. Empaque / despacho — **roto, y corrompe datos**

Es el peor punto del sistema. `EmpaqueService.confirmarEmpaque` (`:65-135`) valida bien la entrada (picking completo, pedido en `procesado`) y luego descuenta stock así:

```java
Inventario inv = inventarios.stream()
        .filter(i -> i.getStockActual() != null && i.getStockActual() > 0)
        .findFirst()                       // :101  ← bodega arbitraria
        .orElse(null);
if (inv == null) { continue; }             // :104  ← despacha sin mover stock
int nuevoStock = inv.getStockActual() - cantidad;
if (nuevoStock < 0) { nuevoStock = 0; }    // :110  ← pierde el déficit en silencio
...
mov.setCantidad(cantidad);                 // :122  ← registra la cantidad completa
```

Tres fallos encadenados, y ninguno avisa:

1. **`findFirst()` sobre una lista sin ordenar** elige *cualquier* bodega con existencias, sin relación con dónde se hizo el picking. Se descuenta de la bodega equivocada.
2. **`continue` cuando no hay stock:** si ninguna bodega tiene existencias, la línea se salta. El pedido pasa igualmente a `enviado`, con su HU y su transportista. **Mercancía que sale sin ningún movimiento de inventario.**
3. **Recorte a cero:** con 3 unidades en stock y una línea de 10, el saldo queda en 0 (se pierden 7 unidades de déficit) **pero el movimiento se graba con cantidad 10**. El libro de movimientos y el saldo quedan permanentemente descuadrados, y nada lo señala.

A esto se suma que la lectura y escritura de `stock_actual` no está bloqueada: dos despachos simultáneos del mismo producto se pisan.

**Qué pasa hoy si un usuario lo intenta:** el despacho "funciona" — devuelve 200, el pedido queda enviado, la pantalla no muestra ningún error. El daño solo se ve después, al cuadrar inventario.

Como referencia del estado de los datos: de 186 737 pedidos en `enviado`/`entregado`, **173 972 no tienen ningún movimiento de inventario asociado**, y hay 498 903 líneas despachadas sin su salida correspondiente. `[NO VERIFICADO]` — no puedo atribuir esas cifras al defecto: casi con certeza vienen de los scripts de poblado masivo, que insertaron pedidos sin generar movimientos. Lo que sí queda demostrado es que **no existe ninguna restricción ni conciliación que obligue a que un pedido despachado tenga sus movimientos**, y que cualquier informe construido sobre el kardex parte de datos que ya no cuadran.

---

#### 8. Facturación (comprobante interno) — **a medias**

`ComprobanteService.generarComprobante` (`:54-77`) copia `pedido.total` al comprobante y el trigger `fn_validar_total_comprobante` verifica que coincida exactamente. Esa parte es correcta.

Dónde se corta:

- **No comprueba el estado del pedido.** Se puede emitir el comprobante de un pedido `pendiente`, o incluso `anulado`. La facturación no está atada al ciclo de la venta (D-11).
- **Anular deja el pedido sin salida.** `anular` (`:105-115`) solo pone `estado='anulado'`, sin tocar nada más. Pero `generarComprobante` bloquea con `findByPedidoIdPedido(idPedido).isPresent()` (`:58`), **sin filtrar por estado**. Una vez anulado el comprobante, ese pedido **no puede volver a facturarse nunca**. El flujo de corrección de una factura no existe (D-06).
- **La numeración usa `count() + 1`** (`:123`). Dos emisiones concurrentes obtienen el mismo número y la segunda choca con `uq_comprobante_numero` → HTTP 500. Además no es una numeración fiscal correcta: no es continua por año ni resistente a borrados (D-07).

---

#### 9. Devolución de cliente (RMA) — **a medias**

Es un flujo bien construido: `solicitada → en_inspeccion → completada|rechazada`, con inspección línea a línea, resultado por línea (`apto_reventa` / `defectuoso` / `rechazado`), rechazo de doble inspección (`:209-211`), reingreso de stock solo para lo apto (`:217-219`) con su movimiento de entrada, cierre automático cuando todas las líneas tienen resultado, y bloqueo de reembolso duplicado (`:252-254`).

Dónde se corta: **`registrarReembolso` no valida el importe contra nada** (`:261`). El `monto` llega del cuerpo de la petición y se guarda tal cual. No se contrasta contra la suma de `cantidad_devuelta × precio_unitario` de las líneas aptas, ni contra el total del pedido. **Qué pasa hoy:** un reembolso de $10 000 sobre una devolución de $50 se acepta sin objeción (D-08).

---

#### 10. Devolución a proveedor — **completo**

Estados `pendiente → enviada → resuelta|rechazada`, con resolución por reembolso o reposición, origen exclusivo garantizado por CHECK (`chk_dpd_origen_exclusivo`: o viene de un RMA de cliente o de una recepción de compra, nunca de ambos) y `UNIQUE` por línea de origen que impide devolver dos veces el mismo ítem.

---

#### 11. Producción (BOM → orden → costeo) — **completo**

`lista_materiales` con trigger que impide asignar BOM a productos comprados; `orden_produccion` con `planificada → en_proceso → completada|cancelada`; verificación previa de disponibilidad de materiales; consumo real por materia prima con `UNIQUE (orden, materia)`; costeo con `fn_set_costo_materia_prima_op` y `fn_proteger_costo_materia_prima_op` para que el costo no se escriba a mano. Es el módulo más completo después de compras.

---

#### 12. Usuarios / roles / permisos — **a medias**

Alta, edición, baja **lógica** (`estado='inactivo'`), protección del admin principal, contraseñas con bcrypt (verificado en la base: los 6 usuarios tienen hash `$2a$10$` de 60 caracteres, y hay un CHECK que lo exige).

Dónde se corta:

- **Desactivar un usuario no lo desconecta.** `JwtAuthenticationFilter` (`:50`) autentica con `jwtUtils.isTokenValid(jwt, userDetails)`, y ese método (`JwtUtils.java:90-93`) comprueba **solo** que el nombre coincida y que el token no haya expirado. `Usuario.isEnabled()` existe (`model/Usuario.java:83`, devuelve `"activo".equals(estado)`) pero **el filtro nunca lo llama**. `AuthService.refresh` tampoco. Resultado: un usuario dado de baja sigue operando con su token hasta 24 h, y como el refresh tampoco valida el estado, puede renovarlo indefinidamente mientras lo haga dentro de la ventana de 7 días. **La única vía documentada para retirar el acceso no retira el acceso** (D-05).
- **Los permisos no se aplican en ninguna parte** (ver §1.4). 49 permisos, tabla `rol_permiso`, guard, claim JWT — todo decorativo (D-13). Nota adicional: el rol `Encargado de Producción` tiene **0 permisos asignados** en la base y aun así opera con normalidad, lo que confirma que nada los consulta.
- `PUT /api/usuarios/{id}/password` está declarado `.authenticated()` y `UsuarioService.cambiarPassword` (`:133`) **nunca comprueba que `id` sea el del usuario autenticado** (D-09).
- `logout` es un no-op: con JWT sin lista de revocación, el token sigue siendo válido tras cerrar sesión.
- `RolService.eliminar` (`:128-137`) borra roles **físicamente**, carga toda la tabla `usuario_rol` en memoria con `findAll()` para comprobar si el rol está en uso, y arrastra una variable muerta marcada `// dummy` en la línea 128.

---

#### 13. Auditoría y bitácora — **a medias**

Hay dos mecanismos: `log_accion` (bitácora de aplicación, 200 000 filas) y `auditoria_cambios` (traza genérica por triggers sobre `cliente`, `producto`, `proveedor`, `usuario`, `rol_permiso`).

Dónde se corta:

- **`auditoria_cambios` está vacía: 0 filas.** La pantalla de auditoría (`AuditoriaController`) lee en realidad `historial_inventario`, que sí tiene datos, así que la pantalla no parece rota — pero la traza genérica de la fase 40 no ha registrado nada.
- **El usuario se pierde en varios caminos.** El propio `LogService.fijarContextoUsuario()` documenta la regla: *«Debe llamarse antes de la escritura que dispara el trigger, y dentro de la misma transacción: `SET LOCAL` muere en el commit»*. Pero `ProductoService.eliminar` (`:207-208`) y los métodos de `UsuarioService` **no llevan `@Transactional`**, así que el `SET LOCAL` se ejecuta en su propia transacción autocommit y ya no está vigente cuando llega el `UPDATE` que dispara el trigger. El código incumple su propio contrato documentado (D-16).
- `PedidoService.cambiarEstado` registra explícitamente `null` como usuario (`:207`).
- `LogService.registrar` se traga cualquier excepción a propósito: si la bitácora falla, falla en silencio.

---

## 3. Qué no tiene el proyecto y debería

### 3.1 Pruebas — **no hay ninguna**

No existe `marathon-backend/src/test`. Cero pruebas unitarias, cero de integración, cero de API. El frontend tampoco tiene specs. `spring-boot-starter-test` está en el `pom.xml` sin usar. Es la carencia que explica casi todo lo demás: nada de lo anterior (el traslado roto, el recorte de stock, el reembolso sin tope) habría sobrevivido a una prueba de flujo.

### 3.2 Validación

Los DTO de entrada sí llevan Bean Validation y los controladores usan `@Valid` — mejor de lo esperado. Lo que falta es la capa de arriba: **validación de reglas de negocio**. Un `@DecimalMin("0.01")` sobre el precio no sirve de nada si el precio no debería venir del cliente. Faltan de forma sistemática: comprobación de estado del recurso relacionado antes de operar, topes de importe contrastados contra el documento origen, y verificación de que el recurso pertenece a quien lo pide.

### 3.3 Manejo de errores

`GlobalExceptionHandler` existe y cubre lo importante (404, 400, 403 de Spring y 403 traducido del `SQLSTATE 42501`). Le falta:

- **No filtra el mensaje interno.** `handleGeneral` (`:124`) devuelve `"Error interno del servidor: " + ex.getMessage()`, que en un fallo de base de datos expone SQL, nombres de constraints y a veces valores. Sumado a que `IAService` devuelve el error crudo de la consulta, hay un oráculo de errores SQL completo (D-12).
- **Faltan manejadores** para `DataIntegrityViolationException` (un nombre de producto duplicado da 500 en vez de 409), `HttpMessageNotReadableException` (JSON mal formado → 500) y `ConstraintViolationException`.

### 3.4 Control de acceso

Lo que hay está bien planteado (autorización centralizada, doble capa con los `GRANT` del motor). Lo que falta:

- Comprobación de **propiedad del recurso** (el caso de la contraseña, D-09).
- Que **desactivar un usuario surta efecto** (D-05).
- **Aplicar los permisos**, o retirar el subsistema (D-13).
- Límite de intentos de login. Hoy no hay ninguno, y `login` distingue «Usuario inactivo» de «Credenciales incorrectas», lo que además delata qué cuentas existen.
- Revocación de tokens (lista de denegación o tokens de vida corta).

### 3.5 Semillas de datos

`DataInitializer` crea los 6 roles, 49 permisos y 6 usuarios demo con **contraseñas fijas en el código fuente versionado**: `Admin1234!` (`:116`) y `Demo1234!` (`:237`). Las 6 cuentas están activas en la base. Para una demo académica es defendible; para cualquier despliegue real es una vía de entrada directa, y combinado con el secreto JWT por defecto (`defaultDevSecretChangeInProduction`, también versionado en `application.properties:37`) permite forjar un token de administrador sin credenciales.

El resto de las semillas está en 39 scripts `.sql` numerados por fase, que hay que ejecutar **en orden y a mano**.

### 3.6 Documentación de despliegue

Hay 17 documentos en la raíz (`SETUP_COMPLETO.md`, `CONFIGURACION_BD.md`, `MANUAL.md`, `ESTRATEGIA_RESPALDO.md`, `GUIA_REPLICACION.md`, `CIFRADO.md`, `DEUDA_TECNICA.md`…) y un `MANUAL.html`. **Documentación no falta; falta que sea consistente.** Los tres caminos de arranque descritos en §1.1 se contradicen entre sí, y `docker-compose.yml` describe un despliegue (Postgres 15, usuario `postgres`, puerto 5432) que no es el que el proyecto usa realmente (Postgres 18, `usr_admin_marathon`, puerto 5433, TLS `verify-full`, clave de cifrado desde DPAPI).

Y sobre todo: **el frontend no es desplegable**. `environment.prod.ts` apunta a `http://localhost:8080/api` — la misma URL que el entorno de desarrollo, y en texto plano (D-10).

### 3.7 Migraciones

No hay herramienta de migración. `ddl-auto=validate` (correcto: la aplicación no toca el esquema), pero la evolución del esquema depende de ejecutar 39 scripts en el orden correcto, sin control de versión aplicado, sin idempotencia garantizada y sin vuelta atrás. Flyway o Liquibase resolvería esto y haría reproducible el «construir desde cero» que ya ocupa varios commits recientes.

---

## 4. Lectura de conjunto

Este proyecto no es un esqueleto a medio hacer: es un sistema **grande y sorprendentemente completo** con un puñado de agujeros muy concretos y muy profundos.

**Lo que está bien** y conviene no tocar: el modelo de datos (60 CHECK, 30 triggers, FK con política explícita, cifrado en reposo), el flujo de compras completo con separación de funciones, el de producción, el de devoluciones a proveedor, la autorización centralizada con doble capa contra los `GRANT` del motor, y la mayor parte de la validación de entrada.

**Lo que está mal** se concentra en el eje de la venta —pedido → picking → empaque → comprobante— que es justamente el flujo principal del negocio y el único donde el dinero y el stock se calculan en Java en vez de dejárselo a la base. Ahí es donde están los tres defectos que corrompen datos en silencio.

El patrón de fondo se repite: **cuando el cálculo lo hace la base de datos, está bien; cuando lo hace el código Java, está mal.** Y no hay una sola prueba que lo hubiera delatado.

El inventario completo de defectos, con severidad y evidencia, está en [`docs/DEFECTOS.md`](DEFECTOS.md).
