-- =============================================================================
-- Fase 94 — Los índices que faltaban en todas las pantallas con filtro
-- =============================================================================
-- EL SÍNTOMA
-- Con pocos datos el sistema iba fino. Con el millón y medio de la F91, escribir
-- en casi cualquier filtro tarda segundos y a veces la pantalla se cae.
--
-- LA CAUSA, MEDIDA ENDPOINT POR ENDPOINT (scripts/perf/medir_pantallas.sh)
-- Las tablas crecieron tres órdenes de magnitud conservando los índices de
-- cuando eran pequeñas. `proveedor` tiene 1.500.109 filas, ocupa 683 MB y
-- tenía **un solo índice**: el de la clave primaria. Buscar un proveedor por
-- nombre recorría los 683 MB enteros.
--
-- POR QUÉ TRIGRAMAS Y NO UN BTREE
-- Un btree sobre `lower(nombre)` sólo sirve para buscar por el PRINCIPIO
-- ('dis%'). Todas estas pantallas buscan con `LIKE '%texto%'`, porque quien
-- busca escribe el trozo que recuerda. `pg_trgm` indexa grupos de tres letras y
-- hace que `%texto%` también use índice.
--
-- LA TRAMPA DEL ORDER BY (ya costó dos correcciones)
-- Un índice de trigramas se pierde si la consulta ordena por otra columna con
-- LIMIT: el planificador prefiere recorrer el índice ordenado y filtrar fila a
-- fila. Cuando se escriba un buscador nuevo, el LIMIT va DENTRO de una
-- subconsulta y el ORDER BY FUERA. Ver DEUDA_TECNICA.md.
--
-- REVERSIÓN: fase94_indices_de_busqueda_rollback.sql
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Todos CONCURRENTLY: se construyen sin bloquear la escritura, y por eso van
-- fuera de cualquier transacción.

-- ---------------------------------------------------------------------------
-- 1. Búsqueda por texto (trigramas)
-- ---------------------------------------------------------------------------
-- proveedor: lo buscan Órdenes de compra, Devoluciones a proveedor, Cuentas por
-- pagar y su propia pantalla. Es la tabla que peor estaba: 683 MB con un índice.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_proveedor_nombre_trgm
    ON proveedor USING gin (nombre gin_trgm_ops);

-- materia_prima: su listado filtra por nombre.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_materia_prima_nombre_trgm
    ON materia_prima USING gin (nombre gin_trgm_ops);

-- usuario: lo filtra la pantalla de Usuarios y lo cruza el rastro de auditoría.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_usuario_nombre_trgm
    ON usuario USING gin (nombre gin_trgm_ops);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_usuario_apellido_trgm
    ON usuario USING gin (apellido gin_trgm_ops);

-- bodega es pequeña (decenas de filas) y no necesita índice: un barrido de una
-- tabla que cabe en una página es más rápido que consultar un índice.

-- comprobante_interno: el filtro por número de comprobante.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_comprobante_numero_trgm
    ON comprobante_interno USING gin (numero_comprobante gin_trgm_ops);

-- factura_compra: Cuentas por pagar busca por el número de factura del proveedor.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_factura_numero_prov_trgm
    ON factura_compra USING gin (numero_factura_proveedor gin_trgm_ops);

