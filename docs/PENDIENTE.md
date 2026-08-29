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
pasaron de **0 → 105 → 131 → 165**, todas en verde. La compilación de producción
del frontend funciona.

A las fases 47-59 se suman ahora:

| Fase | Qué cierra |
|---|---|
| **F60** | D-36 (la factura se contrasta con lo recibido), D-23 (revocación real de sesión) y D-27 (el token sale de `localStorage` a una cookie `HttpOnly`) |
| **F61** | D-26 (los datos demo dejan de crearse solos) **y un defecto latente que nadie había visto**: una instalación nueva nacía con la matriz de permisos vieja, de 49, en vez de la de la F48, de 94 |
| **F62** | La pantalla de inicio pasa a ser **el flujo del sistema**: ocho pasos en orden, con sus opciones y con quién es responsable de cada uno |
| **F63** | Tres cosas que salieron de **recorrer el flujo entero con cada rol**, no solo con el administrador |
| **F64** | El Administrador queda **exento** de la separación de funciones al aprobar órdenes de compra |
| **F65** | Compras y Producción **no podían terminar su parte del flujo**: dos GRANT que nunca se concedieron y un guardado de más |
| **F66** | Documentar la compra es **un clic y un PDF**, con el importe calculado de lo recibido |
| **F67** | La pantalla de pago se parte en dos: a la izquierda lo que se revisa, a la derecha lo que se hace |
| **F68** | La devolución a proveedor **explica de dónde nace**, en qué punto está y qué toca hacer |
| **F69** | Si el proveedor repone, se crea una orden de compra **que no se puede facturar** |
| **F70** | La recepción de mercancía rehecha, el botón del PDF partido en dos, y el aire que les faltaba a las pantallas de detalle |
| **F71** | Las dos pantallas de producción, y **la mano de obra y los indirectos calculados** en vez de tecleados |
| **F72** | Mover stock deja de pedir el **id del producto** y explica qué hace cada movimiento |
| **F73** | El cliente tiene **documento** (cédula, RUC o pasaporte) y por fin se guarda; el pedido especial deja de ocupar una banda entera |
| **F74** | Picking y detalle del pedido rehechos: se acaban los recuadros encimados, y el picking **dice en qué bodega está** la mercancía |
| **F75** | La bodega del picking **se escribe**, con el mismo buscador que el resto de la aplicación |
| **F76** | El buscador compartido trae **su propio tamaño**: en picking medía 19 px y en el resto 44 |
| **F77** | Qué es el **HU**, catálogo de **transportistas**, y la **región sale de la ciudad del cliente** en vez del teclado |
| **F78** | La devolución dice **antes** lo que la base exige: pedido entregado, y lo ya devuelto gasta cupo |
| **F79** | La inspección dice **qué hace cada decisión**, y el reembolso enseña su tope en vez de rechazarlo al enviar |
| **F80** | **Análisis del negocio**: lo más vendido y lo más comprado, quién deja más, en qué región se vende y por qué devuelven |
| **F81** | Reportes y auditoría: dicen **qué contesta cada uno**, abren con datos, y el aviso de la exportación deja de mentir |
| **F82** | El asistente dice que **está apagado antes** de que escribas, y qué puede leer y qué no |
| **F83** | El asistente **funciona**: Gemini como proveedor, y el tope de filas deja de romper toda consulta con `LIMIT` |
| **F84** | **Normalización**: la base deja de guardar cuatro veces lo que ya sabía (tres 3FN y una 1FN) |
| **F85** | Los **formularios y la base** dicen lo mismo: se acaban los campos que se pedían y se tiraban |
| **F86** | Un parámetro que falta deja de ser un **500 anónimo** y pasa a ser un 400 que lo nombra |

---

## 2. Antes de tocar nada: dieciséis cosas que van a morderte

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

12. **`mvn compile` no basta, y de dos maneras distintas.** La primera ya se
    sabía: la compilación incremental da **verde sin recompilar**, así que un
    error de sintaxis puede sobrevivir a un `BUILD SUCCESS`. La segunda apareció
    en la F73 y es peor: borrar a mano un solo `.class` y recompilar dejó
    `ClienteService.class` con referencias a `ClienteResponseDTO` **sin
    paquete**, o sea a una clase del paquete por defecto que no existe. Compiló,
    arrancó, y murió con `ClassNotFoundException: ClienteResponseDTO` — un
    nombre sin punto que no aparece en ninguna parte del código fuente. Si un
    error no cuadra con lo que dice el fuente, `mvn clean compile` antes de
    seguir buscando.

13. **Una clase con nombre corriente puede chocar con `styles.scss`.** Los
    estilos de un componente Angular no están encapsulados frente a los globales
    cuando el nombre coincide: en la F74, una `<section class="bloque acciones">`
    salió con el título, la explicación y los botones en un solo renglón porque
    `styles.scss` ya define `.acciones { display: flex }`. No dio ningún error;
    solo se veía mal. Antes de bautizar una clase con una palabra genérica
    —`acciones`, `header`, `total`, `campo`— búscala en `styles.scss`.

14. **La forma corta de una anotación Java solo vale si es el único elemento.**
    `@Query("SELECT …")` es legal; en cuanto le añades `countQuery = "…"` hay que
    nombrar el primero: `@Query(value = "SELECT …", countQuery = "…")`. Sin eso
    es un **error de sintaxis**, no de Spring, y en la F84 llegó disfrazado: la
    aplicación arrancaba y todas las pruebas reventaban con
    `BeanCreationException … Unresolved compilation problems` sobre
    `pedidoRepository`, que apunta al repositorio pero no dice que el problema
    sea del compilador. Si un fallo de creación de bean menciona
    «compilation problems», el fuente está roto: míralo antes de tocar Spring.

15. **Hacer EAGER una asociación nueva mete un N+1 sin que nadie avise.** En la
    F84, `pedido.transportista` pasó a ser clave ajena y Hibernate empezó a
    pedirlo en una consulta aparte, una por cada transportista distinto de la
    página. Con un solo transportista es una consulta de más; con diez, diez. No
    dio error ni se notó a ojo: lo cazó `RendimientoDespachosTest`, que **cuenta
    consultas** en vez de medir tiempo. Al añadir una asociación a una entidad
    que se lista paginada, o va LAZY, o va con `JOIN FETCH` en la consulta.

16. **El compilador del IDE pisa `target/classes` y tira los nombres de los
    parámetros.** Es la más traicionera de todas y costó un susto el
    2026-08-29: tras arrancar el backend, **33 de 47 endpoints devolvían 500**.
    En el registro:

    ```
    IllegalArgumentException: Name for argument of type [int] not specified,
    and parameter name information not available via reflection.
    Ensure that the compiler uses the '-parameters' flag.
    ```

    Los controladores usan `@RequestParam int page` sin nombrar el parámetro, y
    Spring lo saca del *bytecode*. Maven lo pone —`spring-boot-starter-parent`
    activa `-parameters`—, pero **el compilador del editor escribe en la misma
    carpeta y no lo pone**. Gana el último que compiló, y si fue el IDE, medio
    sistema deja de funcionar sin que nadie haya tocado el código.

    Se comprueba en un segundo:

    ```
    javap -v -cp target/classes com.marathon.controller.ProductoController | grep -c MethodParameters
    ```

    **7 con Maven, 0 con el IDE.** Si sale 0, `mvn -o clean compile` **antes** de
    arrancar. Es de la misma familia que el punto 12: no te fíes de lo que haya
    en `target/`, porque no siempre lo puso Maven.

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

### F64 · El Administrador puede aprobar su propia orden de compra

**Decisión del dueño del proyecto, 2026-08-28.** La regla «quien solicita no
aprueba» se mantiene para todo el mundo **menos para el Administrador**.

**Por qué.** `compras:aprobar` lo tiene solo el rol Administrador y solo existe
**un** usuario con ese rol, así que una orden creada por el administrador **no la
podía aprobar nadie**: el flujo se quedaba muerto en `pendiente_aprobacion` sin
salida, salvo dando de alta un segundo administrador. Se descubrió porque el
dueño se topó con ello usando el sistema.

