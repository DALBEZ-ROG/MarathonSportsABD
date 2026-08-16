# Guía de replicación — `mod_venta_inve` en otro equipo

**Para quién es esto.** Para un agente (o una persona) que tenga que dejar el
proyecto Marathon Sports **funcionando igual** en un portátil o en el equipo de
un compañero de grupo. Se sigue de arriba abajo. Los pasos que **no** puede
hacer un agente están marcados así:

> ### 🖐 INTERVENCIÓN HUMANA
> Lo que hay dentro de un bloque como este necesita a una persona: escribir una
> contraseña, instalar un programa con asistente gráfico, aceptar un UAC o
> decidir algo. Un agente debe **parar aquí**, pedirlo, y no continuar hasta
> tenerlo.

---

## Antes de nada: hay dos vías, y para un compañero de grupo la buena es la B

| | **Vía A — Copiar** | **Vía B — Construir** |
|---|---|---|
| Herramienta | `exportar_bd.ps1` → `importar_bd.ps1` | `construir_desde_cero.ps1` |
| Qué viaja | Un paquete de **22 MB** | **Nada**: solo el repositorio |
| Los datos | Los **mismos**, fila a fila | Se **generan**: mismos recuentos y distribuciones, distintos valores |
| Contraseñas de los 6 usuarios | Las del equipo de origen (hashes SCRAM en `01_roles.sql`) | Cada uno pone las suyas |
| Clave de cifrado | **Hay que compartirla** por un canal aparte | Cada uno **crea la suya**; no se comparte nada |
| Tiempo | ~10 s de restauración | ~2 min de scripts |

**Para tus compañeros de grupo: vía B.** No hay que mandarles datos, ni
contraseñas, ni la clave de cifrado: se bajan el repositorio y ejecutan dos
comandos. Y para una entrega de base de datos es lo que se quiere enseñar — la
base **construida desde los scripts**, no restaurada de un volcado.

**La vía A sirve para otra cosa:** tener una réplica exacta, con los mismos
pedidos y las mismas fechas, por ejemplo en tu propio portátil.

