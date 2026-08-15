# Auditoría de los 8 requisitos — `mod_venta_inve`

**Fecha:** 15/08/2026 · **PostgreSQL:** 18.3 · **Tamaño de la BD:** 12 MB · **Tablas:** 37

> Auditoría de **solo lectura**. Todo verificado contra el estado real de la base
> (consultas al catálogo de PostgreSQL y al sistema de archivos), no contra la
> documentación del proyecto. No se modificó nada.

---

## Tabla resumen

| # | Requisito | Estado | Resumen en una línea |
|---|---|:--:|---|
| 1 | Usuarios PostgreSQL | ⚠️ | 6 cuentas con LOGIN, pero una **por rol**, no por usuario del sistema; `usr_admin_marathon` tiene `CREATEROLE` |
| 2 | Roles PostgreSQL | ✅ | Los 6 roles existen como `NOLOGIN` con su membresía asignada |
| 3 | Privilegios sobre objetos | ✅ | 37 tablas cubiertas, 2.125 privilegios de columna, `PUBLIC` revocado |
| 4 | Seguridad (respaldo + cifrado) | ⚠️ | Respaldos ✅ completos y funcionando; **cifrado de datos ❌ inexistente** |
| 5 | Configuración | ⚠️ | Auditoría y pool bien configurados; el resto en valores por defecto sin justificar |
| 6 | Optimización | ⚠️ | Estudio serio con `EXPLAIN ANALYZE` hecho, pero **no reproducible hoy** y 33/37 tablas sin `ANALYZE` |
| 7 | Volumen (1 millón) | ❌ | **~1.100 filas en toda la base.** Falta el 99,9 % |
| 8 | Auditoría | ⚠️ | Tres mecanismos activos, pero **13 servicios de escritura sin auditar** |

---

## Requisito 1 — Usuarios PostgreSQL ⚠️

Seis cuentas con `LOGIN`, todas con contraseña (SCRAM-SHA-256), ninguna superusuario:

| Usuario | `rolsuper` | `rolcreaterole` | Hereda |
|---|:--:|:--:|---|
| `usr_admin_marathon` | false | **true ⚠️** | `rol_administrador` |
| `usr_supervisor_marathon` | false | false | `rol_supervisor` |
| `usr_bodega_marathon` | false | false | `rol_operador_bodega` |
| `usr_pedidos_marathon` | false | false | `rol_operador_pedidos` |
| `usr_compras_marathon` | false | false | `rol_encargado_compras` |
| `usr_produccion_marathon` | false | false | `rol_encargado_produccion` |

**Dos matices que lo dejan en parcial:**

1. **Es un usuario por rol, no por usuario del sistema.** Hoy hay 6 usuarios en la
   tabla `usuario` y 6 cuentas de PostgreSQL, así que *parece* 1:1 — pero es
   coincidencia: hay exactamente un usuario de aplicación por rol. Si mañana se dan
   de alta tres operadores de bodega más, los cuatro compartirán
   `usr_bodega_marathon`. El modelo por rol es el estándar de la industria y está
   justificado, pero si el requisito pide literalmente una cuenta por persona,
   **no se cumple**.

2. **`usr_admin_marathon` tiene `CREATEROLE`**, y eso contradice la documentación.
   `SEGURIDAD_ROLES.md` §5 afirma «el administrador no es superusuario: no puede
   hacer DDL ni crear roles», y la prueba 61 de la F34 lo confirma… pero esa prueba
   hace `SET ROLE rol_administrador`, que efectivamente no tiene el atributo. El
   atributo está en el **usuario de login**, que es quien realmente se conecta.
   `CREATEROLE` permite crear roles y otorgarles privilegios: es una vía de
   escalada.

```sql
-- evidencia
SELECT rolname, rolcanlogin, rolsuper, rolcreaterole
FROM pg_roles WHERE rolname LIKE 'usr\_%';
```

---

## Requisito 2 — Roles PostgreSQL ✅

Los seis existen, todos `NOLOGIN` (contenedores de privilegios, no cuentas), con su
`GRANT` de rol a usuario:

| Rol de grupo | Miembro |
|---|---|
| `rol_administrador` | `usr_admin_marathon` |
| `rol_supervisor` | `usr_supervisor_marathon` |
| `rol_operador_bodega` | `usr_bodega_marathon` |
| `rol_operador_pedidos` | `usr_pedidos_marathon` |
| `rol_encargado_compras` | `usr_compras_marathon` |
| `rol_encargado_produccion` | `usr_produccion_marathon` |

