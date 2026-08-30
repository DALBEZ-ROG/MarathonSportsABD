# Estrategia de Respaldo — Marathon Sports

Fase 35. Respaldo completo semanal + diferencial diario de `mod_venta_inve`, y la
estrategia equivalente para la capa de aplicación.

---

## 1. Regla de negocio

| Cuándo | Qué | cron equivalente |
|---|---|---|
| Domingo 23:00 | Respaldo **COMPLETO** | `0 23 * * 0` |
| Lunes a sábado 22:00 | Respaldo **DIFERENCIAL** | `0 22 * * 1-6` |
| Domingo 23:30 | Respaldo de la **aplicación** | `30 23 * * 0` |
| Todos los días 08:00 | **Verificación** de que los jobs corrieron | `0 8 * * *` |

**RPO 24 h** (se puede perder como máximo un día de trabajo) · **RTO 2 h**
(el sistema debe estar operativo dentro de las 2 horas siguientes al desastre).

Ventaja del esquema diferencial frente al incremental: para restaurar hacen falta
**exactamente 2 piezas**, el completo y el último diferencial. No hay cadena que
se rompa si falta un eslabón intermedio.

---

## 2. Herramienta elegida: `pg_basebackup` incremental nativo

### 2.1 Por qué no pg_dump, pgBackRest ni Barman

El documento de trabajo del equipo proponía pgBackRest o Barman. Hay un problema
concreto con esa vía: **ninguno de los dos corre en Windows**, y este servidor es
una instalación nativa de Windows (`C:\Program Files\PostgreSQL\18\data`,
servicio `postgresql-x64-18`). Adoptarlos exigiría migrar el servidor a Linux.

`pg_dump` tampoco sirve: es un volcado lógico y **no tiene ningún concepto de
diferencial**. Cada ejecución vuelca la base completa.

### 2.2 La solución: PostgreSQL 17+ trae respaldo incremental nativo

El servidor es **PostgreSQL 18.3**, e incluye `pg_basebackup --incremental`,
`pg_combinebackup` y `pg_walsummary`. Con esto no hace falta ninguna herramienta
externa.

**El detalle que convierte un incremental en un diferencial** es cuál manifiesto
se le pasa a `--incremental`:

- Apuntando al manifiesto del respaldo **del día anterior** → cadena incremental.
  Para restaurar hacen falta todos los eslabones.
- Apuntando **siempre al manifiesto del COMPLETO del domingo** → cada respaldo
  contiene todo lo cambiado desde el domingo. Cada día reemplaza al anterior.
  **Esto es un diferencial**, y es lo que hace `backup_diferencial.ps1`.

### 2.3 Requisito de configuración

```sql
ALTER SYSTEM SET summarize_wal = 'on';
SELECT pg_reload_conf();
```

`summarize_wal` arranca el proceso que registra qué bloques cambian, que es lo que
permite el incremental. Es de contexto **`sighup`**: se activa con una recarga de
configuración, **sin reiniciar el servidor**. Ya está aplicado y verificado.

No hace falta `archive_mode = on` ni cambiar `wal_level` (está en `replica`, que
es suficiente). Eso evita el único paso que habría exigido un reinicio.

`wal_summary_keep_time` está en 14.400 min (10 días), holgado para un ciclo
semanal.

### 2.4 Formato plano, no tar

Los respaldos se guardan en formato plano (`-Fp`) porque `pg_combinebackup`, que
es lo que fusiona completo + diferencial al restaurar, opera sobre directorios de
datos. Un respaldo en tar habría que desempacarlo primero, sumando tiempo al RTO
sin ningún beneficio.

---

## 3. Scripts

En `scripts/backup/`:

| Script | Función |
|---|---|
| `config.ps1` | Configuración común: rutas, retención, credencial desde `.env`. No se ejecuta solo. |
| `backup_full.ps1` | Completo semanal. Verifica con `pg_verifybackup` y aplica retención. |
| `backup_diferencial.ps1` | Diferencial diario contra el manifiesto del completo. |
| `restaurar.ps1` | Fusiona completo + diferencial. Modo `Prueba` (no destructivo) y `Produccion`. |
| `backup_aplicacion.ps1` | Capa web: punto Git, configuración cifrada, archivos subidos. |
| `verificar_respaldos.ps1` | Detecta jobs que dejaron de correr. |
| `registrar_tareas.ps1` | Registra las 4 tareas en el Programador de Windows. Requiere admin. |

### Puesta en marcha

```powershell
# 1. Registrar las tareas (consola COMO ADMINISTRADOR)
cd scripts\backup
.\registrar_tareas.ps1

# 2. Probar sin esperar al horario
Start-ScheduledTask -TaskName 'Marathon_Respaldo_Full' -TaskPath '\MarathonSports\'

# 3. Simulacro de restauración
.\restaurar.ps1 -Modo Prueba
```

---

## 4. Resultados medidos

