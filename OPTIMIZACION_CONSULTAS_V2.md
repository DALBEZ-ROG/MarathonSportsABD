# Optimización de Consultas V2 — `mod_venta_inve`

Fase 39. Estudio de planes con `EXPLAIN (ANALYZE, BUFFERS)` **sobre la base real
poblada**, con 1.042.578 filas.

---

## 0. Por qué existe un V2

El estudio de la **F33** (`OPTIMIZACION_CONSULTAS.md`, que se conserva intacto
como evidencia) fue riguroso, pero tuvo que hacerse en un esquema aparte:

> «La tabla más grande de `mod_venta_inve` tiene **267 filas**. A esa escala
> PostgreSQL elige `Seq Scan` siempre, y hace bien. Un `EXPLAIN ANALYZE` sobre
> `public` mide ~0 ms con índice y ~0 ms sin índice. **No puede distinguir un
> índice útil de uno inútil.**»

La medición se hizo sobre `perf_lab`, un esquema desechable con volumen
simulado, **que se eliminó al terminar** y que hoy no existe: el único esquema
de la base es `public`. Aquel estudio era, por tanto, correcto pero **no
reproducible** y sobre tablas que no eran las de producción (sin las FK, los
triggers ni las columnas `GENERATED` reales).

Este V2 sustituye conceptualmente a aquel: mismas preguntas, **mismo protocolo
de medición**, pero sobre las tablas reales con su volumen real.

| | F33 | F39 (este) |
|---|---|---|
| Dónde se midió | esquema `perf_lab` (desaparecido) | `public`, la base real |
| Volumen | 1.347.000 filas simuladas | 1.042.578 filas reales |
| Reproducible hoy | no | **sí** |
| Triggers y FK activos | no | **sí** |
| Protocolo | `EXPLAIN (ANALYZE, BUFFERS)`, 3 ejecuciones, se reporta la 3.ª | **el mismo** |

El protocolo se mantiene idéntico a propósito: es lo que hace comparables los
dos estudios.

---

## 1. Preparación: el heap, antes de medir nada

Medir sobre un heap inflado invalida la comparación, y el sesgo empuja
justo hacia la conclusión que un estudio de índices querría demostrar: una tabla
con páginas semivacías hace que el `Seq Scan` —que las lee todas— salga
artificialmente caro, y el índice artificialmente bueno.

El criterio habitual (compactar donde haya >10 % de tuplas muertas) **no habría
detectado el problema real**:

| Tabla | Tuplas muertas | Páginas antes | Páginas después | Filas/página antes → después |
|---|--:|--:|--:|---|
| `pedido` | **0** | 4.616 | **2.200** | 35,7 → **75,0** |
| `log_accion` | **0** | 6.485 | **3.185** | 30,8 → **62,8** |
| `movimiento_inventario` | **0** | 4.069 | **1.291** | 19,7 → **62,0** |
| `orden_produccion` | 49,94 % | 95 | 49 | — |
| `cuenta_por_pagar` | 30,02 % | 45 | 24 | — |

Las tres primeras tenían **cero tuplas muertas** —el `VACUUM ANALYZE` de la F38
ya las había limpiado— y aun así arrastraban un 50–68 % de páginas de más:
espacio *libre pero no devuelto*, dejado por los `UPDATE` masivos de la
redistribución. Un `Seq Scan` sobre `pedido` estaba leyendo **el doble de
páginas** de las necesarias.

`VACUUM (FULL, ANALYZE)` + `REINDEX TABLE` sobre las siete tablas afectadas.
**Base: 312 MB → 191 MB.** Después, `ANALYZE` de las 37 tablas: `VACUUM FULL`
reescribe el heap y `REINDEX` reconstruye los índices, así que las estadísticas
previas ya no describían la disposición física.

> **Lección:** «tuplas muertas» y «espacio desperdiciado» no son lo mismo.
> Conviene mirar las filas por página, no solo `n_dead_tup`.

---

## 2. El catálogo: 18 consultas, todas del código

Ninguna está inventada. Cada una cita la clase y el método de los que sale.
Script: `marathon-backend/sql/fase39_estudio_planes.sql`.

