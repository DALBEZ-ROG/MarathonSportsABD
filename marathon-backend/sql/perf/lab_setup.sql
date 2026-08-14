-- ============================================================================
-- LABORATORIO DE MEDICION DE RENDIMIENTO  (esquema perf_lab)
-- ----------------------------------------------------------------------------
-- Proposito: medir el efecto real de los indices candidatos a VOLUMEN DE
-- PRODUCCION PROYECTADO. La BD real (public) tiene como maximo 267 filas en su
-- tabla mas grande; a esa escala PostgreSQL siempre elige Seq Scan porque
-- recorrer 1-3 paginas es mas barato que descender un arbol B, asi que un
-- EXPLAIN ANALYZE sobre public NO puede discriminar entre un indice util y uno
-- inutil. Este esquema replica la FORMA de las tablas criticas con volumen
-- realista para que la comparacion antes/despues sea significativa.
--
-- perf_lab es DESECHABLE: no lo usa la aplicacion, no tiene FKs hacia public,
-- y se elimina con  DROP SCHEMA perf_lab CASCADE;
-- No modifica ninguna tabla de public.
--
-- Volumenes elegidos (proyeccion a ~3 anios de operacion de una cadena retail):
--   cliente          5.000
--   producto         2.000
--   bodega              20
--   pedido         200.000
--   detalle_pedido 600.000  (3 lineas por pedido en promedio)
--   inventario      40.000  (2.000 productos x 20 bodegas)
--   log_accion     500.000
-- ============================================================================

DROP SCHEMA IF EXISTS perf_lab CASCADE;
CREATE SCHEMA perf_lab;
SET search_path = perf_lab;

-- ---------------------------------------------------------------- catalogos --
CREATE TABLE bodega (
    id_bodega   int PRIMARY KEY,
    nombre      varchar(100) NOT NULL,
    estado      varchar(20)  NOT NULL DEFAULT 'activo'
);

CREATE TABLE cliente (
    id_cliente  int PRIMARY KEY,
    nombre      varchar(150) NOT NULL,
    apellido    varchar(150) NOT NULL,
    correo      varchar(100),
    estado      varchar(20)  NOT NULL DEFAULT 'activo'
);

CREATE TABLE producto (
    id_producto  int PRIMARY KEY,
    nombre       varchar(200)  NOT NULL,
    descripcion  text,
    precio       numeric(10,2) NOT NULL,
    estado       varchar(20)   NOT NULL DEFAULT 'activo',
    origen       varchar(20)   NOT NULL DEFAULT 'comprado'
);

-- ------------------------------------------------------------ transaccional --
CREATE TABLE pedido (
    id_pedido     int PRIMARY KEY,
    id_cliente    int NOT NULL,
    id_usuario    int NOT NULL,
    fecha_pedido  timestamp NOT NULL,
    total         numeric(12,2) NOT NULL DEFAULT 0,
    descuento     numeric(12,2) NOT NULL DEFAULT 0,
    estado        varchar(20)   NOT NULL DEFAULT 'pendiente'
);

CREATE TABLE detalle_pedido (
    id_detalle       int PRIMARY KEY,
    id_pedido        int NOT NULL,
    id_producto      int NOT NULL,
    cantidad         int NOT NULL,
    precio_unitario  numeric(10,2) NOT NULL,
    subtotal         numeric(12,2) GENERATED ALWAYS AS (cantidad * precio_unitario) STORED
);

CREATE TABLE inventario (
    id_inventario        int PRIMARY KEY,
    id_producto          int NOT NULL,
    id_bodega            int NOT NULL,
    stock_actual         int NOT NULL DEFAULT 0,
    stock_minimo         int NOT NULL DEFAULT 0,
    fecha_actualizacion  timestamp NOT NULL DEFAULT now()
);

CREATE TABLE log_accion (
    id_log       int PRIMARY KEY,
    id_usuario   int,
    modulo       varchar(50),
    accion       varchar(50),
    descripcion  text,
    fecha        timestamp NOT NULL
);

-- ================================ CARGA DE DATOS ============================
-- Distribuciones deliberadamente NO uniformes donde importa, para que el
-- planificador tenga selectividades realistas y no degeneradas.

INSERT INTO bodega (id_bodega, nombre)
SELECT g, 'Bodega ' || g FROM generate_series(1, 20) g;

