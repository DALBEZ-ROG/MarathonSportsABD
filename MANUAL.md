# Manual de operación — Marathon Sports / `mod_venta_inve`

Todo lo que hay que saber para arrancar, operar, auditar y recuperar este
sistema. Cubre las fases 33 a 42.

**Estado:** proyecto cerrado. 7 de los 8 requisitos en ✅; el restante es una
decisión de diseño argumentada, no trabajo pendiente.

---

## 0. Cómo usar este manual

Este documento es el **punto de entrada**. Cada área tiene además un documento
propio con el detalle y las mediciones:

| Si necesitas… | Ve a |
|---|---|
| Arrancar el sistema hoy | §2 de este manual |
| Entender el modelo de seguridad | `SEGURIDAD_ROLES.md` |
| Saber qué está cifrado y cómo | `CIFRADO.md` |
| Restaurar un respaldo | `ESTRATEGIA_RESPALDO.md` §9 |
| Justificar un parámetro del servidor | `CONFIGURACION_BD.md` |
| Ver la trazabilidad de los 8 requisitos | `AUDITORIA_FINAL_8_REQUISITOS.md` |
| Entender la auditoría de cambios | `AUDITORIA.md` |
| Reproducir el estudio de rendimiento | `OPTIMIZACION_CONSULTAS_V2.md` |
| Montar la base desde cero | `SETUP_COMPLETO.md` |
| Hacer la demostración | `DEMO_CHECKLIST.md` |
| Saber qué quedó a medias y por qué | `DEUDA_TECNICA.md` |

**Si algo no funciona, salta al §10 (Cuando algo falla).** Ahí están todas las
trampas que ya nos costaron tiempo una vez.

---

## 1. El sistema de un vistazo

### Cifras

| | |
|---|--:|
| Tablas | 38 |
| Filas | 1.041.830 |
| Tamaño de la base | 228 MB |
| Índices | 122 |
| Triggers (todos activos) | 30 |
| Privilegios de columna | 2.155 |
| Usuarios de PostgreSQL | 6 |
| Roles de PostgreSQL | 6 |
| Pruebas automatizadas | 227 |

### Piezas

```
Angular 17  ──HTTP/JWT──▶  Spring Boot 3 (Java 17)  ──TLS 1.3──▶  PostgreSQL 18.3
                                    │                                    │
                          RoleRoutingDataSource              6 usuarios / 6 roles
                          (un pool por rol)                  privilegios por columna
                                    │                        pgcrypto · auditoría
                          ClaveCifradoDataSource                    │
                          (publica app.crypto_key)          C:\respaldos\marathon
                                                            + disco externo USB
```

### Dónde está cada cosa

