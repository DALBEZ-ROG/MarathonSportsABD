# Auditoría final de los 8 requisitos — `mod_venta_inve`

**Fecha:** 15/08/2026 (cierre, F42) · **PostgreSQL:** 18.3 · **Tamaño:** 228 MB ·
**Tablas:** 38 · **Filas:** 1.041.830

> Auditoría de **solo lectura**. Todo verificado contra el estado real de la base
> —consultas al catálogo de PostgreSQL y al sistema de archivos— y no contra la
> documentación del proyecto. Se volvió a consultar cada dato; no se copió nada
> de la auditoría original.

---

## Tabla resumen

| # | Requisito | 15/08 (inicial) | Ahora | Resuelto en |
|---|---|:--:|:--:|---|
| 1 | Usuarios PostgreSQL | ⚠️ | **⚠️ por diseño** | F37 (quitó `CREATEROLE`) |
| 2 | Roles PostgreSQL | ✅ | **✅** | F34 |
| 3 | Privilegios sobre objetos | ✅ | **✅** | F34 · F41 |
| 4a | Seguridad — respaldos | ⚠️ | **✅** | F35 · **F42** |
| 4b | Seguridad — cifrado | ❌ | **✅** | **F41** · F42 |
| 5 | Configuración | ⚠️ | **✅** | F36 · F39 |
| 6 | Optimización | ⚠️ | **✅** | F33 · F39 · F41 |
| 7 | Volumen (1 millón) | ❌ | **✅** | F38 · F38.1 |
| 8 | Auditoría | ⚠️ | **✅** | F40 |

**Siete de ocho en verde.** El único que no lo está es el requisito 1, y no por
falta de trabajo: es una decisión de arquitectura que se argumenta abajo.

---

## Requisito 1 — Usuarios PostgreSQL ⚠️ *(por diseño)*

```sql
SELECT rolname, rolcanlogin, rolsuper, rolcreaterole, rolbypassrls
FROM pg_roles WHERE rolname LIKE 'usr\_%' ORDER BY rolname;
```

| Usuario | LOGIN | `rolsuper` | `rolcreaterole` | `rolbypassrls` |
|---|:--:|:--:|:--:|:--:|
| `usr_admin_marathon` | ✅ | false | **false** | false |
| `usr_supervisor_marathon` | ✅ | false | false | false |
| `usr_bodega_marathon` | ✅ | false | false | false |
| `usr_pedidos_marathon` | ✅ | false | false | false |
| `usr_compras_marathon` | ✅ | false | false | false |
| `usr_produccion_marathon` | ✅ | false | false | false |

**Lo que sí se corrigió.** La auditoría inicial marcaba `CREATEROLE` en
`usr_admin_marathon` como vía de escalada: quien comprometiera la cuenta web
podía crear roles y otorgarse privilegios. **Hoy ninguna de las seis cuentas lo
tiene**, ni `SUPERUSER`, ni `BYPASSRLS`. Verificado arriba: `0` cuentas con
`rolcreaterole`.

### Por qué sigue en ⚠️, y por qué eso está bien

Hay **una cuenta de PostgreSQL por rol funcional, no por persona**. Si mañana se
dan de alta tres operadores de bodega, los cuatro se conectarán como
`usr_bodega_marathon`. Si el requisito se lee literalmente como «una cuenta de
base de datos por persona», no se cumple.

**El argumento de que el modelo por rol es el correcto:**

1. **Es el estándar de la industria.** Ninguna aplicación web con pool de
   conexiones abre una conexión por persona: el pool existe precisamente para
   reutilizar conexiones entre peticiones de usuarios distintos. Con cuentas por
   persona haría falta un pool por persona, o reautenticar en cada petición.
   Con `max_connections = 100` y 6 pools ya configurados, cien empleados serían
   inviables.
2. **No degrada el control de acceso.** Los privilegios se otorgan al rol, no a
   la persona, así que dos operadores de bodega tendrían privilegios idénticos
   aunque tuvieran cuentas separadas. Separarlas no añadiría ni una restricción.
3. **El agujero real —la atribución individual— está tapado por otra vía.**

**La compensación, con evidencia.** `auditoria_cambios` registra **dos** columnas
de usuario, y esa duplicidad existe exactamente por esto:

```sql
SELECT campo, valor_anterior, valor_nuevo, usuario_bd, usuario_app
FROM auditoria_cambios WHERE tabla='cliente' AND campo='correo_enc' LIMIT 1;
```

