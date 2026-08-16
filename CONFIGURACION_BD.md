# Configuración de la Base de Datos — `mod_venta_inve`

Inventario de **todos** los parámetros que se apartan del valor por defecto, con
la razón de cada uno. La auditoría del 15/08/2026 marcó este documento como
inexistente: hasta la F39 solo estaban justificados los de la F36 (auditoría),
y dentro de un script SQL.

**Instancia:** PostgreSQL 18.3 en Windows · servicio `postgresql-x64-18` ·
`localhost:5432` · datos en `C:\Program Files\PostgreSQL\18\data`.

> Existe una segunda instancia en el **puerto 5433** que es un **respaldo
> congelado**. No se toca nunca y no está cubierta por este documento.

---

## 1. Cómo leer esta tabla

`source = configuration file` significa que el valor está en `postgresql.conf` o
en `postgresql.auto.conf` (que es donde escribe `ALTER SYSTEM`). Se puede
reproducir el inventario con:

```sql
SELECT name, setting, unit, source, boot_val
FROM pg_settings WHERE source NOT IN ('default','override') ORDER BY name;
```

---

## 2. Planificador de consultas

Los tres se midieron uno a uno en la F39, con el catálogo de 18 consultas
reales. El detalle está en `OPTIMIZACION_CONSULTAS_V2.md`.

| Parámetro | Defecto | Valor | Por qué |
|---|---|---|---|
| `random_page_cost` | 4 | **1.1** | El disco es un **SSD NVMe** (Kingston SFYRS1000G, verificado con `Get-PhysicalDisk`). El valor 4 modela el coste de un cabezal mecánico buscando pistas, que aquí no existe. **La medición no encontró ninguna mejora**: cero cambios de plan en las 18 consultas. Se aplica porque corrige el modelo de costes frente al hardware real y porque se comprobó que **no degrada nada**, no porque mejorara algo. En Q06 el margen a favor del secuencial se estrecha de 44 % a 19 %, así que el efecto existe pero no llega a cambiar decisiones con este catálogo. |
| `effective_cache_size` | 4 GB | **12 GB** | La máquina tiene **31,76 GB de RAM** (17,22 GB libres en la medición). 4 GB subestima la caché disponible y penaliza los planes por índice. 12 GB es ~38 % de la RAM: conservador, y por debajo de la RAM libre observada. Igual que el anterior: **cero cambios de plan medidos**; se aplica por corrección del modelo. |
| `work_mem` | 4 MB | **4 MB (sin cambio)** | **Aquí sí hay evidencia dura, y dice que no hace falta tocarlo.** En las 18 consultas no hay un solo *spill* a disco: todos los ordenamientos son `quicksort` en memoria y todos los hash usan `Batches: 1`. El mayor consumo observado es de **2.900 kB**, por debajo de los 4 MB disponibles. Subir a 16 MB no cambió ningún plan ni podía hacerlo, porque nada llegaba al límite. |

> Estos tres se dejaron deliberadamente intactos durante las fases 36, 37, 38 y
> 38.1 para que, al medirlos en la F39, cualquier cambio de plan fuera
> atribuible a un solo parámetro y no a una combinación.

---

## 3. Auditoría y registro (F36 + F39)

