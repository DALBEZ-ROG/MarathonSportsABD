# Guía del flujo — MarathonSportsABD

> **Para qué es esto.** Para sentarte delante del sistema y recorrerlo entero, en
> orden, sabiendo con qué usuario entrar en cada paso y qué va a pasar por
> debajo. No es un manual de pantallas: es la **cadena de trabajo**, que es otra
> cosa — el menú lateral agrupa por módulo, y el orden real hay que saberlo.
>
> Todo lo que dice este documento está **sacado del código y de la base**, no de
> lo que parece razonable. Cada regla se puede rastrear al fichero que la aplica.
>
> Escrito el **2026-08-28**.

---

## 0. Antes de empezar

### Los servidores

| | Dónde | Cómo se levanta |
|---|---|---|
| Backend | `http://localhost:8080` | `scripts/cifrado/iniciar_backend.ps1` |
| Frontend | **`http://localhost:4300`** | `cd marathon-frontend && npx ng serve` |
| PostgreSQL | **puerto 5433** | servicio de Windows |

> **El 4300, no el 4200.** En este equipo hay otro proyecto Angular que toma el
> 4200. Si abres el navegador y ves una aplicación que no es Marathon, es la
> caché: `Ctrl+Shift+R`.

### Con quién entrar

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin@marathon.com` | `Admin1234!` | Administrador |
| `compras@marathon.com` | `Demo1234!` | Encargado de Compras |
| `produccion@marathon.com` | `Demo1234!` | Encargado de Producción |
| `bodega@marathon.com` | `Demo1234!` | Operador de Bodega |
| `pedidos@marathon.com` | `Demo1234!` | Operador de Pedidos |
| `supervisor@marathon.com` | `Demo1234!` | Supervisor E-Commerce |

> Desde la fase 61 estos usuarios **ya no se crean solos** en cada arranque, pero
> siguen existiendo en la base. Si montas el sistema desde cero, el primer
> administrador lo crea `sql/fase61_siembra_inicial.sql`.

### Dos cosas que parecen fallos y no lo son

1. **Los datos de prueba terminan el 2026-08-17.** Cualquier indicador que mida
   «hoy» sale **vacío**. Por eso el tablero trabaja con ventanas de 7 / 30 / 90
   días.
2. **Un pedido enviado sin comprobante es normal.** Son 19.932 de 24.370, todos
   del poblado masivo. La pantalla lo dice: «Este pedido aún no tiene
   comprobante».

### La pantalla de inicio

Al entrar caes en **el flujo**: los ocho pasos con sus opciones y, en el icono
`ⓘ` de cada uno, quién es responsable. Lo que tu rol no puede abrir sale con
candado y con el nombre de quien sí. Los indicadores están en **`/indicadores`**.

---

## La regla que gobierna todo

> **Quien pide no aprueba, quien vende no recoge, y quien devuelve no juzga.**

No es un consejo: está impuesto con permisos separados y, en el caso de las
compras, con una comprobación extra sobre la persona concreta. Si intentas
saltártelo el sistema te para con un mensaje, no con un error.

---

# Paso 1 · Preparar el catálogo

**Rol: Administrador** — `admin@marathon.com`

Nada de lo que viene después funciona si esto no está puesto.

| Orden | Pantalla | Qué haces |
|---|---|---|
| 1 | `Datos maestros → Ciudades` | Las ciudades, que las necesitan los clientes |
| 2 | `Datos maestros → Categorías` y `Unidades de medida` | Cómo se clasifica y se mide |
| 3 | `Datos maestros → Productos` | El catálogo. **Aquí se decide si un producto se compra o se fabrica** |
| 4 | `Datos maestros → Proveedores` | A quién se le compra |
| 5 | `Datos maestros → Bodegas` | Dónde se guarda |
| 6 | `Usuarios` y `Roles y permisos` | Quién entra y qué puede hacer |

### Lo que hay que entender aquí

- **`origen` del producto decide su camino.** `comprado` va por el paso 2;
  `fabricado` va por el paso 3. El sistema **no deja** crear una orden de
  producción de un producto marcado como comprado.
- **Un producto sin proveedor no se puede comprar.** Desde la fase 57 la pantalla
  de nueva orden solo ofrece los productos de ese proveedor. Si al elegir un
  proveedor no sale nada, es que a ese producto le falta la relación.
- **Los 11 productos de marca propia que la fase 59 marcó como fabricados no
  tienen lista de materiales todavía.** Hasta que alguien la defina no se les
  puede lanzar producción, y el sistema lo dice claro.

---

# Paso 2 · Comprar al proveedor

La mercancía entra por aquí. **Son cinco actos y no todos son del mismo rol.**

### 2.1 · Crear la orden — *Encargado de Compras*

`compras@marathon.com` → **Compras → Nueva orden de compra**

1. Eliges el proveedor. **La lista de productos se filtra a los de ese
   proveedor** — si eliges Nike, salen Nike.
2. Añades líneas. Al elegir el producto **el precio unitario se rellena solo**
   con el precio de compra pactado, y puedes cambiarlo.
3. Guardas. La orden nace en **`borrador`**.

**Lo que valida:** el proveedor tiene que estar activo, el producto también, y
cada línea lleva **producto O materia prima, nunca las dos ni ninguna**.

### 2.2 · Enviarla a aprobación — *Encargado de Compras*

En la orden, cambias su estado a **`pendiente_aprobacion`**.
Requiere el permiso `compras:crear`. Solo se puede desde `borrador`.

> A partir de aquí la orden **ya no se puede modificar**. Editar solo se permite
> en `borrador`.

### 2.3 · Aprobar o rechazar — **Administrador, y nadie más**

`admin@marathon.com` → **Compras → la orden → Aprobar**

> ### ⚠ Esta es la pregunta que más se hace, así que va con todas las letras
>
> **El Encargado de Compras NO aprueba sus órdenes.** Los permisos
> `compras:aprobar` y `compras:rechazar` los tiene **únicamente el
> Administrador**.
>
> Y hay una segunda barrera encima de la primera: **nadie puede aprobar una orden
> que él mismo solicitó**, ni siquiera el Administrador. Si el admin crea una
> orden y luego intenta aprobarla, el sistema responde:
>
> > *«No puede aprobar ni rechazar una orden que usted mismo solicitó (separación
> > de funciones)»*
>
> Esa comprobación **no es un permiso** y no se puede quitar desde la pantalla de
> roles: no depende de quién eres, sino de qué orden es.
>
> **Para probarlo de verdad hacen falta dos personas:** crea la orden con
> `compras@marathon.com` y apruébala con `admin@marathon.com`.

Al aprobar, el sistema guarda **quién aprobó y cuándo**.

### 2.4 · Recibir la mercancía — *Encargado de Compras*

`compras@marathon.com` → **Compras → la orden → Registrar recepción**

> No está en el menú. Se entra **desde la orden**, y es el orden correcto: no se
> puede recibir lo que no se ha aprobado.

- Solo desde `aprobada` o `recibida_parcial`.
- **No deja recibir más de lo pedido.**
- Indicas la bodega: **ahí es donde entra el stock**.
- Si falta algo, la orden queda en `recibida_parcial` y puedes volver; si llega
  todo, pasa a `recibida_completa`.

### 2.5 · Registrar la factura — *Encargado de Compras*

**Compras → la orden → Registrar factura**

- Exige **al menos una recepción**. No se factura lo que no ha llegado.
- El número de factura no se puede repetir en la misma orden.
- El vencimiento no puede ser anterior a la factura.

> ### ⚠ El subtotal no puede superar lo recibido
>
> Decisión de negocio del 2026-08-28: **sin flete y sin tolerancia**. Si el
> subtotal se pasa aunque sea un céntimo, la factura se rechaza:
>
> > *«El subtotal de la factura (9999.00) supera el valor de la mercancía
> > recibida (4971.95) en 5027.05…»*
>
> El intento queda en la bitácora (`compras` / `factura_rechazada_descuadre`).
> **Facturar de menos sí se permite:** un proveedor puede facturar en partes lo
> que entregó de golpe.

Al registrarla se crea automáticamente la **cuenta por pagar**.

### 2.6 · Pagar — *Encargado de Compras*

**Cuentas por pagar → la cuenta → Registrar pago**

No deja pagar más del saldo pendiente. Admite pagos parciales.

### 2.7 · Si llegó algo defectuoso — *Encargado de Compras*

**Devoluciones a proveedor** e **Ítems defectuosos**. Crear y resolver son los
dos del Encargado de Compras.

### Estados de la orden de compra

```
borrador ──► pendiente_aprobacion ──► aprobada ──► recibida_parcial ──► recibida_completa
   │                  │                   │
   │                  └──► rechazada      │
   └──────────────────┴───────────────────┴──► cancelada
