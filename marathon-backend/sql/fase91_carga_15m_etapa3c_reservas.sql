-- =============================================================================
-- FASE 91 · Etapa 3c — El resto de las reservas
-- -----------------------------------------------------------------------------
-- Tras el rebalanceo quedaron 76.666 reservas apuntando a un producto que no
-- esta en su pedido. Son las 'activa' cuyo pedido no tenia tantos productos
-- DISTINTOS como reservas activas: uq_reserva_pedido_producto_activa impide
-- que dos reservas activas del mismo pedido compartan producto, asi que la
-- segunda no podia recibir el unico producto disponible.
--
-- La salida no es inventarle un producto ajeno, sino reconocer lo que son: una
-- reserva ya cerrada. Con estado 'consumida' el UNIQUE parcial deja de
-- aplicar (solo mira las activas), el producto puede ser el del pedido, y
-- chk_reserva_cierre se satisface poniendo la fecha de cierre que ese estado
-- exige. Queda coherente por las dos puntas.
-- =============================================================================

\set ON_ERROR_STOP on
\timing on

SET work_mem = '512MB';
SET synchronous_commit = off;

\ir fase91_andamiaje.sql

UPDATE reserva_stock rs
SET id_producto   = d.id_producto,
    cantidad      = LEAST(rs.cantidad, d.cantidad),
    estado        = 'consumida',
    fecha_cierre  = carga.tope(rs.fecha_reserva + interval '2 days'),
    motivo_cierre = 'Despachada con el pedido'
FROM (SELECT DISTINCT ON (id_pedido) id_pedido, id_producto, cantidad
      FROM detalle_pedido ORDER BY id_pedido, id_detalle) d
WHERE d.id_pedido = rs.id_pedido
  AND rs.id_reserva > 4
  AND NOT EXISTS (SELECT 1 FROM detalle_pedido dp
                  WHERE dp.id_pedido = rs.id_pedido
                    AND dp.id_producto = rs.id_producto);

ANALYZE reserva_stock;

SELECT count(*) AS reservas_de_un_producto_ajeno_al_pedido
FROM reserva_stock rs
WHERE rs.id_reserva > 4
  AND NOT EXISTS (SELECT 1 FROM detalle_pedido d
                  WHERE d.id_pedido = rs.id_pedido AND d.id_producto = rs.id_producto);

SELECT estado, count(*) FROM reserva_stock GROUP BY estado ORDER BY 1;
