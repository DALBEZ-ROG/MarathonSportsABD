# Lo que queda pendiente — MarathonSportsABD

> **Para quién es este documento.** Para retomar el trabajo **sin haber estado en
> las sesiones anteriores**. Todo lo necesario para empezar está aquí; los otros
> tres documentos de `docs/` amplían, pero no son requisito.
>
> Reescrito el **2026-08-28**, después de cerrar **los cuatro que quedaban**:
> D-36, D-23, D-27 (fase 60) y D-26 (fase 61).
>
> **No queda ningún defecto abierto.** Tres de esos cuatro llevaban tiempo
> descartados «por coste»; el dueño del proyecto pidió que se hicieran de
> verdad. El cuarto esperaba una respuesta de negocio, que llegó: **sin flete**.
>
> Sigue en pie la lección de la sesión anterior, que es la que más cuesta:
> **los cinco defectos previos aparecieron levantando y usando la aplicación, no
> en las pruebas** — estaban las 131 en verde mientras tres roles no podían crear
> su documento. Esta vez volvió a pasar en pequeño: la validación de D-36 se
> escribió, pasó, y **dejaba la bitácora vacía**; se vio mirando la base después
> de probar contra la aplicación en marcha.

---

## 1. De dónde venimos, en un párrafo

El proyecto pasó por auditoría (`AUDITORIA.md`, `DEFECTOS.md`), plan (`PLAN.md`),
ejecución de 17 lotes, rediseño del tablero (`DASHBOARD.md`) y, el 2026-08-27,
las fases **47** (reserva de stock), **48** (matriz de permisos), **49** (D-39) y
**50 a 53**, estas ultimas de depurar la aplicacion en el navegador, mas un
repaso de flujos que encontro tres huecos mas.

Se han encontrado **43 defectos** y se han cerrado **los 43**. Las pruebas
pasaron de **0 → 105 → 131 → 143**, todas en verde. La compilación de producción
del frontend funciona.

A las fases 47-59 se suman ahora:

| Fase | Qué cierra |
|---|---|
| **F60** | D-36 (la factura se contrasta con lo recibido), D-23 (revocación real de sesión) y D-27 (el token sale de `localStorage` a una cookie `HttpOnly`) |
| **F61** | D-26 (los datos demo dejan de crearse solos) **y un defecto latente que nadie había visto**: una instalación nueva nacía con la matriz de permisos vieja, de 49, en vez de la de la F48, de 94 |
| **F62** | La pantalla de inicio pasa a ser **el flujo del sistema**: ocho pasos en orden, con sus opciones y con quién es responsable de cada uno |
| **F63** | Tres cosas que salieron de **recorrer el flujo entero con cada rol**, no solo con el administrador |

---

## 2. Antes de tocar nada: once cosas que van a morderte

Esto no es contexto de adorno. Cada punto costó una sesión descubrirlo.

1. **PostgreSQL escucha en el 5433, no en el 5432.** Apuntar mal no da un error
   claro, da un fallo confuso más adelante. La base es `mod_venta_inve`; la de
   pruebas, `mod_venta_inve_test`, en el mismo clúster.

2. **Los datos de prueba terminan el 2026-08-17.** Cualquier consulta que mida
   «hoy» sale **vacía**, y eso **no es un fallo**. El tablero está construido
   sobre ventanas de 7 / 30 / 90 días precisamente por esto.

3. **`spring.jpa.hibernate.ddl-auto=validate`.** La aplicación **nunca** migra el
   esquema. Lo hacen los scripts `faseNN_*.sql` ordenados a mano (ya son 45). Un
   cambio de esquema es un script nuevo, no una anotación de entidad. **Y hay que
   aplicarlo a las DOS bases**, la real y la de pruebas.

4. **La fase 34 concede privilegios COLUMNA POR COLUMNA.** Una columna nueva nace
   **sin permisos**. `fase45` falló con *«permiso denegado a la tabla
   detalle_pedido»* hasta que se le añadieron los `GRANT` de esa columna, y la
   `fase47` volvió a tropezar con lo mismo por no conceder `DELETE` sobre una
   tabla nueva. **Toda columna y toda tabla nuevas necesitan su GRANT
   explícito**, y el `USAGE` de su secuencia si la PK es `IDENTITY`.