```

**Cancelar** solo desde `borrador`, `pendiente_aprobacion` o `aprobada`, y
**nunca si alguna línea ya tiene mercancía recibida**.

---

# Paso 3 · Fabricar lo propio

**Rol: Encargado de Producción** — `produccion@marathon.com`

Solo para productos de **marca propia** (`origen = fabricado`). Lo de marca
ajena se compra en el paso 2.

### 3.1 · Que haya materia prima

**Materia prima** → existencias y kardex de cada insumo.

### 3.2 · Lanzar la orden

**Producción → Nueva orden de producción**

- Solo salen los **14 productos fabricados**.
- El producto **necesita lista de materiales (BOM)**. Sin ella no se puede.
- La cantidad tiene que ser al menos 1.
- Nace en **`planificada`**.

### 3.3 · Iniciarla

**Producción → la orden → Iniciar**

> **Aquí es donde se consume la materia prima de verdad.** El sistema comprueba
> que haya suficiente de cada insumo según el BOM y, si falta, **no arranca** y
> te dice qué falta. Pasa a `en_proceso`.

### 3.4 · Completarla

**Producción → la orden → Completar**

- La cantidad producida es obligatoria y **no puede superar la planificada**.
- Si hubo consumo extra, vuelve a comprobar que haya materia prima para cubrirlo.
- Pasa a `completada` y **el producto terminado entra en inventario**.

### Estados de la orden de producción

```
planificada ──► en_proceso ──► completada
     │
     └──► cancelada
