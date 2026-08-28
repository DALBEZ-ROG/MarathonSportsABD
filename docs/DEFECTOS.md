# Inventario de defectos — MarathonSportsABD

**Fecha:** 2026-08-25 · **Fase:** auditoría de solo lectura · **Mapa del sistema:** [`AUDITORIA.md`](AUDITORIA.md)

Todas las rutas de archivo son relativas a `marathon-backend/src/main/java/com/marathon/` salvo indicación contraria.

## Escala de severidad

| | Significado |
|---|---|
| **S1** | Corrompe o pierde datos |
| **S2** | Bloquea un flujo o expone datos |
| **S3** | Incorrecto pero tolerable |
| **S4** | Cosmético |

## Resumen

| Severidad | Nº | IDs |
|---|---|---|
| **S1** | 7 | D-01, D-02, D-03, D-04, **D-34**, **D-39**, **D-42** |
| **S2** | 14 | D-05 … D-13, **D-35**, **D-36**, **D-38**, **D-40**, **D-41** |
| **S3** | 17 | D-14 … D-28, **D-37**, **D-43** |
| **S4** | 5 | D-29 … D-33 |
| | **43** | |

## Estado a 2026-08-27

| | Nº | Cuáles |
|---|---|---|
| **Cerrados** | 39 | los 30 de los 17 lotes; D-02 (F47), D-13 (F48), D-37 y D-38 del repaso de flujos; **D-39 (F49)**, que apareció al levantar la aplicación; y **D-40 a D-43**, que aparecieron depurando la interfaz en el navegador |
| **Mitigados, no cerrados** | 2 | D-23 (ventana de 24 h → 2 h; sigue sin revocación) y D-26 (el secreto JWT ya no arranca por defecto; `datos-demo` sigue en `true`) |
| **Abiertos por decisión** | 1 | D-27 (el JWT sigue en `localStorage`) |
| **Abierto, pendiente de decisión de negocio** | 1 | **D-36**, el importe de la factura de compra |

Los cuatro que no están cerrados lo están **por una decisión tomada y anotada**,
no por olvido. Cada ficha dice quién lo decidió, cuándo y qué haría falta para
cerrarlo.

> **Nota de numeración.** D-34 y D-35 se añadieron el 2026-08-25, después de la primera redacción: estaban descritos en `AUDITORIA.md` (§2.2, flujos 5 y 2) pero se quedaron sin ficha propia. Los ids son cronológicos, no correlativos por severidad; ambos se listan en su sección de severidad correcta. `AUDITORIA.md` §2.2 flujo 5 citaba el del precio como «D-01», que es otro defecto; la referencia está corregida.

---

## S1 — Corrompe o pierde datos

### D-01 · El despacho descuenta stock de una bodega arbitraria, salta líneas sin existencias y recorta el déficit a cero

> **Estado: partes 2 y 3 cerradas en L1 (2026-08-25).** El `continue` y el
> recorte a cero ya no existen: si el stock no cubre la línea, la transacción
> revierte entera y el pedido se queda en `procesado`. El movimiento se graba con
> la cantidad realmente descontada de cada bodega. Cubierto por
> `EmpaqueServiceDespachoTest` (4 pruebas).
>
> **Parte 1 abierta:** la bodega ya se elige de forma determinista y se reparte
> entre varias, pero sigue sin ser *la del picking*, porque el picking no
> registra ese dato (D-14). La cierra L4.

**Evidencia:** `service/EmpaqueService.java:99-124`

```java
Inventario inv = inventarios.stream()
        .filter(i -> i.getStockActual() != null && i.getStockActual() > 0)
        .findFirst()                       // :101
        .orElse(null);
if (inv == null) { continue; }             // :104
int nuevoStock = inv.getStockActual() - cantidad;
if (nuevoStock < 0) { nuevoStock = 0; }    // :110
...
mov.setCantidad(cantidad);                 // :122
```

Tres fallos encadenados en el mismo bucle:

1. `findFirst()` sobre `inventarioRepository.findByProductoIdProducto(idProducto)` —una lista sin ordenar— elige **cualquier** bodega con existencias, sin relación con dónde se hizo el picking (que ni siquiera registra bodega, ver D-14).
2. `continue` cuando ninguna bodega tiene stock: la línea se salta, pero el pedido pasa igualmente a `enviado` con su HU y transportista. **Mercancía despachada sin ningún movimiento de inventario.**
3. Recorte a cero: con 3 unidades en stock y una línea de 10, el saldo queda en 0 —se pierden 7 unidades de déficit— **pero el movimiento se graba con `cantidad = 10`** (`:122`). El libro de movimientos y el saldo quedan descuadrados de forma permanente.

Ninguno de los tres emite aviso alguno. El endpoint devuelve 200.

**Cómo reproducirlo:** producto con stock 3 en la bodega A y 0 en la B; pedido de 10 unidades; completar picking; `POST /api/empaque/pedidos/{id}/confirmar`. El pedido queda `enviado`, `inventario.stock_actual` de A queda en 0, y `movimiento_inventario` registra una salida de 10.

**Qué debería pasar:** el despacho debe descontar de **la bodega desde la que se hizo el picking**; si el stock disponible no cubre la línea, la operación completa debe abortar con un 400 y hacer rollback, nunca completarse a medias. La cantidad del movimiento debe ser exactamente la descontada.

---

### D-02 · Crear un pedido no comprueba ni reserva stock

> **Estado: CERRADO en la F47 (2026-08-27).** Existe `reserva_stock`, y con ella
> las tres respuestas de negocio que faltaban:
>
> - **Cuándo se reserva:** al pasar el pedido de `pendiente` a `procesado`.
>   Crear comprueba el disponible y lo rechaza si no alcanza, pero no retiene
>   nada — hay 16.099 pedidos en `pendiente` y retener ahí bloquearía mercancía
>   por cada pedido abandonado. Excepción: un **pedido especial** se crea aunque
>   no haya stock (se fabrica; existen órdenes de producción para eso) y el
>   déficit queda en la bitácora.
> - **Quién libera:** la anulación libera, el despacho consume. No hay otro
>   camino automático.
> - **Pedido abandonado:** la reserva **vence a los 7 días y sale en un
>   informe** (`GET /api/inventario/reservas/vencidas`, y un aviso en la pantalla
>   de Inventario). **No se libera sola**; la suelta una persona, con motivo.
>
> `disponible(p) = SUM(inventario.stock_actual) − SUM(reservas activas)`. Lo
> respetan además el despacho (no se come la reserva de otro pedido) y los
> movimientos manuales de salida y de ajuste a la baja.
>
> Cubierto por `ReservaStockTest` (12 pruebas). Script:
> `sql/fase47_reserva_stock.sql`.
>
> **Lo que NO se hizo, y se dice:** no se reconstruyeron reservas para los
> 19.058 pedidos que ya estaban en `procesado`. No se sabe cuáles siguen vivos y
> fabricárselas sería inventarse hechos. Durante la transición, el disponible es
> **optimista** respecto de esos pedidos.