Ejecuciones reales sobre la base de producción, 13/08/2026.

### 4.1 Respaldo completo

| Métrica | Valor |
|---|---|
| Duración | **7 s** |
| Tamaño | **84,12 MB** |
| Verificación `pg_verifybackup` | `backup successfully verified` |

### 4.2 Respaldo diferencial

| Métrica | Valor |
|---|---|
| Duración | **4 s** |
| Tamaño | **22,38 MB** |
| Ahorro frente al completo | **73,4 %** |

### 4.3 Restauración y RTO

Prueba no destructiva: fusión con `pg_combinebackup`, arranque de una instancia
temporal en el puerto 5433 y verificación del contenido. La base de producción no
se tocó en ningún momento.

| Etapa | Tiempo acumulado |
|---|---|
| Fusión completo + diferencial | 15 s |
| Instancia arriba y consultable | **16 s** |

| | |
|---|---|
| **RTO medido** | **16 s (0,27 min)** |
| **RTO objetivo** | 120 min |
| **Margen** | 119,7 min |

Contenido verificado en la instancia restaurada:

```
usuarios=6 clientes=40 productos=108 pedidos=25 detalles=68 inventario=267 logs=85
marca_diferencial = 1      <- el cambio hecho DESPUÉS del completo
roles_restaurados = 6      <- los roles de la F34
indices_f33 = 4            <- los índices de la F33
```

La línea que importa es `marca_diferencial = 1`. Antes del diferencial se insertó
una marca en `log_accion` que **no existía** cuando se tomó el completo. Aparece
en la base restaurada, lo que demuestra que el diferencial captura los cambios y
que la fusión los aplica. Sin esa comprobación, un RTO de 16 s no probaría nada:
podría estar restaurando solo el completo.

> El RTO real de un desastre será mayor que 16 s: hay que sumar el tiempo de
> diagnóstico, la decisión de restaurar, y el arranque del backend y del frontend.
> Con 119,7 min de margen sobre el objetivo, hay espacio de sobra. Y el tiempo
> escalará con el tamaño de la base: a 276 MB tardó 7 s, de modo que el margen
> aguanta un crecimiento de dos órdenes de magnitud.

### 4.4 Las tareas programadas, ejecutadas de verdad

Registrar las tareas no prueba que vayan a funcionar. Corren como **SYSTEM**, no
como el usuario que las registró, y la credencial de base de datos vive en el
`.env` del proyecto, que está **dentro de OneDrive**. Si SYSTEM no pudiera leer
ese archivo, los respaldos fallarían en silencio a las 22:00 y nadie se enteraría
hasta el día que hicieran falta. Por eso se lanzaron a mano las dos tareas, ya
registradas, y se comprobó su resultado:

| Tarea | Código de salida | Qué demuestra |
|---|---|---|
| `Marathon_Verificar_Respaldos` | `0` | SYSTEM lee el `.env` y se conecta por `psql` |
| `Marathon_Respaldo_Diferencial` | `0` | Respaldo real de 22,92 MB en 6 s |

```
22:35:52 [OK  ] DIFERENCIAL completado en 6 s. Tamano: 22.92 MB (el FULL ocupa 84.12 MB: 72.8% menos)
22:35:52 [INFO] Superado por el diferencial de hoy, eliminado: diff_20260813_213848_...
```

La segunda línea confirma la retención del esquema diferencial: el nuevo respaldo
**reemplaza** al anterior en lugar de acumularse, así que en disco queda siempre
un único diferencial por completo, que es lo que hace que restaurar necesite solo
dos archivos.

Cadena completa verificada de punta a punta:
disparador → SYSTEM → credencial → `pg_basebackup --incremental` → verificación →
retención.

| Tarea | Programación |
|---|---|
| `Marathon_Respaldo_Full` | Domingo 23:00 |
| `Marathon_Respaldo_Diferencial` | Lunes a sábado 22:00 |
| `Marathon_Respaldo_Aplicacion` | Domingo 23:30 |
| `Marathon_Verificar_Respaldos` | Todos los días 08:00 |

---

## 5. Retención y almacenamiento

| Parámetro | Valor | Dónde se cambia |
|---|---|---|
| Ubicación | `C:\respaldos\marathon` | `config.ps1` → `$BackupRoot` |
| Completos conservados | **2 semanas** (era 4, ver §5.1) | `$SemanasRetencion` |
| Diferenciales por completo | 1 (el más reciente) | automático |
| Umbral mínimo de disco libre | 5 GB | `$MinEspacioLibreGB` |

La ubicación está **deliberadamente fuera de la carpeta del proyecto**, que vive
dentro de OneDrive: sincronizar decenas de GB de respaldos a la nube en cada
ejecución sería un problema, no una ventaja.

### 5.1 Por qué la retención bajó de 4 a 2 (16/08/2026)