> **Si eliges la vía B, salta a la [sección 12](#12-vía-b--construir-la-base-desde-cero).**
> Las secciones 3 y 4 son de la vía A.

---

## 0. Qué se replica y qué no

| Se replica | Cómo |
|---|---|
| Esquema: 38 tablas, 122 índices, 30 triggers, funciones | `02_base.dump` |
| Los datos: 1.271.200 filas (1.011.103 de negocio) | `02_base.dump` |
| 6 usuarios + 6 roles, con sus contraseñas | `01_roles.sql` (hashes SCRAM) |
| 2.155 privilegios de columna y los GRANT | `02_base.dump` |
| Parámetros del servidor medidos en F36/F39 | `03_configuracion.sql` |
| Los 8 campos cifrados, **como bytes cifrados** | `02_base.dump` |

| **No** se replica | Por qué | Se resuelve en |
|---|---|---|
| La **clave de cifrado** | Protegida con DPAPI de máquina: el blob es indescifrable en otro equipo. Y si viajara junto a los datos que cifra, cifrar no habría servido de nada | Paso 6 |
| El `.env` y `application-local.properties` | Están en `.gitignore`: llevan contraseñas | Paso 5 |
| El **certificado TLS** | Es de un servidor concreto | Paso 7 |
| Las **tareas programadas** | Son del Programador de tareas de Windows | Paso 8 |
| Los **respaldos** de `C:\respaldos\marathon` | `pg_basebackup` es **físico**: solo se restaura sobre la misma versión mayor y plataforma. Por eso la migración usa un volcado **lógico**, que sí es portable | — |

**Sin hacer el paso 6, todo funciona menos una cosa:** los correos, teléfonos y
direcciones de clientes y proveedores se ven **vacíos**. No es un error, es el
cifrado haciendo su trabajo.

---

## 1. Requisitos del equipo destino

> ### 🖐 INTERVENCIÓN HUMANA
> Estas cuatro instalaciones llevan asistente gráfico y hay que hacerlas a mano.
> Un agente puede comprobar si ya están (comandos abajo) pero no instalarlas.
>
> | Programa | Versión | Comprobar con |
> |---|---|---|
> | **PostgreSQL** | **18** (mínimo 17) | `psql --version` |
> | **JDK** | **17** | `java -version` |
> | **Maven** | 3.9+ | `mvn -version` |
> | **Node.js** | 20+ (solo si se quiere el frontend) | `node --version` |
>
> Al instalar PostgreSQL, **anota la contraseña de `postgres`**: hace falta en
> el paso 4.
>
> **Por qué 17 como mínimo:** los respaldos diferenciales usan `summarize_wal` y
> `pg_basebackup --incremental`, que no existen antes de la 17. Con una 15 o 16
> la base funciona pero el requisito 4 (respaldos) no se puede cumplir.

Comprobación rápida, toda junta:

```powershell
psql --version; java -version; mvn -version; node --version
```

**Trampa conocida:** en el equipo de origen el `java` del `PATH` es un **JDK 8**
y el proyecto no arranca con él. Si `java -version` dice `1.8`, hay que forzar
la 17 antes de compilar:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'   # ajustar a la ruta real
```

Y **no hay `mvnw`** en el repositorio: Maven tiene que estar instalado aparte.

---

## 2. Clonar el repositorio

```powershell
git clone https://github.com/DALBEZ-ROG/MarathonSportsABD.git
cd MarathonSportsABD
```

> **Elige una ruta corta**, tipo `C:\MarathonSportsABD`. Las herramientas de
> PostgreSQL no admiten rutas de más de 259 caracteres y fallan con
> `No such file or directory` sobre archivos que **sí existen**. Es un error
> desorientador y ya nos mordió una vez.

---

## 3. Generar el paquete **en el equipo de origen**

Esto se hace **una sola vez, en el equipo que ya tiene la base** (no en el
destino). Genera una carpeta con todo lo portable.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\migracion\exportar_bd.ps1 -Destino D:\entrega
```

Produce `D:\entrega\marathon_<fecha>\` con:

```
01_roles.sql          los 12 roles, con sus hashes SCRAM
02_base.dump          la base entera, formato custom comprimido (~22 MB)
03_configuracion.sql  los ALTER SYSTEM de las fases 36 y 39
importar_bd.ps1       el script del paso 4
GUIA_REPLICACION.md   este archivo
FALTAN_SECRETOS.txt   qué falta y por qué
```

Con `-IncluirSecretos` mete también el `.env` y
`application-local.properties`, y así el paso 5 se salta. **El paquete pasa
entonces a contener contraseñas en claro**: se entrega en mano o por un canal
privado, nunca subido a ningún sitio.

> ### 🖐 INTERVENCIÓN HUMANA
> Copiar la carpeta del paquete al equipo destino (USB, red o carpeta
> compartida). Un agente no puede mover archivos entre dos máquinas.

---

## 4. Importar la base **en el equipo destino**

```powershell
cd <carpeta del paquete>
powershell -ExecutionPolicy Bypass -File importar_bd.ps1
```

> ### 🖐 INTERVENCIÓN HUMANA
> El script pide la **contraseña del usuario `postgres`** por consola. No se
> puede pasar por parámetro a propósito: quedaría en el historial de
> PowerShell. Un agente debe pedírsela a la persona, o dejar que la escriba.
>
> Alternativa para automatizar: definir `$env:PGPASSWORD` antes de llamar.

Opciones útiles:

| Situación | Opción |
|---|---|
| PostgreSQL está en otra ruta | `-PgBin 'C:\Program Files\PostgreSQL\17\bin'` |
| La base `mod_venta_inve` ya existe y hay que rehacerla | `-Recrear` (**la borra**) |
| El paquete está en otra carpeta | `-Paquete D:\entrega\marathon_20260816_113214` |

**El orden importa y el script lo respeta:** roles → base → configuración. Los
roles son objetos del **clúster** y no viajan dentro de un `pg_dump`. Si se
restaurara la base primero, cada `GRANT` fallaría con *«el rol
rol_administrador no existe»* y la base quedaría levantada pero **sin modelo de
privilegios** — funcionando, que es lo que hace que el fallo pase
desapercibido. Por eso la verificación del final cuenta los 2.155 privilegios de
columna: si salen 0, es exactamente eso lo que pasó.

Al terminar debe imprimir esta tabla. **Los ocho valores tienen que coincidir:**

| Comprobación | Esperado |
|---|--:|
| Tablas | 38 |
| Triggers no internos | 30 |
| Triggers desactivados | 0 |
| Privilegios de columna | 2.155 |
| Concesiones a `PUBLIC` | 0 |
| Columnas cifradas (`bytea`) | 8 |
| Índices | 122 |
| **Filas de negocio** | **1.011.103** |

> ### 🖐 INTERVENCIÓN HUMANA
> `shared_buffers` y `logging_collector` **exigen reiniciar el servicio**. En
> consola de administrador (dispara UAC):
> ```powershell
> Restart-Service postgresql-x64-18
> ```

---

## 5. El `.env`

```powershell
Copy-Item .env.example .env
```

Y completar los valores. **Las contraseñas de los seis usuarios de base de datos
son las mismas que en el equipo de origen**, porque `01_roles.sql` trae sus
hashes SCRAM: hay que copiarlas del `.env` del origen.

> ### 🖐 INTERVENCIÓN HUMANA
> Un agente no puede inventarse estas contraseñas ni leerlas de ningún sitio del
> repositorio: el `.env` está en `.gitignore`. Hay que pedírselas a quien tenga
> el equipo de origen, o usar el paquete generado con `-IncluirSecretos`.
>
> Si se prefiere poner contraseñas **nuevas** en este equipo, hay que cambiarlas
> también en la base, una por una:
> ```sql
> ALTER ROLE usr_admin_marathon WITH PASSWORD '<nueva>';
> ```
> y repetir para `usr_supervisor_marathon`, `usr_bodega_marathon`,
> `usr_pedidos_marathon`, `usr_compras_marathon` y `usr_produccion_marathon`.

Ojo con un detalle que despista: el perfil activo es `local`, así que
**`application-local.properties` manda sobre el `.env`**. Es fácil cambiar el
`.env`, no ver ningún efecto y buscar el problema donde no está.

---

## 6. La clave de cifrado

Sin esto la aplicación arranca igual (con un `WARN`) y los datos personales
salen **vacíos**.

> ### 🖐 INTERVENCIÓN HUMANA
> **La clave tiene que llegar al equipo destino por un canal distinto del
> paquete.** Si viajara con los datos que cifra, el cifrado no protegería nada.
> Un agente no puede sacarla de ningún sitio automáticamente.
>
> **En el equipo de origen**, una persona la exporta:
> ```powershell
> powershell -File scripts\cifrado\gestionar_clave.ps1 -Accion Escrow -Destino D:\custodia\clave.txt
> ```
> El script rechaza como destino el repositorio, la carpeta de respaldos y
> cualquier ruta dentro de OneDrive. Ese archivo contiene la clave **en claro**:
> se traslada en mano y se borra después.

**En el equipo destino**, con ese archivo a mano:

```powershell
powershell -File scripts\cifrado\gestionar_clave.ps1 -Accion Importar -Destino D:\custodia\clave.txt
```

O sin archivo, pegándola por consola (no se muestra en pantalla):

```powershell
powershell -File scripts\cifrado\gestionar_clave.ps1 -Accion Importar
```

**Comprobar la huella.** Debe imprimir la misma que el equipo de origen:

```
Huella : 472b43907ba05386
```

La huella es un SHA-256 truncado: permite verificar que dos equipos usan la
misma clave **sin llegar a mostrarla nunca**. Si no coincide, la clave es otra y
los datos personales seguirán saliendo vacíos.

**No usar `-Accion Crear` en un equipo donde ya se restauró la base.** `Crear`
genera una clave **nueva** al azar, y los `bytea` restaurados quedarían
ilegibles para siempre. `Importar` se niega a sobrescribir una clave distinta
justamente por eso.

Comprobar el estado en cualquier momento:

```powershell
powershell -File scripts\cifrado\gestionar_clave.ps1 -Accion Estado
```

---

## 7. TLS

```powershell
powershell -ExecutionPolicy Bypass -File scripts\cifrado\configurar_tls.ps1
```

Genera el certificado de **este** servidor y activa `ssl = on`. El cliente usa
`sslmode=verify-full`, que además de cifrar **autentica al servidor**, así que
el certificado no puede copiarse del equipo de origen: hay que generarlo aquí.

> **No pongas `ssl = on` sin haber generado antes el certificado.** El servidor
> no arranca. Por eso `03_configuracion.sql` lleva esa línea comentada.

Necesita `openssl`, que viene con Git para Windows
(`C:\Program Files\Git\usr\bin\openssl.exe`).

---

## 8. Respaldos

> ### 🖐 INTERVENCIÓN HUMANA
> Registrar las tareas exige **consola de administrador** (dispara UAC):
> ```powershell
> powershell -ExecutionPolicy Bypass -File scripts\backup\registrar_tareas.ps1
> ```

Deja cuatro tareas en `\MarathonSports\`, corriendo como `SYSTEM`:

| Tarea | Cuándo |
|---|---|
| `Marathon_Respaldo_Full` | domingos 23:00 |
| `Marathon_Respaldo_Diferencial` | lunes a sábado 22:00 |
| `Marathon_Respaldo_Aplicacion` | domingos 23:30 |
| `Marathon_Verificar_Respaldos` | diario 08:00 |

**Trampa:** sin elevación esas tareas **no se ven**. `Get-ScheduledTask -TaskPath
'\MarathonSports\'` responde «no se encontraron objetos» y `schtasks /query` las
omite, como si no existieran. No concluir que faltan sin comprobarlo elevado.

Para la copia fuera del equipo (regla 3-2-1) hace falta un USB con **etiqueta de
volumen `MARATHON_BK`**. Se localiza por etiqueta y no por letra porque Windows
no garantiza que el mismo pendrive reciba siempre la misma. Sin él, el respaldo
local se completa igual y sale con **código 10**, que distingue «primario sí,
secundario no» de un fallo real.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\backup\verificar_respaldos.ps1
```

---

## 9. Arrancar

```powershell
powershell -ExecutionPolicy Bypass -File scripts\cifrado\iniciar_backend.ps1
```

**Tiene que ser este script, no `mvn spring-boot:run` a secas.** Sin la clave en
el entorno del proceso, el backend arranca igualmente —solo deja un `WARN`— y
los correos y teléfonos salen vacíos. Es un fallo silencioso, y por eso existe
este arranque.

Frontend, si se quiere:

```powershell
cd marathon-frontend
npm install
npm start
```

Usuario de demostración: `admin@marathon.com` (la contraseña está en
`DEMO_CHECKLIST.md`).

---

## 10. Verificación final

Con esto se comprueba que el equipo destino quedó igual que el de origen.

```powershell
psql -U postgres -d mod_venta_inve -f marathon-backend\sql\fase38_1_cierre_verificacion.sql
psql -U postgres -d mod_venta_inve -f marathon-backend\sql\fase34_pruebas_roles.sql
psql -U postgres -d mod_venta_inve -f marathon-backend\sql\fase40_pruebas_auditoria.sql
psql -U postgres -d mod_venta_inve -f marathon-backend\sql\fase41_pruebas_cifrado.sql
```

Resultados esperados:

| Arnés | Esperado |
|---|---|
| `fase38_1` | 6 invariantes con 0 discrepancias · 238 comprobaciones con 0 violaciones |
| `fase34` | **61 / 61** pruebas de privilegios |
| `fase40` | **29 / 29** pruebas de auditoría |
| `fase41` | **51 / 51** pruebas de cifrado |

Los cuatro son seguros de ejecutar: `fase34`, `fase40` y `fase41` abren `BEGIN`
y terminan en `ROLLBACK`. `fase38_1` no lleva `ROLLBACK` a propósito, porque su
etapa 2 es una reparación que se omite sola sobre una base sana.

Las pruebas de cifrado (`fase41`) **solo pasan si el paso 6 se hizo bien**. Son
la forma más directa de saber si la clave quedó bien instalada.

---

## 11. Cuando algo falla

| Síntoma | Causa | Solución |
|---|---|---|
| `No such file or directory` sobre un archivo que existe | Ruta de más de 259 caracteres: las herramientas de PostgreSQL no son *long-path aware* | Mover el paquete a una ruta corta |
| `el rol rol_administrador no existe` al restaurar | Se restauró la base antes que los roles | Rehacer con `-Recrear` |
| «Privilegios de columna: 0» en la verificación | Lo mismo que la fila anterior, pero silencioso | Rehacer con `-Recrear` |
| Correos y teléfonos **vacíos** en la aplicación | Falta la clave, o se arrancó sin `iniciar_backend.ps1` | Pasos 6 y 9 |
| La huella de la clave no coincide | Se usó `-Accion Crear` en vez de `Importar`, o la clave se copió incompleta | Reponer la clave correcta de la custodia |
| El servidor no arranca tras tocar la configuración | `ssl = on` sin certificado | Paso 7, o comentar `ssl` en `postgresql.auto.conf` |
| `autentificación password falló para el usuario <tu_usuario_windows>` | En un script propio, un parámetro llamado `$Args`: es **variable automática** de PowerShell, no se enlaza, y los argumentos se pierden en silencio | Renombrar el parámetro |
| `ALTER SYSTEM no puede ejecutarse dentro de un bloque de transacción` | Se lanzó por una herramienta que envuelve todo en una transacción (el MCP) | Usar `psql -f` |
| Las tareas de respaldo «no existen» | Consola sin elevar | Comprobar en consola de administrador |
| El backend compila con errores raros | El `java` del `PATH` es un JDK 8 | Forzar `JAVA_HOME` a la 17 |

---

## 12. Vía B — Construir la base desde cero

Esta es la vía para los compañeros de grupo. No necesita el paquete, ni la clave
de cifrado de nadie, ni contraseñas ajenas: **solo el repositorio clonado**
(secciones 1 y 2) y PostgreSQL instalado.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\migracion\construir_desde_cero.ps1 -Etapa Esquema
```

Crea la base y aplica las fases 0 a 29: **37 tablas**. Tarda un par de segundos.

> ### 🖐 INTERVENCIÓN HUMANA
> **Aquí el proceso para a propósito y hay que arrancar el backend una vez.**
>
> Los roles de aplicación, los permisos y los usuarios de demostración los crea
> el `DataInitializer` de Spring, **no un script SQL**, y el seed de la etapa 2
> depende de que existan. No es un olvido de la automatización: esa información
> vive en el código Java.
>
> **Este arranque, y solo este, se hace como `postgres`.** En este punto no
> existe todavía **ni un solo `GRANT`** —los otorga la fase 34, en la etapa 2—,
> así que `usr_admin_marathon` no tiene ningún privilegio sobre las tablas y el
> `DataInitializer` fallaría. Es la única vez en todo el proyecto que la
> aplicación usa el superusuario; a partir de la etapa 2 manda el modelo de
> roles.
>
> 1. Crear el `.env` (copiar de `.env.example`). Para este arranque bastan las
>    variables `DB_*` y `JWT_SECRET`.
> 2. Arrancar:
>    ```powershell
>    cd marathon-backend
>    mvn -q -DskipTests spring-boot:run "-Dspring-boot.run.arguments=--spring.datasource.username=postgres --spring.datasource.password=<clave> --app.datasource.roles.enabled=false"
>    ```
>    `--app.datasource.roles.enabled=false` porque los otros cinco pools tampoco
>    tienen privilegios todavía y el arranque fallaría al abrirlos.
> 3. Esperar a **`Datos iniciales cargados correctamente`** y parar con `Ctrl+C`.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\migracion\construir_desde_cero.ps1 -Etapa Datos
```

Y esto hace **todo lo demás**, en orden, sin parar:

| | Qué |
|---|---|
| seed + F31 + F32 | Datos de demostración y correcciones |
| **F33** | Índices sobre las consultas críticas |
| **F34** | 6 roles, 6 usuarios y los privilegios por columna |
| **F35–F37** | `summarize_wal`, auditoría nativa, privilegios del pool por rol |
| **F38 + F38.1** | Poblado masivo a 1.000.000 de filas y su verificación |
| **F39** | Volumen en compras y manufactura |
| **F40** | `auditoria_cambios` campo a campo |
| **F41** | Baja de 4 índices, **y el cifrado con una clave nueva de este equipo** |
| **F43** | +65.000 pedidos: el millón en tablas de negocio |

Tarda un par de minutos, casi todo en el poblado. Al final imprime una tabla de
verificación con 9 comprobaciones.

**Si la etapa 2 se lanza sin haber arrancado el backend**, no falla a mitad con
errores de clave foránea: lo detecta antes de escribir nada y lo dice —
*«La tabla 'rol' está vacía: el backend no ha arrancado todavía»*.

### Lo que esta vía resuelve sola

- **La clave de cifrado.** El script ejecuta `gestionar_clave.ps1 -Accion Crear`
  y genera una clave **nueva, de ese equipo**. Es correcto: esos datos se acaban
  de generar ahí. Compartir la clave solo hace falta con la vía A, donde se
  restauran bytes cifrados por otro.
- **Las contraseñas.** `fase34_seguridad_roles.sql` crea los seis usuarios con
  contraseña **aleatoria**, a propósito, para que no vivan en el repositorio.

### Lo que sigue necesitando una persona

> ### 🖐 INTERVENCIÓN HUMANA
> Al terminar, el script las lista con el comando exacto:
>
> 1. **Las contraseñas de los seis usuarios.** Elegir una para cada uno,
>    fijarlas con `ALTER ROLE usr_... WITH PASSWORD '<clave>'` y escribir esas
>    mismas seis en el `.env`. Sin esto el backend no abre los pools por rol.
> 2. El certificado TLS (sección 7) y las tareas de respaldo (sección 8).

### Si algo se quiere sin cifrado

```powershell
... construir_desde_cero.ps1 -Etapa Datos -SinCifrado
```

La base queda completa y funcional, pero con los datos personales en claro y sin
las 8 columnas `bytea`. Útil para comparar el antes y el después del requisito
de cifrado.

---

## Resumen para un agente

**Vía B — construir (compañeros de grupo):**

```
1. Comprobar PostgreSQL 18 / JDK 17 / Maven          [🖐 instalar si falta]
2. git clone en una ruta CORTA
3. construir_desde_cero.ps1 -Etapa Esquema           [🖐 contraseña postgres]
4. .env con DB_* y JWT_SECRET, y arrancar el backend
   una vez para que el DataInitializer cree los roles [🖐]
5. construir_desde_cero.ps1 -Etapa Datos
   -> genera el millon de filas y crea SU PROPIA clave
6. ALTER ROLE x6 y esas contrasenas al .env          [🖐 elegirlas]
7. configurar_tls.ps1
8. registrar_tareas.ps1                              [🖐 UAC]
9. iniciar_backend.ps1   (NUNCA mvn a secas)
10. los 4 arneses: 61/61, 29/29, 51/51, y 0 discrepancias
```

**Vía A — copiar (réplica exacta):**

```
1-2. igual que arriba
3. exportar_bd.ps1 en el origen                      [🖐 copiar el paquete]
4. importar_bd.ps1 en el destino                     [🖐 contraseña postgres]
   -> comprobar los 8 valores, y reiniciar el servicio [🖐 UAC]
5. .env                                              [🖐 contraseñas del origen]
6. gestionar_clave.ps1 -Accion Importar              [🖐 traer la clave aparte]
   -> comprobar la huella 472b43907ba05386
7-10. igual que arriba
```

**Dónde tiene que parar un agente.** En la vía B son cuatro: instalar los
programas, la contraseña de `postgres`, arrancar el backend una vez, y elegir
las contraseñas de los seis usuarios. La vía A añade dos más: mover el paquete
entre equipos y traer la clave de cifrado por un canal aparte. Todo lo demás se
automatiza.
