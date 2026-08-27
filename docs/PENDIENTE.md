# Lo que queda pendiente — MarathonSportsABD

> **Para quién es este documento.** Para retomar el trabajo **sin haber estado en
> las sesiones anteriores**. Todo lo necesario para empezar está aquí; los otros
> tres documentos de `docs/` amplían, pero no son requisito.
>
> Escrito el **2026-08-27**, sobre el commit `cd5d04f` de `main`.

---

## 1. De dónde venimos, en un párrafo

El proyecto pasó por cuatro fases: auditoría (`AUDITORIA.md`, `DEFECTOS.md`),
plan (`PLAN.md`), ejecución de 17 lotes, y rediseño del tablero e interfaz
(`DASHBOARD.md`). Se encontraron **35 defectos** y se cerraron **30**. Las
pruebas pasaron de **0 a 105**, todas en verde, y la compilación de producción
del frontend —que no había funcionado nunca— funciona.

Quedan **cinco defectos abiertos**. Ninguno corrompe datos hoy: los dos que
podían hacerlo tienen tapado su lado peligroso. Lo que queda es endurecimiento
de sesión y decisiones de negocio sin tomar.

---

## 2. Antes de tocar nada: seis cosas que van a morderte

Esto no es contexto de adorno. Cada punto costó una sesión descubrirlo.

1. **PostgreSQL escucha en el 5433, no en el 5432.** Apuntar mal no da un error
   claro, da un fallo confuso más adelante. La base es `mod_venta_inve`.

2. **Los datos de prueba terminan el 2026-08-17.** Cualquier consulta que mida
   «hoy» sale **vacía**, y eso **no es un fallo**. El tablero está construido
   sobre ventanas de 7 / 30 / 90 días precisamente por esto.

3. **`spring.jpa.hibernate.ddl-auto=validate`.** La aplicación **nunca** migra el
   esquema. Lo hacen 39 scripts `faseNN_*.sql` ordenados a mano. Un cambio de
   esquema es un script nuevo, no una anotación de entidad.

4. **La fase 34 concede privilegios COLUMNA POR COLUMNA.** Una columna nueva nace
   **sin permisos** y solo el propietario puede tocarla. `fase45` falló con
   *«permiso denegado a la tabla detalle_pedido»* hasta que se le añadieron los
   `GRANT SELECT`/`UPDATE` de esa columna. **Toda columna nueva de este proyecto
   necesita su GRANT explícito.**

5. **Hay seis pools de conexión, uno por rol** (`RoleRoutingDataSource`):
   `usr_admin_marathon`, `usr_supervisor_marathon`, `usr_bodega_marathon`,
   `usr_pedidos_marathon`, `usr_compras_marathon`, `usr_produccion_marathon`.
   Una consulta nueva debe ser ejecutable **por el rol que la va a pedir**, no
   solo por el administrador. El enrutado está desactivado en el perfil de
   pruebas.

6. **Para leer la matriz de permisos reales usa `aclexplode(c.relacl)` sobre
   `pg_class`.** Las vistas de `information_schema` devuelven vacío para
   `usr_admin_marathon` y te harán creer que no hay permisos concedidos.

**Credenciales:** viven en `.env` y `application-local.properties`, ambos en
`.gitignore`. `TEST_DB_PASSWORD` se pasa por variable de entorno. **Nunca se
commitean.** La clave de cifrado de los respaldos es distinta en cada equipo.

---

## 3. Los cinco defectos abiertos

### D-02 · Crear un pedido no comprueba ni reserva stock — S1, *mitigado*

**Dónde:** `service/PedidoService.java:131` — `crear()` valida cliente y producto
por id y **no consulta `inventario` en ningún momento**. Compruébalo: no hay una
sola referencia a inventario en el método.

**Qué se hizo ya:** el lote L1 puso bloqueo pesimista (`SELECT … FOR UPDATE`) en
los cinco puntos de escritura de `stock_actual`. La sobreventa ya **no corrompe
el inventario**: falla con un 400 claro en el despacho.

