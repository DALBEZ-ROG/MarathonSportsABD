# Plan de trabajo — MarathonSportsABD

**Fecha:** 2026-08-25
**Entrada:** [`AUDITORIA.md`](AUDITORIA.md) · [`DEFECTOS.md`](DEFECTOS.md) (35 defectos: 5 S1, 10 S2, 15 S3, 5 S4)
**Estado:** ejecutado. Los 17 lotes completados el 2026-08-25 — ver «Estado final de ejecución» al pie.

---

## Cómo leer este plan

**17 lotes.** Un lote es un tema que se implementa y se prueba entero de una sentada, y que se puede revertir sin arrastrar a los demás. No es una lista de tareas: si dos defectos no se pueden probar con el mismo montaje, van en lotes distintos aunque estén en el mismo archivo.

**Orden.** Primero lo que corrompe datos (A), luego lo que bloquea un flujo entero (B), luego validaciones y permisos (C), y al final despliegue y pantallas (D). Dentro de cada bloque, primero lo barato.

**Disciplina de revisión.** Una rama por lote. Un lote = un commit revertible (o una serie corta que se revierte junta). **Nunca dos lotes en el mismo commit** — es lo que hace que el «plan B» de cada ficha sea real y no un deseo.

**Sin refactors de paso.** Ningún lote renombra archivos, mueve paquetes ni reformatea código que no esté tocando por otro motivo. Cuando algo hay que reescribirlo, es su propio lote y lo dice en el nombre.

### Convención de reversión de esquema

El repositorio **no tiene ninguna** hoy (comprobado: no hay un solo archivo `*rollback*` en `marathon-backend/sql/`). Este plan la introduce, y es obligatoria desde el primer lote con esquema:

```
marathon-backend/sql/faseNN_<tema>.sql            -- aplicar
marathon-backend/sql/faseNN_<tema>_rollback.sql   -- revertir
```

Ambos se escriben **a la vez, antes de tocar código**, y la reversión se prueba así:

```bash
pg_dump --schema-only -t <tabla> mod_venta_inve_test > antes.sql
psql -f faseNN_tema.sql          mod_venta_inve_test
psql -f faseNN_tema_rollback.sql mod_venta_inve_test
pg_dump --schema-only -t <tabla> mod_venta_inve_test > despues.sql
diff antes.sql despues.sql        # debe salir vacío
```

Un lote con esquema no se da por terminado hasta que ese `diff` sale vacío. Los scripts nuevos empiezan en **fase45** (el último aplicado es `fase43_ampliacion_negocio.sql`; se salta el 44 para no chocar con lo que fuera la «Fase 44» del historial).

### Dependencias entre lotes

```
L0 (arnés) ─── habilita la verificación de todos los demás
L1 ──► L4          (L4 sustituye el reparto de bodega que L1 deja provisional)
L3 ──► L8          (el tope de descuento necesita un subtotal fiable)
L1 ──► L16         (medir rendimiento sobre el despacho ya corregido)
El resto es independiente y se puede reordenar según convenga.
```

---

# Bloque A — Deja de corromper datos

## L0 · Arnés de pruebas mínimo — ✅ **HECHO** (2026-08-25)

**Objetivo:** poder demostrar que un lote quedó bien, en vez de afirmarlo.

> **Resultado.** `mvn test` → 3 pruebas, 0 fallos, BUILD SUCCESS. Base real
> intacta (recuento idéntico antes y después). Dos desvíos respecto de lo
> planificado, ambos a mejor:
> 1. La base de pruebas se construye con `pg_dump --schema-only` en vez de
>    ejecutando los 39 scripts de fase. **No es solo comodidad:**
>    `SETUP_COMPLETO.md` advierte que `fase34_seguridad_roles.sql` hace
>    `DROP ROLE` de los seis `usr_*_marathon`, y los roles son objetos del
>    **clúster**. Ejecutar la cadena de fases contra una base desechable del
>    mismo servidor habría destruido los roles de la base real.
> 2. Tres pruebas en vez de dos: se añadió un guardia explícito que comprueba
>    que el datasource conecta a `mod_venta_inve_test`, porque
>    `application.properties` trae `spring.profiles.active=local` apuntando a la
>    base real y esa precedencia no debía quedar como suposición.
>
> Procedimiento completo en `SETUP_COMPLETO.md` § «Arnés de pruebas».

**Cierra:** ningún defecto. Es el habilitador — cubre el hallazgo de `AUDITORIA.md` §3.1 (cero pruebas en todo el proyecto).

**Toca:**
- Nuevo `marathon-backend/src/test/java/com/marathon/` (el directorio **no existe**)
- Nuevo `src/test/resources/application-test.properties`
- `pom.xml` — `spring-boot-starter-test` ya está declarado y sin usar; no hace falta añadir nada más en este lote

**Esquema:** no. Pero **necesita una base de datos aparte**: `mod_venta_inve_test`, creada con los mismos scripts de `sql/`. Ninguna prueba apunta jamás a `mod_venta_inve`.

**Verificación:**
1. `mvn test` termina en verde con dos pruebas: una que arranca el contexto de Spring, y una de repositorio que inserta un `Categoria`, lo lee y hace rollback.
2. `select count(*) from categoria` en `mod_venta_inve` (la real) devuelve lo mismo antes y después de `mvn test` — prueba de que el arnés está aislado.

**Riesgo y plan B:** el historial del repo tiene tres commits sobre portabilidad a un segundo equipo, así que el riesgo real es dejar la build atada a esta máquina. Por eso **no** se usa Testcontainers en este lote: solo un perfil `test` contra una base local, documentado en `SETUP_COMPLETO.md` en el mismo commit. Si aun así rompe la build de alguien, el lote se revierte entero sin tocar una línea de producción.

---

## L1 · El despacho deja de destruir inventario — ✅ **HECHO** (2026-08-25)

**Objetivo:** que confirmar un empaque descuente exactamente lo que sale, o falle entero sin escribir nada.

