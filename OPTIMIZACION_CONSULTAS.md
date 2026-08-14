# Optimización de Consultas — mod_venta_inve

Fase 33. Estudio de tuning con `EXPLAIN (ANALYZE, BUFFERS)`, decisiones tomadas y
justificación de cada índice conservado, creado o eliminado.

---

## 1. Punto de partida real (corrección a la premisa recibida)

El documento de trabajo del equipo afirmaba que *«no existe ningún índice creado»*.
Verificado contra el catálogo, eso no era cierto:

| Tipo de índice | Cantidad |
|---|---|
| De clave primaria | 37 |
| De restricción UNIQUE | 22 |
| **Secundarios creados explícitamente** | **62** |
| **Total en el esquema `public`** | **121** |

Los 62 secundarios eran todos de columna simple, sobre claves foráneas y sobre
columnas de filtro sueltas (`estado`, `fecha`). Así que el trabajo no era crear
índices desde cero: era **evaluar si los existentes se justifican y qué falta**.

---

## 2. Por qué no se midió sobre la base de producción

La tabla más grande de `mod_venta_inve` tiene **267 filas** (`inventario`). Las
demás: 108 productos, 88 ciudades, 68 detalles de pedido, 25 pedidos.

A esa escala PostgreSQL elige `Seq Scan` **siempre**, y hace bien: leer 1 a 3
páginas de disco cuesta menos que descender un árbol B. Un `EXPLAIN ANALYZE`
sobre `public` mide ~0 ms con índice y ~0 ms sin índice. **No puede distinguir un
índice útil de uno inútil.** Cualquier tabla de «antes/después» construida sobre
esos números sería decoración.

La medición se hizo en un esquema desechable `perf_lab` que replica la forma de
las tablas críticas con volumen de producción proyectado a ~3 años:

| Tabla | Filas |
|---|---|
| `detalle_pedido` | 600.000 |
| `log_accion` | 500.000 |
| `pedido` | 200.000 |
| `inventario` | 40.000 |
| `cliente` | 5.000 |
| `producto` | 2.000 |

Distribuciones no uniformes a propósito: 70 % de pedidos entregados, 8 % de
inventario en stock bajo, 15 % de productos fabricados. Con selectividades
degeneradas el planificador toma decisiones que no se parecen a las reales.

Scripts: `marathon-backend/sql/perf/` (`lab_setup.sql`, `queries.sql`,
`etapa2_*.sql`, `etapa3_*.sql`, `etapa4_*.sql`). `perf_lab` se eliminó al
terminar; se recrea corriendo `lab_setup.sql`.

**Metodología por consulta:** 3 ejecuciones, se reporta la 3.ª (caché caliente),
para que la diferencia medida sea de **plan** y no de E/S en frío. `ANALYZE`
antes de cada etapa.

---

## 3. Consultas evaluadas

Se eligieron las seis consultas que el sistema ejecuta con más frecuencia o que
recorren más filas, tomadas de los módulos reales:

| # | Consulta | Módulo |
|---|---|---|
| Q1 | Ventas por rango de fechas + estado, agrupadas | Reportes (F17) |
| Q2 | Historial de pedidos de un cliente, más reciente primero | Pedidos (F10) |
| Q3 | Inventario en stock bajo de una bodega | Inventario (F7) |
| Q4 | Búsqueda de producto por nombre parcial (`ILIKE '%x%'`) | Productos (F6) |
| Q5 | Auditoría por módulo y rango de fecha | Auditoría (F19b) |
| Q6 | Productos más vendidos en un periodo | Reportes (F17) |

---

## 4. Tabla de tuning

Tiempos en milisegundos, `Execution Time` de la 3.ª ejecución.

| # | Etapa 1<br>solo PK | Etapa 2<br>índices actuales | Etapa 3<br>+ candidatos | Mejora<br>E1→E3 | Decisión |
|---|---|---|---|---|---|
| Q1 | 24,451 | 3,199 | **2,664** | **89,1 %** | Mantener + nuevo compuesto |
| Q2 | 25,164 | 0,040 | **0,017** | **99,9 %** | Mantener + nuevo compuesto |
| Q3 | 1,404 | 0,513 | **0,336** | **76,1 %** | Mantener + nuevo parcial |
| Q4 | 0,089 | 0,090 | 0,092 | **−3,4 %** | **Descartar candidato** |
| Q5 | 26,785 | 0,231 | **0,056** | **99,8 %** | Nuevo compuesto, eliminar el simple |
| Q6 | 47,704 | 25,794 | 25,796 | **45,9 %** | **Descartar candidato cubriente** |

