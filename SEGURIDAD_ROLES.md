# Esquema de Seguridad: Roles y Privilegios — mod_venta_inve

Fase 34. Modelo de privilegios sobre las 37 tablas, los 3 requisitos de la
evaluación y las 59 pruebas de acceso permitido/denegado.

> **Actualizado en la F40:** el esquema tiene ahora **38 tablas**. La nueva,
> `auditoria_cambios`, sigue una regla distinta a todas las demás —es
> *append-only* incluso para `rol_administrador`— y está documentada en la
> sección 10 y en `AUDITORIA.md`.

Scripts: `marathon-backend/sql/fase34_seguridad_roles.sql` y
`marathon-backend/sql/fase34_pruebas_roles.sql`.

---

## 1. Estado auditado antes de este trabajo

El documento del equipo decía que *«no existe ningún rol/privilegio otorgado»*.
Verificado contra el catálogo, eso no era cierto: existían 4 roles de grupo y 4
usuarios de login, con 888 filas de privilegios de columna. Pero tenían problemas
reales:

| Hallazgo | Requisito afectado |
|---|---|
| `rol_supervisor` tenía `USAGE` sobre **22 secuencias** siendo de solo lectura | **3.2 incumplido** |
| `rol_operador_bodega` tenía `UPDATE` a nivel de **tabla** sobre `inventario` | **3.3 incumplido** |
| `PUBLIC` conservaba `USAGE` sobre el esquema `public` | **3.4 incumplido** |
| Los privilegios cubrían solo las **20 tablas base F1–F20** | — |
| Las **17 tablas de F21–F29** (compras, devoluciones, manufactura) no tenían ningún privilegio | — |
| Faltaban los roles de **Encargado de Compras** y **Encargado de Producción** | — |
| No existía script: los roles se habían creado a mano | **3.1 incumplido** |

---

## 2. Los 6 roles

Se conserva el prefijo `rol_` ya desplegado. Los roles de grupo son `NOLOGIN`:
son contenedores de privilegios, no cuentas. Quien se conecta son los usuarios
`usr_*_marathon`, que heredan por membresía.

| Rol de grupo | Usuario de login | Rol funcional |
|---|---|---|
| `rol_administrador` | `usr_admin_marathon` | Administrador |
| `rol_supervisor` | `usr_supervisor_marathon` | Supervisor E-Commerce (solo lectura) |
| `rol_operador_bodega` | `usr_bodega_marathon` | Operador de Bodega |
| `rol_operador_pedidos` | `usr_pedidos_marathon` | Operador de Pedidos |
| `rol_encargado_compras` | `usr_compras_marathon` | Encargado de Compras **(nuevo)** |
| `rol_encargado_produccion` | `usr_produccion_marathon` | Encargado de Producción **(nuevo)** |

Los usuarios nuevos se crean con **contraseña aleatoria** y hay que asignarles una:

```sql
ALTER ROLE usr_compras_marathon WITH PASSWORD '<clave>';
```

Deliberadamente no se escriben contraseñas en un script versionado. Si el usuario
ya existe, el script no toca su contraseña.

---

## 3. Los tres requisitos

### 3.1 Script reejecutable

El bloque de limpieza recorre los 6 roles y hace, **en este orden**:

```sql
REVOKE ALL PRIVILEGES ON ALL TABLES    IN SCHEMA public FROM <rol>;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM <rol>;
REVOKE ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public FROM <rol>;
REVOKE ALL PRIVILEGES ON SCHEMA public FROM <rol>;
REVOKE ALL PRIVILEGES ON DATABASE mod_venta_inve FROM <rol>;
REASSIGN OWNED BY <rol> TO postgres;
DROP OWNED BY <rol>;
DROP ROLE <rol>;
```

El `REVOKE` explícito va **antes** del `DROP ROLE`. Sin él, `DROP ROLE` falla con
*«role cannot be dropped because some objects depend on it»* en cuanto el rol
tenga cualquier privilegio. `REASSIGN OWNED` traspasa los objetos que el rol posea
y `DROP OWNED` elimina privilegios y default privileges residuales.

**Verificado: 3 ejecuciones consecutivas, sin errores.**

### 3.2 `rol_supervisor` sin `USAGE` sobre secuencias