| Ruta | Qué es |
|---|---|
| `marathon-backend/` | Backend Spring Boot |
| `marathon-frontend/` | Frontend Angular |
| `marathon-backend/sql/` | Scripts de todas las fases y arneses de prueba |
| `scripts/backup/` | Respaldo, verificación, restauración, tareas programadas |
| `scripts/cifrado/` | Clave, arranque, TLS, búsqueda de filtraciones |
| `C:\respaldos\marathon\` | Respaldos (fuera del repo, fuera de OneDrive) |
| `C:\ProgramData\MarathonSports\crypto\` | Almacén DPAPI de la clave de cifrado |
| `C:\ProgramData\MarathonSports\tls\` | Certificado para `verify-full` |
| `C:\Program Files\PostgreSQL\18\` | Servidor y binarios |

---

## 2. Arrancar y operar

### Arrancar el backend — la forma correcta

```powershell
powershell -ExecutionPolicy Bypass -File scripts\cifrado\iniciar_backend.ps1
```

> **No lo arranques con `mvn spring-boot:run` a secas.** Arrancará igual, pero
> **sin la clave de cifrado**: los correos, teléfonos y direcciones de clientes y
> proveedores se mostrarán **vacíos** y solo quedará un `WARN` en el registro.
> El script descifra la clave del almacén DPAPI y se la pasa al proceso Java.

El arranque tarda ~4 s. Señales de que fue bien:

```
c.m.config.ClaveCifradoDataSource : Clave de cifrado cargada (44 caracteres).
c.marathon.MarathonBackendApplication : Started MarathonBackendApplication in 4.0 s
```

### Entorno de la máquina

Cosas que no se deducen leyendo el repositorio:

| | |
|---|---|
| PostgreSQL | **18.3**, servicio `postgresql-x64-18`, binarios en `C:\Program Files\PostgreSQL\18\bin` |
| Java | El `java` del PATH es **1.8 y no sirve**. Hay que forzar `JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot` |
| Maven | **No hay `mvnw`**. Está fuera del proyecto: `...\apache-maven-3.9.16\bin\mvn.cmd` |
| Perfil activo | `local` → **`application-local.properties` manda sobre el `.env`** |
| Puerto 5432 | Producción |
| Puerto **5433** | **Respaldo congelado. No se toca jamás.** |

### Credenciales

**No hay contraseñas en este manual y no debe haberlas.** Viven en:

- `.env` (gitignored) — dos juegos separados: `DB_USER`/`DB_PASSWORD` para la
  aplicación (`usr_admin_marathon`) y `PG_SUPERUSER`/`PG_SUPERUSER_PASSWORD`
  para los respaldos (`postgres`, porque `pg_basebackup` exige `REPLICATION` y
  la cuenta web no debe tenerlo).
- `application-local.properties` (gitignored) — credenciales de los seis pools.
- Usuarios de demostración de la aplicación: `DEMO_CHECKLIST.md`. El de
  administrador es `admin@marathon.com`.

### Conectarse por psql

```powershell
$env:PGPASSWORD = '<PG_SUPERUSER_PASSWORD del .env>'
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -h localhost -p 5432 -U postgres -d mod_venta_inve
```

Para ejecutar SQL **que necesite descifrar** datos personales, no uses `psql`
directamente: usa el envoltorio que publica la clave en la sesión.

```powershell
powershell -File scripts\cifrado\gestionar_clave.ps1 -Accion Ejecutar -Script <ruta.sql>
```

---

## 3. Seguridad: usuarios, roles y privilegios

Detalle completo en `SEGURIDAD_ROLES.md`.

### El modelo

**Seis roles de grupo (`NOLOGIN`)** que contienen los privilegios, y **seis
usuarios (`LOGIN`)** que son miembros de ellos:

| Rol | Usuario |
|---|---|
| `rol_administrador` | `usr_admin_marathon` |
| `rol_supervisor` | `usr_supervisor_marathon` |
| `rol_operador_bodega` | `usr_bodega_marathon` |
| `rol_operador_pedidos` | `usr_pedidos_marathon` |
| `rol_encargado_compras` | `usr_compras_marathon` |
| `rol_encargado_produccion` | `usr_produccion_marathon` |

Ninguno es superusuario, ninguno tiene `CREATEROLE` ni `BYPASSRLS`. `PUBLIC`
está revocado sobre las 38 tablas.

### Un pool de conexiones por rol (F37)

Hasta la F36 toda la aplicación se conectaba como administrador: el modelo de
privilegios existía pero solo separaba «la aplicación» de «psql». Un operador de
bodega llegaba a la base con privilegios de administrador y lo único que lo
frenaba era `SecurityConfig`.

`RoleRoutingDataSource` elige el pool según el rol del usuario autenticado.
**Falla cerrado**: si un usuario autenticado tiene un rol sin pool configurado,
lanza excepción en lugar de caer al pool del administrador — lo contrario
convertiría un rol mal configurado en una escalada silenciosa.

Tres momentos usan el pool por defecto a propósito: el arranque, el login (que
tiene que leer `usuario` antes de saber quién es quién) y el filtro JWT.

### Privilegios por columna

2.155 privilegios de columna. Ejemplo real: `rol_operador_bodega` puede leer
`cliente` pero no escribirlo; `rol_encargado_compras` no puede leer `cliente` en
absoluto.

> **Un privilegio de columna no protege el contenido cifrado.** Tener `SELECT`
> sobre `correo_enc` devuelve `\x c30d0407...`. Para leerlo hace falta *además*
> la clave en la sesión, y eso solo lo hace la aplicación. Son dos capas
> independientes: `GRANT` decide si puedes seleccionar la columna, la clave
> decide si el contenido significa algo.

### Tres trampas del ORM que encontramos

Ninguna se ve leyendo el código: solo aparecen ejercitando la aplicación con el
rol real.

1. **`@DynamicUpdate` (F37).** Un `GRANT UPDATE(columna)` no sirve de nada si
   Hibernate emite `UPDATE` con **todas** las columnas: el privilegio se evalúa
   sobre las columnas de la sentencia, no sobre las que cambiaron.
2. **`RETURNING` invisible (F40).** `log_accion.id_log` es `GENERATED ALWAYS AS
   IDENTITY`, así que Hibernate recupera la clave con `getGeneratedKeys()`, que
   el driver implementa añadiendo `RETURNING`. **`RETURNING` exige `SELECT`**, y
   bodega y pedidos tienen `INSERT` pero no `SELECT` sobre la bitácora — a
   propósito. Se resolvió con un `INSERT` nativo sin `RETURNING`, sin conceder
   `SELECT`.
3. **`@Formula` para leer columnas cifradas (F41).** Ver §4.

---

## 4. Cifrado

Detalle completo en `CIFRADO.md`.

### Qué está cifrado y qué no

| Dato | Estado |
|---|---|
| `cliente.correo` / `telefono` / `direccion` | **Cifrado** (`pgp_sym_encrypt`) |
| `proveedor.correo` / `telefono` / `direccion` / `contacto` | **Cifrado** |
| `cliente.correo_hash` | HMAC-SHA256 — repone el `UNIQUE(correo)` |
| `usuario.password` | Hash BCrypt (no cifrado: no debe poder revertirse) |
| **`cliente.nombre` y `apellido`** | **EN CLARO** |
| **`usuario.correo`** | **EN CLARO** |
| Pedidos, importes, inventario, bitácoras | EN CLARO |

**Las exclusiones son decisiones, no olvidos.** Cifrar `nombre`/`apellido`
rompería la búsqueda por nombre, el orden alfabético y el listado — funciones
reales de la aplicación. `usuario.correo` es la credencial de acceso: se consulta
en cada login y en cada petición con JWT.

> La regla que se siguió: **se cifra lo que la aplicación solo necesita mostrar,
> no lo que necesita consultar.**

### Cómo funciona en el código

Las columnas en claro **ya no existen**. En su lugar hay `*_enc` de tipo `bytea`.
Las entidades JPA leen mediante `@Formula`, de **solo lectura**:

```java
@Formula("fn_descifrar(correo_enc)")
private String correo;
```

Así `findAll`, los filtros, la paginación y los joins siguen funcionando sin
tocarlos. Las escrituras pasan por **`CifradoService`**, que emite el
`UPDATE ... fn_cifrar(?)` con parámetros enlazados y refresca la entidad.

**La clave nunca entra en el JVM para cifrar**: el cifrado ocurre dentro de
PostgreSQL.

### La clave

| | |
|---|---|
| Qué es | 32 bytes de un generador criptográfico, en base64 (44 caracteres) |
| Dónde vive | Blob DPAPI de máquina en `C:\ProgramData\MarathonSports\crypto\clave.dpapi` |
| Variable | `MARATHON_CRYPTO_KEY_PROTECTED` (ámbito **máquina**) |
| Huella | `472b43907ba05386` |
| Cómo llega a la sesión | `set_config('app.crypto_key', ?, false)` **con parámetro enlazado**, en cada conexión |

**Por qué parámetro enlazado y no literal:** con `log_statement = mod` y
`log_parameter_max_length = -1`, una sentencia con la clave escrita dentro
acabaría en `postgresql-%a.log` en texto plano durante siete días.

Comandos:

```powershell
# ver estado sin revelar la clave (imprime solo la huella)
powershell -File scripts\cifrado\gestionar_clave.ps1 -Accion Estado

