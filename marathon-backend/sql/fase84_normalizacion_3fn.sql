-- =============================================================================
-- Fase 84 - Normalizacion: las cuatro cosas que la base guardaba dos veces
-- =============================================================================
-- POR QUE ESTA FASE
--
-- El dueño pidio revisar si el esquema esta en 3FN. Se reviso entero: 46
-- tablas, sus claves, sus dependencias y sus datos. El resultado esta escrito
-- en docs/PENDIENTE.md. Casi todo estaba bien: no hay ninguna tabla con clave
-- compuesta y dependencias parciales (todas tienen clave sustituta de una sola
-- columna, asi que 2FN se cumple sola), y los "totales" que parecen redundantes
-- —subtotal, saldo_pendiente, costo_total— son COLUMNAS GENERADAS: el gestor
-- garantiza la dependencia y no puede haber anomalia.
--
-- Quedaban CUATRO defectos reales. Tres son dependencias transitivas (3FN) y
-- uno es un grupo repetitivo dentro de una columna (1FN). Los tres primeros
-- nacieron de la misma costumbre: copiar en una tabla un dato que ya se podia
-- llegar a el siguiendo una clave ajena.
--
-- ---------------------------------------------------------------------------
-- 1FN - transportista.cobertura guardaba una LISTA dentro de un texto
-- ---------------------------------------------------------------------------
--   'Nacional, incluye Oriente'  ·  'Costa y Sierra'  ·  'Nacional'
--
-- Eso es una frase, no un dato. La base no puede responder "¿que
-- transportistas llegan al Oriente?" sin leer prosa. Y la cobertura SI es un
-- hecho del negocio que interesa consultar: el empaque ya sabe a que region va
-- el bulto. Se parte en dos: las regiones cubiertas van a una tabla
-- (transportista_cobertura), y lo que una region no sabe expresar —"flota
-- propia", "solo Quito y Guayaquil"— se queda como nota descriptiva.
--
-- ---------------------------------------------------------------------------
-- 3FN - pedido.transportista era el NOMBRE, no la clave
-- ---------------------------------------------------------------------------
-- La F77 creo el catalogo `transportista` y dejo el pedido a medias: la
-- pantalla ya elegia de una lista, pero lo que se guardaba seguia siendo el
-- texto. Con eso, renombrar un transportista en el catalogo deja los pedidos
-- viejos apuntando a un nombre que ya no existe (anomalia de actualizacion), y
-- nada impide escribir uno que no esta en la lista. Pasa a ser clave ajena.
--
-- ---------------------------------------------------------------------------
-- 3FN - pedido.region_destino era deducible: pedido -> cliente -> ciudad
-- ---------------------------------------------------------------------------
-- id_pedido -> id_cliente -> id_ciudad -> region. Un no-clave determinando
-- otro no-clave: dependencia transitiva de manual.
--
-- Se podria defender como "foto del momento del envio", y en otro sistema lo
-- seria. Aqui NO, y esta es la razon: el pedido no guarda ninguna direccion de
-- envio. La direccion se lee siempre viva de cliente.direccion_enc. Congelar
-- solo la region mientras la direccion es la actual produce el peor resultado
-- posible: un informe que dice "Costa" al lado de una direccion de Quito. O se
-- congela el destino entero, o no se congela nada. Hoy no se congela nada, asi
-- que la region se deduce, igual que la direccion.
--
-- ---------------------------------------------------------------------------
-- 3FN - cuenta_por_pagar.id_proveedor era deducible: cxp -> factura -> orden
-- ---------------------------------------------------------------------------
-- La prueba esta en el codigo, no en la teoria. FacturaCompraService hacia
-- literalmente `cuenta.setProveedor(orden.getProveedor())`: copiaba el
-- proveedor de la orden. 2.293 cuentas, las 2.293 coherentes hoy, y nada que
-- garantice que sigan siendolo si alguien corrige el proveedor de una orden.
--
-- ---------------------------------------------------------------------------
-- LO QUE SE MIRO Y SE DEJA COMO ESTA, A PROPOSITO
-- ---------------------------------------------------------------------------
-- · detalle_pedido.precio_unitario, comprobante_interno.total,
--   orden_produccion_consumo.costo_unitario_snapshot -> son FOTOS historicas.
--   El precio de hoy no es el precio al que se vendio. No es redundancia.
-- · inventario.stock_actual, materia_prima.stock_actual -> saldos acumulados,
--   deducibles de los movimientos pero mantenidos por trigger. Es la
--   desnormalizacion deliberada de cualquier sistema de inventario: sin ella,
--   comprobar stock bajo concurrencia obliga a sumar el historico entero.
-- · devolucion_proveedor_detalle.id_producto -> PARECE deducible siguiendo el
--   origen, pero no lo es: 2.824 lineas de orden de compra son materia prima y
--   NO tienen producto. Quitarlo romperia el caso, no lo arreglaria. Lo que si
--   deja al descubierto es un hueco del negocio, anotado en PENDIENTE.md: hoy
--   no se puede devolver materia prima a un proveedor.
-- · token_revocado.correo -> jti es la clave y todo depende de ella; no hay
--   dependencia entre no-claves. Es la lista negra del JWT y el correo viene
--   dentro del token.
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. 1FN: la cobertura del transportista deja de ser una frase
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transportista_cobertura (
    id_transportista INTEGER     NOT NULL,
    region           VARCHAR(20) NOT NULL,
    CONSTRAINT pk_transportista_cobertura PRIMARY KEY (id_transportista, region),
    CONSTRAINT fk_tc_transportista FOREIGN KEY (id_transportista)
        REFERENCES transportista (id_transportista)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_tc_region CHECK (
        region IN ('Costa', 'Sierra', 'Oriente', 'Insular'))
);