5. **Hay seis pools de conexión, uno por rol** (`RoleRoutingDataSource`). Una
   consulta nueva debe ser ejecutable **por el rol que la va a pedir**, no solo
   por el administrador. Consecuencia concreta que apareció en la F47:
   `rol_operador_pedidos` tiene SELECT pero no UPDATE sobre `inventario`, y
   PostgreSQL exige UPDATE para hacer `SELECT … FOR UPDATE`; por eso la reserva
   se serializa con `pg_advisory_xact_lock` y no con bloqueo de fila. El
   enrutado está desactivado en el perfil de pruebas.

6. **Toda entidad nueva necesita `@DynamicInsert` si su tabla se llena por
   etapas.** Hibernate escribe por omisión un INSERT **estático**: nombra todas
   las columnas mapeadas, tengan valor o no. Combinado con el punto 4, eso hace
   que el rol que *arranca* un flujo no pueda insertar nada, porque el INSERT
   nombra columnas que solo rellena una etapa posterior. Es exactamente lo que
   tenía a tres de los seis roles sin poder crear su propio documento desde la
   F37 (D-39). **No es una optimización: es un requisito de este esquema.**

7. **Para leer la matriz de permisos reales de PostgreSQL usa
   `aclexplode(c.relacl)` sobre `pg_class`** (y `pg_attribute.attacl` para los de
   columna). Las vistas de `information_schema` devuelven vacío para
   `usr_admin_marathon` y te harán creer que no hay privilegios concedidos.

8. **`DataInitializer` está entero bajo
   `@ConditionalOnProperty("app.datos-demo.enabled")`.** No solo crea usuarios
   demo: también crea **los roles**. El día que se apague (D-26) hay que sembrar
   `rol` por script antes de nada.

9. **Toda lista paginada necesita `Sort`.** Sin `ORDER BY`, PostgreSQL devuelve
   las filas en el orden del montón y un `UPDATE` reescribe la fila al final: la
   que acabas de editar desaparece de su página, y dos páginas consecutivas
   pueden repetir una fila y esconder otra. Estaba en **20 sitios** (D-41).

10. **Las pruebas no ven la interfaz, y ahí vive una clase entera de defectos.**
    Corren con `roles.enabled=false`, sin navegador y sin pantallas. Un campo que
    nadie mapea, una lista sin orden, una ruta que no existe, una consulta que
    trae los 100 primeros de 19.000: **nada de eso lo detecta `mvn test`**. Cinco
    defectos (D-39 a D-43) aparecieron levantando la aplicación con las 131
    pruebas en verde. Antes de dar algo por bueno, **úsalo**.

11. **Un apunte en la bitácora dentro de un método que va a lanzar una excepción
    NO SE GUARDA.** `LogService.registrar` comparte la transacción de quien lo
    llama; si el método acaba lanzando —un rechazo, una validación—, la
    transacción se deshace y **se lleva el apunte con ella**. Es decir: falla
    justo en el caso que se quería registrar. Pasó en la F60 con D-36: la
    factura se rechazaba bien y `log_accion` quedaba vacía. Para dejar rastro de
    algo que se rechaza hay que usar **`LogService.registrarAparte`**, que corre
    en una transacción propia (`REQUIRES_NEW`).

**Credenciales:** viven en `.env` y `application-local.properties`, ambos en
`.gitignore`. `TEST_DB_PASSWORD` se pasa por variable de entorno. **Nunca se
commitean.** La clave de cifrado de los respaldos es distinta en cada equipo.

---

## 3. Lo que se cerró el 2026-08-27

### F47 · Reserva de stock (cierra D-02)

Antes, crear un pedido no consultaba `inventario` en ningún momento, y la falta
de existencias solo frenaba la operación en el muelle. Ahora:

| Momento | Qué pasa |
|---|---|
| Crear el pedido | Comprueba el **disponible** y lo rechaza si no alcanza. **No retiene nada.** |
| `pendiente` → `procesado` | **Reserva** las unidades. Todo o nada: si una línea no cabe, no se reserva ninguna y el pedido se queda en `pendiente`. |
| Despacho (empaque) | **Consume** la reserva y descuenta el stock. |
| Anulación | **Libera** la reserva. |
| A los 7 días | La reserva **aparece en un informe**. **No se libera sola.** |

`disponible(p) = SUM(inventario.stock_actual) − SUM(reservas activas)`

**Las tres decisiones de negocio** (tomadas por el dueño del proyecto el
2026-08-27, que es lo que el documento anterior exigía antes de escribir código):
se reserva al procesar y no al crear —hay 16.099 pedidos en `pendiente` y
retener ahí bloquearía mercancía por cada pedido abandonado—; libera la anulación
y consume el despacho; y la reserva vencida se informa pero la suelta **una
persona**, con motivo, porque soltarla sola puede vaciar la reserva de un pedido
que sí se despacha mañana.

