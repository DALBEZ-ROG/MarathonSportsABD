# Fase 38 · Etapa 1 — Reconocimiento del esquema antes de la carga masiva

Inventario obtenido **del catálogo de PostgreSQL**, no de la documentación. Todo lo
que el script de la etapa 2 asume sobre nombres de columnas, triggers y
restricciones sale de aquí.

Fecha: 15/08/2026 · Base: `mod_venta_inve` · PostgreSQL 18.3 · 37 tablas.

---

## 0. Hallazgo que condiciona todo el script

**Las 37 claves primarias son `GENERATED ALWAYS AS IDENTITY`.**

```sql
SELECT table_name, column_name, identity_generation
FROM information_schema.columns
WHERE table_schema='public' AND is_identity='YES';   -- 37 filas, todas ALWAYS
```

`ALWAYS` (no `BY DEFAULT`) significa que **no se puede insertar un valor explícito**
en la PK sin `OVERRIDING SYSTEM VALUE`. Consecuencias para la carga:

1. Toda `id_*` se **omite de la lista de columnas** del `INSERT`.
2. **No se pueden precalcular los identificadores.** Para relacionar hijos con
   padres (p. ej. `detalle_pedido` → `pedido`) hay que leer los IDs realmente
   asignados: el patrón es `INSERT ... SELECT ... FROM pedido`, no
   `generate_series` sobre un rango de IDs inventado.

---

## 1. Columnas `GENERATED` — se omiten del `INSERT`, nunca se les asigna valor

| Tabla | Columna | Expresión |
|---|---|---|
| `cuenta_por_pagar` | `saldo_pendiente` | `monto_total - monto_pagado` |
| **`detalle_pedido`** | **`subtotal`** | **`cantidad * precio_unitario`** |
| `factura_compra` | `total` | `subtotal + impuesto` |
| **`orden_compra_detalle`** | **`subtotal`** | **`cantidad * precio_unitario`** |
| `orden_produccion` | `costo_total` | `costo_materia_prima + costo_mano_obra + costo_indirecto` |
| `orden_produccion` | `costo_unitario_producido` | `CASE cantidad_producida = 0 THEN 0 ELSE costo_total / cantidad_producida` |
| `orden_produccion_consumo` | `merma` | `COALESCE(cantidad_real, cantidad_teorica) - cantidad_teorica` |
| `orden_produccion_consumo` | `costo_linea` | `COALESCE(cantidad_real, cantidad_teorica) * costo_unitario_snapshot` |

De estas 8, la carga toca dos: `detalle_pedido.subtotal` y
`orden_compra_detalle.subtotal`. **Ambas se omiten.**

---

## 2. Triggers no internos — los 24, clasificados

Consulta usada (decodifica `pg_trigger.tgtype`):

```sql
SELECT c.relname, t.tgname, p.proname,
       CASE WHEN (t.tgtype & 1) = 1 THEN 'ROW' ELSE 'STATEMENT' END AS nivel,
       CASE WHEN (t.tgtype & 2) = 2 THEN 'BEFORE' ELSE 'AFTER' END AS momento,
       concat_ws(',', CASE WHEN (t.tgtype & 4)=4 THEN 'INSERT' END,
                      CASE WHEN (t.tgtype & 8)=8 THEN 'DELETE' END,
                      CASE WHEN (t.tgtype & 16)=16 THEN 'UPDATE' END) AS eventos
FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_proc p ON p.oid=t.tgfoid
WHERE NOT t.tgisinternal AND n.nspname='public';
```

### 2.1 AUDITORÍA — genera filas en otra tabla

| Tabla | Trigger | Nivel | Momento | Eventos |
|---|---|---|---|---|
| `inventario` | `trg_historial_inventario` | ROW | AFTER | **UPDATE** |

**Uno solo, y es `AFTER UPDATE`, no `INSERT`.** Este es el dato que decide la vía
para `historial_inventario` (§5).