| # | Qué hace | Origen en el código |
|---|---|---|
| Q01 | Pedidos por estado y rango de fechas | `PedidoRepository.findByEstadoAndFechaPedidoBetween` |
| Q02 | Pedidos de un cliente | `PedidoRepository.findByClienteIdCliente` |
| Q03 | Pedidos de un cliente + estado | `PedidoRepository.findByClienteIdClienteAndEstado` |
| Q04 | Ventas por día (gráfico del dashboard) | `PedidoRepository.ventasPorDia` |
| Q05 | Conteo de pedidos por estado (KPI) | `PedidoRepository.pedidosPorEstado` |
| Q06 | Despachos por región y fecha de empaque | `PedidoRepository.findDespachados` |
| Q07 | Detalle de pedido con join a producto | `DetallePedidoRepository.findByPedidoIdPedido` |
| Q08 | Top de productos vendidos | `DetallePedidoRepository.topProductos` |
| Q09 | Inventario en stock bajo | `InventarioRepository.contarStockBajo` |
| Q10 | Inventario de un producto | `InventarioRepository.findByProductoIdProducto` |
| Q11 | Kardex de inventario | `MovimientoInventarioRepository.findByInventarioProducto…` |
| Q12 | Búsqueda de producto por nombre | `ProductoRepository.buscarConFiltros` |
| Q13 | Auditoría por módulo y fecha | `LogAccionRepository.buscar` |
| Q14 | Órdenes de compra por estado y proveedor | `OrdenCompraRepository.findByEstadoAndProveedor…` |
| Q15 | Cuentas por pagar vencidas | `CuentaPorPagarRepository.findByFechaVencimientoLessThanAndEstado` |
| Q16 | Saldo pendiente total | `CuentaPorPagarRepository.sumaSaldoPendienteTotal` |
| Q17 | Kardex de materia prima | `MovimientoMateriaPrimaRepository.findByMateriaPrima…` |
| Q18 | Órdenes de producción por estado | `OrdenProduccionRepository.buscar` |

### Línea base (`perf/f39_baseline.txt`)

13 de 18 consultas usan índice. Las 5 que hacen `Seq Scan` lo hacen **con razón**:

| Consulta | Nodo | Tiempo | Por qué es correcto |
|---|---|--:|---|
| Q05 | Parallel Seq Scan | 28,8 ms | Agrupa **toda** la tabla por estado: no hay filtro que un índice pueda aprovechar |
| Q06 | Parallel Seq Scan | 27,8 ms | `fecha_empaque` es `NULL` en todos los pedidos → 0 filas |
| Q08 | Parallel Seq Scan | 94,3 ms | Agrega 450.000 líneas: leerlas todas es más barato que 450.000 accesos aleatorios |
| Q12 | Seq Scan | 0,04 ms | `producto` tiene 108 filas y ocupa 2 páginas |
| Q16 | Seq Scan | 0,32 ms | `cuenta_por_pagar` cabe en 24 páginas |

---

## 3. Los tres parámetros del planificador, uno a uno

Se midieron **por separado**, cada uno con los otros dos en su valor base. Es la
razón por la que `postgresql.conf` se dejó intacto durante las fases 36–38.1:
sin esa disciplina, ningún cambio de plan sería atribuible.

### El resultado, en una línea

**Ninguno de los tres cambió un solo nodo de acceso en las 18 consultas.**

| Parámetro | Base → nuevo | Consultas que cambiaron de plan | Δ tiempo total |
|---|---|--:|--:|
| `random_page_cost` | 4 → 1.1 | **0 de 18** | +15,1 % |
| `effective_cache_size` | 4 GB → 12 GB | **0 de 18** | +3,4 % |
| `work_mem` | 4 MB → 16 MB | **0 de 18** | +4,9 % |

### Por qué las diferencias de tiempo son ruido y no señal

Si el plan es idéntico, el trabajo es idéntico, y cualquier diferencia de tiempo
solo puede venir del ruido de ejecución. Para cuantificarlo se ejecutó **la línea
base dos veces con la misma configuración**:

| | Δ por consulta | Δ total |
|---|--:|--:|
| Dos corridas idénticas | media **12,7 %**, máxima **51,0 %** | +1,0 % |

Con una desviación natural del 12,7 % por consulta, los +15,1 % / +3,4 % / +4,9 %
observados no soportan ninguna conclusión sobre rendimiento. Lo que sí es sólido
es el conteo de cambios de plan: **cero**.

### Evidencia específica de cada parámetro

**`work_mem` — el único con evidencia concluyente, y dice que no hace falta.**
En las 18 consultas no hay un solo *spill* a disco: todos los ordenamientos son
`quicksort` en memoria y todos los hash reportan `Batches: 1`. El mayor consumo
es de **2.900 kB**, por debajo de los 4 MB del defecto. Subir a 16 MB no podía
cambiar nada porque nada llegaba al límite. **Se deja en 4 MB.**

**`random_page_cost` — mueve la aguja, pero no cruza el umbral.** Midiendo el
coste estimado del plan elegido frente al forzado por índice en Q06:

| `random_page_cost` | Coste `Seq Scan` | Coste índice forzado | Margen a favor del secuencial |
|---|--:|--:|--:|
| 4 | 5.626,58 | 8.128,67 | **+44 %** |
| 1.1 | 5.626,58 | 6.691,83 | **+19 %** |

El efecto existe y va en la dirección esperada, pero con este catálogo no llega
a cambiar ninguna decisión.

**`effective_cache_size`** — mismo patrón: cero cambios de plan.

### Valores aplicados, y por qué

`random_page_cost = 1.1` y `effective_cache_size = 12 GB` se aplicaron con
`ALTER SYSTEM` + `pg_reload_conf()`. **No porque mejoraran algo medible** —no lo
hicieron—, sino porque:

1. el disco es un **SSD NVMe verificado** y la RAM es de **31,76 GB**, así que los
   valores por defecto describen mal este hardware;
2. la medición demuestra que **no degradan ningún plan actual**.

Es un cambio de modelo de costes con riesgo medido, no una optimización. Si se
prefiere el criterio estricto de «sin mejora medible, sin cambio», revertirlo es
una línea de `ALTER SYSTEM`. `work_mem` **no se tocó**, porque ahí la evidencia
era concluyente en contra.

---

## 4. Los 63 índices explícitos

Contadores reseteados con `pg_stat_reset()` y tráfico real generado con el
catálogo **más** los 86 endpoints de `fase37_pruebas_endpoints.ps1` y
`fase37_pruebas_navbar.ps1`, con el backend arriba.

| Clase | Índices | Espacio |
|---|--:|--:|
| **Usados** (`idx_scan > 0`) | 21 | 38 MB |
| **No usados pero justificables** (respaldan una FK) | 32 | 7.968 kB |
| **Candidatos a revisar** | 10 | 3.872 kB |

Los 32 «justificables» respaldan una clave foránea. PostgreSQL **no crea índice
automáticamente** para el lado hijo de una FK, y sin él un `DELETE` en el padre
obliga a un escaneo secuencial del hijo por cada fila borrada. Que no aparezcan
en `pg_stat_user_indexes` significa que el catálogo no los ejercita, no que
sobren.

### El hallazgo que justifica todo el método

`idx_historial_fecha` tenía **`idx_scan = 0`** y es el más grande de los
candidatos (3.080 kB). Por el criterio de «0 usos → eliminar», habría caído. La
prueba con `DROP INDEX` + `ROLLBACK` dice otra cosa:

```
Con índice : Index Scan Backward ...  Execution Time:  0,055 ms
Sin índice : Parallel Seq Scan   ...  Execution Time: 29,909 ms      ← ×544
```

**Un índice con cero usos registrados puede ser esencial.** Lo que mide
`idx_scan` es qué ejercitó el tráfico de prueba, no qué necesita la aplicación.

### Tabla de recomendaciones

Método: `BEGIN; DROP INDEX …; EXPLAIN (ANALYZE, BUFFERS) …; ROLLBACK;`. `DROP
INDEX` es transaccional en PostgreSQL, así que el índice vuelve al deshacer.
Evidencia completa en `perf/f39_indices_rollback.txt`.

| Índice | Tamaño | Con | Sin | Factor | Recomendación |
|---|--:|--:|--:|--:|---|
| `idx_historial_fecha` | 3.080 kB | 0,055 ms | 29,909 ms | **×544** | **Conservar** — imprescindible |
| `idx_mmp_fecha` | 304 kB | 0,108 ms | 1,370 ms | ×12,7 | **Conservar** |
| `idx_pp_fecha` | 120 kB | 0,048 ms | 0,224 ms | ×4,7 | Conservar (barato) |
| `idx_mmp_tipo` | 112 kB | 0,095 ms | 0,436 ms | ×4,6 | Conservar (barato) |
| `idx_rm_fecha` | 56 kB | 0,050 ms | 0,119 ms | ×2,4 | Conservar (barato) |
| `idx_fc_estado` | 48 kB | 0,128 ms | 0,221 ms | ×1,7 | Indiferente |
| `idx_oc_estado` | 64 kB | 0,046 ms | **0,018 ms** | **más rápido sin él** | **Eliminar** |
| `idx_fc_vencimiento` | 56 kB | 0,016 ms | 0,012 ms | no se usa ni existiendo | **Eliminar** |
| `idx_dp_estado` | 16 kB | 0,051 ms | 0,041 ms | no se usa ni existiendo | **Eliminar** |
| `idx_sd_estado` | 16 kB | 0,096 ms | 0,074 ms | no se usa ni existiendo | **Eliminar** |

