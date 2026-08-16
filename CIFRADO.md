# Cifrado de datos — `mod_venta_inve`

Fase 41. Qué se cifra, qué no, con qué clave, dónde vive esa clave y qué cuesta.

---

## 1. Estado del cifrado tras esta fase

| Mecanismo | Estado | Desde |
|---|---|---|
| Contraseñas de aplicación (BCrypt `$2a$`, 60 car.) | ✅ | F02 |
| Contraseñas de PostgreSQL (SCRAM-SHA-256) | ✅ | F34 |
| Configuración con secretos (DPAPI de máquina) | ✅ | F35 |
| `data_checksums` (integridad, no cifrado) | ✅ | instalación |
| **Datos personales en reposo (`pgcrypto` 1.4)** | ✅ | **F41** |
| **TLS en tránsito (TLSv1.3)** | ✅ | **F41** |
| Respaldos de base de datos cifrados | ❌ | pendiente |

---

## 2. Qué se cifra y qué no

| Columna | ¿Cifrada? | Mecanismo | Por qué |
|---|---|---|---|
| `cliente.correo` | ✅ | `pgp_sym_encrypt` → `correo_enc` | Dato personal directo |
| `cliente.correo_hash` | — | `hmac(sha256)` | Repone `UNIQUE(correo)`, que el cifrado aleatorizado hacía imposible |
| `cliente.telefono` | ✅ | `pgp_sym_encrypt` → `telefono_enc` | Dato personal directo |
| `cliente.direccion` | ✅ | `pgp_sym_encrypt` → `direccion_enc` | Dato personal directo |
| `proveedor.correo` | ✅ | `pgp_sym_encrypt` → `correo_enc` | Dato de contacto |
| `proveedor.telefono` | ✅ | `pgp_sym_encrypt` → `telefono_enc` | Dato de contacto |
| `proveedor.direccion` | ✅ | `pgp_sym_encrypt` → `direccion_enc` | Dato de contacto |
| `proveedor.contacto` | ✅ | `pgp_sym_encrypt` → `contacto_enc` | Nombre de persona |
| `cliente.nombre` / `apellido` | ❌ | — | **Ver abajo** |
| `usuario.correo` | ❌ | — | **Ver abajo** |
| `usuario.password` | — | BCrypt (hash, no cifrado) | No debe poder revertirse |
| `proveedor` — columna hash | ❌ | — | No existía `UNIQUE(correo)` ni búsqueda por correo. Añadirla sería superficie sin función |

### Las exclusiones, que son decisiones y no olvidos

**`cliente.nombre` y `cliente.apellido` siguen en claro.** Cifrarlos rompería tres
funciones reales de la aplicación, no hipotéticas:

- `findByNombreOrApellido` hace `LOWER(nombre) LIKE '%texto%'`. Sobre un `bytea`
  no hay `LIKE` que valga: habría que descifrar las 5.003 filas en cada búsqueda.
- `findByEstadoOrderByApellidoAsc` ordena alfabéticamente. El cifrado
  aleatorizado destruye el orden, así que el `ORDER BY` daría un orden arbitrario.
- El selector de «Pedido nuevo» muestra `nombre apellido`, es decir, los pinta en
  pantalla igualmente.

Es una limitación declarada del alcance: **un atacante con el fichero de datos ve
quiénes son los clientes, pero no cómo contactarlos**. Se protege el dato de
contacto, no la identidad.

**`usuario.correo` sigue en claro** porque es la credencial de acceso:
`findByCorreo` se ejecuta en *cada* autenticación y en *cada* petición con JWT.
Cifrarlo obligaría a un hash determinista adicional para el login, y a descifrar
en cada petición, a cambio de proteger seis correos internos.

---

## 3. Arquitectura: por qué la opción A

Se evaluaron las dos opciones planteadas.

| | **A — columnas `bytea` + capa de servicio** | **B — tabla base + vista descifradora** |
|---|---|---|
| Esquema | `cliente` conserva su nombre; las columnas pasan a `bytea` | `cliente` → `cliente_base`, más una vista `cliente` |
| Java | Entidades tocadas | Entidades intactas |
| Escritura | `UPDATE` nativo con `fn_cifrar()` | Triggers `INSTEAD OF` |

**Se eligió A, y el motivo decisivo es medible, no estético.**