La F43 llevó la base de 228 a 250 MB, y con ella cada completo de 303 a **326
MB**. Con la retención en 4, los respaldos ocupaban ~1,6 GB sobre **9,28 GB
libres**, y `verificar_respaldos.ps1` ya avisaba de que el margen se acercaba al
umbral de aborto de 5 GB. Con 2 se recuperan unos 650 MB.

**Lo que se pierde:** la ventana de recuperación baja de 4 semanas a 2. Para una
base cuyo RPO es de 24 h no es una degradación real —lo que protege ante un
error humano descubierto tarde es la profundidad del histórico, y dos semanas
siguen cubriendo el ciclo de trabajo—, pero conviene saberlo.

> **Mientras el USB `MARATHON_BK` siga sin conectarse, estas copias son las
> únicas que existen, y ahora son dos en vez de cuatro.** La regla 3-2-1 no está
> cumplida: si se pierde el disco `C:`, se pierde todo. Bajar la retención es
> aceptable *porque* la réplica externa está prevista; sin ella, es el único
> ejemplar de cada respaldo.

La retención se aplica **al ejecutar el siguiente completo**, no al guardar
`config.ps1`.

Al eliminar un completo por retención se eliminan también sus diferenciales: un
diferencial sin su base no sirve para nada.

Si el disco baja del umbral, el respaldo **se aborta antes de empezar** en lugar
de llenar el disco y tumbar el servidor de base de datos.

### Cifrado

Los respaldos de base de datos **no se cifran**: quedan en un volumen local del
mismo equipo, y cifrarlos añadiría un punto de fallo (una clave que perder) sin
mitigar ninguna amenaza que no esté ya cubierta por los permisos del sistema de
archivos. La decisión cambia en el momento en que se implemente la copia externa
de la sección 7.

Los archivos de **configuración de la aplicación sí se cifran**, porque contienen
la contraseña de la base de datos y el secreto de firma JWT. Ver sección 6.

---

## 6. Respaldo de la capa de aplicación

La base de datos no es lo único que hay que poder recuperar. Con solo el respaldo
de la base, tras perder el servidor queda una base sin sistema que la consulte.

`backup_aplicacion.ps1` trata cada tipo de activo según su naturaleza:

### 6.1 Código fuente → punto de recuperación, no copia

Ya está en Git. Lo que el script guarda **no es el código** sino el identificador
exacto de la versión: commit, rama, etiqueta y URL del remoto, en
`punto_recuperacion_git.json`. Copiar el código a un ZIP diario sería redundante y
además peor, porque perdería el historial.

Lo que falta en Git es la trazabilidad de **qué versión estaba en producción**, y
eso es lo que se registra.

El script **avisa si hay cambios sin confirmar**: un respaldo cuyo código no está
commiteado no es reproducible, porque al restaurar ese trabajo no estará en ningún
sitio.

> **Punto de recuperación recomendado:** etiquetar cada entrega con
> `git tag -a v1.0 -m "Entrega"` para que el punto de recuperación tenga un nombre
> legible y no solo un hash.

### 6.2 Configuración y secretos → copiados y cifrados

`.env`, `application-local.properties`, los `environment.ts` del frontend y
`docker-compose.yml` **no están en Git** (están en `.gitignore`, y con razón). Si
se pierde el disco, se pierden. Se copian también `postgresql.conf`, `pg_hba.conf`,
`pg_ident.conf` y `postgresql.auto.conf` del servidor.

Se empaquetan y se cifran con **DPAPI en ámbito de máquina**: solo se pueden
descifrar en este equipo, lo que es adecuado para un respaldo local y no obliga a
custodiar otra clave. Junto al archivo se deja `COMO_DESCIFRAR.txt`, porque un
respaldo que nadie sabe abrir no sirve de nada en una emergencia.

> **Limitación anotada a propósito:** si se pierde el equipo, ese archivo es
> indescifrable. Para la copia externa de la sección 7 hay que usar una clave
> gestionada aparte.

### 6.3 Archivos subidos por usuarios → espejo con Robocopy

Hoy **no hay ninguno**: el sistema genera los PDF al vuelo y no los persiste. El
script lo comprueba, lo deja anotado en el log y avisa de que si más adelante se
añaden imágenes de producto o adjuntos, hay que agregar su carpeta a la lista
`$carpetasSubidas`.

### 6.4 RPO y RTO de la capa web

| | Base de datos | Capa de aplicación |
|---|---|---|
| **RPO** | 24 h | **Código: ~0** (cada push a Git). **Configuración: 7 días** |
| **RTO** | 2 h | **1 h** (clonar, descifrar configuración, `mvn package`, `npm build`) |

El RPO de la configuración es de 7 días y no de 24 h porque cambia muy poco: entre
entregas puede pasar meses sin tocarse. Si se modifica un `.properties`, lo
correcto es ejecutar `backup_aplicacion.ps1` a mano en ese momento en lugar de
esperar al domingo.

---

## 6.5 Custodia de la clave de cifrado (F41)