# ejecutar SQL con la clave publicada en la sesión
powershell -File scripts\cifrado\gestionar_clave.ps1 -Accion Ejecutar -Script <ruta.sql>

# exportar copia de custodia
powershell -File scripts\cifrado\gestionar_clave.ps1 -Accion Escrow -Destino <ruta>

# comprobar que no se filtró a ningún registro
powershell -File scripts\cifrado\buscar_filtraciones.ps1
```

### Custodia y recuperación

> **Si se pierde la clave, los datos cifrados son irrecuperables.** No hay puerta
> trasera, y los respaldos contienen el dato **ya cifrado**: restaurar sin la
> clave devuelve los mismos `bytea` ilegibles.
>
> Peor: **DPAPI `LocalMachine` muere con la máquina.** El blob no se puede
> descifrar en otro equipo.

El script de escrow **rechaza tres destinos**: el repositorio (acabaría en un
commit), `C:\respaldos\marathon` (la clave viajaría con los datos que cifra) y
**cualquier ruta de OneDrive** (se sincronizaría a la nube y quedaría en el
historial de versiones aunque después se borre el archivo). En este equipo el
Escritorio y Documentos **están dentro de OneDrive**.

**Qué pasa exactamente si falta la clave** (ensayado, no supuesto):

| | |
|---|---|
| ¿Arranca la aplicación? | **Sí**, con un `WARN` |
| `fn_descifrar` | Devuelve `NULL` |
| Lectura | Nombre, ciudad y estado correctos; contacto en `null` |
| Escritura | **Rechazada** en voz alta; la transacción revierte entera |
| ¿Filas a medias? | **No.** Cero |

Degrada, no cae. Y no corrompe.

### TLS

`ssl = on` con certificado autofirmado; el cliente usa **`sslmode=verify-full`**
con `sslrootcert` apuntando al certificado (no se tocó el truststore del JDK,
que se habría perdido en la primera actualización de Java).

Resultado verificado: **TLSv1.3 / `TLS_AES_256_GCM_SHA384`**, y rechaza una CA
ajena con `certificate verify failed`. El tráfico va cifrado **y el servidor
queda autenticado**.

### Qué costó cifrar

| Capacidad | Estado |
|---|---|
| Unicidad del correo | **Repuesta** con HMAC + índice único (0,009 ms) |
| Validación de formato | **Movida** a `CifradoService` (era un `CHECK`) |
| Búsqueda parcial `LIKE` | **Perdida** |
| Ordenamiento | **Perdido** |
| Lectura | ≈0,2–0,35 ms por dato descifrado |
| Tamaño | +65 bytes por valor |

**La lección de rendimiento:** `GET /api/clientes/activos` pasó a tardar
**6.038 ms** al cifrar, porque descifraba 13.860 datos personales para un
selector que solo muestra `nombre apellido`. Con una proyección que selecciona
solo lo que se usa: **154 ms**. No descifres lo que no vas a mostrar — es a la
vez lo rápido y lo correcto en protección de datos.

---

## 5. Auditoría

Detalle completo en `AUDITORIA.md`.

**Cuatro mecanismos**, cada uno cubriendo lo que los otros no:

| Mecanismo | Capa | Qué registra | Alcance |
|---|---|---|---|
| Registro nativo | Servidor | Toda sentencia de modificación, con usuario, origen y `xid` | 38 tablas |
| `log_accion` | Aplicación | Operación de negocio legible + usuario | 22 servicios |
| `historial_inventario` | Trigger | Stock anterior y nuevo | `inventario` |
| `auditoria_cambios` | Trigger | **Campo a campo**, con dos usuarios y `txid` | 5 tablas críticas |

### `auditoria_cambios`

Registra **una fila por campo que cambió**, no un volcado JSON de la fila entera:
una auditoría en la que hay que diffear dos JSON a mano no responde «qué valor
tenía antes» sin trabajo extra. El `txid` reagrupa los cambios de un mismo acto.

**Append-only incluso para el administrador.** El administrador *lee* su propia
auditoría; no la reescribe. Solo `postgres` puede purgar. Los seis roles generan
filas igualmente, mediante una función `SECURITY DEFINER` con `search_path`
fijado dentro.

### Las dos columnas de usuario

Es la pieza que compensa que haya una cuenta de PostgreSQL **por rol** y no por
persona:

- **`usuario_bd`** (`session_user`) dice *el rol*: «un operador de bodega», no cuál.
- **`usuario_app`** dice *la persona*, pero solo si la aplicación fijó
  `app.current_user_id`.

> **`usuario_app` nulo con `usuario_bd` presente = un cambio hecho fuera de la
> aplicación.** Por psql, por un script, o por alguien con la credencial. Es el
> caso que más le interesa a una auditoría, y por eso el nulo no se rellena con
> un valor por defecto: significa algo.

**Campos enmascarados** (se registra que cambiaron, nunca a qué): `password` y
todas las columnas cifradas. Un hash BCrypt en la bitácora es material para un
ataque offline.

---

## 6. Respaldos

Detalle completo en `ESTRATEGIA_RESPALDO.md`.

### Esquema

`pg_basebackup` **completo** semanal + **diferencial** diario, fusionados con
`pg_combinebackup` al restaurar. Manifiesto SHA-256 y verificación con
`pg_verifybackup` en **cada** corrida — un respaldo que no se verifica no es un
respaldo, es una suposición.

### Regla 3-2-1

| Requisito | Cómo se cumple |
|---|---|
| 3 copias | original + completo + diferencial + réplica externa |
| 2 medios | disco interno + **disco externo USB** |
| 1 fuera del sitio | el USB sale del equipo |

El destino secundario se localiza por **etiqueta de volumen** (`MARATHON_BK`),
no por letra: Windows no garantiza que el mismo pendrive reciba siempre la misma
letra, y un respaldo configurado contra `E:` empieza a copiar en el disco
equivocado el día que `E:` es otra cosa.

**Con el USB desconectado el respaldo local se completa y se verifica igual.**
El código de salida lo distingue:

| Código | Significado |
|---|---|
| `0` | Local **y** réplica externa correctos |
| `10` | Local correcto, **réplica no hecha** (3-2-1 incompleta) |
| `1`–`5` | Fallo del respaldo |

**Cifra el USB con BitLocker To Go.** Es el medio más fácil de robar de toda la
instalación. El cifrado de la F41 protege correos y teléfonos, pero **nombre,
apellido, pedidos e importes van en claro** en el respaldo. El cifrado de
columnas y el de volumen resuelven problemas distintos y no se sustituyen.

### Tareas programadas

Registradas en `\MarathonSports\`, corriendo como `SYSTEM`:

| Tarea | Horario |
|---|---|
| `Marathon_Respaldo_Full` | domingos 23:00 |
| `Marathon_Respaldo_Diferencial` | lunes a sábado 22:00 |
| `Marathon_Respaldo_Aplicacion` | domingos 23:30 |
| `Marathon_Verificar_Respaldos` | diario 08:00 |

`verificar_respaldos.ps1` **avisa por ausencia**: el fallo más peligroso no es
que un respaldo dé error —eso se ve en el log— sino que el job **deje de
ejecutarse** y nadie lo note, porque entonces no hay ningún error que mirar.

### Restaurar

```powershell
# ensayo (no destructivo). OJO: pasar siempre un puerto distinto de 5433
powershell -File scripts\backup\restaurar.ps1 -Modo Prueba -PuertoPrueba 5434

