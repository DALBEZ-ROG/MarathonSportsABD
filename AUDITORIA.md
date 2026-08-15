# Auditoría — `mod_venta_inve`

Fase 40. Cuatro mecanismos que, juntos, responden para cualquier fila de las
tablas críticas: **quién** la cambió, **cuándo** y **qué valor tenía antes**.

---

## 1. Los cuatro mecanismos

| Mecanismo | Capa | Qué registra | Alcance |
|---|---|---|---|
| **Registro nativo de PostgreSQL** | Servidor | Toda sentencia que modifica datos o esquema, con usuario, origen, aplicación y `xid` | Las 38 tablas |
| **`log_accion`** | Aplicación | Operación de negocio con su descripción legible y el usuario de la aplicación | 22 servicios |
| **`historial_inventario`** | Trigger | Stock anterior y nuevo en cada ajuste | Solo `inventario` |
| **`auditoria_cambios`** | Trigger | **Campo a campo**: valor anterior y nuevo, con los dos usuarios y el `txid` | `usuario`, `rol_permiso`, `producto`, `cliente`, `proveedor` |

Ninguno sobra. El nativo dice *qué sentencia se ejecutó* pero no *qué valor había
antes*. `log_accion` dice *qué hizo una persona* en lenguaje de negocio pero no el
detalle del dato. `auditoria_cambios` da el detalle exacto pero no el contexto de
la acción. Se complementan.

---

## 2. `auditoria_cambios`

### Estructura

| Columna | Tipo | Para qué |
|---|---|---|
| `id` | `bigint IDENTITY` | PK |
| `tabla` | `varchar(63)` | Tabla auditada |
| `pk_valor` | `varchar(100)` | PK de la fila afectada, como texto |
| `operacion` | `varchar(10)` | `INSERT` / `UPDATE` / `DELETE` |
| `campo` | `varchar(63)` | Columna que cambió (`NULL` en INSERT/DELETE) |
| `valor_anterior` | `text` | `NULL` en INSERT |
| `valor_nuevo` | `text` | `NULL` en DELETE |
| `usuario_bd` | `varchar(63)` | `session_user` — la cuenta de PostgreSQL |
| `usuario_app` | `integer` | `app.current_user_id` — la persona |
| `fecha` | `timestamp` | `now()` |
| `txid` | `bigint` | Agrupa los cambios de una misma transacción |

Un `UPDATE` que toca tres campos genera **tres filas**, no un volcado JSON de la
fila entera: una auditoría en la que hay que diffear dos JSON a mano para saber
qué se tocó no responde «qué valor tenía antes» sin trabajo extra. El `txid` es
lo que permite reagruparlas y ver que fueron un mismo acto.

### `usuario_bd` frente a `usuario_app`: por qué hacen falta las dos

Desde la F37 la aplicación se conecta con **una cuenta de PostgreSQL por rol**, no
por persona. Eso deja a cada columna a medias:

- `usuario_bd` (`session_user`) dice **«un operador de bodega»**, no cuál. Si seis
  personas comparten `usr_bodega_marathon`, no identifica a nadie.
- `usuario_app` dice **la persona**, pero solo si la aplicación fijó
  `app.current_user_id` antes de la escritura.

Su **combinación** es lo informativo, y en particular un caso:

> **`usuario_app` nulo con `usuario_bd` presente = un cambio hecho fuera de la
> aplicación.** Por `psql`, por un script de mantenimiento, o por alguien que
> tiene la credencial. Es justo el caso que más le interesa a una auditoría, y
> por eso el nulo no se rellena con un valor por defecto: significa algo.

> **`session_user`, no `current_user`.** La función del trigger es
> `SECURITY DEFINER`, así que dentro de ella `current_user` vale `postgres` y
> registrarlo sería inútil: todas las filas dirían lo mismo. Se detectó en la
> prueba funcional de esta fase, cuando las primeras filas se grabaron con
> `usuario_bd = 'postgres'`.

### Append-only, incluido para el administrador

```sql
REVOKE ALL ON auditoria_cambios FROM PUBLIC;   -- y de los 6 roles
GRANT SELECT ON auditoria_cambios TO rol_administrador;
GRANT SELECT ON auditoria_cambios TO rol_supervisor;
```

| Rol | SELECT | INSERT | UPDATE | DELETE | TRUNCATE |
|---|:--:|:--:|:--:|:--:|:--:|
| `rol_administrador` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `rol_supervisor` | ✅ | ❌ | ❌ | ❌ | ❌ |
| Los 4 roles operativos | ❌ | ❌ | ❌ | ❌ | ❌ |
| `postgres` (dueño) | ✅ | ✅ | ✅ | ✅ | ✅ |