### 2.2 RECÁLCULO — escribe un total o agregado en otra fila

| Tabla | Trigger | Nivel | Momento | Eventos | Escribe en |
|---|---|---|---|---|---|
| `detalle_pedido` | `trg_recalcular_total_pedido_insert` | **STATEMENT** | AFTER | INSERT | `pedido.total` |
| `detalle_pedido` | `trg_recalcular_total_pedido_update` | **STATEMENT** | AFTER | UPDATE | `pedido.total` |
| `detalle_pedido` | `trg_recalcular_total_pedido_delete` | **STATEMENT** | AFTER | DELETE | `pedido.total` |
| `orden_compra_detalle` | `trg_oc_total_insert` | **STATEMENT** | AFTER | INSERT | `orden_compra.total` |
| `orden_compra_detalle` | `trg_oc_total_update` | **STATEMENT** | AFTER | UPDATE | `orden_compra.total` |
| `orden_compra_detalle` | `trg_oc_total_delete` | **STATEMENT** | AFTER | DELETE | `orden_compra.total` |
| `pago_proveedor` | `trg_cxp_pagado_insert/update/delete` | **STATEMENT** | AFTER | I/U/D | `cuenta_por_pagar.monto_pagado` |
| `pedido` | `trg_recalcular_total_por_descuento` | ROW | BEFORE | UPDATE | `NEW.total` |

> **Corrección a la premisa del encargo.** Los recálculos de `detalle_pedido` y
> `orden_compra_detalle` son **`FOR EACH STATEMENT`**, no `FOR EACH ROW`. No por
> eso son inocuos: al contrario. Un `INSERT` de 50.000 filas dispara **una**
> ejecución, pero esa ejecución hace un `UPDATE` sobre todos los pedidos tocados
> por el lote. Con lotes grandes es exactamente el trabajo que hay que evitar
> durante la carga, así que se desactivan igual.
>
> `fn_recalcular_total_pedido_stmt` usa una *transition table*
> (`REFERENCING NEW TABLE AS affected_rows`), que sólo existe en triggers de
> sentencia. `ALTER TABLE ... DISABLE/ENABLE TRIGGER` conserva esa declaración.

### 2.3 PROTECCIÓN / INTEGRIDAD — abortan escrituras

| Tabla | Trigger | Nivel | Momento | Eventos | Qué impide |
|---|---|---|---|---|---|
| `pedido` | `trg_proteger_total_pedido` | ROW | BEFORE | UPDATE | Escribir `total` con un valor que no sea el calculado |
| `orden_compra` | `trg_proteger_total_oc` | ROW | BEFORE | UPDATE | Ídem para `orden_compra.total` |
| `cuenta_por_pagar` | `trg_proteger_monto_pagado_cxp` | ROW | BEFORE | UPDATE | Ídem para `monto_pagado` |
| `orden_produccion` | `trg_proteger_costo_materia_prima_op` | ROW | BEFORE | UPDATE | Ídem para `costo_materia_prima` |
| `comprobante_interno` | `trg_validar_total_comprobante` | ROW | **BEFORE INSERT** | INSERT | Que `comprobante.total <> pedido.total` |
| `lista_materiales` | `trg_validar_bom_producto_fabricado` | ROW | BEFORE | INSERT,UPDATE | BOM sobre producto no fabricado |
| `orden_produccion` | `trg_validar_op_producto_fabricado` | ROW | BEFORE | INSERT,UPDATE | OP sobre producto no fabricado |
| `producto` | `trg_validar_cambio_origen_producto` | ROW | BEFORE | UPDATE | Cambio de origen inválido |

### 2.4 `updated_at` — inofensivos para la carga

`cliente`, `pedido`, `producto`, `proveedor`, `usuario` → `fn_set_updated_at`,
todos ROW BEFORE **UPDATE**. Un `INSERT` no los dispara.

---

## 3. La fórmula canónica del total (leída del código, no supuesta)