| Parámetro | Defecto | Valor | Por qué |
|---|---|---|---|
| `logging_collector` | off | **on** | Sin él no hay archivos de log rotados; la salida se pierde. |
| `log_statement` | none | **mod** | Registra toda sentencia que **modifica** datos o esquema (`INSERT`, `UPDATE`, `DELETE`, `TRUNCATE`, `COPY FROM` y DDL). Equivale a lo que se pedía de `pgaudit.log = 'ddl, write'`. Deja fuera los `SELECT` a propósito: los reportes generan miles de lecturas que sepultarían las modificaciones. |
| `log_line_prefix` | `%m [%p] ` | **`%m [%p] usuario=%u base=%d origen=%r app=%a xid=%x `** | El valor original solo daba la hora. Un registro que dice «se borró una fila» sin decir quién no sirve para auditar. Con la conexión por rol de la F37, `%u` es lo que distingue a `usr_bodega_marathon` de `usr_admin_marathon`. |
| `log_connections` | vacío | **all** | Permite reconstruir quién estuvo conectado. |
| `log_disconnections` | off | **on** | La otra mitad del rastro de sesión. |
| `log_min_duration_statement` | −1 | **20 ms** | **Medido, no elegido por convención.** Con 5.987 consultas registradas bajo tráfico real de los 6 roles, el p50 es 0,010 ms, el p95 0,062 ms y el **p99 2,08 ms**. El umbral anterior de 1.000 ms no capturó una sola consulta de la aplicación en tres fases (las 12 que aparecían eran los `DO` de las cargas masivas). 20 ms es ~10× el p99: captura el **0,55 % del tráfico**, incluidas las tres agregaciones del dashboard (28–94 ms), que son las primeras candidatas a degradarse cuando crezca el volumen. 200 ms se descartó porque no habría capturado nada de la aplicación. |
| `log_lock_waits` | off | **on** | Un bloqueo largo es invisible en el tiempo de consulta si no se registra aparte. |
| `log_min_error_statement` | error | **warning** | Baja el listón para que también quede la sentencia que provocó un aviso. |
| `log_filename` | `postgresql-%Y-%m-%d_%H%M%S.log` | **`postgresql-%a.log`** | Rotación por día de la semana: 7 archivos que se reescriben. **Retención efectiva: 7 días.** Es la limitación conocida del esquema; ampliarla exige cambiar el patrón. |
| `log_rotation_age` | 1440 | **1440** (explícito) | Un archivo por día, coherente con `%a`. |
| `log_truncate_on_rotation` | off | **on** | Necesario con `%a`: sin esto, el log del martes siguiente se **añadiría** al del martes anterior. |
| `log_rotation_size` | 10 MB | **0 (desactivado)** | Con rotación por día, rotar además por tamaño rompería la correspondencia un archivo = un día. |
| `log_file_mode` | 0600 | **0640** | Permite que el grupo lea los logs sin darles permiso de escritura. |
| `log_timezone` | GMT | **America/Bogota** | Un rastro de auditoría con hora en otro huso es inútil para reconstruir cuándo pasó algo. |

`pgaudit` **no está disponible** en esta instalación (`pg_available_extensions`
no la lista) y exigiría `shared_preload_libraries`, es decir, reiniciar el
servidor. El análisis completo está en `DECISION_PGAUDIT.md`.

---

## 4. Respaldo y WAL (F35)

| Parámetro | Defecto | Valor | Por qué |
|---|---|---|---|
| `summarize_wal` | off | **on** | **Requisito del respaldo incremental nativo de PostgreSQL 17+.** Sin él, `pg_basebackup --incremental` no puede saber qué bloques cambiaron y el diferencial es imposible. |
| `wal_summary_keep_time` | 14400 | **14400** (explícito, 10 días) | Los resúmenes de WAL deben sobrevivir más que el ciclo de respaldo completo (semanal), o un diferencial se quedaría sin su base. |
| `max_wal_size` / `min_wal_size` | 1 GB / 80 MB | iguales, explícitos | Documentados para que un cambio futuro sea deliberado. |
| `wal_level` | replica | **replica** (defecto) | Suficiente para `pg_basebackup`. **No hay PITR**: `archive_mode` está en `off`. Queda anotado como limitación. |

---

## 5. Memoria y concurrencia

| Parámetro | Defecto | Valor | Por qué |
|---|---|---|---|
| `shared_buffers` | 128 MB | **128 MB** | Sin cambio. Con una base de 191 MB y 31 GB de RAM, el sistema operativo cachea el resto; el estudio de la F39 no encontró ninguna consulta limitada por E/S. Revisar si la base crece por encima de ~1 GB. |
| `max_connections` | 100 | **100** | El backend abre **6 pools** (F37): 10 conexiones el del administrador y 5 cada uno de los cinco pools de rol → **35 como máximo**. Sobra margen. |
| `autovacuum` | on | **on** | Activo. La F39 comprobó que hacía su trabajo: las tablas grandes tenían 0 tuplas muertas antes de compactar. |

---

## 6. Red y autenticación

