# SETUP COMPLETO — Marathon Sports (BD desde cero)

Guía para levantar la base de datos `mod_venta_inve` **desde cero en cualquier máquina**, en el **orden EXACTO** requerido. Cada paso depende del anterior.

> **Base de datos:** `mod_venta_inve` — PostgreSQL 17+ (el proyecto usa la 18)
> **Usuario:** `postgres` (ajusta según tu entorno)
> **Todos los scripts SQL viven en** `marathon-backend/sql/`
>
> **Validado sobre un clúster limpio el 16/08/2026.** Los cuatro fallos que
> aparecieron entonces —y que meses de trabajo sobre la base existente no podían
> revelar— están corregidos y documentados al final, en
> [Fallos que solo aparecen en un equipo limpio](#fallos-que-solo-aparecen-en-un-equipo-limpio).

> ### ⚡ Atajo: hacer todo esto con un comando
>
> **Este documento cubre las fases 0 a 32**, que dejan el esquema y los datos de
> demostración. El proyecto sigue hasta la **fase 43**: índices, los 6 roles y
> sus privilegios, respaldos, auditoría, cifrado y el millón de filas.
>
> Para levantarlo **entero y en orden**, sin ir script por script:
>
> ```powershell
> powershell -ExecutionPolicy Bypass -File scripts\migracion\construir_desde_cero.ps1 -Etapa Esquema
> #   ... arrancar el backend una vez y pararlo ...
> powershell -ExecutionPolicy Bypass -File scripts\migracion\construir_desde_cero.ps1 -Etapa Datos
> ```
>
> Ese script ejecuta exactamente los pasos de abajo y luego las fases 33 a 43,
> verificando al final. Está documentado en
> **[GUIA_REPLICACION.md](./GUIA_REPLICACION.md) §12**.
>
> Lo que sigue en este documento es útil para entender **qué hace cada fase** y
> para ejecutarlas sueltas cuando algo falla.

---

## Requisitos previos

- **PostgreSQL 17 o superior** con cliente `psql` en el `PATH` (el proyecto usa
  la **18**). Los pasos 1–15 de este documento funcionan desde la 15, pero las
  fases 33–43 **no**: los respaldos diferenciales dependen de `summarize_wal` y
  `pg_basebackup --incremental`, que no existen antes de la 17.
- Java 17+ y Maven 3.9+ (para el **paso 12**, arrancar el backend). El
  repositorio no incluye `mvnw`.
- Acceso con un usuario con permisos para `CREATE DATABASE`

Para todos los comandos, exporta la contraseña una vez (evita el prompt):

```bash
# Linux / macOS
export PGPASSWORD='tu_password'
```
```powershell
# Windows PowerShell
$env:PGPASSWORD='tu_password'; $env:PGCLIENTENCODING='UTF8'
```

---

## Orden EXACTO de ejecución

### 1. Crear la base de datos

```bash
psql -U postgres -d postgres -c "CREATE DATABASE mod_venta_inve;"
```

### 2. DDL base — 20 tablas F1–F20 (+ funciones y triggers base)

Archivo: **`marathon-backend/sql/fase00_ddl_base.sql`**

```bash
psql -U postgres -d mod_venta_inve -v ON_ERROR_STOP=1 -f marathon-backend/sql/fase00_ddl_base.sql
```

> **Nota:** El repositorio **no incluía** un DDL base (era una dependencia implícita que solo existía en la BD viva). Este archivo se **generó con `pg_dump --schema-only`** sobre las 20 tablas base y se le antepusieron las funciones de sus triggers (porque `pg_dump -t` no exporta funciones). Ver detalle en `DEUDA_TECNICA.md`. Las 20 tablas base son: `bodega, categoria, ciudad, cliente, comprobante_interno, detalle_pedido, historial_inventario, inventario, log_accion, movimiento_inventario, pedido, permiso, producto, producto_proveedor, proveedor, rol, rol_permiso, unidad_medida, usuario, usuario_rol`.

### 3. Fase 21 — Órdenes de Compra

```bash
psql -U postgres -d mod_venta_inve -v ON_ERROR_STOP=1 -f marathon-backend/sql/fase21_ordenes_compra.sql
```

### 4. Fase 22 — Recepción de Mercancía

```bash
psql -U postgres -d mod_venta_inve -v ON_ERROR_STOP=1 -f marathon-backend/sql/fase22_recepcion_mercancia.sql
```

### 5. Fase 23 — Factura de Compra y Cuentas por Pagar

```bash
psql -U postgres -d mod_venta_inve -v ON_ERROR_STOP=1 -f marathon-backend/sql/fase23_factura_compra_cxp.sql
```

### 6. Fase 24 — Devolución de Cliente (RMA)

```bash
psql -U postgres -d mod_venta_inve -v ON_ERROR_STOP=1 -f marathon-backend/sql/fase24_devolucion_cliente.sql
```

### 7. Fase 25 — Devolución a Proveedor

```bash
psql -U postgres -d mod_venta_inve -v ON_ERROR_STOP=1 -f marathon-backend/sql/fase25_devolucion_proveedor.sql
```

### 8. Fase 26 — Kardex de Materia Prima

```bash
psql -U postgres -d mod_venta_inve -v ON_ERROR_STOP=1 -f marathon-backend/sql/fase26_kardex_materia_prima.sql
```

### 9. Fase 27 — Origen del Producto + Lista de Materiales (BOM)

Archivo real: **`marathon-backend/sql/fase27_origen_producto_bom.sql`**

```bash
psql -U postgres -d mod_venta_inve -v ON_ERROR_STOP=1 -f marathon-backend/sql/fase27_origen_producto_bom.sql
```

> **Nota de nombre:** en algunas notas se menciona como `fase27_bom_origen.sql`; el nombre **real en el repo** es `fase27_origen_producto_bom.sql`.

### 10. Fase 28 — Órdenes de Producción

Archivo: **`marathon-backend/sql/fase28_ordenes_produccion.sql`**

```bash
psql -U postgres -d mod_venta_inve -v ON_ERROR_STOP=1 -f marathon-backend/sql/fase28_ordenes_produccion.sql
```

> Crea `orden_produccion` y `orden_produccion_consumo`, el trigger `trg_validar_op_producto_fabricado`, y aplica el **retrofit** de la FK `fk_mmp_orden_produccion` sobre `movimiento_materia_prima` (pendiente desde F26). Idempotente.

### 11. Fase 29 — Costeo de Producción

Archivo: **`marathon-backend/sql/fase29_costeo_produccion.sql`**

```bash
psql -U postgres -d mod_venta_inve -v ON_ERROR_STOP=1 -f marathon-backend/sql/fase29_costeo_produccion.sql
```

> **No crea tablas** (siguen siendo 37): solo agrega columnas de costo (`materia_prima.costo_unitario_promedio`; `orden_produccion_consumo.costo_unitario_snapshot` y `costo_linea`; `orden_produccion.costo_materia_prima/costo_mano_obra/costo_indirecto/costo_total/costo_unitario_producido`), el trigger `trg_proteger_costo_materia_prima_op` y la función `fn_set_costo_materia_prima_op`. Idempotente.

> **Fase 30 — Reportes de Manufactura: NO requiere script SQL.** Es una fase de solo lectura (consultas agregadas, endpoints de reporte, dashboard y exportables); no crea tablas, columnas, vistas, funciones ni triggers. Solo código de aplicación — nada que ejecutar en la BD.

### 12. Arrancar el backend (crea roles, permisos y usuarios demo)

`DataInitializer` crea los **roles**, **permisos** y los **6 usuarios demo** (incluido `admin@marathon.com`). Esto **debe correr ANTES del seed**, porque el seed de pedidos requiere que exista `admin@marathon.com` (aborta con excepción si no está).

> ### ⚠ Este arranque, y solo este, se hace como `postgres`
>
> **En este punto de la construcción no existe ni un solo `GRANT`.** Los otorga
> `fase34_seguridad_roles.sql`, mucho después. Las 37 tablas son propiedad de
> `postgres`, y `usr_admin_marathon` —el usuario que `application-local.properties`
> configura para la aplicación— **no tiene ningún privilegio sobre ellas**, así que
> el `DataInitializer` falla nada más intentar escribir.
>
> Es la **única** vez en todo el proyecto que la aplicación usa el superusuario.
> A partir de la fase 34 manda el modelo de roles, y volver a usar `postgres`
> anularía todos los `GRANT` (un superusuario los ignora).

```powershell
cd marathon-backend
mvn -q -DskipTests spring-boot:run "-Dspring-boot.run.arguments=--spring.datasource.username=postgres --spring.datasource.password=<clave de postgres> --app.datasource.roles.enabled=false"
```

`--app.datasource.roles.enabled=false` es necesario por lo mismo: los otros cinco
pools por rol (F37) tampoco tienen privilegios todavía, y el arranque fallaría al
abrirlos.

Espera a **`Datos iniciales cargados correctamente`** y detenlo con `Ctrl+C`.

> Si `java -version` dice `1.8`, fuerza antes el JDK 17:
> `$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'`.
> El repositorio **no tiene `mvnw`**: Maven debe estar instalado aparte.

Usuarios demo creados (contraseña por defecto según `DataInitializer`):
`admin@marathon.com`, `supervisor@marathon.com`, `bodega@marathon.com`, `pedidos@marathon.com`, `compras@marathon.com`, `produccion@marathon.com`.

### 13. Seed de datos de negocio

Archivo: **`marathon-backend/sql/seed_marathon_sports.sql`**

```bash
psql -U postgres -d mod_venta_inve -v ON_ERROR_STOP=1 -f marathon-backend/sql/seed_marathon_sports.sql
```

> Carga ciudades, categorías, productos, proveedores, bodegas, inventario, clientes y pedidos. **NO** incluye roles/permisos/admin (los crea el `DataInitializer` en el paso 12). Ejecutar **UNA sola vez** (no es idempotente: reejecutarlo duplica datos o viola constraints UNIQUE).

### 13.1 Unidades de medida que faltan en el seed (obligatorio antes del paso 14)

Archivo: **`marathon-backend/sql/fase31_0_unidades_faltantes.sql`**

```bash
psql -U postgres -d mod_venta_inve -v ON_ERROR_STOP=1 -f marathon-backend/sql/fase31_0_unidades_faltantes.sql
```

> **Sin este paso, el paso 14 aborta en su primera sentencia:**
>
> ```
> ERROR:  insert or update on table "materia_prima" violates foreign key constraint "fk_materia_prima_unidad"
> DETAIL:  Key (id_unidad_medida)=(6) is not present in table "unidad_medida".
> ```
>
> El seed del paso 13 crea **tres** unidades de medida (Unidad, Par, Caja), pero
> `fase31` da de alta materias primas que referencian la **4** y la **6** —litros
> para las tintas, metros para la cinta elástica—. Este script completa hasta
> nueve.
>
> **Por qué no se había detectado nunca:** en la base de desarrollo había nueve
> unidades desde el principio, dadas de alta *por la aplicación* a lo largo del
> proyecto, y esas filas nunca llegaron a ningún script. Es la misma clase de
> dependencia invisible que documenta `DEUDA_TECNICA.md` sobre el DDL base: algo
> que solo existía en la base viva. Se destapó el 16/08/2026 al construir el
> entorno entero sobre un clúster limpio.
>
> Los ids van **explícitos** y con `OVERRIDING SYSTEM VALUE` (las 37 claves
> primarias son `GENERATED ALWAYS AS IDENTITY`). Si el orden lo decidiera la
> secuencia, la unidad 6 podría acabar siendo «Litro» y las materias primas
> quedarían medidas en unidades absurdas **sin violar ninguna restricción**:
> exactamente la clase de fallo que las restricciones no pueden ver.
>
> Es idempotente.

### 14. Seed de demostración de los bloques nuevos (Compras, Devoluciones, Manufactura)

Archivo: **`marathon-backend/sql/fase31_seed_demo_bloques_nuevos.sql`**

```bash
psql -U postgres -d mod_venta_inve -v ON_ERROR_STOP=1 -f marathon-backend/sql/fase31_seed_demo_bloques_nuevos.sql
```

> Datos **permanentes** de demostración para que los módulos nuevos tengan contenido realista: 10 materias primas, 3 productos fabricados con su BOM, 4 órdenes de compra (2 recibidas con costo promedio ponderado real, 1 aprobada pendiente, 1 borrador), 2 facturas con sus cuentas por pagar (una pagada, una con abono parcial), 3 devoluciones de cliente (RMA), 1 devolución a proveedor y 2 órdenes de producción (una completada con costeo, una planificada).
>
> **Es idempotente**: se puede ejecutar más de una vez sin duplicar (usa `ON CONFLICT` y un guard que detecta si ya se sembró). Respeta todas las reglas de negocio: no escribe columnas GENERATED ni calculadas por trigger, y fija `orden_produccion.costo_materia_prima` mediante `fn_set_costo_materia_prima_op()`.
>
> Requiere que los pasos 12 (usuarios demo) y 13 (seed base) se hayan ejecutado antes, porque referencia usuarios, proveedores, bodegas y pedidos existentes.

### 15. Fase 32 — Correcciones de deuda técnica (ÚLTIMO PASO, obligatorio)

Archivo: **`marathon-backend/sql/fase32_fixes.sql`**

```bash
psql -U postgres -d mod_venta_inve -v ON_ERROR_STOP=1 -f marathon-backend/sql/fase32_fixes.sql
```

> **No crea tablas ni columnas.** Reemplaza la función `fn_proteger_total_pedido` y recrea su trigger. La versión que viene en `fase00_ddl_base.sql` condiciona la excepción a `pg_trigger_depth() = 0`, condición que **nunca se cumple** dentro de un trigger (allí esa función vale 1), por lo que `UPDATE pedido SET total = 9999` pasaba sin error y la regla de negocio #1 del proyecto quedaba sin protección efectiva. Este script la reimplementa comparando contra el **total neto real** (`GREATEST(SUM(subtotal) − descuento, 0)`), el patrón ya validado en F21/F23/F29.
>
> **Sin este paso la base de datos queda con el bug.** Es la razón por la que es obligatorio y no opcional.
>
> Idempotente (`CREATE OR REPLACE` + `DROP TRIGGER IF EXISTS`) e **incluye su propia verificación**: al ejecutarlo emite dos avisos que deben decir `OK`:
>
> ```
> NOTICE:  OK: ninguna funcion usa pg_trigger_depth
> NOTICE:  OK: todos los pedidos tienen total coherente con el neto
> ```
>
> Puede ejecutarse en cualquier momento posterior al paso 2; se coloca al final para no interferir con los seeds. Verificado: ninguno de los seeds escribe `pedido.total` (dejan que el trigger lo calcule), así que el orden no afecta el resultado.

---

## Verificación final

Ejecuta esta query. Debe devolver **37 tablas** y los conteos de datos de negocio esperados.

```sql
-- Total de tablas (esperado: 37)
SELECT COUNT(*) AS total_tablas
FROM information_schema.tables
WHERE table_schema = 'public';

-- Conteos de datos de negocio
SELECT 'ciudad'          AS tabla, COUNT(*) AS filas, 88  AS esperado FROM ciudad
UNION ALL SELECT 'categoria',       COUNT(*),  3   FROM categoria
UNION ALL SELECT 'producto',        COUNT(*),  105 FROM producto
UNION ALL SELECT 'proveedor',       COUNT(*),  6   FROM proveedor
UNION ALL SELECT 'bodega',          COUNT(*),  20  FROM bodega
UNION ALL SELECT 'inventario',      COUNT(*),  265 FROM inventario
UNION ALL SELECT 'cliente',         COUNT(*),  40  FROM cliente
UNION ALL SELECT 'pedido',          COUNT(*),  25  FROM pedido
UNION ALL SELECT 'detalle_pedido',  COUNT(*),  68  FROM detalle_pedido
ORDER BY tabla;

-- Columnas y objetos de costeo (F29). El total de tablas NO cambia (37):
-- esta fase solo agrega columnas, un trigger y una función.
SELECT
  (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_name='materia_prima' AND column_name='costo_unitario_promedio')            AS mp_costo_promedio,      -- esperado 1
  (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_name='orden_produccion_consumo'
       AND column_name IN ('costo_unitario_snapshot','costo_linea'))                        AS opc_cols_costo,         -- esperado 2
  (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_name='orden_produccion'
       AND column_name IN ('costo_materia_prima','costo_mano_obra','costo_indirecto',
                           'costo_total','costo_unitario_producido'))                       AS op_cols_costo,          -- esperado 5
  (SELECT COUNT(*) FROM pg_trigger
     WHERE tgname='trg_proteger_costo_materia_prima_op')                                    AS trigger_costo,          -- esperado 1
  (SELECT COUNT(*) FROM pg_proc
     WHERE proname='fn_set_costo_materia_prima_op')                                         AS funcion_costo;          -- esperado 1

-- Conteos de los módulos nuevos tras el seed demo (paso 14)
SELECT 'materia_prima'                AS tabla, COUNT(*) AS filas, 10 AS esperado FROM materia_prima
UNION ALL SELECT 'lista_materiales (BOM)',       COUNT(*), 12 FROM lista_materiales
UNION ALL SELECT 'producto origen=fabricado',    COUNT(*),  3 FROM producto WHERE origen='fabricado'
UNION ALL SELECT 'orden_compra',                 COUNT(*),  4 FROM orden_compra
UNION ALL SELECT 'orden_compra_detalle',         COUNT(*), 10 FROM orden_compra_detalle
UNION ALL SELECT 'recepcion_mercancia',          COUNT(*),  2 FROM recepcion_mercancia
UNION ALL SELECT 'recepcion_mercancia_detalle',  COUNT(*),  7 FROM recepcion_mercancia_detalle
UNION ALL SELECT 'factura_compra',               COUNT(*),  2 FROM factura_compra
UNION ALL SELECT 'cuenta_por_pagar',             COUNT(*),  2 FROM cuenta_por_pagar
UNION ALL SELECT 'pago_proveedor',               COUNT(*),  2 FROM pago_proveedor
UNION ALL SELECT 'solicitud_devolucion',         COUNT(*),  3 FROM solicitud_devolucion
UNION ALL SELECT 'solicitud_devolucion_detalle', COUNT(*),  3 FROM solicitud_devolucion_detalle
UNION ALL SELECT 'reembolso_cliente',            COUNT(*),  1 FROM reembolso_cliente
UNION ALL SELECT 'devolucion_proveedor',         COUNT(*),  1 FROM devolucion_proveedor
UNION ALL SELECT 'devolucion_proveedor_detalle', COUNT(*),  1 FROM devolucion_proveedor_detalle
UNION ALL SELECT 'movimiento_materia_prima',     COUNT(*), 11 FROM movimiento_materia_prima
UNION ALL SELECT 'orden_produccion',             COUNT(*),  2 FROM orden_produccion
UNION ALL SELECT 'orden_produccion_consumo',     COUNT(*),  8 FROM orden_produccion_consumo
UNION ALL SELECT 'usuarios demo (6 roles)',      COUNT(*),  6 FROM usuario
  WHERE correo IN ('admin@marathon.com','supervisor@marathon.com','bodega@marathon.com',
                   'pedidos@marathon.com','compras@marathon.com','produccion@marathon.com')
ORDER BY tabla;

-- Integridad del costeo: el costo promedio ponderado debe igualar el precio de
-- compra de cada material recibido una sola vez (esperado: 0 filas con 'REVISAR')
SELECT mp.nombre, mp.costo_unitario_promedio,
       CASE WHEN mp.costo_unitario_promedio =
                 (SELECT ocd.precio_unitario FROM orden_compra_detalle ocd
                   WHERE ocd.id_materia_prima = mp.id_materia_prima LIMIT 1)
            THEN 'OK' ELSE 'REVISAR' END AS costo_ok
FROM materia_prima mp
WHERE mp.costo_unitario_promedio > 0;
```

Resultado esperado (tras el seed base; el paso 14 añade productos e inventario):

| tabla | filas (esperado) |
|-------|------------------|
| bodega | 20 |
| categoria | 3 |
| ciudad | 88 |
| cliente | 40 |
| detalle_pedido | 68 |
| inventario | 265 |
| pedido | 25 |
| producto | 105 |
| proveedor | 6 |
| **Total de tablas** | **37** |
| mp_costo_promedio / opc_cols_costo / op_cols_costo | 1 / 2 / 5 |
| trigger_costo / funcion_costo | 1 / 1 |

> Los conteos de datos de negocio dependen del contenido de `seed_marathon_sports.sql`. Las 37 tablas dependen solo de los pasos 2–11 (DDL) y se validaron ejecutando la secuencia completa `fase00`→`fase29` sobre una BD vacía. La F29 no agrega tablas, por eso se verifican sus columnas/trigger/función.

---

## Resumen del orden

| # | Paso | Artefacto |
|---|------|-----------|
| 1 | Crear BD | `CREATE DATABASE mod_venta_inve` |
| 2 | DDL base (20 tablas F1–F20) | `fase00_ddl_base.sql` |
| 3 | Órdenes de Compra | `fase21_ordenes_compra.sql` |
| 4 | Recepción de Mercancía | `fase22_recepcion_mercancia.sql` |
| 5 | Factura + CxP | `fase23_factura_compra_cxp.sql` |
| 6 | Devolución Cliente | `fase24_devolucion_cliente.sql` |
| 7 | Devolución Proveedor | `fase25_devolucion_proveedor.sql` |
| 8 | Kardex Materia Prima | `fase26_kardex_materia_prima.sql` |
| 9 | Origen + BOM | `fase27_origen_producto_bom.sql` |
| 10 | Órdenes de Producción | `fase28_ordenes_produccion.sql` |
| 11 | Costeo de Producción (solo columnas + trigger) | `fase29_costeo_produccion.sql` |
| 12 | Roles/permisos/usuarios demo | Arrancar backend (`DataInitializer`) — **como `postgres`**, ver el paso |
| 13 | Datos de negocio | `seed_marathon_sports.sql` |
| 13.1 | **Unidades de medida 4–9 (obligatorio)** | `fase31_0_unidades_faltantes.sql` |
| 14 | Demo de Compras, Devoluciones y Manufactura | `fase31_seed_demo_bloques_nuevos.sql` |
| 15 | **Correcciones de deuda técnica (obligatorio)** | `fase32_fixes.sql` |

Y a partir de aquí el proyecto continúa hasta la **fase 43**: índices, los 6
roles de PostgreSQL, respaldos, auditoría, cifrado y el millón de filas. Esa
segunda mitad no se detalla en este documento; la ejecuta en orden
`scripts\migracion\construir_desde_cero.ps1 -Etapa Datos` y está documentada en
**[GUIA_REPLICACION.md](./GUIA_REPLICACION.md) §12**.

> **Nota:** la fase 30 no tiene script SQL — es solo código de aplicación (reportes y dashboard de manufactura), no toca el esquema.

---

## Verificación de integridad (opcional, recomendada)

Comprobación final de que la base quedó consistente. Los cuatro bloques deben devolver los valores indicados.

```sql
-- 1) Objetos del esquema: 37 tablas, 17 funciones, 24 triggers
SELECT
  (SELECT COUNT(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
     WHERE n.nspname='public' AND c.relkind='r')                       AS tablas,        -- 37
  (SELECT COUNT(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
     WHERE n.nspname='public' AND p.prokind='f')                       AS funciones,     -- 17
  (SELECT COUNT(*) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
     JOIN pg_namespace n ON n.oid=c.relnamespace
     WHERE n.nspname='public' AND NOT t.tgisinternal)                  AS triggers_,     -- 24
  (SELECT COUNT(*) FROM pg_constraint
     WHERE contype='f' AND connamespace='public'::regnamespace)        AS fks;           -- 70

-- 2) Ninguna función debe usar pg_trigger_depth (esperado: 0 filas)
SELECT p.proname FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname='public' AND p.prokind='f'
  AND pg_get_functiondef(p.oid) LIKE '%pg_trigger_depth%';

-- 3) Integridad referencial real: revalida cada fila de las 70 FKs.
--    Si alguna tiene huérfanos, emite un WARNING con el nombre.
DO $$
DECLARE r record; fallos int := 0;
BEGIN
  FOR r IN SELECT conrelid::regclass AS tab, conname FROM pg_constraint
           WHERE contype='f' AND connamespace='public'::regnamespace LOOP
    BEGIN
      EXECUTE format('ALTER TABLE %s VALIDATE CONSTRAINT %I', r.tab, r.conname);
    EXCEPTION WHEN others THEN
      fallos := fallos + 1;
      RAISE WARNING 'FK ROTA: %.% -> %', r.tab, r.conname, SQLERRM;
    END;
  END LOOP;
  IF fallos = 0 THEN RAISE NOTICE 'OK: 0 FKs con huerfanos';
  ELSE RAISE WARNING '% FKs con problemas', fallos; END IF;
END $$;

-- 4) Coherencia de las columnas calculadas (todos los conteos deben dar 0)
SELECT
  (SELECT COUNT(*) FROM pedido p WHERE p.total <> GREATEST(
     COALESCE((SELECT SUM(d.subtotal) FROM detalle_pedido d WHERE d.id_pedido=p.id_pedido),0)
     - p.descuento, 0))                                                AS pedido_total_malo,
  (SELECT COUNT(*) FROM orden_compra o WHERE o.total <>
     COALESCE((SELECT SUM(d.subtotal) FROM orden_compra_detalle d
                WHERE d.id_orden_compra=o.id_orden_compra),0))         AS oc_total_malo,
  (SELECT COUNT(*) FROM cuenta_por_pagar c WHERE c.monto_pagado <>
     COALESCE((SELECT SUM(pp.monto) FROM pago_proveedor pp
                WHERE pp.id_cuenta_pagar=c.id_cuenta_pagar),0))        AS cxp_pagado_malo,
  (SELECT COUNT(*) FROM lista_materiales b JOIN producto p USING (id_producto)
     WHERE p.origen <> 'fabricado')                                    AS bom_no_fabricado,
  (SELECT COUNT(*) FROM inventario WHERE stock_actual < 0)             AS stock_negativo,
  (SELECT COUNT(*) FROM materia_prima WHERE stock_actual < 0)          AS mp_negativa;
```

---

## Fallos que solo aparecen en un equipo limpio

Esta guía se validó durante meses **sobre una base que ya existía**, y eso oculta
una clase entera de defectos: los que dependen de algo que estaba en la base viva
pero en ningún script. El 16/08/2026 se construyó el entorno completo sobre un
clúster recién creado con `initdb`, y aparecieron cuatro. Los cuatro están
corregidos; se dejan documentados con su **síntoma**, porque es lo que se busca
cuando algo falla.

| # | Síntoma | Causa | Dónde |
|---|---|---|---|
| 1 | El `DataInitializer` falla al escribir, o el arranque no abre los pools | **No existe ningún `GRANT` hasta la fase 34.** `usr_admin_marathon` no tiene privilegios sobre unas tablas que son de `postgres` | Paso 12 |
| 2 | `violates foreign key constraint "fk_materia_prima_unidad"` · `Key (id_unidad_medida)=(6) is not present` | El seed crea 3 unidades de medida y `fase31` referencia la 4 y la 6 **por número** | Paso 13.1 |
| 3 | `summarize_wal quedo en "off" y debe estar en "on"` | Carrera: `fase35` comprobaba el valor tras un `pg_sleep(2)` fijo, y `pg_reload_conf()` es asíncrono. Sobre un clúster recién creado dos segundos no bastan | `fase35_respaldo_prerequisitos.sql`, ahora sondea 15 s |
| 4 | El cifrado se aplica a la base equivocada | `gestionar_clave.ps1 -Accion Ejecutar` tenía `-h localhost -p 5432` **fijo**, así que `-Base` prometía algo que no cumplía | `gestionar_clave.ps1`, ahora acepta `-PgHost`/`-PgPort` |

### Si vas a probar la cadena entera, hazlo en un clúster aparte

**No uses una base desechable del mismo servidor.** `fase34_seguridad_roles.sql`
hace `DROP ROLE` de los seis roles, y los roles son objetos del **clúster**, no
de la base; además lleva `mod_venta_inve` escrito a mano en un
`REVOKE ... ON DATABASE`. Ejecutarlo contra `mi_base_de_pruebas` toca los roles
de la base real.

```powershell
initdb -D C:\pgtest -U postgres --pwfile=<archivo con la clave> -E UTF8
pg_ctl -D C:\pgtest -o "-p 5434" -l C:\pgtest\server.log start
# ... construir con -PgPort 5434 ...
pg_ctl -D C:\pgtest -m fast stop
```

La construcción completa sobre ese clúster tarda **~60 s** (más el arranque del
backend del paso 12), y termina con 38 tablas, 1.011.313 filas de negocio y los
cuatro arneses en verde: **61/61** privilegios, **29/29** auditoría, **51/51**
cifrado y 0 violaciones en 238 comprobaciones de integridad.