```

> **Cancelar solo se puede en `planificada`.** Una orden `en_proceso` ya consumió
> la materia prima; el sistema se niega y lo explica.

### Para mirar, no para tocar

**Tablero de manufactura** y **Análisis de costos** — los ve también el
Supervisor E-Commerce.

---

# Paso 4 · Controlar las existencias

**Rol: Operador de Bodega** — `bodega@marathon.com`

**Inventario** → stock por bodega, movimientos y reservas.

> ### Disponible no es lo mismo que stock
>
> ```
> disponible = stock físico − reservas activas
> ```
>
> Un pedido procesado **reserva** unidades que siguen en la estantería pero ya
> tienen dueño.

- **Registrar movimiento** (entradas, salidas, traspasos): Administrador y
  Operador de Bodega.
- **Reservas vencidas**: las que llevan más de 7 días reteniendo stock.
  **El sistema NO las suelta solo** — las pone delante de una persona, que decide
  y escribe un motivo. Soltar mercancía sin que nadie mire es peor que el
  problema que resuelve.
- **Liberar una reserva**: Administrador y Operador de Bodega.

> El Encargado de Compras entra a Inventario —necesita ver existencias para saber
> qué comprar— pero **no ve las reservas**: cuelgan de pedidos de clientes, y
> quien compra no lee los pedidos de los clientes.

---

# Paso 5 · Vender: tomar el pedido

**Rol: Operador de Pedidos** — `pedidos@marathon.com`

### 5.1 · El cliente

**Clientes** → si es nuevo, se da de alta (necesita ciudad, del paso 1).

### 5.2 · El pedido

**Pedidos → Nuevo pedido**

1. Eliges cliente. La lista se escribe y se filtra.
2. Añades productos. **El precio unitario se rellena solo** con el precio de
   venta del catálogo, y **no se puede editar**: el backend lo descarta y usa
   siempre el del catálogo. Es deliberado — el precio no se negocia en la
   pantalla.
3. Guardas. El pedido nace en **`pendiente`**.

> ### El stock se comprueba al crear, pero no se retiene
>
> Si no alcanza, **el pedido no se crea**. Pero crear **no reserva nada**: hay
> 16.099 pedidos en `pendiente`, y retener mercancía por cada uno bloquearía el
> almacén entero.

> **Excepción — los pedidos especiales.** Un pedido `personalizado`,
> `corporativo` o `regalo` **sí se crea sin stock**: existe precisamente para
> prepararse o fabricarse, y tiene fecha límite de entrega. El déficit queda en
> la bitácora (`pedidos` / `crear_sin_stock`), no callado.

### 5.3 · Procesarlo

**Pedidos → el pedido → cambiar estado a `procesado`**

> **Aquí se reservan las unidades.** Es **todo o nada**: si una sola línea no
> cabe, no se reserva ninguna y el pedido se queda en `pendiente`.

### 5.4 · El comprobante

**Pedidos → el pedido → Generar comprobante** (permiso `comprobantes:emitir`:
Administrador y Operador de Pedidos). Se puede descargar en PDF.

### Estados del pedido

```
pendiente ──► procesado ──► enviado ──► entregado
    │             │
    └─────────────┴──► anulado
