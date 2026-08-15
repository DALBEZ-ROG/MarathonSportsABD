# Poblado Masivo a 1.000.000 de Filas — `mod_venta_inve`

Fase 38. Carga de volumen de producción proyectado sobre un esquema con
integridad referencial, triggers de recálculo y columnas generadas.

Scripts:

| Archivo | Papel |
|---|---|
| `marathon-backend/sql/fase38_reconocimiento.md` | Inventario del esquema previo a escribir una sola fila |
| `marathon-backend/sql/fase38_poblado_masivo.sql` | Carga idempotente por lotes |
| `marathon-backend/sql/fase38_correccion_distribuciones.sql` | Corrección de las distribuciones (§6) |
| `marathon-backend/sql/fase38_verificacion_poblado.sql` | `ANALYZE`, invariantes e integridad |

---

## 1. Objetivo y resultado

| Tabla | Antes | Después | Objetivo |
|---|--:|--:|--:|
| `detalle_pedido` | 68 | **450.000** | 450.000 |
| `log_accion` | 131 | **200.000** | 200.000 |
| `pedido` | 25 | **165.000** | 165.000 |
| `movimiento_inventario` | 3 | **80.000** | 80.000 |
| `historial_inventario` | 9 | **60.000** | 60.000 |
| `comprobante_interno` | 0 | **30.000** | 30.000 |
| `orden_compra_detalle` | 10 | **8.000** | 8.000 |
| `cliente` | 40 | **5.000** | 5.000 |
| `inventario` | 267 | **2.000** | 2.000 |
| `orden_compra` | 4 | **2.668** | (ver §4) |

**Total de la base: ~1.100 → 1.003.195 filas. 12 MB → 305 MB.**

---

## 2. Cómo se decidió qué desactivar

El script no supone nada del esquema: el inventario de
`fase38_reconocimiento.md` sale del catálogo. De ahí salieron tres hechos que
condicionaron todo el diseño:

**Las 37 claves primarias son `GENERATED ALWAYS AS IDENTITY`.** No se puede
insertar un valor explícito ni precalcular los identificadores. Los hijos se
enlazan con `INSERT ... SELECT ... FROM padre`, nunca con rangos inventados.

**Los recálculos de total son `FOR EACH STATEMENT`, no `FOR EACH ROW`.** Eso no
los hace inofensivos: un `INSERT` de 50.000 filas dispara una sola ejecución,
pero esa ejecución actualiza el total de todos los pedidos del lote.

**`trg_historial_inventario` es `AFTER UPDATE`, no `AFTER INSERT`.** Decide la
vía para `historial_inventario` (§5).

### Triggers desactivados durante la carga — 5, los mínimos

| Tabla | Trigger | Por qué |
|---|---|---|
| `detalle_pedido` | `trg_recalcular_total_pedido_insert` | Recalcularía el total de todos los pedidos de cada lote |
| `pedido` | `trg_proteger_total_pedido` | **Bloquearía el `UPDATE` agregado** de reconstrucción |
| `pedido` | `trg_recalcular_total_por_descuento` | Recalcularía fila a fila durante ese `UPDATE` |
| `orden_compra_detalle` | `trg_oc_total_insert` | Igual que el primero, para `orden_compra.total` |
| `orden_compra` | `trg_proteger_total_oc` | Bloquearía el `UPDATE` agregado de `orden_compra.total` |

### Triggers que se dejaron encendidos a propósito

- **`trg_validar_total_comprobante`** (`BEFORE INSERT` sobre `comprobante_interno`)
  compara el total del comprobante con el del pedido y aborta si difieren.
  Cargar 30.000 comprobantes con él encendido es una comprobación gratuita de que
  el `UPDATE` agregado quedó bien: si se hubiera equivocado, la carga falla ahí y
  lo delata. **Pasó los 30.000.**
- `trg_historial_inventario`, que no se dispara en `INSERT` y se usa
  deliberadamente en §5.
- Los cinco `updated_at`, que tampoco se disparan en `INSERT`.

Reactivación comprobada contra `pg_trigger.tgenabled = 'O'` al final del script
de carga y otra vez en el de verificación. **Los 24 triggers quedaron activos.**

---

## 3. La fórmula del total

Leída de `fn_recalcular_total_pedido_stmt` y confirmada en
`fn_proteger_total_pedido`:

```
pedido.total = GREATEST( SUM(detalle_pedido.subtotal) - pedido.descuento , 0 )
```

**El descuento se resta.** Se reconstruye con un **único `UPDATE` agregado**
contra `detalle_pedido`, nunca fila a fila, y `detalle_pedido.subtotal` —columna
`GENERATED`— se omite de la lista de columnas del `INSERT`.