COMMENT ON TABLE transportista_cobertura IS
    'Regiones a las que llega cada transportista (F84). Antes esto vivia dentro '
    'de transportista.cobertura como texto libre —"Nacional, incluye Oriente"—, '
    'que es una lista metida en una columna: incumple la 1FN y no se puede '
    'consultar. El dominio de region es el mismo de ciudad.region.';

ALTER TABLE transportista
    ADD COLUMN IF NOT EXISTS nota VARCHAR(150) NULL;

COMMENT ON COLUMN transportista.nota IS
    'Matiz que una lista de regiones no sabe expresar (F84): "flota propia", '
    '"solo Quito y Guayaquil". Es texto para leer, no dato para consultar.';

-- Las regiones que decia cada frase, ahora como filas.
INSERT INTO transportista_cobertura (id_transportista, region)
SELECT t.id_transportista, r.region
  FROM transportista t
  JOIN (VALUES
        ('Servientrega',        'Costa'),
        ('Servientrega',        'Sierra'),
        ('Servientrega',        'Oriente'),
        ('Laar Courier',        'Costa'),
        ('Laar Courier',        'Sierra'),
        ('Laar Courier',        'Oriente'),
        ('Urbano Express',      'Costa'),
        ('Urbano Express',      'Sierra'),
        ('Urbano Express',      'Oriente'),
        ('Tramaco Express',     'Costa'),
        ('Tramaco Express',     'Sierra'),
        ('Tramaco Express',     'Oriente'),
        ('Correos del Ecuador', 'Costa'),
        ('Correos del Ecuador', 'Sierra'),
        ('Correos del Ecuador', 'Oriente'),
        ('Correos del Ecuador', 'Insular'),
        ('Speed Express',       'Costa'),
        ('Speed Express',       'Sierra'),
        ('Entrega propia',      'Costa'),
        ('Entrega propia',      'Sierra')
       ) AS r(nombre, region) ON r.nombre = t.nombre
ON CONFLICT DO NOTHING;

-- Y lo que la lista de regiones NO sabia decir.
UPDATE transportista SET nota = 'Mas fuerte en Costa y Sierra'
 WHERE nombre = 'Urbano Express' AND nota IS NULL;
UPDATE transportista SET nota = 'Flota propia, solo Quito y Guayaquil'
 WHERE nombre = 'Entrega propia' AND nota IS NULL;

ALTER TABLE transportista DROP COLUMN IF EXISTS cobertura;

-- ---------------------------------------------------------------------------
-- 2. 3FN: el pedido guarda la CLAVE del transportista, no su nombre
-- ---------------------------------------------------------------------------
ALTER TABLE pedido
    ADD COLUMN IF NOT EXISTS id_transportista INTEGER NULL;