# desde el disco externo
powershell -File scripts\backup\restaurar.ps1 -Modo Prueba -Desde Secundario -PuertoPrueba 5434

# desastre real (DESTRUCTIVO)
powershell -File scripts\backup\restaurar.ps1 -Modo Produccion -Confirmar
```

**RTO medido: 15 s** (objetivo 120 min), restaurando 165.000 pedidos y 450.000
detalles desde el destino secundario. El modo Producción no borra el directorio
actual: lo renombra con sufijo `.reemplazado_<fecha>` para poder volver atrás.

---

## 7. Rendimiento

Detalle en `OPTIMIZACION_CONSULTAS_V2.md` y `CONFIGURACION_BD.md`.

### El protocolo de medición

`EXPLAIN (ANALYZE, BUFFERS)`, **3 ejecuciones, se reporta la 3.ª** (caché
caliente). Las 18 consultas del catálogo están **extraídas del código**, no
inventadas: cada una cita la clase Java y el método del que sale.

> **El ruido medido entre corridas idénticas es del 12,7 %.** Nada por debajo de
> eso se llama mejora ni degradación. Es lo que evita confundir varianza con
> resultado — y ya nos salvó una vez de reportar como regresión una lectura
> atípica de 140 ms que al repetirse volvió a 35 ms con el mismo plan.

### Parámetros ajustados

| Parámetro | Valor | Por qué |
|---|---|---|
| `random_page_cost` | 1.1 | SSD: el acceso aleatorio no cuesta 4× el secuencial |
| `effective_cache_size` | 12 GB | ~75 % de la RAM |
| `log_min_duration_statement` | 20 ms | ~10× el p99 medido (2,083 ms); captura el 0,55 % del tráfico |

Los que siguen en su valor por defecto (`work_mem`, `wal_level`,
`max_connections`) están así **como decisión medida**, no por omisión.

### Reglas arquitectónicas que no se rompen

- `pedido.total`, `orden_compra.total`, `cuenta_por_pagar.monto_total` y
  `orden_produccion.costo_total` **los mantienen triggers**: nunca se escriben
  fila a fila.
- `detalle_pedido.subtotal` y `cuenta_por_pagar.saldo_pendiente` son
  `GENERATED`.
- `usuario.password` siempre pre-hasheado BCrypt, **exactamente 60 caracteres**.
- Todo cambio de stock requiere `SET LOCAL app.current_user_id`.
- **`ddl-auto` permanece en `validate`.** Es el detector de que las entidades y
  el esquema siguen alineados. Si falla, es un hallazgo: se investiga, no se
  tapa bajando la validación.
- **El PostgreSQL del puerto 5433 es un respaldo congelado: no se toca jamás.**

---

## 8. Pruebas

Cinco arneses, 227 comprobaciones. **Todos son seguros de ejecutar sin banderas.**

| Arnés | Qué comprueba | Resultado |
|---|---|--:|
| `fase34_pruebas_roles.sql` | Privilegios por rol | 61/61 |
| `fase40_pruebas_auditoria.sql` | Auditoría e inmutabilidad | 29/29 |
| `fase41_pruebas_cifrado.sql` | Cifrado, clave, vías laterales | 51/51 |
| `scripts\fase37_pruebas_endpoints.ps1` | Acceso por rol contra la API real | 66/66 |
| `scripts\fase37_pruebas_navbar.ps1` | Navegación por rol | 20/20 |

Más `fase38_1_cierre_verificacion.sql`: **6 invariantes** de recálculo y
**238 comprobaciones** de integridad estructural.

```powershell
# SQL (los tres primeros)
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -h localhost -U postgres -d mod_venta_inve -f marathon-backend\sql\fase34_pruebas_roles.sql

