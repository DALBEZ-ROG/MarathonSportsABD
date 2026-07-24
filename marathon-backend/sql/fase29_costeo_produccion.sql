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
-- Trigger de protección: costo_materia_prima no se modifica manualmente.
-- El servicio lo actualiza mediante fn_actualizar_costo_mp_op() (SECURITY
-- DEFINER via pg_trigger_depth), que ejecuta el UPDATE de forma controlada.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_proteger_costo_materia_prima_op()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.costo_materia_prima IS DISTINCT FROM NEW.costo_materia_prima
       AND pg_trigger_depth() = 0
       AND current_setting('app.allow_costo_mp_update', true) IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION 'El costo de materia prima se calcula automáticamente a partir de los consumos. No puede modificarse directamente.';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_proteger_costo_materia_prima_op ON orden_produccion;
CREATE TRIGGER trg_proteger_costo_materia_prima_op
    BEFORE UPDATE OF costo_materia_prima ON orden_produccion
    FOR EACH ROW EXECUTE FUNCTION fn_proteger_costo_materia_prima_op();

-- ---------------------------------------------------------------------
-- Función controlada para que el servicio actualice costo_materia_prima
-- sin violar el trigger de protección. Setea la variable de sesión que
-- el trigger reconoce como "actualización autorizada".
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_set_costo_materia_prima_op(p_id INTEGER, p_costo NUMERIC)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    PERFORM set_config('app.allow_costo_mp_update', 'on', true);
    UPDATE orden_produccion SET costo_materia_prima = p_costo WHERE id_orden_produccion = p_id;
    PERFORM set_config('app.allow_costo_mp_update', 'off', true);
END;
$$;