**Lo que se pierde, y conviene que esté escrito.** Una compra del administrador
ya no pasa por un segundo par de ojos, y este era el **único punto del sistema
donde el dinero salía con doble firma**. Para desarrollo y demostración es lo
razonable; en una instalación real la respuesta correcta es tener **dos
administradores** y quitar la excepción — es borrar una condición en
`OrdenCompraService.cambiarEstado`.

**La excepción pregunta por el ROL, no por el permiso**, y eso es deliberado: los
permisos se editan desde la pantalla de roles, así que si preguntara por
`compras:aprobar` la exención se podría regalar marcando una casilla. El rol no.

**La restricción sigue viva para los demás.** Si algún día se concede
`compras:aprobar` a Encargado de Compras, ese rol **no** podrá aprobar lo suyo.
Lo fija `AprobacionOrdenCompraTest` (5 pruebas), que existe justamente para que
nadie afloje esa mitad sin darse cuenta.

**Comprobado sobre la aplicación en marcha**, no solo en pruebas: el admin creó
una orden, la mandó a aprobación y la aprobó él mismo.

### F65 · Compras y Producción no podían terminar su parte del flujo

El dueño del proyecto aprobó una orden, entró a registrar la recepción y leyó:

> «Tu rol no tiene permisos sobre estos datos»

**El mensaje engañaba.** Apunta a la matriz de permisos, y la matriz estaba
bien: `recepciones:registrar` lo tiene el Encargado de Compras desde la F48.
Quien denegaba era PostgreSQL. Tres cosas distintas, encontradas tirando del
hilo:

**1. `movimiento_inventario` sin SELECT (Compras y Producción).** Los dos tenían
INSERT pero no SELECT, y la clave primaria es IDENTITY: Hibernate emite
`INSERT … RETURNING id_movimiento`, y **RETURNING exige SELECT**. Con INSERT a
secas PostgreSQL rechaza la sentencia entera. Es la misma trampa ya documentada
en `LogService.registrar`, resuelta allí al revés —con un INSERT nativo— porque
allí se quería escribir en la bitácora sin poder leerla. **Producción tenía el
mismo hueco y nadie lo había visto**, porque nadie había llegado a completar una
orden con ese rol.

**2. `orden_produccion_consumo.costo_unitario_snapshot` sin UPDATE.** Producción
tenía INSERT en esa columna y UPDATE en `cantidad_real`, pero no en el snapshot
— y ese UPDATE es deliberado: la F29 fotografía el costo del insumo **al
iniciar**, no al planificar. Caso de manual de la regla 4: una tabla que se
llena por etapas necesita el UPDATE de las columnas de cada etapa posterior. La
F34 concedió la etapa 1 y la 3 y se saltó la 2.

**3. El pago a proveedor se guardaba dos veces.** Insertaba, y volvía a guardar
solo para escribir la referencia `PAG-000123`, que necesita el id recién
generado. Ese segundo guardado emitía un UPDATE sobre `pago_proveedor`, donde
Compras no tiene UPDATE — **y está bien que no lo tenga**: un pago es un asiento
contable. Por eso **no** se concedió el privilegio; se arregló el código para
escribir una sola vez. La referencia se deriva del id y `toDTO()` ya la
calculaba sola cuando venía vacía. De paso dejó de pisarse la referencia que
manda quien registra el pago.

> **Por qué no lo vio nada de lo anterior, que es lo que más importa.** Es la
> misma trampa que escondió D-39 desde la F37. El perfil de pruebas usa un solo
> pool (`app.datasource.roles.enabled=false`), así que el arnés **nunca** se
> conecta como `rol_encargado_compras`. Y el barrido de la F63 recorrió 128
> pantallas **solo con GET**: cargar la pantalla de recepción funciona; lo que
> falla es enviarla.
>
> Se corrigieron las dos cosas. `PrivilegiosDelFlujoTest` (6 pruebas) lee los
> privilegios reales de PostgreSQL con `aclexplode()` y comprueba que están los
> que el código necesita — y también que **no** se concedió de más: nadie salvo
> el administrador puede corregir un movimiento ni un pago. Y se hizo un
> **barrido de escritura** recorriendo la cadena de venta entera cambiando de
> rol en cada paso (pedido → picking → empaque → comprobante → entrega →
> devolución → inspección): **cero fallos**.

### F66 · Documentar la compra: un clic, un PDF, y solo lo recibido

Registrar la factura llevaba a un formulario con cinco campos —número, fecha,
vencimiento, subtotal e impuesto—. Cuatro de los cinco se deducen de la orden y
de sus recepciones. Ahora el botón de la orden **documenta y abre el PDF en otra
pestaña**, sin pantalla intermedia.

**Lo importante no es el ahorro de teclas.** Calcular el subtotal de lo recibido
**elimina de raíz el descuadre que D-36 tenía que vigilar**: si el importe sale
de lo que entró, no puede superarlo. El tope de D-36 sigue en pie para
`POST /api/facturas-compra`, que es la vía donde alguien puede teclear una cifra.

**La regla, que es la que pidió el dueño del proyecto:**

```
importe = valor recibido − lo ya documentado (las anuladas no cuentan)
```

Una orden de 10 unidades recibida en dos tandas de 4 y 6 produce un documento de
40 y otro de 60. **Sin esa resta saldrían dos de 100 y la cuenta por pagar
quedaría al doble** — el error que más caro sale descubrir tarde, porque no
falla: cuadra mal. Lo fija `DocumentoDeCompraTest` (6 pruebas).

> **Se llama «documento interno de compra» y no «factura», a propósito.** La
> factura la emite el proveedor, con su numeración y su firma; esto lo emite
> Marathon a partir de lo que entró en la bodega. Llamarlo factura sería decir
> que es algo que no es, así que el PDF lo dice en el título y en el pie. Para
> registrar la factura real del proveedor sigue estando el endpoint de siempre,
> **que no se tocó**.

**El IVA es una propiedad, no una constante**: `app.compras.iva-porcentaje`, 15
por defecto (Ecuador desde 2024). Un tipo impositivo cambia por ley, no por un
despliegue.

**Detalle del front que conviene no deshacer:** el PDF se pide con
`ApiService.getBlob` y se abre como `blob:`, no apuntando una pestaña nueva a la
URL del API. Desde la F60 la sesión va en una cookie, y una pestaña nueva hacia
otro origen no la lleva: saldría un 401 en blanco.

### F70 · Recepción rehecha, el botón del PDF partido, y el aire que faltaba

Tres cosas que pidió el dueño el 2026-08-28, después de usar el sistema.

**1. La pantalla de recepción, que quedaba pendiente desde la F68.** Era una
tabla de campos sueltos que no decía a dónde iba el stock, ni cuánto se estaba
recibiendo, ni qué pasaba con lo defectuoso. Y sobre eso último **mentía**:
ponía «devolución a proveedor (próximamente)» cuando ese circuito funciona desde
la F68. Ahora está partida —lo que llega a la izquierda, la confirmación a la
derecha— con resumen en vivo de unidades, defectuosas e importe, el estado en
que quedará la orden, y el aviso de que confirmar mueve stock y no se deshace.

**2. El botón hacía dos cosas y por eso se atascaba.** «Documentar compra y abrir
PDF» seguía ahí después de documentar, pero al pulsarlo intentaba documentar otra
vez y el servidor lo rechazaba — con razón. **No había forma de volver a abrir el
PDF solo para mirarlo.** Ahora son dos: «Documentar» aparece solo si queda algo
pendiente (lo recibido menos lo ya documentado, la misma resta que hace el
servidor), y sale un «Ver PDF» por documento emitido. Con recepciones parciales
conviven los dos, que es justo lo que hacía falta.

Hizo falta un endpoint nuevo, `GET /api/facturas-compra/orden/{id}`, para que la
pantalla sepa qué documentos existen.