# el de cifrado necesita la clave
powershell -File scripts\cifrado\gestionar_clave.ps1 -Accion Ejecutar -Script marathon-backend\sql\fase41_pruebas_cifrado.sql

# los de endpoints exigen el backend arriba
powershell -File scripts\fase37_pruebas_endpoints.ps1
```

> **Por qué son seguros.** Los tres arneses SQL abren `BEGIN` y terminan en
> `ROLLBACK`. `fase38_1` **no** lleva `ROLLBACK` a propósito: su etapa 2 es una
> reparación deliberada que se omite sobre una base sana («ETAPA 2 OMITIDA»).
>
> Esto no siempre fue así, y la historia importa — ver §10.

---

## 9. Los 8 requisitos

| # | Requisito | Estado | Resuelto en |
|---|---|:--:|---|
| 1 | Usuarios PostgreSQL | ⚠️ **por diseño** | F37 |
| 2 | Roles PostgreSQL | ✅ | F34 |
| 3 | Privilegios sobre objetos | ✅ | F34 · F41 |
| 4a | Seguridad — respaldos | ✅ | F35 · F42 |
| 4b | Seguridad — cifrado | ✅ | F41 · F42 |
| 5 | Configuración | ✅ | F36 · F39 |
| 6 | Optimización | ✅ | F33 · F39 · F41 |
| 7 | Volumen (1 millón) | ✅ | F38 · F38.1 |
| 8 | Auditoría | ✅ | F40 |

**El requisito 1 en una frase:** hay una cuenta de PostgreSQL **por rol**, no por
persona. Es el estándar de la industria —ninguna aplicación con pool abre una
conexión por persona— y no degrada el control de acceso, porque los privilegios
se otorgan al rol de todos modos. La atribución individual, que es lo que el
requisito persigue de verdad, la da `auditoria_cambios.usuario_app`. Argumentado
con evidencia en `AUDITORIA_FINAL_8_REQUISITOS.md` §1.

---

## 10. Cuando algo falla

Todas estas ya nos costaron tiempo una vez.

### Aplicación

| Síntoma | Causa | Solución |
|---|---|---|
| Los correos y teléfonos salen vacíos | El backend arrancó sin la clave | Arrancar con `iniciar_backend.ps1` |
| `permiso denegado a la tabla log_accion` con `INSERT` concedido | Hibernate añadió `RETURNING`, que exige `SELECT` | `INSERT` nativo sin `RETURNING` (ya aplicado) |
| Un `GRANT UPDATE(columna)` no surte efecto | Hibernate emite `UPDATE` con todas las columnas | `@DynamicUpdate` en la entidad |
| El backend no arranca tras cambiar el esquema | `ddl-auto=validate` detectó desalineación | **Es un hallazgo.** Arreglar el mapeo, no bajar la validación |
| Cambié el `.env` y no surte efecto | El perfil `local` pisa el `.env` | Editar `application-local.properties` |

### Base de datos

| Síntoma | Causa | Solución |
|---|---|---|
| `ALTER SYSTEM` falla por el MCP | El MCP envuelve todo en una transacción | Usar `psql -f` |
| `pgaudit` no existe | No está disponible en esta instalación | Registro nativo; ver `DECISION_PGAUDIT.md` |
| Un `SELECT` sobre columna cifrada devuelve `\x c30d...` | No hay clave en la sesión | Es el comportamiento correcto |

### PowerShell y Windows

| Síntoma | Causa | Solución |
|---|---|---|
| `El token '||' no es un separador válido` | Archivo UTF-8 **sin BOM**: PS 5.1 lo lee como ANSI y convierte los guiones largos en comillas tipográficas | Guardar con BOM, o usar solo ASCII |
| Un here-string `@"…"@` no parsea | Saltos de línea LF | Usar CRLF, o cadenas de una línea |
| `openssl` aborta con una línea de puntos | Escribe su progreso en **stderr**, y `ErrorActionPreference='Stop'` lo vuelve excepción | Bajar la preferencia durante la llamada y decidir por el resultado |
| `icacls` dice «no se efectuó ninguna asignación» | Windows en español: el grupo se llama «Administradores» | Usar SID (`*S-1-5-32-544`), invariantes al idioma |
| `Start-Process -Verb RunAs` no acepta `-RedirectStandardOutput` | Conjuntos de parámetros incompatibles | Que el propio script elevado haga `*>&1 \| Out-File` |
| La salida de un proceso elevado se pierde | Su ventana se cierra al terminar | Igual que arriba |
| `pg_ctl start` cuelga el script | El servidor hereda el descriptor de salida del padre | Redirigir a archivos con `Start-Process -Wait` |

### La historia que conviene recordar

**Un arnés de pruebas que decía no tocar datos, los borraba.**

`fase40_pruebas_auditoria.sql` declaraba «NO MODIFICA DATOS» y terminaba en
`ROLLBACK`. Pero **nunca abría transacción**, y psql trabaja en autocommit: cada
sentencia de nivel superior —incluido cada bloque `DO`— se confirmaba sola, así
que el `ROLLBACK` final no tenía nada que revertir.

Por cada ejecución del arnés:

- el administrador quedaba `estado='inactivo'` y no podía iniciar sesión;
- su contraseña quedaba en 61 caracteres terminados en `AAA` (BCrypt son
  exactamente 60), con lo que el login era irrecuperable;
- se borraba una fila de `rol_permiso`, distinta en cada corrida.

Se descubrió porque el login de administrador falló en una prueba funcional. **La
reparación se hizo con los datos que la propia `auditoria_cambios` había
registrado** — que es exactamente para lo que sirve una bitácora de cambios.

Tres lecciones que valen para cualquier proyecto:

1. **Un bloque plpgsql solo abre subtransacción si tiene cláusula `EXCEPTION`.**
   El ayudante `pg_temp.probar` la tenía; los bloques `DO` que se añadieron
   después, no. Ahí estuvo el hueco.
2. **Un comentario que afirma una garantía no es la garantía.** La cabecera decía
   lo correcto durante meses mientras el archivo hacía lo contrario.
3. **Verifica la reversión ejecutando y comparando.** Se comprobó corriendo cada
   arnés varias veces con conteo **exacto** de las 38 tablas antes y después. Un
   `n_live_tup` no habría servido: es un estimador.

---

## 11. Lo que queda

### Decisiones de diseño (no son deuda)

1. **Una cuenta de PostgreSQL por rol, no por persona.** Cambiarlo empeoraría el
   rendimiento sin mejorar el control de acceso.
2. **`nombre`, `apellido` y `usuario.correo` sin cifrar.** §4.
3. **3 filas de `auditoria_cambios` con correos legibles**, escritas antes de que
   el cifrado existiera. No se borran: la tabla es append-only por diseño y
   reescribir la bitácora para tapar un hallazgo sería peor que el hallazgo. Son
   datos de prueba. El arnés exige que ese conjunto **no crezca**.

### Acciones pendientes del usuario

1. **Sacar del equipo la copia de custodia de la clave.** Ya está generada y
   verificada en `C:\Users\dbeni\custodia_marathon\clave_cifrado_marathon.txt`
   (descifra 5.005 de 5.005 correos). Falta copiarla a un gestor de contraseñas
   o unidad extraíble —**que no sea la de los respaldos**— y **borrar el archivo
   del disco**. Mientras siga en `C:`, la clave y los datos que cifra están en el
   mismo equipo.
2. **Conectar un USB con etiqueta de volumen `MARATHON_BK`** y cifrarlo con
   BitLocker To Go.

### Mejoras propuestas, no pedidas

- **Sacar `password` del mapeo de la entidad `Usuario`.** Hoy los cinco roles
  no-admin tienen `SELECT` sobre la tabla `usuario` completa, incluido el hash,
  porque Hibernate materializa la entidad entera.
- **`auditoria_cambios` cubre 5 de 38 tablas.** Las transaccionales no tienen
  auditoría campo a campo; el sobrecoste sobre tablas de alto volumen no se ha
  medido.
- **Retención del registro nativo: 7 días** por la rotación `postgresql-%a.log`.

---

## 12. Trazabilidad

| Fase | Qué hizo |
|---|---|
| **F33** | Índices sobre las consultas críticas |
| **F34** | 6 roles, privilegios por objeto y columna, 61 pruebas |
| **F35** | Respaldos completo + diferencial, verificación, restauración |
| **F36** | Auditoría nativa de PostgreSQL |
| **F37** | Un pool por rol; retirado `CREATEROLE` del administrador |
| **F38** | Poblado a 1.000.000 de filas |
| **F38.1** | Verificación: 6 invariantes, 238 comprobaciones |
| **F39** | Estudio de planes, `CONFIGURACION_BD.md`, ruido del 12,7 % |
| **F40** | `auditoria_cambios` genérica + `log_accion` en 13 servicios |
| **F41** | Cifrado, HMAC para unicidad, TLS, −4 índices |
| **F42** | Regla 3-2-1 con USB, restauración verificada, `verify-full`, arneses reparados, auditoría final |