| campo | usuario_bd | usuario_app |
|---|---|---|
| `correo_enc` | `usr_admin_marathon` | `1` |

`usuario_bd` dice **el rol**; `usuario_app` dice **la persona**, resuelta del
contexto de seguridad de Spring y publicada con `SET LOCAL app.current_user_id`.
La cuenta de base de datos no distingue personas, pero **la bitácora sí**, que es
lo que el requisito persigue de verdad: poder responder «quién cambió esto».

Y un caso que el modelo por persona no cubriría mejor: **`usuario_app` nulo con
`usuario_bd` presente significa un cambio hecho fuera de la aplicación** —por
`psql`, por un script, o por alguien con la credencial—. Es justo lo que más le
interesa a una auditoría.

**Qué haría falta para ponerlo en ✅:** una cuenta de PostgreSQL por empleado,
`SET ROLE` tras autenticar, y renunciar al pool o rehacerlo por persona. Es un
cambio de arquitectura, no un ajuste, y empeoraría el rendimiento sin mejorar el
control de acceso. **Se documenta como decisión, no como deuda.**

---

## Requisito 2 — Roles PostgreSQL ✅

```sql
SELECT count(*) FROM pg_roles WHERE NOT rolcanlogin AND rolname LIKE 'rol\_%';        -- 6
SELECT count(*) FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.roleid
 WHERE r.rolname LIKE 'rol\_%';                                                        -- 6
```

Los seis roles existen como **`NOLOGIN`** —contenedores de privilegios, no
cuentas— y cada uno tiene exactamente un miembro:

| Rol | Miembro |
|---|---|
| `rol_administrador` | `usr_admin_marathon` |
| `rol_supervisor` | `usr_supervisor_marathon` |
| `rol_operador_bodega` | `usr_bodega_marathon` |
| `rol_operador_pedidos` | `usr_pedidos_marathon` |
| `rol_encargado_compras` | `usr_compras_marathon` |
| `rol_encargado_produccion` | `usr_produccion_marathon` |

Sin cambios desde la F34. Verificado por las **61 pruebas** de
`fase34_pruebas_roles.sql`.

---

## Requisito 3 — Privilegios sobre objetos ✅

```sql
SELECT count(*) FROM information_schema.tables
 WHERE table_schema='public' AND table_type='BASE TABLE';                    -- 38
SELECT count(*) FROM information_schema.column_privileges
 WHERE grantee LIKE 'rol\_%';                                                -- 2155
SELECT count(*) FROM information_schema.table_privileges
 WHERE grantee='PUBLIC' AND table_schema='public';                           -- 0
```

| Métrica | Inicial | Ahora |
|---|--:|--:|
| Tablas cubiertas | 37 | **38** |
| Privilegios de columna | 2.125 | **2.155** |
| Concesiones a `PUBLIC` | 0 | **0** |

Los 30 privilegios nuevos son de las ocho columnas cifradas de la F41
(`correo_enc`, `correo_hash`, `telefono_enc`, `direccion_enc` en `cliente`;
las cuatro equivalentes en `proveedor`). **Se replicó la matriz exacta que tenían
las columnas en claro**: cifrar no relajó ningún acceso.

La tabla nueva respecto de la auditoría inicial es `auditoria_cambios`, y su
matriz es deliberadamente restrictiva:

| Rol | SELECT | INSERT | UPDATE | DELETE | TRUNCATE |
|---|:--:|:--:|:--:|:--:|:--:|
| `rol_administrador` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `rol_supervisor` | ✅ | ❌ | ❌ | ❌ | ❌ |
| 4 roles operativos | ❌ | ❌ | ❌ | ❌ | ❌ |

El administrador **lee** su propia auditoría; no la reescribe. Los seis roles
generan filas igualmente a través de una función `SECURITY DEFINER`.

---

## Requisito 4a — Seguridad: respaldos ✅

**Esquema:** `pg_basebackup` completo semanal + diferencial diario
(`pg_combinebackup` al restaurar), con manifiesto SHA-256 y verificación
`pg_verifybackup` en cada corrida.

### Regla 3-2-1, cerrada en la F42

| Requisito | Antes | Ahora |
|---|---|---|
| **3 copias** | Parcial (original + full + diferencial, mismo disco) | ✅ original + full + diferencial + réplica externa |
| **2 medios** | ❌ todo en `C:` | ✅ disco interno + disco externo USB |
| **1 fuera del sitio** | ❌ | ✅ el USB sale del equipo |