**Evidencia (del defecto original):** `service/PedidoService.java:131-180` — `crear()` valida cliente y producto por id, pero no consulta `inventario` en ningún momento. Tampoco hay reserva.

Combinado con D-01 (que no detiene el despacho cuando falta stock), no hay **ningún** punto del flujo de venta donde la falta de existencias frene la operación. Se pueden crear 100 pedidos de un producto con 3 unidades y despacharlos todos.

**Qué debería pasar:** validar disponibilidad al crear el pedido (o al pasarlo a `procesado`) y reservar el stock comprometido, de modo que dos pedidos no puedan comprometer las mismas unidades.

---

### D-03 · Lectura-modificación-escritura de `stock_actual` sin bloqueo: actualizaciones perdidas

> **Estado: cerrado en L1 (2026-08-25).** `InventarioRepository` expone dos
> lecturas con `SELECT … FOR UPDATE` (`buscarParaActualizar`,
> `buscarPorProductoParaActualizar`) y los cinco puntos de escritura las usan.
> Verificado por mutación: al retirar el bloqueo, la prueba de concurrencia falla
> 3 de 3 veces con `expected: 0 but was: 5`.
>
> **Corrección al recuento original:** este defecto estaba en **cinco** sitios,
> no en tres. A los tres listados abajo hay que sumar
> `service/OrdenProduccionService.java:363` (alta de producto terminado) y
> `service/RecepcionMercanciaService.java:222` (entrada por recepción de compra).

**Evidencia:** `service/EmpaqueService.java:108-116`, `service/InventarioService.java:115-155`, `service/SolicitudDevolucionService.java:283-290`

Los tres leen `inv.getStockActual()`, calculan en Java y guardan. Ningún repositorio usa `@Lock(PESSIMISTIC_WRITE)` ni `SELECT … FOR UPDATE`, y el aislamiento es el `READ COMMITTED` por defecto de PostgreSQL.

Dos despachos simultáneos del mismo producto leen el mismo saldo y escriben cada uno el suyo: uno de los dos descuentos se pierde, mientras ambos dejan su movimiento en el kardex. Con D-01 el efecto se agrava, porque el recorte a cero enmascara el descuadre.

**Qué debería pasar:** bloqueo pesimista sobre la fila de `inventario` antes de leer el saldo, o un `UPDATE inventario SET stock_actual = stock_actual - :n WHERE id = :id AND stock_actual >= :n` atómico que falle si no afecta filas.

---

### D-04 · El asistente IA ejecuta SQL generado por el modelo, protegido solo por una lista de palabras prohibidas

**Evidencia:** `service/IAService.java:122-137`

```java
String upper = sql.toUpperCase();
String[] prohibidas = {"INSERT","UPDATE","DELETE","DROP","TRUNCATE","ALTER","CREATE"};  // :124
for (String palabra : prohibidas) { if (upper.contains(palabra)) { /* rechaza */ } }
...
List<Tuple> rows = entityManager.createNativeQuery(sql, Tuple.class)   // :136
        .setMaxResults(500).getResultList();
```

El texto libre del usuario (`pregunta`) va al modelo, y el SQL que este devuelve se ejecuta **tal cual** contra la base, con la conexión de `usr_admin_marathon`. El único control es una comparación de subcadenas, que se elude sin dificultad:

- `DO`, `CALL`, `GRANT`, `REVOKE`, `COPY`, `SET`, `VACUUM` y `pg_read_file()` **no están en la lista**.
- Un bloque `DO $$ BEGIN EXECUTE 'DEL'||'ETE FROM pedido'; END $$;` no contiene ninguna palabra prohibida y escribe en la base.
- No hay transacción de solo lectura ni `SET TRANSACTION READ ONLY`.

Aunque la ruta está restringida a Administrador y Supervisor E-Commerce, un Supervisor puede leer cualquier tabla, incluidos los hashes de contraseña de `usuario`.

Y como el `catch` devuelve `"Error al ejecutar la consulta: " + e.getMessage()`, el endpoint es además un oráculo de errores SQL para explorar el esquema.

**Qué debería pasar:** ejecutar en una transacción de solo lectura con un rol de base de datos que solo tenga `SELECT` sobre una lista blanca de tablas/vistas; validar el SQL con un analizador sintáctico real —una única sentencia, y que sea `SELECT`— en lugar de comparar subcadenas; y devolver un mensaje genérico al usuario, dejando el detalle en el log del servidor.

---

### D-34 · El precio de venta lo fija quien llama al endpoint, no el catálogo

**Evidencia:** `service/PedidoService.java:160-165`

```java
Producto producto = productoRepository.findById(item.getIdProducto())
        .orElseThrow(...);                                  // :160  se carga el producto...
DetallePedido detalle = new DetallePedido();
detalle.setProducto(producto);
detalle.setCantidad(item.getCantidad());
detalle.setPrecioUnitario(item.getPrecioUnitario());        // :165  ...y se ignora su precio
```

El producto se lee de la base únicamente para asociarlo por id. **`producto.getPrecio()` no se consulta en ningún momento**: el precio unitario que se persiste es el que venía en el cuerpo de la petición.

Aguas abajo todo funciona «bien» sobre ese dato falso: el trigger `fn_recalcular_total_pedido_stmt` calcula fielmente el total desde los subtotales, `fn_proteger_total_pedido` impide después alterarlo, y `fn_validar_total_comprobante` verifica que el comprobante coincida con el pedido. Las tres defensas del motor confirman un importe inventado, porque el motor no tiene forma de saber cuál era el precio de catálogo.

La validación existente no ayuda: `@DecimalMin("0.01")` en `dto/pedido/DetallePedidoItemDTO.java:19` solo exige que el precio sea positivo.

**Cómo reproducirlo:** `POST /api/pedidos` con `{"idProducto": <uno de $200>, "cantidad": 1, "precioUnitario": 0.01}` → se crea un pedido válido de $0.01, con su comprobante, su descuento de stock y su entrada en los informes de venta.

**Alcance del daño:** contamina `detalle_pedido`, `pedido.total`, `comprobante_interno.total`, y todo lo que se construye encima (dashboard de KPIs, informe de ventas por producto, análisis de márgenes). Es S1 y no S2 porque el dato queda mal escrito de forma permanente: no hay manera de reconstruir a posteriori cuál era el precio correcto en el momento de la venta.

**Qué debería pasar:** `detalle.setPrecioUnitario(producto.getPrecio())`. Si el negocio necesita precios negociados por línea, deben venir de una lista de precios con su propia autorización, nunca del cuerpo de la petición.

---

## S2 — Bloquea un flujo o expone datos

### D-05 · Desactivar un usuario no le retira el acceso

**Evidencia:** `config/JwtAuthenticationFilter.java:50` + `config/JwtUtils.java:90-93` + `model/Usuario.java:83`

```java
public boolean isTokenValid(String token, UserDetails userDetails) {
    return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);   // JwtUtils:90-92
}
```

`Usuario.isEnabled()` existe y devuelve `"activo".equals(estado)`, pero **el filtro nunca lo consulta**. `AuthService.refresh` tampoco. Como el refresh emite un token nuevo de 24 h sin comprobar el estado, un usuario dado de baja conserva el acceso indefinidamente mientras renueve dentro de la ventana de 7 días.