La opción B renombra `cliente` a `cliente_base`. El trigger de auditoría de la
F40 viaja con la tabla, así que pasaría a llamarse sobre `cliente_base`. Y
`fase40_pruebas_auditoria.sql` comprueba exactamente esto:

```sql
WHERE c.relname = t AND tg.tgname = 'trg_auditoria_' || t   -- t = 'cliente'
```

Con B, las pruebas 21 y 22 de una fase cerrada **fallarían**, y esa fase no se
puede modificar. Además, `auditoria_cambios.tabla` empezaría a registrar
`cliente_base` mientras las filas históricas dicen `cliente`, partiendo en dos la
trazabilidad de una misma tabla.

**Riesgo de la descartada**, para dejarlo dicho: B tenía la ventaja real de no
tocar Java, pero sumaba `INSTEAD OF INSERT` sobre una vista con
`GenerationType.IDENTITY`, es decir, dependía de que el `RETURNING` que emite
`getGeneratedKeys()` funcionara a través de la vista. Es justo el mecanismo que
ya rompió la bitácora en la F40.

### Cómo se implementó A sin reescribir las consultas

El problema de A es que la aplicación lee `cliente` por todas partes con
consultas derivadas de Spring Data. Convertirlas en consultas nativas habría
puesto en riesgo las 66 pruebas de endpoint.

La solución: los campos de la entidad pasan a ser `@Formula` de **solo lectura**.

```java
@Formula("fn_descifrar(correo_enc)")
private String correo;
```

Hibernate inyecta la llamada en el `SELECT` de cada consulta, así que
`findAll`, `findByEstado`, la paginación y los joins **siguen funcionando sin
tocarlos**. Las escrituras, que `@Formula` no cubre, pasan por `CifradoService`,
que emite el `UPDATE ... fn_cifrar(?)` con parámetros enlazados.

Las columnas `*_enc` **no se mapean como atributos JPA**. `ddl-auto=validate`
valida las entidades contra el esquema y no al revés: una columna sin atributo no
rompe el arranque, y mapearla invitaría a escribir texto cifrado desde Java.

---

## 4. La clave

### Dónde vive

```
C:\ProgramData\MarathonSports\crypto\clave.dpapi     blob DPAPI (ámbito máquina)
MARATHON_CRYPTO_KEY_PROTECTED                        variable de entorno de MÁQUINA, base64 del mismo blob
```

> **F42: la variable pasó de ámbito de usuario a ámbito de máquina.** En la F41
> se quedó en el del usuario porque escribir la de máquina exige consola
> elevada. **No era un problema de seguridad** —lo que se guarda ahí es el blob
> DPAPI cifrado, no la clave— sino de disponibilidad: en ámbito de usuario solo
> la ve la cuenta que la creó, así que un backend corriendo como servicio de
> Windows (SYSTEM o una cuenta de servicio) no la habría encontrado y habría
> arrancado mostrando los datos de contacto vacíos. Se promueve con
> `scripts\cifrado\completar_instalacion.ps1`, que pide UAC una sola vez.
> Verificado tras el cambio: misma huella `472b43907ba05386`, backend arranca y
> descifra igual.

32 bytes de `RandomNumberGenerator`, en base64 (44 caracteres). **No se deriva de
ninguna contraseña existente**: reutilizar la de la base pondría la clave al
alcance de quien ya tiene la base.

Deliberadamente **fuera de `C:\respaldos\marathon`**. Si la clave viajara dentro
del mismo respaldo que los datos que cifra, cifrar no habría servido de nada:
quien robe el respaldo se lleva las dos mitades.

### Cómo llega a la sesión

```
gestionar_clave.ps1 / iniciar_backend.ps1
        │  descifra el blob DPAPI
        ▼
  variable de entorno DEL PROCESO  (no de máquina, no de usuario)
        │  la hereda el JVM
        ▼
  ClaveCifradoDataSource, en cada getConnection()
        │  SELECT set_config('app.crypto_key', ?, false)   ← parámetro enlazado
        ▼
  fn_descifrar() la lee con current_setting('app.crypto_key')
```

**Por qué un parámetro enlazado y no `connectionInitSql`.** Hikari solo admite
SQL de inicialización literal, lo que metería la clave escrita dentro de la
sentencia. Con `log_statement = mod` y `log_parameter_max_length = -1`, eso puede
acabar en `postgresql-%a.log` en texto plano durante siete días. Con el parámetro
enlazado, el texto de la sentencia lleva un `?`.

