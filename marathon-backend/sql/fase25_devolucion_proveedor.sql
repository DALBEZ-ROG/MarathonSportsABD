-- =====================================================================
-- Fase 25 — Devolucion a Proveedor
-- Crea: devolucion_proveedor, devolucion_proveedor_detalle
-- Idempotente: usa IF NOT EXISTS.
-- =====================================================================

CREATE TABLE IF NOT EXISTS devolucion_proveedor (
    id_devolucion_prov INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_proveedor INTEGER NOT NULL,
    id_usuario_registro INTEGER NOT NULL,
    fecha_devolucion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    tipo_resolucion VARCHAR(20),
    monto_reembolso NUMERIC(10,2),
    observaciones TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_dp_estado CHECK (estado IN ('pendiente','enviada','resuelta','rechazada')),
    CONSTRAINT chk_dp_tipo_resolucion CHECK (tipo_resolucion IS NULL OR
        tipo_resolucion IN ('reembolso','reposicion')),
    CONSTRAINT chk_dp_monto CHECK (monto_reembolso IS NULL OR monto_reembolso > 0),
    CONSTRAINT fk_dp_proveedor FOREIGN KEY (id_proveedor)
        REFERENCES proveedor(id_proveedor)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_dp_usuario FOREIGN KEY (id_usuario_registro)
        REFERENCES usuario(id_usuario)
        ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_dp_proveedor ON devolucion_proveedor(id_proveedor);
CREATE INDEX IF NOT EXISTS idx_dp_estado ON devolucion_proveedor(estado);

CREATE TABLE IF NOT EXISTS devolucion_proveedor_detalle (
    id_detalle_dp INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_devolucion_prov INTEGER NOT NULL,
    origen VARCHAR(20) NOT NULL,
    id_solicitud_devolucion_detalle INTEGER,
    id_recepcion_detalle INTEGER,
    id_producto INTEGER NOT NULL,
    cantidad INTEGER NOT NULL,
    motivo TEXT,
    CONSTRAINT chk_dpd_origen CHECK (origen IN ('rma_cliente','recepcion_compra')),
    CONSTRAINT chk_dpd_cantidad CHECK (cantidad > 0),
    CONSTRAINT chk_dpd_origen_exclusivo CHECK (
        (origen = 'rma_cliente' AND id_solicitud_devolucion_detalle IS NOT NULL
            AND id_recepcion_detalle IS NULL) OR
        (origen = 'recepcion_compra' AND id_recepcion_detalle IS NOT NULL
            AND id_solicitud_devolucion_detalle IS NULL)),
    CONSTRAINT fk_dpd_devolucion FOREIGN KEY (id_devolucion_prov)
        REFERENCES devolucion_proveedor(id_devolucion_prov)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_dpd_sdd FOREIGN KEY (id_solicitud_devolucion_detalle)
        REFERENCES solicitud_devolucion_detalle(id_detalle_sd)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_dpd_recepcion_detalle FOREIGN KEY (id_recepcion_detalle)
        REFERENCES recepcion_mercancia_detalle(id_detalle_rm)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_dpd_producto FOREIGN KEY (id_producto)
        REFERENCES producto(id_producto)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uq_dpd_sdd UNIQUE (id_solicitud_devolucion_detalle),
    CONSTRAINT uq_dpd_recepcion UNIQUE (id_recepcion_detalle)
);
CREATE INDEX IF NOT EXISTS idx_dpd_devolucion ON devolucion_proveedor_detalle(id_devolucion_prov);