**Una bitácora que el auditado puede editar no es una bitácora.** El
administrador lee su propia auditoría; no la reescribe. Solo `postgres` puede
purgar.

Y sin embargo los seis roles **generan** filas. Eso lo resuelve el
`SECURITY DEFINER` de `fn_auditoria_cambios()`, que corre con los privilegios de
`postgres`. La función fija `SET search_path = public, pg_temp` **dentro de sí
misma**: sin eso, un `SECURITY DEFINER` es una vía de escalada de privilegios
clásica.

### Índices

| Índice | Responde a |
|---|---|
| `idx_auditoria_tabla_pk (tabla, pk_valor)` | «¿qué le pasó a esta fila?» |
| `idx_auditoria_fecha (fecha DESC)` | «¿qué pasó entre estas dos fechas?» |
| `idx_auditoria_usuario (usuario_app) WHERE NOT NULL` | «¿qué hizo esta persona?» |

---

## 3. Qué NO se registra, a propósito

**Columnas de ruido** — `updated_at`, `created_at`, `fecha_actualizacion`. Si se
registrara `updated_at` en cada `UPDATE`, la bitácora se llenaría de filas que no
dicen nada y enterraría las que sí.

**Contraseñas** — `password` y equivalentes se registran como cambiadas, con los
valores enmascarados a `***`. **Un hash BCrypt en la bitácora es material para un
ataque offline**, y la bitácora la leen dos roles. La prueba 15 del arnés lo
verifica: el cambio consta, el hash no aparece.

---

## 4. Sobrecoste medido

1.000 `UPDATE` sobre `producto`, con y sin el trigger, dentro de una transacción
revertida:

| | Tiempo | Por operación |
|---|--:|--:|
| Sin auditoría | 37,1 ms | 0,037 ms |
| Con auditoría | 107,9 ms | 0,108 ms |
| **Sobrecoste** | **+190,7 %** | +0,071 ms |

El porcentaje asusta y el valor absoluto no: **0,07 ms más por escritura**. Y el
contexto importa más que la cifra: el trigger está en cinco tablas de **escritura
poco frecuente** (datos maestros, usuarios, permisos), no en `pedido` ni en
`detalle_pedido`, que son las de alto volumen. Un catálogo de productos recibe
decenas de escrituras al día, no decenas de miles.

Si algún día hiciera falta auditar una tabla transaccional, este número es el que
habría que volver a mirar antes.

---

## 5. Política de retención

**Crecimiento medido:** ~115 bytes por fila (heap), y una fila por **campo
modificado**, no por operación.

| Escenario | Filas/año | Tamaño/año |
|---|--:|--:|
| Conservador (50 cambios/día × 3 campos) | ~55.000 | ~6 MB |
| Intenso (500 cambios/día × 3 campos) | ~550.000 | ~63 MB |

**Política propuesta:**

| Aspecto | Valor |
|---|---|
| Retención en línea | **24 meses** |
| Antes de purgar | Exportar a fichero y guardarlo con los respaldos |
| Quién purga | **Solo `postgres`**. Ningún rol de la aplicación puede borrar |
| Cuándo revisar | Si la tabla supera los 500 MB o el 25 % de la base |

```sql
-- Purga anual, solo como postgres, previa exportación
\copy (SELECT * FROM auditoria_cambios WHERE fecha < now() - interval '24 months') TO 'auditoria_YYYY.csv' CSV HEADER
DELETE FROM auditoria_cambios WHERE fecha < now() - interval '24 months';
```

Sin política, en un año la tabla es el objeto más grande de la base y nadie se
atreve a tocarla.

---

## 6. Lo que NO cubre ninguno de los cuatro

Declarado, no escondido:

1. **Los `SELECT` no se auditan.** `log_statement='mod'` los excluye a propósito:
   los reportes generan miles de lecturas que sepultarían las modificaciones. Si
   hiciera falta saber *quién consultó* datos personales, esto no lo responde.
2. **`auditoria_cambios` cubre 5 de 38 tablas.** Las transaccionales (`pedido`,
   `detalle_pedido`, `movimiento_inventario`…) no tienen auditoría campo a campo;
   tienen `log_accion` y el registro nativo. Fue una decisión de coste: el
   sobrecoste del §4 sobre tablas de alto volumen no se ha medido.