```

- `enviado` **no lo pones tú**: lo pone el empaque al despachar (paso 6).
- Desde `entregado` o `anulado` **ya no se sale**.
- **Anular libera las reservas**, y queda anotado.

---

# Paso 6 · Preparar y entregar

**Rol: Operador de Bodega** — `bodega@marathon.com`

> **El orden es obligatorio y lo impone el sistema**: primero picking, luego
> empaque, luego el despacho es consecuencia.

### 6.1 · Picking

**Picking** → la cola de pedidos por recoger → entras a uno → marcas línea por
línea lo recogido y de qué bodega.

> **Un pedido no aparece en la cola de empaque hasta que está recogido del
> todo.** Antes de la fase 52 la cola pedía los 100 primeros de 19.059
> procesados, así que un pedido recién recogido no se podía empacar nunca.

### 6.2 · Empaque

**Empaque** → la lista de pedidos listos, **el más reciente primero** → confirmas
el empaque e indicas el **número HU** (la etiqueta del bulto).

> ### Confirmar el empaque es el momento en que todo se hace real
>
> En una sola transacción:
> 1. **descuenta el stock**, línea por línea, de la bodega de la que se recogió;
> 2. **consume las reservas** del pedido;
> 3. deja el movimiento de inventario con su rastro;
> 4. pone el pedido en **`enviado`**.
>
> Si una línea no se puede cubrir, **se deshace todo**: no hay pedidos enviados a
> medias.

### 6.3 · Despachos

**Despachos** → lo que ya salió y hacia dónde. Es una consulta, no una acción.
Lo ve también el Supervisor E-Commerce.

### 6.4 · Entregado

Cuando el cliente recibe, se pasa el pedido a **`entregado`**
(Administrador, Operador de Bodega u Operador de Pedidos).

---

# Paso 7 · Atender la posventa

> **Tres actos, tres roles distintos.** No es casualidad: quien pide la devolución
> no es quien juzga el estado de la mercancía.

### 7.1 · Abrir la solicitud — *Operador de Pedidos*

`pedidos@marathon.com` → **Pedidos → el pedido → Solicitar devolución**
Indicas motivo (talla incorrecta, defecto…) y las líneas. Nace en **`solicitada`**.

### 7.2 · Inspeccionar — **Operador de Bodega**

`bodega@marathon.com` → **Devoluciones → la solicitud → Iniciar inspección**
(pasa a `en_inspeccion`) → **Inspeccionar**: línea por línea, aceptar o rechazar.

- Si se rechazan **todas** las líneas → `rechazada`.
- Si se acepta alguna → `completada`, y **lo aceptado en buen estado vuelve al
  inventario**.

### 7.3 · Reembolsar — *Operador de Pedidos*

`pedidos@marathon.com` → **Devoluciones → la solicitud → Registrar reembolso**.
Hay un tope: no se puede reembolsar más de lo que se cobró.

### Estados de la devolución

```
solicitada ──► en_inspeccion ──► completada
                     │
                     └──► rechazada