-- El traspaso va dentro de un bloque condicionado a que la columna vieja siga
-- existiendo, para que esta fase se pueda repetir sobre una base ya migrada
-- sin reventar por referirse a una columna que ya no esta.
DO $$
DECLARE tenian INT; tienen INT; huerfanos INT; nombres TEXT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'pedido'
                      AND column_name = 'transportista') THEN
        RAISE NOTICE 'pedido.transportista ya no existe: traspaso ya hecho.';
        RETURN;
    END IF;

    EXECUTE 'SELECT count(*) FROM pedido WHERE transportista IS NOT NULL'
       INTO tenian;

    EXECUTE 'UPDATE pedido p SET id_transportista = t.id_transportista '
            'FROM transportista t WHERE t.nombre = p.transportista '
            'AND p.transportista IS NOT NULL AND p.id_transportista IS NULL';

    -- Ningun pedido puede perder su transportista por el camino. Si alguno
    -- tenia un nombre que no esta en el catalogo, la fase se para entera.
    EXECUTE 'SELECT count(*), string_agg(DISTINCT transportista, '', '') '
            'FROM pedido WHERE transportista IS NOT NULL '
            'AND id_transportista IS NULL'
       INTO huerfanos, nombres;
    IF huerfanos > 0 THEN
        RAISE EXCEPTION '% pedidos tienen un transportista que no esta en el '
                        'catalogo (%). Anadelo a transportista antes de migrar: '
                        'si se sigue, esos pedidos se quedan sin transportista.',
                        huerfanos, nombres;
    END IF;

    -- La invariante que de verdad importa: salen tantos como entraron. No
    -- "al menos dos" —eso seria una suposicion sobre los datos de una base
    -- concreta, y la de pruebas tiene otros.
    SELECT count(*) INTO tienen FROM pedido WHERE id_transportista IS NOT NULL;
    IF tienen <> tenian THEN
        RAISE EXCEPTION 'Entraron % pedidos con transportista y salieron %. '
                        'Se perdio el dato por el camino.', tenian, tienen;
    END IF;
    RAISE NOTICE 'Transportista traspasado en % pedidos.', tienen;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conname = 'fk_pedido_transportista') THEN
        ALTER TABLE pedido
            ADD CONSTRAINT fk_pedido_transportista FOREIGN KEY (id_transportista)
                REFERENCES transportista (id_transportista)
                ON UPDATE CASCADE ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_pedido_transportista
    ON pedido (id_transportista) WHERE id_transportista IS NOT NULL;

COMMENT ON COLUMN pedido.id_transportista IS
    'Transportista que se lleva el bulto (F84). Antes era pedido.transportista, '
    'un VARCHAR(100) escrito a mano: "Servientrega" y "servientrega" eran dos '
    'transportistas distintos para cualquier consulta.';

ALTER TABLE pedido DROP COLUMN IF EXISTS transportista;

-- ---------------------------------------------------------------------------
-- 3. 3FN: la region de destino se deduce, no se guarda
-- ---------------------------------------------------------------------------
-- Antes de tirar la columna hay que probar que no dice nada distinto de lo que
-- dice la ciudad del cliente. Si dijera otra cosa, habria informacion que se
-- perderia y esta fase no debe seguir.
DO $$
DECLARE discrepan INT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'pedido'
                      AND column_name = 'region_destino') THEN
        RETURN;
    END IF;

    EXECUTE 'SELECT count(*) FROM pedido p '
            'JOIN cliente c ON c.id_cliente = p.id_cliente '
            'JOIN ciudad ci ON ci.id_ciudad = c.id_ciudad '
            'WHERE p.region_destino IS NOT NULL '
            'AND p.region_destino IS DISTINCT FROM ci.region'
       INTO discrepan;
    IF discrepan > 0 THEN
        RAISE EXCEPTION '% pedidos tienen una region de destino distinta de la '
                        'region de la ciudad de su cliente. Eso ya no es un dato '
                        'repetido, es un dato propio: revisalo a mano antes de '
                        'borrar la columna.', discrepan;
    END IF;
END $$;

ALTER TABLE pedido DROP COLUMN IF EXISTS region_destino;

-- ---------------------------------------------------------------------------
-- 4. 3FN: la cuenta por pagar llega al proveedor por su factura
-- ---------------------------------------------------------------------------
DO $$
DECLARE discrepan INT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'cuenta_por_pagar'
                      AND column_name = 'id_proveedor') THEN
        RETURN;
    END IF;

    EXECUTE 'SELECT count(*) FROM cuenta_por_pagar cp '
            'JOIN factura_compra f ON f.id_factura_compra = cp.id_factura_compra '
            'JOIN orden_compra o ON o.id_orden_compra = f.id_orden_compra '
            'WHERE cp.id_proveedor IS DISTINCT FROM o.id_proveedor'
       INTO discrepan;
    IF discrepan > 0 THEN
        RAISE EXCEPTION '% cuentas por pagar apuntan a un proveedor distinto del '
                        'de su orden de compra. Cuadralas antes de quitar la '
                        'columna: el dato bueno es el de la orden.', discrepan;
    END IF;
END $$;

ALTER TABLE cuenta_por_pagar DROP COLUMN IF EXISTS id_proveedor;

-- Filtrar cuentas por proveedor ahora pasa por la factura y la orden. Esos dos
-- saltos necesitan sus indices; sin ellos el listado de cuentas por proveedor
-- pasaria de una lectura de indice a recorrer la tabla entera.
CREATE INDEX IF NOT EXISTS idx_factura_compra_orden
    ON factura_compra (id_orden_compra);
CREATE INDEX IF NOT EXISTS idx_orden_compra_proveedor
    ON orden_compra (id_proveedor);