Desde la F41 los datos de contacto de `cliente` y `proveedor` están cifrados en
la base. Eso cambia lo que significa «tener un respaldo».

> **Un respaldo sin la clave no es un respaldo.** `pg_basebackup` copia el dato
> **ya cifrado**. Restaurar sin la clave devuelve `bytea` ilegibles: la base
> arranca, la aplicación funciona, y los correos, teléfonos y direcciones salen
> vacíos. **Sin clave, esos datos están perdidos de forma definitiva.**

| | |
|---|---|
| Copia operativa | `C:\ProgramData\MarathonSports\crypto\clave.dpapi`, blob DPAPI de máquina |
| **Deliberadamente fuera de** | `C:\respaldos\marathon` |
| Copia de custodia | **Manual, fuera del equipo** — ver abajo |
| Huella de verificación | `472b43907ba05386` (SHA-256 truncada; permite comprobar que dos entornos usan la misma clave sin mostrarla) |

**Por qué la clave no está en la carpeta de respaldos.** Si viajara dentro del
mismo respaldo que los datos que cifra, cifrar no habría servido de nada: quien
se lleve el respaldo se lleva las dos mitades. La separación no es un detalle
organizativo, es lo que hace que el cifrado signifique algo frente al robo de un
respaldo.

**Y una limitación que agrava lo anterior:** DPAPI en ámbito `LocalMachine` ata
el blob a **este equipo**. Es la misma limitación ya anotada para
`configuracion_secretos.zip.dpapi` en §5, pero aquí la consecuencia es mayor: el
ZIP de configuración se puede reconstruir a mano, y los datos personales
cifrados, no. **Perder el equipo sin copia de custodia = perder los datos**,
aunque los respaldos estén íntegros y verificados.

### Estado: copia de custodia GENERADA, pendiente de trasladar

```
C:\Users\dbeni\custodia_marathon\clave_cifrado_marathon.txt
```

| Comprobación | Resultado |
|---|---|
| Huella anotada en el archivo | `472b43907ba05386` |
| ¿Es la misma clave que la operativa? | ✅ |
| **¿Descifra de verdad?** | ✅ **5.005 de 5.005 correos de cliente y 6 de 6 proveedores**, usando *solo* la clave del archivo |
| ¿Está dentro de OneDrive? | ❌ (ruta local no sincronizada) |
| Permisos | Solo `dbeni` (R,W) y Administradores (R) |

Que el archivo exista no prueba nada: podría contener una clave equivocada y
nadie lo sabría hasta necesitarla. Por eso la verificación **usa** la clave
custodiada para descifrar, ignorando el almacén DPAPI.

> **Sigue siendo un paso intermedio.** Mientras el archivo esté en `C:`, la clave
> y los datos que cifra viven en el mismo equipo, que es justo lo que la custodia
> pretende evitar. **Quedan dos acciones del usuario:** copiar el contenido a un
> gestor de contraseñas o el archivo a una unidad extraíble —**que no sea la de
> los respaldos**—, y después **borrarlo del disco**.

### Tres destinos que el script rechaza, y por qué

`gestionar_clave.ps1 -Accion Escrow` no acepta:

| Destino | Motivo |
|---|---|
| Dentro del repositorio | Acabaría en un commit |
| Dentro de `C:\respaldos\marathon` | La clave viajaría con los datos que cifra |
| **Dentro de OneDrive** | Se sincronizaría a la nube y quedaría en el historial de versiones **aunque después se borre el archivo** |

El tercero se añadió en la F42 al comprobar que en este equipo **el Escritorio y
Documentos están dentro de OneDrive**: «guardarla en el escritorio» habría subido
la clave en claro a un servicio de terceros y replicado en todos los dispositivos
vinculados. Es una fuga silenciosa y permanente, y la más fácil de cometer.

```powershell
powershell -File scripts\cifrado\gestionar_clave.ps1 -Accion Escrow -Destino <ruta fuera del equipo>
```

El script rechaza como destino el repositorio y `C:\respaldos\marathon`. El
archivo generado contiene la clave **en claro** y debe trasladarse a un soporte
fuera del equipo y borrarse de disco después.

> **La copia de custodia NO va en el USB de respaldos.** Es el mismo principio
> que impide guardarla en `C:\respaldos\marathon`, y con el USB es aún más
> evidente: si la clave viaja en el mismo pendrive que los datos que cifra,
> quien lo robe se lleva las dos mitades y el cifrado no ha servido de nada.
> Destino correcto: un gestor de contraseñas, o una copia física guardada
> **aparte del USB** —caja fuerte, sobre sellado en otro sitio—.

Verificación sin revelar la clave: `gestionar_clave.ps1 -Accion Estado` imprime
su **huella** (`472b43907ba05386`). Sirve para comprobar que la copia custodiada
es la buena antes de necesitarla de verdad.

El procedimiento completo de reposición, y el ensayo de qué ocurre exactamente
si la clave falta, están en `CIFRADO.md` §4.