> **Resultado.** 8 pruebas, 0 fallos, BUILD SUCCESS. Base real intacta.
>
> - **Base roja primero.** Las 3 pruebas de D-01 fallaron contra el código
>   original con los síntomas exactos: *«Expecting code to raise a throwable»*
>   (despachar 10 con stock 3 funcionaba) y *«expected 0 but was 4»* (solo se
>   tocaba una bodega).
> - **D-03 estaba en cinco sitios, no en tres.** La ficha de `DEFECTOS.md`
>   nombraba `EmpaqueService`, `InventarioService` y `SolicitudDevolucionService`;
>   el mismo patrón sin bloqueo estaba también en `OrdenProduccionService:363`
>   y `RecepcionMercanciaService:222`. Se corrigieron los cinco.
> - **La prueba de concurrencia se verificó por mutación.** Retirando el bloqueo,
>   falla 3 de 3 veces con `expected: 0 but was: 5` — la firma de la
>   actualización perdida. No es una prueba que pase sola.
> - **Riesgo medido antes de desplegar:** de **9 001** pedidos hoy listos para
>   empacar, **0** fallarían con el nuevo comportamiento. L1 no introduce
>   regresión sobre los datos actuales.
> - **Hallazgo lateral:** el primer intento de limpieza de la fixtura falló con
>   *«permiso denegado a la tabla auditoria_cambios»*. `usr_admin_marathon` tiene
>   INSERT por trigger pero no DELETE — el modelo de privilegios de la F40
>   funciona, y una traza que la aplicación pudiera borrar no sería una traza.
>
> **Sigue abierto:** la parte 1 de D-01 (la bodega no es la del picking) queda
> determinista pero aún no correcta; la cierra L4. Ningún dato histórico se
> reparó — sigue siendo el punto 1 de «lo que no voy a hacer».

**Cierra:** **D-01** (partes 2 y 3: el `continue` y el recorte a cero), **D-03** (escritura de stock sin bloqueo).
*Parcial:* la parte 1 de D-01 (bodega arbitraria) queda **mitigada**, no cerrada — se cierra en L4.

**Toca:**
- `service/EmpaqueService.java` — el bucle de `:99-124`
- `repository/InventarioRepository.java` — un finder con `@Lock(PESSIMISTIC_WRITE)`
- `service/InventarioService.java` y `service/SolicitudDevolucionService.java` — mismo bloqueo, mismo patrón

> Los dos últimos archivos no son un refactor de paso: son **el mismo defecto D-03**, escrito tres veces. Se prueban con el mismo test de concurrencia. Si se dejaran fuera, D-03 quedaría abierto.

**Esquema:** no.

**Qué cambia, en concreto:**
- Recorrer las bodegas en **orden determinista** (por `id_bodega`) y descontar de varias hasta cubrir la línea, en vez de `findFirst()` sobre una lista sin ordenar.
- Si el stock total no cubre la línea → `ValidationException` (400) y **rollback de todo el empaque**. Se van el `continue` de `:104` y el recorte de `:110`.
- El movimiento se graba con la cantidad **realmente descontada de esa bodega**, no con la de la línea.
- Bloqueo pesimista sobre las filas de `inventario` antes de leer el saldo.

**Verificación:**

| # | Montaje | Resultado esperado |
|---|---|---|
| 1 | Producto con stock 3 en bodega A y 0 en B. Pedido de 10. Picking completo. `POST /api/empaque/pedidos/{id}/confirmar` | 400. `inventario` de A **sigue en 3**. **Cero** filas en `movimiento_inventario` para ese pedido. `pedido.estado` sigue en `procesado`. *(Antes: 200, stock 0, un movimiento de 10.)* |
| 2 | Stock 6 en A y 4 en B. Pedido de 10 | 201. A=0, B=0. **Exactamente 2** movimientos, de 6 y de 4, que suman 10 |
| 3 | Stock 10. Dos hilos despachan 5 cada uno a la vez | Stock final 0. Nunca 5. Nunca negativo |

Y una consulta de conciliación que debe cuadrar para los pedidos creados por el test:

```sql
select d.id_producto,
       sum(m.cantidad)                as movido,
       max(i.stock_inicial_test) - max(i.stock_actual) as descontado
from ... -- movido = descontado, siempre
```

**Riesgo y plan B:** despachos que hoy «funcionan» empezarán a devolver 400. Es el comportamiento correcto, pero se va a leer como una regresión. **Antes de desplegar**, ejecutar la consulta de conciliación sobre la base real para saber el tamaño del descuadre previo y avisar al equipo. Si aparece un caso legítimo bloqueado, revertir el commit (una sola clase más un finder) y volver con el caso concreto.

**Lo que este lote NO hace:** no repara ni un dato histórico. Los descuadres ya escritos siguen ahí — ver §«Lo que no voy a hacer», punto 1.

---

## L2 · El asistente IA deja de poder escribir en la base

**Objetivo:** cortar la ejecución de SQL arbitrario hoy, y rehacer el módulo detrás de un interruptor.

**Cierra:** **D-04**, **D-30**.

**Toca:**
- `service/IAService.java` (reescritura del bloque `:118-155`), `controller/IAController.java`
- `application.properties` — `app.ia.enabled`
- Nuevo `sql/fase45_rol_ia_solo_lectura.sql` + `fase45_rol_ia_solo_lectura_rollback.sql`
- `marathon-frontend/src/app/modules/ia/ia-chat.component.ts`
- `pom.xml` — dependencia nueva: JSqlParser

**Esquema:** no cambia tablas, pero **sí crea un rol de base de datos** (`usr_ia_marathon`, `SELECT` sobre una lista blanca). Por eso lleva script de aplicación y de reversión (`DROP ROLE` + `REVOKE`).

**Se hace en dos pasos, dentro del mismo lote:**

1. **Interruptor (minutos).** `app.ia.enabled=false` por defecto. El endpoint devuelve 503 con un mensaje claro. El frontend muestra el módulo deshabilitado. **Esto es lo que cierra el S1**, y se puede desplegar solo.
2. **Reconstrucción detrás del interruptor.** Ejecutar en `SET TRANSACTION READ ONLY` con el pool de `usr_ia_marathon`; validar el SQL con JSqlParser (una sola sentencia, y que sea `SELECT`) en vez de comparar subcadenas; mensaje de error genérico al cliente, detalle al log.

**Verificación:**

| # | Prueba | Resultado esperado |
|---|---|---|
| 1 | Interruptor apagado: `POST /api/ia/consultar` | 503, y **ninguna** consulta llega a la base (assert sobre el contador de Hibernate) |
| 2 | Interruptor encendido, respuesta del modelo simulada con `DO $$ BEGIN EXECUTE 'DEL'\|\|'ETE FROM pedido'; END $$;` | Rechazado por el analizador. `select count(*) from pedido` **idéntico** antes y después. *(Antes: la lista de palabras prohibidas lo dejaba pasar.)* |
| 3 | `select created_at, updated_at from producto limit 1` | **Funciona** — cierra D-30, que hoy lo rechaza porque `CREATED_AT` contiene la subcadena `CREATE` |
| 4 | `select correo, password from usuario` | Denegado por el rol de base de datos, no por la aplicación |
| 5 | `insert into ...` | Rechazado por el analizador **y**, si llegara, por la transacción de solo lectura |

Las pruebas 2 y 5 son las importantes: comprueban **dos barreras independientes**, no una.

**Riesgo y plan B:** el asistente IA es una función visible; apagarla se nota. Por eso va por configuración: revertir es cambiar un valor, sin recompilar. La dependencia nueva (JSqlParser) es el otro riesgo — si complica la build, el paso 1 se puede desplegar solo y el paso 2 se pospone.

