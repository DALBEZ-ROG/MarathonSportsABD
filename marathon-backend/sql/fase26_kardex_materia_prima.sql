-- =====================================================================
-- Fase 26 — Materia Prima: Kardex de Movimientos
-- Crea: movimiento_materia_prima
-- Idempotente.
-- =====================================================================

CREATE TABLE IF NOT EXISTS movimiento_materia_prima (
    id_movimiento_mp INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_materia_prima INTEGER NOT NULL,
    id_usuario INTEGER NOT NULL,
    tipo_movimiento VARCHAR(20) NOT NULL,
    cantidad NUMERIC(12,3) NOT NULL,
    stock_anterior NUMERIC(12,3) NOT NULL,
    stock_nuevo NUMERIC(12,3) NOT NULL,
    id_recepcion INTEGER,
    id_orden_produccion INTEGER,
    observacion TEXT,
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mmp_tipo CHECK (tipo_movimiento IN (
        'entrada_compra','salida_produccion','ajuste','merma')),
    CONSTRAINT chk_mmp_cantidad CHECK (cantidad > 0),
    CONSTRAINT fk_mmp_materia_prima FOREIGN KEY (id_materia_prima)
        REFERENCES materia_prima(id_materia_prima)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mmp_usuario FOREIGN KEY (id_usuario)
        REFERENCES usuario(id_usuario)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mmp_recepcion FOREIGN KEY (id_recepcion)
        REFERENCES recepcion_mercancia(id_recepcion)
        ON UPDATE CASCADE ON DELETE SET NULL
    -- id_orden_produccion: FK se agregara en F28 cuando exista la tabla orden_produccion
);
CREATE INDEX IF NOT EXISTS idx_mmp_materia_prima ON movimiento_materia_prima(id_materia_prima);
CREATE INDEX IF NOT EXISTS idx_mmp_fecha ON movimiento_materia_prima(fecha);
CREATE INDEX IF NOT EXISTS idx_mmp_tipo ON movimiento_materia_prima(tipo_movimiento);
