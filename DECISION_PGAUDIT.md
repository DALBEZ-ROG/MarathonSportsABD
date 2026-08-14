# Decisión sobre el alcance de la auditoría — pgAudit

> Documento de decisión del grupo. Responde la pregunta que dejó abierta la
> evaluación: **¿el proyecto audita solo a nivel de base de datos, o también las
> acciones de usuario en la aplicación web?**
>
> Implementación asociada: `marathon-backend/sql/fase36_auditoria_nativa.sql`.

---

## 1. Resumen de la decisión

| Pregunta | Decisión |
|---|---|
| ¿Se usa la extensión pgAudit? | **No.** No está disponible en esta instalación (evidencia en §2) |
| ¿Se audita a nivel de base de datos? | **Sí**, con el registro nativo de PostgreSQL (§3) |
| ¿Se audita a nivel de aplicación? | **Sí**, con la tabla `log_accion`, que ya existía desde la F19b (§4) |
| ¿Alcance final del proyecto? | **Las dos capas**, porque ninguna sustituye a la otra (§5) |

---

## 2. Por qué no se usa pgAudit

pgAudit no está instalado ni se puede instalar con `CREATE EXTENSION`. No es que
esté desactivado: **el binario no existe en esta máquina.** Comprobado contra el
catálogo del servidor:

```sql
SELECT count(*) FROM pg_available_extensions WHERE name = 'pgaudit';
-- 0
```

`pg_available_extensions` lista lo que el servidor *podría* instalar, no lo que
está instalado. Un 0 ahí significa que el archivo de control de la extensión no
está en el directorio `share/extension` de la instalación. `CREATE EXTENSION
pgaudit` falla con *"extensión no disponible"*.

La causa es el entorno: el servidor es **PostgreSQL 18.3 sobre Windows**, y el
instalador de EDB para Windows no incluye pgAudit. En las distribuciones de
Linux llega como paquete aparte (`postgresql-18-pgaudit`), pero aquí no hay
paquete equivalente.

Dos obstáculos más, aunque se consiguiera el binario:

1. **Exige reiniciar el servidor.** pgAudit tiene que cargarse en
   `shared_preload_libraries`, que es un parámetro de contexto `postmaster`: no
   basta con recargar la configuración, hay que **detener y arrancar** el
   servicio de base de datos. Toda la estrategia de las fases 35 y 36 se diseñó
   para aplicarse sin ventana de mantenimiento.
2. **Compilarlo desde fuente con MSVC** para una práctica académica es un costo
   desproporcionado frente a lo que aporta por encima del registro nativo.

---

## 3. Qué se implementó en su lugar (capa de base de datos)

El registro nativo de PostgreSQL cubre lo esencial de lo que se le pedía a
pgAudit. Equivalencia directa:

| Lo que pedía el requisito | Con pgAudit | Lo implementado (F36) |
|---|---|---|
| Auditar DDL y escrituras | `pgaudit.log = 'ddl, write'` | `log_statement = 'mod'` |
| Saber **quién** | prefijo del log | `log_line_prefix` con `%u` |
| Saber **desde dónde** | prefijo del log | `%r` (host y puerto) y `%a` (aplicación) |
| Rastro de sesiones | — | `log_connections = 'all'`, `log_disconnections = 'on'` |
| Intentos rechazados | — | `log_min_error_statement = 'warning'` |

Se auditan **todas** las tablas, sin excepción, lo que incluye de sobra las
sensibles del requisito (`inventario`, `pedido`, `usuario`, `cuenta_por_pagar`).

Una línea real del registro, tomada de la verificación:

```
2026-08-13 22:30:01.446 -05 [3704] usuario=postgres base=mod_venta_inve
origen=::1(52647) app=psql xid=0 LOG:  sentencia: CREATE TEMP TABLE ...
```

**Decisión deliberada: no se registran los `SELECT`.** Los reportes del sistema
generan miles de lecturas y ahogarían el registro, dejando lo que importa —las
modificaciones— sepultado entre ruido. Si en el futuro hiciera falta auditar
lecturas sobre una tabla concreta, esa es justamente la capacidad que pgAudit
tiene y el registro nativo no (§6).

**Dónde y cuánto:** archivos en `C:\Program Files\PostgreSQL\18\data\log`, con
un archivo por día de la semana (`postgresql-%a.log`) y truncado al rotar. La
retención efectiva es de **7 días**, sin proceso de limpieza: el archivo del
martes se sobrescribe el martes siguiente.

---

## 4. La capa de aplicación: `log_accion`

pgAudit —y el registro nativo— son extensiones **de PostgreSQL**, no de la
aplicación web. No ven clics, ni sesiones de usuario, ni rutas visitadas. Para
eso hace falta una bitácora propia en el backend, y el proyecto **ya la tiene**
desde la F19b: la tabla `log_accion`.