`USAGE` sobre una secuencia solo sirve para llamar a `nextval()`, y `nextval()`
solo se usa al insertar. Un rol que no inserta no tiene ninguna razón para poder
avanzar un contador de la base.

No hay nada que revocar porque **no existe la línea que lo otorga**. El script
simplemente no contiene `GRANT ... ON ALL SEQUENCES ... TO rol_supervisor`.

Verificación automática en el script (falla si no se cumple) y prueba funcional:

```
rol_supervisor | REQ 3.2 - nextval() sobre una secuencia | DENEGADO_PRIVILEGIO | PASA
```

Estado final: **0 de 39 secuencias** con privilegio para `rol_supervisor`.

### 3.3 `rol_operador_bodega` con `UPDATE` por columna en `inventario`

```sql
GRANT UPDATE (stock_actual, fecha_actualizacion) ON inventario TO rol_operador_bodega;
-- NO se otorga:  GRANT UPDATE ON inventario TO rol_operador_bodega;
```

El operador ajusta la cantidad en existencia. **No** reasigna a qué producto ni a
qué bodega pertenece la fila: eso cambiaría la identidad del registro y además
violaría el `UNIQUE (id_producto, id_bodega)`. `fecha_actualizacion` se incluye
porque el propio flujo de ajuste la escribe.

Cuatro pruebas cubren el requisito:

```
REQ 3.3 - Ajustar stock_actual        | PERMITIDO           | PASA
REQ 3.3 - Reasignar id_producto       | DENEGADO_PRIVILEGIO | PASA
REQ 3.3 - Reasignar id_bodega         | DENEGADO_PRIVILEGIO | PASA
REQ 3.3 - Cambiar stock_minimo        | DENEGADO_PRIVILEGIO | PASA
```

### 3.4 `PUBLIC` sin privilegios

```sql
REVOKE ALL ON DATABASE mod_venta_inve FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
```

`PUBLIC` es un pseudo-rol al que pertenece **todo** usuario de la instancia. Por
defecto PostgreSQL le da `USAGE` sobre el esquema `public`, así que cualquier
cuenta nueva podría resolver y listar objetos. `CONNECT` y `CREATE` ya estaban
revocados; faltaba `USAGE`.

---

## 4. Política de columnas calculadas como privilegio

La regla de oro del proyecto («las columnas GENERATED y las calculadas por trigger
nunca se escriben desde la aplicación») estaba sostenida solo por convención en el
código. Aquí se expresa además como privilegio de base de datos.

### (a) Columnas `GENERATED` → nunca se otorga `UPDATE`, a ningún rol

Son 8: `detalle_pedido.subtotal`, `orden_compra_detalle.subtotal`,
`factura_compra.total`, `cuenta_por_pagar.saldo_pendiente`,
`orden_produccion.costo_total`, `orden_produccion.costo_unitario_producido`,
`orden_produccion_consumo.merma`, `orden_produccion_consumo.costo_linea`.

El script verifica automáticamente que ningún rol tenga `UPDATE` sobre ellas.

### (b) Columnas calculadas por trigger → `UPDATE` solo para el rol que las necesita

Son 4: `pedido.total`, `orden_compra.total`, `cuenta_por_pagar.monto_pagado`,
`orden_produccion.costo_materia_prima`.

Aquí la defensa efectiva **es el trigger**, no el privilegio: el trigger de
protección rechaza cualquier valor que no sea el recalculado real. El privilegio
permite que el trigger legítimo escriba; no permite falsear. Las pruebas lo
demuestran: `rol_operador_bodega` **tiene** `UPDATE (total)` sobre `pedido` y aun
así no puede falsearlo.

### (c) Roles operativos → solo las columnas de su flujo

