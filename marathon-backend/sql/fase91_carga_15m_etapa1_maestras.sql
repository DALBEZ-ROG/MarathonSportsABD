-- =============================================================================
-- FASE 91 · Etapa 1 — Tablas maestras
-- -----------------------------------------------------------------------------
-- Objetivo global: 1.500.000 filas en cada una de las 34 tablas maestras y
-- transaccionales. Las 8 tablas de catalogo y seguridad (rol, permiso,
-- rol_permiso, usuario_rol, unidad_medida, categoria, ciudad, bodega) NO se
-- tocan: son el modelo de permisos y las dimensiones de las que cuelga todo.
-- Un millon de roles no seria volumen, seria MATRIZ_ROLES.md destruido.
--
-- Esta etapa: usuario, proveedor, transportista, materia_prima, cliente,
-- producto. Son las seis de las que dependen todas las demas.
--
-- Se ejecuta como postgres (hacen falta ALTER TABLE ... DISABLE TRIGGER) y con
-- la clave de cifrado publicada en la sesion:
--   powershell -ExecutionPolicy Bypass -File scripts/cifrado/gestionar_clave.ps1
--       -Accion Ejecutar -PgPort 5433
--       -Script marathon-backend/sql/fase91_carga_15m_etapa1_maestras.sql
--
-- Es reanudable: carga.faltan() mide contra el estado real, asi que volver a
-- ejecutarlo tras un fallo completa lo que quede y no duplica nada.
-- =============================================================================

\set ON_ERROR_STOP on
\timing on

\getenv clave MARATHON_CRYPTO_KEY
SELECT set_config('app.crypto_key', :'clave', false) IS NOT NULL AS clave_publicada;

SET work_mem = '256MB';
SET maintenance_work_mem = '1GB';
SET synchronous_commit = off;

\ir fase91_andamiaje.sql

-- -----------------------------------------------------------------------------
-- Triggers de auditoria y de hash: fuera durante la carga.
--   trg_auditoria_* escribiria una fila en auditoria_cambios por cada INSERT
--   (6.000.000 en total) y multiplicaria el tiempo de carga. auditoria_cambios
--   se puebla despues, en la etapa 2, con su propio contenido.
--   trg_cliente_hash_correo descifraria cada correo solo para volver a
--   hashearlo; aqui el hash se calcula desde el texto en claro, que es
--   exactamente la misma operacion sin el viaje de ida y vuelta. El ensayo
--   comprobo que el valor resultante es identico.
-- La etapa 5 los reactiva y verifica que los 30 quedan activos.
-- -----------------------------------------------------------------------------
ALTER TABLE usuario   DISABLE TRIGGER trg_auditoria_usuario;
ALTER TABLE producto  DISABLE TRIGGER trg_auditoria_producto;
ALTER TABLE proveedor DISABLE TRIGGER trg_auditoria_proveedor;
ALTER TABLE cliente   DISABLE TRIGGER trg_auditoria_cliente;
ALTER TABLE cliente   DISABLE TRIGGER trg_cliente_hash_correo;

-- -----------------------------------------------------------------------------
-- 1. usuario
-- -----------------------------------------------------------------------------
CALL carga.marcar('usuario', 'id_usuario');
INSERT INTO usuario (nombre, apellido, correo, password, estado, created_at)
SELECT v.nom[1 + mod(g * 13, 20)],
       v.ape[1 + mod(g * 29, 20)],
       'masivo' || g || '@marathon.test',
       s.inerte,
       CASE WHEN mod(g, 20) = 0 THEN 'inactivo' ELSE 'activo' END,
       carga.fecha(g)
FROM generate_series(1, carga.faltan('usuario')) g, carga.voc v, carga.secreto s;
CALL carga.cerrar('usuario', 'id_usuario');

-- -----------------------------------------------------------------------------
-- 2. proveedor  (4 columnas cifradas)
-- -----------------------------------------------------------------------------
CALL carga.marcar('proveedor', 'id_proveedor');
INSERT INTO proveedor (nombre, estado, created_at, correo_enc, telefono_enc, direccion_enc, contacto_enc)
SELECT v.marca[1 + mod(g, 10)] || ' Distribucion ' || g,
       CASE WHEN mod(g, 30) = 0 THEN 'inactivo' ELSE 'activo' END,
       carga.fecha(g),
       carga.cifrar('compras' || g || '@' || lower(v.marca[1 + mod(g, 10)]) || '.test'),
       carga.cifrar('02' || lpad(mod(g * 7919, 10000000)::text, 7, '0')),
       carga.cifrar('Parque Industrial ' || mod(g, 500) || ', nave ' || mod(g, 90)),
       carga.cifrar(v.nom[1 + mod(g * 17, 20)] || ' ' || v.ape[1 + mod(g * 23, 20)])
FROM generate_series(1, carga.faltan('proveedor')) g, carga.voc v;
CALL carga.cerrar('proveedor', 'id_proveedor');

-- -----------------------------------------------------------------------------
-- 3. transportista
-- -----------------------------------------------------------------------------
CALL carga.marcar('transportista', 'id_transportista');
INSERT INTO transportista (nombre, estado, nota)
SELECT 'Transportes ' || v.ape[1 + mod(g * 11, 20)] || ' ' || g,
       CASE WHEN mod(g, 40) = 0 THEN 'inactivo' ELSE 'activo' END,
       'Ruta ' || mod(g, 300)
FROM generate_series(1, carga.faltan('transportista')) g, carga.voc v;
CALL carga.cerrar('transportista', 'id_transportista');