| Columna | Para qué |
|---|---|
| `id_usuario` | Usuario **de negocio** (`usuario.id_usuario`), no el de base de datos |
| `modulo` | Módulo funcional: `auth`, `pedidos`, `compras`, `produccion`… |
| `accion` | Acción realizada: `login`, `prueba_rto`, etc. |
| `descripcion` | Detalle legible del hecho |
| `ip_address` | Origen de la petición web |
| `fecha` | Cuándo |

Estado actual: **85 registros en 9 módulos**, desde el 24/06/2026. La escribe
`LogService.java` y se consulta por `LogController.java`. El índice
`idx_log_modulo_fecha` de la F33 existe precisamente para que esa consulta de
auditoría (filtro por módulo + rango de fechas) resuelva filtro y orden en una
sola pasada.

---

## 5. Por qué hacen falta las dos capas

Esta es la razón de fondo de la decisión, y no es una formalidad:

**La aplicación multiplexa todas las sesiones.** Spring Boot se conecta con un
único usuario de base de datos para atender a todos los usuarios del sistema.
Por tanto:

- **La auditoría de BD sin `log_accion`** ve la sentencia exacta, pero atribuye
  *todo* al mismo usuario de conexión. Sabe que se borró una fila; no sabe qué
  persona la borró.
- **`log_accion` sin la auditoría de BD** sabe qué persona hizo qué, pero solo
  si la acción pasó por la aplicación. **No ve a quien entra por psql, pgAdmin o
  cualquier cliente directo** — que es exactamente el acceso que más importa
  vigilar, porque se salta todos los controles del backend.

Cada capa es ciega justo donde la otra ve. Por eso el alcance del proyecto son
las dos, y no se sustituyen entre sí.

---

## 6. Qué se pierde frente a pgAudit (declarado, no escondido)

| Capacidad de pgAudit | ¿Se tiene? | Comentario |
|---|---|---|
| Auditar DDL y escrituras | Sí | `log_statement = 'mod'` |
| Atribución por usuario y origen | Sí | vía `log_line_prefix` |
| **Filtrado por objeto** (auditar solo ciertas tablas) | **No** | `log_statement` es todo o nada: o se registran todas las escrituras o ninguna |
| **Auditoría selectiva de `SELECT`** (p. ej. solo lecturas de `usuario`) | **No** | Registrar todos los `SELECT` es inviable por volumen |
| **Formato estructurado** de las entradas | **No** | El registro nativo es texto libre; pgAudit emite campos separados, más fáciles de procesar |
| Auditoría por rol (`pgaudit.role`) | **No** | — |

Ninguna de esas tres carencias afecta a los requisitos del proyecto: lo que se
pedía era registrar operaciones sobre tablas sensibles y acciones de roles con
privilegio, y auditar **todas** las escrituras cubre ese conjunto por exceso.

**Si en el futuro se exigiera pgAudit literalmente**, el camino es: migrar el
servidor a Linux (o compilar la extensión con MSVC), añadirla a
`shared_preload_libraries`, **reiniciar el servicio**, `CREATE EXTENSION
pgaudit` y configurar `pgaudit.log = 'ddl, write'`. La decisión aquí es de
entorno, no de criterio: si el binario estuviera disponible, se usaría.

---

## 7. Cómo verificar que la auditoría está activa

```powershell
# 1. Parámetros efectivos en el servidor
psql -U postgres -d mod_venta_inve -c "SELECT name, setting FROM pg_settings
  WHERE name IN ('log_statement','log_line_prefix','log_connections','log_lock_waits');"

# 2. Reaplicar la configuración (es idempotente)
psql -U postgres -d mod_venta_inve -f marathon-backend/sql/fase36_auditoria_nativa.sql

# 3. Ver el rastro del día
Get-Content "C:\Program Files\PostgreSQL\18\data\log\postgresql-$((Get-Date).ToString('ddd')).log" -Tail 40
```

> **Nota sobre la autoverificación del script F36:** comprueba
> `pg_file_settings`, no `pg_settings`. `pg_settings` refleja lo que tiene
> cargado *la sesión actual*, y la señal de `pg_reload_conf()` la reparte el
> postmaster entre sus procesos hijos: el backend que ejecuta el script no la
> procesa hasta terminar la sentencia en curso. Verificar contra `pg_settings`
> ahí daba un fallo falso, con los parámetros ya aplicados en el servidor.

---

## 8. Decisiones y por qué

| Decisión | Motivo |
|---|---|
| Registro nativo en vez de pgAudit | La extensión no existe en esta instalación (evidencia: `pg_available_extensions` = 0) |
| No auditar `SELECT` | Los reportes ahogarían el registro y ocultarían las modificaciones |
| Auditar las 37 tablas, no solo las sensibles | `log_statement` no permite filtrar por objeto; cubrir de más es preferible a cubrir de menos |
| Rotación por día de la semana | Acota la retención a 7 días sin necesidad de un proceso de limpieza |
| Registrar sentencias fallidas (`warning`) | Un intento **rechazado** es lo más interesante de una auditoría de seguridad |
| Conservar `log_accion` además del registro de BD | La base no sabe qué persona está detrás de una sesión de la aplicación |