INSERT INTO cliente (id_cliente, nombre, apellido, correo)
SELECT g,
       'Cliente' || g,
       'Apellido' || g,
       'cliente' || g || '@correo.com'
FROM generate_series(1, 5000) g;

-- 15% de los productos son 'fabricado' (como en el modelo real de F27)
INSERT INTO producto (id_producto, nombre, descripcion, precio, origen)
SELECT g,
       (ARRAY['Zapatilla','Camiseta','Short','Chaqueta','Gorra','Medias','Mochila','Pantalon'])[1 + (g % 8)]
         || ' ' || (ARRAY['Nike','Adidas','Puma','Reebok','Umbro'])[1 + (g % 5)]
         || ' Modelo ' || g,
       'Descripcion del producto ' || g,
       round((random() * 250 + 10)::numeric, 2),
       CASE WHEN g % 20 < 3 THEN 'fabricado' ELSE 'comprado' END
FROM generate_series(1, 2000) g;

-- 200.000 pedidos repartidos en 3 anios hacia atras.
-- Estados con distribucion realista: la mayoria ya entregada.
INSERT INTO pedido (id_pedido, id_cliente, id_usuario, fecha_pedido, total, descuento, estado)
SELECT g,
       1 + (random() * 4999)::int,
       1 + (g % 6),
       now() - (random() * 1095 || ' days')::interval,
       0,
       CASE WHEN g % 10 = 0 THEN round((random() * 20)::numeric, 2) ELSE 0 END,
       CASE
           WHEN g % 100 < 70 THEN 'entregado'
           WHEN g % 100 < 85 THEN 'enviado'
           WHEN g % 100 < 95 THEN 'procesado'
           WHEN g % 100 < 99 THEN 'pendiente'
           ELSE 'anulado'
       END
FROM generate_series(1, 200000) g;

-- 600.000 lineas de detalle (3 por pedido)
INSERT INTO detalle_pedido (id_detalle, id_pedido, id_producto, cantidad, precio_unitario)
SELECT g,
       1 + (g / 3)::int % 200000,
       1 + (random() * 1999)::int,
       1 + (random() * 4)::int,
       round((random() * 250 + 10)::numeric, 2)
FROM generate_series(1, 600000) g;

-- Alinear pedido.total con la suma real de sus detalles
UPDATE pedido p
SET total = GREATEST(COALESCE(d.suma, 0) - p.descuento, 0)
FROM (SELECT id_pedido, SUM(subtotal) AS suma FROM detalle_pedido GROUP BY id_pedido) d
WHERE d.id_pedido = p.id_pedido;

-- 40.000 filas de inventario: 2.000 productos x 20 bodegas.
-- ~8% de las filas quedan en situacion de stock bajo (caso de uso real de alerta)
INSERT INTO inventario (id_inventario, id_producto, id_bodega, stock_actual, stock_minimo)
SELECT row_number() OVER () ,
       p.id_producto,
       b.id_bodega,
       CASE WHEN random() < 0.08 THEN (random() * 5)::int ELSE (random() * 400 + 20)::int END,
       10
FROM producto p CROSS JOIN bodega b;

-- 500.000 registros de auditoria
INSERT INTO log_accion (id_log, id_usuario, modulo, accion, descripcion, fecha)
SELECT g,
       1 + (g % 6),
       (ARRAY['pedidos','inventario','compras','produccion','usuarios','clientes','reportes'])[1 + (g % 7)],
       (ARRAY['crear','editar','eliminar','ver','cambiar_estado'])[1 + (g % 5)],
       'Accion registrada numero ' || g,
       now() - (random() * 1095 || ' days')::interval
FROM generate_series(1, 500000) g;

-- Estadisticas frescas: sin esto el planificador trabaja a ciegas y la
-- comparacion antes/despues no seria valida.
ANALYZE bodega;
ANALYZE cliente;
ANALYZE producto;
ANALYZE pedido;
ANALYZE detalle_pedido;
ANALYZE inventario;
ANALYZE log_accion;

SELECT 'perf_lab creado' AS estado;
SELECT relname AS tabla, n_live_tup AS filas
FROM pg_stat_user_tables
WHERE schemaname = 'perf_lab'
ORDER BY n_live_tup DESC;