3. **`usuario_app` solo se rellena donde la aplicación lo fija.** Hoy: los tres
   servicios de datos maestros instrumentados y los flujos de inventario. Un
   cambio desde otro punto de la aplicación registrará el rol pero no la persona.
4. **Retención del registro nativo: 7 días**, por la rotación `postgresql-%a.log`.
   Ampliarla exige cambiar el patrón de nombre.
5. **La bitácora no está firmada.** `postgres` puede alterarla. Contra un
   administrador de sistema con acceso al servidor, ningún mecanismo dentro de la
   propia base es suficiente; eso exigiría exportación a un destino externo de solo
   añadido.

---

## 7. Consultas de auditoría habituales

```sql
-- Todo lo que le pasó a un producto
SELECT fecha, operacion, campo, valor_anterior, valor_nuevo, usuario_bd, usuario_app
FROM auditoria_cambios
WHERE tabla = 'producto' AND pk_valor = '14' ORDER BY fecha DESC;

-- Quién cambió precios el último mes
SELECT a.fecha, a.pk_valor, a.valor_anterior, a.valor_nuevo, u.correo
FROM auditoria_cambios a LEFT JOIN usuario u ON u.id_usuario = a.usuario_app
WHERE a.tabla='producto' AND a.campo='precio' AND a.fecha > now() - interval '30 days'
ORDER BY a.fecha DESC;

-- Cambios hechos FUERA de la aplicación (los más interesantes)
SELECT fecha, tabla, pk_valor, campo, usuario_bd
FROM auditoria_cambios WHERE usuario_app IS NULL ORDER BY fecha DESC;

-- Reconstruir una transacción completa
SELECT tabla, pk_valor, campo, valor_anterior, valor_nuevo
FROM auditoria_cambios WHERE txid = 12345 ORDER BY id;

-- Cruce con la bitácora de aplicación
SELECT l.fecha, l.modulo, l.accion, l.descripcion, u.correo
FROM log_accion l LEFT JOIN usuario u ON u.id_usuario = l.id_usuario
WHERE l.fecha > now() - interval '1 day' ORDER BY l.fecha DESC;
```

---

## 8. Hallazgo de la fase: el `RETURNING` invisible

Al incorporar los 13 servicios a `log_accion`, la bitácora dejó de funcionar para
**Operador de Bodega** y **Operador de Pedidos**: `permiso denegado a la tabla
log_accion`, con el privilegio `INSERT` correctamente otorgado y verificado
—`has_table_privilege` devolvía `true` desde la propia aplicación—.

La causa: `id_log` es `GENERATED ALWAYS AS IDENTITY`, así que Hibernate recupera
la clave con `getGeneratedKeys()`, y el driver de PostgreSQL lo implementa
añadiendo un **`RETURNING`** a la sentencia. **`RETURNING` exige `SELECT` sobre la
tabla**, y esos dos roles tienen `INSERT` pero no `SELECT` sobre `log_accion` — a
propósito desde la F34: escriben en la bitácora pero no pueden leerla.

Comprobado aislando la sentencia:

```
INSERT INTO log_accion (...) VALUES (...) RETURNING id_log;   -- ERROR: permiso denegado
INSERT INTO log_accion (...) VALUES (...);                    -- INSERT 0 1
```

Se podría haber otorgado `SELECT`, pero eso les dejaría leer la bitácora entera y
desharía una decisión deliberada de mínimo privilegio. Como `LogService` no
necesita el id generado, se cambió a un `INSERT` nativo sin `RETURNING`.

Es el mismo tipo de hallazgo que el de `@DynamicUpdate` en la F37: **el ORM emite
SQL que el modelo de privilegios no anticipaba**, y solo se ve ejercitando la
aplicación con el rol real.

---

## 9. Verificación

| Prueba | Resultado |
|---|---|
| `fase40_pruebas_auditoria.sql` | **29 / 29** |
| `fase34_pruebas_roles.sql` | **61 / 61** (sin cambios: prueba operaciones, no cuenta tablas) |
| `fase37_pruebas_endpoints.ps1` | **66 / 66** |
| `fase37_pruebas_navbar.ps1` | **20 / 20** |
| Triggers | **29**, todos en `tgenabled='O'` |
| Arranque con `ddl-auto=validate` | ✅ 3,98 s |

`auditoria_cambios` **no se mapea como entidad JPA** a propósito:
`ddl-auto=validate` valida las entidades contra el esquema y no al revés, así que
una tabla sin entidad no rompe el arranque — y mapearla invitaría a que alguien
escribiera en ella desde Java, que es justo lo que el diseño impide.