```

---

# Paso 8 · Medir y auditar

| Pantalla | Quién | Para qué |
|---|---|---|
| **Indicadores** (`/indicadores`) | todos, cada uno los suyos | Las cifras de tu rol |
| **Reportes** | Administrador, Supervisor E-Commerce | Ventas, inventario y manufactura |
| **Asistente IA** | Administrador, Supervisor E-Commerce | Preguntar a los datos en castellano |
| **Auditoría** | **solo Administrador** | Quién hizo qué y cuándo |

> La auditoría no la ve quien opera, y es a propósito: es la traza que vigila a
> los que operan.

---

# Recorrido completo, de una sentada

Para probar la cadena entera de punta a punta. Vas a **cambiar de usuario cinco
veces** — es justo lo que demuestra que la separación de funciones existe.

| # | Entra como | Haz esto |
|---|---|---|
| 1 | `admin` | Comprueba que hay un producto activo, con proveedor y con bodega |
| 2 | `compras` | Nueva orden de compra → añade líneas → **guardar** |
| 3 | `compras` | Cambia la orden a **`pendiente_aprobacion`** |
| 4 | `compras` | **Intenta aprobarla.** No podrás: no tienes el permiso |
| 5 | **`admin`** | **Aprueba la orden.** Se guarda quién aprobó y cuándo |
| 6 | `compras` | Registra la **recepción** e indica la bodega |
| 7 | `compras` | Registra la **factura**. Prueba a poner un subtotal mayor: te para |
| 8 | `compras` | **Cuentas por pagar** → registra el pago |
| 9 | `bodega` | **Inventario** → comprueba que el stock subió en esa bodega |
| 10 | `pedidos` | **Nuevo pedido** de ese producto. El precio se rellena solo |
| 11 | `pedidos` | Pásalo a **`procesado`** → mira en Inventario: el disponible bajó, el stock no |
| 12 | `pedidos` | **Genera el comprobante** |
| 13 | `bodega` | **Picking** → recoge todas las líneas |
| 14 | `bodega` | **Empaque** → confirma con número HU → **el pedido pasa a `enviado` y el stock baja de verdad** |
| 15 | `bodega` | **Despachos** → ahí está |
| 16 | `pedidos` | Pásalo a **`entregado`** |
| 17 | `pedidos` | Abre una **devolución** de una línea |
| 18 | **`bodega`** | **Inspecciónala** y acepta la línea → vuelve al inventario |
| 19 | **`pedidos`** | Registra el **reembolso** |
| 20 | `admin` | **Auditoría** → ahí está todo lo que acabas de hacer, con nombre y hora |

---

## Tabla rápida: quién hace qué

| Acción | Administrador | Compras | Producción | Bodega | Pedidos | Supervisor |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| Datos maestros, usuarios, roles | ✔ | | | | | |
| Crear orden de compra | ✔ | ✔ | | | | |
| **Aprobar / rechazar orden de compra** | **✔** | | | | | |
| Cancelar orden de compra | ✔ | ✔ | | | | |
| Registrar recepción | ✔ | ✔ | | | | |
| Registrar / anular factura de compra | ✔ | ✔ | | | | |
| Registrar pago a proveedor | ✔ | ✔ | | | | |
| Devoluciones a proveedor | ✔ | ✔ | | | | |
| Crear / iniciar / completar producción | ✔ | | ✔ | | | |
| Materia prima (leer) | ✔ | ✔ | ✔ | | | ✔ |
| Materia prima (escribir, movimientos) | ✔ | | ✔ | | | |
| Movimientos de inventario | ✔ | | | ✔ | | |
| Liberar reserva vencida | ✔ | | | ✔ | | |
| Crear pedido | ✔ | | | | ✔ | |
| Cambiar estado del pedido | ✔ | | | ✔ | ✔ | |
| Anular pedido | ✔ | | | ✔ | ✔ | |
| Emitir comprobante | ✔ | | | | ✔ | |
| Picking | ✔ | | | ✔ | | |
| Confirmar empaque | ✔ | | | ✔ | | |
| Crear devolución de cliente | ✔ | | | | ✔ | |
| **Inspeccionar devolución** | ✔ | | | **✔** | | |
| **Reembolsar** | ✔ | | | | **✔** | |
| Reportes y Asistente IA | ✔ | | | | | ✔ |
| **Auditoría** | **✔** | | | | | |

---

## Si algo se para, mira aquí primero

| Lo que ves | Casi siempre es |
|---|---|
| «No tienes acceso a esa sección» | Ese rol no entra ahí. Míralo en el `ⓘ` del flujo |
| «No puede aprobar… que usted mismo solicitó» | Correcto. Necesitas **otra persona** |
| «Solo se puede recibir mercancía de órdenes aprobadas» | Falta el paso 2.3 |
| «…la orden de compra no tiene recepciones registradas» | Falta el paso 2.4 |
| «El subtotal… supera el valor de la mercancía recibida» | Correcto. Sin flete, sin tolerancia |
| «No se puede iniciar: falta materia prima» | Compra insumos o ajusta la orden |
| «Solo se puede cancelar una orden en estado planificada» | Ya consumió materia prima |
| Un pedido no sale en Empaque | No está recogido **del todo** |
| Un indicador sale vacío | Los datos terminan el **2026-08-17** |
| Sale otra aplicación en el navegador | Caché. `Ctrl+Shift+R`, y usa el **4300** |

---

## De dónde sale cada cosa

| Regla | Fichero |
|---|---|
| Estados y aprobación de la orden de compra | `service/OrdenCompraService.java` |
| Factura contra lo recibido | `service/FacturaCompraService.java` |
| Reserva y consumo de stock | `service/ReservaStockService.java` |
| Estados del pedido | `service/PedidoService.java` |
| Descuento real de stock | `service/EmpaqueService.java` |
| Producción y consumo de materia prima | `service/OrdenProduccionService.java` |
| Devoluciones de cliente | `service/SolicitudDevolucionService.java` |
| Quién puede entrar a cada URL | `config/SecurityConfig.java` |
| Quién tiene cada permiso | `sql/fase48_matriz_permisos.sql` |
| El flujo de la pantalla de inicio | `modules/flujo/flujo.model.ts` |

Para el detalle de por qué cada cosa es como es: `docs/PENDIENTE.md`.