---

## 7. La regla 3-2-1 — cerrada en la F42

Hasta la F41 este esquema **no la cumplía**: las tres copias vivían en el mismo
disco que la base, así que protegía de un borrado accidental y de una corrupción
lógica, pero no de la pérdida del equipo. La F42 añadió el segundo destino.

| Requisito | Antes | Ahora |
|---|---|---|
| **3 copias** | Parcial: original + completo + diferencial, mismo disco | ✅ + réplica en disco externo |
| **2 medios distintos** | ❌ todo en `C:` | ✅ disco interno + **disco externo USB** |
| **1 copia fuera del sitio** | ❌ | ✅ el USB sale del equipo |

### Cómo se configura el destino secundario

En `config.ps1`. Se busca en este orden:

| Variable | Uso |
|---|---|
| `$SecundarioRuta` | Ruta explícita (recurso de red, unidad ya montada, o un simulacro) |
| `$SecundarioLetra` | Letra fija, p. ej. `'E:'` |
| `$SecundarioEtiqueta` | **Etiqueta de volumen — lo recomendado para un USB** |

**Por qué la etiqueta y no la letra.** Windows no garantiza que el mismo pendrive
reciba siempre la misma letra: depende de qué otros dispositivos estén
conectados. Un respaldo configurado contra `E:` empieza a copiar en el disco
equivocado el día que `E:` es otra cosa. Buscar por etiqueta (`MARATHON_BK`) es
lo único estable.

También se puede sobrescribir con la variable de entorno
`MARATHON_BK_SECUNDARIO`, útil para apuntar a un NAS o para hacer un simulacro
sin desconfigurar el USB de verdad.

### Qué pasa si el USB no está conectado

Es la situación normal, no una avería: un medio extraíble está desconectado la
mayor parte del tiempo. **El respaldo primario no puede depender de eso.**

```
[OK   ] Verificacion correcta: checksums y manifiesto coinciden.
[OK   ] FULL completado en 11 s. Tamano: 303.35 MB
[AVISO] Destino secundario: no hay ningun volumen con etiqueta 'MARATHON_BK' conectado.
[AVISO] COPIA SECUNDARIA OMITIDA (full). El respaldo primario esta completo y verificado.
```

**Códigos de salida:**

| Código | Significado |
|---|---|
| `0` | Respaldo local **y** réplica externa correctos |
| `10` | Respaldo local correcto; **réplica externa no hecha** (regla 3-2-1 incompleta) |
| `1`–`5` | Fallo del respaldo |

El 10 existe para que un supervisor pueda avisar sin tratarlo como un fallo de
respaldo, que es lo que sería un `exit 1`. **Nunca se falla el respaldo entero
por un USB desenchufado.**

### Protección contra copias a medias

La réplica se escribe en `<nombre>.parcial` y solo al terminar se renombra. Si el
USB se retira a media escritura —lo típico con medios extraíbles— lo que queda es
una carpeta `.parcial` que nadie confundirá con un respaldo bueno.
`verificar_respaldos.ps1` las detecta y avisa.

### Retención y espacio

Retención propia del secundario (`$SecundarioSemanas = 4`), declarada aparte de
la del primario porque un USB suele ser más pequeño que el disco interno y
conviene poder recortarla sin tocar la política principal. Antes de copiar se
comprueba el espacio libre (`$SecundarioMinLibreGB = 2`) y, si no llega, no se
copia: mejor no tener réplica que tener una a medias que parezca válida.

En `C:` quedan **9,28 GB libres** (16/08/2026). Con la base en 250 MB tras la
F43, la retención del primario se bajó a 2 semanas por ese margen (§5.1); la del
secundario sigue en 4 porque el USB aún no está conectado y no consume nada.
`verificar_respaldos.ps1` avisa cuando el margen se acerca al umbral.

### CIFRA EL USB CON BITLOCKER TO GO

No es una recomendación de manual, es lo que hace falta aquí:

> Un disco externo de respaldos es el medio **más fácil de robar** de toda la
> instalación. Sale del edificio en un bolsillo y no deja rastro. Todo lo que se
> ha construido en las fases 34 a 41 —seis roles, 2.155 privilegios de columna,
> auditoría append-only— protege el acceso *a través de la base de datos*, y un
> pendrive perdido lo esquiva entero.

**Qué protege ya el cifrado de la F41 y qué no**, para que la decisión se tome
con el dato correcto:

| En el respaldo | Estado |
|---|---|
| Correos, teléfonos y direcciones de clientes y proveedores | **Cifrados** (`pgp_sym_encrypt`) |
| Contraseñas de usuario | Hasheadas (BCrypt) |
| **`nombre` y `apellido` de los clientes** | **EN CLARO** |
| Pedidos, importes, productos, inventario, bitácoras | **EN CLARO** |