**3. Las pantallas de detalle salían pegadas al borde.** Las de las F67 y F68
traen su propio contenedor (`.cxp`, `.dvp`) en vez de `.crud-container`, así que
se quedaron **sin el `padding` que este daba**. Y el botón de volver era una
flecha fina con un texto, sin caja ni área donde pinchar. Las dos cosas se
arreglan en `styles.scss`, en un sitio y no en cada componente: `.btn-volver` es
ahora un botón con borde, relleno y foco visible. **No cambia lo que hace.**

### F71 · Producción: lo que cuesta deja de teclearse a mano

**La pregunta del dueño:** al completar una orden salían dos campos —costo de
mano de obra y costo indirecto— y él no sabía qué poner. *«¿eso no debería salir
calculado automáticamente en base a lo que se produzca?»*

Tenía razón, y el efecto de no saberlo era peor de lo que parece: **se dejaban en
cero**, y con cero el análisis de costes decía que fabricar sale exactamente lo
que vale la materia prima. Una cifra falsa presentada como buena.

**Ahora el sistema los propone.** No salen de ningún fichaje ni de ninguna
factura de luz —el sistema no los tiene— sino de dos tarifas configurables, que
es lo que hace un costeo por absorción cuando no hay datos reales:

```
mano de obra = tarifa por unidad × unidades producidas
indirecto    = porcentaje × (materia prima + mano de obra)
```

La mano de obra escala con las **unidades** porque fabricar cada una cuesta un
rato de trabajo; el indirecto se aplica como **porcentaje del coste directo**,
que es como se prorratean de verdad la luz, el alquiler y la maquinaria.

**Y siguen siendo editables**, que es la otra mitad de lo que pidió: si esa tanda
llevó horas extra, se escribe lo que costó. Lo que se evita es el cero por
omisión. El modal dice de dónde salen los números, para que nadie los tome por
un dato medido.

Las tarifas son propiedades: `app.produccion.mano-obra-por-unidad` (2,50) y
`app.produccion.indirecto-porcentaje` (15). El cálculo vive en el **servidor**
—`GET /api/ordenes-produccion/{id}/costos-sugeridos`— para que cambiar una
tarifa no obligue a recompilar el front.

**Las dos pantallas, además.** «Nueva orden de producción» gana el panel de
coste: materia prima por unidad, total, precio de venta y **margen**, antes de
crear nada — sale del mismo cálculo de la F29 que usa la pantalla de costes, así
que no es una cuenta paralela. Y cuando el producto no tiene lista de materiales
—**11 de los 14 fabricados**, desde la F59— lo dice y explica dónde se define,
en vez de quedarse mudo con un botón bloqueado.

### F72 · Mover stock: sin ids, y diciendo qué hace cada cosa

Dos quejas del dueño, las dos justas:

**1. Pedía el id del producto a mano.** Un `<input type="number">` donde había
que escribir el número interno. Nadie se lo sabe, y no hay pantalla que lo
enseñe. Ahora es el mismo selector con búsqueda que ya usaban las bodegas: se
escribe el nombre.

**2. No se entendía qué hacía cada movimiento, sobre todo el ajuste.** Y ese es
el peligroso: **fija un valor ABSOLUTO**. Se escribe el total contado y el
sistema calcula la diferencia. Quien lo lea como «sumar o restar» descuadrará el
stock sin enterarse — y hay 10.757 ajustes en la base. Ahora los cuatro tipos son
tarjetas que dicen lo que hacen, la del ajuste se llama **«Ajuste por conteo»**, y
el rótulo de la cantidad cambia con el tipo: «unidades que entran», «unidades que
salen», **«stock real que has contado»**.

**Y se ve el efecto antes de confirmar.** En cuanto hay producto y bodega, la
pantalla enseña lo que hay ahora y en qué quedará: **«63 → 70 (+7)»**. Eso es lo
que convierte un formulario en algo que se entiende sin manual.

El stock actual se resuelve con el listado que ya existía —filtrando por bodega y
buscando por nombre— en vez de añadir un endpoint: la pantalla ya tenía lo que
necesitaba, y una consulta nueva habría que concedérsela a seis roles.

### F73 · El documento del cliente: se pedía, y se tiraba

Lo pidió el dueño de una frase: *«lo de cliente no solo tiene cédula sino también
RUC»*. Al ir a añadirlo apareció algo peor que una carencia.

**El formulario pedía la cédula, la marcaba como obligatoria, el DTO la llevaba
de ida y vuelta… y la tabla `cliente` no tenía ninguna columna donde guardarla.**
Se exigía un dato para tirarlo. Por eso la columna «Cédula» del listado salía
vacía en los 5.000 clientes: no es que no se hubiera rellenado, es que nunca se
guardó nada.

Ahora son dos columnas —`tipo_documento` y `numero_documento`— y no una por
tipo, porque un cliente tiene **un** documento de **un** tipo; dos columnas
admitirían el estado imposible de tener cédula y RUC a la vez y obligarían a
decidir cuál mirar en cada consulta.

| tipo | formato | quién |
|---|---|---|
| `cedula` | 10 dígitos | persona natural |
| `ruc` | 13 dígitos | empresa o persona con actividad económica |
| `pasaporte` | 5 a 20 caracteres | extranjero sin cédula |

**Cuatro decisiones que conviene no deshacer:**

1. **Admite nulo, y es a propósito.** Los 5.000 clientes que ya existen no tienen
   documento y **no se les inventa uno** (§5: no se reparan datos históricos).
   Salen como «sin documento», que es lo que son. Exigirlo habría impedido hasta
   editarles el teléfono.
2. **El número se normaliza antes de guardarlo.** `17-1234-5620` y `1712345620`
   son la misma cédula para una persona y dos textos distintos para el índice
   único. Sin limpiar, dos empleados escribiéndola con y sin guiones habrían
   creado dos clientes sin que la base se enterara.
3. **Es único, pero solo entre los que lo tienen** — índice parcial, para que los
   5.000 nulos convivan con la garantía. Y el aviso dice **de quién** es el
   documento repetido: el índice solo, al saltar, daba un conflicto genérico que
   no decía ni el dato ni el dueño.
4. **No se cifra**, al revés que correo, teléfono y dirección. El documento hay
   que poder **buscarlo** —es como se identifica a un cliente en mostrador— y lo
   cifrado no se busca por prefijo ni se indexa para exigir unicidad. Los otros
   tres se cifran porque solo se leen.

**El fallo que tuvo esta misma fase, y que la prueba fija.** La validación
comprobaba el número **ya normalizado**, que es nulo cuando falta el tipo — así
que un número escrito sin elegir tipo **se perdía en silencio**: exactamente el
defecto que la fase venía a arreglar. Ahora se mira el original.

### F73-bis · El pedido especial deja de ocupar una banda para una casilla

«Pedido Especial» era una sección entera del formulario para contener **una
casilla de verificación**. Ahora es un interruptor en la cabecera de «Datos del
pedido», y los campos aparecen debajo solo cuando hay algo que rellenar — con una
línea explicando lo que casi nadie sabe: que un pedido especial **se crea aunque
no haya stock**, porque existe precisamente para prepararse o fabricarse.

### F74 · Picking y detalle del pedido: los recuadros encimados y algo peor

Lo dijo el dueño mirando las dos pantallas: *«se ven feas, hay unos cuadros que se
ven encimados»*. Tenía razón, y la causa era literal.

**Ninguno de los dos componentes tenía una sola regla de estilo.** Los dos
llevaban el mismo comentario —`/* Inherits global dark theme from styles.scss */`—
y no heredaban nada, porque las clases que usaban (`.linea-controles`, `.campo`,
`.info-card`) **no existen** en `styles.scss`. Sin reglas, los elementos se
apilaban en el orden del HTML: en picking, «cantidad total» caía al fondo de su
columna y el desplegable de bodega quedaba montado sobre la casilla de al lado.

**Pero lo que más costaba no se veía.** El desplegable de bodega listaba **las 20
bodegas sin decir cuál tiene la mercancía**. Quien recoge tenía que adivinar el
almacén, y equivocarse no es un error de pantalla: es un movimiento de stock
contra una bodega que no tenía la prenda.