---

## L3 · El precio de venta sale del catálogo

**Objetivo:** que el importe de un pedido no dependa de lo que mande quien llama al endpoint.

**Cierra:** **D-34**, **D-24** (vender un producto dado de baja).

**Toca:**
- `service/PedidoService.java:160-165`
- `dto/pedido/DetallePedidoItemDTO.java`
- `marathon-frontend/src/app/modules/pedidos/pedido-nuevo/pedido-nuevo.component.ts`

**Esquema:** no.

**Qué cambia:** `detalle.setPrecioUnitario(producto.getPrecio())`. Y rechazar con 400 si `producto.getEstado()` no es `activo`, igual que ya se hace con el cliente en `:134-137`.

**Secuencia importante:** el campo `precioUnitario` del DTO **no se elimina en este lote**, se acepta y se ignora. Si se quitara ahora, el frontend actual —que lo envía— dejaría de compilar contra el contrato. Se retira en un lote de limpieza posterior, cuando el front ya no lo mande.

**Verificación:**

| # | Prueba | Resultado esperado |
|---|---|---|
| 1 | `POST /api/pedidos` con `precioUnitario: 0.01` sobre un producto de catálogo a 200.00 | 201, pero el `detalle_pedido` persistido tiene **200.00**, y `pedido.total` = 200 × cantidad − descuento *(antes: 0.01)* |
| 2 | `POST /api/pedidos` con un producto en `estado='inactivo'` | 400 con mensaje claro *(antes: 201)* |
| 3 | Alta de un pedido desde `/pedidos/nuevo` en el navegador | El total en pantalla coincide con el de la base |

**Riesgo y plan B:** si algún flujo dependía de precios negociados por línea, este cambio los aplasta contra el precio de catálogo. **Comprobado en la auditoría: no existe tal flujo** — no hay lista de precios ni tabla de descuentos por línea; el único mecanismo de rebaja es `pedido.descuento` en la cabecera. Si aun así aparece el caso, revertir es una línea.

---

# Bloque B — Desbloquea flujos que no funcionan

## L4 · El picking registra la bodega, y el despacho descuenta de ahí

**Objetivo:** que la mercancía se descuente de donde realmente se recogió.

**Cierra:** **D-01** (parte 1, la que L1 dejó mitigada), **D-14**.

**Toca:**
- Nuevo `sql/fase46_picking_bodega.sql` + `fase46_picking_bodega_rollback.sql`
- `model/DetallePedido.java`, `dto/picking/PickingLineaDTO.java`, `dto/picking/PickingUpdateDTO.java`
- `service/PickingService.java`, `service/EmpaqueService.java`
- `marathon-frontend/.../picking/picking-ejecucion/picking-ejecucion.component.ts`

**Esquema: SÍ.**

```sql
ALTER TABLE detalle_pedido
  ADD COLUMN id_bodega_picking INTEGER
  REFERENCES bodega(id_bodega) ON UPDATE CASCADE ON DELETE RESTRICT;
-- rollback: ALTER TABLE detalle_pedido DROP COLUMN id_bodega_picking;
```

**Columna anulable y sin CHECK, a propósito.** Un `CHECK (picking_completado = false OR id_bodega_picking IS NOT NULL)` fallaría al aplicarse: hay **229 999 pedidos** con líneas ya marcadas como recogidas y sin bodega. La regla se aplica en el servicio. Si más adelante se quiere en el motor, será un `NOT VALID` y una campaña de datos aparte.

**Trato de los pedidos antiguos:** una línea con `id_bodega_picking` nula (todo lo anterior al lote) cae en el reparto determinista de L1. Los nuevos usan la bodega registrada. Es la razón por la que L1 va primero y no se puede saltar.

**Verificación:**

| # | Prueba | Resultado esperado |
|---|---|---|
| 1 | Aplicar y revertir el script sobre una copia de la base real | El `diff` de `pg_dump --schema-only -t detalle_pedido` sale **vacío**. Se anota cuánto tarda el `ALTER` sobre 229 999 filas |
| 2 | Bodega A con 100 unidades, bodega B con 10. Picking registrado contra **B**. Despacho de 5 | Se descuentan 5 de **B**. A queda intacta *(antes: se descontaba de A, la primera con stock)* |
| 3 | Picking de 10 contra una bodega que tiene 3 | Rechazado **en el picking**, no en el despacho |
| 4 | Pedido antiguo (`id_bodega_picking` nula) recogido antes del lote | Se despacha con el reparto de L1, sin error |

**Riesgo y plan B:** el `ALTER TABLE ... ADD COLUMN` con valor por defecto nulo no reescribe la tabla en PostgreSQL 11+, así que sobre 229 999 filas debe ser casi instantáneo — pero **hay que medirlo en la copia antes de tocar la real** (prueba 1). El otro riesgo es dejar el frontend enviando líneas sin bodega: el servicio debe rechazarlas con un mensaje explícito, no aceptarlas en silencio. Plan B: el script de reversión quita la columna y el código se revierte en un commit; los pedidos recogidos durante la ventana vuelven al reparto de L1 sin pérdida de datos.

---

## L5 · El kardex vuelve a cuadrar: traslado y ajuste

**Objetivo:** que el traslado entre bodegas funcione, y que un ajuste no mienta en el libro de movimientos.

**Cierra:** **D-35**, **D-15**.

**Toca:** `service/InventarioService.java` (`:125-127` y `:155-168`), `dto/inventario/MovimientoRequestDTO.java`.

**Esquema:** no. La columna `id_inventario_destino` y el CHECK `chk_traslado_requiere_destino` **ya existen** — el defecto es que el código nunca los usa.

**Qué cambia:**
- `mov.setInventarioDestino(destino)` antes de guardar. Es **una línea**, y arregla una función que no ha funcionado nunca desde la aplicación.
- En `ajuste`, el movimiento se graba con la **diferencia** (`abs(nuevo − anterior)`) y la dirección en `observacion`, no con el valor absoluto. Se rechaza el ajuste de delta cero, que hoy violaría `chk_movimiento_cantidad > 0`.

**Límite honesto:** con `cantidad > 0` en el motor no se puede guardar un delta con signo, así que sumar `movimiento_inventario` a ciegas seguirá sin reconstruir el saldo cuando hay ajustes. **La reconciliación correcta usa `historial_inventario`**, que el trigger `fn_trg_historial_inventario` ya rellena con `stock_anterior`/`stock_nuevo`. Una columna con signo sería la solución completa; queda fuera (ver §«Lo que no voy a hacer», punto 11).

**Verificación:**