**Por qué por conexión y no `SET LOCAL` por transacción.** Los listados de
cliente y proveedor **no son transaccionales**, y `SET LOCAL` fuera de una
transacción no tiene efecto ninguno: un listado sin clave devolvería una tabla
llena de huecos.

### Comprobación de filtración (no suposición)

`scripts\cifrado\buscar_filtraciones.ps1`, ejecutado tras la migración:

| Búsqueda | Resultado |
|---|---|
| Clave en los 101 `postgresql-*.log` | **0 coincidencias** |
| Clave en `auditoria_cambios` | **0** |
| Clave en `log_accion` | **0** |
| Un correo de cliente en claro en los registros | **0** |
| Un correo de cliente en claro en `auditoria_cambios` | **3, todas anteriores al cifrado** (§7) |
| Líneas `parameters: $1` en los registros | 0 |

### Recuperación

> **Si se pierde la clave, los datos cifrados son irrecuperables.** No hay puerta
> trasera, y los respaldos de `pg_basebackup` contienen el dato **ya cifrado**:
> restaurar el respaldo sin la clave devuelve los mismos `bytea` ilegibles.

Y un matiz que agrava lo anterior: **DPAPI en ámbito `LocalMachine` muere con la
máquina**. El blob no se puede descifrar en otro equipo. Un incendio, un disco
roto o un cambio de portátil dejan la copia operativa inservible aunque los
respaldos estén intactos.

Por eso la copia de custodia es obligatoria y **manual**:

```powershell
powershell -File scripts\cifrado\gestionar_clave.ps1 -Accion Escrow -Destino E:\custodia\clave.txt
```

El script rechaza como destino el repositorio (acabaría en un commit) y
`C:\respaldos\marathon` (la clave viajaría con el dato). El archivo resultante
contiene la clave **en claro** y debe trasladarse a un soporte fuera del equipo:
caja fuerte, gestor de contraseñas o unidad extraíble bajo llave.

**Procedimiento de reposición en un equipo nuevo:**

1. Instalar PostgreSQL y restaurar el respaldo. Los datos personales se ven como
   `\x c30d0407...`; es lo esperado.
2. Recuperar la clave de la custodia.
3. Reponerla: crear `C:\ProgramData\MarathonSports\crypto\clave.dpapi` con
   `ProtectedData::Protect` sobre el texto de la clave, ámbito `LocalMachine`, en
   el equipo nuevo.
4. Comprobar con `gestionar_clave.ps1 -Accion Estado` que la **huella** coincide
   con la anotada en la custodia (`472b43907ba05386` para la clave actual). La
   huella permite verificar que dos entornos usan la misma clave **sin llegar a
   mostrarla**.
5. Arrancar con `iniciar_backend.ps1` y comprobar que un listado devuelve correos
   legibles.

### Ensayo en seco: qué pasa exactamente si falta la clave

Un procedimiento de recuperación que nadie ha ensayado no sirve. Se arrancó el
backend **sin** `MARATHON_CRYPTO_KEY` y se midió el comportamiento real:

| Qué se comprobó | Resultado |
|---|---|
| ¿Arranca la aplicación? | **Sí**, en 4,05 s |
| Aviso en el registro | `WARN ... No hay clave de cifrado ... se mostraran VACIOS` |
| ¿Qué devuelve `fn_descifrar`? | `NULL` |
| Lectura de un cliente | `nombre`, `apellido`, ciudad y estado **correctos**; `email`, `telefono` y `direccion` en `null` |
| Alta de un cliente | **Rechazada** con `app.crypto_key no esta fijada en la sesion: no se puede cifrar` |
| ¿Queda un cliente a medias? | **No.** 0 filas con `correo_enc IS NULL` |

Las dos conclusiones que importan:

1. **Degrada, no cae.** Sin clave la aplicación sigue en pie y el resto del
   negocio —pedidos, inventario, compras— funciona con normalidad. Es
   deliberado: preferimos una aplicación que arranca y no muestra datos
   personales a una que no arranca.
2. **No corrompe.** La escritura falla en voz alta y, como `crear` es
   `@Transactional`, la transacción entera revierte. No queda ningún cliente
   guardado con los datos de contacto vacíos, que sería la forma silenciosa de
   perder información.

---

## 5. Sobrecoste medido

