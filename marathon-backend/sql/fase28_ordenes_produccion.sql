-- =====================================================================
-- Fase 28 — Órdenes de Producción (Manufactura)
-- Crea: orden_produccion, orden_produccion_consumo
-- Retrofit: FK fk_mmp_orden_produccion en movimiento_materia_prima (F26)
-- Trigger: trg_validar_op_producto_fabricado (solo productos fabricados)
-- Idempotente: IF NOT EXISTS / DO-block para constraints / CREATE OR REPLACE.
-- =====================================================================

SET client_encoding = 'UTF8';

-- ---------------------------------------------------------------------
-- orden_produccion (encabezado)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orden_produccion (
    id_orden_produccion INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_producto INTEGER NOT NULL,
    id_bodega_destino INTEGER NOT NULL,
    id_usuario_registro INTEGER NOT NULL,
    id_usuario_completa INTEGER,
    cantidad_planificada INTEGER NOT NULL,
    cantidad_producida INTEGER,
    estado VARCHAR(20) NOT NULL DEFAULT 'planificada',
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_inicio TIMESTAMP,
    fecha_fin TIMESTAMP,
    observaciones TEXT,
    CONSTRAINT chk_op_estado CHECK (estado IN
        ('planificada','en_proceso','completada','cancelada')),
    CONSTRAINT chk_op_cantidad_plan CHECK (cantidad_planificada > 0),
    CONSTRAINT chk_op_cantidad_prod CHECK (cantidad_producida IS NULL
        OR cantidad_producida >= 0),
    CONSTRAINT fk_op_producto FOREIGN KEY (id_producto)
        REFERENCES producto(id_producto)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_op_bodega FOREIGN KEY (id_bodega_destino)
        REFERENCES bodega(id_bodega)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_op_usuario_registro FOREIGN KEY (id_usuario_registro)
        REFERENCES usuario(id_usuario)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_op_usuario_completa FOREIGN KEY (id_usuario_completa)
        REFERENCES usuario(id_usuario)
        ON UPDATE CASCADE ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_op_producto ON orden_produccion(id_producto);
CREATE INDEX IF NOT EXISTS idx_op_estado ON orden_produccion(estado);
CREATE INDEX IF NOT EXISTS idx_op_fecha ON orden_produccion(fecha_creacion);

-- ---------------------------------------------------------------------
-- orden_produccion_consumo (líneas de consumo teórico/real + merma)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orden_produccion_consumo (
    id_consumo INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_orden_produccion INTEGER NOT NULL,
    id_materia_prima INTEGER NOT NULL,
    cantidad_teorica NUMERIC(12,3) NOT NULL,
    cantidad_real NUMERIC(12,3),
    merma NUMERIC(12,3) GENERATED ALWAYS AS
        (COALESCE(cantidad_real, cantidad_teorica) - cantidad_teorica) STORED,
    CONSTRAINT chk_opc_teorica CHECK (cantidad_teorica > 0),
    CONSTRAINT chk_opc_real CHECK (cantidad_real IS NULL OR cantidad_real >= 0),
    CONSTRAINT uq_opc_orden_materia UNIQUE (id_orden_produccion, id_materia_prima),
    CONSTRAINT fk_opc_orden FOREIGN KEY (id_orden_produccion)
        REFERENCES orden_produccion(id_orden_produccion)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_opc_materia_prima FOREIGN KEY (id_materia_prima)
        REFERENCES materia_prima(id_materia_prima)
        ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_opc_orden ON orden_produccion_consumo(id_orden_produccion);

-- ---------------------------------------------------------------------
-- RETROFIT (F26): FK de movimiento_materia_prima.id_orden_produccion.
-- Quedó pendiente en F26 porque orden_produccion no existía todavía.
-- ---------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_mmp_orden_produccion'
    ) THEN
        ALTER TABLE movimiento_materia_prima
            ADD CONSTRAINT fk_mmp_orden_produccion
            FOREIGN KEY (id_orden_produccion)
            REFERENCES orden_produccion(id_orden_produccion)
            ON UPDATE CASCADE ON DELETE SET NULL;
    END IF;
END$$;

-- ---------------------------------------------------------------------
-- Trigger de integridad: solo productos 'fabricado' admiten OP
-- (defensa en profundidad, mismo patrón que F27)
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_validar_op_producto_fabricado()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_origen VARCHAR(20);
BEGIN
    SELECT origen INTO v_origen FROM producto WHERE id_producto = NEW.id_producto;
    IF v_origen IS DISTINCT FROM 'fabricado' THEN
        RAISE EXCEPTION 'Solo productos con origen=fabricado pueden tener órdenes de producción (producto id: %, origen: %)', NEW.id_producto, v_origen;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validar_op_producto_fabricado ON orden_produccion;
CREATE TRIGGER trg_validar_op_producto_fabricado
    BEFORE INSERT OR UPDATE ON orden_produccion
    FOR EACH ROW EXECUTE FUNCTION fn_validar_op_producto_fabricado();