| Rol | Tabla | Columnas con `UPDATE` |
|---|---|---|
| `rol_operador_bodega` | `inventario` | `stock_actual`, `fecha_actualizacion` |
| | `detalle_pedido` | `picking_completado`, `cantidad_recogida` |
| | `pedido` | `estado`, `numero_hu`, `transportista`, `region_destino`, `fecha_empaque`, `updated_at`, `total`* |
| `rol_operador_pedidos` | `pedido` | `estado`, `descuento`, `es_pedido_especial`, `tipo_especial`, `nota_especial`, `fecha_limite_entrega`, `updated_at`, `total`* |
| | `detalle_pedido` | `cantidad`, `precio_unitario` |
| `rol_encargado_compras` | `orden_compra` | `estado`, `observaciones`, `updated_at`, `total`* |
| | `orden_compra_detalle` | `cantidad`, `precio_unitario`, `cantidad_recibida` |
| | `materia_prima` | `nombre`, `descripcion`, `id_unidad_medida`, `estado`, `stock_minimo`, `stock_actual`, `costo_unitario_promedio` |
| | `factura_compra` / `cuenta_por_pagar` | `estado` (+ `monto_pagado`*) |
| | `inventario` | `stock_actual`, `fecha_actualizacion` |
| `rol_encargado_produccion` | `orden_produccion` | `cantidad_producida`, `estado`, fechas, costos base |
| | `orden_produccion_consumo` | `cantidad_real` |
| | `materia_prima` | `stock_actual` |
| | `producto` | `origen` |
| | `inventario` | `stock_actual`, `fecha_actualizacion` |

\* Otorgada solo porque la exige un trigger. Ver sección 6.

---

## 5. Otras decisiones de mínimo privilegio

**Nadie recibe `TRUNCATE`, ni el administrador.** `TRUNCATE` no dispara triggers
de fila: un `TRUNCATE detalle_pedido` dejaría `pedido.total` desincronizado sin
que salte ninguna protección. `DELETE` sí los dispara, y es lo que se otorga.

**Nadie recibe `REFERENCES`.** Crear claves foráneas es una operación de esquema, y
el esquema solo se cambia por script versionado.

**Separación de funciones en compras** (regla de negocio 8: quien crea una orden no
puede aprobarla). Se expresa en la base negando a `rol_encargado_compras` el
`UPDATE` sobre `id_usuario_aprobador` y `fecha_aprobacion`: sin esas dos columnas
la aprobación es imposible de completar. Solo `rol_administrador` las tiene.

**`log_accion`: `INSERT` sí, `UPDATE` y `DELETE` no.** Una bitácora que se puede
editar o borrar no es una bitácora. Solo el administrador puede corregirla.

**El administrador no es superusuario:** no puede hacer DDL ni crear roles.

---

## 6. Hallazgo: los triggers corren con los privilegios de quien dispara la sentencia

Este fue el hallazgo más importante, y lo destaparon las pruebas.

Una función de trigger que no es `SECURITY DEFINER` se ejecuta con los privilegios
del **usuario que dispara la sentencia**, no con los del dueño de la tabla.
Consecuencia práctica: para que un rol pueda modificar una tabla hay que otorgarle
también los privilegios que los triggers de esa tabla necesitan **sobre otras
tablas**.

Con el modelo inicial, un operador de bodega **no podía ni marcar un picking**:

```
Marcar picking en un detalle | PERMITIDO | DENEGADO | FALLA
   detalle: permiso denegado a la tabla pedido
```

El `UPDATE` era sobre `detalle_pedido` y el error hablaba de `pedido`, porque
`trg_recalcular_total_pedido_update` ejecuta `UPDATE pedido SET total = ...`.

Los cuatro casos, corregidos en la Parte 9b del script:

| Trigger en… | …escribe en | Privilegio que hubo que añadir |
|---|---|---|
| `detalle_pedido` | `pedido.total` | `UPDATE (total) ON pedido` a bodega y pedidos |
| `inventario` | `historial_inventario` | `INSERT ON historial_inventario` + su secuencia, a bodega, compras y producción |
| `orden_compra_detalle` | `orden_compra.total` | `UPDATE (total) ON orden_compra` a compras |
| `pago_proveedor` | `cuenta_por_pagar.monto_pagado` | `UPDATE (monto_pagado) ON cuenta_por_pagar` a compras |

Esto **no debilita el modelo**: en los cuatro casos el trigger de protección sigue
rechazando cualquier valor que no sea el real. Las pruebas 20, 27, 37 y 40 lo
confirman.