-- ---------------------------------------------------------------------------
-- 2. Filtros por estado + orden
-- ---------------------------------------------------------------------------
-- Todos estos listados hacen `WHERE estado = ? ORDER BY id DESC LIMIT 10`. El
-- orden de las columnas importa: con (estado, id DESC) la base salta al primer
-- registro del estado pedido y lee diez; al revés tendría que ordenar todo.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pedido_estado_id
    ON pedido (estado, id_pedido DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orden_compra_estado_id
    ON orden_compra (estado, id_orden_compra DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orden_produccion_estado_id
    ON orden_produccion (estado, id_orden_produccion DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_solicitud_dev_estado_id
    ON solicitud_devolucion (estado, id_solicitud DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_devolucion_prov_estado_id
    ON devolucion_proveedor (estado, id_devolucion_prov DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cuenta_pagar_estado_id
    ON cuenta_por_pagar (estado, id_cuenta_pagar DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_factura_compra_estado_id
    ON factura_compra (estado, id_factura_compra DESC);

-- ---------------------------------------------------------------------------
-- 3. Claves ajenas sin índice
-- ---------------------------------------------------------------------------
-- PostgreSQL indexa la clave primaria, NO la ajena. Cada uno de estos joins
-- estaba resolviéndose sin índice por el lado que apunta.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orden_compra_proveedor
    ON orden_compra (id_proveedor);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_devolucion_prov_proveedor
    ON devolucion_proveedor (id_proveedor);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_producto_proveedor_prov
    ON producto_proveedor (id_proveedor);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_solicitud_dev_pedido
    ON solicitud_devolucion (id_pedido);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cuenta_pagar_factura
    ON cuenta_por_pagar (id_factura_compra);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orden_produccion_producto
    ON orden_produccion (id_producto);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transportista_cobertura_transp
    ON transportista_cobertura (id_transportista);

-- ---------------------------------------------------------------------------
-- 4. Los dos «stock bajo», que tardaban diez segundos cada uno
-- ---------------------------------------------------------------------------
-- La consulta es `WHERE stock_actual <= stock_minimo`: compara dos columnas de
-- la misma fila, y eso ningún índice normal lo puede resolver — hay que mirar
-- fila por fila, 1,5 millones de veces.
--
-- Un índice PARCIAL sí: se le pone la condición dentro, así que el índice
-- contiene exactamente las filas que están bajo mínimos y la consulta se
-- resuelve leyéndolo entero, que son unos pocos miles.
--
-- La contrapartida, dicha: cada INSERT/UPDATE de stock tiene que decidir si la
-- fila entra o sale del índice. Es un coste minúsculo comparado con diez
-- segundos por consulta, y estas dos pantallas se abren a diario.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_inventario_stock_bajo
    ON inventario (id_producto)
    WHERE stock_actual <= stock_minimo;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_materia_prima_stock_bajo
    ON materia_prima (id_materia_prima)
    WHERE stock_actual <= stock_minimo;

-- ---------------------------------------------------------------------------
-- 5. El tablero
-- ---------------------------------------------------------------------------
-- «Top productos» agrupa detalle_pedido por producto dentro de un rango de
-- fechas. Sin índice, son 3,15 millones de líneas barridas cada vez que alguien
-- abre el inicio.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_detalle_pedido_producto
    ON detalle_pedido (id_producto);

-- Las series por día del tablero filtran pedido por fecha.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pedido_fecha
    ON pedido (fecha_pedido DESC);

-- `producto` se cuenta por origen (comprado/fabricado) en Producción.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_producto_origen
    ON producto (origen);

-- ---------------------------------------------------------------------------
-- 6. Que el planificador se entere
-- ---------------------------------------------------------------------------
-- Un índice recién creado no cambia nada si las estadísticas siguen diciendo
-- que la tabla es de otro tamaño. ANALYZE las recalcula.
ANALYZE proveedor;
ANALYZE materia_prima;
ANALYZE usuario;
ANALYZE inventario;
ANALYZE pedido;
ANALYZE orden_compra;
ANALYZE detalle_pedido;
ANALYZE cuenta_por_pagar;
ANALYZE solicitud_devolucion;
ANALYZE devolucion_proveedor;
ANALYZE orden_produccion;
ANALYZE comprobante_interno;
ANALYZE factura_compra;
ANALYZE producto;

-- Verificación
--   SELECT relname, count(*) FROM pg_index i JOIN pg_class c ON c.oid=i.indrelid
--    WHERE relname IN ('proveedor','materia_prima','usuario') GROUP BY 1;