**Excepción, los pedidos especiales.** Un pedido `personalizado`, `corporativo`
o `regalo` se crea aunque no haya stock: existe para prepararse o fabricarse,
tiene `fecha_limite_entrega`, y el sistema tiene órdenes de producción para
cumplirlo. El déficit queda en la bitácora (`pedidos` / `crear_sin_stock`) en vez
de callado. **Si esto no es lo que quiere negocio, es la primera línea a
cambiar** — está en `PedidoService.crear`, en un solo `if`.

**Dónde mirarlo:** `sql/fase47_reserva_stock.sql`, `service/ReservaStockService`,
`ReservaStockTest` (12 pruebas). En pantalla: aviso y modal en Inventario, y
`GET /api/inventario/reservas/vencidas`.

**Lo que NO se hizo, y se dice en voz alta:** no se reconstruyeron reservas para
los **19.058** pedidos que ya estaban en `procesado`. No se sabe cuáles siguen
vivos, y fabricárselas sería inventarse hechos. **Durante la transición, el
disponible es optimista respecto de esos pedidos.** Se irá corrigiendo solo según
se despachen o se anulen.

### F48 · La matriz de permisos decide (cierra D-13)

Los 49 permisos pasan a **94** y, por primera vez, alguien los consulta.

Se hizo en el orden que exigía el plan: **(1)** cargar la matriz, **(2)**
verificar que ningún rol queda en cero —lo comprueba el propio script, dentro de
la transacción—, **(3)** encender la comprobación.

**La matriz no está inventada:** cada fila sale de una regla que ya se aplicaba
en `SecurityConfig`. Por eso encenderla no le quitó el acceso a nadie. Lo que
cambia es que ahora **decide**: quitarle `pedidos:crear` al Operador de Pedidos
en la pantalla de roles se lo quita de verdad, y en la petición siguiente —las
authorities se releen de `rol_permiso` en cada petición, no del claim del token,
así que no hace falta volver a entrar—.

| Rol | Permisos |
|---|---|
| Administrador | 94 |
| Encargado de Compras | 24 |
| Operador de Pedidos | 20 |
| **Encargado de Producción** | **20** (era 0 de 49) |
| Supervisor E-Commerce | 20 |
| Operador de Bodega | 19 |

**Dónde mirarlo:** `sql/fase48_matriz_permisos.sql`, 153 anotaciones
`@PreAuthorize` en los controladores, `config/Permisos` para los cuatro casos en
que un mismo endpoint hace varias cosas, `MatrizPermisosTest` (6 pruebas) y
`PermisosSeAplicanTest` (5 pruebas).

**Cuidado al tocar:** `DataInitializer.ensureComprasFase21()` ya **no** asigna
permisos. Volvía a colgar `compras:aprobar` del Encargado de Compras en cada
arranque, contradiciendo al propio `OrdenCompraService`. Si se revierte ese
cambio, la matriz queda deshecha en el siguiente reinicio.

### Sesión: la ventana de 24 h pasa a 2 h

`app.jwt.expiration` = **7.200.000** (eran 86.400.000). Una línea que acorta a la
vez la ventana de D-23 y la de D-27. No obliga a nadie a volver a entrar: el
refresh sigue durando 7 días y comprueba que el usuario siga activo.

### F49 · Tres roles no podían crear su propio documento (cierra D-39)

**Es el hallazgo más grave de la sesión, y salió de levantar la aplicación.** Las
130 pruebas pasaban, la compilación pasaba, y el primer `POST /api/pedidos` como
`pedidos@marathon.com` devolvió **403**.

| Rol | Su documento | Antes | Después |
|---|---|---|---|
| Operador de Pedidos | `POST /api/pedidos` | **403** | 201 |
| Encargado de Compras | `POST /api/ordenes-compra` | **403** | 201 |
| Encargado de Producción | `POST /api/ordenes-produccion` | **403** | 201 |

**La causa.** Hibernate escribe por omisión un INSERT **estático**: nombra todas
las columnas mapeadas, tengan valor o no. En un documento que atraviesa etapas,
eso nombra columnas que se rellenan **más tarde** (`numero_hu` la pone el
empaque, `fecha_aprobacion` la aprobación, `fecha_inicio` el cierre de la orden).
La F34 concede privilegios columna por columna, así que el rol que **arranca** el
flujo no las tiene — y PostgreSQL rechaza el INSERT entero.