**Qué falta:** la *reserva* (`stock_reservado`), para que dos pedidos no puedan
comprometer las mismas unidades.

**Por qué no se hizo:** es un cambio de **proceso de negocio**, no de código.
Hace falta responder antes: ¿cuánto dura una reserva? ¿quién la libera? ¿qué
pasa con un pedido abandonado? Implementarlo sin esas respuestas es inventarse
la regla.

> **Si lo retomas:** no empieces por el código. Empieza por conseguir esas tres
> respuestas de quien lleve el negocio.

---

### D-13 · El modelo de permisos existe entero y no se aplica — S2, *parcial*

**Dónde:** hay 49 permisos, la tabla `rol_permiso`, un `PermisoController`, un
claim en el JWT y un array en la respuesta de login. **Ninguna decisión de
autorización los consulta.**

**La prueba:** el rol *Encargado de Producción* tiene **0 permisos de 49
asignados y funciona con normalidad**.

**Qué se hizo ya:** se retiró lo que *aparentaba* control de acceso sin serlo
(`permisoGuard` y `AuthService.hasPermiso()`, que no protegían ninguna ruta), y
se documentó en `PermisoController` que los permisos son descriptivos. El
sistema deja de mentir sobre lo que protege. La autorización real hoy es **por
rol**, y esa sí funciona.

**Por qué no se cerró:** las dos salidas del plan resultaron inviables.
*Aplicar* los permisos deja a Producción sin acceso a nada de un día para otro.
*Retirarlos* destruye la pantalla de roles, que **funciona** y es justamente el
editor de esa matriz — el paso previo a poder aplicarla.

> **Si lo retomas:** el orden correcto es (1) decidir y cargar qué puede hacer
> cada rol usando la pantalla que ya existe, (2) verificar que ningún rol queda
> en cero, (3) recién entonces encender la comprobación. Encenderla primero
> rompe el sistema.

---

### D-23 · `logout` no invalida el token — S3, *recorte deliberado*

**Dónde:** `controller/AuthController.java:40` y
`marathon-frontend/src/app/core/services/auth.service.ts:46` — el logout solo
borra `localStorage`.

**Efecto:** con JWT sin estado y sin lista de revocación, un token capturado
antes del cierre de sesión **sigue valiendo hasta expirar**.

- `app.jwt.expiration=86400000` → **24 h** (`application.properties:36`)
- `REFRESH_EXPIRATION = 604800000` → **7 días** (`config/JwtUtils.java:29`)

**Por qué no se hizo:** revocar exige una lista de denegación persistida y una
comprobación en **cada** petición. Coste alto para el beneficio en este
proyecto. El caso grave —el usuario dado de baja— ya lo cierra L7.

> **La mitigación barata, y es la mejor relación esfuerzo/beneficio que queda
> abierta:** bajar `app.jwt.expiration` de 24 h a algo del orden de 1–2 h. Es
> **una línea de configuración**, no necesita decisiones de nadie, y reduce a la
> vez la ventana de D-23 y la de D-27. Si solo se va a hacer una cosa de esta
> lista, que sea esta.

---

### D-26 · Credenciales fijas y secreto JWT versionados — S3, *parcial*

**Qué se tapó, que era el agujero real:** el secreto JWT por defecto estaba
publicado en el repositorio, lo que permitía **forjar un token de administrador
sin credenciales**. La aplicación ahora **se niega a arrancar** con ese valor
(`config/ComprobacionesDeArranque.java`).

**Qué queda:** `app.datos-demo.enabled` sigue en `true` por defecto
(`application.properties:79`, leído en `ComprobacionesDeArranque.java:41` y
`DataInitializer.java:23`). Con él encendido se crean usuarios con contraseñas
fijas; `ComprobacionesDeArranque` avisa por consola mientras lo esté.

**Por qué no se cambió:** ponerlo en `false` **rompe el primer arranque descrito
en `SETUP_COMPLETO.md`** y todos los entornos ya montados, incluidos los de los
demás integrantes del grupo. Es una decisión de despliegue, no de código.