-- ---------------------------------------------------------------------------
-- 5. Privilegios. Regla 4 de PENDIENTE.md: lo nuevo nace SIN NADA, y una
--    COLUMNA nueva tampoco hereda los permisos de su tabla.
-- ---------------------------------------------------------------------------
-- id_transportista hereda exactamente los permisos que tenia la columna de
-- texto a la que sustituye: lee todo el mundo, escribe quien empaca.
GRANT SELECT (id_transportista) ON pedido TO rol_administrador;
GRANT SELECT (id_transportista) ON pedido TO rol_supervisor;
GRANT SELECT (id_transportista) ON pedido TO rol_operador_bodega;
GRANT SELECT (id_transportista) ON pedido TO rol_operador_pedidos;
GRANT INSERT (id_transportista), UPDATE (id_transportista) ON pedido TO rol_administrador;
GRANT UPDATE (id_transportista) ON pedido TO rol_operador_bodega;

-- La cobertura la lee quien ve el catalogo; la nota igual.
GRANT SELECT ON transportista_cobertura TO rol_administrador;
GRANT SELECT ON transportista_cobertura TO rol_supervisor;
GRANT SELECT ON transportista_cobertura TO rol_operador_bodega;
GRANT SELECT ON transportista_cobertura TO rol_operador_pedidos;

GRANT SELECT (nota) ON transportista TO rol_administrador;
GRANT SELECT (nota) ON transportista TO rol_supervisor;
GRANT SELECT (nota) ON transportista TO rol_operador_bodega;
GRANT SELECT (nota) ON transportista TO rol_operador_pedidos;

-- ---------------------------------------------------------------------------
-- 6. Comprobaciones, dentro de la transaccion
-- ---------------------------------------------------------------------------
DO $$
DECLARE n INT; faltan TEXT;
BEGIN
    -- (Que no se perdiera ningun transportista se comprueba arriba, donde se
    --  traspasa: alli se sabe cuantos habia antes. Aqui ya no.)

    -- Ningun pedido apunta a un transportista que no exista. La clave ajena lo
    -- impide de ahora en adelante; esto comprueba lo que ya habia.
    SELECT count(*) INTO n
      FROM pedido p
     WHERE p.id_transportista IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM transportista t
                        WHERE t.id_transportista = p.id_transportista);
    IF n > 0 THEN
        RAISE EXCEPTION '% pedidos apuntan a un transportista inexistente.', n;
    END IF;

    -- Cada transportista llega a alguna parte.
    SELECT count(*) INTO n
      FROM transportista t
     WHERE NOT EXISTS (SELECT 1 FROM transportista_cobertura c
                        WHERE c.id_transportista = t.id_transportista);
    IF n > 0 THEN
        RAISE EXCEPTION '% transportistas se quedaron sin ninguna region. El '
                        'catalogo diria que no llegan a ningun sitio.', n;
    END IF;

    -- Las columnas viejas ya no estan.
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'pedido'
                  AND column_name IN ('transportista', 'region_destino')) THEN
        RAISE EXCEPTION 'pedido todavia tiene transportista o region_destino.';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'cuenta_por_pagar'
                  AND column_name = 'id_proveedor') THEN
        RAISE EXCEPTION 'cuenta_por_pagar todavia tiene id_proveedor.';
    END IF;

    -- Y los permisos de la columna nueva existen: sin ellos el empaque
    -- fallaria al guardar, y con un 42501 que no dice donde.
    SELECT string_agg(r, ', ') INTO faltan
      FROM (VALUES ('rol_operador_bodega'), ('rol_administrador')) AS v(r)
     WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.column_privileges
         WHERE table_schema = 'public' AND table_name = 'pedido'
           AND column_name = 'id_transportista'
           AND privilege_type = 'UPDATE' AND grantee = v.r);
    IF faltan IS NOT NULL THEN
        RAISE EXCEPTION 'Sin UPDATE sobre pedido.id_transportista: %.', faltan;
    END IF;
END $$;

COMMIT;

-- Verificacion
--   SELECT t.nombre, string_agg(c.region, ', ' ORDER BY c.region), t.nota
--     FROM transportista t LEFT JOIN transportista_cobertura c USING (id_transportista)
--    GROUP BY t.id_transportista, t.nombre, t.nota ORDER BY t.nombre;
--
--   SELECT p.id_pedido, t.nombre AS transportista, ci.region AS region_destino
--     FROM pedido p
--     JOIN cliente c ON c.id_cliente = p.id_cliente
--     JOIN ciudad ci ON ci.id_ciudad = c.id_ciudad
--     LEFT JOIN transportista t ON t.id_transportista = p.id_transportista
--    WHERE p.id_transportista IS NOT NULL;
