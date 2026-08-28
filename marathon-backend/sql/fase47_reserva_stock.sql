-- =============================================================================
-- Fase 47 (D-02) — Reserva de stock: el pedido que entra al almacen retiene
--                  las unidades que va a llevarse
-- =============================================================================
-- Cierra el defecto D-02, que quedaba abierto en docs/PENDIENTE.md §3.
--
-- POR QUE
-- Hasta aqui, la falta de existencias solo frenaba la operacion en el DESPACHO
-- (L1/L4, EmpaqueService). Eso evita que el inventario se corrompa, pero no
-- evita que dos pedidos comprometan las mismas unidades: los dos se crean, los
-- dos pasan a 'procesado', los dos se recogen, y el segundo descubre en el
-- muelle que no hay mercancia. La reserva es lo que hace que ese choque ocurra
-- pronto, en la mesa, y no tarde, en el muelle.
--
-- LAS TRES DECISIONES DE NEGOCIO (tomadas el 2026-08-27 por el dueno del
-- proyecto; PENDIENTE.md §3 D-02 exigia responderlas ANTES de escribir codigo)
--
--   1. ¿CUANDO se reserva?  Al pasar el pedido de 'pendiente' a 'procesado'.
--      Crear un pedido comprueba disponibilidad y avisa, pero no retiene nada.
--      Motivo: hay 16.099 pedidos viviendo en 'pendiente'. Si la creacion
--      retuviera stock, cada pedido abandonado bloquearia mercancia real hasta
--      que alguien se acordara de anularlo. 'procesado' es el estado en el que
--      el pedido entra de verdad al almacen (es el requisito del picking), y
--      es ahi donde retener significa algo.
--
--   2. ¿QUIEN la libera?  La anulacion del pedido la libera; el despacho la
--      consume. No hay ningun otro camino, y los dos estan en la maquina de
--      estados que ya existia.
--
--   3. ¿QUE pasa con un pedido abandonado en 'procesado'?  La reserva CADUCA a
--      los 7 dias y aparece en el informe de reservas vencidas
--      (GET /api/inventario/reservas/vencidas), pero NO se libera sola. Soltar
--      automaticamente la reserva de un pedido que si se va a despachar manana
--      es peor que el problema que resuelve: lo decide una persona.
--
-- POR QUE UNA TABLA Y NO UNA COLUMNA 'stock_reservado' EN inventario
--   a) Una reserva tiene dueno (id_pedido) y fecha. Un contador agregado no:
--      con una columna no se puede contestar "¿quien retiene estas 8 unidades
--      y desde cuando?", que es exactamente lo que pide la decision 3.
--   b) inventario tiene grano (producto, bodega). En el momento de reservar
--      todavia NO se sabe la bodega: la elige el picking despues. La reserva es
--      por producto, y este grano lo dice explicitamente.
--   c) La fase 34 concede privilegios columna por columna (PENDIENTE.md §2.4).
--      Una tabla nueva se concede entera y de una vez, sin el riesgo de que una
--      columna nazca sin permisos.
--
-- LO QUE ESTE SCRIPT NO HACE: no reconstruye reservas historicas.
--   Hay 19.058 pedidos en 'procesado' de antes de esta fase. No se sabe cuales
--   siguen vivos, y fabricarles una reserva seria inventarse hechos —lo mismo
--   que docs/PENDIENTE.md §4 prohibe para los despachos sin movimiento. Las
--   reservas cuentan desde hoy hacia adelante. La consecuencia practica esta
--   dicha en voz alta: durante la transicion, el disponible que ve la
--   aplicacion es OPTIMISTA respecto de esos 19.058 pedidos.
--
-- REVERSION: fase47_reserva_stock_rollback.sql
-- =============================================================================

BEGIN;

CREATE TABLE reserva_stock (
    id_reserva     INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_pedido      INTEGER      NOT NULL,
    id_producto    INTEGER      NOT NULL,
    cantidad       INTEGER      NOT NULL,
    estado         VARCHAR(20)  NOT NULL DEFAULT 'activa',
    fecha_reserva  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_cierre   TIMESTAMP,
    motivo_cierre  VARCHAR(200),

    CONSTRAINT fk_reserva_pedido   FOREIGN KEY (id_pedido)
        REFERENCES pedido(id_pedido)     ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_reserva_producto FOREIGN KEY (id_producto)
        REFERENCES producto(id_producto) ON UPDATE CASCADE ON DELETE RESTRICT,

    -- Una reserva de cero unidades no es una reserva.
    CONSTRAINT chk_reserva_cantidad CHECK (cantidad > 0),

    CONSTRAINT chk_reserva_estado CHECK (estado IN ('activa', 'consumida', 'liberada')),

    -- Una reserva cerrada dice CUANDO se cerro; una activa no puede decirlo.
    -- Sin esto, 'consumida' sin fecha seria indistinguible de un error de
    -- codigo, y el informe de vencidas no podria fiarse de la fecha.
    CONSTRAINT chk_reserva_cierre CHECK (
        (estado = 'activa'  AND fecha_cierre IS NULL)
     OR (estado <> 'activa' AND fecha_cierre IS NOT NULL)
    )
);

COMMENT ON TABLE reserva_stock IS
    'Unidades comprometidas por un pedido que ya entro al almacen (F47, D-02). '
    'Se crea al pasar el pedido a procesado, se consume en el despacho y se '
    'libera al anular. El disponible de un producto es '
    'SUM(inventario.stock_actual) - SUM(reserva activa).';