Ahora cada línea le pregunta al inventario dónde está el producto y lo dice en el
propio buscador. **La bodega se escribe**, con el mismo `app-searchable-select` que ya
usaban el cliente y el producto: se teclea «BRE» y sale «Bodega BRE1 · 331
u.». Cada opción lleva **las unidades que hay en esa bodega**, las que tienen
existencias van primero y las demás dicen «sin existencias» —recoger de un
almacén sin registro es raro, pero pasa—. Si solo hay un sitio posible, viene ya
puesto.

> **Primero se probó con fichas, una por bodega, y estaba mal.** Con un producto
> de catálogo salían veinte fichas por línea, y con siete líneas en pantalla era
> un muro. Lo pidió el dueño y tenía razón: *«solo pon un filtro así como los
> otros para buscar en él por escrito»*. Un buscador ocupa un renglón y encuentra
> por nombre, que es como se busca una bodega de verdad.

> **Se usa el listado de inventario que ya existía, no un endpoint nuevo.** Uno
> nuevo habría que concedérselo a los seis roles; éste ya lo tiene quien recoge.

El resto de la pantalla: contador con **−/+** y un botón «Todas» en vez de teclear
la cifra, borde de color por estado (sin empezar / a medias / entera), avance en
barra, y al terminar un cierre que dice **qué toca después** —empacar— con el
enlace. La explicación de «guardar a medias» solo sale cuando de verdad se va a
guardar a medias.

**En el detalle del pedido** se decía «Corporativo» tres veces en tres recuadros
anidados. Ahora hay una banda; la ruta del pedido se ve como cuatro puntos
(pendiente → procesado → enviado → entregado) en vez de deducirla del color de
una etiqueta; la tabla tiene su fila de total; y los botones de estado llevan
delante lo que significan: que procesar **reserva** las unidades, y que es todo o
nada.

Y una tercera del mismo tipo, que costó encontrar porque no parecía un fallo
de apilamiento: la lista del buscador **salía recortada** porque la tarjeta de
la línea lleva `overflow: hidden`. El z-index 1200 de la lista no pinta nada ahí
—no la tapa nadie, la recorta su propia caja—. Se arregla con el mismo `:has()`
que `styles.scss` ya usa para las secciones de formulario: mientras hay una lista
abierta, la tarjeta deja de recortar y se levanta.

**Dos cosas que enseñó esta fase.** La primera, que `.acciones` ya existía en
`styles.scss` con `display: flex`, y aplanó una sección entera sin dar error
(aviso 13). La segunda, que un solape **se mide**, no se mira: comparar los
rectángulos de los bloques con `getBoundingClientRect()` encontró en un segundo
lo que a ojo, en una captura, es discutible.

### F76 · Un componente compartido no puede depender de la pantalla que lo usa

«Está demasiado fina, ¿qué es esooo?», y era verdad: en picking la barra de
búsqueda medía **19 px** y en el resto de la aplicación **44**.

La causa: `app-searchable-select` declaraba de su caja de texto **solo el hueco
de la flecha** (`padding-right: 2.5rem`). El alto se lo ponía la regla global
`.form-group input`, que existe en `styles.scss`. En «Pedido nuevo» y en «Nueva
orden de compra» el buscador va dentro de un `.form-group` y hereda los 44 px;
en picking no hay ningún `.form-group` alrededor, así que se quedaba en la altura
natural del texto: una raya.

El arreglo no es ponerle un alto a la pantalla de picking —eso deja la trampa
puesta para la siguiente—, sino que **el componente traiga su tamaño**: las
mismas medidas que ya calculaba la regla global (`.7rem 1rem`, `.9rem` de letra),
declaradas donde vive el componente. Medido después en las tres pantallas: 44 px
en las tres, exactamente lo que valían antes las otras dos.

> **Un `line-height` de más las subió a 45 px**, es decir, cambió pantallas que
> nadie había tocado. Un componente compartido se toca midiendo antes y después
> **en todas** las pantallas que lo usan, no solo en la que se está arreglando.

### F77 · El empaque preguntaba tres cosas que no debía preguntar así

Tres preguntas del dueño delante de la ventana de confirmar empaque, y las tres
destapaban algo.

**«¿Qué es número HU?»** *Handling Unit*: la etiqueta del **bulto** que sale del
almacén —la caja, no el pedido—. Es lo que se pega encima y por lo que se
pregunta si algo se pierde. **No lo decía ningún sitio.** Ahora lo explica la
propia ventana, y el número viene propuesto: antes era `HU-<fecha>-<3 cifras al
azar>`, que choca consigo mismo con muy pocos bultos el mismo día y **sin avisar**,
porque la columna no es única. Ahora lleva el número del pedido, que es lo que
hace falta cuando alguien llama preguntando por una caja.

**«¿Los transportistas se pueden buscar por escrito, o no hay transportista en la
bd?»** No lo había. `pedido.transportista` era un `VARCHAR(100)` libre escrito a
mano: en 19.000 pedidos, **un solo valor**, de una prueba. Con texto libre
«Servientrega», «servientrega» y «Servi entrega» son tres transportistas para
cualquier consulta, y no se puede responder «cuánto mandamos por cada uno» sin
adivinar. Ahora hay catálogo, se elige escribiendo, y cada opción dice su
cobertura —saber el nombre no dice si llega al Oriente—.

> **El catálogo es de solo lectura desde la aplicación**, y es deliberado: dar de
> alta un transportista es una decisión de negocio, no una casilla de la pantalla
> de almacén. La F77 concede `SELECT` y nada más, así que la base rechaza un
> INSERT aunque lo intente el usuario administrador. Administrarlo desde la
> interfaz será otra fase, con su permiso y sus privilegios.

**«¿Región de destino qué es? ¿No sería la ciudad del cliente que lo pidió?»**
Tenía razón, y era el fallo de modelado más gordo de los tres: **se tecleaba un
dato que ya se sabía**. El pedido tiene cliente, el cliente tiene ciudad, y la
ciudad está en una región. Pedir a mano algo deducible es la forma segura de que
acabe mal escrito.

La región no es del pedido: **es de la ciudad**. Por eso la columna va en
`ciudad`, se rellenó una vez para las 88, y el empaque la propone en lugar de
preguntarla. Sigue siendo editable, porque un bulto puede mandarse a otro sitio,
pero el caso normal deja de teclearse.

> **Clasificar las 88 ciudades no es inventar un dato**: la región natural de una
> ciudad ecuatoriana es un hecho geográfico. El criterio es la **provincia** del
> cantón, para que la zona de reparto cuadre con la división administrativa —por
> eso Puerto Quito queda en Sierra (Pichincha) y La Concordia en Costa (Santo
> Domingo), aunque las dos estén en tierras bajas—. Salieron 47 Costa, 30 Sierra
> y 11 Oriente; ninguna sin clasificar, y el script falla si queda alguna.

**El fallo que costó una hora, y la lección.** Al probar el buscador de
transportista, la lista **no filtraba**: se escribía «laar» y seguían saliendo los
siete. El estado interno del componente era correcto —`busqueda: "laar"`,
`filtradas: 1`— pero la pantalla no se repintaba.

La causa no estaba ni en el buscador ni en el filtro: `formValido()` hacía
`this.form.transportista.trim()`, y el buscador pone ese valor a `null` en cada
letra —lo escrito a medias todavía no es una elección—. `null.trim()` lanzaba
dentro de la **plantilla**, y una excepción ahí **aborta la detección de cambios
entera**: el resto de la vista se queda congelado. El síntoma —«el filtro no
filtra»— no se parecía en nada a la causa —«una validación no admite nulos»—.

> **Lo que lo encontró fue mirar la consola del navegador**, no leer el código:
> el error estaba ahí desde el principio, en rojo, diciendo exactamente qué línea.
> Antes de teorizar sobre change detection, léela.

### F78 · La devolución dejaba rellenarlo todo para fallar al enviar

Dos casos distintos, la misma causa: **la pantalla enseñaba menos de lo que la
base exige**.

