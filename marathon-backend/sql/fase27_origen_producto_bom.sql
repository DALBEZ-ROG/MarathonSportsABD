-- =====================================================================
-- Fase 27 — Diferenciacion de Origen del Producto + Lista de Materiales (BOM)
-- Modifica: producto (columna origen)
-- Crea: lista_materiales
-- + 2 triggers de integridad (defensa en profundidad, la BD protege
--   aunque el backend valide):
--     trg_validar_bom_producto_fabricado  -> solo productos fabricados admiten BOM
--     trg_validar_cambio_origen_producto  -> no cambiar a comprado si hay BOM activo
-- Idempotente.
-- =====================================================================

-- ---------------------------------------------------------------------
-- producto.origen ('comprado' | 'fabricado')
-- DEFAULT 'comprado' es seguro: no rompe los 105 productos del seed
-- original, todos eran de marcas (Nike, Adidas, etc.)
-- ---------------------------------------------------------------------
ALTER TABLE producto ADD COLUMN IF NOT EXISTS origen VARCHAR(20) NOT NULL DEFAULT 'comprado';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_producto_origen'
    ) THEN
        ALTER TABLE producto ADD CONSTRAINT chk_producto_origen
            CHECK (origen IN ('comprado','fabricado'));
    END IF;
END$$;

-- ---------------------------------------------------------------------
-- lista_materiales (BOM: receta de un producto fabricado)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lista_materiales (
    id_bom INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_producto INTEGER NOT NULL,
    id_materia_prima INTEGER NOT NULL,
    cantidad_necesaria NUMERIC(12,3) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'activo',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_bom_producto_materia UNIQUE (id_producto, id_materia_prima),
    CONSTRAINT chk_bom_cantidad CHECK (cantidad_necesaria > 0),
    CONSTRAINT chk_bom_estado CHECK (estado IN ('activo','inactivo')),
    CONSTRAINT fk_bom_producto FOREIGN KEY (id_producto)
        REFERENCES producto(id_producto)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_bom_materia_prima FOREIGN KEY (id_materia_prima)
        REFERENCES materia_prima(id_materia_prima)
        ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_bom_producto ON lista_materiales(id_producto);
CREATE INDEX IF NOT EXISTS idx_bom_materia_prima ON lista_materiales(id_materia_prima);

-- ---------------------------------------------------------------------
-- Trigger 1: solo productos con origen='fabricado' pueden tener BOM
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_validar_bom_producto_fabricado()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_origen VARCHAR(20);
BEGIN
    SELECT origen INTO v_origen FROM producto WHERE id_producto = NEW.id_producto;
    IF v_origen IS DISTINCT FROM 'fabricado' THEN
        RAISE EXCEPTION 'Solo productos con origen=fabricado pueden tener lista de materiales (producto id: %, origen actual: %)', NEW.id_producto, v_origen;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validar_bom_producto_fabricado ON lista_materiales;
CREATE TRIGGER trg_validar_bom_producto_fabricado
    BEFORE INSERT OR UPDATE ON lista_materiales
    FOR EACH ROW EXECUTE FUNCTION fn_validar_bom_producto_fabricado();

-- ---------------------------------------------------------------------
-- Trigger 2: no cambiar un producto a 'comprado' si tiene BOM activo
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_validar_cambio_origen_producto()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_tiene_bom BOOLEAN;
BEGIN
    IF NEW.origen = 'comprado' AND OLD.origen = 'fabricado' THEN
        SELECT EXISTS(
            SELECT 1 FROM lista_materiales
            WHERE id_producto = NEW.id_producto AND estado = 'activo'
        ) INTO v_tiene_bom;
        IF v_tiene_bom THEN
            RAISE EXCEPTION 'No se puede cambiar el producto a comprado: tiene lista de materiales activa. Elimine o desactive el BOM primero.';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validar_cambio_origen_producto ON producto;
CREATE TRIGGER trg_validar_cambio_origen_producto
    BEFORE UPDATE OF origen ON producto
    FOR EACH ROW EXECUTE FUNCTION fn_validar_cambio_origen_producto();