Script fuente: `marathon-backend/sql/fase34_seguridad_roles.sql` (reejecutable,
verificado en 3 pasadas consecutivas).

```sql
-- evidencia
SELECT g.rolname AS rol_grupo, g.rolcanlogin,
       string_agg(m.rolname, ', ') AS miembros
FROM pg_roles g
LEFT JOIN pg_auth_members am ON am.roleid = g.oid
LEFT JOIN pg_roles m ON m.oid = am.member
WHERE g.rolname LIKE 'rol\_%'
GROUP BY g.rolname, g.rolcanlogin;
```

---

## Requisito 3 — Privilegios sobre objetos ✅

| Rol | Tablas | SELECT | INSERT | UPDATE (tabla) | DELETE | TRUNCATE | REFERENCES |
|---|--:|--:|--:|--:|--:|--:|--:|
| `rol_administrador` | 37 | 37 | 37 | 29 | 37 | **0** | **0** |
| `rol_supervisor` | 37 | 37 | 0 | 0 | 0 | 0 | 0 |
| `rol_encargado_compras` | 26 | 24 | 11 | 0 | 1 | 0 | 0 |
| `rol_operador_bodega` | 23 | 22 | 5 | 0 | 0 | 0 | 0 |
| `rol_encargado_produccion` | 18 | 16 | 6 | 0 | 2 | 0 | 0 |
| `rol_operador_pedidos` | 15 | 14 | 6 | 1 | 1 | 0 | 0 |

Los `UPDATE` de los roles operativos aparecen en 0 a nivel de tabla porque están
otorgados **por columna**: hay **2.125 privilegios de columna** registrados. Nadie
tiene `TRUNCATE` (ni el administrador) ni `REFERENCES`.

**`REVOKE` de `PUBLIC` aplicado y verificado.** Ni el esquema ni la base tienen
entrada para `PUBLIC` en su ACL; todos los grantees son explícitos:

```
schema public:  pg_database_owner=UC/... | rol_administrador=U/... | rol_supervisor=U/... | (los 6)
database:       postgres=CTc/postgres | rol_administrador=c/postgres | (los 6)
```

Verificado además con 61 pruebas SQL de acceso permitido/denegado
(`fase34_pruebas_roles.sql`, 61/61 pasan) y 66 pruebas de endpoint por rol contra
la aplicación real (`scripts/fase37_pruebas_endpoints.ps1`).

```sql
-- evidencia
SELECT grantee, count(DISTINCT table_name) AS tablas,
       count(*) FILTER (WHERE privilege_type='SELECT') AS sel,
       count(*) FILTER (WHERE privilege_type='INSERT') AS ins,
       count(*) FILTER (WHERE privilege_type='UPDATE') AS upd,
       count(*) FILTER (WHERE privilege_type='DELETE') AS del
FROM information_schema.role_table_grants
WHERE table_schema='public' AND grantee LIKE 'rol\_%'
GROUP BY grantee;
```

---

## Requisito 4 — Seguridad ⚠️

### 4a. Respaldos ✅

**No es solo documentación.** Hay 7 scripts en `scripts/backup/`:

| Archivo | Función |
|---|---|
| `backup_full.ps1` | Completo con `pg_basebackup` |
| `backup_diferencial.ps1` | Incremental nativo de PostgreSQL 17+ |
| `backup_aplicacion.ps1` | Código (punto de recuperación Git), config y secretos |
| `restaurar.ps1` | Restauración con medición de RTO |
| `verificar_respaldos.ps1` | Verificación de integridad |
| `registrar_tareas.ps1` | Alta en el Programador de tareas |
| `config.ps1` | Configuración central |

**Y están ejecutándose de verdad.** En `C:\respaldos\marathon` hay **208 MB**:

```
full/full_20260813_213812                                    13/08 21:38
diferencial/diff_20260815_094904_base_full_20260813_213812   15/08 09:49  <- corrió solo
logs/diferencial_20260815_094904.log
```

El diferencial de hoy a las 09:49 se ejecutó por tarea programada, no a mano.
Política documentada en `ESTRATEGIA_RESPALDO.md` (423 líneas): retención 4 semanas,
RPO 24 h, RTO medido 2 h, aborta si el disco baja de 5 GB libres.

