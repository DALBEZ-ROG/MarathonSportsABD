-- ETAPA 2: replica en perf_lab los indices secundarios que la BD real (public)
-- YA TIENE sobre estas tablas. Sirve para responder: los 62 indices existentes,
-- se justifican? Son todos de columna simple sobre FKs y filtros sueltos.
SET search_path = perf_lab;

CREATE INDEX idx_pedido_cliente    ON pedido (id_cliente);
CREATE INDEX idx_pedido_fecha      ON pedido (fecha_pedido);
CREATE INDEX idx_pedido_usuario    ON pedido (id_usuario);

CREATE INDEX idx_detalle_pedido    ON detalle_pedido (id_pedido);
CREATE INDEX idx_detalle_producto  ON detalle_pedido (id_producto);
CREATE INDEX idx_detalle_subtotal  ON detalle_pedido (subtotal);

CREATE INDEX idx_inventario_bodega   ON inventario (id_bodega);
CREATE INDEX idx_inventario_producto ON inventario (id_producto);

CREATE INDEX idx_log_fecha    ON log_accion (fecha);
CREATE INDEX idx_log_modulo   ON log_accion (modulo);
CREATE INDEX idx_log_usuario  ON log_accion (id_usuario);

ANALYZE pedido; ANALYZE detalle_pedido; ANALYZE inventario; ANALYZE log_accion;
SELECT 'etapa2: indices actuales creados' AS estado;
