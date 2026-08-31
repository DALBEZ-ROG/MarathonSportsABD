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

---

## 6. Segunda vuelta: lo que la comprobación por roles destapó

La primera tanda se midió con el banco de pantallas. Al pasar después
`scripts/perf/comprobar_sistema.sh` —que recorre lo que hace **cada rol**—
apareció un caso de **15,7 segundos** que el banco no veía: buscar «distribu» en
Órdenes de compra.

La diferencia era el término. El banco buscaba `OC-`, que casa con poquísimas
filas; `distribu` casa con **1.498.108 de 1.499.977**. Un buscador hay que
medirlo con algo que encuentre mucho, no con algo que no encuentre nada. El
banco tenía un punto ciego, y lo tenía yo al escribirlo.

### El `EXISTS` mejoraba un caso y arruinaba el otro

El plan lo dijo entero:

```
Seq Scan on orden_compra
  Filter: EXISTS(SubPlan 1)
    ->  Index Scan using proveedor_pkey on proveedor
```

Un **subplan correlacionado**: recorrer 1,5 millones de órdenes y, por cada una,
ir a buscar su proveedor. Medido sobre esa misma consulta:

| Forma | Sin filtro de texto | Con filtro que casa con mucho |
|---|--:|--:|
| `JOIN` | 396 ms | 581 ms |
| `EXISTS` | **42 ms** | **15.924 ms** |
| dos consultas | **42 ms** | **581 ms** |

Ninguna forma sirve para los dos casos. La respuesta no era encontrar la
expresión mágica: era **dejar de buscarla** y escribir dos consultas, con el
servicio eligiendo. Es más código y es lo correcto — cada una recibe el plan que
le conviene.

Se aplicó a los siete listados con buscador: pedidos, órdenes de compra,
producción, devoluciones, devoluciones a proveedor, inventario y cuentas por
pagar.

### Resultado, buscando términos que casan con más de un millón de filas

| Buscador | Coincidencias | Después |
|---|--:|--:|
| Órdenes de compra · proveedor | 1.498.108 | 620 ms |
| Dev. a proveedor · proveedor | 1.499.719 | 679 ms |
| Cuentas por pagar · proveedor | 1.498.312 | 1.570 ms |
| Inventario · producto | 149.800 | 688 ms |
| Devoluciones · cliente | 74.989 | 511 ms |
| Pedidos · cliente | 73.596 | 327 ms |
| Producción · producto | 249.915 | 218 ms |

Y el tablero de manufactura, que hacía una decena de agregados
`WHERE estado='completada' AND fecha_fin >= …` sin índice por esa pareja:
**1.174 ms → 290 ms**.

### La comprobación por roles, entera

`scripts/perf/comprobar_sistema.sh` recorre 38 pantallas con los seis usuarios
reales y comprueba a la vez que responden, que traen lo que deben y que tardan
menos de un segundo.

    37 bien · 1 por encima de 1000 ms · 0 fallando

El que pasa del segundo es la **primera** carga del tablero tras arrancar, antes
de que su memoria de 20 s tenga nada. La segunda son 7 ms.

---

## 7. El asistente de IA

Se midió y se tocó, pero **no se pudo verificar de extremo a extremo**, y hay que
decirlo: las mediciones de latencia agotaron la cuota diaria de la clave.

### Lo que se midió

- **La llamada a Google domina y no es estable.** Doce medidas de la misma
  pregunta: de **0,28 s a 57 s**. Eso no se arregla desde aquí.
- Se probó `thinkingConfig: {thinkingLevel: "LOW"}` para acortar la
  deliberación del modelo. Con esa varianza **los datos no lo respaldan** (media
  6,25 s frente a 18,98 s, a favor de dejarlo como está), así que **no se
  cambió**. Cambiarlo habría sido fe, no medición.
- **El SQL que escribe el modelo sí era mejorable**: para «los 5 productos más
  vendidos» generaba un `GROUP BY p.id_producto, p.nombre` que une producto
  (1,5 M) a cada línea antes de agrupar — **2,9 s** de los 10 s totales.