**Falta:** la regla 3-2-1 — todo está en el mismo disco que la base (§7 del
documento, pendiente de decisión).

### 4b. Cifrado ❌ — es el hueco más claro de toda la auditoría

| Mecanismo | Estado | Evidencia |
|---|:--:|---|
| Hash de contraseñas de aplicación | ✅ | `usuario.password` = `$2a$...`, longitud 60 → BCrypt correcto |
| Hash de contraseñas de PostgreSQL | ✅ | `SCRAM-SHA-256$...` en `pg_authid`; `password_encryption=scram-sha-256` |
| Cifrado de archivos de configuración | ✅ | DPAPI de máquina en `backup_aplicacion.ps1` |
| Checksums de datos | ✅ | `data_checksums = on` (integridad, no cifrado) |
| **Extensión `pgcrypto`** | ❌ | **No instalada.** `pg_extension` = solo `plpgsql`. Disponible v1.4, nunca se hizo `CREATE EXTENSION` |
| **Columnas cifradas** | ❌ | **Cero.** No hay ninguna columna `bytea` en las 37 tablas |
| **TLS en tránsito** | ❌ | `ssl = off`. La aplicación viaja en claro a la base |
| **Respaldos de BD cifrados** | ❌ | Decisión documentada y razonada, pero sin cifrar |

**Datos personales en claro** (relevante si el requisito menciona protección de
datos sensibles):

- `cliente`: `nombre`, `apellido`, `correo`, `telefono`, `direccion` — 40 filas
- `proveedor`: `nombre`, `contacto`, `correo`, `telefono`, `direccion` — 6 filas
- `usuario`: `correo` en claro (el `password` sí está hasheado)

Lo único protegido hoy son las **credenciales**. Ningún **dato de negocio** está
cifrado.

```sql
-- evidencia
SELECT extname FROM pg_extension;                                    -- solo plpgsql
SELECT count(*) FROM information_schema.columns
  WHERE table_schema='public' AND data_type='bytea';                 -- 0
SELECT left(password,4), length(password) FROM usuario LIMIT 1;      -- $2a$ / 60
SELECT setting FROM pg_settings WHERE name='ssl';                    -- off
```

---

## Requisito 5 — Configuración ⚠️

**Bien configurado y documentado** (todo con `source = configuration file`, es
decir, cambiado a propósito):

| Parámetro | Valor | Nota |
|---|---|---|
| `TimeZone` / `log_timezone` | `America/Bogota` | Ajustado |
| `server_encoding` / `client_encoding` | `UTF8` | Correcto |
| `log_statement` | `mod` | F36 — registra toda modificación |
| `log_line_prefix` | `%m [%p] usuario=%u base=%d origen=%r app=%a xid=%x` | F36 — permite atribución |
| `log_connections` / `log_disconnections` | `all` / `on` | F36 |
| `log_min_duration_statement` | `1000` ms | Detección de consultas lentas |
| `logging_collector` / `log_rotation_age` | `on` / 1440 min | Rotación diaria, retención 7 días |
| `shared_buffers` | 128 MB | Cambiado del default |

**Pool de conexiones** (F37): 6 pools HikariCP, el del administrador con tamaño por
defecto (10) y los 5 de rol con `maximumPoolSize=5`, `minimumIdle=1`. Total máximo
35 conexiones contra `max_connections=100`. Configurado en `DataSourceConfig.java`
y `RoleDataSourceProperties.java`.

**Autenticación:** `pg_hba.conf` usa `scram-sha-256` en todas las líneas, solo
`127.0.0.1` y `::1`. Sin acceso remoto.

**Lo que baja la nota a parcial:**

- `work_mem` = 4 MB, `effective_cache_size` = 4 GB, `maintenance_work_mem` = 64 MB,
  `random_page_cost` = 4 → todos en **default**. `random_page_cost=4` asume disco
  mecánico; en SSD lo habitual es 1.1, y afecta directamente a si el planificador
  elige índice o secuencial (requisito 6).
- `wal_level = replica`, `archive_mode = off` → sin PITR.
- **No existe un documento de configuración de la base.** Los parámetros de la F36
  están justificados dentro de `fase36_auditoria_nativa.sql`, pero no hay un
  `CONFIGURACION_BD.md` que reúna y razone el resto.
