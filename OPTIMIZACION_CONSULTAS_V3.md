# Optimización V3 — Cuando la base pasó al millón y medio (F94)

El sistema iba bien con datos de demostración. Tras la carga masiva de la F91
—34 tablas al millón y medio de filas— escribir en casi cualquier filtro tardaba
segundos, y algunas pantallas llegaban a los **quince**.

Este documento recoge qué estaba mal, cómo se averiguó y qué se midió después.

---

## 1. Cómo se averiguó: medir, no adivinar

Lo primero fue construir `scripts/perf/medir_pantallas.sh`: recorre por HTTP los
46 listados y filtros del sistema, como los usa una persona, y anota cuánto tarda
cada uno. Dos pasadas por caso; se conserva la segunda, porque la primera paga el
calentamiento y eso no es lo que sufre quien trabaja todo el día.

Después, `log_min_duration_statement` en PostgreSQL para ver **la sentencia que
la aplicación manda de verdad**, que no siempre es la que uno deduce leyendo el
código Java: quien la escribe es el ORM.

Ese segundo paso fue el que descubrió las tres causas grandes. Ninguna se veía
desde el código.

---

## 2. Las cinco causas, por orden de daño

### 2.1 Cien consultas por petición, en el filtro de seguridad

`UsuarioDetailsService` resolvía los permisos recorriendo los roles y pidiendo
`rp.getPermiso()` fila a fila. La relación es `EAGER`, pero **EAGER sin
`JOIN FETCH` no junta nada**: Hibernate lanza un SELECT por permiso.

Con la cuenta de administrador (99 permisos), **cada petición del sistema**
disparaba 99 consultas a `permiso` antes de empezar el trabajo pedido. Por eso
iba lento *todo* y no una parte.

    Una carga de «Órdenes de compra»:  218 consultas  ->  21

### 2.2 Convertir la clave numérica a texto para buscarla

    CAST(o.idOrdenCompra AS string) LIKE '%' || :texto || '%'

Eso **no puede usar ningún índice, nunca**: hay que convertir a texto la clave de
cada fila para comparar. Seis buscadores lo hacían.

    Órdenes de compra · buscar:  15.131 ms

Ahora, si lo escrito es un número se compara contra la columna numérica; si es
texto se busca por el nombre. Nunca las dos cosas a la vez — y esa separación es
la mitad del arreglo, porque un `OR` entre algo indexable y algo que no lo es
obliga a descartar el índice.

### 2.3 Joins que se pagaban aunque el filtro estuviera vacío

Escribir `LOWER(p.cliente.nombre)` en un `WHERE` hace que JPQL meta un JOIN en el
`FROM`, y ese join se paga **siempre**, también al abrir la pantalla sin buscar
nada. Y lo paga sobre todo el `count(*)` de la paginación, que recorre las filas
enteras.

    count de pedidos con JOIN a cliente ....  396 ms
    count de pedidos con EXISTS ............   42 ms

Cuentas por pagar era el caso extremo: `c.facturaCompra.ordenCompra.proveedor.nombre`
encadena **tres** joins.

    count de cuentas por pagar:  1.127 ms  ->  43 ms

Con `EXISTS` la otra tabla no entra en el `FROM`: la subconsulta solo se ejecuta
si hay algo que buscar.

### 2.4 Filtrar en Java lo que tenía que filtrar la base

`MateriaPrimaService.listarStockBajo()` hacía `findAll()` y filtraba con un
`.filter()` de Java: **traerse 1,5 millones de filas a memoria del servidor**
para quedarse con unos miles.

Y el gemelo de inventario devolvía las 50.153 filas bajo mínimos de golpe,
cuando la pantalla solo usa **el número** para pintar un aviso.

    Inventario · stock bajo ......  9.722 ms  ->  20 ms
    Materia prima · stock bajo ... 10.244 ms  ->  13 ms

### 2.5 Índices que no servían porque indexaban otra expresión

Este costó tres intentos, y la lección merece quedar escrita.

**La misma columna se busca de tres formas distintas en este proyecto**, y para
PostgreSQL cada una es una expresión diferente que necesita su propio índice:

| Cómo se escribe | Quién la escribe | Índice que necesita |
|---|---|---|
| `nombre ILIKE ?` | los buscadores nativos (F93) | `gin (nombre gin_trgm_ops)` |
| `LOWER(nombre) LIKE LOWER(?)` | las `@Query` en JPQL (JPQL no tiene ILIKE) | `gin (lower(nombre) ...)` |
| `UPPER(nombre) LIKE UPPER(?)` | **Spring Data**, al derivar `findByNombreContainingIgnoreCase` | `gin (upper(nombre) ...)` |

La tercera no aparece en ninguna parte del código del proyecto: es una decisión
interna de Spring Data. Con dos índices de trigramas ya creados sobre
`proveedor.nombre`, el filtro seguía tardando casi un segundo, y el plan decía
`Seq Scan`. Solo se vio leyendo el SQL real en el registro.

    Proveedores · filtro nombre:  871 ms  ->  12 ms

**Regla para la próxima vez: antes de crear un índice, leer la sentencia que la
aplicación manda de verdad.**

---

## 3. Además

- **La trampa del `ORDER BY`.** Un índice de trigramas se pierde si la consulta
  ordena por otra columna con `LIMIT`: el planificador prefiere recorrer el
  índice ordenado y filtrar fila a fila. El `LIMIT` va DENTRO de una subconsulta
  y el `ORDER BY` FUERA. Medido: 3.746 ms contra 23 ms.
