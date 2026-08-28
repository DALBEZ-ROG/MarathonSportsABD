# Lo que queda pendiente — MarathonSportsABD

> **Para quién es este documento.** Para retomar el trabajo **sin haber estado en
> las sesiones anteriores**. Todo lo necesario para empezar está aquí; los otros
> tres documentos de `docs/` amplían, pero no son requisito.
>
> Reescrito el **2026-08-27**, después de cerrar D-02, D-13 y D-39 a D-43.
> **Los cinco últimos aparecieron levantando y usando la aplicación, no en las
> pruebas** — que estaban las 131 en verde mientras tres roles no podían crear su
> documento y un pedido recogido no se podía empacar.

---

## 1. De dónde venimos, en un párrafo

El proyecto pasó por auditoría (`AUDITORIA.md`, `DEFECTOS.md`), plan (`PLAN.md`),
ejecución de 17 lotes, rediseño del tablero (`DASHBOARD.md`) y, el 2026-08-27,
las fases **47** (reserva de stock), **48** (matriz de permisos), **49** (D-39) y
**50 a 53**, estas ultimas de depurar la aplicacion en el navegador, mas un
repaso de flujos que encontro tres huecos mas.

Se han encontrado **43 defectos** y se han cerrado **39**. Las pruebas pasaron de
**0 → 105 → 131**, todas en verde. La compilación de producción del frontend
funciona.

**De los cuatro que no están cerrados, tres lo están por una decisión tomada y
escrita, y uno espera una respuesta de negocio.** Ninguno es un olvido.

---

## 2. Antes de tocar nada: diez cosas que van a morderte

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

## 4. Lo que queda abierto

### D-36 · El importe de la factura de compra no se contrasta con lo recibido — S2

**El único que espera una respuesta de negocio.**

`FacturaCompraService.crear()` valida la orden, que tenga recepciones, que el
número no se repita y las fechas. **El subtotal lo pone quien registra la factura
y nadie lo compara con lo que entró.** Los otros dos lados del cotejo sí están:
la recepción no deja recibir más de lo pedido, y el pago no deja pagar más del
saldo.

Medido: de **2.287** facturas, **1.649** tienen el subtotal por encima del valor
recibido; el mayor exceso, **11.194,86**. Casi todas del poblado masivo, así que
no se puede distinguir lo que escribió la aplicación.

**La pregunta que hay que contestar antes de tocar código:** ¿puede el subtotal
llevar flete u otros cargos por encima de lo recibido? ¿Con qué tolerancia? ¿Se
bloquea o se avisa?

**Mientras tanto** el descuadre deja de ser silencioso: queda en `log_accion`
(`compras` / `factura_descuadre`) con las dos cifras. Antes de decidir la regla,
mídelo:

```sql
SELECT count(*) FROM log_accion WHERE modulo='compras' AND accion='factura_descuadre';
```

### D-23 · `logout` no invalida el token — S3, *mitigado*

La ventana baja de 24 h a 2 h. Lo que sigue abierto es la revocación real, que
exige una lista de denegación persistida y una comprobación en **cada** petición.
Descartado por coste el 2026-08-27.

### D-26 · `app.datos-demo.enabled=true` — S3, *parcial*

El agujero grave está cerrado: la aplicación se niega a arrancar con el secreto
JWT por defecto. Queda que los datos demo se creen solos. El cambio es trivial
(`DATOS_DEMO=false`); lo que hay que resolver antes es el procedimiento de primer
arranque sin ellos —incluida la siembra de `rol`, ver §2.7— y avisar a quien ya
tenga el entorno montado.

### D-27 · El JWT vive en `localStorage` — S3, *abierto por decisión*

Mover el token a cookie `HttpOnly` toca CORS, CSRF, el interceptor y todas las
llamadas del front: es rediseñar la sesión. Fuera de alcance el 2026-08-27. Lo
único que cambió es que el token robado caduca doce veces antes.

---

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
# Backend — 130 pruebas, deben quedar todas en verde
cd marathon-backend
mvn test                     # necesita TEST_DB_PASSWORD en el entorno

# Frontend — la compilación de producción debe pasar los presupuestos de tamaño
cd marathon-frontend
npx ng build --configuration production
```

Si `mvn test` baja de 131 pruebas o alguna falla, **algo se rompió**.

**Y con la aplicación levantada, la comprobación que faltaba.** Las 130 pruebas
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