> **Alternativa considerada y descartada:** marcar esas funciones de trigger como
> `SECURITY DEFINER` para que ningún rol necesitara el privilegio. Es más elegante,
> pero implica modificar lógica de las fases F1–F23, que por diseño del proyecto
> solo la F32 tenía permitido tocar. Se prefirió la vía de privilegios, que es
> reversible y no altera el comportamiento existente.

La secuencia de `historial_inventario` se resuelve con `pg_get_serial_sequence()`
en vez de por nombre, porque en esta base hay dos secuencias con nombre parecido
(`historial_inventario_id_historial_seq` y `..._seq1`) y solo una es la de la
columna.

---

## 7. Pruebas de acceso permitido y denegado

`marathon-backend/sql/fase34_pruebas_roles.sql`. **59 pruebas, 59 pasan.**

No modifica datos: cada intento corre en una subtransacción que **siempre** se
revierte, incluso cuando la operación tiene éxito (se fuerza un error centinela).

Usa `SET LOCAL ROLE` en lugar de conectarse con cada usuario, para no necesitar
sus contraseñas. Al cambiar de rol desde una sesión de superusuario, PostgreSQL
evalúa los privilegios con el rol asumido y **no** hereda la condición de
superusuario, así que la prueba es real.

### Tres resultados posibles

Importa **dónde** se detiene cada intento, no solo que se detenga:

| Resultado | Significado |
|---|---|
| `PERMITIDO` | La sentencia se ejecutó |
| `DENEGADO_PRIVILEGIO` | Lo cortó el sistema de privilegios (`SQLSTATE 42501`) |
| `DENEGADO_REGLA_BD` | Lo cortó una regla de la base: columna `GENERATED` (`428C9`) o un trigger de protección (`P0001`) |

La distinción no es cosmética. Un mismo intento puede estar cubierto por una capa
y no por la otra, y conviene saber por cuál. La primera versión del arnés
clasificaba todo error no-de-privilegio como «permitido», lo que producía falsos
fallos y ocultaba el mecanismo real.

### Resumen

| Rol | Pruebas | Pasan |
|---|---|---|
| `rol_administrador` | 7 | 7 |
| `rol_encargado_compras` | 12 | 12 |
| `rol_encargado_produccion` | 9 | 9 |
| `rol_operador_bodega` | 13 | 13 |
| `rol_operador_pedidos` | 8 | 8 |
| `rol_supervisor` | 10 | 10 |
| **Total** | **59** | **59** |

Reparto por capa: **25** intentos detenidos por privilegios, **12** por reglas de
la base.

### Casos representativos

| Rol | Intento | Resultado |
|---|---|---|
| `rol_supervisor` | Leer cualquiera de las 37 tablas | PERMITIDO |
| `rol_supervisor` | Insertar, modificar o borrar cualquier cosa | DENEGADO_PRIVILEGIO |
| `rol_supervisor` | `nextval()` sobre una secuencia | DENEGADO_PRIVILEGIO |
| `rol_operador_bodega` | Ajustar `stock_actual` | PERMITIDO |
| `rol_operador_bodega` | Reasignar `id_producto` / `id_bodega` | DENEGADO_PRIVILEGIO |
| `rol_operador_bodega` | Leer la tabla `usuario` | DENEGADO_PRIVILEGIO |
| `rol_operador_bodega` | Falsear `pedido.total` | DENEGADO_REGLA_BD (trigger) |
| `rol_operador_pedidos` | Crear pedido y agregar líneas | PERMITIDO |
| `rol_operador_pedidos` | Escribir `subtotal` (GENERATED) | DENEGADO_REGLA_BD |
| `rol_operador_pedidos` | Leer facturas de compra | DENEGADO_PRIVILEGIO |
| `rol_encargado_compras` | Crear orden y enviarla a aprobación | PERMITIDO |
| `rol_encargado_compras` | **Firmar la aprobación** | DENEGADO_PRIVILEGIO |
| `rol_encargado_compras` | Registrar un pago a proveedor | PERMITIDO |
| `rol_encargado_compras` | Definir un BOM | DENEGADO_PRIVILEGIO |
| `rol_encargado_produccion` | Crear orden de producción, consumir MP | PERMITIDO |
| `rol_encargado_produccion` | Alterar el costo promedio de MP | DENEGADO_PRIVILEGIO |
| `rol_encargado_produccion` | Escribir `merma` o `costo_total` | DENEGADO_REGLA_BD |
| `rol_administrador` | Gestionar usuarios, aprobar órdenes | PERMITIDO |
| `rol_administrador` | `TRUNCATE detalle_pedido` | DENEGADO_PRIVILEGIO |
| `rol_administrador` | `CREATE TABLE` / `CREATE ROLE` | DENEGADO_PRIVILEGIO |