Protocolo de la F39: `EXPLAIN (ANALYZE, BUFFERS)`, 3 ejecuciones, se reporta la
3.ª. **Ruido medido entre corridas idénticas: 12,7 %.** Nada por debajo de eso
cuenta como señal.

| Medición | Sin cifrado | Con cifrado | Diferencia |
|---|--:|--:|--:|
| Listado de 4.620 clientes activos (3 campos) | 2,4 ms | 4.904 ms | **×2.013** |
| Página de 20 clientes (el listado real de la UI) | — | 15,3 ms | — |
| Búsqueda por correo, por `correo_hash` (índice único) | — | **0,009 ms** | — |
| Búsqueda por correo descifrando fila a fila | — | 1.815 ms | ×200.000 |
| Alta de cliente (INSERT + cifrado) | — | 1,2 ms | — |

**Coste unitario: ~0,33 ms por `pgp_sym_decrypt`.** No es lento por accidente:
PGP deriva la clave con S2K iterado y salado en **cada** llamada. Sobre una
página de 20 filas es invisible; sobre 4.620 filas × 3 campos son 14.000
derivaciones y casi 5 segundos.

### Lo que esa cifra obligó a cambiar

`GET /api/clientes/activos` pasó a tardar **6.038 ms**. Descifraba 13.860 datos
personales para alimentar un selector que solo pinta `nombre apellido (cedula)`
— es decir, **no mostraba ninguno de los datos que descifraba**.

Se cambió por una proyección que selecciona solo las columnas que se usan:

| Endpoint | Antes de la proyección | Después |
|---|--:|--:|
| `GET /api/clientes/activos` (4.620 filas) | 6.038 ms | **154 ms** |
| `GET /api/clientes?size=20` | 28 ms | 28 ms |
| `GET /api/proveedores?size=20` | 17 ms | 17 ms |

Es a la vez lo rápido y lo correcto en protección de datos: no se descifra lo que
no se va a mostrar.

**La columna hash es lo que salva la búsqueda por correo:** 0,009 ms con índice
único frente a 1.815 ms descifrando las 5.003 filas.

### Crecimiento

| | Antes | Ahora |
|---|--:|--:|
| Correo de un cliente | ~25 bytes | **92 bytes** cifrados + 32 de hash |
| Tabla `cliente` | ~1,4 MB | **3,4 MB** |
| Base completa | 191 MB | **227 MB** |

El sobrecoste por columna es fijo (~65 bytes de cabecera PGP por valor), así que
pesa mucho en campos cortos como el teléfono.

---

## 5.bis Qué costó cifrar

Es la primera pregunta que hace quien sabe del tema, así que conviene que esté
respondida antes de que la haga. **Cifrar una columna no es cambiarle el tipo: es
renunciar a todo lo que la base sabía hacer con su contenido.**

| Capacidad perdida | Estado | Cómo se compensó |
|---|---|---|
| **Unicidad** (`UNIQUE(correo)`) | **Repuesta** | Columna `correo_hash` con HMAC-SHA256 determinista e índice único. Búsqueda en **0,009 ms** |
| **Validación de formato** (`CHECK` regex) | **Movida fuera de la base** | Misma expresión en `CifradoService`, más `@Email` en el DTO |
| **Búsqueda parcial** (`LIKE '%texto%'`) | **Perdida** | Ninguna. Requeriría descifrar las 5.009 filas en cada búsqueda: **1.815 ms** medidos |
| **Ordenamiento** (`ORDER BY correo`) | **Perdida** | Ninguna. El cifrado aleatorizado destruye el orden |
| **Rango / comparación** (`BETWEEN`, `<`) | **Perdida** | Ninguna. No aplica a datos de contacto |
| **Rendimiento de lectura** | **Degradado y mitigado** | 0,33 ms por dato; proyección que no descifra lo que no se muestra |
| **Tamaño** | **+65 bytes por valor** | Cabecera PGP fija. Pesa mucho en campos cortos como el teléfono |

### Las dos pérdidas que no tienen vuelta

**Búsqueda parcial y ordenamiento sobre los campos cifrados no se pueden
recuperar** sin renunciar al cifrado. Es la razón exacta por la que
`cliente.nombre` y `apellido` **se quedaron en claro**: la aplicación busca por
nombre y ordena por apellido, y cifrarlos habría roto dos funciones reales a
cambio de proteger un dato que la propia pantalla muestra igualmente.