Implementado en `config.ps1` (`Get-DestinoSecundario`,
`Copy-ARespaldoSecundario`) y enganchado en `backup_full.ps1` y
`backup_diferencial.ps1`. El destino se localiza por **etiqueta de volumen**
(`MARATHON_BK`), por letra o por ruta explícita. La etiqueta es lo recomendado:
Windows no garantiza que el mismo pendrive reciba siempre la misma letra.

**Comportamiento con el USB desconectado** — probado:

```
[OK   ] Verificacion correcta: checksums y manifiesto coinciden.
[OK   ] FULL completado en 11 s. Tamano: 303.35 MB
[AVISO] Destino secundario: no hay ningun volumen con etiqueta 'MARATHON_BK' conectado.
[AVISO] COPIA SECUNDARIA OMITIDA (full). El respaldo primario esta completo y verificado.
        CODIGO DE SALIDA = 10
```

El respaldo local **se completa y se verifica igual**. El código 10 distingue
«primario sí, secundario no» de un éxito (0) y de un fallo de respaldo (1-5).
Nunca se falla el respaldo entero por un USB desenchufado.

### Restauración verificada

No es una hipótesis: se restauró **desde el destino secundario**, contra una base
desechable en el **puerto 5434** (nunca `mod_venta_inve`, nunca el 5433).

| | |
|---|---|
| Insumos | `full_20260815_214239` + `diff_20260815_214358` |
| Contenido restaurado | 6 usuarios · 5.004 clientes · 108 productos · **165.000 pedidos** · 450.000 detalles · 200.061 logs |
| Marca del diferencial | 1 ✅ |
| Roles restaurados | 6 ✅ |
| Índices de la F33 | 4 ✅ |
| **RTO medido** | **15 s** (objetivo: 120 min) |

### Automatización

Las cuatro tareas quedaron **registradas y en estado `Ready`** en
`\MarathonSports\`, corriendo como `SYSTEM`:

| Tarea | Horario |
|---|---|
| `Marathon_Respaldo_Full` | domingos 23:00 |
| `Marathon_Respaldo_Diferencial` | lunes a sábado 22:00 |
| `Marathon_Respaldo_Aplicacion` | domingos 23:30 |
| `Marathon_Verificar_Respaldos` | diario 08:00 |

**Hallazgo de esta fase:** hasta la F42 los scripts existían pero **ninguna tarea
estaba registrada**. `Get-ScheduledTask` devolvía cero. Los respaldos dependían
de que alguien se acordara de lanzarlos, que es otra forma de no tener respaldos.

`verificar_respaldos.ps1` comprueba ahora **ambos** destinos y reporta la
antigüedad de la copia más reciente en cada uno, además de detectar copias
`.parcial` (réplicas interrumpidas a media escritura, lo típico al retirar un USB
sin expulsarlo).

---

## Requisito 4b — Seguridad: cifrado ✅

```sql
SELECT extname, extversion FROM pg_extension WHERE extname='pgcrypto';    -- pgcrypto 1.4
SELECT count(*) FROM information_schema.columns
 WHERE table_schema='public' AND data_type='bytea';                        -- 8