### Cambio de plan por consulta

| # | Plan en Etapa 1 | Plan final |
|---|---|---|
| Q1 | `Parallel Seq Scan on pedido` | `Bitmap Index Scan on idx_pedido_estado_fecha` |
| Q2 | `Parallel Seq Scan` + `Sort` | `Index Scan using idx_pedido_cliente_fecha` (sin Sort) |
| Q3 | `Seq Scan on inventario` | `Bitmap Index Scan on idx_inventario_stock_bajo` |
| Q4 | `Seq Scan on producto` | `Seq Scan on producto` (sin cambio) |
| Q5 | `Parallel Seq Scan on log_accion` | `Index Scan using idx_log_modulo_fecha` |
| Q6 | `Parallel Seq Scan on detalle_pedido` | `Index Only Scan` sobre el cubriente, **sin ganancia** |

---

## 5. Índices creados (4)

Aplicados por `marathon-backend/sql/fase33_optimizacion_indices.sql`.

### 5.1 `idx_pedido_estado_fecha` — `pedido (estado, fecha_pedido)`
La Etapa 2 filtraba solo por fecha y descartaba por estado ya en el heap: leía
**16.438** filas para devolver **11.472**. El compuesto resuelve los dos
predicados en el índice. `3,199 → 2,664 ms` (**16,7 %**). Además sirve a Q6, que
filtra por el mismo par. Tamaño: 7,7 MB.

### 5.2 `idx_pedido_cliente_fecha` — `pedido (id_cliente, fecha_pedido DESC)`
Elimina el nodo `Sort`: el índice ya entrega el orden que pide la pantalla.
`0,040 → 0,017 ms` (**57,5 %**). Tamaño: 6,2 MB.
**Hace redundante** `idx_pedido_cliente`, que es su prefijo izquierdo.

### 5.3 `idx_inventario_stock_bajo` — parcial, `WHERE stock_actual <= stock_minimo`
El caso de uso es la alerta de reposición, y solo ~8 % de las filas la cumplen.
El índice parcial contiene únicamente esas filas: apunta directo a las 140 en
lugar de leer las 2.000 de la bodega y descartar. `0,513 → 0,336 ms` (**34,5 %**).
Tamaño: **40 kB** contra 304 kB del índice completo sobre la misma columna.

### 5.4 `idx_log_modulo_fecha` — `log_accion (modulo, fecha DESC)`
Resuelve filtro y orden en una sola pasada. `0,231 → 0,056 ms` (**75,8 %**).
**Hace redundante** `idx_log_modulo`. Tamaño: 18 MB, el más caro de los cuatro,
justificado porque `log_accion` es la tabla que más crece y su consulta de
filtrado es la más usada del módulo de auditoría.

---

## 6. Índices eliminados (3)

### 6.1 `idx_detalle_subtotal` — existía en la base, **eliminado**
**13 MB, 0 usos** en toda la batería. No hay caso de uso: nadie busca un detalle
por su importe exacto ni hace rangos sobre `subtotal`. Y como `subtotal` es una
columna `GENERATED`, el índice se reescribe en cada `INSERT` y en cada `UPDATE`
de `cantidad` o `precio_unitario`. Costo permanente, beneficio nulo.

### 6.2 `idx_log_modulo` — existía en la base, **eliminado**
Prefijo izquierdo exacto de `idx_log_modulo_fecha`. Toda consulta que filtre por
`modulo`, con o sin fecha, usa igual el compuesto. Medido: **0 usos** una vez
creado. Mantener los dos duplicaba el costo de escritura en la tabla que crece
con cada acción del sistema. 3,4 MB liberados.

### 6.3 `idx_pedido_cliente` — existía en la base, **eliminado**
Prefijo izquierdo de `idx_pedido_cliente_fecha`. 1,4 MB liberados.

---

## 7. Candidatos evaluados y rechazados (2)

Estos son los casos donde la medición contradijo la intuición. Quedan
documentados en el script para que nadie los reproponga sin datos nuevos.