**Por qué llevaba desde la F37 sin verse.** `fase37_pruebas_endpoints.ps1` dio 66
de 66, pero **todas sus pruebas son GET**. El único camino de escritura probado
era el del Administrador, que usa el pool por defecto y tiene INSERT sobre la
tabla entera.

**Corregido en dos mitades:** `@DynamicInsert` en `Pedido`, `DetallePedido`,
`OrdenCompra` y `OrdenProduccion` —resuelve las nueve columnas que están a NULL
sin conceder ni un privilegio— y `sql/fase49_privilegios_de_creacion.sql` para
las cuatro que llevan valor por defecto en Java. Se concede **INSERT y no
UPDATE**: el Operador de Pedidos crea la línea con el picking sin empezar, pero
sigue sin poder marcarla como recogida.

> **Lo que esto deja abierto, y es lo importante:**
> `scripts/fase37_pruebas_endpoints.ps1` sigue probando **solo lecturas**.
> Mientras siga así, este defecto vuelve con la próxima entidad que se añada. La
> tarea pendiente es extenderlo a los POST/PUT de cada rol.

### F50–F53 · Cuatro defectos que solo se ven usando la aplicación

Los cuatro salieron de **conducir Chrome**: pantalla por pantalla, rol por rol, y
el flujo de venta completo de punta a punta. **Ninguno lo veían las 131
pruebas.** El primero lo reportó el dueño del proyecto usándolo.

| | Qué pasaba | Cerrado con |
|---|---|---|
| **D-40** | La bodega decía «guardada correctamente» y **no guardaba el responsable**: el campo estaba en el formulario, en el DTO y en la cabecera de la tabla, pero no en el servicio, ni en la entidad, ni en la tabla. | `fase50_bodega_responsable.sql` |
| **D-41** | **20 listas sin `ORDER BY`.** Al editar una fila, PostgreSQL la reescribe al final del montón y **desaparece de su página**. Se vio al guardar la bodega 1: la lista pasó a empezar por la 2. Y otra vez en el picking: la bodega editada salía la última de las 20 del desplegable. | `Sort` en 16 listados paginados y 4 no paginados |
| **D-42** | **Un pedido recién recogido no se podía empacar.** La pantalla pedía los 100 primeros de 19.059 pedidos procesados, del más antiguo; el recién recogido estaba en la posición 19.059. Sin paginación ni buscador: inalcanzable. | `buscarListosParaEmpacar` + `/api/empaque/pedidos/listos` paginado |
| **D-43** | **Cinco «Ver detalle» del inicio no llevaban a ninguna parte**: la ruta era la del endpoint de la API, no la de la pantalla. Afectaba al inicio de Pedidos, Compras y Producción. | rutas corregidas + `RutasDelTableroTest` |

> **La lección, que vale más que los cuatro arreglos.** Las pruebas corren con
> `roles.enabled=false`, sin navegador y sin la interfaz. Todo lo que vive entre
> la pantalla y la base —un campo que nadie mapea, una lista sin orden, una ruta
> que no existe, una consulta que trae los 100 primeros de 19.000— les es
> invisible por construcción. **Levantar la aplicación y usarla encontró cinco
> defectos (D-39 incluido) que 131 pruebas en verde no vieron.**

### Repaso de flujos: dos huecos más, cerrados

- **D-37**: un traslado con la misma bodega de origen y destino dejaba un
  movimiento en el kardex que no trasladaba nada. Ahora se rechaza.
- **D-38**: las devoluciones se comprobaban solicitud a solicitud. Dos
  solicitudes seguidas podían devolver cada una las 10 unidades de una línea de
  10. Ahora se compara contra el acumulado.

Los dos tenían **0 casos** en la base —se comprobó antes de tocar—, así que
cerrarlos no rompe ningún histórico.

---

## 3-bis. Lo que se cerró el 2026-08-28 (F60, F61 y F62)

### F60 · La sesión, de verdad (cierra D-23 y D-27) y la factura cotejada (D-36)

| Antes | Ahora |
|---|---|
| `logout` devolvía «Sesión cerrada correctamente» **y no hacía nada** | Revoca el token de acceso **y el de refresco**, y el filtro consulta la lista en cada petición |
| El token vivía en `localStorage`, donde un XSS lo lee | Vive en dos cookies `HttpOnly` + `SameSite=Strict` que el JavaScript no puede leer |
| El subtotal de la factura no se comparaba con nada | No puede superar el valor recibido. **Sin flete y sin tolerancia** (decisión de negocio del 2026-08-28) |