Los cuatro marcados para eliminar suman **152 kB**: el ahorro de espacio es
irrelevante y el motivo real es otro — cada índice penaliza todos los `INSERT`
y `UPDATE` de su tabla, y estos cuatro no aportan nada a cambio. En los tres
últimos el planificador prefiere el `Seq Scan` **incluso teniéndolos
disponibles**, porque sus tablas caben en pocas páginas.

> **Ningún índice se eliminó.** La decisión es del usuario; aquí está la
> evidencia. Se verificó al final que los **63 siguen existiendo**.

### Índice nuevo propuesto (no creado)

Q06 (`findDespachados`) filtra por `fecha_empaque` y hoy hace `Parallel Seq
Scan` sobre 165.000 filas. **No se propone índice**: la columna es `NULL` en el
100 % de las filas porque el flujo de empaque no se ha ejercitado con volumen.
Un índice parcial `WHERE fecha_empaque IS NOT NULL` sería la opción natural
cuando esos datos existan; medirlo antes sería medir sobre el vacío.

---

## 5. Consultas lentas

`log_min_duration_statement` llevaba en 1.000 ms desde la F36 **sin capturar una
sola consulta de la aplicación**. Con 5.987 consultas registradas bajo tráfico
real de los seis roles:

| Percentil | Tiempo |
|---|--:|
| p50 | 0,010 ms |
| p90 | 0,034 ms |
| p95 | 0,062 ms |
| **p99** | **2,083 ms** |

| Umbral | Consultas capturadas | % del tráfico |
|---|--:|--:|
| 1.000 ms | 12 | 0,20 % |
| 200 ms | 12 | 0,20 % |
| 100 ms | 15 | 0,25 % |
| 50 ms | 17 | 0,28 % |
| **20 ms** | **33** | **0,55 %** |
| 10 ms | 38 | 0,63 % |

Las 12 que superan 1.000 ms son los bloques `DO` de las cargas masivas de las
fases 38 y 39 — **ninguna es tráfico de aplicación**.

**Umbral fijado: 20 ms.** Es ~10× el p99 real, captura el 0,55 % del tráfico
(volumen manejable para el log) e incluye las tres agregaciones del dashboard
(Q05 28,8 ms, Q06 27,8 ms, Q08 94,3 ms), que son las primeras candidatas a
degradarse cuando el volumen crezca. **200 ms se descartó** porque, con el p99 en
2 ms, no habría capturado nada de la aplicación: habría dejado el requisito
igual de vacío que 1.000 ms.

---

## 6. Reproducir el estudio

```powershell
$env:PGPASSWORD='<clave>'
$sql = "marathon-backend\sql"

# Linea base
psql -U postgres -d mod_venta_inve -v rpc=4 -v ecs=4GB -v wm=4MB `
     -f "$sql\fase39_estudio_planes.sql" > "$sql\perf\f39_baseline.txt"

# Un parametro a la vez
psql ... -v rpc=1.1 -v ecs=4GB  -v wm=4MB  ... > perf\f39_random_page_cost.txt
psql ... -v rpc=4   -v ecs=12GB -v wm=4MB  ... > perf\f39_effective_cache_size.txt
psql ... -v rpc=4   -v ecs=4GB  -v wm=16MB ... > perf\f39_work_mem.txt
```

Evidencia bruta en `marathon-backend/sql/perf/`: `f39_baseline.txt`,
`f39_baseline_repeticion.txt` (la medición del ruido), `f39_random_page_cost.txt`,
`f39_effective_cache_size.txt`, `f39_work_mem.txt`, `f39_final.txt` y
`f39_indices_rollback.txt`.

---

## 7. Conclusiones

1. **Los índices de la F33 estaban bien elegidos.** Con volumen real, 13 de 18
   consultas usan índice y las 5 restantes hacen `Seq Scan` con razón. El estudio
   sobre `perf_lab` acertó, aunque no pudiera demostrarse sobre la base real.
2. **Ningún parámetro del planificador cambia una sola decisión** con este
   catálogo y este volumen. Los dos aplicados lo son por corrección del modelo
   frente al hardware, con la degradación medida en cero.
3. **`work_mem` no necesita tocarse**, y hay evidencia dura: cero *spills*.
4. **El espacio libre en el heap importaba más que los parámetros.** Compactar
   `pedido`, `log_accion` y `movimiento_inventario` redujo a la mitad las páginas
   que un `Seq Scan` debe leer — un efecto mayor que el de los tres parámetros
   juntos, y en tablas que el criterio de tuplas muertas declaraba limpias.
5. **`idx_scan = 0` no significa «sobra».** Cuatro de los diez candidatos se
   recomiendan eliminar; uno de ellos, con cero usos, resultó ser el más
   importante de todos.