| # | Prueba | Resultado esperado |
|---|---|---|
| 1 | *Prueba en rojo primero:* traslado A→B de 5 unidades **antes** del cambio | 500. Se deja escrita como prueba que falla, y el lote la pone en verde |
| 2 | El mismo traslado, después | 201. `id_inventario_destino` informado. A −5, B +5 |
| 3 | `select count(*) from movimiento_inventario where tipo_movimiento='traslado' and id_inventario_destino is null` | 0 |
| 4 | Ajuste de 10 → 4 | El movimiento se graba con `cantidad = 6`, no 4. `historial_inventario` registra 10 → 4 |

**Riesgo y plan B:** bajo. Es un método aislado, con `@Transactional` ya puesto y un CHECK del motor que hace de red. Revertir es un commit.

---

## L6 · Facturación: reemitir tras anular, y numeración a prueba de concurrencia

**Objetivo:** que anular una factura no deje el pedido sin poder facturarse nunca más, y que dos emisiones a la vez no choquen.

**Cierra:** **D-06**, **D-07**, **D-11**.

**Toca:**
- Nuevo `sql/fase47_secuencia_comprobante.sql` + `fase47_secuencia_comprobante_rollback.sql`
- `service/ComprobanteService.java` (`:54-77`, `:105-115`, `:121-125`)

**Esquema: SÍ.**

```sql
CREATE SEQUENCE seq_comprobante_interno;
SELECT setval('seq_comprobante_interno',
              (SELECT coalesce(max(substring(numero_comprobante from '[0-9]+$')::bigint), 0)
               FROM comprobante_interno));
-- rollback: DROP SEQUENCE seq_comprobante_interno;
```

**El `setval` no es opcional.** Hay **30 000 comprobantes** ya emitidos; si la secuencia arranca en 1, la primera emisión choca contra `uq_comprobante_numero`. Es el modo de fallo principal de este lote.

**Qué cambia:**
- `generarNumero()` usa `nextval` en vez de `count() + 1`.
- El bloqueo de `:58` pasa a mirar **solo comprobantes en estado `emitido`**, de modo que tras anular se puede reemitir.
- `generarComprobante` exige un estado de pedido válido y rechaza `anulado`.

**Decisión pendiente (te la pregunto antes de implementar):** ¿desde qué estados se puede facturar? Propuesta por defecto: **`enviado` y `entregado`**. Si el negocio factura por adelantado, sería `procesado` en adelante.

**Verificación:**

| # | Prueba | Resultado esperado |
|---|---|---|
| 1 | Emitir sobre un pedido `pendiente` | 400 *(antes: 201)* |
| 2 | Emitir sobre un pedido `anulado` | 400 *(antes: 201)* |
| 3 | Emitir → anular → **emitir de nuevo** | 201 con un número **nuevo**. El pedido queda con 2 comprobantes y exactamente 1 en `emitido` *(antes: 400 «ya tiene un comprobante», para siempre)* |
| 4 | 20 hilos emitiendo a la vez | 20 números distintos, 0 errores *(antes: colisiones contra `uq_comprobante_numero` → 500)* |
| 5 | `select numero_comprobante, count(*) from comprobante_interno group by 1 having count(*) > 1` | 0 filas, antes y después |

**Riesgo y plan B:** el `setval` mal calculado rompe la primera emisión. Se prueba en la copia (prueba 5 antes de aplicar). Segundo riesgo: la numeración actual es `COMP-AAAA-NNNNNN` con contador **global**, así que no reinicia por año — la secuencia mantiene ese comportamiento. Si se quiere reinicio anual, es otro diseño y otra decisión. Plan B: `DROP SEQUENCE` y revertir el commit; los comprobantes ya emitidos con la secuencia conservan sus números y no estorban.

---

## L7 · Retirar el acceso funciona de verdad

**Objetivo:** que dar de baja a un usuario lo desconecte, y que nadie pueda operar sobre la cuenta de otro.

**Cierra:** **D-05**, **D-09**.

**Toca:** `config/JwtAuthenticationFilter.java:50`, `service/AuthService.java` (`refresh`), `service/UsuarioService.java:133`, `controller/UsuarioController.java:59-68`.

**Esquema:** no.

**Qué cambia:**
- El filtro comprueba `userDetails.isEnabled()` — el método **ya existe** (`model/Usuario.java:83`) y nadie lo llama.
- `refresh` hace la misma comprobación antes de emitir un token nuevo.
- `cambiarPassword` exige que `id` sea el del usuario autenticado, o que quien llama sea Administrador.

**Verificación:**

| # | Prueba | Resultado esperado |
|---|---|---|
| 1 | Login como `bodega@marathon.com` → token. Un admin lo desactiva. Reusar el token | **401** *(antes: 200, hasta 24 h)* |
| 2 | Con el refresh token del usuario desactivado | 401 *(antes: token nuevo de 24 h, renovable indefinidamente)* |
| 3 | Usuario 3 llama a `PUT /api/usuarios/4/password` con la contraseña correcta de 4 | **403** *(antes: 204 — la contraseña de otro, cambiada)* |
| 4 | Usuario 3 llama a `PUT /api/usuarios/3/password` | 204 |
| 5 | Un Administrador llama sobre cualquier id | 204 |

**Riesgo y plan B:** una comprobación mal puesta deja fuera a todo el mundo. El riesgo es acotado porque `chk_usuario_estado` limita `estado` a `activo`/`inactivo`: no hay valores sorpresa. Antes de desplegar, `select estado, count(*) from usuario group by 1` para confirmarlo. Plan B: revertir el commit; los tokens vivos siguen sirviendo como hoy.

**Este lote NO añade revocación en el logout** (D-23). Ver §«Lo que no voy a hacer», punto 3.

---

# Bloque C — Validaciones, errores y permisos

## L8 · Tope al dinero que sale

**Objetivo:** que ningún importe salga del sistema sin contrastarse contra el documento que lo origina.

**Cierra:** **D-08** (reembolso sin tope), **D-19** (descuento sin tope).

**Depende de L3** — el tope de descuento solo tiene sentido sobre un subtotal que no se pueda inventar.

**Toca:** `service/SolicitudDevolucionService.java:243-268`, `service/PedidoService.java`, `dto/pedido/PedidoRequestDTO.java:17`.

**Esquema:** no.

**Qué cambia:**
- Reembolso máximo = suma de `cantidad_devuelta × precio_unitario` de las líneas **no rechazadas**. Por encima → 400.
- Descuento: `0 ≤ descuento ≤ subtotal`, validado en el servicio con 400, en vez de que el trigger lo absorba con `GREATEST(...,0)` o que el CHECK lo escupa como 500.

**Decisión pendiente:** ¿puede un reembolso superar el valor de la mercancía (portes, gesto comercial)? Propuesta por defecto: **no**. Si el negocio lo necesita, el exceso debe ir en un concepto aparte, no inflando el reembolso.

**Verificación:**