La regla que se siguió: **se cifra lo que la aplicación solo necesita mostrar,
no lo que necesita consultar.**

### La validación bajó de nivel, y eso importa

El `CHECK chk_cliente_correo` era una garantía **de la base**: se aplicaba a
cualquier escritura, viniera de donde viniera. Ahora vive en `CifradoService`, y
eso significa que **quien escriba por `psql` se la salta**. Se puso ahí y no solo
en el DTO porque este servicio es el único punto por el que pasan todas las
escrituras de datos cifrados —de cliente y de proveedor—, así que cubre también a
un servicio futuro que olvide anotar su DTO.

Que aporta cobertura real está comprobado: `@Email` acepta `a@b`, y el `CHECK`
original lo rechazaba. Hoy lo rechaza `CifradoService`:

```
{"status":400,"message":"El correo de cliente no tiene un formato valido: a@b"}
```

### La alternativa que se evaluó y se descartó: `s2k-mode=0`

`pgp_sym_encrypt` deriva la clave con S2K **iterado y salado** en cada llamada, y
ahí se va casi todo el coste. Con `s2k-mode=0` esa derivación desaparece.
Medido sobre 2.000 descifrados:

| | Tiempo | Por dato | Tamaño |
|---|--:|--:|--:|
| S2K por defecto (iterado) | 410,6 ms | 0,205 ms | 91 bytes |
| `s2k-mode=0` | **6,7 ms** | **0,0033 ms** | 82 bytes |
| Diferencia | — | **61× más rápido** | −9 bytes |

**Y sin embargo se descartó.** El argumento a favor es fuerte: el estiramiento
S2K existe para encarecer el ataque por fuerza bruta a contraseñas *de baja
entropía*, y la nuestra son **32 bytes de un generador criptográfico**. Contra una
clave de 256 bits reales, iterar la derivación no protege de nada porque nadie va
a probar 2²⁵⁶ combinaciones, y la sal solo evita precomputación entre objetivos
múltiples, que aquí tampoco aplica.

La razón de conservarlo es otra, y es de diseño defensivo:

> **`s2k-mode=0` convierte un error futuro en una brecha.** El día que alguien
> rote la clave y, en lugar de generarla con el script, escriba una frase que
> pueda recordar —que es exactamente el tipo de cosa que pasa—, con S2K por
> defecto esa frase débil seguiría costando 65.536 iteraciones por intento; con
> `s2k-mode=0` se rompería en minutos. El valor por defecto **falla seguro** y el
> otro **falla abierto**.

A eso se suma que el coste ya es aceptable tras la proyección de la F41 (154 ms
el listado completo, 15 ms una página), y que cambiarlo obligaría a recifrar las
5.009 filas. **Se gana un 61 % de un coste que ya no molesta, a cambio de perder
el margen de seguridad ante un error humano previsible.** No compensa.

---

## 6. TLS en tránsito

Cifrar en reposo mientras la conexión viaja en claro es incoherente, aunque todo
sea `127.0.0.1`: cualquier proceso local puede leer el tráfico de loopback, y por
ahí pasan las contraseñas de los seis roles en cada arranque.

| | |
|---|---|
| Certificado | Autofirmado, `CN=localhost`, 825 días, `subjectAltName` con `localhost` e `IP:127.0.0.1` |
| Ubicación | `server.crt` / `server.key` en el directorio de datos |
| Permisos de la clave | Solo `NT AUTHORITY\NetworkService` (cuenta del servicio) y Administradores |
| Activación | `ALTER SYSTEM SET ssl = on` + `pg_reload_conf()` — contexto `sighup`, **sin reiniciar** |
| JDBC | **`sslmode=verify-full`** + `sslrootcert` *(F42)* |
| Verificado | `pg_stat_ssl`: **TLSv1.3 / TLS_AES_256_GCM_SHA384** en las conexiones del pool |

`ALTER SYSTEM` escribe en `postgresql.auto.conf`, así que la configuración del
planificador de la F39 queda intacta y esto se revierte con una línea.

### F42: de `require` a `verify-full`

La F41 se quedó en `require`, que **cifra pero no autentica al servidor**: no
protege de un intermediario. El obstáculo era el certificado autofirmado, que
`verify-full` exige validar contra una CA de confianza.