Es decir: quien robe el USB sin BitLocker no puede contactar a los clientes, pero
sí sabe **quiénes son, qué compraron y por cuánto**. El cifrado de columnas y el
cifrado de volumen resuelven problemas distintos y **no se sustituyen**.

### Restauración verificada desde el secundario

Un respaldo en un medio que nunca se ha leído es una hipótesis. Ensayado:

```powershell
restaurar.ps1 -Modo Prueba -Desde Secundario -PuertoPrueba 5434
```

| | |
|---|---|
| Insumos | `full_20260815_214239` + su diferencial |
| Contenido | 6 usuarios · 5.004 clientes · 165.000 pedidos · 450.000 detalles · 200.061 logs |
| Marca del diferencial · roles · índices F33 | 1 · 6 · 4 ✅ |
| **RTO medido** | **15 s** (objetivo 120 min) |

El parámetro `-Desde Secundario` es de la F42. El puerto de prueba se pasa
explícitamente: **el 5433 es un respaldo congelado y no se toca jamás**.

---

## 8. Monitoreo: avisar por ausencia

El fallo más peligroso de un sistema de respaldos no es que uno salga con error:
eso se ve en el log. Es que **el job deje de ejecutarse y nadie lo note**, porque
entonces no hay ningún error que mirar. Se descubre el día que hay que restaurar.

`verificar_respaldos.ps1` corre a diario y revisa:

- Antigüedad del último completo (tolera 8 días), diferencial (48 h, porque el
  domingo no corre) y respaldo de aplicación.
- Que el completo vigente tenga **exactamente un** diferencial asociado.
- Que el completo conserve su `backup_manifest`, sin el cual no se pueden generar
  más diferenciales.
- Que `summarize_wal` siga activo.
- Que el simulacro de restauración se haya hecho en los últimos 30 días.
- Espacio libre en disco.

Cada tipo de respaldo deja un `estado_<tipo>.json` en `C:\respaldos\marathon\logs`
con resultado, fecha, duración y tamaño, que es lo que consume el verificador.

Códigos de salida: `0` todo al día · `1` avisos · `2` fallos críticos. Así la
tarea programada se puede encadenar con una notificación por correo.

Última verificación ejecutada:

```
OK  [full]         hace 0.6 h, 84.12 MB, 7 s
OK  [diferencial]  hace 0.6 h, 22.38 MB, 4 s
OK  [aplicacion]   hace 0.1 h, 0.02 MB, 1 s
OK  el FULL vigente tiene exactamente 1 diferencial
OK  summarize_wal sigue activo
OK  simulacro de restauración hace 0 días (RTO=0.27 min; objetivo=120 min)
RESULTADO: todos los respaldos al día.
```

---

## 9. Procedimiento de restauración ante desastre

### Paso 1 — Verificar qué hay disponible

```powershell
cd scripts\backup
.\verificar_respaldos.ps1
```

### Paso 2 — Ensayar antes de tocar producción

```powershell
.\restaurar.ps1 -Modo Prueba
```

Fusiona y arranca una instancia temporal en el puerto 5433. Confirma que los datos
están ahí **sin tocar** la base de producción. Nunca se restaura en producción sin
haber comprobado antes que el respaldo sirve.

### Paso 3 — Restaurar en producción

```powershell
.\restaurar.ps1 -Modo Produccion -Confirmar
```

Sin `-Confirmar` el script se niega a ejecutarse. Detiene el servicio, **renombra**
el directorio de datos actual a `data.reemplazado_<fecha>` (no lo borra: es la
única vía de vuelta si el respaldo resultara estar mal), copia el restaurado y
arranca el servicio.

### Paso 4 — Recuperar la aplicación

```powershell
git clone <remoto> && git checkout <commit de punto_recuperacion_git.json>
# descifrar configuracion_secretos.zip.dpapi según COMO_DESCIFRAR.txt
# restaurar .env y los .properties en su ubicación
cd marathon-backend  && mvn clean package
cd marathon-frontend && npm ci && npm run build
```

### Paso 5 — Validar y cerrar

Comprobar login, un pedido, el stock y un reporte. Cuando todo esté confirmado,
eliminar `data.reemplazado_<fecha>` para recuperar espacio.

---

## 10. Decisiones tomadas y por qué

| Decisión | Motivo |
|---|---|
| `pg_basebackup` nativo, no pgBackRest/Barman | Ninguno de los dos corre en Windows |
| Diferencial contra el manifiesto del completo | Restaurar con 2 piezas, sin cadena que romperse |
| Formato plano, no tar | `pg_combinebackup` necesita directorios; desempacar sumaría al RTO |
| `summarize_wal` en vez de `archive_mode` | Es `sighup`: no exige reiniciar el servidor |
| Respaldos fuera de la carpeta del proyecto | Está en OneDrive; se sincronizarían a la nube |
| Abortar si falta disco | Llenar el disco tumbaría el servidor de base de datos |
| Verificar con `pg_verifybackup` | Un respaldo sin verificar es una suposición |
| Conservar el directorio anterior al restaurar | Única vía de vuelta si el respaldo está mal |
| Cifrar la configuración, no la base | Los secretos son el activo sensible; la base está en un volumen local |
| Git como respaldo del código | Copiar código a ZIP perdería el historial |
| Monitoreo por ausencia | Un job que dejó de correr no genera errores que mirar |