### Ejecutar

```powershell
psql -U postgres -d mod_venta_inve -f marathon-backend\sql\fase34_seguridad_roles.sql
psql -U postgres -d mod_venta_inve -f marathon-backend\sql\fase34_pruebas_roles.sql
```

Ambos abortan con `RAISE EXCEPTION` si algo no cumple, para que nadie pueda dar
por bueno un despliegue a medias.

---

## 8. Puesta en producción: el modelo ya está en el camino de ejecución

Hasta esta fase la aplicación se conectaba como el superusuario `postgres`, y un
superusuario **ignora todos** estos privilegios. El modelo estaba construido y
verificado, pero no protegía nada porque no estaba en el camino de ejecución.
**Ya lo está.**

### 8.1 Qué se cambió

| Antes | Ahora |
|---|---|
| `spring.datasource.username=postgres` | `usr_admin_marathon` |
| Superusuario: ignora todo GRANT | Hereda `rol_administrador` y queda sujeto al modelo |
| Sin contraseña asignada a los `usr_*` | `usr_admin_marathon` con contraseña asignada |

La contraseña se asignó con `ALTER ROLE`, **no** en un script versionado:

```sql
ALTER ROLE usr_admin_marathon WITH PASSWORD '<clave>';
```

Los archivos tocados son los dos que están en `.gitignore`
(`application-local.properties`, que es el que manda porque el perfil activo es
`local`, y `.env`). Ninguna credencial entra al repositorio.

### 8.2 La credencial de los respaldos va aparte

`pg_basebackup` exige el atributo `REPLICATION`, que `usr_admin_marathon` **no
tiene ni debe tener**: concedérselo a la cuenta que atiende el tráfico web
permitiría a quien la comprometa clonar la base entera. Por eso el `.env` ahora
separa las dos credenciales:

| Clave | Cuenta | La usa |
|---|---|---|
| `DB_USER` / `DB_PASSWORD` | `usr_admin_marathon` | La aplicación (Spring) |
| `PG_SUPERUSER` / `PG_SUPERUSER_PASSWORD` | `postgres` | Los scripts de `scripts/backup` |

`config.ps1` falla con un mensaje explícito si falta `PG_SUPERUSER_PASSWORD`, en
lugar de intentar conectarse con la credencial de la aplicación y morir con un
error de autenticación difícil de leer.

### 8.3 Verificación: se probó, no se supuso

La sección 6 avisaba de que era previsible encontrar privilegios faltantes al
conectar la aplicación de verdad. Por eso no se dio por bueno el cambio hasta
ejecutarlo:

**1. Simulacro del trabajo de la aplicación** como `usr_admin_marathon`, dentro de
una transacción revertida al final:

| Operación | Resultado |
|---|---|
| Lecturas de productos, inventario, pedidos y cuentas por pagar | OK |
| `INSERT` de pedido + detalle (usa `nextval` sobre secuencias) | OK |
| Trigger que recalcula `pedido.total` escribiendo en otra tabla | OK — total 161,97 calculado |
| `UPDATE` de inventario → dispara el `INSERT` en `historial_inventario` | OK — 1 fila escrita |
| `INSERT` en `log_accion` y avance de estado del pedido | OK |

El caso del inventario es el que más privilegios encadena: el `UPDATE` lo hace el
usuario, pero la escritura en `historial_inventario` la hace un trigger **con los
privilegios de quien disparó la sentencia** (el hallazgo de la sección 6). Si a
`rol_administrador` le faltara el `INSERT` sobre esa tabla, el ajuste de stock
fallaría en producción y no en las pruebas de la sección 7.

**2. La aplicación real, arrancada y consultada:**

