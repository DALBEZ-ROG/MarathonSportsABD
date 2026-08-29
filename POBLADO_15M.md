# Carga a 1.500.000 filas por tabla — Fase 91

Segunda carga de volumen sobre `mod_venta_inve`, muy por encima de la de la
fase 38. Objetivo: **1.500.000 filas en cada tabla**, no en el total de la base.

| | Antes (fase 38) | Ahora (fase 91) |
|---|--:|--:|
| Filas en las 34 tablas | ~1.003.000 | **54.207.156** |
| Tamano de la base | 305 MB | **12 GB** |
| Tablas en el objetivo | — | **34 de 34** |

Scripts, en orden de ejecucion:

| Archivo | Papel |
|---|---|
| `marathon-backend/sql/fase91_andamiaje.sql` | Andamiaje comun; lo cargan todas las etapas con `\ir` |
| `fase91_carga_15m_etapa1_maestras.sql` | usuario, proveedor, transportista, materia_prima, cliente, producto |
| `fase91_carga_15m_etapa2_nivel1.sql` | Las 10 tablas que solo dependen de las maestras |
| `fase91_carga_15m_etapa3_lineas.sql` | Lineas, totales y movimiento (8 tablas) |
| `fase91_carga_15m_etapa3b_rebalanceo.sql` | **Coherencia entre cabecera y detalle** |
| `fase91_carga_15m_etapa3c_reservas.sql` | El resto de las reservas |
| `fase91_carga_15m_etapa4_compras_devoluciones.sql` | Compras, cuentas por pagar y devoluciones (10 tablas) |
| `fase91_carga_15m_etapa5_verificacion.sql` | Reactivacion de triggers y verificacion |

Las etapas 1 y 3 escriben columnas cifradas, asi que van por
`gestionar_clave.ps1 -Accion Ejecutar -PgPort 5433`, que publica la clave en la
sesion y conecta como `postgres` (hacen falta `ALTER TABLE ... DISABLE TRIGGER`,
y las 47 tablas son de `postgres`).

---

## 1. Que NO se cargo, y por que

**Ocho tablas se quedaron con su tamano real**: `rol` (6), `permiso` (95),
`rol_permiso` (201), `usuario_rol` (6), `unidad_medida` (9), `categoria` (3),
`ciudad` (88), `bodega` (20).

No es que no se pudiera. Es que un millon de roles no es volumen: es
`MATRIZ_ROLES.md` destruido, las pruebas de las fases 34 y 48 sin sentido y los
desplegables del frontend cargando un millon de ciudades. Son dimensiones y
modelo de permisos, no hechos. Verificado al final: las ocho siguen intactas.

Los 1.500.000 usuarios nuevos **no pueden entrar al sistema**: llevan un hash
bcrypt valido en formato pero de un secreto aleatorio que nadie conoce, y no
tienen ninguna fila en `usuario_rol`.

---

## 2. El cifrado de volumen

`cliente` y `proveedor` guardan correo, telefono, direccion y contacto cifrados:
10,5 millones de valores. Medido en este equipo antes de escribir una sola fila:

| Via | ms/valor | Tamano de salida | 10,5M de valores |
|---|--:|--:|--:|
| `fn_cifrar` (s2k por defecto) | 0,203 | 92 B | ~35 min |
| `carga.cifrar` (`s2k-count=1024`) | **0,0077** | 92 B | **~80 s** |

Mismo algoritmo, misma clave, mismo tamano, y **`fn_descifrar` lo lee sin
cambio alguno** porque los parametros PGP viajan dentro del propio mensaje. Se
comprobo en el ensayo, no se supuso.

El `s2k-count` protege contra la fuerza bruta de una **contrasena** debil. Aqui
la clave son 32 bytes aleatorios, asi que bajarlo no cede nada.

El `correo_hash` se calcula desde el texto en claro en vez de dejarselo al
trigger, que descifraria cada correo solo para volver a hashearlo. El ensayo
comprobo que el valor resultante es identico al que habria puesto el trigger.

