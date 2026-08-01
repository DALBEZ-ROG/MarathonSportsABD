-- =====================================================================
-- Fase 29 — Costeo de Producción
-- Agrega columnas de costo (promedio ponderado + costos de OP) y el
-- trigger de protección sobre orden_produccion.costo_materia_prima.
-- NO crea tablas nuevas (solo columnas + trigger). Idempotente.
-- =====================================================================

SET client_encoding = 'UTF8';

-- ---------------------------------------------------------------------
-- materia_prima: costo unitario promedio ponderado
-- ---------------------------------------------------------------------
ALTER TABLE materia_prima ADD COLUMN IF NOT EXISTS
    costo_unitario_promedio NUMERIC(12,4) NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_mp_costo') THEN
        ALTER TABLE materia_prima ADD CONSTRAINT chk_mp_costo
            CHECK (costo_unitario_promedio >= 0);
    END IF;
END$$;

-- ---------------------------------------------------------------------
-- orden_produccion_consumo: snapshot del costo al consumir + costo de línea
-- ---------------------------------------------------------------------
ALTER TABLE orden_produccion_consumo ADD COLUMN IF NOT EXISTS
    costo_unitario_snapshot NUMERIC(12,4) NOT NULL DEFAULT 0;

ALTER TABLE orden_produccion_consumo ADD COLUMN IF NOT EXISTS
    costo_linea NUMERIC(14,4) GENERATED ALWAYS AS
        (COALESCE(cantidad_real, cantidad_teorica) * costo_unitario_snapshot) STORED;

-- ---------------------------------------------------------------------
-- orden_produccion: costos (MP calculado por servicio; total y unitario GENERATED)
-- ---------------------------------------------------------------------
ALTER TABLE orden_produccion ADD COLUMN IF NOT EXISTS
    costo_materia_prima NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE orden_produccion ADD COLUMN IF NOT EXISTS
    costo_mano_obra NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE orden_produccion ADD COLUMN IF NOT EXISTS
    costo_indirecto NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE orden_produccion ADD COLUMN IF NOT EXISTS
    costo_total NUMERIC(14,2) GENERATED ALWAYS AS
        (costo_materia_prima + costo_mano_obra + costo_indirecto) STORED;
ALTER TABLE orden_produccion ADD COLUMN IF NOT EXISTS
    costo_unitario_producido NUMERIC(14,4) GENERATED ALWAYS AS
        (CASE WHEN cantidad_producida IS NULL OR cantidad_producida = 0
              THEN 0
              ELSE (costo_materia_prima + costo_mano_obra + costo_indirecto)
                   / cantidad_producida
         END) STORED;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_op_costos') THEN
        ALTER TABLE orden_produccion ADD CONSTRAINT chk_op_costos
            CHECK (costo_materia_prima >= 0 AND costo_mano_obra >= 0
                   AND costo_indirecto >= 0);
    END IF;
END$$;

-- ---------------------------------------------------------------------
-- Trigger de protección: costo_materia_prima no se modifica arbitrariamente.
--
-- IMPORTANTE: se usa el patrón "comparar contra el valor real" de F21/F23
-- (fn_proteger_total_orden_compra / fn_proteger_monto_pagado_cxp) y NO el
-- de pg_trigger_depth() = 0, porque dentro de una función de trigger
-- pg_trigger_depth() vale 1 (nunca 0) y por tanto esa condición NUNCA se
-- cumple: el trigger jamás protegería. Ver DEUDA_TECNICA.md (bug detectado
-- en fn_proteger_total_pedido, que arrastra ese mismo defecto).
--
-- Así, el UPDATE se permite solo si el nuevo valor coincide con la suma
-- real de los costo_linea de los consumos (lo que hace el servicio);
-- cualquier otro valor se rechaza.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_proteger_costo_materia_prima_op()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_costo_real NUMERIC(14,2);
BEGIN
    IF OLD.costo_materia_prima IS DISTINCT FROM NEW.costo_materia_prima THEN
        SELECT ROUND(COALESCE(SUM(c.costo_linea), 0), 2) INTO v_costo_real
        FROM orden_produccion_consumo c
        WHERE c.id_orden_produccion = NEW.id_orden_produccion;
        IF NEW.costo_materia_prima IS DISTINCT FROM v_costo_real THEN
            RAISE EXCEPTION 'El costo de materia prima se calcula automáticamente a partir de los consumos. No puede modificarse directamente.';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_proteger_costo_materia_prima_op ON orden_produccion;
CREATE TRIGGER trg_proteger_costo_materia_prima_op
    BEFORE UPDATE OF costo_materia_prima ON orden_produccion
    FOR EACH ROW EXECUTE FUNCTION fn_proteger_costo_materia_prima_op();

-- ---------------------------------------------------------------------
-- Función dedicada que usa el servicio para fijar costo_materia_prima.
-- Encapsula el cálculo: siempre escribe la suma real de los consumos, por
-- lo que pasa la validación del trigger de protección por construcción.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_set_costo_materia_prima_op(p_id INTEGER)
RETURNS NUMERIC LANGUAGE plpgsql AS $$
DECLARE
    v_costo NUMERIC(14,2);
BEGIN
    SELECT ROUND(COALESCE(SUM(c.costo_linea), 0), 2) INTO v_costo
    FROM orden_produccion_consumo c
    WHERE c.id_orden_produccion = p_id;

    UPDATE orden_produccion SET costo_materia_prima = v_costo
    WHERE id_orden_produccion = p_id;

    RETURN v_costo;
END;
$$;

-- Restaura el valor correcto si quedó desalineado por pruebas previas
UPDATE orden_produccion o SET costo_materia_prima = sub.real_costo
FROM (SELECT c.id_orden_produccion AS id, ROUND(COALESCE(SUM(c.costo_linea), 0), 2) AS real_costo
      FROM orden_produccion_consumo c GROUP BY c.id_orden_produccion) sub
WHERE o.id_orden_produccion = sub.id AND o.costo_materia_prima IS DISTINCT FROM sub.real_costo;