- `application.properties` versionado trae `spring.datasource.password=${DB_PASSWORD:1234}`
  — el default `1234` no se usa (manda `application-local.properties`), pero es un
  valor por defecto poco afortunado en un archivo del repositorio.

---

## Requisito 6 — Optimización ⚠️

**Índices:** 122 en total → **63 creados explícitamente** + 59 de constraint
(PK/UNIQUE). Ejemplos: `idx_pedido_estado_fecha`, `idx_pedido_cliente_fecha`,
`idx_inventario_stock_bajo` (parcial, `WHERE stock_actual <= stock_minimo`),
`idx_log_modulo_fecha`.

**Sí se ha corrido `EXPLAIN ANALYZE`, y en serio.** `OPTIMIZACION_CONSULTAS.md`
(246 líneas) documenta un estudio con `EXPLAIN (ANALYZE, BUFFERS)`, 3 ejecuciones
por consulta reportando la 3.ª (caché caliente): 4 índices creados, 3 eliminados
por inútiles, 2 candidatos evaluados y rechazados (GIN de trigramas, índice
cubriente). Evidencia bruta en `marathon-backend/sql/perf/`:
`etapa1_solo_pk.txt` (49 KB), `etapa2_indices_actuales.txt` (49 KB),
`etapa3_indices_candidatos.txt` (46 KB).

**Por qué queda en parcial — y aquí está el vínculo con el requisito 7:**

1. **La medición no se hizo sobre esta base, porque no se puede.** Con 267 filas en
   la tabla más grande, PostgreSQL elige `Seq Scan` siempre y con razón. El estudio
   se hizo en un esquema `perf_lab` con volumen proyectado (600.000 detalles,
   500.000 logs, 200.000 pedidos, 40.000 inventario, 5.000 clientes, 2.000 productos).
2. **`perf_lab` ya no existe.** Verificado: el único esquema en la base es `public`.
   El estudio no es reproducible sin volver a ejecutar `lab_setup.sql`.
3. **33 de 37 tablas nunca han recibido `ANALYZE`** (`reltuples = -1` en la mayoría;
   `usuario` reporta 4 filas cuando hay 6). Sin estadísticas el planificador trabaja
   a ciegas.
4. **Muchos índices con 0 usos**: de los 63, unos 40 tienen `idx_scan = 0`. A este
   volumen no significa que sobren, significa que no se pueden evaluar.

```sql
-- evidencia
SELECT count(*) FILTER (WHERE c.conname IS NULL) AS indices_explicitos,
       count(*) FILTER (WHERE c.conname IS NOT NULL) AS de_constraint
FROM pg_indexes i LEFT JOIN pg_constraint c ON c.conname = i.indexname
WHERE i.schemaname='public';                                    -- 63 / 59

SELECT count(*) FROM pg_stat_user_tables
  WHERE last_analyze IS NULL AND last_autoanalyze IS NULL;      -- 33 de 37

SELECT nspname FROM pg_namespace
  WHERE nspname NOT LIKE 'pg\_%' AND nspname <> 'information_schema';  -- solo public
```

---

## Requisito 7 — Volumen de 1 millón ❌

Conteos **reales** (`COUNT(*)`, no estimaciones del catálogo):

| Tabla transaccional | Filas | | Tabla | Filas |
|---|--:|---|---|--:|
| `inventario` | 267 | | `recepcion_mercancia_detalle` | 7 |
| `log_accion` | 131 | | `orden_compra` | 4 |
| `producto` | 108 | | `solicitud_devolucion` | 3 |
| `detalle_pedido` | 68 | | `solicitud_devolucion_detalle` | 3 |
| `cliente` | 40 | | `movimiento_inventario` | 3 |
| `pedido` | 25 | | `pago_proveedor` | 2 |
| `lista_materiales` | 12 | | `orden_produccion` | 2 |
| `movimiento_materia_prima` | 11 | | `cuenta_por_pagar` | 2 |
| `orden_compra_detalle` | 10 | | `factura_compra` | 2 |
| `materia_prima` | 10 | | `recepcion_mercancia` | 2 |
| `historial_inventario` | 9 | | `devolucion_proveedor` | 1 |
| `orden_produccion_consumo` | 8 | | **`comprobante_interno`** | **0** |

