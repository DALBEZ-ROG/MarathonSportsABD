-- =====================================================================
-- Fase 23 — Factura de Compra y Cuentas por Pagar (Procure-to-Pay)
-- Crea: factura_compra, cuenta_por_pagar, pago_proveedor
-- + triggers de recálculo de monto_pagado y protección
-- Idempotente: usa IF NOT EXISTS / CREATE OR REPLACE.
-- =====================================================================

-- ---------------------------------------------------------------------
-- factura_compra
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS factura_compra (
    id_factura_compra INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_orden_compra INTEGER NOT NULL,
    id_usuario_registro INTEGER NOT NULL,
    numero_factura_proveedor VARCHAR(50) NOT NULL,
    fecha_factura DATE NOT NULL,
    fecha_vencimiento DATE NOT NULL,
    subtotal NUMERIC(12,2) NOT NULL,
    impuesto NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) GENERATED ALWAYS AS (subtotal + impuesto) STORED,
    estado VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_fc_numero_proveedor UNIQUE (id_orden_compra, numero_factura_proveedor),
    CONSTRAINT chk_fc_estado CHECK (estado IN ('pendiente','pagada','anulada')),
    CONSTRAINT chk_fc_subtotal CHECK (subtotal > 0),
    CONSTRAINT chk_fc_impuesto CHECK (impuesto >= 0),
    CONSTRAINT chk_fc_vencimiento CHECK (fecha_vencimiento >= fecha_factura),
    CONSTRAINT fk_fc_orden FOREIGN KEY (id_orden_compra)
        REFERENCES orden_compra(id_orden_compra)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_fc_usuario FOREIGN KEY (id_usuario_registro)
        REFERENCES usuario(id_usuario)
        ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_fc_orden ON factura_compra(id_orden_compra);
CREATE INDEX IF NOT EXISTS idx_fc_estado ON factura_compra(estado);
CREATE INDEX IF NOT EXISTS idx_fc_vencimiento ON factura_compra(fecha_vencimiento);