**Se resolvió sin tocar el truststore del JDK.** El driver de PostgreSQL acepta
`sslrootcert` apuntando al certificado, que se copia a
`C:\ProgramData\MarathonSports\tls\server.crt`. Importarlo al `cacerts` del JDK
habría atado la aplicación a esa instalación concreta de Java y se habría perdido
en la primera actualización.

```
jdbc:postgresql://localhost:5432/mod_venta_inve?sslmode=verify-full&sslrootcert=C:/ProgramData/MarathonSports/tls/server.crt
```

`verify-full` comprueba además el **nombre del host**, así que el certificado
necesita `subjectAltName`; el nuestro lleva `DNS:localhost` e `IP:127.0.0.1`.

**Probado que verifica de verdad, no que lleva la etiqueta:**

| Prueba | Resultado |
|---|---|
| Con el certificado correcto | Conecta, `ssl=true TLSv1.3` |
| Con el fichero ausente | `root certificate file ... does not exist` |
| Con una **CA ajena válida** | `SSL error: certificate verify failed` |

La tercera es la que importa: un intermediario con su propio certificado válido
es rechazado.

Revertir: `configurar_tls.ps1 -Revertir`.

---

## 7. Vías laterales cerradas

**1. La auditoría de la F40.** `cliente` y `proveedor` son dos de las cinco
tablas con trigger genérico, y ese trigger escribe `valor_anterior` y
`valor_nuevo` en una tabla que leen `rol_administrador` y `rol_supervisor`. Se
añadieron `correo_enc`, `telefono_enc`, `direccion_enc`, `contacto_enc` y
`correo_hash` a la lista de campos **enmascarados**, junto a `password`. La
bitácora registra **qué campo cambió**, nunca a qué valor:

```
1035 | cliente | 5027 | UPDATE | correo_enc | *** | *** (modificado) | usr_admin_marathon | 1
```

**2. Índices.** Ningún índice sobrevive sobre las columnas en claro — cayeron con
ellas — y se comprueba en la prueba 44 en vez de suponerlo. El único índice nuevo
es `uq_cliente_correo_hash`, sobre el HMAC, que no revela el correo.

**3. Vistas y consultas.** Cero vistas dependen de `cliente` o `proveedor`.
Ninguna consulta del código leía esas columnas por SQL: solo `ClienteService` y
`ProveedorService` las tocaban, vía entidad. Con la opción A esto se habría roto
en el arranque (`ddl-auto=validate`) de haberse escapado alguna.

**4. Residuo declarado.** La bitácora conserva **3 filas con correos legibles**,
escritas por la prueba funcional de la F40 cuando la columna todavía estaba en
claro. Cifrar la tabla no limpia retroactivamente lo que la auditoría ya guardó.

No se borran, y la razón importa: `auditoria_cambios` es **append-only por
diseño**, y reescribir la bitácora para tapar un hallazgo sería peor que el
hallazgo. Son datos de prueba (`@correo-demo.ec`), no clientes reales. Lo que sí
se exige, y se comprueba en cada corrida del arnés, es que **ese conjunto no
crezca**: ninguna fila posterior al cifrado puede contener un correo legible.

---

## 8. Lo que este cifrado NO protege

Declarado, no escondido:

1. **No protege de la propia aplicación.** Quien comprometa el backend tiene la
   clave en memoria y la publica en cada conexión. Esto protege del acceso al
   *fichero de datos*, al *respaldo* y al *psql directo*, no de un atacante
   dentro del proceso.
2. **No protege de `postgres`.** El superusuario puede leer
   `current_setting('app.crypto_key')` de una sesión de la aplicación.
3. **La identidad no está cifrada.** `nombre` y `apellido` en claro (§2).
4. **El CHECK de formato de correo se perdió como garantía de base de datos.**
   Se repuso en `CifradoService` (§5.bis), pero quien escriba por `psql` se la
   salta. La garantía bajó de nivel; no desapareció, pero tampoco es la que era.
5. **Los respaldos no están cifrados aparte.** Contienen el dato ya cifrado, que
   es lo relevante, pero `nombre`, `apellido` y todo el resto del negocio van en
   claro. Por eso el USB de la regla 3-2-1 debe llevar BitLocker To Go: ver
   `ESTRATEGIA_RESPALDO.md`.
6. ~~`sslmode=require` no autentica al servidor~~ — **resuelto en la F42** con
   `verify-full` (§6).