`UsuarioService.eliminar` (`:149-162`) —la única vía de retirar acceso que ofrece el sistema— se limita a poner `estado='inactivo'`. **No hace lo que dice hacer.**

**Qué debería pasar:** el filtro y el refresh deben rechazar al usuario cuyo `isEnabled()` sea falso, devolviendo 401.

---

### D-06 · Un comprobante anulado deja el pedido sin poder facturarse nunca más

**Evidencia:** `service/ComprobanteService.java:58` y `:105-115`

```java
if (comprobanteRepository.findByPedidoIdPedido(idPedido).isPresent()) {   // :58
    throw new ValidationException("El pedido ya tiene un comprobante emitido");
}
```

La comprobación **no filtra por estado**. `anular` solo escribe `estado='anulado'` y no borra ni desvincula nada. Así que tras anular, `generarComprobante` sigue encontrando el comprobante y rechaza la emisión.

**Qué pasa hoy:** anular una factura por un error de emisión deja ese pedido facturado a perpetuidad con un documento anulado. No hay corrección posible desde la aplicación.

**Qué debería pasar:** la comprobación debe ignorar los comprobantes anulados, permitiendo reemitir; y la relación pedido→comprobante debe admitir varios documentos con a lo sumo uno vigente.

---

### D-07 · La numeración de comprobantes usa `count() + 1`

**Evidencia:** `service/ComprobanteService.java:121-125`

```java
long count = comprobanteRepository.count() + 1;
return String.format("COMP-%d-%06d", anio, count);
```

Dos emisiones concurrentes obtienen el mismo `count` y generan el mismo número; la segunda choca con `uq_comprobante_numero` y sale como HTTP 500. Además el contador es global mientras el formato incluye el año, de modo que la numeración no reinicia por ejercicio ni es continua.

**Qué debería pasar:** una `SEQUENCE` de PostgreSQL por año (o una tabla de contadores con bloqueo), que garantice unicidad bajo concurrencia.

---

### D-08 · El importe del reembolso al cliente no se valida contra nada

**Evidencia:** `service/SolicitudDevolucionService.java:243-268`, en concreto `:261`

```java
reembolso.setMonto(dto.getMonto());
```

`ReembolsoRequestDTO` valida que el monto sea positivo, y el CHECK `chk_rc_monto` que sea `> 0`. Nadie comprueba que guarde relación con lo devuelto: ni contra la suma de `cantidad_devuelta × precio_unitario` de las líneas aptas, ni contra el total del pedido.

**Qué pasa hoy:** un reembolso de $10 000 sobre una devolución de $50 se acepta sin objeción.

**Qué debería pasar:** calcular el importe máximo reembolsable a partir de las líneas efectivamente aceptadas y rechazar cualquier valor superior.

---

### D-09 · Cualquier usuario autenticado puede invocar el cambio de contraseña de cualquier cuenta

**Evidencia:** `config/SecurityConfig.java` (regla `PUT /api/usuarios/*/password` → `.authenticated()`) + `service/UsuarioService.java:133-147`

```java
public void cambiarPassword(Integer id, UsuarioCambiarPasswordDTO dto) {
    Usuario usuario = usuarioRepository.findById(id)...      // :134  ← id sin verificar
    if (!passwordEncoder.matches(dto.getPasswordActual(), usuario.getPassword())) { ... }
```

La regla de seguridad se declara `.authenticated()` **antes** de la regla general que reserva `/api/usuarios/**` al Administrador, y el comentario del controlador (`controller/UsuarioController.java:60-66`) afirma que el endpoint opera «sobre su propia cuenta». El servicio **nunca compara `id` con el usuario autenticado**.

No es una toma de control directa —hace falta acertar la contraseña actual de la víctima—, pero deja un **oráculo de adivinación de contraseñas contra cualquier cuenta**: intentos ilimitados (no hay límite de tasa, D-25) y respuestas distinguibles entre «contraseña actual incorrecta» (400) y usuario inexistente (404).

**Qué debería pasar:** el servicio debe exigir que `id` coincida con el usuario autenticado, o bien que quien llama sea Administrador.

---

### D-10 · El build de producción del frontend apunta a `localhost` por HTTP plano

**Evidencia:** `marathon-frontend/src/environments/environment.prod.ts`

```ts
export const environment = { production: true, apiUrl: 'http://localhost:8080/api' };
```

Idéntico a `environment.ts`. El artefacto de producción llama a `localhost`, así que no funciona fuera de la máquina del desarrollador; y lo hace sobre HTTP plano, de modo que el JWT viajaría sin cifrar. Contrasta con el esfuerzo puesto en TLS entre la aplicación y la base (`sslmode=verify-full`), que queda anulado en el tramo navegador→backend.

**Qué debería pasar:** URL configurable por entorno, sobre HTTPS.

---

### D-11 · Se puede emitir un comprobante de un pedido en cualquier estado, incluido `anulado`

**Evidencia:** `service/ComprobanteService.java:54-77` — solo comprueba que el pedido exista y que no tenga ya comprobante. No mira `pedido.estado`.

**Qué pasa hoy:** un pedido `pendiente` —sin picking, sin despacho, sin stock movido— o incluso `anulado` puede recibir su comprobante interno. La facturación queda desligada del ciclo de la venta.

**Qué debería pasar:** exigir un estado válido para facturar (`enviado`/`entregado` según la regla de negocio) y rechazar explícitamente los pedidos anulados.

---

### D-12 · Las respuestas de error 500 exponen el mensaje interno

**Evidencia:** `exception/GlobalExceptionHandler.java:124`

```java
"Error interno del servidor: " + ex.getMessage(),
```

En un fallo de base de datos, `getMessage()` arrastra la sentencia SQL, nombres de tablas y columnas, nombres de constraints y a veces valores. Sumado a `IAService` (`:151`), que devuelve el error crudo de la consulta, un usuario obtiene una imagen bastante fiel del esquema.

Se agrava porque **faltan manejadores** para `DataIntegrityViolationException` (nombre de producto duplicado → 500 en lugar de 409), `HttpMessageNotReadableException` (JSON mal formado → 500) y `ConstraintViolationException`. Buena parte de los 500 evitables del sistema pasan por aquí.

**Qué debería pasar:** mensaje genérico al cliente con un identificador de correlación; el detalle, al log del servidor. Y manejadores específicos que conviertan las violaciones de integridad en 409/400.

---

### D-13 · El modelo de permisos está construido de punta a punta y no se aplica en ninguna parte