-- ---------------------------------------------------------------------
-- cuenta_por_pagar
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cuenta_por_pagar (
    id_cuenta_pagar INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_factura_compra INTEGER NOT NULL UNIQUE,
    id_proveedor INTEGER NOT NULL,
    monto_total NUMERIC(12,2) NOT NULL,
    monto_pagado NUMERIC(12,2) NOT NULL DEFAULT 0,
    saldo_pendiente NUMERIC(12,2) GENERATED ALWAYS AS (monto_total - monto_pagado) STORED,
    fecha_vencimiento DATE NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'vigente',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_cxp_estado CHECK (estado IN ('vigente','vencida','pagada')),
    CONSTRAINT chk_cxp_montos CHECK (monto_pagado >= 0 AND monto_pagado <= monto_total),
    CONSTRAINT fk_cxp_factura FOREIGN KEY (id_factura_compra)
        REFERENCES factura_compra(id_factura_compra)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_cxp_proveedor FOREIGN KEY (id_proveedor)
        REFERENCES proveedor(id_proveedor)
        ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_cxp_proveedor ON cuenta_por_pagar(id_proveedor);
CREATE INDEX IF NOT EXISTS idx_cxp_estado ON cuenta_por_pagar(estado);
CREATE INDEX IF NOT EXISTS idx_cxp_vencimiento ON cuenta_por_pagar(fecha_vencimiento);

-- ---------------------------------------------------------------------
-- pago_proveedor
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pago_proveedor (
    id_pago INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_cuenta_pagar INTEGER NOT NULL,
    id_usuario_registro INTEGER NOT NULL,
    monto NUMERIC(12,2) NOT NULL,
    fecha_pago TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metodo_pago VARCHAR(30) NOT NULL,
    referencia VARCHAR(100),
    observaciones TEXT,
    CONSTRAINT chk_pp_monto CHECK (monto > 0),
    CONSTRAINT chk_pp_metodo CHECK (metodo_pago IN ('transferencia','cheque','efectivo','tarjeta')),
    CONSTRAINT fk_pp_cuenta FOREIGN KEY (id_cuenta_pagar)
        REFERENCES cuenta_por_pagar(id_cuenta_pagar)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_pp_usuario FOREIGN KEY (id_usuario_registro)
        REFERENCES usuario(id_usuario)
        ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_pp_cuenta ON pago_proveedor(id_cuenta_pagar);
CREATE INDEX IF NOT EXISTS idx_pp_fecha ON pago_proveedor(fecha_pago);

-- ---------------------------------------------------------------------
-- Trigger: recalcula cuenta_por_pagar.monto_pagado tras cada pago
-- Patrón STATEMENT con REFERENCING NEW/OLD TABLE (igual que OC total)
-- Al recalcular, si monto_pagado >= monto_total → estado='pagada'
-- y además actualiza factura_compra.estado='pagada'
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_recalcular_monto_pagado_cxp()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        UPDATE cuenta_por_pagar cxp
        SET monto_pagado = (SELECT COALESCE(SUM(p.monto), 0)
                            FROM pago_proveedor p
                            WHERE p.id_cuenta_pagar = cxp.id_cuenta_pagar),
            estado = CASE
                WHEN (SELECT COALESCE(SUM(p.monto), 0)
                      FROM pago_proveedor p
                      WHERE p.id_cuenta_pagar = cxp.id_cuenta_pagar) >= cxp.monto_total
                THEN 'pagada'
                ELSE cxp.estado
            END
        WHERE cxp.id_cuenta_pagar IN (SELECT DISTINCT id_cuenta_pagar FROM new_table);

        -- Cascada lógica: si la cuenta quedó pagada, marcar la factura también
        UPDATE factura_compra fc
        SET estado = 'pagada'
        WHERE fc.id_factura_compra IN (
            SELECT cxp.id_factura_compra FROM cuenta_por_pagar cxp
            WHERE cxp.id_cuenta_pagar IN (SELECT DISTINCT id_cuenta_pagar FROM new_table)
              AND cxp.estado = 'pagada'
        ) AND fc.estado != 'pagada';

    ELSIF (TG_OP = 'DELETE') THEN
        UPDATE cuenta_por_pagar cxp
        SET monto_pagado = (SELECT COALESCE(SUM(p.monto), 0)
                            FROM pago_proveedor p
                            WHERE p.id_cuenta_pagar = cxp.id_cuenta_pagar),
            estado = CASE
                WHEN (SELECT COALESCE(SUM(p.monto), 0)
                      FROM pago_proveedor p
                      WHERE p.id_cuenta_pagar = cxp.id_cuenta_pagar) >= cxp.monto_total
                THEN 'pagada'
                WHEN cxp.fecha_vencimiento < CURRENT_DATE THEN 'vencida'
                ELSE 'vigente'
            END
        WHERE cxp.id_cuenta_pagar IN (SELECT DISTINCT id_cuenta_pagar FROM old_table);

    ELSE -- UPDATE
        UPDATE cuenta_por_pagar cxp
        SET monto_pagado = (SELECT COALESCE(SUM(p.monto), 0)
                            FROM pago_proveedor p
                            WHERE p.id_cuenta_pagar = cxp.id_cuenta_pagar),
            estado = CASE
                WHEN (SELECT COALESCE(SUM(p.monto), 0)
                      FROM pago_proveedor p
                      WHERE p.id_cuenta_pagar = cxp.id_cuenta_pagar) >= cxp.monto_total
                THEN 'pagada'
                ELSE cxp.estado
            END
        WHERE cxp.id_cuenta_pagar IN (
            SELECT DISTINCT id_cuenta_pagar FROM new_table
            UNION SELECT DISTINCT id_cuenta_pagar FROM old_table);
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_cxp_pagado_insert ON pago_proveedor;
CREATE TRIGGER trg_cxp_pagado_insert
    AFTER INSERT ON pago_proveedor
    REFERENCING NEW TABLE AS new_table
    FOR EACH STATEMENT EXECUTE FUNCTION fn_recalcular_monto_pagado_cxp();

DROP TRIGGER IF EXISTS trg_cxp_pagado_update ON pago_proveedor;
CREATE TRIGGER trg_cxp_pagado_update
    AFTER UPDATE ON pago_proveedor
    REFERENCING NEW TABLE AS new_table OLD TABLE AS old_table
    FOR EACH STATEMENT EXECUTE FUNCTION fn_recalcular_monto_pagado_cxp();

DROP TRIGGER IF EXISTS trg_cxp_pagado_delete ON pago_proveedor;
CREATE TRIGGER trg_cxp_pagado_delete
    AFTER DELETE ON pago_proveedor
    REFERENCING OLD TABLE AS old_table
    FOR EACH STATEMENT EXECUTE FUNCTION fn_recalcular_monto_pagado_cxp();

-- ---------------------------------------------------------------------
-- Trigger de protección: impide UPDATE manual de cuenta_por_pagar.monto_pagado
-- (patrón fn_proteger_total_orden_compra). Permite el cambio solo cuando lo
-- realiza el trigger de recálculo (que coincide con la suma real).
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_proteger_monto_pagado_cxp()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_pagado_real NUMERIC(12,2);
BEGIN
    IF (NEW.monto_pagado IS DISTINCT FROM OLD.monto_pagado) THEN
        SELECT COALESCE(SUM(p.monto), 0) INTO v_pagado_real
        FROM pago_proveedor p
        WHERE p.id_cuenta_pagar = NEW.id_cuenta_pagar;
        IF (NEW.monto_pagado IS DISTINCT FROM v_pagado_real) THEN
            RAISE EXCEPTION 'El campo cuenta_por_pagar.monto_pagado es calculado automáticamente y no puede modificarse manualmente';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_proteger_monto_pagado_cxp ON cuenta_por_pagar;
CREATE TRIGGER trg_proteger_monto_pagado_cxp
    BEFORE UPDATE ON cuenta_por_pagar
    FOR EACH ROW EXECUTE FUNCTION fn_proteger_monto_pagado_cxp();