**Total de toda la base: ~1.100 filas. Tamaño: 12 MB.** Estamos al **0,1 %** del
objetivo.

**Estimación de reparto realista** para llegar a 1.000.000 respetando las
proporciones del negocio (~2,7 líneas por pedido, un log por operación):

| Tabla | Filas objetivo | Criterio |
|---|--:|---|
| `detalle_pedido` | 450.000 | ~2,7 líneas por pedido |
| `log_accion` | 200.000 | Una entrada por operación de negocio |
| `pedido` | 165.000 | El eje del volumen |
| `movimiento_inventario` | 80.000 | Movimientos por pedido despachado |
| `historial_inventario` | 60.000 | Lo genera el trigger automáticamente |
| `comprobante_interno` | 30.000 | Uno por pedido facturado |
| `orden_compra_detalle` | 8.000 | |
| `cliente` | 5.000 | |
| `inventario` | 2.000 | producto × bodega |
| **Total** | **~1.000.000** | |

**Hay un activo reutilizable:** `marathon-backend/sql/perf/lab_setup.sql` ya genera
**1.347.000 filas** con distribuciones no uniformes deliberadas (70 % pedidos
entregados, 8 % stock bajo, 15 % productos fabricados). Apunta al esquema
`perf_lab`, no a `public`. Adaptarlo para poblar las tablas reales es **bastante
menos trabajo que escribirlo desde cero**, pero hay que resolver que las tablas
reales tienen FK, triggers de recálculo y columnas `GENERATED` que el laboratorio
no tenía — cargar 450.000 `detalle_pedido` disparará
`trg_recalcular_total_pedido_*` en cada fila.

---

## Requisito 8 — Auditoría ⚠️

**Tres mecanismos activos:**

| Mecanismo | Estado | Evidencia |
|---|:--:|---|
| `log_accion` (auditoría de aplicación) | ✅ | 131 registros en 9 módulos |
| `historial_inventario` (auditoría por trigger) | ✅ | 9 registros; trigger `trg_historial_inventario` sobre `inventario` |
| Registro nativo de PostgreSQL (F36) | ✅ | `log_statement=mod` + prefijo con usuario; atribuye por `usr_*` |

Cobertura de `log_accion` por módulo: `auth` (82), `produccion` (22), `compras`
(11), `devoluciones` (7), `devoluciones_proveedor` (3), `pedidos` (3), `respaldos`
(1), `comprobantes` (1), `empaque` (1).

**Los huecos:**

1. **Solo una tabla tiene trigger de auditoría.** Hay 14 tablas con triggers, pero
   de los ~24 triggers, **solo `trg_historial_inventario` audita**. El resto son de
   integridad (`fn_proteger_total_pedido`), recálculo
   (`fn_recalcular_total_orden_compra_stmt`) o `updated_at`. No hay tabla de
   auditoría genérica.

2. **13 servicios que escriben en la base no registran nada en `log_accion`:**

   `BodegaService`, `CategoriaService`, `CiudadService`, `ClienteService`,
   **`InventarioService`**, `ListaMaterialesService`, `MateriaPrimaService`,
   **`PickingService`**, `ProductoService`, `ProveedorService`, `RolService`,
   `UnidadMedidaService`

   Los dos más graves: **`InventarioService`** (los ajustes de stock quedan en
   `historial_inventario` pero no en la bitácora central) y **`RolService`**
   (cambiar los permisos de un rol no deja rastro en `log_accion`).

3. **Tablas críticas sin auditoría de ningún tipo:** `usuario` (alta, baja, cambio
   de estado), `rol_permiso` (quién cambió qué permiso), `producto` (cambios de
   precio), `cliente`, `proveedor`. Solo tienen `updated_at`, que dice *cuándo* pero
   no *quién* ni *qué valor tenía antes*.

4. `log_min_duration_statement=1000` está activo pero **0 consultas lentas
   registradas** — consecuencia directa del requisito 7: con 1.100 filas nada tarda
   más de 1 segundo.

5. Retención del registro nativo: 7 días por rotación `postgresql-%a.log`.

```sql
-- evidencia
SELECT c.relname, string_agg(t.tgname || ' [' || p.proname || ']', '; ') AS triggers
FROM pg_trigger t
JOIN pg_class c ON c.oid = t.tgrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
JOIN pg_proc p ON p.oid = t.tgfoid
WHERE NOT t.tgisinternal AND n.nspname='public'
GROUP BY c.relname;

SELECT modulo, count(*) FROM log_accion GROUP BY modulo ORDER BY 2 DESC;
```