> **Estado: CERRADO en la F48 (2026-08-27).** Se siguió el orden que exigía el
> plan, y en ese orden:
>
> 1. **Cargar la matriz.** `sql/fase48_matriz_permisos.sql`. Los 49 permisos
>    pasan a **94**: se añaden los 14 módulos que no tenían ninguna fila
>    (empaque, devoluciones, recepciones, facturas de compra, cuentas por pagar,
>    pagos, devoluciones a proveedor, materia prima, producción, BOM, análisis de
>    costos, auditoría, logs, IA) y las acciones que faltaban. **La matriz no
>    está inventada:** cada fila sale de una regla que ya se aplicaba en
>    `SecurityConfig`, que es la autorización real y funciona. Por eso encender
>    la comprobación no le quitó el acceso a nadie.
> 2. **Verificar que ningún rol queda en cero.** Lo comprueba el propio script,
>    dentro de la transacción: si algún rol quedara vacío no se aplica nada. Hoy:
>    Administrador 94, Compras 24, Pedidos 20, Producción **20** (era 0),
>    Supervisor 20, Bodega 19.
> 3. **Encender.** `@EnableMethodSecurity` y **153 anotaciones**
>    `@PreAuthorize("hasAuthority('modulo:accion')")`, una por método de
>    controlador. Las cuatro decisiones que no caben en una anotación —anular un
>    pedido frente a cambiarle el estado, y aprobar/rechazar/cancelar una orden
>    de compra, que comparten endpoint— se comprueban dentro del servicio con
>    `config/Permisos`.
>
> Las authorities se releen de `rol_permiso` en **cada petición**
> (`JwtAuthenticationFilter` → `UsuarioDetailsService`), no del claim del token:
> un cambio en la pantalla de roles surte efecto en la siguiente llamada, sin
> volver a entrar. La pantalla de roles pasa de ser un editor de adorno a ser el
> mando real.
>
> `DataInitializer.ensureComprasFase21()` ya **no** asigna permisos: volvía a
> colgar `compras:aprobar` del Encargado de Compras en cada arranque, y eso
> contradecía al propio `OrdenCompraService`, que exige Administrador.
>
> Cubierto por `MatrizPermisosTest` (6 pruebas: ningún rol en cero, ningún
> permiso inventado, ninguno huérfano, ningún endpoint sin cubrir) y
> `PermisosSeAplicanTest` (5 pruebas: quitar el permiso quita de verdad la
> capacidad).

**Evidencia (del defecto original):** 49 filas en `permiso` y la tabla `rol_permiso` en la base; `controller/PermisoController.java`; claim `permisos` en `config/JwtUtils.java`; array `permisos` en `LoginResponseDTO`; `marathon-frontend/src/app/core/guards/permiso.guard.ts`; `AuthService.hasPermiso()`.

Comprobado:

- `grep -c "@PreAuthorize" controller/*.java` → **0** en los 33 controladores.
- `SecurityConfig` usa exclusivamente `hasAuthority("ROLE_…")`; nunca consulta permisos.
- `grep -rn "permisoGuard" marathon-frontend/src/` → solo encuentra su propia definición. **Ninguna ruta lo usa**; las 45 rutas usan `rolGuard`.

Confirmación desde los datos: el rol `Encargado de Producción` tiene **0 permisos asignados** y opera con total normalidad.

**Qué pasa hoy:** la pantalla de administración permite editar permisos por rol y guardarlos, sin que eso cambie absolutamente nada. Es una función que aparenta funcionar y no hace nada — la clase de defecto que más caro sale en una auditoría de seguridad.

**Qué debería pasar:** o se aplican (con `@PreAuthorize("hasAuthority('modulo:accion')")` y `permisoGuard` en las rutas), o se retira el subsistema completo. Mantenerlo desconectado es peor que cualquiera de las dos opciones.

---

### D-35 · El traslado entre bodegas falla siempre: nunca se asigna la bodega destino

**Evidencia:** `service/InventarioService.java:128-155` + `model/MovimientoInventario.java:55-56, 93` + constraint `chk_traslado_requiere_destino`

El caso `traslado` descuenta del inventario origen y suma al destino correctamente, pero al construir el `MovimientoInventario` (`:160-168`) solo asigna `inventario`, `tipoMovimiento`, `cantidad` y `usuario`. **`setInventarioDestino(...)` no se invoca en ninguna parte del proyecto** — comprobado:

```
$ grep -rn "setInventarioDestino" --include=*.java .
./model/MovimientoInventario.java:93:    public void setInventarioDestino(...)   ← solo la definición
```

La tabla tiene la restricción:

```sql
CHECK (tipo_movimiento <> 'traslado' OR id_inventario_destino IS NOT NULL)
```

de modo que el `INSERT` del movimiento viola el CHECK y aborta.

**Qué pasa hoy si un usuario lo intenta:** el método es `@Transactional`, así que los dos ajustes de stock hacen rollback —eso está bien— y el usuario recibe un **HTTP 500 con el texto crudo de PostgreSQL** (por D-12). El traslado nunca llega a ocurrir. La función de traslado entre bodegas **no ha funcionado nunca desde la aplicación**.

**Por qué no se ve en los datos:** hay 6 134 filas de traslado en `movimiento_inventario` y **las 6 134 tienen `id_inventario_destino` informado** — porque las insertaron los scripts de poblado masivo, no la aplicación. Mirar los datos sugiere que el traslado funciona; es justo al revés.

**Qué debería pasar:** asignar `mov.setInventarioDestino(destino)` antes de guardar. Es una línea. La restricción del motor ya estaba haciendo su trabajo: impidió que se escribieran miles de traslados sin destino.

---

## S3 — Incorrecto pero tolerable

### D-14 · El picking no registra de qué bodega se recogió

**Evidencia:** `service/PickingService.java:68-104`; `model/DetallePedido.java` solo guarda `cantidad_recogida` y `picking_completado`.

Sin ese dato, el despacho no puede saber de dónde descontar — es la causa raíz del punto 1 de D-01. Además el picking no consulta stock en ningún momento: es una lista de verificación, no un picking contra ubicaciones.

**Qué debería pasar:** registrar `id_inventario` (o `id_bodega`) por línea recogida, y validar disponibilidad al recoger.

---

### D-15 · El movimiento de tipo `ajuste` registra un valor absoluto como si fuera un delta

**Evidencia:** `service/InventarioService.java:125-127` y `:167`

```java
case "ajuste":
    inv.setStockActual(dto.getCantidad());   // :126  ← valor ABSOLUTO
...
mov.setCantidad(dto.getCantidad());          // :167  ← se graba como si fuera un DELTA
```

Sumar los movimientos del kardex deja de reconstruir el saldo en cuanto hay un ajuste de por medio. Hay 10 757 ajustes en la base.

**Qué debería pasar:** grabar en el movimiento la diferencia (`nuevo - anterior`), o distinguir el tipo en la lectura del kardex.

---

### D-16 · `SET LOCAL app.current_user_id` se emite fuera de transacción: la auditoría pierde al autor

**Evidencia:** `service/LogService.java:76-84` (el método y su contrato documentado), `service/ProductoService.java:128, 145, 208`, `service/UsuarioService.java:133, 149`

El propio javadoc de `fijarContextoUsuario()` fija la regla: *«Debe llamarse **antes** de la escritura que dispara el trigger, y **dentro de la misma transacción**: `SET LOCAL` muere en el commit»*.