COMMENT ON COLUMN reserva_stock.estado IS
    'activa = retiene unidades. consumida = el despacho se las llevo. '
    'liberada = el pedido se anulo o alguien solto la reserva a mano. '
    'Solo las activas descuentan del disponible.';

COMMENT ON COLUMN reserva_stock.fecha_reserva IS
    'Momento en que el pedido paso a procesado. Es la que hace vencer la '
    'reserva a los 7 dias en el informe; vencer NO la libera.';

-- Un pedido no puede tener dos reservas activas del mismo producto: las lineas
-- repetidas del mismo articulo se suman en una sola. El indice es parcial para
-- que un pedido pueda volver a reservar el mismo producto si la primera reserva
-- se libero (anular y rehacer es un camino legitimo).
CREATE UNIQUE INDEX uq_reserva_pedido_producto_activa
    ON reserva_stock (id_pedido, id_producto)
    WHERE estado = 'activa';

-- La consulta caliente: "¿cuanto hay reservado de este producto?". Solo indexa
-- las activas, que son las unicas que se suman.
CREATE INDEX idx_reserva_producto_activa
    ON reserva_stock (id_producto)
    WHERE estado = 'activa';

-- El informe de vencidas recorre las activas por fecha.
CREATE INDEX idx_reserva_activa_fecha
    ON reserva_stock (fecha_reserva)
    WHERE estado = 'activa';

-- Para liberar/consumir por pedido sin recorrer la tabla.
CREATE INDEX idx_reserva_pedido
    ON reserva_stock (id_pedido);

-- -----------------------------------------------------------------------------
-- PRIVILEGIOS  (imprescindible; ver PENDIENTE.md §2.4 y §2.5)
-- -----------------------------------------------------------------------------
-- Cada rol funcional se conecta con su propio usuario de PostgreSQL (F37), asi
-- que la tabla tiene que ser utilizable POR EL ROL QUE LA VA A PEDIR, no solo
-- por el administrador. El reparto sale de quien puede llegar a cada endpoint
-- en SecurityConfig:
--
--   LEER   los seis roles. El disponible de un producto se calcula en la
--          pantalla de pedidos (Operador de Pedidos), en el picking y el
--          empaque (Operador de Bodega), en inventario (todos), y en los
--          informes (Supervisor). Compras y Produccion lo leen al mirar
--          inventario. No hay dato sensible en una reserva.
--   CREAR  quien puede pasar un pedido a 'procesado':
--          PUT /api/pedidos/*/estado -> Administrador, Bodega, Pedidos.
--   TOCAR  los mismos, mas el despacho (Administrador y Bodega), que marca la
--          reserva como consumida.
--
-- BORRAR solo el administrador, y solo porque tiene que poder.
--   La regla de negocio es que una reserva no se borra: se cierra ('consumida'
--   o 'liberada'), y el historico de quien retuvo que y cuando es justamente lo
--   que hace util a esta tabla. Ningun camino de la aplicacion borra una fila.
--   Pero rol_administrador ya tiene DELETE sobre pedido, detalle_pedido e
--   inventario, y las FK de reserva_stock son ON DELETE RESTRICT: sin DELETE
--   aqui, un pedido con reservas quedaria imposible de borrar y el
--   administrador dejaria de poder limpiar la base. El primer sitio donde se
--   noto fue la limpieza de la fixtura de pruebas, que fallo con
--   "permiso denegado a la tabla reserva_stock".
--   A los otros cinco roles no se les concede: no lo necesitan.
GRANT SELECT ON reserva_stock TO rol_administrador;
GRANT SELECT ON reserva_stock TO rol_supervisor;
GRANT SELECT ON reserva_stock TO rol_operador_bodega;
GRANT SELECT ON reserva_stock TO rol_operador_pedidos;
GRANT SELECT ON reserva_stock TO rol_encargado_compras;
GRANT SELECT ON reserva_stock TO rol_encargado_produccion;

GRANT INSERT ON reserva_stock TO rol_administrador;
GRANT INSERT ON reserva_stock TO rol_operador_bodega;
GRANT INSERT ON reserva_stock TO rol_operador_pedidos;

GRANT UPDATE ON reserva_stock TO rol_administrador;
GRANT UPDATE ON reserva_stock TO rol_operador_bodega;
GRANT UPDATE ON reserva_stock TO rol_operador_pedidos;

GRANT DELETE ON reserva_stock TO rol_administrador;

-- La PK es IDENTITY: sin USAGE sobre su secuencia, el INSERT falla con
-- "permiso denegado a la secuencia reserva_stock_id_reserva_seq". Es el mismo
-- olvido que la F46 tuvo que corregir para seq_comprobante_interno.
GRANT USAGE, SELECT ON SEQUENCE reserva_stock_id_reserva_seq TO rol_administrador;
GRANT USAGE, SELECT ON SEQUENCE reserva_stock_id_reserva_seq TO rol_operador_bodega;
GRANT USAGE, SELECT ON SEQUENCE reserva_stock_id_reserva_seq TO rol_operador_pedidos;

COMMIT;

-- Verificacion
--   SELECT count(*) FROM reserva_stock;                       -- esperado: 0
--   SELECT (aclexplode(relacl)).grantee::regrole::text, (aclexplode(relacl)).privilege_type
--     FROM pg_class WHERE relname = 'reserva_stock';
--   -- esperado: SELECT para los seis rol_*, INSERT/UPDATE para tres.