### 7.1 Índice GIN de trigramas sobre `producto.nombre` — **RECHAZADO**

```sql
CREATE EXTENSION pg_trgm;
CREATE INDEX idx_producto_nombre_trgm ON producto USING gin (nombre gin_trgm_ops);
```

Es la estructura teóricamente correcta para `ILIKE '%patrón%'`, porque un B-tree
no puede servir un patrón sin prefijo anclado. Y sin embargo:

- El planificador lo **ignoró por completo**: 0 usos.
- Forzando su uso con `enable_seqscan = off` para comprobar si esa decisión era
  correcta:

| Plan | Tiempo |
|---|---|
| `Seq Scan` | **0,092 ms** |
| `Bitmap Index Scan` + trigramas | 0,296 ms (**3,2× más lento**) |

Con 2.000 productos la tabla entera son 59 páginas y cabe en memoria. El costo
de consultar el índice GIN y luego hacer el recheck supera el de leer la tabla
completa. **El planificador tenía razón.** Reevaluar solo si el catálogo supera
las ~100.000 filas.

### 7.2 Índice cubriente sobre `detalle_pedido` — **RECHAZADO**

```sql
CREATE INDEX idx_detalle_pedido_cover ON detalle_pedido (id_pedido)
    INCLUDE (id_producto, cantidad, subtotal);
```

Sí consiguió el `Index Only Scan` que se buscaba, evitando el viaje al heap. Pero
el tiempo total **no se movió**: `25,794 → 25,796 ms`. El cuello de botella de Q6
no estaba ahí. Y el costo es real y medido:

| Operación | Con el índice | Sin el índice |
|---|---|---|
| 30.000 `INSERT` en `detalle_pedido` | **256,986 ms** | 199,347 ms |

**+28,9 % de costo de escritura y 23 MB de almacenamiento, a cambio de 0 % de
mejora en lectura.** Es el ejemplo más claro del estudio de por qué hay que medir
el lado de la escritura antes de conservar un índice.

---

## 8. Índices con 0 usos que **se conservan**

`idx_pedido_usuario` (1,4 MB) e `idx_log_usuario` (3,4 MB) registraron 0 usos en
la batería y **no se eliminan**. Un índice sobre la columna que referencia
acelera la comprobación de integridad al borrar o actualizar la fila padre, y ese
costo no aparece nunca en el `EXPLAIN` de un `SELECT`. Eliminarlos por «0 usos en
lectura» sería leer mal la evidencia.

---

## 9. Resumen

| Concepto | Cantidad |
|---|---|
| Índices creados | 4 |
| Índices eliminados | 3 |
| Candidatos rechazados con evidencia | 2 |
| Espacio liberado (a volumen proyectado) | ~18 MB |
| Espacio añadido | ~32 MB |
| Mejor caso de mejora | Q5: 99,8 % (26,8 → 0,056 ms) |
| Peor caso | Q4: sin mejora posible por índice a este volumen |

**Criterio de aceptación aplicado:** se conserva el índice si la mejora supera el
15 % **y** el plan pasa de `Seq Scan` a acceso por índice **y** el costo de
escritura no anula la ganancia. Los tres criterios a la vez; el candidato
cubriente cumplía los dos primeros y falló el tercero.

### Reproducir el estudio

```powershell
cd marathon-backend\sql\perf
$env:PGPASSWORD = '<clave>'
psql -U postgres -d mod_venta_inve -f lab_setup.sql
psql -U postgres -d mod_venta_inve -f queries.sql   > etapa1.txt
psql -U postgres -d mod_venta_inve -f etapa2_indices_actuales.sql
psql -U postgres -d mod_venta_inve -f queries.sql   > etapa2.txt
psql -U postgres -d mod_venta_inve -f etapa3_indices_candidatos.sql
psql -U postgres -d mod_venta_inve -f queries.sql   > etapa3.txt
psql -U postgres -d mod_venta_inve -f etapa4_costo_escritura.sql
psql -U postgres -d mod_venta_inve -c "DROP SCHEMA perf_lab CASCADE;"
```

> **Nota de entorno:** el servidor real es **PostgreSQL 18.3 en Windows**, no
> PostgreSQL 15 como indicaba `.kiro/steering/stack.md`. Corregido en el steering.
