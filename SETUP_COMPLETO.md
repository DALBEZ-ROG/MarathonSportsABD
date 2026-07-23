# SETUP COMPLETO — Marathon Sports (BD desde cero)

Guía para levantar la base de datos `mod_venta_inve` **desde cero en cualquier máquina**, en el **orden EXACTO** requerido. Cada paso depende del anterior.

> **Base de datos:** `mod_venta_inve` — PostgreSQL 15+
> **Usuario:** `postgres` (ajusta según tu entorno)
> **Todos los scripts SQL viven en** `marathon-backend/sql/`

---

## Requisitos previos

- PostgreSQL 15+ con cliente `psql` en el `PATH`
- Java 17+ y Maven 3.9+ (para el paso 10, arrancar el backend)
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

### 11. Arrancar el backend (crea roles, permisos y usuarios demo)

`DataInitializer` crea los **roles**, **permisos** y los **6 usuarios demo** (incluido `admin@marathon.com`). Esto **debe correr ANTES del seed**, porque el seed de pedidos requiere que exista `admin@marathon.com` (aborta con excepción si no está).

```bash
cd marathon-backend
mvn spring-boot:run
```

Espera a ver el log de inicialización de usuarios y luego puedes detenerlo (Ctrl+C) si solo querías sembrar la BD.

Usuarios demo creados (contraseña por defecto según `DataInitializer`):
`admin@marathon.com`, `supervisor@marathon.com`, `bodega@marathon.com`, `pedidos@marathon.com`, `compras@marathon.com`, `produccion@marathon.com`.

### 12. Seed de datos de negocio

Archivo: **`marathon-backend/sql/seed_marathon_sports.sql`**

```bash
psql -U postgres -d mod_venta_inve -v ON_ERROR_STOP=1 -f marathon-backend/sql/seed_marathon_sports.sql
```

> Carga ciudades, categorías, productos, proveedores, bodegas, inventario, clientes y pedidos. **NO** incluye roles/permisos/admin (los crea el `DataInitializer` en el paso 11). Ejecutar **UNA sola vez** (no es idempotente: reejecutarlo duplica datos o viola constraints UNIQUE).

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
```

Resultado esperado:

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

> Los conteos de datos de negocio dependen del contenido de `seed_marathon_sports.sql`. Las 37 tablas dependen solo de los pasos 2–10 (DDL) y se validaron ejecutando la secuencia completa `fase00`→`fase28` sobre una BD vacía.

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
| 11 | Roles/permisos/usuarios demo | Arrancar backend (`DataInitializer`) |
| 12 | Datos de negocio | `seed_marathon_sports.sql` |