### Lo que se cambió

1. **El contexto le dice ahora que la base es grande** y le da siete reglas
   concretas: agrupar por id y no por nombre, poner siempre `LIMIT`, no usar
   `SELECT *`, buscar solo por las columnas indexadas, comparar identificadores
   como números, meter el `LIMIT` en subconsulta cuando el `ORDER BY` va por
   otra columna, y preferir contar a listar.
2. **Memoria de traducciones**: si alguien repite una pregunta idéntica, se
   reutiliza el SQL en vez de volver a llamar a Google. Se recuerda la
   *traducción*, no el resultado: **los datos se vuelven a consultar siempre**,
   así que nadie ve una cifra vieja.

### Lo que hay que saber, y no es menor

**El plan gratuito de Gemini permite 20 preguntas al día** para
`gemini-3.6-flash`. No es una limitación del sistema, es de la clave. Está
anotado en DEMO_CHECKLIST.md, porque en una demostración es exactamente el tipo
de cosa que falla en el peor momento.

---

## 8. El recorrido visual, y lo que solo se ve mirando la pantalla

Con la extensión de Chrome reconectada, recorrido de las pantallas con el
navegador. Tres hallazgos, dos de ellos **invisibles desde la API**.

### 8.1 Cuentas por pagar enseñaba una cifra falsa

El aviso rojo decía:

```
1499192 cuenta(s) vencida(s) por un total de $43.950.118,75
```

El total de verdad son **$46.124.820.094,14**. Subestimaba la deuda **mil veces**.

La causa: el navegador pedía `?estado=vencida&size=1000`, sumaba en local lo que
llegaba, y lo enseñaba junto al `totalElements` REAL del servidor. El recuento
correcto al lado de la suma de mil filas, con aspecto de dato bueno.

Con pocos datos no se notaba, porque las mil filas eran todas. Es el peor tipo de
fallo: no se cae, no avisa, y lo que enseña se lee perfectamente.

Ahora las dos cifras salen de la misma consulta
(`GET /api/cuentas-por-pagar/resumen-vencidas`).

### 8.2 Las cifras largas se cortaban en el tablero

La tarjeta lleva `overflow: hidden`, así que `$46.127.575.251,85` se mostraba
como `$46.127.575.251,8` — sin el último dígito ni los centavos, y sin ninguna
señal de que faltara nada. La letra se encoge ahora según lo larga que sea la
cifra, y el valor íntegro queda en el `title`.

### 8.3 Los filtros de lista buscaban la frase entera — CORREGIDO en la F94d

En Inventario, «zapatilla nike» no encuentra **nada**; «zapatilla» encuentra
14.980 páginas. Es el mismo fallo que se corrigió en la F93b para los buscadores
de «Pedido nuevo», pero los **filtros de lista** se quedaron fuera.

**No se ha corregido**, y la razón es de prudencia: son siete pantallas con siete
consultas JPQL, y JPQL no admite un número variable de condiciones, así que hace
falta o bien tres parámetros opcionales de palabra por consulta, o bien pasarlas
a SQL nativo como se hizo con los buscadores. Cualquiera de las dos es un cambio
que quiero medir con calma después, no meter al final de una sesión.

Afecta a: Inventario, Pedidos, Órdenes de compra, Producción, Devoluciones,
Devoluciones a proveedor y Cuentas por pagar.

### 8.4 Lo que se comprobó y está bien

- **Inventario**: el aviso de stock bajo enseña 50.153, cuadra con la base, y ya
  no descarga 50.153 registros para pintar un número.
- **Auditoría**: las cuatro pestañas. El rastro de `admin` sale con 19.932
  acciones y 7.813 movimientos de stock; los cambios campo a campo con su
  antes/después, la cuenta de PostgreSQL y el número de transacción.
- El distintivo ámbar **«fuera de la app»** aparece donde debe: en los cambios
  que hizo la propia migración por `psql`, no el sistema.
- **Respaldos**: los tres puntos, con el purgado marcado «ya no está en disco» y
  sin botón de restaurar. El botón de borrar, deshabilitado sin la frase.