Pero `ProductoService.eliminar` (`:207`) y los métodos de `UsuarioService` **no llevan `@Transactional`** (verificado sobre todos los servicios). Cada llamada a repositorio abre su propia transacción autocommit, así que el `SET LOCAL` ya no está vigente cuando llega el `UPDATE` que dispara `trg_auditoria_producto` / `trg_auditoria_usuario`. **El código incumple su propio contrato documentado.**

**Qué debería pasar:** anotar esos métodos con `@Transactional`.

---

### D-17 · La tabla `auditoria_cambios` está vacía

**Evidencia:** `select count(*) from auditoria_cambios` → **0**, con 5 triggers activos sobre `cliente`, `producto`, `proveedor`, `usuario` y `rol_permiso`.

La pantalla de auditoría no lo delata porque `AuditoriaController` lee en realidad `historial_inventario` (60 000 filas). La traza genérica de la fase 40 no ha registrado un solo cambio.

`[NO VERIFICADO]` — no puedo determinar si es porque la tabla se vació tras las pruebas de la fase 40 o porque los triggers nunca llegan a escribir. Se resuelve haciendo un `UPDATE` sobre `producto` desde la aplicación y comprobando si aparece la fila.

---

### D-18 · El cambio de estado de un pedido se registra sin autor

**Evidencia:** `service/PedidoService.java:207`

```java
logService.registrar(null, "pedidos", "cambio_estado", ...);
```

Se pasa `null` explícitamente donde el resto de los servicios pasa `idUsuarioActual`. Las transiciones de estado de pedido —incluida la anulación— quedan en la bitácora sin persona responsable. `LogService.idUsuarioActual()` ya resuelve el usuario desde el contexto de seguridad sin necesidad de cambiar la firma.

---

### D-19 · El descuento del pedido no tiene tope: el exceso se pierde en silencio

**Evidencia:** `dto/pedido/PedidoRequestDTO.java:17` (`descuento` sin anotación de validación) + trigger `fn_recalcular_total_pedido_stmt`

```sql
SET total = GREATEST( (SELECT COALESCE(SUM(d.subtotal),0) ...) - p.descuento, 0)
```

Un descuento superior al subtotal no produce error: el `GREATEST(...,0)` deja el total en 0. El CHECK `chk_pedido_total >= 0` queda satisfecho y nadie se entera. Tampoco hay `@PositiveOrZero`, así que un descuento negativo **inflaría** el total (lo frena `chk_pedido_descuento >= 0` en la base, pero como un 500).

**Qué debería pasar:** validar en el servicio que `0 <= descuento <= subtotal` y devolver 400.

---

### D-20 · Borrado físico de `categoria` y `unidad_medida` contra claves foráneas `RESTRICT`

**Evidencia:** `service/CategoriaService.java:96`, `service/UnidadMedidaService.java:105`

```java
categoriaRepository.delete(cat);   // "Eliminación física (no tiene campo estado)"
```

Las FK `fk_producto_categoria`, `fk_producto_unidad_medida` y `fk_materia_prima_unidad` son todas `ON DELETE RESTRICT` (verificado en el catálogo). Borrar una categoría en uso lanza una violación de integridad que, al no haber manejador (D-12), sale como **HTTP 500 con el texto crudo de PostgreSQL** en vez de un 409 con un mensaje comprensible.

El resto del sistema usa baja lógica (`producto`, `bodega`, `cliente`, `usuario`). Estos dos catálogos son la excepción.

**Qué debería pasar:** añadir `estado` a ambas tablas y dar de baja lógicamente, o comprobar el uso antes de borrar y devolver 409.

---

### D-21 · `RolService.eliminar` borra roles físicamente y carga toda la tabla en memoria

**Evidencia:** `service/RolService.java:128-137`

```java
long usuarios = usuarioRolRepository.findByUsuarioIdUsuario(0).size(); // dummy   // :128
if (!usuarioRolRepository.findAll().stream()                                       // :130
        .filter(ur -> ur.getRol().getIdRol().equals(id)).collect(...).isEmpty()) { ... }
...
rolRepository.delete(rol);                                                          // :137
```

Tres problemas en diez líneas: una variable muerta marcada `// dummy`; un `findAll()` que trae la tabla `usuario_rol` entera al heap para filtrar en Java lo que sería un `exists` en SQL; y un **borrado físico** de un rol, que destruye la referencia histórica de autorización (`rol` no tiene columna `estado`, y `rol_permiso` es `ON DELETE CASCADE`).

**Qué debería pasar:** `existsByRolIdRol(id)` en el repositorio, baja lógica del rol, y borrar la variable muerta.

---

### D-22 · No hay endpoint para modificar una orden de compra

**Evidencia:** `controller/OrdenCompraController.java` expone `GET`, `GET /{id}`, `POST` y `PUT /{id}/estado`. No hay `PUT /{id}` ni endpoints de líneas.

Una OC creada en `borrador` con una cantidad o un precio mal puestos solo se puede cancelar y rehacer. El estado `borrador` existe en el CHECK y en la máquina de estados, pero no sirve para lo que un borrador sirve.

---

### D-23 · `logout` no invalida el token

**Evidencia:** `controller/AuthController.java:40` + `marathon-frontend/src/app/core/services/auth.service.ts:44-49`

El logout solo borra `localStorage` en el navegador. Con JWT sin estado y sin lista de revocación, el token sigue siendo válido hasta su expiración (24 h; el de refresco, 7 días). Un token capturado antes del cierre de sesión sigue sirviendo.

> **Estado: MITIGADO, no cerrado (2026-08-27).** `app.jwt.expiration` baja de
> **24 h a 2 h** (`application.properties`). La ventana de un token robado se
> reduce doce veces, y la misma línea acorta también la de D-27. No obliga a
> nadie a volver a entrar: el refresh sigue durando 7 días y desde la L7
> comprueba que el usuario siga activo.
>
> **Lo que sigue abierto:** el logout no invalida nada. Cerrarlo de verdad exige
> una lista de revocación persistida y una comprobación en **cada** petición. El
> dueño del proyecto lo dejó fuera del alcance el 2026-08-27, a la vista del
> coste; no es un olvido.

---

### D-24 · Se puede vender un producto dado de baja

**Evidencia:** `service/PedidoService.java:160-165` — se busca el producto por id y se usa; no se comprueba `producto.getEstado()`.

Contrasta con la validación análoga que sí existe para el cliente (`:134-137`, «El cliente no está activo»).

---

### D-25 · No hay límite de intentos de login, y los mensajes distinguen cuentas existentes

**Evidencia:** `service/AuthService.java:47-81`

No hay control de tasa, bloqueo temporal ni retardo progresivo en ninguna capa. Además el `catch (DisabledException)` devuelve «Usuario inactivo» mientras `BadCredentialsException` devuelve «Credenciales incorrectas»: la diferencia permite enumerar qué correos corresponden a cuentas reales.

Se agrava en combinación con D-09, que ofrece un segundo oráculo de contraseñas sin límite de intentos.

---

### D-26 · Credenciales fijas en el código versionado, y un secreto JWT por defecto también versionado