**Uno. El pedido tiene que estar entregado**, y no lo decía en ninguna parte.
Sobre un pedido en `procesado` se podía elegir motivo, marcar líneas y pulsar
«Registrar solicitud» — para recibir un error al final. Ahora, si el pedido no
está entregado el formulario **no aparece**: en su lugar se explica por qué y qué
le falta al pedido para llegar ahí, distinto según el estado en que esté.

**Dos. Lo devuelto antes gasta cupo.** La tabla solo enseñaba «cantidad comprada»
y dejaba escribir hasta ahí, aunque una solicitud anterior ya se hubiera llevado
media línea. El backend lo rechaza —compara contra el acumulado— pero la pantalla
lo descubría después de escribirlo. Ahora cada línea dice **comprado · ya
devuelto · queda**, el tope es lo que queda, y una línea agotada no se puede ni
marcar.

> **La cuenta se hace con la misma regla que el backend**: cuentan todas las
> solicitudes menos las `rechazada`, porque en una rechazada no se llevó
> mercancía. Si las dos cuentas se separan, la pantalla vuelve a ofrecer un tope
> que el servidor rechaza — que es exactamente el defecto que se estaba
> arreglando. Sale del listado que ya existía (`/devoluciones?idPedido=`), sin
> endpoint nuevo.

Lo demás de la pantalla: cabecera con cliente, fecha y estado; los seis motivos
como tarjetas con lo que significa cada uno; un aviso —solo cuando se elige
«producto defectuoso»— de que esa es la única causa que puede acabar en devolución
al proveedor; el resumen de cuántas unidades y líneas van; y una línea diciendo lo
que esto **no** hace: no devuelve dinero ni mueve stock, es una solicitud que
bodega inspecciona.

**Y se quitó la explicación de qué es un HU** de la ventana de empaque, a petición
del dueño: *«ya los que manejan el sistema sabrán eso»*. Tenía razón — una
definición de término no es ayuda contextual, es ruido para quien usa la pantalla
todos los días. Lo que sí se queda es lo que la pantalla **hace**: que confirmar
descuenta stock, y que la región sale de la ciudad del cliente.

### F79 · Inspeccionar una devolución: tres decisiones que no se deshacen

La pantalla enseñaba los datos, pero **no decía qué hace cada cosa** — y aquí se
toman tres decisiones que no tienen vuelta atrás.

**El resultado de cada línea mueve, o no, el stock.** «Apto reventa» **devuelve la
mercancía al inventario** de la bodega que se elija; «defectuoso» no la devuelve y
la deja disponible para reclamársela al proveedor; «rechazado» no hace nada. Los
tres se elegían en un desplegable idéntico, sin una palabra sobre la diferencia:
tres opciones que parecen equivalentes y una de ellas escribe en el inventario.
Ahora cada una es una tarjeta que dice su efecto, y antes de guardar la pantalla
avisa de cuántas unidades van a volver al stock.

**Una línea inspeccionada no se puede volver a inspeccionar** —el backend lo
rechaza— y tampoco se decía. Ahora se dice, y se pueden revisar de una en una:
lo que se deje sin marcar queda pendiente.

**El reembolso tiene tope**, y era el mismo defecto de la F78 en otra pantalla: el
importe se escribía a mano y el error salía al enviar. El tope es el valor de las
líneas que la inspección **no rechazó**, al precio al que se vendieron. Ahora se
calcula, se enseña, viene propuesto en la casilla y teclear de más lo recorta.

> **Hizo falta un dato del backend**: `precioUnitario` en la línea de la
> devolución. Sin él la pantalla no puede calcular el tope, y un tope calculado
> con otra fórmula que la del servidor es peor que no tenerlo. Es un campo en un
> DTO que ya se enviaba, no un endpoint nuevo.

> **La misma trampa, por tercera vez.** F78 y F79 son el mismo defecto en dos
> pantallas: la interfaz pedía datos sin enseñar la regla que el servidor va a
> aplicar. Cuando una validación del backend dice «no puedes», merece la pena
> preguntarse si la pantalla podía haberlo dicho antes.

### F80 · Análisis del negocio: la pregunta que no tenía dónde vivir

Lo pidió el dueño para el paso 8: *«métele otros gráficos extra de producto más
vendido, más comprado, mejor cliente… qué ciudad o región vende más»*.

**Por qué una pantalla nueva y no más tarjetas en /indicadores.** Son dos
preguntas distintas. Los indicadores contestan «¿cómo va todo **ahora**?» y se
miran de pie, en diez segundos; el análisis contesta «¿qué está pasando?» y se
mira sentado, cambiando la ventana de tiempo y comparando. Meter lo segundo en lo
primero habría hecho el tablero más lento de leer sin hacer el análisis mejor. Las
dos pantallas se enlazan entre sí, y el paso 8 del flujo ofrece las dos.

**Ocho bloques, una sola petición.** Comparten la ventana de fechas y se miran
juntos: partirlos en ocho llamadas los dejaría desincronizados —un gráfico de
agosto al lado de otro de julio— y obligaría a conceder ocho permisos donde basta
uno. Va con `dashboard:ver`, que ya tienen exactamente los dos roles a los que
esto le sirve; los otros cuatro reciben 403, comprobado.

**Las decisiones de dibujo, que no son de gusto.**

- **Barras horizontales en todo ranking.** Un producto se llama «ZAP NIK
  DM0113-100 W NIKE COURT V 5»; en columnas verticales ese nombre se gira y deja
  de leerse.
- **Un solo color por gráfico.** Las barras son categorías *nominales* —productos,
  ciudades— y su magnitud ya la dice la longitud. Pintar cada barra de un color
  gastaría el canal de identidad en repetir lo que la barra ya dice. Se usa el oro
  de la marca, que da **8,5:1** sobre el fondo del panel (el mínimo para una marca
  es 3:1).
- **La granularidad la elige la ventana.** En 30 días una serie mensual son **dos
  puntos**, y dos puntos unidos por una recta no son una tendencia: son una recta.
  Hasta 120 días la serie es diaria; a partir de ahí, mensual.
- **Un día sin ventas vale cero y se dibuja.** El día existió y no se vendió: es un
  dato, no un hueco. Sin rellenarlo, la línea junta el día 3 con el día 7 como si
  fueran consecutivos. Un **mes** que falta sí se deja fuera —en «todo el
  histórico» la ventana empieza en 2000 y rellenar veinte años de ceros sería
  inventar—. La diferencia es si el hueco se conoce.
- **Cada gráfico lleva su tabla**, plegada. Es la lectura que funciona sin ver
  color y la que se copia a un informe.

**Tres cosas que la prueba fija y que no se ven en pantalla:** que un pedido
anulado no cuenta en ninguna cifra —contarlo sería premiar una venta que no
ocurrió, y el gráfico saldría igual de bonito—; que la ventana la traduce el
servidor y vuelve en la respuesta, para que la pantalla pueda decir de cuándo son
las cifras; y que la serie diaria trae un punto por día.

> **Lo más comprado cuenta lo recibido, no lo pedido.** Una orden aprobada todavía
> no es mercancía, y en una `recibida_parcial` lo que hay en el almacén es lo que
> llegó. Se filtra además la materia prima: una línea de orden de compra puede no
> ser un producto del catálogo.

> **Es «por categoría» y no «por marca» porque la marca no es una tabla**: vive
> dentro del texto de la descripción del producto («Marca: NIKE»). Agrupar por un
> trozo de texto libre sería inventarse una dimensión que el modelo no tiene.

### F81 · Reportes y auditoría: pantallas que no explicaban de qué iban

**Reportes abría en blanco.** Seis pestañas con seis nombres, un panel de filtros
y nada más: había que adivinar cuál servía, abrirla, y pulsar «Vista previa» para
ver si traía algo. Ahora abre con **los últimos 30 días ya cargados**, y debajo de
las pestañas hay una frase que dice **qué pregunta contesta** el informe abierto —
que es lo que hacía falta para elegir sin abrirlos todos.

**Y había un aviso que mentía.** Cuando la vista previa traía 100 filas o más,
salía este texto:

> «Mostrando 100 de N resultados. **El archivo exportado incluirá todos los
> registros.**»

Es **falso**: la exportación manda el mismo filtro, con el mismo `limite`. Quien
exportaba con el límite en 100 se llevaba un Excel de 100 filas creyendo que
estaban todas — y un informe incompleto que parece completo es peor que no tener
informe. Ahora el campo se llama «cuántas filas traer», dice que **también limita
lo que se exporta**, y cuando el resultado llega justo al límite la pantalla avisa
de que es posible que haya más quedando fuera.

**La región de destino se escribía a mano** en una caja de texto con el ejemplo
«Ej: Sierra». Desde la F77 la región es un conjunto cerrado de cuatro valores que
sale de la ciudad del cliente: ahora son cuatro botones. Escribir «sierra» en
minúscula no encontraba nada y no lo decía.

**En auditoría se pedía el «Producto ID» a mano**, el mismo defecto que el dueño
señaló en el movimiento de inventario (F72): nadie se sabe los ids. Ahora es el
buscador por nombre. Además: atajos de fecha —hoy, 7 días, 30 días, todo—, el
número de registros que cumplen el filtro, y las acciones que mueven dinero o
stock —aprobar, anular, reembolsar, liberar reserva— resaltadas, que son las que
se buscan en una lista de cientos de líneas iguales.

Las dos pestañas dicen ahora qué son. La del historial de inventario dice algo que
no se ve y que importa: **lo escribe un disparador de la base de datos, no la
aplicación**, así que aunque alguien tocara el stock por fuera del sistema la fila
aparecería igual.

### F82 · El asistente: una caja de texto que no iba a contestar

**El módulo está apagado en esta instalación** —`app.ia.enabled=false`, que es el
valor por defecto— y la pantalla no lo decía. Ofrecía ocho ejemplos y una caja de
texto como si funcionara; el 503 llegaba **después** de escribir la pregunta y
enviarla. Ahora se pregunta al entrar, con un endpoint nuevo
(`GET /api/ia/estado`, mismo permiso), y si está apagado se dice antes de que
nadie escriba: qué significa el interruptor, cómo se enciende, y —lo más útil—
que **lo que casi siempre se le pregunta ya está resuelto sin IA**, con enlaces al
análisis del negocio y a los reportes.

> El endpoint devuelve solo `habilitado`. Ni la clave ni el modelo: eso es
> configuración del servidor y el navegador no tiene por qué verla.

**Y no decía qué puede ver.** Un asistente que consulta la base plantea una
pregunta legítima —«¿puede leerlo todo?»— que la pantalla no contestaba. La
respuesta estaba en el validador del servidor y en ningún sitio donde la viera
quien usa la pantalla:

- Solo ejecuta **una** sentencia, y tiene que ser un `SELECT`; además corre en una
  transacción de solo lectura, así que el motor rechazaría una escritura aunque
  colara.
- Solo sobre una lista **blanca** de tablas de negocio: una tabla nueva queda
  fuera mientras nadie la añada a mano, que es el lado seguro por el que
  equivocarse.
- **Nunca** usuarios, roles, permisos ni la bitácora. Un Supervisor no tiene por
  qué poder pedirle al asistente los datos de sus compañeros.

Eso está ahora en la propia pantalla, plegado. Lo demás son detalles de uso: la
consulta que ejecutó se puede copiar (solo el Administrador la ve), las columnas
numéricas se alinean a la derecha y se formatean, `total_vendido` se lee como
«Total vendido», un error ofrece volver a preguntar, y si apagan el módulo con la
pantalla abierta se pasa al panel de apagado en vez de dejar la caja viva.

> **Se comprobaron las dos mitades sin gastar la clave del dueño.** La mitad
> encendida se verificó arrancando el backend con `IA_ENABLED=true` **en el
> entorno del proceso** —sin tocar ningún fichero— y mirando la pantalla **sin
> enviar ninguna pregunta**: enviarla habría llamado a un servicio externo y eso
> lo decide él, no yo. Después se devolvió el backend a como estaba.

### F83 · El asistente ya contesta, y por el camino había un fallo de verdad

El dueño consiguió una clave de Google Gemini y pidió enchufarla. Tres cosas
salieron de ahí, y la segunda es la que importa.

**1. El proveedor es ahora una pieza intercambiable.** Hasta aquí el servicio
hablaba con Anthropic desde dentro: el cuerpo de la petición, las cabeceras y el
camino dentro del JSON estaban escritos en medio de la lógica, así que cambiar de
proveedor obligaba a tocar el mismo método que valida y ejecuta el SQL — la parte
que no debe moverse. Ahora hay una interfaz de un solo método: **recibe dos textos
y devuelve uno**. Validar que es un `SELECT`, ejecutarlo en solo lectura y no
filtrar el error de PostgreSQL al cliente sigue igual y no depende de quién
conteste. Se elige con `app.ia.proveedor`.

**2. Y entonces apareció el fallo que llevaba ahí desde el principio.** Con el
asistente ya contestando, la primera pregunta de verdad —«los 3 productos más
vendidos»— devolvía *«no se pudo ejecutar la consulta, prueba a reformular la
pregunta»*. La consulta que escribió el modelo era correcta.

El ejecutor acotaba las filas con `setMaxResults(500)`. Sobre una consulta
**nativa**, eso hace que Hibernate le pegue al final un
`fetch first ? rows only`; si el SQL ya traía su propio `LIMIT` quedaban las dos
cláusulas seguidas y PostgreSQL respondía *«error de sintaxis en o cerca de
fetch»*. Es decir: **la pregunta más natural que se le puede hacer a un asistente
—«dame los N primeros»— era justo la que no funcionaba**, y el mensaje apuntaba a
donde no era: a la redacción de la pregunta, cuando el problema estaba en cómo se
ejecutaba.

Ahora el tope se pone **envolviendo** la consulta (`select * from (…) limit 500`),
que respeta el LIMIT de dentro y añade el de la casa por fuera. Cuatro pruebas lo
fijan, y ninguna llama a ningún modelo: ejecutan SQL de la forma que devuelve el
asistente, que es la parte que tiene que aguantar.

**3. Gemini se satura, y eso no puede costarle la pregunta al usuario.** El
servicio devuelve 503 «high demand» con bastante frecuencia y casi siempre se le
pasa en segundos. Se reintenta hasta tres veces con espera creciente, y **solo**
lo que tiene sentido reintentar: saturación (503) y límite de ritmo (429). Una
clave inválida o un modelo que no existe no mejoran esperando. El cuerpo del error
de Google va al log del servidor y se traduce a una frase que dice qué arreglar
—«la clave no es válida», «el modelo está saturado»—, porque «400 Bad Request» no
se puede arreglar.

> **El modelo por defecto es `gemini-3.6-flash`.** El de la documentación de
> Google, `gemini-flash-latest`, apunta a uno que devolvía 503 una vez tras otra;
> y `gemini-2.5-flash` responde 404: *«no longer available to new users»*.
> Comprobado con la clave del proyecto, no supuesto.

> **La clave no está en el repositorio.** Vive en `application-local.properties`,
> que está en `.gitignore`; `application.properties` solo lleva el marcador
> `${GEMINI_API_KEY:}`. Y como se pegó en un chat, **hay que rotarla**: una clave
> que ha viajado por un canal que no controlas se da por comprometida.

### F84 · Normalización: cuatro cosas que la base guardaba dos veces

El dueño pidió revisar si el esquema está **en 3FN como mínimo** y, si no,
normalizarlo. Se revisó entero: 46 tablas, sus claves, sus dependencias y sus
datos.

**Lo que estaba bien, y por qué.** No hay ninguna tabla con clave compuesta y
dependencias parciales —todas tienen clave sustituta de una sola columna, así que
la 2FN se cumple sola—. Y los «totales» que a primera vista parecen redundantes
(`detalle_pedido.subtotal`, `cuenta_por_pagar.saldo_pendiente`,
`orden_produccion.costo_total`, `merma`, `costo_linea`) son **columnas
GENERATED**: el gestor garantiza la dependencia y no puede haber anomalía. Eso no
es guardar dos veces, es que la base calcula.