**Por qué revocar también el refresco.** Sin eso el logout no habría cerrado
nada: con el refresco se saca un token de acceso nuevo. Habría sido un rodeo de
una petición, no una barrera.

**Dónde mirarlo:** `sql/fase60_revocacion_de_sesion.sql`,
`config/CookieSesion`, `service/TokenRevocadoService`,
`SesionRevocableTest` (6 pruebas) y `FacturaCotejadaTest` (5).

### F61 · Los datos demo dejan de crearse solos (cierra D-26)

`app.datos-demo.enabled` pasa a `false`. No era una línea porque
`DataInitializer` también creaba **los roles y el primer administrador**:
apagarlo dejaba una base sin forma de entrar. Eso vive ahora en
`sql/fase61_siembra_inicial.sql`.

> **Y apareció algo peor que D-26.** En base vacía, `DataInitializer` repartía
> **49 permisos** con criterio propio, cuando desde la F48 el reparto bueno son
> **94** derivados de `SecurityConfig`. Este equipo tenía el bueno por venir de
> antes; **una instalación nueva habría nacido con el viejo**, y no se habría
> notado hasta montar el sistema en otra máquina. Ese reparto ya no está en
> `DataInitializer`.

**Orden en una base nueva:** esquema → **fase61** → resto → **fase48**.

### F62 · El inicio es el flujo, no las cifras

La pantalla de inicio pasa a ser **el flujo del sistema**: ocho pasos en orden,
del catálogo a la auditoría, con las opciones de cada paso y un icono de
información que dice **quién es responsable y qué le corresponde**.

El tablero de indicadores no desaparece: vive en **`/indicadores`**, está en el
menú y es la primera opción del octavo paso.

**Por qué.** Los indicadores contestan «cómo va todo», que es una pregunta que
solo sabe hacerse quien ya conoce el sistema. Quien entra por primera vez tiene
otra —«¿y ahora qué hago, y en qué orden?»— y no la contestaba nadie: el menú
lateral agrupa por módulo, no por secuencia.

**Dos decisiones que conviene no deshacer sin querer:**

- **Las opciones que tu rol no puede abrir se ven, con candado y con el nombre
  de quien sí.** Ocultarlas dejaría un flujo con agujeros, dando a entender que
  el trabajo salta del paso 2 al 5. Los roles de cada tarjeta están copiados de
  `app.routes.ts`: **si cambian allí, hay que cambiarlos en `flujo.model.ts` o
  el tablero mentirá.**
- **La ficha del icono de información empuja el contenido, no flota sobre él.**
  Es la lección de los desplegables que se veían por detrás de las tarjetas de
  más abajo: lo que no flota no puede quedar debajo de nada.

### F63 · Lo que salió de recorrer el flujo con cada rol

Se recorrieron **44 pantallas como administrador** y **84 comprobaciones más
repartidas entre los otros cinco roles**, recogiendo respuestas 4xx/5xx, errores
de consola, redirecciones y paneles de error. Salieron tres cosas.

**1. Una dirección que no existe devolvía 500.** `NoResourceFoundException` caía
en el cajón de sastre del manejador global, así que al cliente le llegaba
«Error interno del servidor» —culpando al servidor de una dirección mal
escrita— y al registro le caía un `ERROR "Error no controlado"` con su traza
entera, por cada enlace roto y cada rastreador que pasara. Eso segundo es lo
que de verdad importa: **un registro lleno de errores que no son errores es un
registro que nadie mira**, y el día que caiga un 500 de verdad estará enterrado.

**2. El Encargado de Compras se comía un 403 en cada carga de `/inventario`.**
La pantalla pedía el informe de reservas vencidas, que une `reserva_stock` con
`pedido` — y sobre `pedido` Compras no tiene SELECT, deliberadamente desde la
F34: quien compra no lee los pedidos de los clientes.

> **Lo interesante es *por qué* se denegaba.** No estaba escrito en ninguna
> regla: la petición caía en el `.authenticated()` general, llegaba a
> PostgreSQL, y PostgreSQL decía que no. **Funcionaba por accidente**, y quien
> leyera `SecurityConfig` no podía saberlo. Ahora la regla está escrita, y la
> pantalla directamente no pide lo que sabe que le van a denegar.

**3. El Supervisor E-Commerce tenía el filtro de materias primas vacío.** Podía
ejecutar el informe de consumo de materia prima pero no acotarlo, porque la
llamada que llena el filtro devolvía 403.