---

## 3. El rebalanceo: donde el encargo chocaba consigo mismo

Pedir 1,5M en `pedido` **y** 1,5M en `detalle_pedido` es incompatible con que
todo pedido tenga lineas. Las 614.511 lineas que ya existian pertenecen a solo
230.004 pedidos (2,67 cada uno, que es lo normal), asi que a los 1.269.996
pedidos nuevos les quedaban 885.489 lineas: **384.507 pedidos vacios y con
total 0**. Lo mismo en compras, con 5.332 ordenes sin ninguna linea.

La salida fue subir las tablas de LINEA **por encima** de 1,5M. El encargo era
"mas de un millon, o metele 1,5": un suelo, no un techo. Y una tabla de detalle
mayor que su cabecera es justo la forma que tienen los datos de verdad.

| | Antes del rebalanceo | Despues |
|---|--:|--:|
| `detalle_pedido` | 1.500.000 | **3.154.503** |
| `orden_compra_detalle` | 1.500.000 | **3.002.653** |
| `recepcion_mercancia_detalle` | — | **1.550.000** |
| Pedidos sin linea | 384.507 | **0** |
| Ordenes sin linea | 5.332 | **0** |
| Lineas por pedido nuevo | 0,70 | **2,00** |
| Reservas de un producto ajeno al pedido | 76.666 | **0** |

Lo que el rebalanceo arrastro:

- `pedido.total` y `orden_compra.total` recalculados con la formula del
  sistema (`suma(subtotal) - descuento`, con suelo en 0).
- `comprobante_interno.total` **tenia** que seguir al nuevo total de su pedido,
  o se rompia la igualdad que `trg_validar_total_comprobante` existe para
  defender. Se aprovecho para repartir los comprobantes sobre todos los pedidos.
- `solicitud_devolucion` repartida sobre todos los pedidos en vez de amontonada.
- Las tablas hijas que se generaban por aritmetica de indices pasaron a
  generarse por `JOIN` real, porque la aritmetica solo era correcta mientras
  hubiera una linea por cabecera.

**Los `UPDATE` agregados solo tocan el tramo generado.** Los pedidos y las
ordenes anteriores conservan su total: recalcularselo seria reescribir datos
reales del duenno con una formula reconstruida.

### El detalle de las reservas

`uq_reserva_pedido_producto_activa` es un UNIQUE **parcial** sobre
`(id_pedido, id_producto)` que solo mira las reservas `'activa'`. Por eso dos
reservas activas del mismo pedido no pueden compartir producto, y 76.666 se
quedaron sin uno propio cuando su pedido no tenia tantos productos distintos.

No se les invento un producto ajeno: se reconocio lo que son, una reserva ya
cerrada. Con estado `'consumida'` el UNIQUE deja de aplicar, el producto puede
ser el del pedido y `chk_reserva_cierre` se satisface con la fecha de cierre
que ese estado exige.

---

## 4. Las fechas

**Ninguna fila generada pasa del 31/07/2026.** El tope vive en el modulo de
`carga.fecha()` (943 dias desde el 2024-01-01, y 2024-01-01 + 942 = 2026-07-31),
no en un comentario: por construccion no puede salir otra cosa. Donde hay que
sumar un intervalo a una fecha —un vencimiento, la expiracion de un token, el
fin de una orden— el resultado pasa por `carga.tope()`.

La verificacion recorre las **50 columnas de fecha** de las 34 tablas. Senalo
15 con valores posteriores a julio de 2026; todas resultaron ser:

- datos previos de la carga de la fase 39 (`movimiento_materia_prima`, cuyos
  identificadores tienen huecos, asi que "id alto" no significaba "mio"), o
- filas que la aplicacion escribio **hoy** mientras la carga corria.

Contadas por la marca de texto que llevan las filas generadas:

| Tabla | Filas mias | Mias posteriores a julio de 2026 |
|---|--:|--:|
| `movimiento_materia_prima` | 1.492.199 | **0** |
| `log_accion` | 1.299.855 | **0** |
| `movimiento_inventario` | 1.419.980 | **0** |

---

## 5. Los triggers

Once triggers apagados durante la carga, y por que cada uno:

| Trigger | Motivo |
|---|---|
| `trg_auditoria_usuario` / `_producto` / `_proveedor` / `_cliente` | Escribirian 6.000.000 de filas de rebote en `auditoria_cambios`, que se puebla aparte con su propio contenido |
| `trg_cliente_hash_correo` | Descifraria cada correo solo para volver a hashearlo |
| `trg_recalcular_total_pedido_insert` / `trg_oc_total_insert` | Son `FOR EACH STATEMENT`: una sola ejecucion, pero recalcula el total de **todos** los pedidos del lote |
| `trg_proteger_total_pedido` / `trg_proteger_total_oc` | **Bloquearian** el `UPDATE` agregado que reconstruye los totales. Son su razon de ser |
| `trg_recalcular_total_por_descuento` | Recalcularia fila a fila durante ese mismo `UPDATE` |
| `trg_pedido_updated_at` | Pondria `now()`, que hoy es 29/08/2026 y se saldria del tope de julio |
| `trg_cxp_pagado_insert` / `trg_proteger_monto_pagado_cxp` | Lo mismo, para `cuenta_por_pagar.monto_pagado` |

**Dejados encendidos a proposito**, como comprobacion gratuita:

- `trg_validar_total_comprobante` compara el total del comprobante con el del
  pedido y aborta si difieren. 1,5 millones de comprobaciones de que el `UPDATE`
  agregado quedo bien. Los paso todos.
- `trg_validar_bom_producto_fabricado` y `trg_validar_op_producto_fabricado`
  exigen que el producto sea `'fabricado'`. 3 millones de comprobaciones de que
  la eleccion de origen de la etapa 1 fue la correcta.

Verificado contra `pg_trigger` al final: **30 activos, 0 apagados.**

---

## 6. Dos fallos que costaron una reejecucion

**Desbordamiento de entero.** Las secciones que generan desde un `JOIN` (no
desde `generate_series`) multiplican una PK `integer` por 7919 o 104729, y
`1.500.000 * 7919` se sale de `int4`. Las etapas anteriores no lo sufrian
porque `generate_series` devuelve `bigint`. Arreglado con `::bigint`.

**`carga.cerrar` borraba el rango anotado.** Al reanudar tras un fallo, una
tabla ya completa inserta 0 filas y el procedimiento sobrescribia su rango con
uno vacio, dejando sin padre a las tablas que colgaban de el. Ahora, sin filas
nuevas, sale sin tocar `carga.rango`.

---

## 7. El esquema `carga` no se borra

`carga.rango` guarda, por tabla, el primer y ultimo identificador **generado**.
Todo lo que queda por debajo de `id_min` son datos reales anteriores a esta
carga. Es la unica forma de poder deshacer la carga mas adelante sin tocar lo
de antes.

---

## 8. Verificacion final

| Comprobacion | Resultado |
|---|---|
| Tablas que llegan a 1.500.000 | **34 de 34** |
| Triggers activos | **30 de 30** |
| Catalogos y modelo de permisos | **intactos** |
| Filas generadas posteriores a julio de 2026 | **0** |
| Pedidos con el total descuadrado | **0** |
| Ordenes de compra con el total descuadrado | **0** |
| Comprobantes que no cuadran con su pedido | **0** |
| Cuentas pagadas por encima de su total | **0** |
| Pedidos y ordenes sin lineas | **0** |
| Reservas de un producto ajeno a su pedido | **0** |
| Filas en las 34 tablas | **54.207.156** |
| Tamano de la base | **12 GB** |