**Evidencia:** `config/DataInitializer.java:116` (`"Admin1234!"`) y `:237` (`"Demo1234!"`); `src/main/resources/application.properties:37` (`app.jwt.secret=${JWT_SECRET:defaultDevSecretChangeInProduction}`) y `:30` (`spring.datasource.password=${DB_PASSWORD:1234}`)

Las 6 cuentas demo están activas en la base con esas contraseñas. Los hashes son bcrypt correctos (`$2a$10$`, 60 caracteres, con CHECK que lo exige), así que el defecto no es el almacenamiento sino que las claves estén publicadas.

Más grave que las contraseñas: si `JWT_SECRET` no está definido y falta `application-local.properties`, la aplicación arranca **firmando tokens con un secreto conocido y versionado**, con lo que cualquiera puede forjar un token de administrador sin credenciales.

Los secretos reales sí están correctamente fuera de git (comprobado: `.env` y `application-local.properties` están en `.gitignore` y `git ls-files` no los devuelve).

**Qué debería pasar:** que la aplicación **no arranque** si `app.jwt.secret` conserva el valor por defecto; y que las cuentas demo se creen solo bajo un perfil explícito.

> **Estado: SIGUE PARCIAL (revisado el 2026-08-27).** La primera mitad —la
> grave— la cerró `ComprobacionesDeArranque`: la aplicación se niega a arrancar
> con el secreto por defecto, así que ya no se pueden forjar tokens de
> administrador. La segunda sigue abierta: `app.datos-demo.enabled=true`.
>
> El dueño del proyecto decidió el 2026-08-27 **no** ponerlo en `false` todavía.
> Lo que hay que resolver antes no es el código (`DATOS_DEMO=false` y ya), sino
> el procedimiento de primer arranque sin datos de demo y el aviso a quien ya
> tenga el entorno montado.
>
> **Detalle que descubrió la F48 y hay que tener presente:** `DataInitializer`
> está entero bajo `@ConditionalOnProperty("app.datos-demo.enabled")`. Con los
> datos demo apagados **no se crean ni los roles**, así que el día que se apague
> hay que sembrar `rol` por script antes de ejecutar `fase48_matriz_permisos.sql`
> —que falla a propósito, y con un mensaje claro, si los seis roles no existen—.

---

### D-27 · El JWT se guarda en `localStorage`

**Evidencia:** `marathon-frontend/src/app/core/services/auth.service.ts:31-33`

Accesible desde cualquier JavaScript de la página, así que un XSS entrega el token. La alternativa habitual es una cookie `HttpOnly` + `Secure` + `SameSite`. Se anota como S3 porque no hay hoy un XSS conocido en la aplicación, pero eleva el coste de cualquier otro defecto de front.

> **Estado: ABIERTO por decisión (2026-08-27).** Lo único que cambió es que el
> token que se puede robar **caduca en 2 h en vez de en 24** (ver D-23): el
> agujero es el mismo, la ventana es doce veces menor.
>
> Mover el token a cookie `HttpOnly` toca CORS, CSRF —hoy deshabilitado a
> propósito—, el interceptor HTTP y todas las llamadas del front. Es rediseñar la
> sesión, no arreglar un fallo, y el dueño del proyecto lo dejó fuera del alcance
> el 2026-08-27.

---

### D-28 · `listarDespachados` genera N+1 consultas

**Evidencia:** `service/EmpaqueService.java:139-147`

```java
.map(p -> pedidoService.obtener(p.getIdPedido()))
```

`obtener()` ejecuta dos consultas por pedido (el pedido y sus detalles). Una página de 20 despachos son 41 consultas. Con 229 999 pedidos en la base, es un patrón que va a doler.

---

## S4 — Cosmético

### D-29 · Un carácter suelto corrompe la clave de API de Anthropic

**Evidencia:** `src/main/resources/application.properties:39`

```properties
anthropic.api.key=${ANTHROPIC_API_KEY:}m
```

La `m` final está fuera del marcador. Si `ANTHROPIC_API_KEY` está definida, el valor efectivo es la clave **con una `m` pegada al final** → 401 de Anthropic. Si no lo está, el valor es `"m"`, que **no** es cadena vacía ni `"TU_API_KEY_AQUI"`, con lo que la guarda de `IAService.java:60` no salta y la aplicación hace una llamada condenada al fallo en vez de avisar de que no está configurada.

En este equipo queda tapado porque `application-local.properties` reescribe la propiedad con `TU_API_KEY_AQUI`.

*(Nota: el identificador de modelo `claude-sonnet-4-6` de la línea 41 **es válido** — comprobado. No es un defecto.)*

---

### D-30 · La lista de palabras prohibidas del asistente IA rechaza consultas legítimas

**Evidencia:** `service/IAService.java:124`

`upper.contains("UPDATE")` casa con `UPDATED_AT`, y `upper.contains("CREATE")` con `CREATED_AT`. Como `created_at` y `updated_at` existen en `cliente`, `producto`, `usuario`, `pedido` y `proveedor`, **cualquier consulta que seleccione una fecha de alta o modificación se rechaza** con «Query no permitida por seguridad».

Es la otra cara de D-04: la misma comprobación deja pasar lo peligroso y bloquea lo inofensivo.

---

### D-31 · El administrador protegido se identifica por su correo, escrito a mano

**Evidencia:** `service/UsuarioService.java:153`

```java
if ("admin@marathon.com".equals(usuario.getCorreo())) {
    throw new ValidationException("No se puede desactivar al administrador principal");
}
```

Cambiar el correo de esa cuenta desactiva la protección sin previo aviso. Mejor sería una bandera en la tabla, o comprobar que quede al menos un administrador activo.

---

### D-32 · Declaración y sentencia en la misma línea

**Evidencia:** `service/PedidoService.java:121`

```java
public PedidoResponseDTO obtener(Integer id) {        Pedido pedido = pedidoRepository.findById(id)
```

---

### D-33 · Pantallas que ofrecen acciones que el backend va a rechazar

**Evidencia:** `marathon-frontend/src/app/app.routes.ts:27` frente a `config/SecurityConfig.java`

La ruta `/clientes` admite `Supervisor E-Commerce`, pero `POST`/`PUT`/`DELETE /api/clientes` está reservado a Administrador y Operador de Pedidos. Un Supervisor entra a la pantalla, ve los botones de crear y editar, y recibe un 403 al pulsarlos.

Es el desajuste típico entre el guard de ruta (que decide si se ve la pantalla) y la autorización real (que decide si se puede actuar). El resto de las rutas está bien alineado.

---

## Añadidos del repaso de flujos del 2026-08-27

Tras cerrar D-02 y D-13 se recorrió el sistema flujo por flujo preguntando «¿y
esto quién lo comprueba?», y contrastando cada respuesta contra
`mod_venta_inve`. Salieron tres huecos que no estaban en la lista de 35. Los dos
primeros no necesitaban decidir nada y se cerraron; el tercero sí, y se deja
anotado.

### D-36 · El importe de la factura de compra no se contrasta con lo recibido — S2, *abierto*

