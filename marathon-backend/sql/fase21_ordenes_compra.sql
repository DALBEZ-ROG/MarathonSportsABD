-- =====================================================================
-- Fase 21 — Órdenes de Compra (Procure-to-Pay)
-- Crea: materia_prima, orden_compra, orden_compra_detalle
-- + triggers de recálculo y protección de total (patrón pedido.total)
-- Idempotente: usa IF NOT EXISTS / CREATE OR REPLACE.
-- =====================================================================

-- ---------------------------------------------------------------------
-- materia_prima (solo catálogo — sin inventario ni kardex aún, eso es F26)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS materia_prima (
    id_materia_prima INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    descripcion TEXT,
    id_unidad_medida INTEGER NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'activo',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_materia_prima_nombre UNIQUE (nombre),
    CONSTRAINT chk_materia_prima_estado CHECK (estado IN ('activo','inactivo')),
    CONSTRAINT fk_materia_prima_unidad FOREIGN KEY (id_unidad_medida)
        REFERENCES unidad_medida(id_unidad_medida)
        ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_materia_prima_unidad ON materia_prima(id_unidad_medida);

-- ---------------------------------------------------------------------
-- orden_compra
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orden_compra (
    id_orden_compra INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_proveedor INTEGER NOT NULL,
    id_usuario_solicitante INTEGER NOT NULL,
    id_usuario_aprobador INTEGER,
    fecha_orden TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_aprobacion TIMESTAMP,
    estado VARCHAR(30) NOT NULL DEFAULT 'borrador',
    total NUMERIC(12,2) NOT NULL DEFAULT 0,
    observaciones TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_oc_estado CHECK (estado IN
        ('borrador','pendiente_aprobacion','aprobada','rechazada',
         'recibida_parcial','recibida_completa','cancelada')),
    CONSTRAINT chk_oc_total CHECK (total >= 0),
    CONSTRAINT fk_oc_proveedor FOREIGN KEY (id_proveedor)
        REFERENCES proveedor(id_proveedor)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_oc_solicitante FOREIGN KEY (id_usuario_solicitante)
        REFERENCES usuario(id_usuario)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_oc_aprobador FOREIGN KEY (id_usuario_aprobador)
        REFERENCES usuario(id_usuario)
        ON UPDATE CASCADE ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_oc_proveedor ON orden_compra(id_proveedor);
CREATE INDEX IF NOT EXISTS idx_oc_estado ON orden_compra(estado);
CREATE INDEX IF NOT EXISTS idx_oc_fecha ON orden_compra(fecha_orden);

-- ---------------------------------------------------------------------
-- orden_compra_detalle (asociación polimórfica exclusiva producto|materia_prima)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orden_compra_detalle (
    id_detalle_oc INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_orden_compra INTEGER NOT NULL,
    tipo_item VARCHAR(20) NOT NULL,
    id_producto INTEGER,
    id_materia_prima INTEGER,
    cantidad INTEGER NOT NULL,
    precio_unitario NUMERIC(10,2) NOT NULL,
    subtotal NUMERIC(12,2) GENERATED ALWAYS AS (cantidad * precio_unitario) STORED,
    cantidad_recibida INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_oc_detalle_tipo CHECK (tipo_item IN ('producto','materia_prima')),
    CONSTRAINT chk_oc_detalle_cantidad CHECK (cantidad > 0),
    CONSTRAINT chk_oc_detalle_precio CHECK (precio_unitario > 0),
    CONSTRAINT chk_oc_detalle_recibida CHECK (cantidad_recibida >= 0
        AND cantidad_recibida <= cantidad),
    CONSTRAINT chk_oc_detalle_item_exclusivo CHECK (
        (tipo_item = 'producto' AND id_producto IS NOT NULL AND id_materia_prima IS NULL) OR
        (tipo_item = 'materia_prima' AND id_materia_prima IS NOT NULL AND id_producto IS NULL)),
    CONSTRAINT fk_ocd_orden FOREIGN KEY (id_orden_compra)
        REFERENCES orden_compra(id_orden_compra)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_ocd_producto FOREIGN KEY (id_producto)
        REFERENCES producto(id_producto)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_ocd_materia_prima FOREIGN KEY (id_materia_prima)
        REFERENCES materia_prima(id_materia_prima)
        ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_ocd_orden ON orden_compra_detalle(id_orden_compra);
CREATE INDEX IF NOT EXISTS idx_ocd_producto ON orden_compra_detalle(id_producto);
CREATE INDEX IF NOT EXISTS idx_ocd_materia_prima ON orden_compra_detalle(id_materia_prima);

-- ---------------------------------------------------------------------
-- Trigger: recálculo de orden_compra.total (patrón statement-level de pedido.total)
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_recalcular_total_orden_compra_stmt()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        UPDATE orden_compra oc
        SET total = (SELECT COALESCE(SUM(d.subtotal), 0)
                     FROM orden_compra_detalle d
                     WHERE d.id_orden_compra = oc.id_orden_compra)
        WHERE oc.id_orden_compra IN (SELECT DISTINCT id_orden_compra FROM new_table);
    ELSIF (TG_OP = 'DELETE') THEN
        UPDATE orden_compra oc
        SET total = (SELECT COALESCE(SUM(d.subtotal), 0)
                     FROM orden_compra_detalle d
                     WHERE d.id_orden_compra = oc.id_orden_compra)
        WHERE oc.id_orden_compra IN (SELECT DISTINCT id_orden_compra FROM old_table);
    ELSE -- UPDATE
        UPDATE orden_compra oc
        SET total = (SELECT COALESCE(SUM(d.subtotal), 0)
                     FROM orden_compra_detalle d
                     WHERE d.id_orden_compra = oc.id_orden_compra)
        WHERE oc.id_orden_compra IN (
            SELECT DISTINCT id_orden_compra FROM new_table
            UNION SELECT DISTINCT id_orden_compra FROM old_table);
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_oc_total_insert ON orden_compra_detalle;
CREATE TRIGGER trg_oc_total_insert
    AFTER INSERT ON orden_compra_detalle
    REFERENCING NEW TABLE AS new_table
    FOR EACH STATEMENT EXECUTE FUNCTION fn_recalcular_total_orden_compra_stmt();

DROP TRIGGER IF EXISTS trg_oc_total_update ON orden_compra_detalle;
CREATE TRIGGER trg_oc_total_update
    AFTER UPDATE ON orden_compra_detalle
    REFERENCING NEW TABLE AS new_table OLD TABLE AS old_table
    FOR EACH STATEMENT EXECUTE FUNCTION fn_recalcular_total_orden_compra_stmt();

DROP TRIGGER IF EXISTS trg_oc_total_delete ON orden_compra_detalle;
CREATE TRIGGER trg_oc_total_delete
    AFTER DELETE ON orden_compra_detalle
    REFERENCING OLD TABLE AS old_table
    FOR EACH STATEMENT EXECUTE FUNCTION fn_recalcular_total_orden_compra_stmt();

-- ---------------------------------------------------------------------
-- Trigger de protección: impide UPDATE manual de orden_compra.total
-- (patrón fn_proteger_total_pedido). Permite el cambio solo cuando lo
-- realiza el trigger de recálculo (que ejecuta un UPDATE explícito del
-- campo total). Se detecta que el nuevo total coincide con la suma real.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_proteger_total_orden_compra()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_total_real NUMERIC(12,2);
BEGIN
    IF (NEW.total IS DISTINCT FROM OLD.total) THEN
        SELECT COALESCE(SUM(d.subtotal), 0) INTO v_total_real
        FROM orden_compra_detalle d
        WHERE d.id_orden_compra = NEW.id_orden_compra;
        IF (NEW.total IS DISTINCT FROM v_total_real) THEN
            RAISE EXCEPTION 'El campo orden_compra.total es calculado automáticamente y no puede modificarse manualmente';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_proteger_total_oc ON orden_compra;
CREATE TRIGGER trg_proteger_total_oc
    BEFORE UPDATE ON orden_compra
    FOR EACH ROW EXECUTE FUNCTION fn_proteger_total_orden_compra();