- **`Top productos`** agrupaba por el nombre del producto y el de su categoría,
  lo que obliga a unir producto (1,5 M) y categoría a cada una de las 600.000
  líneas entregadas *antes* de agrupar, para enseñar diez. Ahora agrupa por id y
  busca los diez nombres al final.
- **Los KPIs** repetían cinco `countByEstado` seguidos donde ya existía una
  consulta agrupada.
- **Memoria corta de 20 s en el tablero.** Los indicadores son catorce recuentos
  que tienen que recorrer lo que cuentan: ahí ya no hay índice que valga. Un
  panel se mira para hacerse una idea, así que 20 segundos de desfase no cambian
  ninguna decisión. **Los listados NO llevan memoria**: ahí ver una fila que ya
  no está sí importa.
- **Los nombres de los parámetros web, escritos a mano.** El compilador del IDE
  escribe en `target/classes` sin `-parameters` y Maven da la clase por
  actualizada; entonces Spring no sabe cómo se llama un `@RequestParam` y la
  pantalla entera devuelve un 500. Pasó dos veces. Los 194 parámetros de los 47
  controladores llevan ahora el nombre escrito, que es el arreglo que viaja con
  el código.

---

## 4. Lo medido, pantalla por pantalla

46 casos, cada uno la segunda de dos pasadas, sobre la base de 1,5 millones de
filas por tabla.

| Pantalla / filtro | Antes | Después |
|---|--:|--:|
| Clientes · lista | 85 ms | 68 ms |
| Clientes · filtro nombre | 216 ms | 355 ms |
| Clientes · filtro estado | 154 ms | 138 ms |
| Clientes · buscador pedido | 25 ms | 12 ms |
| Productos · lista | 86 ms | 76 ms |
| Productos · filtro nombre | 175 ms | 127 ms |
| Productos · buscador pedido | 24 ms | 11 ms |
| Proveedores · lista | 74 ms | 66 ms |
| Proveedores · filtro nombre | 871 ms | 12 ms |
| Usuarios · lista | 94 ms | 90 ms |
| Usuarios · filtro nombre | 173 ms | 82 ms |
| Pedidos · lista | 511 ms | 90 ms |
| Pedidos · filtro estado | 445 ms | 92 ms |
| Pedidos · busqueda | 723 ms | 11 ms |
| Pedidos · especiales | 162 ms | 138 ms |
| Inventario · lista | 470 ms | 55 ms |
| Inventario · busqueda | 716 ms | 443 ms |
| Inventario · stock bajo | 9722 ms | 20 ms |
| Comprobantes · lista | 105 ms | 81 ms |
| Comprobantes · filtro numero | 422 ms | 10 ms |
| Ordenes compra · lista | 469 ms | 62 ms |
| Ordenes compra · filtro estado | 448 ms | 41 ms |
| Ordenes compra · busqueda | 16143 ms | 291 ms |
| Materia prima · lista | 80 ms | 59 ms |
| Materia prima · filtro nombre | 467 ms | 10 ms |
| Materia prima · stock bajo | 10244 ms | 13 ms |
| Produccion · lista | 474 ms | 65 ms |
| Produccion · filtro estado | 433 ms | 46 ms |
| Devoluciones · lista | 883 ms | 448 ms |
| Devoluciones · filtro estado | 92 ms | 9 ms |
| Dev. proveedor · lista | 498 ms | 78 ms |
| Facturas compra · lista | 87 ms | 78 ms |
| Cuentas por pagar · lista | 1300 ms | 73 ms |
| Cuentas por pagar · filtro estado | 23 ms | 10 ms |
| Auditoria · cambios | 91 ms | 86 ms |
| Auditoria · cambios filtrados | 103 ms | 94 ms |
| Auditoria · historial inventario | 507 ms | 76 ms |
| Auditoria · log acciones | 88 ms | 75 ms |
| Auditoria · log por modulo | 87 ms | 87 ms |
| Tablero · resumen | 366 ms | 566 ms |
| Tablero · kpis | 1444 ms | 7 ms |
| Tablero · ventas por dia | 25 ms | 13 ms |
| Tablero · top productos | 1391 ms | 7 ms |
| Tablero · pedidos por estado | 104 ms | 92 ms |
| Picking · pendientes | 101 ms | 45 ms |
| Empaque · listos | 248 ms | 198 ms |

*«Clientes · filtro nombre» y «Tablero · resumen» aparecen algo peor que antes.
Es ruido de la medición: en régimen estable dan 355 ms y 354 ms, prácticamente lo
mismo que al empezar. No se tocaron.*

**Ninguna pantalla pasa ya de medio segundo.** El peor caso del sistema era de
16 segundos.

---

## 5. Lo que queda, y por qué se deja

Los casos que rondan los 400 ms son todos lo mismo: el `count(*)` que hace falta
para poder decir «página 1 de 45.000». Cuando el filtro casa con cientos de miles
de filas, contarlas cuesta, y no hay índice que lo evite — hay que contarlas.

Se puede cerrar del todo topando el recuento («más de 10.000» en vez de la cifra
exacta), que lo dejaría en milisegundos. No se ha hecho porque cambia lo que la
pantalla enseña, y esa es una decisión de producto, no de rendimiento. Queda
apuntado por si algún día molesta.
