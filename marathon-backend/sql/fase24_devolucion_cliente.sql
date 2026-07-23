-- =====================================================================
-- Fase 24 — Devolucion de Cliente (RMA)
-- Crea: solicitud_devolucion, solicitud_devolucion_detalle, reembolso_cliente
-- Idempotente: usa IF NOT EXISTS.
-- =====================================================================

CREATE TABLE IF NOT EXISTS solicitud_devolucion (
    id_solicitud INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_pedido INTEGER NOT NULL,
    id_usuario_registro INTEGER NOT NULL,
    motivo VARCHAR(50) NOT NULL,
    descripcion TEXT,
    estado VARCHAR(30) NOT NULL DEFAULT 'solicitada',
    fecha_solicitud TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_inspeccion TIMESTAMP,
    id_usuario_inspector INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sd_motivo CHECK (motivo IN (
        'producto_defectuoso','talla_incorrecta','no_esperado',
        'cambio_opinion','producto_incompleto','otro')),
    CONSTRAINT chk_sd_estado CHECK (estado IN (
        'solicitada','en_inspeccion','completada','rechazada')),
    CONSTRAINT fk_sd_pedido FOREIGN KEY (id_pedido)
        REFERENCES pedido(id_pedido)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_sd_usuario_registro FOREIGN KEY (id_usuario_registro)
        REFERENCES usuario(id_usuario)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_sd_inspector FOREIGN KEY (id_usuario_inspector)
        REFERENCES usuario(id_usuario)
        ON UPDATE CASCADE ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_sd_pedido ON solicitud_devolucion(id_pedido);
CREATE INDEX IF NOT EXISTS idx_sd_estado ON solicitud_devolucion(estado);

CREATE TABLE IF NOT EXISTS solicitud_devolucion_detalle (
    id_detalle_sd INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_solicitud INTEGER NOT NULL,
    id_detalle_pedido INTEGER NOT NULL,
    cantidad_devuelta INTEGER NOT NULL,
    resultado_inspeccion VARCHAR(20),
    observacion_inspeccion TEXT,
    CONSTRAINT chk_sdd_cantidad CHECK (cantidad_devuelta > 0),
    CONSTRAINT chk_sdd_resultado CHECK (
        resultado_inspeccion IS NULL OR
        resultado_inspeccion IN ('apto_reventa','defectuoso','rechazado')),
    CONSTRAINT fk_sdd_solicitud FOREIGN KEY (id_solicitud)
        REFERENCES solicitud_devolucion(id_solicitud)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_sdd_detalle_pedido FOREIGN KEY (id_detalle_pedido)
        REFERENCES detalle_pedido(id_detalle)
        ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_sdd_solicitud ON solicitud_devolucion_detalle(id_solicitud);
CREATE INDEX IF NOT EXISTS idx_sdd_detalle_pedido ON solicitud_devolucion_detalle(id_detalle_pedido);

CREATE TABLE IF NOT EXISTS reembolso_cliente (
    id_reembolso INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_solicitud INTEGER NOT NULL UNIQUE,
    id_usuario_registro INTEGER NOT NULL,
    monto NUMERIC(10,2) NOT NULL,
    metodo VARCHAR(30) NOT NULL,
    fecha_reembolso TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observaciones TEXT,
    CONSTRAINT chk_rc_monto CHECK (monto > 0),
    CONSTRAINT chk_rc_metodo CHECK (metodo IN ('nota_credito','transferencia','efectivo')),
    CONSTRAINT fk_rc_solicitud FOREIGN KEY (id_solicitud)
        REFERENCES solicitud_devolucion(id_solicitud)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_rc_usuario FOREIGN KEY (id_usuario_registro)
        REFERENCES usuario(id_usuario)
        ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_rc_solicitud ON reembolso_cliente(id_solicitud);