`fn_recalcular_total_pedido_stmt` y `fn_proteger_total_pedido` coinciden:

```
pedido.total = GREATEST( SUM(detalle_pedido.subtotal) - pedido.descuento , 0 )
```

**El descuento se resta.** El `UPDATE` agregado de la etapa 2 debe usar esta
fórmula exacta, o el trigger de protección rechazará la reactivación implícita y
la verificación de la etapa 3 fallará.

`orden_compra.total` sigue el mismo patrón, sin descuento.

---

## 4. FK de las tablas a poblar → orden de inserción

| Tabla | Depende de |
|---|---|
| `cliente` | `ciudad` |
| `inventario` | `producto`, `bodega` |
| `pedido` | `cliente`, `usuario` |
| `detalle_pedido` | `pedido` (CASCADE), `producto` |
| `comprobante_interno` | `pedido`, `usuario` |
| `movimiento_inventario` | `inventario`, `inventario` (destino), `usuario`, `pedido`, `comprobante_interno`, `proveedor` |
| `historial_inventario` | `inventario`, `usuario` |
| `orden_compra` | `proveedor`, `usuario` ×2 |
| `orden_compra_detalle` | `orden_compra` (CASCADE), `producto`, `materia_prima` |
| `log_accion` | `usuario` |

**Orden derivado:**

```
1. cliente
2. inventario
3. pedido
4. detalle_pedido
5. UPDATE pedido.total          (agregado único, triggers de pedido apagados)
6. comprobante_interno          (necesita pedido.total ya correcto)
7. movimiento_inventario        (necesita inventario, pedido y comprobante)
8. historial_inventario         (necesita inventario)
9. orden_compra → orden_compra_detalle
10. log_accion                  (sólo depende de usuario)
```

El paso 5 va **antes** del 6 porque `trg_validar_total_comprobante` compara contra
`pedido.total` en el momento del `INSERT`.

---

## 5. Decisión sobre `historial_inventario`

El encargo plantea dos vías. Los datos del catálogo deciden:

**`trg_historial_inventario` es `AFTER UPDATE`, no `AFTER INSERT`.** La carga de
`inventario` es un `INSERT` de ~1.733 filas, así que el trigger genera
**exactamente 0 filas**. Cero no «se acerca» a 60.000.

→ **Vía (b): inserción directa**, con un matiz que la mejora:

- Se ejecuta primero **una pasada real de `UPDATE`** sobre las ~2.000 filas de
  `inventario` con el trigger **activo** y `SET LOCAL app.current_user_id`
  presente, para que una parte del historial la produzca el mecanismo real y quede
  demostrado que sigue vivo tras la carga.
- El resto hasta 60.000 se inserta directamente. La inserción directa además
  permite repartir `motivo` entre los 5 valores que admite el `CHECK`, cosa que el
  trigger no hace (escribe siempre `'actualizacion_stock'`).

---

## 6. Restricciones que los datos generados deben respetar

### CHECK

| Tabla | Restricción |
|---|---|
| `cliente` | `correo ~* '^[^@]+@[^@]+\.[^@]+$'` · `estado IN ('activo','inactivo')` |
| `pedido` | `estado IN ('pendiente','procesado','enviado','entregado','anulado')` · `total >= 0` · `descuento >= 0` · `tipo_especial IN ('personalizado','regalo','corporativo')` |
| `detalle_pedido` | `cantidad > 0` · `precio_unitario > 0` · `cantidad_recogida >= 0` |
| `inventario` | `stock_actual >= 0` · `stock_minimo >= 0` |
| `movimiento_inventario` | `cantidad > 0` · `tipo_movimiento IN ('entrada','salida','ajuste','traslado')` · si es `traslado`: `id_inventario_destino NOT NULL` **y** distinto del origen |
| `historial_inventario` | `motivo IN ('actualizacion_stock','ajuste_manual','correccion','importacion','traslado')` |
| `comprobante_interno` | `total >= 0` · `estado IN ('emitido','anulado')` |
| `orden_compra_detalle` | `cantidad > 0` · `precio_unitario > 0` · `0 <= cantidad_recibida <= cantidad` · `tipo_item IN ('producto','materia_prima')` · **item exclusivo**: `producto` ⇒ `id_producto NOT NULL` y `id_materia_prima NULL` (y al revés) |