7. **Toda escritura reescribe las tres columnas cifradas**, incluso si el valor
   no cambió, porque `pgp_sym_encrypt` nunca produce el mismo `bytea`. La
   auditoría no puede distinguir «se volvió a cifrar lo mismo» de «cambió de
   verdad»: registra ambos como cambio.

---

## 9. Verificación

| Prueba | Resultado |
|---|---|
| `fase41_pruebas_cifrado.sql` | **51 / 51** |
| `fase34_pruebas_roles.sql` | **61 / 61** (sin cambios) |
| `fase40_pruebas_auditoria.sql` | **29 / 29** (corregido, ver §10) |
| `fase37_pruebas_endpoints.ps1` | **66 / 66** |
| `fase37_pruebas_navbar.ps1` | **20 / 20** |
| Arranque con `ddl-auto=validate` | ✅ 4,12 s |
| Migración verificada por descifrado | 5.003 + 6 filas, **0 discrepancias** |

---

## 10. Hallazgo de la fase: un arnés que no revertía

`fase40_pruebas_auditoria.sql` dice en su cabecera «NO MODIFICA DATOS» y termina
en `ROLLBACK`. **Pero no abre transacción**, y psql trabaja en autocommit: cada
sentencia de nivel superior —incluido cada bloque `DO`— se confirma sola. El
`ROLLBACK` final no revierte nada porque no hay nada abierto que revertir.

La salvaguarda de subtransacciones que sí tiene (`pg_temp.probar`, con su
excepción centinela) cubre las pruebas de privilegios, pero **no** los bloques
`DO` que hacen cambios reales para comprobar que la auditoría los registra.

Efecto medido, por cada ejecución del arnés:

| Daño | Detalle |
|---|---|
| `usuario` #1 desactivado | `estado = 'inactivo'` — el administrador no podía iniciar sesión |
| `usuario` #1 con la contraseña destruida | 61 caracteres terminados en `AAA`; BCrypt son exactamente 60 |
| Una fila de `rol_permiso` borrada | Una por corrida: `id_rol_permiso` 1 y 2, ambas del rol Administrador |

Se detectó porque el login de administrador fallaba en la prueba funcional de
esta fase. **La reparación se hizo con los datos que la propia
`auditoria_cambios` había registrado** (ids 1029-1031 y 1046), que es exactamente
para lo que sirve una bitácora de cambios.

### Corregido

`fase40_pruebas_auditoria.sql` lleva ahora **`BEGIN;` como primera sentencia**,
para que el `ROLLBACK` del final tenga algo que revertir. Se corrigieron también
los dos comentarios del archivo que afirmaban lo contrario.

Verificado ejecutándolo **cuatro veces sin `psql -1`**, que es exactamente como
rompía:

| | Antes de correr | Después de 4 corridas |
|---|---|---|
| `usuario` #1 | `activo`, hash de 60 car. | `activo`, hash de 60 car. |
| Filas en `rol_permiso` | 56 | 56 |
| Filas en `auditoria_cambios` | 30 | 30 |
| Resultado del arnés | — | **29/29** en las cuatro |

Ni siquiera las filas de auditoría que generan las propias pruebas sobreviven, que
es lo que se espera de un arnés que dice no modificar datos. Endpoints 66/66 y
navbar 20/20 después de las corridas.

`psql -1` sigue funcionando como red adicional —solo emite un aviso de que ya hay
una transacción en curso— pero ya no hace falta.

`fase41_pruebas_cifrado.sql` abrió con `BEGIN;` desde el principio por este mismo
motivo.

---

## 11. Archivos

| Archivo | Qué hace |
|---|---|
| `marathon-backend/sql/fase41_eliminar_indices.sql` | Baja de los 4 índices de la F39, con la reversión escrita dentro |
| `marathon-backend/sql/fase41_cifrado.sql` | Extensión, funciones, columnas, migración, privilegios y enmascarado |
| `marathon-backend/sql/fase41_pruebas_cifrado.sql` | 51 pruebas |
| `scripts/cifrado/gestionar_clave.ps1` | Crear, consultar, custodiar la clave y ejecutar SQL con ella |
| `scripts/cifrado/iniciar_backend.ps1` | Arranca el backend con la clave en el entorno del proceso |
| `scripts/cifrado/buscar_filtraciones.ps1` | Busca clave y datos en claro en registros y bitácoras |
| `scripts/cifrado/configurar_tls.ps1` | Certificado, `ssl=on` y verificación por `pg_stat_ssl` |