> **Y aquí las dos capas se contradecían:** `rol_supervisor` **ya tenía** SELECT
> sobre `materia_prima` en la base desde la F34, mientras `SecurityConfig` y la
> matriz de la F48 decían que no. Ganaba la de aplicación. Se alineó la
> aplicación con la base —que es la capa que este proyecto trata como la más
> deliberada— y no al revés: **no se tocó ningún GRANT**. Sigue sin poder
> escribir: crear, editar, borrar y mover materia prima siguen siendo de
> Producción y Administrador.

**Un falso positivo que conviene no volver a investigar.** `/pedidos/:id` provoca
un `404 /api/comprobantes/pedido/:id` cuando el pedido no tiene comprobante.
**Está bien así:** el API responde lo correcto, la pantalla lo traga y muestra
«Este pedido aún no tiene comprobante». Son 19.932 de 24.370 pedidos enviados,
todos del poblado masivo.

**Dónde mirarlo:** `sql/fase63_supervisor_lee_materia_prima.sql`,
`GlobalExceptionHandler.handleRutaInexistente`, la regla de
`/api/inventario/reservas/**` en `SecurityConfig`, y `ErroresTest`.

---

## 4. Lo que queda abierto

**Ningún defecto.** Los 43 están cerrados. Lo que sigue no son defectos: son
cosas que conviene saber antes de tocar nada.

### Deuda que sigue ahí, y que no es un defecto

- **`scripts/fase37_pruebas_endpoints.ps1` sigue probando solo lecturas.** Es la
  tarea pendiente más importante que queda. Es lo que dejó a D-39 escondido
  desde la F37: 66 pruebas en verde, todas GET, mientras tres roles no podían
  hacer un POST. Mientras siga así, el próximo defecto de ese tipo tampoco se
  verá. **Probar siempre un POST por rol con la aplicación en marcha.**

- **Las cinco tablas `*_respaldo_f59`** siguen en `mod_venta_inve`. Son la única
  vuelta atrás de las 14.161 filas que borró la F59. Se borran cuando el dueño
  lo diga, como se hizo con el respaldo de la F58.

- **Los 11 productos que la F59 marcó como fabricados no tienen lista de
  materiales**, así que no se les puede crear una orden de producción hasta que
  alguien la defina. Es correcto que se note: son de marca propia, su origen
  está bien, y «fabricado sin BOM todavía» es verdad y se puede completar,
  mientras que «comprado» sería mentira.

- **El kardex de materia prima no cuadra con el saldo en ninguna de las 300
  materias primas.** Viene del poblado masivo y ya no cuadraba antes de la F59.
  Si alguien lo mide, que no lo tome por un fallo nuevo.

### Riesgos aceptados a conciencia

- **Un XSS sigue pudiendo actuar como el usuario** mientras la página está
  abierta: el navegador adjunta la cookie él solo. Lo que ya no puede, desde la
  F60, es **robarse la credencial** para usarla desde otro sitio más tarde. Esa
  es la diferencia exacta que compra `HttpOnly`, y no conviene venderla como
  más de lo que es.

- **CSRF sigue desactivado**, y ahora que la sesión va en cookie eso pide
  explicación: lo cierra `SameSite=Strict`. El doble envío de token no era
  viable —el front en el 4300 no puede leer una cookie puesta por la API en el
  8080— y para `SameSite` el puerto no cuenta, así que la cookie sí viaja en las
  llamadas legítimas. **Si el front se sirve algún día desde otro dominio, hay
  que rehacer esto**: `SameSite` dejaría de proteger nada.

- **Consultar la lista de revocación cuesta una consulta por petición
  autenticada.** Es el precio de que cerrar sesión signifique algo. Si la
  consulta falla **se deja pasar**, no se deniega: negar ante la duda
  convertiría un problema de esa tabla en una caída total para todos a la vez.

- **La reserva de stock de los 19.058 pedidos ya en `procesado` no se
  reconstruyó.** Durante la transición el disponible es optimista respecto de
  ellos. Se corrige solo según se despachen o se anulen.

## 5. Lo que NO hay que hacer

Recortes deliberados. Deshacerlos sin querer rompe trabajo que sí está bien:

- **No repares los datos históricos.** Hay 22.226 pedidos enviados sin movimiento
  de inventario y 1.649 facturas por encima de lo recibido. No se puede
  distinguir lo que escribió la aplicación de lo que escribieron los scripts de
  poblado, así que cualquier corrección masiva sería **inventarse hechos**. Lo
  correcto es un informe de conciliación de solo lectura y una fecha de corte
  decidida por negocio.