```
Started MarathonBackendApplication in 4.042 seconds
POST /api/auth/login          -> 200, token emitido
GET  /api/productos           -> 200
GET  /api/inventario          -> 200
GET  /api/pedidos             -> 200
```

**3. El rastro de auditoría de la F36 cerrando el círculo.** El login escribió en
`log_accion` a través de la aplicación, y el registro de PostgreSQL lo atribuyó
correctamente:

```
usuario=usr_admin_marathon base=mod_venta_inve origen=127.0.0.1
app=PostgreSQL JDBC Driver  LOG:  ejecutar <unnamed>: insert into log_accion ...
```

Ya no dice `usuario=postgres`. Las tres fases quedan enlazadas: el usuario
restringido de la F34 opera bajo el modelo de privilegios, y la auditoría de la
F36 lo identifica por nombre.

### 8.4 Lo que quedaba sin hacerse

Una **conexión distinta por rol**, con el backend eligiendo el pool según el rol
del usuario autenticado. Con lo hecho hasta aquí toda la aplicación se conecta
como `usr_admin_marathon`, así que los otros cinco roles siguen sin estar en el
camino de ejecución: un operador de bodega que use la web llega a la base con los
privilegios del administrador, y lo que lo detiene es `SecurityConfig`, no la
base.

> **Cerrado en la fase 37.** Ver la sección 9.

---

## 9. Fase 37 — un pool de conexiones por rol

Script: `marathon-backend/sql/fase37_conexion_por_rol.sql`.
Pruebas: `scripts/fase37_pruebas_endpoints.ps1` y `scripts/fase37_pruebas_navbar.ps1`.

### 9.1 Cómo se enruta

`RoleRoutingDataSource` (un `AbstractRoutingDataSource`) elige el pool leyendo la
autoridad `ROLE_*` del `SecurityContext`. Cada rol funcional tiene su propio
usuario de login en PostgreSQL:

| Rol de la aplicación | Pool | Usuario de base de datos |
|---|---|---|
| Administrador | `administrador` (por defecto) | `usr_admin_marathon` |
| Supervisor E-Commerce | `supervisor` | `usr_supervisor_marathon` |
| Operador de Bodega | `operador-bodega` | `usr_bodega_marathon` |
| Operador de Pedidos | `operador-pedidos` | `usr_pedidos_marathon` |
| Encargado de Compras | `encargado-compras` | `usr_compras_marathon` |
| Encargado de Producción | `encargado-produccion` | `usr_produccion_marathon` |

**Tres momentos usan a propósito el pool por defecto**, porque son trabajo de la
infraestructura de autenticación y no trabajo hecho en nombre de un rol: el
arranque (Hibernate y `DataInitializer`), el login —que tiene que leer la tabla
`usuario` antes de saber quién es quién— y el filtro JWT, que resuelve el token
consultando la base *antes* de poblar el contexto. A ellos se suma el cambio de
la propia contraseña (`RoleRoutingDataSource.conPoolDeAutenticacion`): es un
`UPDATE` sobre `usuario`, y concedérselo a los cinco roles les permitiría cambiar
la contraseña de *cualquier* cuenta, porque un privilegio de base de datos no
distingue «mi fila» de «la fila de otro».

**Falla cerrado:** si un usuario autenticado tiene un rol sin pool configurado,
se lanza una excepción en lugar de caer al pool del administrador. Lo contrario
convertiría un rol mal configurado en una escalada de privilegios silenciosa.

El interruptor `app.datasource.roles.enabled=false` devuelve la aplicación al
comportamiento anterior a esta fase sin tocar código.

### 9.2 El hallazgo: el privilegio por columna no servía de nada con Hibernate

Ninguna entidad tenía `@DynamicUpdate`, y sin ella **Hibernate emite todas las
columnas mapeadas en cada `UPDATE`**, no solo las que cambiaron. Contra un
privilegio otorgado por columna eso no falla a medias: falla siempre.

Los `GRANT UPDATE (columna)` de la F34 —el corazón del requisito 3.3 y de toda
la política de la sección 4— estaban, en la práctica, denegando el flujo entero
a los roles operativos. Nunca se había notado porque hasta la F36 quien escribía
era el administrador, que tiene la tabla completa.