> **Si lo retomas:** el cambio en sí es trivial (`DATOS_DEMO=false`). Lo que hay
> que resolver antes es el procedimiento de primer arranque sin datos de demo, y
> avisar a quien ya tenga el entorno montado.

---

### D-27 · El JWT se guarda en `localStorage` — S3, *recorte deliberado*

**Dónde:** `marathon-frontend/src/app/core/services/auth.service.ts:32-33`.

**Efecto:** cualquier JavaScript de la página lo lee, así que un XSS entrega la
sesión completa. Se clasificó S3 porque **hoy no hay ningún XSS conocido** en la
aplicación, pero eleva el coste de cualquier otro defecto de front.

**Por qué no se hizo:** mover el token a una cookie `HttpOnly` + `Secure` +
`SameSite` toca CORS, CSRF (hoy deshabilitado a propósito), el interceptor HTTP
y **todas** las llamadas del front. Es rediseñar la sesión, no arreglar un
fallo.

---

## 4. Lo que NO hay que hacer

Recortes deliberados de las fases anteriores. Deshacerlos sin querer rompe
trabajo que sí está bien:

- **No repares los datos históricos.** Hay 173 972 pedidos despachados sin
  movimiento de inventario. No se puede distinguir lo que escribió la
  aplicación de lo que escribieron los scripts de poblado, así que cualquier
  corrección masiva sería **inventarse hechos**. Lo correcto es un informe de
  conciliación de solo lectura y una fecha de corte decidida por negocio.
- **No toques los triggers ni los CHECK.** Son la parte más sólida del sistema y
  **las pruebas de L1, L5, L6 y L13 dependen de que se comporten igual**.
- **No metas Flyway ni Liquibase ahora.** Está anotado como deuda, pero migrar
  los 39 scripts de fase choca con los lotes que cambian esquema.
- **No añadas librerías nuevas** si con lo que ya usa el proyecto alcanza.
- **`docker-compose.yml` miente** (dice Postgres 15, usuario `postgres`, puerto
  5432; la realidad es Postgres 18, `usr_admin_marathon`, 5433). O se reescribe
  entero o se borra — no se parchea a medias, porque sería una tercera
  descripción inconsistente.

---

## 5. Reglas de trabajo del dueño del proyecto

Se aplican a cualquier cosa que se añada, y no son negociables:

1. **Ninguna cifra sin su denominador o su período.** «Ventas: 4.300» no dice
   nada.
2. **Si un dato no se puede calcular con lo que hay en la base, se DICE en
   pantalla.** No se inventa y **no se pone en cero**: cero y «no hay dato» son
   cosas distintas y se leen distinto.
3. **Nada de librerías nuevas** si con lo que ya hay alcanza.
4. **Diagnosticar y medir contra la base antes de rediseñar.** Toda cifra nueva
   se contrasta contra su consulta directa en psql antes de darla por buena.
5. **Lo que no se pueda verificar se dice como «no verificado»**, no se supone.

---

## 6. Cómo comprobar que no se rompió nada

```bash
# Backend — 105 pruebas, deben quedar todas en verde
cd marathon-backend
mvn test                     # necesita TEST_DB_PASSWORD en el entorno

# Frontend — la compilación de producción debe pasar los presupuestos de tamaño
cd marathon-frontend
npx ng build --configuration production
```

Si `mvn test` baja de 105 pruebas o alguna falla, **algo se rompió**. Antes de
esta serie de sesiones el proyecto tenía cero pruebas, así que cualquier
regresión ahora sí se ve.

---

## 7. Dónde está el detalle

| Documento | Qué contiene |
|---|---|
| `docs/AUDITORIA.md` | El recorrido completo del sistema, flujo por flujo |
| `docs/DEFECTOS.md` | Ficha de los 35 defectos, con evidencia y `fichero:línea` |
| `docs/PLAN.md` | Los 17 lotes, el estado final de cada uno y los recortes razonados |
| `docs/DASHBOARD.md` | Diagnóstico y rediseño del tablero; las 22 cifras contrastadas |
