-- =====================================================================
-- Fase 22 — Recepción de Mercancía (Procure-to-Pay)
-- Crea: recepcion_mercancia, recepcion_mercancia_detalle
-- Agrega: materia_prima.stock_actual, materia_prima.stock_minimo
-- Idempotente.
-- =====================================================================

-- ---------------------------------------------------------------------
-- recepcion_mercancia (encabezado — una entrega/visita física)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS recepcion_mercancia (
    id_recepcion INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_orden_compra INTEGER NOT NULL,
    id_usuario_receptor INTEGER NOT NULL,
    id_bodega INTEGER NOT NULL,
    fecha_recepcion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    numero_guia_remision VARCHAR(50),
    observaciones TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rm_orden FOREIGN KEY (id_orden_compra)
        REFERENCES orden_compra(id_orden_compra)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_rm_usuario FOREIGN KEY (id_usuario_receptor)
        REFERENCES usuario(id_usuario)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_rm_bodega FOREIGN KEY (id_bodega)
        REFERENCES bodega(id_bodega)
        ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_rm_orden ON recepcion_mercancia(id_orden_compra);
CREATE INDEX IF NOT EXISTS idx_rm_fecha ON recepcion_mercancia(fecha_recepcion);

-- ---------------------------------------------------------------------
-- recepcion_mercancia_detalle (líneas de la entrega)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS recepcion_mercancia_detalle (
    id_detalle_rm INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_recepcion INTEGER NOT NULL,
    id_detalle_oc INTEGER NOT NULL,
    cantidad_recibida_ahora INTEGER NOT NULL,
    cantidad_defectuosa INTEGER NOT NULL DEFAULT 0,
    observacion TEXT,
    CONSTRAINT chk_rmd_cantidad CHECK (cantidad_recibida_ahora > 0),
    CONSTRAINT chk_rmd_defectuosa CHECK (cantidad_defectuosa >= 0
        AND cantidad_defectuosa <= cantidad_recibida_ahora),
    CONSTRAINT fk_rmd_recepcion FOREIGN KEY (id_recepcion)
        REFERENCES recepcion_mercancia(id_recepcion)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_rmd_detalle_oc FOREIGN KEY (id_detalle_oc)
        REFERENCES orden_compra_detalle(id_detalle_oc)
        ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_rmd_recepcion ON recepcion_mercancia_detalle(id_recepcion);
CREATE INDEX IF NOT EXISTS idx_rmd_detalle_oc ON recepcion_mercancia_detalle(id_detalle_oc);

-- ---------------------------------------------------------------------
-- materia_prima: stock físico (global, sin bodega — simplificación F22)
-- NUMERIC porque se mide en metros, kg, litros (decimales)
-- ---------------------------------------------------------------------
ALTER TABLE materia_prima ADD COLUMN IF NOT EXISTS stock_actual NUMERIC(12,3) NOT NULL DEFAULT 0;
ALTER TABLE materia_prima ADD COLUMN IF NOT EXISTS stock_minimo NUMERIC(12,3) NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_materia_prima_stock'
    ) THEN
        ALTER TABLE materia_prima ADD CONSTRAINT chk_materia_prima_stock CHECK (stock_actual >= 0);
    END IF;
END$$;