### UNIQUE

| Tabla | Restricción | Efecto en la generación |
|---|---|---|
| `cliente` | `UNIQUE (correo)` | Correo con el número de secuencia embebido |
| `comprobante_interno` | `UNIQUE (numero_comprobante)` | Numeración correlativa sin repetir |
| `inventario` | `UNIQUE (id_producto, id_bodega)` | **Techo duro: 108 × 20 = 2.160 combinaciones.** Con 267 ya usadas quedan 1.893 libres |

---

## 7. Cardinalidades disponibles para las FK

| Tabla de referencia | Filas | Rango de IDs |
|---|--:|---|
| `producto` | 108 | 1–108 |
| `bodega` | 20 | 1–20 |
| `ciudad` | 88 | 1–88 |
| `usuario` | 6 | 1–6 |
| `proveedor` | 6 | 1–6 |
| `materia_prima` | 10 | 7–16 (**no arranca en 1**) |
| `orden_compra` | 4 | 4–7 (**no arranca en 1**) |

Dos avisos que el script debe respetar:

- `materia_prima` y `orden_compra` **no tienen IDs contiguos desde 1**. Generar
  FK con `1 + (random()*N)::int` produciría violaciones. Se seleccionan de la
  tabla real.
- **Sólo hay 4 órdenes de compra.** Repartir 8.000 líneas entre 4 órdenes daría
  2.000 líneas por orden, que no es un dato realista sino un artefacto. El script
  crea órdenes de compra padre adicionales (~2.600, a ~3 líneas cada una) para que
  las 8.000 líneas tengan padres verosímiles. Queda anotado como desviación
  deliberada del encargo, que sólo listaba `orden_compra_detalle`.

---

## 8. Triggers que se desactivarán durante la carga

Decisión derivada de todo lo anterior: **5 triggers**, los mínimos necesarios.

| # | Tabla | Trigger | Por qué |
|---|---|---|---|
| 1 | `detalle_pedido` | `trg_recalcular_total_pedido_insert` | Cada lote recalcularía el total de todos los pedidos tocados |
| 2 | `pedido` | `trg_proteger_total_pedido` | **Bloquearía el `UPDATE` agregado** del paso 5 |
| 3 | `pedido` | `trg_recalcular_total_por_descuento` | Recalcularía fila a fila durante ese mismo `UPDATE` |
| 4 | `orden_compra_detalle` | `trg_oc_total_insert` | Igual que 1, para `orden_compra.total` |
| 5 | `orden_compra` | `trg_proteger_total_oc` | Bloquearía el `UPDATE` agregado de `orden_compra.total` |

**Se dejan ACTIVOS a propósito:**

- `trg_historial_inventario` — no se dispara en `INSERT`, y se usa deliberadamente
  en la pasada de `UPDATE` (§5).
- `trg_validar_total_comprobante` — validar 30.000 comprobantes contra el total de
  su pedido es una comprobación gratuita de que el paso 5 quedó bien. Si el
  `UPDATE` agregado hubiera calculado mal, esta carga falla y lo delata.
- Los cinco `updated_at` — no se disparan en `INSERT`.
- Los triggers de `pago_proveedor`, `cuenta_por_pagar`, `orden_produccion`,
  `lista_materiales` y `producto` — sus tablas no se tocan en esta fase.

Requiere conectarse como **`postgres`**: `ALTER TABLE ... DISABLE TRIGGER` exige
ser dueño de la tabla, y `rol_administrador` no lo es.

Reactivación verificada contra `pg_trigger.tgenabled = 'O'` en la etapa 3.