Esto tiene una consecuencia para la verificación: comparar `pedido.total` contra
`SUM(subtotal)` a secas daría 24.427 «discrepancias» que no lo son, porque son
justo los pedidos con descuento. Se verifica la fórmula real del sistema.

---

## 4. Desviación deliberada: se crearon órdenes de compra padre

El encargo pedía 8.000 líneas en `orden_compra_detalle` «respetando las órdenes
de compra padre». Sólo existían **4** órdenes. Repartir 8.000 líneas entre 4
padres da 2.000 líneas por orden, que no es un dato realista sino un artefacto
que distorsionaría cualquier medición de agregación por orden.

Se crearon **2.664 órdenes** adicionales, a ~3 líneas cada una. Los estados
salen de `chk_oc_estado` leído del catálogo (`borrador`,
`pendiente_aprobacion`, `aprobada`, `rechazada`, `recibida_parcial`,
`recibida_completa`, `cancelada`) y no de una lista inventada: el primer intento
usó `'recibida'` y `'enviada'`, que la restricción no admite.

---

## 5. Decisión sobre `historial_inventario`: vía (b)

El trigger que alimenta esta tabla es **`AFTER UPDATE`**, y la carga de
`inventario` es un `INSERT`. Generó **0 filas**. Cero no se acerca a 60.000, así
que se eligió la **vía (b): inserción directa**, con un matiz que la mejora:

1. Primero se hace **una pasada real de `UPDATE`** sobre las 2.000 filas de
   `inventario` con el trigger encendido y `app.current_user_id` fijado
   (`set_config(..., true)`, que es `SET LOCAL` desde PL/pgSQL). Generó **2.000
   filas por el mecanismo real**, dejando demostrado que sigue vivo.
2. Las **57.991 restantes** por inserción directa, lo que además permite repartir
   `motivo` entre los 5 valores del `CHECK`: el trigger sólo escribe
   `'actualizacion_stock'`.

---

## 6. Lo que salió mal en la primera pasada, y por qué

La carga terminó en 28,5 s, sin errores, y pasó **todas** las verificaciones de
integridad: 0 discrepancias en el invariante, 70 FK sin huérfanos, 72 `CHECK` sin
violar. Y aun así los datos estaban mal, en una dimensión que ninguna restricción
de la base puede detectar:

| Columna | Primera pasada | Debía ser |
|---|---|---|
| `pedido.fecha_pedido` | **5 fechas distintas** en 165.000 filas | 24 meses de dispersión |
| `pedido.estado` | sólo `entregado` y `pendiente` | 5 estados, 70/10/8/8/4 |
| `orden_compra.fecha_orden` | 2 fechas | dispersa |
| `historial_inventario.motivo` | 1 valor | 5 valores |
| `movimiento_inventario.tipo` | 2 valores | 4 valores |

**Causa raíz.** Un `CROSS JOIN LATERAL` cuya subconsulta no se correlaciona con
la fila externa —del tipo `CROSS JOIN LATERAL (SELECT random() AS x) r`— es una
subconsulta **no correlacionada**, y PostgreSQL la evalúa **una sola vez por
sentencia**. Cada lote de 50.000 filas recibía el mismo valor. Los bloques que
pusieron las expresiones volátiles en el `SELECT` de una subconsulta sobre
`generate_series` (`cliente`, y las *fechas* de `log_accion`,
`movimiento_inventario` e `historial_inventario`) salieron correctos: ahí sí se
evalúan por fila.

> **La lección:** las restricciones de la base garantizan que los datos sean
> *válidos*, no que sean *representativos*. Un millón de filas válidas repartidas
> en cinco fechas no sirve para medir un índice de fecha, que era justamente el
> propósito de esta fase. Por eso la etapa de verificación no se queda en las
> restricciones y comprueba también las distribuciones.

**Corrección.** `fase38_poblado_masivo.sql` quedó arreglado para futuras
ejecuciones, y `fase38_correccion_distribuciones.sql` redistribuyó los datos ya
cargados sin borrar y recargar el millón de filas, preservando dos coherencias
que ya estaban escritas:

- los 30.000 pedidos con comprobante emitido siguen en estado facturable;
- al cambiar el tipo de un movimiento se mueven también sus FK (una salida va
  contra un pedido, una entrada contra un proveedor, un ajuste contra ninguno).

---

## 7. Distribuciones finales

**Nada es uniforme a propósito.** Un dataset plano hace que todos los índices
parezcan igual de buenos.