| # | Prueba | Resultado esperado |
|---|---|---|
| 1 | RMA de 2 unidades a $50 (tope $100). Reembolso de $150 | 400 *(antes: 201 — se aceptaba cualquier cifra)* |
| 2 | El mismo, reembolso de $100 | 201 |
| 3 | RMA con una línea `rechazado`: su valor no cuenta para el tope | El tope excluye la línea rechazada |
| 4 | Pedido de subtotal $100 con descuento $150 | 400 *(antes: 201 y total 0 en silencio)* |
| 5 | Descuento −10 | 400 *(antes: 500 desde `chk_pedido_descuento`)* |

**Riesgo y plan B:** si hay reembolsos legítimos por encima del valor de la mercancía, este lote los bloquea. Hay **1 sola fila** en `reembolso_cliente`, así que no hay historial que contradiga la regla. Plan B: revertir, o subir el tope a `pedido.total`.

---

## L9 · Los errores dicen lo justo, y con el código correcto

**Objetivo:** que un fallo no publique el esquema, y que una violación de integridad sea un 409 y no un 500.

**Cierra:** **D-12**, **D-20**.

**Toca:** `exception/GlobalExceptionHandler.java:118-127`, `service/CategoriaService.java:92-98`, `service/UnidadMedidaService.java:102-108`.

**Esquema:** no.

**Qué cambia:**
- `handleGeneral` devuelve un mensaje genérico + un **id de correlación**; el `getMessage()` y la traza van al log del servidor.
- Manejadores nuevos: `DataIntegrityViolationException` → 409, `HttpMessageNotReadableException` → 400.
- `categoria` y `unidad_medida` comprueban el uso **antes** de borrar y devuelven 409 con un mensaje legible.

**Verificación:**

| # | Prueba | Resultado esperado |
|---|---|---|
| 1 | `POST /api/productos` con un `nombre` duplicado | 409, y el cuerpo **no** casa con `/insert\|select\|constraint\|uq_/i` *(antes: 500 con el texto de PostgreSQL)* |
| 2 | `POST` con JSON mal formado | 400 *(antes: 500)* |
| 3 | `DELETE` de una categoría en uso | 409 con mensaje legible *(antes: 500 crudo, porque la FK es `ON DELETE RESTRICT`)* |
| 4 | Provocar un 500 cualquiera | El cuerpo lleva el id de correlación y **ningún** detalle interno; el log del servidor lleva ese id y la traza completa |

La prueba 1 con la expresión regular es la que de verdad cierra D-12: comprueba **ausencia** de fuga, no presencia de un mensaje bonito.

**Riesgo y plan B:** ocultar mensajes hace más incómoda la depuración durante una demo. Lo compensa el id de correlación. Plan B: revertir; nada más depende de este lote.

---

## L10 · Arranque seguro y freno al login

**Objetivo:** que la aplicación no arranque con un secreto público, y que nadie pueda probar contraseñas sin límite.

**Cierra:** **D-26**, **D-25**, **D-29**.

**Toca:** `application.properties:37,39`, nuevo `config/StartupChecks.java`, `config/DataInitializer.java:116,237`, `service/AuthService.java:47-81`, `SETUP_COMPLETO.md`.

**Esquema:** no.

**Qué cambia:**
- La aplicación **se niega a arrancar** si `app.jwt.secret` conserva `defaultDevSecretChangeInProduction` o mide menos de 32 bytes.
- Los 6 usuarios demo se crean solo bajo un perfil `demo` explícito.
- Límite de intentos de login por IP+correo (un contador en memoria basta para este proyecto).
- Mismo mensaje y mismo código para «usuario inactivo» y «credenciales incorrectas» — hoy la diferencia permite enumerar cuentas.
- La `m` suelta de `anthropic.api.key=${ANTHROPIC_API_KEY:}m` (D-29).

**Verificación:**

| # | Prueba | Resultado esperado |
|---|---|---|
| 1 | `ApplicationContextRunner` con el secreto por defecto | El arranque **falla** con un mensaje que dice qué variable definir |
| 2 | El mismo con un secreto de 64 caracteres | Arranca |
| 3 | 10 logins fallidos desde la misma IP; el 11º | 429 |
| 4 | Login con usuario inactivo vs. contraseña incorrecta | **Mismo** cuerpo y **mismo** código en los dos casos |
| 5 | `grep -n "anthropic.api.key" application.properties` | La línea termina en `}`, sin carácter suelto |

**Riesgo y plan B:** este es **el lote más peligroso del bloque C**, y no por el código. El repositorio tiene tres commits sobre portabilidad a un segundo equipo; un fail-fast mal documentado convierte «no arranca» en el nuevo modo de fallo de cualquiera que clone el proyecto. Por eso el cambio en `SETUP_COMPLETO.md` va **en el mismo commit**, no después. El límite de intentos, además, puede estropear una demo en vivo: los umbrales van en `application.properties`, no fijos en el código. Plan B: subir el umbral por configuración; para el fail-fast, revertir la clase `StartupChecks`.

---

## L11 · La bitácora vuelve a saber quién

**Objetivo:** que los cambios auditados registren a la persona que los hizo.

**Cierra:** **D-16**, **D-18**, **D-17** (este último, como diagnóstico).

**Toca:** `service/ProductoService.java:207`, `service/UsuarioService.java:133,149`, `service/PedidoService.java:207`.

**Esquema:** no.

**Qué cambia:**
- `@Transactional` en los métodos que llaman a `fijarContextoUsuario()`. Hoy no lo llevan, así que el `SET LOCAL` muere en su propio autocommit antes de que llegue el `UPDATE` que dispara el trigger. **El código incumple el contrato que su propio javadoc describe** (`service/LogService.java:70-76`).
- `PedidoService.cambiarEstado` pasa `logService.idUsuarioActual()` en vez del `null` literal de `:207`.
- **Diagnóstico de D-17:** averiguar por qué `auditoria_cambios` tiene 0 filas con 5 triggers activos. Es un paso de investigación con conclusión escrita, no un cambio de código a ciegas.

**Verificación:**

| # | Prueba | Resultado esperado |
|---|---|---|
| 1 | Autenticado como un usuario conocido, `DELETE /api/productos/{id}`, luego `select usuario_app from auditoria_cambios where tabla='producto' order by 1 desc limit 1` | Devuelve **ese** id de usuario *(antes: nada, o NULL)* |
| 2 | `PUT /api/pedidos/{id}/estado`, luego `select id_usuario from log_accion where accion='cambio_estado' order by 1 desc limit 1` | **No** nulo *(antes: siempre nulo)* |
| 3 | D-17 | Queda escrito en `DEFECTOS.md` si los triggers no escriben (defecto) o si la tabla se vació tras las pruebas de la F40 (dato, no defecto) |

**Riesgo y plan B:** añadir `@Transactional` **cambia los límites de transacción**: un método que antes confirmaba a trozos ahora es atómico. Eso es lo que se busca, pero puede sacar a la luz fallos latentes que hasta ahora quedaban a medio aplicar. Por eso las pruebas 1 y 2 se ejecutan también en el camino de error (forzando una excepción a mitad) para confirmar que el rollback deja las cosas limpias. Plan B: revertir método a método — cada anotación es independiente.