---

## Lo que falta, priorizado

| # | Trabajo | Req. | Por qué en esta posición | Esfuerzo |
|---|---|:--:|---|---|
| **1** | **Poblar a 1.000.000 de filas** | 7 | Es el único ❌ rotundo, y **desbloquea el 6 y parte del 8**: sin volumen no se puede medir un plan de ejecución ni detectar una consulta lenta. Base reutilizable en `perf/lab_setup.sql`. Ojo con FK, triggers de recálculo y columnas `GENERATED`. | Alto |
| **2** | **Cifrado de datos sensibles** | 4b | El hueco más visible: `pgcrypto` ni siquiera está instalado y no hay una sola columna cifrada. Candidatos: `cliente.correo/telefono/direccion`, `proveedor.*`. Decidir entre `pgp_sym_encrypt` (reversible, para consultar) y hash (irreversible). | Medio |
| **3** | **Quitar `CREATEROLE` a `usr_admin_marathon`** | 1 | Una línea de SQL que cierra una vía de escalada y alinea la base con lo que ya afirma la documentación. **El mejor retorno por esfuerzo de toda la lista.** | Trivial |
| **4** | **`ANALYZE` de las 37 tablas + revisión de `random_page_cost`** | 5, 6 | 33 tablas sin estadísticas dejan al planificador a ciegas. Hacerlo *después* de poblar (#1), no antes. | Bajo |
| **5** | **Ampliar `log_accion` a los 13 servicios sin auditar** | 8 | Prioridad dentro de esto: `InventarioService`, `RolService`, `ProductoService`. | Medio |
| **6** | **Trigger de auditoría genérico sobre tablas críticas** | 8 | Una tabla `auditoria_cambios` (tabla, PK, campo, valor anterior, valor nuevo, usuario, fecha) alimentada por trigger sobre `usuario`, `rol_permiso`, `producto`, `cliente`, `proveedor`. Es la respuesta a «quién cambió este precio». | Medio |
| **7** | **Rehacer el estudio de `EXPLAIN ANALYZE` sobre la base poblada** | 6 | Con #1 hecho, el estudio pasa de laboratorio desechable a medición sobre la base real — y ahí sí se puede decidir qué hacer con los ~40 índices que hoy tienen 0 usos. | Medio |
| **8** | **Documento `CONFIGURACION_BD.md`** | 5 | Reunir y justificar los parámetros; hoy solo los de la F36 están razonados. | Bajo |
| **9** | **Regla 3-2-1 de respaldos** | 4a | Sigue esperando decisión sobre el segundo destino físico. | Bajo (tras decidir) |
| **10** | **TLS entre aplicación y base** | 4b | Menor mientras todo sea `127.0.0.1`; obligatorio si la base se mueve a otro host. | Bajo |

**Sugerencia de secuencia:** #3 y #4 son de un rato y suben dos requisitos de golpe.
#1 es el trabajo grande y debe ir antes que #4, #7 y la parte medible de #8. #2 es
independiente y se puede paralelizar.

---

## Contexto: qué hay hecho antes de esta auditoría

Las fases 33 a 37 están aplicadas y verificadas contra la base:

| Fase | Qué cubre | Documento |
|---|---|---|
| F33 | Optimización de índices y estudio de consultas | `OPTIMIZACION_CONSULTAS.md` |
| F34 | 6 roles, privilegios por objeto y por columna, 61 pruebas | `SEGURIDAD_ROLES.md` §1–7 |
| F35 | Estrategia de respaldo completo + diferencial | `ESTRATEGIA_RESPALDO.md` |
| F36 | Auditoría nativa de PostgreSQL (alternativa a pgAudit) | `DECISION_PGAUDIT.md` |
| F37 | Un pool de conexiones por rol | `SEGURIDAD_ROLES.md` §9 |

Arneses de prueba reutilizables:

- `marathon-backend/sql/fase34_pruebas_roles.sql` — 61 pruebas de privilegios (61/61)
- `scripts/fase37_pruebas_endpoints.ps1` — 11 endpoints × 6 roles (66/66)
- `scripts/fase37_pruebas_navbar.ps1` — enlaces del menú por rol (20/20)