| Parámetro | Valor | Por qué |
|---|---|---|
| `listen_addresses` | `*` | Escucha en todas las interfaces, pero **`pg_hba.conf` solo admite `127.0.0.1/32` y `::1/128`**, así que en la práctica el acceso es local. Restringirlo a `localhost` sería más coherente; anotado como mejora. |
| `port` | 5432 | La instancia de producción. **5433 es el respaldo congelado.** |
| `password_encryption` | `scram-sha-256` (defecto) | Método moderno. Los seis `usr_*_marathon` tienen su contraseña con SCRAM, verificado en `pg_authid`. |
| `ssl` | **on** *(F41)* | TLS activo con certificado autofirmado. `pg_stat_ssl` confirma **TLSv1.3 / TLS_AES_256_GCM_SHA384** en las conexiones del pool. Fijado con `ALTER SYSTEM` (escribe en `postgresql.auto.conf`, no en `postgresql.conf`), contexto `sighup`: se activó **sin reiniciar el servidor**. Detalle en `CIFRADO.md` §6. |
| `ssl_cert_file` | `server.crt` *(F41)* | Autofirmado, `CN=localhost`, 825 días, con `subjectAltName` para `localhost` e `IP:127.0.0.1`. En el directorio de datos. |
| `ssl_key_file` | `server.key` *(F41)* | Clave privada **sin contraseña**: con contraseña, PostgreSQL la pediría por consola en cada arranque y el servicio no podría iniciarse desatendido. Permisos restringidos a `NT AUTHORITY\NetworkService` (la cuenta del servicio) y Administradores. |

> **El cliente usa `sslmode=require`, no `verify-full`.** El tráfico va cifrado
> pero **el servidor no queda autenticado**: con un certificado autofirmado,
> `verify-full` exigiría distribuirlo como CA a cada cliente. Para `127.0.0.1` es
> una compensación razonable; **si la base se mueve a otro host, hay que
> revisarla**, porque `require` no protege de un intermediario.
>
> Revertir: `scripts\cifrado\configurar_tls.ps1 -Revertir`.

### `pg_hba.conf`

```
local   all             all                                     scram-sha-256
host    all             all             127.0.0.1/32            scram-sha-256
host    all             all             ::1/128                 scram-sha-256
local   replication     all                                     scram-sha-256
host    replication     all             127.0.0.1/32            scram-sha-256
host    replication     all             ::1/128                 scram-sha-256
```

Sin `trust` ni `md5` en ninguna línea. Las de `replication` existen porque
`pg_basebackup` las necesita, y las usa `postgres`, no la cuenta de la
aplicación.

---

## 7. Localización

| Parámetro | Valor | Por qué |
|---|---|---|
| `TimeZone` | `America/Bogota` | Huso del negocio (Ecuador, UTC−5). |
| `DateStyle` | `ISO, DMY` | Formato día/mes/año, el local. |
| `default_text_search_config` | `pg_catalog.spanish` | Búsqueda de texto con lematización en español. |
| `server_encoding` / `client_encoding` | `UTF8` | Necesario para tildes y `ñ` en nombres de producto y cliente. |
| `data_checksums` | **on** | Detecta corrupción silenciosa en disco. Solo se puede activar al crear el clúster. |

---

## 8. Credenciales de la aplicación

No son parámetros del servidor, pero condicionan cómo se conecta:

| Clave | Cuenta | La usa |
|---|---|---|
| `DB_USER` / `DB_PASSWORD` | `usr_admin_marathon` | El pool por defecto de Spring |
| `DB_USER_*` / `DB_PASSWORD_*` | los cinco `usr_*_marathon` | Los pools por rol (F37) |
| `PG_SUPERUSER` / `PG_SUPERUSER_PASSWORD` | `postgres` | `scripts/backup` (`pg_basebackup` exige `REPLICATION`) |

Todo vive en `.env` y `application-local.properties`, **ambos en `.gitignore`**.
`usr_admin_marathon` perdió el atributo `CREATEROLE` en la F38 y no es
superusuario: queda sujeto al modelo de privilegios de la F34.

---

## 9. Cómo cambiar un parámetro

```sql
ALTER SYSTEM SET random_page_cost = 1.1;   -- escribe en postgresql.auto.conf
SELECT pg_reload_conf();                   -- aplica sin reiniciar
SELECT name, setting, source, pending_restart FROM pg_settings WHERE name = '...';
```

Si `pending_restart` sale `t`, el parámetro necesita reinicio del servicio
(`shared_buffers` y `shared_preload_libraries`, entre otros).

> `ALTER SYSTEM` **no se puede ejecutar dentro de una transacción**, así que
> falla a través del MCP `mod-venta-inve`, que las envuelve. Hay que usar
> `psql -c`.