**Los cuatro defectos que sí había:**

| | Qué guardaba de más | Por dónde se llega ahora |
|---|---|---|
| 1FN | `transportista.cobertura` era una **frase** con una lista dentro: «Nacional, incluye Oriente», «Costa y Sierra» | tabla `transportista_cobertura`, una fila por región, + `nota` para el matiz |
| 3FN | `pedido.transportista` guardaba el **nombre**, no la clave | `pedido.id_transportista` &rarr; `transportista` |
| 3FN | `pedido.region_destino` se **tecleaba** y era deducible | `pedido` &rarr; `cliente` &rarr; `ciudad.region` |
| 3FN | `cuenta_por_pagar.id_proveedor` se **copiaba** de la orden | `cuenta` &rarr; `factura` &rarr; `orden_compra.id_proveedor` |

**La región tenía una defensa posible, y no se sostiene.** Se podría argumentar
que es una «foto del momento del envío». En otro sistema lo sería; aquí no,
porque **el pedido no guarda ninguna dirección de envío**: la dirección se lee
siempre viva de `cliente.direccion_enc`. Congelar solo la región mientras la
dirección es la actual da el peor resultado posible — un informe que dice «Costa»
al lado de una dirección de Quito. O se congela el destino entero, o no se
congela nada; hoy no se congela nada.

**La prueba de la cuenta por pagar estaba en el código, no en la teoría.**
`FacturaCompraService` hacía literalmente `cuenta.setProveedor(orden.getProveedor())`.
Las 2.293 cuentas cuadraban, y nada garantizaba que siguieran cuadrando.

**Lo que se miró y se deja como está, a propósito:**

- `detalle_pedido.precio_unitario`, `comprobante_interno.total`,
  `orden_produccion_consumo.costo_unitario_snapshot` — son **fotos históricas**.
  El precio de hoy no es el precio al que se vendió.
- `inventario.stock_actual`, `materia_prima.stock_actual` — saldos acumulados,
  deducibles de los movimientos pero mantenidos por trigger. Es la
  desnormalización deliberada de cualquier inventario.
- `devolucion_proveedor_detalle.id_producto` — **parece** deducible siguiendo el
  origen, y no lo es: **2.824 líneas de orden de compra son materia prima y no
  tienen producto**. Quitarlo rompería el caso en vez de arreglarlo.
- `token_revocado.correo` — `jti` es la clave y todo depende de ella; no hay
  dependencia entre no-claves.

**Cómo se comprobó que no se pierde nada.** El guion se para entero si algún
pedido tiene un transportista que no está en el catálogo, si alguna región de
destino no coincide con la de la ciudad de su cliente, o si alguna cuenta apunta
a otro proveedor que su orden. Ninguna saltó: 2 pedidos migraron su
transportista, las 2 regiones coincidían y las 2.293 cuentas también.

> **Una consulta de menos, no de más.** Al hacer el transportista una clave
> ajena, Hibernate empezó a pedirlo aparte: una consulta por cada transportista
> distinto de la página. Lo cazó `RendimientoDespachosTest`, que **cuenta
> consultas** en vez de medir tiempo. Se arregló con `LEFT JOIN FETCH` en
> `findDespachados` y dejando la cobertura en LAZY con su propio `JOIN FETCH` en
> el único sitio que la necesita.

### F85 · Los formularios y la base dicen lo mismo

El dueño pidió comprobar «que los campos que están en la base sean los que los
formularios del sistema piden». Se recorrieron los 27 formularios de alta y
edición contra las columnas. **Salieron cuatro cosas.**

**1. La ficha de producto exigía un precio de compra, lo tiraba, y enseñaba el de
venta en su lugar.** Es la peor de las cuatro. `ProductoService.toDTO` hacía
`dto.setPrecioCompra(producto.getPrecio())` — el precio de **venta**—, así que el
margen de cualquier producto salía exactamente cero. Y al guardar,
`guardarProveedores` marcaba **siempre** `esProveedorPrincipal = false` y nunca
escribía `precioCompra`: ningún producto dado de alta por la pantalla llegaba a
tener proveedor principal ni precio de compra. No se veía mirando los datos
porque los 105 productos del poblado inicial sí lo tienen — lo puso el seed.

Ahora el precio de compra se guarda donde vive, en
`producto_proveedor.precio_compra` del proveedor principal, y se lee de ahí.
Deja de ser obligatorio: un producto **fabricado** no se le compra a nadie, tiene
coste de producción. Y sin proveedor sale **nulo**, no cero — cero sería «se
compra gratis».

**2. «Stock mínimo» en la ficha del producto no se guardaba en ninguna parte.**
El mínimo no es del producto: es del producto **en cada bodega**
(`inventario.stock_minimo`). Un número suelto en la ficha no sabría a qué bodega
aplicarse. El campo se ha quitado; se pone desde Inventario.

**3. El formulario de proveedor pedía una ciudad que la tabla no tiene.**
`proveedor` no tiene columna de ciudad: lo que se elegía se perdía al guardar y
la columna «Ciudad» del listado salía siempre vacía. Se ha quitado el campo (y la
columna del listado, que ahora enseña el correo). Ponerle ciudad al proveedor es
una decisión con su columna y su clave ajena — queda anotada abajo.

**4. El formulario de ciudad no pedía la región, y desde la F84 es
imprescindible.** `ciudad.region` existe desde la F77 y es el único sitio de
donde sale la región de destino de un envío. Toda ciudad creada desde la pantalla
nacía sin región: sus pedidos no enseñaban destino al empacar y **no aparecían
nunca** en el filtro de despachos por región. No fallaba nada; simplemente no
salía. Ahora se pide, con las cuatro del CHECK, y el listado marca las que están
«sin clasificar».

**Y de paso, las longitudes.** Ocho campos no decían cuánto cabe mientras su
columna sí tenía tope. Sin `@Size`, un nombre de 200 caracteres llega a
PostgreSQL, salta por longitud (22001) y `GlobalExceptionHandler` lo traduce a
*«La operación entra en conflicto con datos existentes. Puede que el registro ya
exista»* — que no es lo que pasa, y manda a buscar un duplicado que no hay. Hay
una prueba que compara **cada `@Size` con `information_schema`**, para que no
vuelvan a separarse.

---

## 4. Lo que queda abierto

**Ningún defecto.** Los 43 están cerrados. Lo que sigue no son defectos: son
cosas que conviene saber antes de tocar nada.

### F69 · La reposición del proveedor deja de ser una promesa verbal

Lo preguntó el dueño al registrar que un proveedor «manda otra igual»: *«¿yo no
tendría que pagar eso, no? ¿y cómo sé que me va a llegar?»*. Tenía razón en las
dos, y eran dos agujeros distintos: **la reposición llegaba y se recibía como una
compra cualquiera**, con su factura y su cuenta por pagar — es decir, pagando dos
veces la misma mercancía.

Ahora, al aceptar una reposición se crea una **orden de compra marcada**
(`es_reposicion`), ya aprobada y ligada a la devolución. Sabes que viene porque
sale en «Aprobadas sin recibir»; entra al stock al recibirla, con su movimiento;
y **no se puede facturar por ninguna vía**.

**Su propuesta era una orden a precio cero, y se cambió una cosa.** La idea de
fondo —reutilizar la orden de compra, que ya tiene el circuito de recepción
montado— es la correcta. El precio cero no:

- `chk_oc_detalle_precio` exige `precio_unitario > 0`, y §5 prohíbe tocar los CHECK.
- La recepción recalcula el **costo promedio ponderado** (F29). Entrar mercancía
  a cero falsearía el costo de todo lo que hay en bodega — una mentira contable
  peor que el problema original.

Así que la línea lleva **precio real** y lo que impide pagarla es la marca, no un
cero. `FacturaCompraService` la rechaza en las dos vías: la automática y la
manual. Blindar solo el botón no habría servido — el endpoint está abierto a
cualquiera con `facturas_compra:registrar`.