**Evidencia:** `service/FacturaCompraService.java:63-88` — `crear()` valida que
la orden exista, que tenga al menos una recepción, que el número no se repita y
que las fechas sean coherentes. **El subtotal lo pone quien registra la factura y
nadie lo compara con la mercancía que entró.** Una factura de 50.000 sobre una
orden de la que se recibieron 500 se acepta y genera una cuenta por pagar de
50.000, que después se paga.

Es el tercer lado del cotejo clásico OC ↔ recepción ↔ factura. Los otros dos sí
están: la recepción no deja recibir más de lo pedido, y el pago no deja pagar más
del saldo. Falta este.

**Medido en la base (2026-08-27):** de **2.287** facturas, **1.649** tienen el
subtotal por encima del valor recibido de su orden; el mayor exceso es
**11.194,86**. Casi todas vienen del poblado masivo de la F38, así que **no se
puede distinguir** lo que escribió la aplicación de lo que escribieron los
scripts — la misma limitación que impide reparar los despachos históricos.

**Por qué no se cierra:** hace falta decidir antes si el subtotal puede incluir
flete u otros cargos por encima de lo recibido y con qué tolerancia. Bloquear sin
esa respuesta es inventarse la regla, y con el histórico así tampoco se puede
contrastar el bloqueo.

**Qué se hizo mientras tanto:** el descuadre deja de ser silencioso. Se registra
en `log_accion` con módulo `compras` y acción `factura_descuadre`, con las dos
cifras y la diferencia, para poder medir cuánto ocurre de verdad **antes** de
decidir la regla:

```sql
SELECT count(*), min(fecha), max(fecha)
  FROM log_accion WHERE modulo = 'compras' AND accion = 'factura_descuadre';
```

## Añadidos del 2026-08-27 (tarde) — depurando la aplicación en el navegador

Los cuatro siguientes salieron de conducir la interfaz con Chrome, pantalla por
pantalla y rol por rol. **Ninguno lo veían las 131 pruebas.** El primero lo
reportó el dueño del proyecto usando el sistema.

### D-40 · La bodega decía «guardada correctamente» y no guardaba el responsable — S2, *cerrado*

**Cómo apareció:** el dueño del proyecto editó la bodega 1 para ponerle un
responsable. Salió el aviso verde y la columna siguió vacía.

**La cadena entera, y dónde se rompía:**

| Capa | Qué hacía con `responsable` |
|---|---|
| `bodegas.component.ts` | lo pide en el formulario, lo envía y tiene columna para pintarlo |
| `BodegaRequestDTO` | lo acepta |
| **`BodegaService`** | **no lo miraba** |
| `model/Bodega` | no tenía el campo |
| **tabla `bodega`** | **no tenía la columna** |
| `BodegaResponseDTO` | tiene el campo, siempre `null` |

Es la misma familia que D-34 (el precio del pedido, aceptado e ignorado) y que
D-13 (una pantalla que aparentaba controlar accesos): **el sistema afirma haber
hecho algo que no hizo.**

**Cerrado:** `sql/fase50_bodega_responsable.sql` añade la columna, y la entidad y
el servicio la mapean. Se añadió la columna en vez de quitar el campo porque
todo el trabajo de interfaz ya estaba hecho y el dato es útil; quitarlo era tirar
eso para que el sistema dejara de mentir.

**Privilegios:** ninguno hizo falta, y es la excepción a la regla del §2.4 de
`PENDIENTE.md`: sobre `bodega` la F34 concedió a nivel de **tabla**, no de
columna, y un GRANT de tabla cubre las columnas futuras. Se comprobó igualmente.

### D-41 · Las listas paginadas no tenían orden: la fila que editabas desaparecía — S2, *cerrado*

**Cómo apareció:** al verificar D-40. Se guardó el responsable, y la bodega 1
**se cayó de la lista**: la primera fila pasó a ser la bodega 2, con la 1 aún
activa en la base.

**La causa:** `PageRequest.of(page, size)` **sin `Sort`**. Sin `ORDER BY`,
PostgreSQL devuelve las filas en el orden del montón, y un `UPDATE` reescribe la
fila al final: la que acabas de editar salta a la última página. Además dos
páginas consecutivas pueden repetir una fila y esconder otra.

**Alcance:** **16 listados** paginados y **4 listas sin paginar** que alimentan
desplegables e informes. La segunda tanda apareció al hacer el picking: el
desplegable de bodegas mostraba la AAM1 **al final** de las 20, por lo mismo.

**Cerrado:**
- catálogos y maestros → por id ascendente (es el orden que enseña la columna ID);
- comprobantes y movimientos de inventario → por id descendente (lo más reciente primero);
- desplegable de bodegas → **alfabético**, que es como se busca una bodega entre 20;
- auditoría, bitácora y despachos → ya ordenaban por fecha, que **no es única**: se les añadió el id como desempate, sin el cual la paginación sigue sin ser estable;
- `ClienteService:147` y `DashboardService:79` se dejaron sin `Sort` **a propósito**, y con su comentario: el primero es una comprobación de existencia, el segundo lleva su `ORDER BY` en el JPQL.

### D-42 · Un pedido recién recogido no se podía empacar — S1, *cerrado*

**Cómo apareció:** haciendo el flujo completo de venta por navegador. Se creó el
pedido, se procesó, se recogió… y en la pantalla de Empaque **no estaba**.

**Medido:** la pantalla pedía a `/api/picking/pedidos` los **100 primeros**
pedidos en `procesado` —ordenados del más antiguo— y filtraba en el navegador
los que tenían el picking completo. Hay **19.059** pedidos en `procesado`. El
pedido recién recogido estaba en la posición **19.059 de 19.059**. No había
paginación ni buscador: era inalcanzable.

Dicho de otro modo: **quien recogía un pedido no podía empacarlo.** El flujo de
almacén estaba cortado por la mitad para todo lo que no fueran los 100 pedidos
más antiguos del sistema.

**Cerrado:**
- `PedidoRepository.buscarListosParaEmpacar` filtra **en la base** (procesado + picking completo), en vez de traer 100 y descartar en el cliente;
- `GET /api/empaque/pedidos/listos`, paginado;
- ordena del **más reciente al más antiguo**, al revés que la cola de picking: la cola de picking es trabajo por hacer y va por orden de llegada; el empaque es lo que *acabas* de recoger;
- la pantalla pagina de verdad y dice cuántos hay (`Página 1 de 901 · 9.002 pedidos listos para empacar`).

El `EXISTS` de la consulta no sobra: sin él, un pedido **sin líneas** cumpliría
el `NOT EXISTS` por vacuidad y saldría listado como listo para empacar.

### D-43 · Cinco «Ver detalle» del tablero de inicio no llevaban a ninguna parte — S3, *cerrado*

**Cómo apareció:** recorriendo con cada rol todas las pantallas que su menú
ofrece. Tres roles tenían enlaces que los devolvían a `/inicio`.

**La causa:** `DashboardResumenService` da a cada indicador la ruta de su enlace
«Ver detalle», y esa ruta es **del frontend**. Cinco de las catorce estaban
puestas con el nombre del **endpoint de la API**, que se parece pero no coincide:

| Emitía | Existe en `app.routes.ts` |
|---|---|
| `/ordenes-compra` | `/compras` |
| `/ordenes-produccion` | `/produccion` |
| `/analisis-costos` | `/produccion/costos` |
| `/recepciones` | no hay pantalla suelta → `/compras` |
| `/pedidos-especiales` | `/pedidos/especiales` |

Afectaba al inicio de **Operador de Pedidos, Encargado de Compras y Encargado de
Producción** — casi todo lo que ven esos tres roles al entrar.

**Cerrado:** rutas corregidas, y `RutasDelTableroTest` contrasta cada ruta que
emite el tablero contra `app.routes.ts`. Nada en el compilador ata un literal de
Java con uno de TypeScript; esa prueba lo ata. Se comprobó por mutación: al
devolver una ruta a su valor roto, la prueba falla.

---

### D-39 · Tres de los seis roles no podían crear el documento central de su trabajo — S1, *cerrado*

> **El defecto más grave de los que quedaban, y llevaba abierto desde la F37.**

**Cómo apareció:** levantando la aplicación para probar las fases 47 y 48. El
primer `POST /api/pedidos` como `pedidos@marathon.com` devolvió **403**, y el
registro decía *«permiso denegado a la tabla pedido»*.

**Medido contra la aplicación en marcha (2026-08-27):**

| Rol | Su documento | Antes | Después |
|---|---|---|---|
| Operador de Pedidos | `POST /api/pedidos` | **403** | 201 |
| Encargado de Compras | `POST /api/ordenes-compra` | **403** | 201 |
| Encargado de Producción | `POST /api/ordenes-produccion` | **403** | 201 |

**La causa, que es una sola para los tres.** Hibernate escribe por omisión un
INSERT **estático**: nombra *todas* las columnas mapeadas de la entidad, tengan
valor o no. En un documento que atraviesa varias etapas eso significa nombrar
columnas que solo se rellenan **más tarde** —`numero_hu` y `transportista` las
pone el empaque; `id_usuario_aprobador` y `fecha_aprobacion`, la aprobación;
`fecha_inicio` y `fecha_fin`, el cierre de la orden—. La fase 34 concede
privilegios columna por columna, así que el rol que **arranca** el flujo
correctamente no las tiene, y PostgreSQL no rechaza esas columnas: **rechaza el
INSERT entero**.

**Por qué no se había visto.** `scripts/fase37_pruebas_endpoints.ps1` dio 66 de
66, pero **todas sus pruebas son GET**: comprueban quién puede *ver* cada
pantalla, no quién puede *escribir* en ella. El único camino de escritura que se
ejercitaba de verdad era el del Administrador, que usa el pool por defecto y
tiene INSERT sobre la tabla entera. Con el administrador todo funcionaba; con los
otros tres roles, nada. Es el mismo punto ciego que ya había señalado
`PENDIENTE.md §2.5` —«una consulta nueva debe ser ejecutable por el rol que la va
a pedir»— aplicado a la escritura.

**Cerrado en dos mitades:**

1. `@DynamicInsert` en `Pedido`, `DetallePedido`, `OrdenCompra` y
   `OrdenProduccion`. Hibernate pasa a nombrar solo las columnas **con valor**,
   así que las que se rellenan más tarde desaparecen del INSERT. Resuelve las
   nueve columnas que están a NULL al crear, **sin conceder ni un privilegio**.
2. `sql/fase49_privilegios_de_creacion.sql`, para las cuatro que sí llevan valor
   porque la entidad les da un valor por defecto en Java
   (`picking_completado`=false, `cantidad_recogida`=0, `costo_mano_obra`=0,
   `costo_indirecto`=0). En los cuatro casos el valor insertado es **exactamente
   el DEFAULT que ya tiene la columna**, y se concede **INSERT y no UPDATE**: el
   Operador de Pedidos puede crear una línea con el picking sin empezar, pero
   **sigue sin poder marcarla como recogida** — eso es del Operador de Bodega.

**Cómo se comprobó:** con la aplicación en marcha, los tres POST devuelven 201, y
`has_column_privilege('usr_pedidos_marathon','detalle_pedido','picking_completado','UPDATE')`
sigue devolviendo `false`.

**Lo que deja abierto:** `fase37_pruebas_endpoints.ps1` sigue probando solo
lecturas. Mientras siga así, este defecto puede volver con cualquier entidad
nueva.

---

### D-37 · Un traslado podía tener la misma bodega de origen y destino — S3, *cerrado*

**Evidencia:** `service/InventarioService.java`, rama `traslado`. Se exigía
bodega destino, pero no que fuera distinta de la de origen. Con las dos iguales,
origen y destino son **la misma fila** de `inventario`: el `−cantidad` y el
`+cantidad` se anulan y en el kardex queda un movimiento de traslado que no
trasladó nada.

**Cerrado:** se rechaza con un 400. Había **0 casos** en la base, así que no
rompe histórico. Cubierto por `ValidacionesDeFlujoTest`.

### D-38 · Las devoluciones se comprobaban solicitud a solicitud, no acumulando — S2, *cerrado*

**Evidencia:** `service/SolicitudDevolucionService.java` — se comprobaba
`item.getCantidadDevuelta() > dp.getCantidad()` mirando **solo la solicitud en
curso**. Dos solicitudes seguidas sobre el mismo pedido podían devolver cada una
las 10 unidades de una línea de 10, y las dos pasaban: 20 unidades devueltas de
10 vendidas, con su reembolso detrás.

**Cerrado:** se compara contra el acumulado
(`SolicitudDevolucionDetalleRepository.devueltoAcumuladoDe`), excluyendo las
solicitudes `rechazada` —que no se llevaron mercancía y no deben gastar cupo—.
Había **0 casos** en la base. El mensaje dice las tres cifras: comprado, ya
devuelto y lo que queda.

---

## Apéndice — Qué está bien y conviene no romper

Un inventario de defectos da una imagen deformada. Para equilibrarla:

- **El modelo de datos.** ~60 CHECK, 30 triggers, FK con política explícita, cifrado en reposo de datos de contacto. Los triggers de protección (`fn_proteger_total_pedido`, `fn_proteger_total_orden_compra`, `fn_proteger_monto_pagado_cxp`) impiden falsear importes calculados, y funcionan.
- **El flujo de compras.** Máquina de estados completa, separación de funciones (quien solicita no aprueba), validación de todas las líneas antes de persistir, costo promedio ponderado bien calculado.
- **`InventarioService.registrarMovimiento`** para entradas y salidas: valida stock antes de descontar y lanza un error claro. Es exactamente lo que `EmpaqueService` debería hacer y no hace.
- **La autorización centralizada** en `SecurityConfig`, con orden de reglas correcto y una segunda capa real basada en los `GRANT` de PostgreSQL por rol.
- **La validación de entrada** con Bean Validation en los DTO, aplicada de forma bastante consistente.
- **El flujo de devoluciones de cliente**, salvo el importe del reembolso.

El patrón que resume el diagnóstico: **cuando el cálculo lo hace la base de datos, está bien; cuando lo hace el código Java, está mal.** Y no hay una sola prueba automatizada que lo hubiera delatado.