---

## 11. La capa lógica: respaldar y restaurar desde la web (F92)

Todo lo anterior sigue igual y sigue siendo la copia de seguridad de verdad.
Esta sección añade una **segunda capa** con otro propósito, y la distinción
importa porque las dos se llaman «respaldo» y no sirven para lo mismo.

### 11.1 Por qué no bastaba con la capa física

La pantalla nueva pide poder **restaurar desde el navegador**. Con
`pg_basebackup` eso no se puede hacer, y no es cuestión de programarlo mejor:

- Un respaldo físico se restaura **parando el servicio de PostgreSQL y
  reemplazando el directorio de datos**. La aplicación web estaría serrando la
  rama en la que se sienta: su propia conexión muere a mitad.
- Exige privilegios de administrador de Windows sobre el servicio.
- Al terminar, la aplicación está caída y nadie ha visto el resultado.

Un volcado **lógico** (`pg_dump` / `pg_restore`) se restaura con el servidor
encendido, sobre la base en caliente. Ese es el motivo, y el único.

### 11.2 Las dos capas, repartidas

| | Física — `pg_basebackup` | Lógica — `pg_dump` (F92) |
|---|---|---|
| Dónde vive | Tareas Programadas de Windows | Dentro de la aplicación |
| Corre con el backend apagado | **Sí** | No |
| Para qué | Disco muerto, clúster corrupto | Punto de retorno a mano, simulacro |
| Cómo se restaura | Parando el servicio | Con el servidor encendido |
| Quién la dispara | El sistema operativo | Un administrador, desde la pantalla |

**La física no se retira ni se degrada.** Si el backend está apagado a las 02:00
no hay respaldo lógico esa noche, y eso es aceptable precisamente porque la
física no depende de él.

### 11.3 Medido en esta base (12 GB, 50,8 M filas)

| Operación | Tiempo | Resultado |
|---|--:|---|
| Volcado `-Fd -j 4 -Z 1` | **29 s** | 2,43 GB en disco |
| Borrado de 39 tablas (`TRUNCATE`) | **0,7 s** | 46,4 M filas |
| Restauración sobre base vacía | **3 min 46 s** | sin errores |
| Restauración sobre base llena | **4 min 53 s** | sin errores |

La restauración sobre una base llena es un minuto más lenta porque `--clean`
tiene que borrar 12 GB antes de escribir. Es el caso normal, así que es el
número que se enseña en la pantalla.

`-j 4` no es un adorno: con `-j 1` el volcado se va a varios minutos, y ahí un
botón en una pantalla deja de ser una idea razonable.

### 11.4 El esquema `control`: lo único que sobrevive

El diario de respaldos vive en un esquema propio, y esa es la decisión que
sostiene el diseño entero. Tiene que sobrevivir justo a las dos operaciones que
registra:

- El **borrado** vacía las tablas de negocio. Un diario entre ellas se borraría
  a sí mismo del registro.
- La **restauración** reemplaza `public` con el contenido del volcado. Un diario
  ahí dentro volvería al estado que tenía cuando se tomó ese respaldo, y perdería
  la fila que dice «fulano restauró hoy».

Con el diario fuera, `pg_dump --exclude-schema=control` lo deja fuera del
volcado y `pg_restore --clean` —que solo borra lo que el volcado contiene— no lo
toca. **Verificado**: tras restaurar sobre una base recién vaciada, `control`
conservaba las tres filas de las tres operaciones, con su autor y su IP.

### 11.5 Qué NO borra el borrado, y por qué

`usuario`, `usuario_rol`, `rol`, `permiso`, `rol_permiso` y `token_revocado` se
conservan siempre. **Sin ellos nadie puede volver a entrar**, y quien acaba de
borrar la base se quedaría fuera del sistema justo cuando necesita pulsar
«Restaurar». Un simulacro del que no se puede volver no es un simulacro.

`auditoria_cambios` y `log_accion` se conservan **salvo que se marque la casilla**.
La F40 dejó `auditoria_cambios` en append-only incluso para el administrador
(AUDITORIA.md §2); un botón web que la vaciara por omisión desharía esa decisión
de tapadillo.

`historial_inventario` **sí se va siempre**, y no por elección: tiene clave ajena
contra `inventario`, y `TRUNCATE` exige que toda tabla que apunte a otra que se
vacía esté también en la lista. La pantalla enseña la lista completa antes de
pedir la confirmación precisamente para que eso no sea una sorpresa.

La lista se calcula con el **cierre transitivo de las claves ajenas** consultando
`pg_constraint`, no escrita a mano: una lista a mano se queda vieja en cuanto
alguien añade una tabla, y se queda vieja en silencio.