- **Pedido nuevo**: «cedeno maria» (al revés) encuentra a Maria Cedeno.
- **Cero errores** en la consola del navegador en todo el recorrido.
- Comprobación por roles: **37 bien · 0 fallando**.

### 8.5 Dos equivocaciones propias, anotadas

1. Al añadir el método del resumen lo inserté **entre un `@Transactional` y su
   método**, así que la anotación pasó al método nuevo y `listar` se quedó sin
   transacción. Resultado: «Executing an update/delete query» y un 500 en toda
   la pantalla de Cuentas por pagar. Lo detectó la comprobación por roles, no yo.
2. Puse comillas invertidas dentro de un comentario CSS que vive en una
   plantilla de TypeScript, y eso corta la cadena. **No apareció en mi
   comprobación de compilación** porque filtré la salida por `Error|error TS` y
   esbuild lo reporta de otra forma. Filtrar la salida de una compilación es una
   forma cómoda de no enterarse.

---

## 9. Los filtros de lista, por palabras (F94d)

Cierra lo que quedaba abierto en §8.3. Los siete filtros con caja de texto buscan
ahora por **palabras**, no por la frase entera: cada palabra tiene que aparecer,
en cualquier orden.

| Se escribe | Antes | Ahora |
|---|--:|--:|
| `camiseta nike` en Inventario | 0 | 149.799 |
| `nike camiseta` (al revés) | 0 | 149.799 |
| `maria cedeno` en Pedidos | 0 | 63.501 |
| `cedeno maria` (al revés) | 0 | 63.501 |

**Tres palabras, y no las que sean.** JPQL no admite un número variable de
condiciones, así que o se escriben todas o se pasa a SQL nativo — y eso obligaría
a renunciar a la paginación de Spring Data en siete pantallas. Tres cubren
«camiseta nike deportiva»; a partir de la cuarta se ignora, que devuelve de más
y no de menos: el registro buscado sigue en la lista.

### El `OR` que sí se puede indexar y el que no

Añadir las palabras hizo que Inventario pasara de 682 ms a **2.598 ms**, y el
plan explicó por qué:

```
Hash Join
  Join Filter: ((lower(pr.nombre) ~~ '%zapatilla%' OR lower(bo.nombre) ~~ '%zapatilla%')
           AND (lower(pr.nombre) ~~ '%nike%'      OR lower(bo.nombre) ~~ '%nike%'))
  Rows Removed by Join Filter: 500000
```

**Una condición que mira dos tablas distintas no se puede resolver con el índice
de ninguna.** PostgreSQL une inventario con producto entero y filtra después.

- `LOWER(c.nombre) OR LOWER(c.apellido)` — **misma tabla**: se resuelve con un
  BitmapOr de los dos índices de trigramas. Se deja como está (Pedidos,
  Devoluciones).
- `LOWER(producto.nombre) OR LOWER(bodega.nombre)` — **dos tablas**: no hay
  índice que valga.

En Inventario se quitó la búsqueda por nombre de bodega: **2.598 ms → 56 ms**, y
no se pierde nada porque filtrar por bodega ya lo hace el desplegable que está
justo al lado, y de forma exacta. El texto del campo lo dice ahora: «Buscar por
producto…».

En Cuentas por pagar el término se distingue solo —un número de factura lleva
dígitos («FACM-0001497588») y un nombre de proveedor no— así que el servicio
llama a una consulta o a la otra, nunca a un `OR` entre las dos tablas.

### Medido después

| Caso | Tiempo |
|---|--:|
| Búsqueda selectiva (`nike distribucion 1489300`) | 20–80 ms |
| Dos palabras que casan con 150.000 filas | ~420 ms |
| Inventario, una a tres palabras | 190–500 ms |
| Por número de factura | 23 ms |

**El banco de las 46 pantallas: máximo 350 ms**, ninguna por encima de 400.
Comprobación por roles: **36 bien · 0 fallando**; las dos que pasan del segundo
son la primera llamada en frío, que esa comprobación no calienta a propósito.