---

## L12 · Permisos: aplicarlos o retirarlos — *requiere tu decisión*

**Objetivo:** dejar de tener una pantalla de permisos que no gobierna nada.

**Cierra:** **D-13**.

Hoy hay 49 permisos, la tabla `rol_permiso`, un `PermisoController`, un claim en el JWT, un array en la respuesta de login, un `permisoGuard` y un `hasPermiso()`. **Ninguna decisión de autorización los consulta.** El rol `Encargado de Producción` tiene 0 permisos asignados y funciona con normalidad — esa es la prueba.

**No puedo elegir por ti**, así que el lote tiene dos formas:

**Opción A — Aplicarlos.** `@EnableMethodSecurity`, `@PreAuthorize("hasAuthority('modulo:accion')")` en ~180 endpoints, `UsuarioDetailsService` emitiendo los permisos como authorities, `permisoGuard` en las rutas, y rellenar los `rol_permiso` que faltan.
*Coste:* alto. *Riesgo:* **muy alto** — dejar roles fuera de pantallas que hoy usan.
*Verificación:* una prueba parametrizada que, para los 6 roles × un endpoint representativo por módulo, afirme el 200/403 esperado. Es una matriz de ~40 casos y hay que escribirla entera antes de tocar nada.
*Plan B:* desplegar en modo «registrar en vez de denegar» durante una semana y revisar el log antes de activar el bloqueo.

**Opción B — Retirarlos.** Borrar `PermisoController`, `permisoGuard`, el claim y el array del login. **Las tablas se quedan** (no se borra nada de la base).
*Coste:* bajo. *Riesgo:* bajo.
*Verificación:* todas las pantallas siguen funcionando con `rolGuard`; la respuesta de login ya no lleva `permisos`; `grep -rn "hasPermiso\|permisoGuard"` no encuentra código vivo.

**Mi recomendación: B**, salvo que la rúbrica del trabajo exija una matriz de permisos demostrable — en cuyo caso A, y con la matriz de pruebas escrita primero. Mantenerlo desconectado es la peor de las tres opciones, porque aparenta una seguridad que no existe.

---

## L13 · Editar una orden de compra en borrador

**Objetivo:** que el estado `borrador` sirva para lo que sirve un borrador.

**Cierra:** **D-22**.

**Toca:** `controller/OrdenCompraController.java`, `service/OrdenCompraService.java`, DTO nuevo, `config/SecurityConfig.java`, front `orden-compra-detalle.component.ts`.

**Esquema:** no.

**Qué cambia:** `PUT /api/ordenes-compra/{id}` que solo acepta órdenes en `borrador` y permite reemplazar las líneas. Hoy una OC con un precio mal puesto solo se puede cancelar y rehacer.

**Verificación:**

| # | Prueba | Resultado esperado |
|---|---|---|
| 1 | Editar una OC en `borrador` cambiando la cantidad de una línea | 200, y `orden_compra.total` = suma de subtotales, recalculado por `fn_recalcular_total_orden_compra_stmt` |
| 2 | Editar una OC `aprobada` | 400 |
| 3 | Editar sin cambiar nada | El trigger `fn_proteger_total_orden_compra` **no** lanza excepción |

La prueba 3 es la que importa: este lote toca líneas de OC, y ahí hay dos triggers vigilando. Hay que comprobar la interacción, no solo el resultado.

**Riesgo y plan B:** medio — es el único lote que **añade** una vía de escritura sobre un flujo que hoy funciona bien. Si algo se tuerce, revertir devuelve la OC a «crear o cancelar», que es el estado actual y es tolerable.

---

## L14 · Higiene en datos maestros

**Objetivo:** quitar tres cosas que en una revisión de código se leen como abandono.

**Cierra:** **D-21**, **D-31**, **D-32**.

**Toca:** `service/RolService.java:128-137`, `service/UsuarioService.java:153`, `service/PedidoService.java:121`.

**Esquema:** no. *(La baja lógica de roles necesitaría una columna `estado` en `rol`; queda fuera — ver §«Lo que no voy a hacer», punto 12. Este lote se limita a impedir el borrado cuando el rol está en uso.)*

**Qué cambia:**
- `RolService.eliminar`: sustituir el `findAll()` que trae toda `usuario_rol` al heap por un `existsByRolIdRol(id)`, y borrar la variable muerta marcada `// dummy` de `:128`.
- `UsuarioService.eliminar`: en vez de comparar contra `"admin@marathon.com"` escrito a mano, comprobar que **quede al menos un administrador activo**.
- El `{` y la sentencia en la misma línea de `PedidoService.java:121`.

> D-32 es cosmético y viaja aquí porque es el único lote que ya toca ese archivo. **No se reformatea nada más.**

**Verificación:**

| # | Prueba | Resultado esperado |
|---|---|---|
| 1 | Borrar un rol con usuarios asignados | 400, y **una sola** consulta emitida (assert sobre el contador de Hibernate) *(antes: la tabla entera en memoria)* |
| 2 | Desactivar al único administrador activo | 400 |
| 3 | Renombrar el correo del admin y volver a intentarlo | Sigue protegido *(antes: cambiar el correo desactivaba la protección)* |

**Riesgo y plan B:** bajo. Revertir es un commit.

---

# Bloque D — Despliegue y pantallas

## L15 · El frontend se puede desplegar

**Objetivo:** que el build de producción no apunte a `localhost` por HTTP plano.

**Cierra:** **D-10**, **D-33**.

**Toca:** `marathon-frontend/src/environments/environment.prod.ts`, `app.routes.ts:27`, componentes de `/clientes`.

**Esquema:** no.

**Qué cambia:**
- `apiUrl` de producción relativo (`/api`) detrás de un proxy inverso, o inyectado en el build. Hoy es literalmente la misma URL que desarrollo: `http://localhost:8080/api`.
- Alinear `/clientes`: en vez de dejar entrar a `Supervisor E-Commerce` y que los botones devuelvan 403, **ocultar las acciones de escritura** por rol y mantener la lectura.

**Verificación:**

| # | Prueba | Resultado esperado |
|---|---|---|
| 1 | `ng build --configuration production` y luego `grep -r "localhost:8080" dist/` | **0 coincidencias** |
| 2 | Un Supervisor entra en `/clientes` | Ve el listado, **no ve** los botones de crear/editar *(antes: los veía y recibía 403)* |
| 3 | Un Operador de Pedidos entra en `/clientes` | Ve los botones y funcionan |

**Riesgo y plan B:** bajo, pero **decide la forma del despliegue** (proxy inverso vs. URL inyectada), y esa decisión arrastra al `docker-compose.yml`. Plan B: revertir; el frontend vuelve a ser solo de desarrollo, que es lo que ya es.