| Dimensión | Resultado |
|---|---|
| `pedido.estado` | entregado 71,7 % · enviado 11,6 % · procesado 8,6 % · pendiente 5,5 % · anulado 2,6 % |
| `pedido.fecha_pedido` | **731 días distintos** en un rango de 730 días (2024-08-15 → 2026-08-15) |
| Líneas por pedido | media **2,73** (mín. 1, máx. 8) |
| `inventario` en stock bajo | **9,6 %** (`idx_inventario_stock_bajo` es parcial sobre esta condición) |
| `log_accion` por módulo | auth 62,9 % · produccion 16,7 % · compras 8,3 % · devoluciones 5,3 % · resto < 3 % |
| `movimiento_inventario` | salida 45 % · entrada 37 % · ajuste 14 % · **traslado 4 %** |
| `historial_inventario.motivo` | los 5 valores del `CHECK`, con `actualizacion_stock` dominante |
| Clientes y productos | sesgo `power(random(), 2)` y `power(random(), 1.6)`: pocos clientes con muchos pedidos, catálogo con rotación desigual |

Los traslados existen para que `chk_traslado_requiere_destino` y
`chk_traslado_origen_distinto_destino` queden ejercitados por los datos y no
simplemente esquivados.

---

## 8. Verificación

| Comprobación | Resultado |
|---|---|
| `ANALYZE` de las 37 tablas | ✅ ninguna sin estadísticas (antes: 33 de 37) |
| `reltuples` vs `COUNT(*)`, umbral 5 % | ✅ ninguna tabla se desvía |
| **Invariante financiero** | ✅ **165.000 pedidos, 0 discrepancias** |
| `orden_compra.total` | ✅ 0 descuadres |
| FK huérfanas | ✅ 70 restricciones revalidadas con `VALIDATE CONSTRAINT` |
| `CHECK` violados | ✅ 72 evaluadas, 0 violadas |
| Triggers activos | ✅ los 24 en `tgenabled = 'O'` |
| `fase34_pruebas_roles.sql` | ✅ **61 / 61** — el poblado no alteró ningún privilegio |

---

## 9. Restricciones respetadas

- **Cero DDL estructural.** No se creó, alteró ni eliminó ninguna tabla, columna,
  índice, constraint ni tipo. Los `DISABLE/ENABLE TRIGGER` son temporales y
  quedaron revertidos.
- `pedido.total` **nunca** se escribió fila a fila; `detalle_pedido.subtotal` y
  `orden_compra_detalle.subtotal` nunca se escribieron.
- No se crearon usuarios de aplicación: las FK a `usuario` reutilizan los 6
  existentes.
- No se tocó ningún parámetro de `postgresql.conf`. `random_page_cost`,
  `work_mem` y compañía quedan para una fase posterior, a propósito, para poder
  atribuir después qué cambio de plan vino de las estadísticas y cuál del costo.
- Se ejecutó `VACUUM ANALYZE` al final, mantenimiento rutinario tras los `UPDATE`
  masivos de la corrección.

## 10. Cierre F38.1 — verificación completa

Script: `marathon-backend/sql/fase38_1_cierre_verificacion.sql` (idempotente:
reejecutarlo sobre una base sana no modifica una sola fila).

La F38 dejó cuatro puntos sin demostrar: si `orden_compra.total` se había
reconstruido de verdad, si el `ANALYZE` se había corrido *después* de la
redistribución, si la integridad estructural estaba comprobada, y si el
invariante financiero seguía en pie tras mover estados y fechas. Esta fase los
mide todos.

### 10.1 Las cuatro fórmulas de recálculo son distintas entre sí

Leídas una por una con `pg_get_functiondef()`. **No se supuso `SUM(columna)` en
ningún caso**, y con razón:

| Par padre / detalle | Fórmula real | Fuente |
|---|---|---|
| `pedido` / `detalle_pedido` | `GREATEST(COALESCE(SUM(subtotal),0) − descuento, 0)` | `fn_recalcular_total_pedido_stmt` |
| `orden_compra` / `orden_compra_detalle` | `COALESCE(SUM(subtotal), 0)` | `fn_recalcular_total_orden_compra_stmt` |
| `cuenta_por_pagar` / `pago_proveedor` | `COALESCE(SUM(monto), 0)` | `fn_recalcular_monto_pagado_cxp` |
| `orden_produccion` / `orden_produccion_consumo` | `ROUND(COALESCE(SUM(costo_linea),0), 2)` | `fn_proteger_costo_materia_prima_op` |
| `comprobante_interno` / `pedido` | `comprobante.total = pedido.total` | `fn_validar_total_comprobante` |
| `cuenta_por_pagar.saldo_pendiente` | `monto_total − monto_pagado` (`GENERATED`) | catálogo |