-- -----------------------------------------------------------------------------
-- 4. materia_prima
-- -----------------------------------------------------------------------------
CALL carga.marcar('materia_prima', 'id_materia_prima');
INSERT INTO materia_prima (nombre, descripcion, id_unidad_medida, estado, created_at,
                           stock_actual, stock_minimo, costo_unitario_promedio)
SELECT 'MP ' || g || ' ' || v.lin[1 + mod(g * 7, 10)],
       'Insumo de produccion, lote ' || mod(g, 5000),
       c.um[1 + mod(g, array_length(c.um, 1))],
       CASE WHEN mod(g, 50) = 0 THEN 'inactivo' ELSE 'activo' END,
       carga.fecha(g),
       round((mod(g * 7919, 90000) / 10.0)::numeric, 3),
       round((mod(g * 37, 5000) / 10.0)::numeric, 3),
       round((0.5 + mod(g * 104729, 250000) / 1000.0)::numeric, 4)
FROM generate_series(1, carga.faltan('materia_prima')) g, carga.voc v, carga.cat c;
CALL carga.cerrar('materia_prima', 'id_materia_prima');

-- -----------------------------------------------------------------------------
-- 5. cliente  (3 columnas cifradas + hash unico del correo + cedula unica)
--    Las cedulas arrancan en 2.000.000.001: el unico documento preexistente en
--    la base es 1719171917, asi que no hay solape posible con
--    uq_cliente_documento, y las 1,5M cumplen el CHECK de 10 digitos.
-- -----------------------------------------------------------------------------
CALL carga.marcar('cliente', 'id_cliente');
INSERT INTO cliente (id_ciudad, nombre, apellido, estado, created_at,
                     correo_enc, correo_hash, telefono_enc, direccion_enc,
                     tipo_documento, numero_documento)
SELECT c.ciudad[1 + mod(g * 31, array_length(c.ciudad, 1))],
       v.nom[1 + mod(g * 13, 20)],
       v.ape[1 + mod(g * 29, 20)],
       CASE WHEN mod(g, 25) = 0 THEN 'inactivo' ELSE 'activo' END,
       carga.fecha(g),
       carga.cifrar('cli' || g || '@marathon.test'),
       fn_hash_correo('cli' || g || '@marathon.test'),
       carga.cifrar('09' || lpad(mod(g * 7919, 100000000)::text, 8, '0')),
       carga.cifrar('Av. ' || v.ape[1 + mod(g * 3, 20)] || ' ' || mod(g, 3000) || ' y Calle ' || mod(g, 200)),
       'cedula',
       (2000000000 + g)::text
FROM generate_series(1, carga.faltan('cliente')) g, carga.voc v, carga.cat c;
CALL carga.cerrar('cliente', 'id_cliente');

-- -----------------------------------------------------------------------------
-- 6. producto
--    El 60% sale 'fabricado' a proposito: lista_materiales necesita 1,5M pares
--    UNIQUE (producto, materia_prima) y su trigger exige que el producto sea
--    fabricado. Con 900.000 fabricados y 3 materias primas cada uno hay 2,7M
--    pares posibles: holgura suficiente. orden_produccion tiene la misma
--    exigencia y no lleva UNIQUE, asi que le sobra.
-- -----------------------------------------------------------------------------
CALL carga.marcar('producto', 'id_producto');
INSERT INTO producto (id_categoria, nombre, descripcion, precio, estado,
                      id_unidad_medida, created_at, origen)
SELECT c.categoria[1 + mod(g * 7, array_length(c.categoria, 1))],
       v.art[1 + mod(g * 3, 10)] || ' ' || v.marca[1 + mod(g * 11, 10)] || ' ' ||
       v.lin[1 + mod(g * 19, 10)] || ' #' || g,
       'Articulo deportivo, temporada ' || (2024 + mod(g, 3)) || ', referencia ' || mod(g * 7919, 999999),
       round((3 + mod(g * 104729, 45000) / 100.0)::numeric, 2),
       CASE WHEN mod(g, 35) = 0 THEN 'inactivo' ELSE 'activo' END,
       c.um[1 + mod(g * 5, array_length(c.um, 1))],
       carga.fecha(g),
       CASE WHEN mod(g, 5) < 3 THEN 'fabricado' ELSE 'comprado' END
FROM generate_series(1, carga.faltan('producto')) g, carga.voc v, carga.cat c;
CALL carga.cerrar('producto', 'id_producto');

-- -----------------------------------------------------------------------------
-- 7. Cierre de etapa. El ANALYZE no es cosmetico: las etapas siguientes eligen
--    planes de union sobre estas seis tablas y sin estadisticas frescas el
--    planificador seguiria creyendo que producto tiene 109 filas.
-- -----------------------------------------------------------------------------
ANALYZE usuario; ANALYZE proveedor; ANALYZE transportista;
ANALYZE materia_prima; ANALYZE cliente; ANALYZE producto;

SELECT tabla, id_min, id_max, filas FROM carga.rango ORDER BY tabla;

SELECT 'usuario' AS tabla, count(*) FROM usuario
UNION ALL SELECT 'proveedor',     count(*) FROM proveedor
UNION ALL SELECT 'transportista', count(*) FROM transportista
UNION ALL SELECT 'materia_prima', count(*) FROM materia_prima
UNION ALL SELECT 'cliente',       count(*) FROM cliente
UNION ALL SELECT 'producto',      count(*) FROM producto;

SELECT max(created_at) AS fecha_mas_alta_cliente FROM cliente;
SELECT count(*) FILTER (WHERE origen = 'fabricado') AS productos_fabricados FROM producto;