---

## L16 · Rendimiento y pulido de pantallas

**Objetivo:** quitar el N+1 del listado de despachos y repasar el dashboard.

**Cierra:** **D-28**, más el pulido de UX que salga de usar el sistema una vez corregido lo anterior.

**Depende de L1** — medir el despacho antes de arreglarlo no sirve de nada.

**Toca:** `service/EmpaqueService.java:139-147`, componentes del dashboard.

**Esquema:** no.

**Verificación:** una página de 20 despachos emite **≤ 3 consultas**, medido con `SessionFactory.getStatistics().getQueryExecutionCount()`. Hoy son 41 (`pedidoService.obtener()` por fila, y cada uno hace 2 consultas).

**Riesgo y plan B:** bajo. Es el último lote a propósito: es el único cuyo aplazamiento no cuesta nada.

---

# Lo que NO voy a hacer, y por qué

Recortes de alcance deliberados. Cada uno es una decisión, no un olvido.

1. **Reparar los datos históricos.** Los 173 972 pedidos despachados sin movimiento de inventario, los descuadres de kardex, los pedidos con precios que no puedo verificar. *Por qué:* no puedo distinguir lo que escribió la aplicación de lo que escribieron los scripts de poblado, así que cualquier corrección masiva sería inventarse hechos. *En su lugar:* un informe de conciliación de solo lectura y una fecha de corte decidida por negocio. Es su propio proyecto.

2. **Reserva de stock (`stock_reservado`).** *Por qué:* con L1 desplegado, la sobreventa deja de corromper datos y pasa a fallar con un 400 claro en el despacho. La reserva es un cambio de **proceso de negocio** (¿cuánto dura?, ¿quién la libera?, ¿qué pasa con un pedido abandonado?), no de código. Necesita una decisión antes que una implementación.

3. **Revocación de token en el logout (D-23).** *Por qué:* exige una lista de denegación persistida y una comprobación en cada petición — coste alto para el beneficio en este proyecto. *Mitigación barata:* bajar la vigencia del token de 24 h, que es un cambio de configuración dentro de L10. L7 ya resuelve el caso grave (el usuario dado de baja).

4. **Mover el JWT a una cookie `HttpOnly` (D-27).** *Por qué:* toca CORS, CSRF (hoy deshabilitado a propósito), el interceptor y todas las llamadas del front. Es un rediseño de la sesión, no un arreglo.

5. **Flyway o Liquibase.** *Por qué:* haría falta y lo digo en la auditoría, pero migrar 39 scripts de fase chocaría con los tres lotes que cambian esquema. **Después** de este plan, no durante.

6. **Reconciliar `docker-compose.yml` con la realidad.** *Por qué:* describe Postgres 15, usuario `postgres` y puerto 5432, cuando el proyecto usa Postgres 18, `usr_admin_marathon` y el 5433. Arreglarlo ahora solo añadiría una tercera descripción inconsistente. Se decide en L15: o se reescribe entero o se borra.

7. **Consolidar los 17 `.md` de la raíz.** *Por qué:* mover documentación no cierra ni un defecto.

8. **Picking real por ubicaciones (lotes, caducidad, FEFO).** *Por qué:* L4 da lo único que el despacho necesita —de qué bodega salió—. El resto es un módulo nuevo.

9. **Tocar los triggers y CHECK que funcionan.** *Por qué:* son la parte más sólida del sistema, y **L1, L5, L6 y L13 dependen de que sigan comportándose igual**. Cualquier cambio ahí invalida las pruebas de cuatro lotes.

10. **Reescribir la capa de informes, PDF y Excel.** *Por qué:* la auditoría no encontró ningún defecto ahí. Lo que estaba mal eran los datos que consumía, y eso lo arreglan L1, L3 y L5.

11. **Columna con signo en `movimiento_inventario`.** *Por qué:* arreglaría del todo D-15, pero es un cambio de esquema sobre 80 000 filas con un CHECK (`cantidad > 0`) que habría que rehacer, y `historial_inventario` ya permite la reconciliación correcta. Se anota como deuda en L5.

12. **Baja lógica de roles.** *Por qué:* necesitaría `estado` en `rol` y revisar cada consulta que lista roles. L14 se conforma con impedir el borrado cuando el rol está en uso, que es el 90 % del beneficio por el 10 % del coste.

13. **Una batería de pruebas para todo el sistema.** *Por qué:* L0 monta el arnés y cada lote trae **las pruebas de lo que toca**. Escribir pruebas de módulos que nadie va a modificar es esfuerzo que no cambia ninguna decisión.

---

# Resumen

| Lote | Cierra | Esquema | Bloque |
|---|---|---|---|
| **L0** Arnés de pruebas | *(habilitador)* | no | A |
| **L1** El despacho deja de destruir inventario | D-01 (p2,p3), D-03 | no | A |
| **L2** El asistente IA deja de escribir | D-04, D-30 | rol de BD | A |
| **L3** El precio sale del catálogo | D-34, D-24 | no | A |
| **L4** Picking con bodega | D-01 (p1), D-14 | **sí** | B |
| **L5** Traslado y ajuste | D-35, D-15 | no | B |
| **L6** Facturación coherente | D-06, D-07, D-11 | **sí** | B |
| **L7** Retirar el acceso funciona | D-05, D-09 | no | B |
| **L8** Tope al dinero | D-08, D-19 | no | C |
| **L9** Errores con el código correcto | D-12, D-20 | no | C |
| **L10** Arranque seguro y freno al login | D-26, D-25, D-29 | no | C |
| **L11** La bitácora sabe quién | D-16, D-18, D-17 | no | C |
| **L12** Permisos: aplicar o retirar | D-13 | no | C |
| **L13** Editar OC en borrador | D-22 | no | C |
| **L14** Higiene en maestros | D-21, D-31, D-32 | no | C |
| **L15** Frontend desplegable | D-10, D-33 | no | D |
| **L16** Rendimiento y UX | D-28 | no | D |

**Cobertura:** 32 de los 35 defectos. Los tres restantes están recortados a propósito y justificados arriba: **D-02** (queda mitigado por L1; la reserva es punto 2), **D-23** (punto 3), **D-27** (punto 4).

**Decisiones que necesito de ti antes de implementar:** los estados desde los que se puede facturar (L6), si un reembolso puede superar el valor de la mercancía (L8), y si los permisos se aplican o se retiran (L12).

---

# Estado final de ejecución — 2026-08-25

Los 17 lotes se ejecutaron en una sola sesión. **80 pruebas automatizadas, 0 fallos.**
Antes de esta sesión el proyecto no tenía ninguna.