> **Dos decisiones que parecen omisiones y no lo son.**
>
> **La orden no tiene aprobador.** Aprobar es autorizar un gasto, y aquí no se
> gasta: el proveedor ya se comprometió al aceptar la reclamación. Poner una
> firma sería inventarla. Y hay una segunda razón, descubierta a la fuerza: la
> F34 no le concede a Compras el INSERT de `id_usuario_aprobador` —para que no
> pueda auto-aprobarse—, así que rellenarlo hacía que PostgreSQL rechazara el
> INSERT entero. La respuesta correcta **no era conceder el privilegio**.
>
> **`es_reposicion` tiene INSERT pero NUNCA UPDATE.** La marca se pone al nacer
> la orden o no se pone; nadie puede volver no facturable una compra que sí había
> que pagar. Se comprobó sin querer: la primera versión de la prueba hacía un
> `UPDATE` y PostgreSQL lo rechazó, que es exactamente lo que tenía que pasar.

`sql/fase69_reposicion_del_proveedor.sql`, `ReposicionNoSePagaTest` (5 pruebas).
Comprobado de punta a punta sobre la aplicación: recepción con 2 defectuosas →
devolución → reposición → orden #2679 creada → recibida (**el stock subió**) →
factura rechazada por las dos vías.

---

### Deuda que sigue ahí, y que no es un defecto

- **`scripts/fase37_pruebas_endpoints.ps1` sigue probando solo lecturas.** Es la
  tarea pendiente más importante que queda, y la F65 lo confirmó por segunda
  vez: dos pasos del flujo estaban rotos para sus propios roles y ni las 148
  pruebas ni un barrido de 128 pantallas lo vieron, porque ninguno escribía. Es lo que dejó a D-39 escondido
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

### F86 · Un parámetro que falta no es una avería del servidor

Salió al barrer **los 47 endpoints GET** uno a uno para responder a la pregunta
«¿el programa sigue funcionando al 100%?». Dos devolvían **500 «Error interno del
servidor»**, y no porque estuvieran rotos: es que hay que llamarlos con un
parámetro y no se lo pasé.

Es exactamente la misma historia que el 404 de la **F63**, con otro disfraz. Al
cliente le llegaba «error interno del servidor» —haciéndole creer que el fallo
está en el servidor cuando está en la petición— y al registro le caía un
`ERROR "Error no controlado"` con la traza entera, por cada llamada mal hecha.

Ahora `MissingServletRequestParameterException` y
`MethodArgumentTypeMismatchException` devuelven **400 diciendo qué parámetro es**:
*«Falta el parámetro «idProducto»»*, *«El parámetro «idProducto» no tiene un
valor válido»*. El nombre del parámetro es parte del contrato público del
endpoint, así que decirlo no filtra nada y ahorra adivinar.

**Resultado del barrido, con el arreglo puesto: 45 de 47 en 200, y los 2
restantes en 400 con su motivo.** (Uno de ellos, `verificar-disponibilidad`, da
400 también con parámetros correctos si el producto no tiene lista de materiales
— y lo dice: *«El producto no tiene lista de materiales definida»*. Con un
producto fabricado de verdad devuelve 200.)

### Spring Boot 3.2 se quedó sin soporte gratuito

`pom.xml` fija **Spring Boot 3.2.x**, y el soporte gratuito de esa rama terminó
el **2024-12-31** (el comercial, el 2025-12-31). No es un fallo: la aplicación
funciona y las 198 pruebas pasan. Es deuda, y **se decidió no tocarla ahora**
(2026-08-29), porque un salto de versión mayor puede mover Spring Security,
Hibernate y la configuración de las pruebas, y eso no es trabajo de un rato en un
sistema que ya está entregado.

Cuando se retome, el orden razonable es: primero el **parche dentro de la misma
rama** (3.2.12, sin cambios de API, solo correcciones), pasar las 198 pruebas, y
solo entonces plantear el salto a 3.5.x leyendo sus notas de migración.

Son los **cuatro únicos avisos** que quedan en el panel de problemas del IDE.

### El panel del IDE mentía, y por partida doble

Anotado porque volverá a pasar y hace perder una tarde:

- El 2026-08-29 el panel marcaba **18 errores** en `FormulariosContraLaBaseTest`
  («no se resuelve `com.marathon.soporte`»). **No existía ninguno**: `mvn clean
  test-compile` daba `BUILD SUCCESS` y otros doce tests importaban ese mismo
  paquete sin quejarse. Era el índice del IDE sin refrescar, y se arregló
  **tocando el fichero** para que lo volviera a leer. Regla: antes de perseguir
  un error del panel, **compila a mano**; si Maven no lo ve, no está.
- Y marcaba **186 avisos de «Null type safety»** que javac no emite (el backend
  compila con cero). Salían porque Spring Data declara sus parámetros `@NonNull`
  y este proyecto no usa anotaciones de nulabilidad, así que el análisis avisaba
  en casi toda llamada a un repositorio. Se apagó **solo para este proyecto** en
  `marathon-backend/.settings/org.eclipse.jdt.core.prefs`, que explica por qué;
  se deshace borrando el fichero. El motivo no fue el ruido sino lo que tapaba:
  186 falsos positivos esconden los de verdad — que es exactamente lo que pasó.

### Lo que dejó abierto la revisión de formularios (F85)

Son **decisiones de negocio**, no defectos: se anotan para que las tome el dueño,
no para arreglarlas por cuenta propia.

- **El RUC del proveedor vive cifrado en una columna llamada `contacto_enc`.**
  Funciona —se escribe y se lee—, pero tiene dos consecuencias: **no se puede
  buscar un proveedor por su RUC**, porque está cifrado, y el nombre de la
  columna dice otra cosa que lo que guarda, así que `contacto` ya no puede usarse
  para la persona de contacto. Es justo lo contrario de lo que se decidió para el
  cliente en la **F73**, donde el documento se dejó **sin cifrar a propósito**
  para poder buscarlo. Arreglarlo es una columna `proveedor.ruc` propia, sin
  cifrar, única cuando no es nula y con su CHECK de 13 dígitos — copiando la F73.

- **El proveedor no tiene ciudad.** El formulario la pedía y se perdía; se ha
  quitado el campo. Si hace falta, es una columna `id_ciudad` con su clave ajena
  y su índice, no un campo suelto en la pantalla.

- **El código de producto es fabricado, no guardado.** `producto` no tiene
  columna de código; el `PROD-000123` que se ve en el listado lo compone la
  respuesta a partir del identificador. Sirve para nombrar una fila, **no** para
  guardar el código de fábrica ni un código de barras. Eso sería una columna
  nueva con su unicidad.

- **`bodega.responsable` es texto libre y no apunta a ningún usuario.** Las tres
  bodegas que lo tienen puesto dicen «El Toke», «Fifo» y «Pozo», que no son
  usuarios del sistema. Si el responsable tiene que ser una persona del sistema,
  es `id_usuario` con clave ajena; si es un nombre a mano, está bien como está.

- **No se puede devolver materia prima a un proveedor.**
  `devolucion_proveedor_detalle.id_producto` es obligatorio, y **2.824 líneas de
  orden de compra son materia prima** y no tienen producto. Salió al mirar si esa
  columna era redundante (F84): no lo es, pero deja el hueco a la vista.

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
- **No conviertas la exención del Administrador (F64) en un permiso.** Pregunta
  por el rol a propósito: un permiso se concede marcando una casilla en la
  pantalla de roles, y la separación de funciones dejaría de significar nada.
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
# Backend — 165 pruebas, deben quedar todas en verde
cd marathon-backend
mvn test                     # necesita TEST_DB_PASSWORD en el entorno

# Frontend — la compilación de producción debe pasar los presupuestos de tamaño
cd marathon-frontend
npx ng build --configuration production
```

Si `mvn test` baja de 165 pruebas o alguna falla, **algo se rompió**.

**Y con la aplicación levantada, la comprobación que faltaba.** Las 165 pruebas
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