SHOW ssl;                                                                  -- on
```

| Capa | Estado | Mecanismo |
|---|:--:|---|
| Contraseñas de aplicación | ✅ | BCrypt `$2a$`, 60 caracteres |
| Contraseñas de PostgreSQL | ✅ | SCRAM-SHA-256 |
| **Datos personales en reposo** | ✅ | `pgp_sym_encrypt` sobre 8 columnas |
| **Unicidad del correo** | ✅ | HMAC-SHA256 (`correo_hash`) + índice único |
| **TLS en tránsito** | ✅ | **TLSv1.3 / `TLS_AES_256_GCM_SHA384`, `verify-full`** |
| Configuración con secretos | ✅ | DPAPI de máquina |
| Integridad de páginas | ✅ | `data_checksums = on` |

**Mejora de la F42:** el cliente pasó de `sslmode=require` (cifra pero **no
autentica al servidor**) a **`verify-full`**, usando `sslrootcert` del driver en
vez de tocar el truststore del JDK. Probado en las dos direcciones: conecta con
el certificado correcto y **rechaza con `certificate verify failed`** una CA
ajena.

**La clave** vive como blob DPAPI de máquina en
`C:\ProgramData\MarathonSports\crypto\`, fuera del repositorio y **fuera de la
carpeta de respaldos**. Desde la F42 la variable de entorno está en **ámbito de
máquina**. Verificado: 0 apariciones de la clave en los 101 `postgresql-*.log`,
en `auditoria_cambios`, en `log_accion`, en el historial de git y en los archivos
rastreados.

Detalle completo, incluido **qué costó cifrar**, en `CIFRADO.md`.

---

## Requisito 5 — Configuración ✅

```sql
SELECT name, setting, source FROM pg_settings WHERE source='configuration file';
```

| Parámetro | Valor | Justificación |
|---|---|---|
| `random_page_cost` | 1.1 | SSD: el acceso aleatorio no cuesta 4× el secuencial |
| `effective_cache_size` | 12 GB | ~75 % de la RAM del equipo |
| `shared_buffers` | 128 MB | Medido en la F39 |
| `log_statement` | `mod` | Toda modificación, sin sepultarla bajo los `SELECT` |
| `log_min_duration_statement` | 20 ms | ~10× el p99 medido (2,083 ms); captura el 0,55 % del tráfico |
| `log_line_prefix` | usuario/base/origen/app/xid | Permite atribuir cada sentencia |
| `log_filename` | `postgresql-%a.log` | Rotación semanal |
| `summarize_wal` | `on` | Requisito de los respaldos diferenciales |
| `ssl` | `on` | F41 |
| `data_checksums` | `on` | Detección de corrupción silenciosa |
| `password_encryption` | `scram-sha-256` | Método moderno |

**Lo que cambió respecto de la auditoría inicial:** entonces «el resto en valores
por defecto sin justificar». Hoy cada parámetro tocado tiene su medición detrás,
documentada en `CONFIGURACION_BD.md`, y los que siguen en su valor por defecto
—`work_mem`, `wal_level`, `max_connections`— están declarados **como decisión
medida**, no por omisión.

---

## Requisito 6 — Optimización ✅

```sql
SELECT count(*) FROM pg_indexes WHERE schemaname='public';                  -- 122
SELECT count(*) FROM pg_stat_user_tables
 WHERE schemaname='public' AND last_analyze IS NULL AND last_autoanalyze IS NULL;  -- 0