`pedido` lleva descuento y suelo en cero; `orden_compra` no lleva ninguno de los
dos; `orden_produccion` **redondea dentro de la fórmula**. Aplicar la de `pedido`
a `orden_compra` habría producido 2.668 falsas discrepancias, y la de
`orden_compra` a `pedido`, 24.427.

**Resultado: 0 discrepancias en los 6 invariantes.** `orden_compra.total` sí se
había reconstruido en la F38. La etapa 2 se omitió por innecesaria, que es
justamente lo que debe hacer un script idempotente.

### 10.2 Integridad estructural — 238 comprobaciones, 0 violaciones

Todo generado dinámicamente desde `pg_constraint`, con las columnas reales de
`conkey`/`confkey`:

| Tipo | Comprobaciones | Violaciones |
|---|--:|--:|
| FK (anti-join `NOT EXISTS`) | 70 | **0** |
| `CHECK` | 72 | **0** |
| PK (duplicados por agrupación) | 37 | **0** |
| `UNIQUE` | 22 | **0** |
| `NOT NULL` | 37 tablas | **0** |

### 10.3 Estadísticas — al final, no antes

`ANALYZE` de las 37 tablas ejecutado **después** de corregir totales y verificar
integridad. `reltuples` vs `COUNT(*)`: **desviación 0,000 % en las 37 tablas**,
ninguna por encima del umbral del 5 %. `last_analyze` no nulo en las 37.

> Un tropiezo que merece quedar anotado: la comprobación de `last_analyze` falló
> dos veces por un defecto **del propio script**, no de la base.
> `pg_stat_user_tables` incluye las tablas **temporales**, y las que este script
> crea para acumular resultados (`_invariantes`, `_integridad`, `_stats`) viven
> en `pg_temp_N` y nunca reciben `ANALYZE`. No se veían desde otra sesión, porque
> las temporales son invisibles fuera de la suya. Se corrigió filtrando por
> `schemaname = 'public'`.

### 10.4 Representatividad

| Dimensión | Resultado |
|---|---|
| `pedido`, dispersión temporal | **731 días distintos** en un rango de 730 |
| `pedido.estado` | 71,72 / 11,61 / 8,57 / 5,46 / 2,64 % |
| Líneas por pedido | media **2,727**, desviación típica **0,946**, rango 1–8 |
| `historial_inventario.motivo` | 70,71 / 14,55 / 7,81 / 4,95 / 1,98 % — los 5 valores del `CHECK` |
| `movimiento_inventario.tipo` | salida 45,08 · entrada 36,86 · ajuste 13,95 · **traslado 4,11** (3.289, todos con destino) |
| `inventario` en stock bajo | 9,60 % |

**Veredicto sobre las 57.991 filas de `historial_inventario` insertadas
directamente** — el punto que más merecía sospecha:

| Origen | Filas | Días distintos | Motivos | Inventarios distintos |
|---|--:|--:|--:|--:|
| Inserción directa | 57.975 | **731** | **5** | 2.000 |
| Trigger real | 2.025 | 1 | 1 | 2.000 |

No son un bloque uniforme: cubren los 731 días del rango, los 5 motivos del
`CHECK` y los 2.000 inventarios. Las 2.025 del trigger tienen un solo día y un
solo motivo, y **eso es correcto**: se generaron en un único `UPDATE` y el
trigger escribe siempre `'actualizacion_stock'`.

### 10.5 `ddl-auto = validate`

Cambiado de `none` a `validate` en `application.properties`. **El backend arranca
limpio** (4,03 s), así que las 37 entidades JPA coinciden con el esquema real y
la regla del proyecto queda cumplida de verdad, no por convención. A partir de
ahora cada arranque es un test de deriva de esquema gratis.

### 10.6 Pruebas tras el poblado

| Prueba | Resultado |
|---|---|
| `fase34_pruebas_roles.sql` | **61 / 61** |
| `fase37_pruebas_endpoints.ps1` | **66 / 66** |
| `fase37_pruebas_navbar.ps1` | **20 / 20** |
| Triggers activos | **24 / 24** en `tgenabled = 'O'` |

Ninguna de las tres baterías se degradó con el millón de filas.

---

## 11. Corrección aplicada fuera del poblado

`usr_admin_marathon` tenía el atributo **`CREATEROLE`**, que contradecía
`SEGURIDAD_ROLES.md` §5 y abría una vía de escalada. Se ejecutó
`ALTER ROLE usr_admin_marathon NOCREATEROLE` y se verificó
(`rolcreaterole = false`). La prueba 61 de la F34 no lo detectaba porque usa
`SET ROLE rol_administrador`, y el atributo estaba en el usuario de login.

Antes de tocar nada se tomó un respaldo completo verificado:
`C:\respaldos\marathon\full\full_20260815_110734` (100 MB, `pg_verifybackup` OK).