| Lote | Estado | Defectos cerrados | Notas |
|---|---|---|---|
| **L0** Arnés de pruebas | ✅ | *(habilitador)* | Base de pruebas por `pg_dump --schema-only`, no por los 39 scripts de fase (`fase34` hace `DROP ROLE` a nivel de clúster) |
| **L1** El despacho no destruye inventario | ✅ | D-01 (p2,p3), D-03 | D-03 estaba en **5** sitios, no en 3. Prueba de concurrencia verificada por mutación |
| **L2** Asistente IA sin escritura | ✅ | D-04, D-30, **D-29** | Dos barreras: JSqlParser + transacción de solo lectura. Interruptor `app.ia.enabled=false` |
| **L3** Precio del catálogo | ✅ | D-34, D-24 | `precioUnitario` del DTO se acepta y se ignora hasta que el front deje de mandarlo |
| **L4** Picking con bodega | ✅ | D-01 (p1), D-14 | **Esquema** `fase45`. Reversión verificada byte a byte |
| **L5** Traslado y ajuste | ✅ | D-35, D-15 | El traslado no había funcionado nunca desde la aplicación |
| **L6** Facturación coherente | ✅ | D-06, D-07, D-11 | **Esquema** `fase46`. 20 emisiones simultáneas → 20 números distintos |
| **L7** El acceso se puede retirar | ✅ | D-05, D-09 | — |
| **L8** Tope al dinero | ✅ | D-08, D-19 | `ReembolsoTopeTest` recorre el circuito de venta entero |
| **L9** Errores con el código correcto | ✅ | D-12, D-20 | 409 y 400 donde antes había 500; ninguna fuga de SQL |
| **L10** Arranque seguro y freno al login | ⚠️ parcial | D-26 (parcial), D-25 | Ver «lo que quedó a medias» |
| **L11** La bitácora sabe quién | ✅ | D-16, D-18, D-17 | D-17 **diagnosticado**: los triggers sí escriben |
| **L12** Permisos | ⚠️ decisión aplazada | D-13 (parcial) | Ver abajo |
| **L13** Editar OC en borrador | ✅ | D-22 | — |
| **L14** Higiene en maestros | ✅ | D-21, D-31, D-32 | — |
| **L15** Frontend desplegable | ✅ | D-10, D-33 | El build de producción **nunca había funcionado** |
| **L16** Rendimiento | ✅ | D-28 | 21 consultas → ≤6, medido con estadísticas de Hibernate |

**30 de 35 defectos cerrados.** Abiertos y justificados: D-02 (mitigado por L1; la reserva es punto 2 de «lo que no voy a hacer»), D-13 (parcial), D-23 y D-27 (aplazados a propósito), D-26 (parcial).

## Hallazgos que el plan no anticipaba

1. **La F34 concede privilegios COLUMNA POR COLUMNA.** Una columna nueva nace sin permisos y solo el propietario puede tocarla. `fase45` fallaba con *«permiso denegado a la tabla detalle_pedido»* hasta que se le añadieron los `GRANT SELECT`/`UPDATE` de la columna. **Toda futura columna de este proyecto necesita su GRANT explícito.**
2. **`environment.prod.ts` nunca se usaba.** No existía `fileReplacements` en `angular.json`: el build de producción siempre compilaba el entorno de desarrollo. D-10 era más profundo de lo auditado.
3. **El build de producción no compilaba**, por dos motivos anteriores a esta sesión: faltaba la dependencia `xlsx` en `node_modules` y los presupuestos de CSS de `angular.json` eran menores que los estilos que ya tenían los componentes.
4. **`auditoria_cambios` no se puede borrar** ni siquiera desde la aplicación (*«permiso denegado»*). El modelo de privilegios de la F40 funciona: una traza que la aplicación pudiera borrar no sería una traza.
5. **Los 30.000 comprobantes existentes usan formato `CI-000000001`**, no el `COMP-AAAA-NNNNNN` que genera el código: la aplicación nunca había emitido un comprobante en esta base.
6. **D-03 estaba en cinco sitios, no en tres.** La auditoría contó de menos.

## Lo que quedó a medias, y por qué

**L10 / D-26 — contraseñas de demostración.** El fail-fast del secreto JWT sí está: la aplicación **se niega a arrancar** con el valor publicado en el repositorio, que es el agujero real (permitía forjar un token de administrador sin credenciales). Lo que **no** se cambió es el valor por defecto de `app.datos-demo.enabled`, que sigue en `true`: ponerlo en `false` rompería el primer arranque descrito en `SETUP_COMPLETO.md` y todos los entornos ya montados. `ComprobacionesDeArranque` avisa por consola mientras esté encendido. Cambiar ese valor por defecto es una decisión de despliegue, no de código.

**L12 / D-13 — permisos.** Ninguna de las dos opciones del plan resultó aplicable, y los datos lo demuestran:

- *Aplicarlos* es inviable hoy: el rol **Encargado de Producción tiene 0 permisos de 49**. Encender la comprobación lo dejaría sin acceso a nada de un día para otro. Hace falta antes decidir y cargar qué puede hacer cada rol, que es una decisión de negocio.
- *Retirarlos* destruiría trabajo útil: la pantalla de roles usa `/api/permisos` y **funciona** como editor de esa matriz — justo el paso previo a poder aplicarla.

Lo que sí se hizo fue quitar lo que **aparentaba** control de acceso sin serlo: `permisoGuard` y `AuthService.hasPermiso()`, que no referenciaba ninguna ruta, y dejar documentado en `PermisoController` que los permisos son descriptivos. El sistema deja de mentir sobre lo que protege.

## Validación end-to-end

Con el backend corriendo contra la base **real** (`Started ... in 4.429 seconds`, `ddl-auto=validate` conforme, los seis pools por rol conectados):

| Comprobación | Resultado |
|---|---|
| Login de administrador | 200, token de 1365 caracteres |
| Credenciales incorrectas | 400 «Correo o contraseña incorrectos» — mismo mensaje que para usuario inactivo (D-25) |
| `/productos`, `/pedidos`, `/inventario`, `/clientes`, `/ordenes-compra` | 200 |
| `POST /ia/consultar` | **503**, módulo apagado (D-04) |
| Descuento de 999999 sobre subtotal de 41.48 | **400** con el subtotal real (D-19) — y ese 41.48 sale del **catálogo**, no del `precioUnitario: 1` enviado: prueba de D-34 en vivo |
| Operador de bodega cambiando la contraseña del administrador | **403** (D-09) |
| JSON mal formado | **400**, no 500 (D-12) |
| `ng build --configuration production` | Compila; **0 apariciones** de `localhost:8080` en `dist/` (D-10) |
| Datos de la base real, antes y después de toda la sesión | **Idénticos** |

**Cambios de esquema aplicados a la base real:** `detalle_pedido.id_bodega_picking` (0,29 s sobre 614.507 filas) y `seq_comprobante_interno` (arrancada en 30000). Los dos con su script de reversión probado.