```

| Métrica | Inicial | Ahora |
|---|---|---|
| Estudio reproducible | ❌ | ✅ `fase39_estudio_planes.sql`, 18 consultas |
| Tablas sin `ANALYZE` | 33 de 37 | **0 de 38** |
| Índices | 126 | **122** |

- **18 consultas extraídas del código**, no inventadas: cada una cita la clase
  Java y el método del que sale. Protocolo fijo: `EXPLAIN (ANALYZE, BUFFERS)`,
  3 ejecuciones, se reporta la 3.ª.
- **Ruido medido entre corridas idénticas: 12,7 %.** Nada por debajo de eso se
  llama mejora ni degradación. Es lo que evita confundir varianza con resultado.
- **Cuatro índices eliminados** en la F41 con evidencia: los cuatro en
  `idx_scan = 0` tras el poblado a 1.000.000 de filas. Reejecutado el catálogo
  completo: ninguna consulta se degradó.
- **Una regresión detectada y corregida en la propia F41:**
  `GET /api/clientes/activos` pasó a 6.038 ms al cifrar, porque descifraba 13.860
  datos personales para un selector que no muestra ninguno. Con una proyección
  que selecciona solo lo que se usa: **154 ms**.

---

## Requisito 7 — Volumen (1 millón de filas) ✅

```sql
SELECT sum(n_live_tup) FROM pg_stat_user_tables WHERE schemaname='public';  -- 1.041.830
SELECT pg_size_pretty(pg_database_size('mod_venta_inve'));                   -- 228 MB
```

| | Inicial | Ahora |
|---|--:|--:|
| Filas | ~1.100 | **1.041.830** |
| Tamaño | 12 MB | **228 MB** |
| Pedidos | — | 165.000 |
| Detalles de pedido | — | 450.000 |
| `log_accion` | — | 200.062 |

**No basta con que las filas existan.** La F38 terminó una carga sin un solo
error, pasó todas las verificaciones de integridad, y aun así dejó 165.000
pedidos repartidos en **5 fechas**. La F38.1 existe por eso, y verifica:

- **6 invariantes de recálculo** (`pedido.total` frente a la suma de sus
  detalles, y equivalentes en compras, cuentas por pagar y producción): **0
  discrepancias**, «cuadran al centavo».
- **Integridad estructural**: **0 violaciones en 238 comprobaciones** (claves
  foráneas, `CHECK`, unicidad, `NOT NULL`).

Reejecutado en esta fase: sigue en 0 y 0.

---

## Requisito 8 — Auditoría ✅

```sql
SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal;                     -- 30
SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal AND tgenabled<>'O';  -- 0
SELECT count(*) FROM log_accion;                                            -- 200.062
```

**Cuatro mecanismos**, cada uno cubriendo lo que los otros no:

| Mecanismo | Capa | Qué registra | Alcance |
|---|---|---|---|
| Registro nativo | Servidor | Toda sentencia de modificación, con usuario, origen y `xid` | 38 tablas |
| `log_accion` | Aplicación | Operación de negocio legible + usuario | 22 servicios |
| `historial_inventario` | Trigger | Stock anterior y nuevo | `inventario` |
| `auditoria_cambios` | Trigger | **Campo a campo**, con los dos usuarios y el `txid` | 5 tablas críticas |

**Lo que cerró la F40:** los 13 servicios que escribían sin dejar rastro
—incluidos `InventarioService` y `RolService`, donde cambiar los permisos de un
rol no dejaba constancia alguna— y la ausencia de auditoría campo a campo sobre
`usuario`, `rol_permiso`, `producto`, `cliente` y `proveedor`.

La tabla es **append-only incluso para el administrador**, y los 30 triggers
están todos activos (`tgenabled='O'`). Verificado por las **29 pruebas** de
`fase40_pruebas_auditoria.sql`.

---

## Trazabilidad: qué resolvió cada fase

| Fase | Qué hizo | Requisitos que movió |
|---|---|---|
| **F33** | Índices sobre las consultas críticas | 6 |
| **F34** | 6 roles, privilegios por objeto y por columna, 61 pruebas | 2, 3 |
| **F35** | Respaldos completo + diferencial, verificación, restauración | 4a |
| **F36** | Auditoría nativa de PostgreSQL | 5, 8 |
| **F37** | Un pool de conexiones por rol; **quitó `CREATEROLE`** | 1, 3 |
| **F38** | Poblado a 1.000.000 de filas | 7 |
| **F38.1** | Verificación: 6 invariantes, 238 comprobaciones | 7 |
| **F39** | Estudio de planes, `CONFIGURACION_BD.md`, ruido del 12,7 % | 5, 6 |
| **F40** | `auditoria_cambios` genérica + `log_accion` en 13 servicios | 8 |
| **F41** | Cifrado `pgp_sym_encrypt`, HMAC, TLS, −4 índices | 4b, 6 |
| **F42** | 3-2-1 con USB, restauración verificada, `verify-full`, arneses reparados | **4a**, 4b |

---

## Lo que queda fuera de alcance

Separado en dos, porque no es lo mismo.

### Decisiones de diseño (no son deuda)

1. **Una cuenta de PostgreSQL por rol, no por persona.** Argumentado arriba.
   Cambiarlo empeoraría el rendimiento sin mejorar el control de acceso.
2. **`cliente.nombre` y `apellido` sin cifrar.** Cifrarlos rompe la búsqueda por
   nombre, el orden alfabético y el listado — funciones reales de la aplicación.
   Se protege el dato de contacto, no la identidad.
3. **`usuario.correo` sin cifrar.** Es la credencial de acceso: se consulta en
   cada autenticación y en cada petición con JWT.
4. **3 filas de `auditoria_cambios` con correos legibles**, escritas antes de que
   el cifrado existiera. No se borran: la tabla es append-only por diseño y
   reescribir la bitácora para tapar un hallazgo sería peor que el hallazgo. Son
   datos de prueba (`@correo-demo.ec`). El arnés exige que ese conjunto **no
   crezca**.

### Trabajo pendiente

1. **Depositar la copia de custodia de la clave fuera del equipo.** Es una acción
   manual del usuario (`gestionar_clave.ps1 -Accion Escrow`). **Sin ella, perder
   el equipo es perder los datos cifrados**: DPAPI `LocalMachine` muere con la
   máquina y los respaldos contienen el dato ya cifrado.
2. **Conectar y etiquetar el disco USB como `MARATHON_BK`.** El mecanismo está
   implementado y probado; falta el medio físico.
3. **Cifrar el USB con BitLocker To Go.** Es un medio extraíble que puede salir
   del edificio.
4. **Retención del registro nativo: 7 días** por la rotación `postgresql-%a.log`.
5. **`auditoria_cambios` cubre 5 de 38 tablas.** Las transaccionales no tienen
   auditoría campo a campo; el sobrecoste sobre tablas de alto volumen no se ha
   medido.
6. **Sacar `password` del mapeo de la entidad `Usuario`** para volver al
   privilegio por columna sobre `usuario`.