Se añadió `@DynamicUpdate` a las 15 entidades cuyas tablas tienen privilegios de
`UPDATE` por columna. El registro de PostgreSQL lo confirma sobre un ajuste de
stock real hecho desde la web por un operador de bodega:

```
usuario=usr_bodega_marathon base=mod_venta_inve origen=127.0.0.1
  LOG:  ejecutar <unnamed>: update inventario set stock_actual=$1 where id_inventario=$2
```

Una sola columna. Sin `@DynamicUpdate` la sentencia habría incluido
`id_producto`, `id_bodega` y `stock_minimo`, y la base la habría rechazado.

> **La regla general:** con privilegios por columna hay que otorgar **las
> columnas que emite el ORM**, no las que aparecen en el código Java. El mismo
> principio destapó que un `existsBy...` de Spring Data proyecta la clave
> primaria (`select lm1_0.id_bom from lista_materiales ...`), así que `id_bom`
> tuvo que entrar en el `GRANT` aunque ninguna línea del código la pida.

### 9.3 Privilegios que faltaban, y cómo se decidió cuáles otorgar

Al enrutar por rol, la base pasó a ser el filtro efectivo y aparecieron
discrepancias con `SecurityConfig`. El criterio para resolver cada una:

> Si `SecurityConfig` **nombra al rol**, la responsabilidad está asignada a
> propósito y lo que falta es el privilegio. Si el endpoint solo pedía
> `.authenticated()`, lo que sobra es la laxitud del backend y se corrige allí.

| Discrepancia | Resolución |
|---|---|
| Ningún rol operativo podía leer `usuario`, y 16 servicios lo hacen para atribuir cada operación | `GRANT SELECT` (§9.4) |
| El módulo de devoluciones de cliente (F24) solo tenía escritura para el administrador | Repartido entre Pedidos (registra y reembolsa) y Bodega (inspecciona) |
| El catálogo de productos consultaba `lista_materiales` para la bandera `tieneBom` | `GRANT SELECT (id_bom, id_producto, estado)` — la receta sigue protegida |
| El listado de devoluciones incluye el reembolso | `GRANT SELECT ON reembolso_cliente` a Bodega |
| Las devoluciones a proveedor (F25, módulo de Compras) llegan hasta `detalle_pedido` | `GRANT SELECT ON detalle_pedido` a Compras, que sigue sin ver `pedido` ni `cliente` |
| Compras y Producción veían Pedidos, Clientes y Comprobantes | **Se restringió `SecurityConfig`**, no se ampliaron los `GRANT` |
| Bodega, Pedidos y Producción veían Proveedores | **Se restringió `SecurityConfig`** |

### 9.4 La concesión: `SELECT` sobre `usuario` incluye el hash

La intención era otorgar solo `(id_usuario, nombre, apellido, correo, estado)` y
dejar `password` fuera. No se puede sin cambiar el mapeo: la entidad `Usuario`
mapea `password` como columna normal, así que Hibernate la incluye en
**cualquier** carga de la entidad, incluidos los 36 puntos que solo necesitan
nombre y apellido para armar un DTO.

Queda expuesto un hash bcrypt —diseñado precisamente para resistir su
exposición—, legible solo por quien ya tenga la credencial de uno de esos
usuarios de base de datos, y que la aplicación nunca incluye en una respuesta.
Ninguno de los cuatro roles recibe `INSERT`, `UPDATE` ni `DELETE`.

**Mejora pendiente:** sacar `password` del mapeo de `Usuario` (campo
`@Transient` poblado por una consulta dedicada que solo use el pool de
autenticación) permitiría volver al privilegio por columna. Toca el núcleo de
autenticación y no se hizo en esta fase.

### 9.5 Un `GET` que escribía

`GET /api/cuentas-por-pagar` ejecutaba `actualizarVencidas()` —un `UPDATE`—
antes de listar. Con todo el mundo conectado como administrador pasó
inadvertido desde la F23; en cuanto el Supervisor llegó a la base con su propio
usuario, la base rechazó la escritura y el listado entero devolvía 403.

Un rol de solo lectura no puede recibir `UPDATE` sobre esa tabla sin dejar de
ser de solo lectura, así que quien cedió fue la escritura escondida en la
lectura: ahora solo la ejecutan los roles que además operan las cuentas.