### 11.6 Modo mantenimiento, y el fallo que apareció al probarlo

Mientras dura la restauración, cualquier petición a `/api/**` recibe un **503**
con un mensaje legible y `Retry-After`. Sin eso, una petición que llegue a mitad
no se encuentra un dato viejo: se encuentra una tabla que no existe.

Dejar `/api/respaldos/**` fuera del 503 **no bastó**, y solo se vio probándolo:
a los cuatro segundos de empezar, `GET /api/respaldos/estado` devolvía **401**.
La causa es que el filtro JWT resuelve el token *consultando la tabla `usuario`*
en cada petición —a propósito desde la F48—, y esa tabla se está reemplazando.
Es decir: la única pantalla que tenía que sobrevivir era la primera en caerse.

La salida es servir ese estado **desde memoria**, antes de que la cadena de
seguridad toque la base: el progreso de la tarea más una foto de los contadores
tomada antes de empezar. Ningún dato de negocio, y nada que el propio 503 no
revele ya.

### 11.7 La barra de progreso, y por qué mentía

`pg_restore` no publica su avance por ningún lado salvo `--verbose`. Se cuenta
cuántos objetos trae el volcado con `pg_restore --list` y cuántos anuncia
`--verbose`, y el cociente es el porcentaje.

En la primera prueba la barra saltaba al 99 % a los cuarenta segundos y ahí se
quedaba cuatro minutos. El motivo era propio: la salida de los procesos se
recortaba a 8 KB para no llenar el diario de ruido, y ese recorte se aplicaba
**también** a `--list`, que devolvía 100 objetos en vez de 709. El recorte ahora
solo actúa en la corrida con `--verbose`, que es la que lo necesitaba.

Es el mismo tipo de error que el `RETURNING` de la F40: una decisión razonable
tomada en un sitio, aplicada sin querer a un caso donde significaba otra cosa.

### 11.8 La credencial

`pg_dump` y el `TRUNCATE` sobre `auditoria_cambios` necesitan superusuario, que
`usr_admin_marathon` deliberadamente no es. La contraseña **no está en ningún
fichero del repositorio**: llega por variable de entorno del proceso, igual que
la clave de cifrado de la F41, y la pone `scripts\cifrado\iniciar_backend.ps1`
leyéndola del `.env`.

Sin ella la aplicación arranca igual y la pantalla se declara «no disponible»
diciendo exactamente qué falta y cómo arreglarlo, en vez de ofrecer botones que
van a fallar.

### 11.9 Retención de la capa lógica

Se conservan en disco los **7 puntos más recientes** (`app.respaldo.retencion`),
y la purga corre al terminar cada respaldo.

No es un detalle menor: un respaldo automático de 2,4 GB cada noche son **73 GB
al mes**, y el día que se llene el volumen el que se cae no es la pantalla de
respaldos, es el servidor de base de datos. La capa física ya tenía su retención
(§5); esta necesitaba la suya.

**Se borra la carpeta, nunca la fila del diario.** Son dos cosas distintas: la
fila dice que el respaldo *se hizo* —eso es historia y no se reescribe— y la
carpeta dice si *todavía se puede usar*. El punto purgado deja de ofrecer el
botón de restaurar y su mensaje explica que fue la retención, no que alguien lo
borrara a mano. En una lista con un hueco, saber cuál de las dos cosas pasó es
exactamente lo que hace falta.

`app.respaldo.retencion=0` desactiva la purga, y es una opción legítima si los
respaldos van a un volumen que se vacía por otro medio.

### 11.10 Lo que esta capa NO resuelve

Dicho en voz alta, para que no se descubra el día que haga falta:

- **Si el backend está apagado a las 02:00, no hay respaldo lógico esa noche.**
  Es la contrapartida de tenerlo dentro de la aplicación (§11.2). Los respaldos
  físicos no dependen de esto.
- **Durante la restauración no se puede iniciar sesión.** `AuthService` lee la
  tabla `usuario`, que es una de las que se están reemplazando. Quien ya tiene la
  sesión abierta sigue viendo la pantalla de respaldos; quien llegue nuevo tendrá
  que esperar los cuatro o cinco minutos.
- **El 503 de mantenimiento no tiene pantalla propia.** Las demás vistas reciben
  el error y lo enseñan como cualquier otro fallo de red. Cerrarlo bien pediría
  un interceptor HTTP que reconozca el 503 y muestre un aviso general; no se ha
  hecho porque quien restaura se queda en la pantalla de respaldos, que sí lo
  explica.
- **El volcado se guarda sin cifrar**, en `C:\respaldos\marathon\web`. Contiene
  los datos personales ya cifrados en columna (F41), así que los contactos de
  cliente y proveedor siguen ilegibles sin la clave; el resto de la base, no.
  Vale lo mismo que decir que el volumen de respaldos hay que tratarlo como
  material sensible.