- **No rellenes las reservas de los 19.058 pedidos en `procesado`.** Es el mismo
  error con otro nombre.
- **No toques los triggers ni los CHECK.** Son la parte más sólida del sistema y
  **las pruebas de L1, L5, L6 y L13 dependen de que se comporten igual**.
- **No devuelvas la asignación de permisos a `DataInitializer`.** Pisa la matriz
  de la F48 en cada arranque.
- **No devuelvas la siembra de roles ni de permisos a `DataInitializer`.** Desde
  la F61 los roles los siembra `fase61_siembra_inicial.sql` y los permisos la
  F48. `DataInitializer` ya no reparte permisos **ni siquiera en el primer
  arranque**, que era el único rincón donde todavía lo hacía — y donde le daba
  a una instalación nueva la matriz vieja de 49.
- **No pongas `app.sesion.cookie-segura=true` en desarrollo.** Una cookie
  `Secure` no viaja por `http://localhost`, así que la sesión dejaría de
  funcionar sin decir por qué. En producción, con HTTPS, tiene que estar en
  `true`.
- **No amplíes `ROLES_CON_RESERVAS` de `inventario.component.ts` sin tocar
  también `SecurityConfig`.** Esa lista existe para no pedir lo que va a ser
  denegado; si se desincroniza de la regla del servidor, vuelve el 403 en cada
  carga. Lo mismo vale para los roles de `flujo.model.ts` y `app.routes.ts`.
- **No metas Flyway ni Liquibase ahora.** Está anotado como deuda.
- **No añadas librerías nuevas** si con lo que ya usa el proyecto alcanza. Las
  pruebas de permisos se hicieron sin `spring-security-test`, poniendo la
  autenticación a mano en el `SecurityContextHolder`, que es lo mismo que hace
  `JwtAuthenticationFilter`.
- **`docker-compose.yml` miente** (dice Postgres 15, usuario `postgres`, puerto
  5432; la realidad es Postgres 18, `usr_admin_marathon`, 5433). O se reescribe
  entero o se borra — no se parchea a medias.

---

## 6. Reglas de trabajo del dueño del proyecto

Se aplican a cualquier cosa que se añada, y no son negociables:

1. **Ninguna cifra sin su denominador o su período.** «Ventas: 4.300» no dice
   nada.
2. **Si un dato no se puede calcular con lo que hay en la base, se DICE en
   pantalla.** No se inventa y **no se pone en cero**: cero y «no hay dato» son
   cosas distintas y se leen distinto. (Por eso `disponible` puede salir negativo
   y sale negativo: −5 y 0 no significan lo mismo.)
3. **Nada de librerías nuevas** si con lo que ya hay alcanza.
4. **Diagnosticar y medir contra la base antes de rediseñar.** Toda cifra nueva
   se contrasta contra su consulta directa en psql antes de darla por buena.
5. **Lo que no se pueda verificar se dice como «no verificado»**, no se supone.

---

## 7. Cómo comprobar que no se rompió nada

```bash
# Backend — 143 pruebas, deben quedar todas en verde
cd marathon-backend
mvn test                     # necesita TEST_DB_PASSWORD en el entorno

# Frontend — la compilación de producción debe pasar los presupuestos de tamaño
cd marathon-frontend
npx ng build --configuration production
```

Si `mvn test` baja de 143 pruebas o alguna falla, **algo se rompió**.

**Y con la aplicación levantada, la comprobación que faltaba.** Las 143 pruebas
usan un solo pool (`app.datasource.roles.enabled=false`), así que **no ven** los
privilegios por rol. D-39 pasó por ahí. Con el backend en marcha:

```bash
tok() { curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
        -d "{\"correo\":\"$1@marathon.com\",\"password\":\"Demo1234!\"}" \
        | grep -o '"token":"[^"]*"' | cut -d'"' -f4; }

# Cada rol tiene que poder CREAR su documento. Los tres deben dar 201.
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/api/pedidos \
  -H "Authorization: Bearer $(tok pedidos)" -H 'Content-Type: application/json' \
  -d '{"idCliente":1,"descuento":0,"esPedidoEspecial":false,
       "detalles":[{"idProducto":55,"cantidad":1,"precioUnitario":1}]}'
```

Un **403** con *«Tu rol no tiene permisos sobre estos datos»* no es autorización:
es la base rechazando el INSERT. Míralo en el registro, que dice qué tabla.

Comprobaciones rápidas contra la base, por si la matriz o las reservas se
desalinean:

```sql
-- Ningún rol puede quedarse sin permisos: con la comprobación encendida,
-- un rol vacío es un rol sin acceso a nada.
SELECT r.nombre, count(rp.*) FROM rol r
  LEFT JOIN rol_permiso rp ON rp.id_rol = r.id_rol
 GROUP BY r.nombre ORDER BY 2;

-- Reservas activas que llevan demasiado tiempo reteniendo mercancía.
SELECT count(*) FROM reserva_stock
 WHERE estado = 'activa' AND fecha_reserva < now() - interval '7 days';
```

---

## 8. Dónde está el detalle

| Documento | Qué contiene |
|---|---|
| `docs/AUDITORIA.md` | El recorrido completo del sistema, flujo por flujo |
| `docs/DEFECTOS.md` | Ficha de los 43 defectos, con evidencia y `fichero:línea` |
| `docs/PLAN.md` | Los 17 lotes, el estado final de cada uno y los recortes razonados |
| `docs/DASHBOARD.md` | Diagnóstico y rediseño del tablero; las 22 cifras contrastadas |

---

## 9. Interfaz: lo que se rehizo el 2026-08-27 (F54)

Tres quejas del dueño del proyecto, y lo que salió de cada una.

### La tabla se quedaba a media pantalla — una línea de CSS

`styles.scss` ponía `display: block` a las tablas **sin media query**, o sea en
todas las pantallas. Sobre un `<table>`, `display: block` deshace la caja de
tabla: el ancho pasa a calcularlo una caja anónima que se encoge al contenido, y
el `width: 100%` de la regla de arriba deja de tener efecto.

Medido antes y después en el navegador, sobre seis pantallas: la tabla ocupaba
**~65 %** del ancho útil y ahora ocupa **100 %**. El desplazamiento horizontal
—que era la intención original y es buena— se movió a la media query de
pantallas estrechas, que es donde hace falta.

**Una sola regla arregló las 31 pantallas con tabla.**

### Filtros de búsqueda por escrito

Siete listados solo tenían desplegables. Se les añadió búsqueda por texto, con
el filtro **en la base**, no en el navegador:

| Pantalla | Busca por | Efecto medido |
|---|---|---|
| Pedidos | nº de pedido o cliente | 23.000 páginas → 33 buscando «Doris» |
| Órdenes de compra | nº de orden o proveedor | 267 páginas → 1 buscando «2665» |
| Inventario | producto o bodega | 200 páginas → 2 buscando «SAMBA» |
| Órdenes de producción | nº de orden o producto | 300 → 227 |
| Cuentas por pagar | proveedor o nº de factura | 229 → 45 |
| Devoluciones | nº, pedido o cliente | 80 páginas → 1 |
| Dev. a proveedor | nº o proveedor | 40 → 8 |

En Pedidos se acepta **«PED-230005», «230005» y «Doris»**: el número con formato
lo compone el DTO y no existe como columna, así que copiar lo que se ve en
pantalla no encontraba nada — la forma más segura de que alguien concluya que su
pedido se perdió.

### El producto se escribe, no se despliega

`orden-compra-nueva` tenía un `<select>` con todos los productos. Ahora usa
`app-searchable-select`, **que ya existía** y usaba la pantalla de pedidos: aquí
solo faltaba usarlo. Proveedor, producto y materia prima se escriben.

### Dos trampas que dejó esto, y que conviene no repetir

1. **Un parámetro nulo en un `LIKE` no se puede tipar.** `CONCAT('%', :texto,
   '%')` con `:texto` nulo hace que PostgreSQL lo tome por `bytea` y falle con
   *«el operador no existe: text ~~ bytea»*. Hay que escribir
   `CAST(:texto AS string)`.
2. **Ni un `? IS NULL` con una fecha.** Da *«no se pudo determinar el tipo del
   parámetro»*. Por eso las fechas se pasan **siempre**, con topes por defecto —
   que es lo que ya hacía `EmpaqueService.listarDespachados`.

Las dos juntas rompieron los **siete** listados sin búsqueda, que es el caso
normal. **Y mi primera comprobación en el navegador dio verde**, porque medí el
resultado *con* búsqueda y no el listado sin ella. Al probar una pantalla,
comprueba el caso vacío antes que el caso lleno.

### El puerto: Marathon ahora es el 4300

Los dos proyectos Angular de este equipo tomaban el 4200 por defecto y el primero
que arrancaba se lo quedaba; el navegador llegó a servir **otra aplicación entera
desde caché** mientras el servidor ya devolvía ésta. `angular.json` fija el
**4300**, y el CORS de `SecurityConfig` lo acompaña.
