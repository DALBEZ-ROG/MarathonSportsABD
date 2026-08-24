-- =====================================================================
-- Fase 39.1 - Reparacion de lineas omitidas por la carga masiva F39
-- Idempotente: solo completa devoluciones que todavia no tienen detalle.
-- =====================================================================

-- Cada solicitud de cliente toma una linea real del pedido relacionado.
INSERT INTO solicitud_devolucion_detalle
    (id_solicitud, id_detalle_pedido, cantidad_devuelta,
     resultado_inspeccion, observacion_inspeccion)
SELECT sd.id_solicitud,
       dp.id_detalle,
       GREATEST(1, LEAST(dp.cantidad, 1 + (random() * 2)::int)),
       CASE
           WHEN sd.estado = 'completada' THEN
               (ARRAY['apto_reventa','defectuoso'])[1 + (random() < 0.35)::int]
           WHEN sd.estado = 'rechazada' THEN 'rechazado'
           ELSE NULL
       END,
       'Linea generada por reparacion de la carga F39'
FROM solicitud_devolucion sd
JOIN LATERAL (
    SELECT d.id_detalle, d.cantidad
    FROM detalle_pedido d
    WHERE d.id_pedido = sd.id_pedido
    ORDER BY d.id_detalle
    LIMIT 1
) dp ON true
WHERE NOT EXISTS (
    SELECT 1 FROM solicitud_devolucion_detalle x
    WHERE x.id_solicitud = sd.id_solicitud
);

-- Asigna a cada devolucion de proveedor una recepcion defectuosa real y
-- del mismo proveedor. ROW_NUMBER evita reutilizar una misma recepcion.
WITH devoluciones_vacias AS (
    SELECT d.id_devolucion_prov, d.id_proveedor,
           row_number() OVER (PARTITION BY d.id_proveedor ORDER BY d.id_devolucion_prov) AS rn
    FROM devolucion_proveedor d
    WHERE NOT EXISTS (
        SELECT 1 FROM devolucion_proveedor_detalle x
        WHERE x.id_devolucion_prov = d.id_devolucion_prov
    )
),
recepciones_disponibles AS (
    SELECT rmd.id_detalle_rm,
           o.id_proveedor,
           doc.id_producto,
           rmd.cantidad_defectuosa,
           row_number() OVER (PARTITION BY o.id_proveedor ORDER BY rmd.id_detalle_rm) AS rn
    FROM recepcion_mercancia_detalle rmd
    JOIN recepcion_mercancia rm ON rm.id_recepcion = rmd.id_recepcion
    JOIN orden_compra o ON o.id_orden_compra = rm.id_orden_compra
    JOIN orden_compra_detalle doc ON doc.id_detalle_oc = rmd.id_detalle_oc
    WHERE rmd.cantidad_defectuosa > 0
      AND doc.tipo_item = 'producto'
      AND doc.id_producto IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM devolucion_proveedor_detalle x
          WHERE x.id_recepcion_detalle = rmd.id_detalle_rm
      )
)
INSERT INTO devolucion_proveedor_detalle
    (id_devolucion_prov, origen, id_recepcion_detalle,
     id_producto, cantidad, motivo)
SELECT d.id_devolucion_prov,
       'recepcion_compra',
       r.id_detalle_rm,
       r.id_producto,
       GREATEST(1, r.cantidad_defectuosa),
       'Producto defectuoso detectado en recepcion (carga F39)'
FROM devoluciones_vacias d
JOIN recepciones_disponibles r
  ON r.id_proveedor = d.id_proveedor AND r.rn = d.rn;

-- Verificacion: ambos valores deben quedar en cero si existen candidatos.
SELECT 'solicitudes_sin_lineas' AS verificacion, count(*) AS cantidad
FROM solicitud_devolucion s
WHERE NOT EXISTS (SELECT 1 FROM solicitud_devolucion_detalle d WHERE d.id_solicitud=s.id_solicitud)
UNION ALL
SELECT 'devoluciones_proveedor_sin_lineas', count(*)
FROM devolucion_proveedor p
WHERE NOT EXISTS (SELECT 1 FROM devolucion_proveedor_detalle d WHERE d.id_devolucion_prov=p.id_devolucion_prov);