Es el resultado más interesante de la fase: **la conexión por rol funciona como
un detector de operaciones que no declaran lo que hacen.**

### 9.6 Verificación

| Prueba | Resultado |
|---|---|
| `fase34_pruebas_roles.sql` (privilegios, sin la aplicación) | **61 de 61** |
| `fase37_pruebas_endpoints.ps1` (11 endpoints × 6 roles: permitido y denegado) | **66 de 66** |
| `fase37_pruebas_navbar.ps1` (todo enlace del menú de cada rol debe abrir) | **20 de 20** |
| `pg_stat_activity` | seis usuarios distintos conectados a la vez |
| Registro de PostgreSQL (F36) | atribuye cada sentencia a su `usr_*`, no a `usr_admin_marathon` |

### 9.7 Estado de las cuatro capas

La defensa es de cuatro capas —`rolGuard` en el frontend, `SecurityConfig` en el
backend, los triggers y los privilegios de la base— y ahora la cuarta **distingue
un rol de otro**, no solo la aplicación del acceso directo por psql. Un operador
de bodega que use la web llega a `mod_venta_inve` como `usr_bodega_marathon`, y
lo que lo detiene ya no es únicamente `SecurityConfig`.

El frontend no necesitó cambios: sus guards de ruta y su navbar resultaron ser
iguales o **más** restrictivos que la base en todos los casos.

---

## 10. Fase 40 — `auditoria_cambios`, la tabla que nadie puede reescribir

El esquema pasa de 37 a **38 tablas**. La nueva rompe deliberadamente el patrón
de privilegios de todas las demás.

### 10.1 La matriz

| Rol | SELECT | INSERT | UPDATE | DELETE | TRUNCATE |
|---|:--:|:--:|:--:|:--:|:--:|
| `rol_administrador` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `rol_supervisor` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `rol_operador_bodega` | ❌ | ❌ | ❌ | ❌ | ❌ |
| `rol_operador_pedidos` | ❌ | ❌ | ❌ | ❌ | ❌ |
| `rol_encargado_compras` | ❌ | ❌ | ❌ | ❌ | ❌ |
| `rol_encargado_produccion` | ❌ | ❌ | ❌ | ❌ | ❌ |

**En todas las demás tablas `rol_administrador` puede escribir. Aquí no**, y es
el punto entero del diseño: una bitácora que el auditado puede editar no es una
bitácora. El administrador lee su propia auditoría; solo `postgres`, dueño de la
tabla, puede purgarla.

### 10.2 Cómo escriben los seis roles sin tener `INSERT`

`fn_auditoria_cambios()` es **`SECURITY DEFINER`** y propiedad de `postgres`, así
que se ejecuta con sus privilegios. Los roles disparan el trigger, no insertan.

La función fija `SET search_path = public, pg_temp` **dentro de sí misma**: un
`SECURITY DEFINER` sin `search_path` fijo es una vía de escalada de privilegios
conocida —quien pueda crear objetos en el `search_path` del llamante podría
suplantar la tabla de destino—.

### 10.3 Consecuencia para la F34

Las 61 pruebas de `fase34_pruebas_roles.sql` **siguen pasando sin cambios**: ese
arnés prueba operaciones concretas sobre tablas concretas, no cuenta tablas del
esquema. Las pruebas de la tabla nueva viven en `fase40_pruebas_auditoria.sql`
(29 de 29).

### 10.4 Un privilegio que faltaba y no se vio hasta ejercitar la aplicación

`rol_operador_bodega` y `rol_operador_pedidos` tienen `INSERT` pero **no
`SELECT`** sobre `log_accion` — deliberado desde esta fase 34: escriben en la
bitácora y no pueden leerla. Al incorporarlos a `log_accion` en la F40, sus
escrituras fallaban con *permiso denegado* pese a tener el privilegio.

La causa era que Hibernate añade un `RETURNING` para recuperar la clave
`IDENTITY`, y `RETURNING` exige `SELECT`. Se resolvió con un `INSERT` nativo en
`LogService`, **sin otorgar `SELECT`**: la decisión de mínimo privilegio se
mantiene intacta. Detalle en `AUDITORIA.md` §8.
