-- ============================================================================
-- FASE 31 · PASO 0 — UNIDADES DE MEDIDA QUE FALTABAN EN EL SEED
-- ----------------------------------------------------------------------------
-- Script IDEMPOTENTE. Ejecutar ENTRE seed_marathon_sports.sql y
-- fase31_seed_demo_bloques_nuevos.sql.
--
-- QUE PROBLEMA RESUELVE
--   seed_marathon_sports.sql crea TRES unidades de medida (Unidad, Par, Caja),
--   pero fase31 da de alta materias primas que referencian la unidad 4 y la 6:
--
--       ('Cinta elastica 2cm', ..., 6, ...)   -> metros
--       ('Tinta de estampado', ..., 4, ...)   -> litros
--
--   Sobre una base construida desde cero esas dos filas no existen y fase31
--   aborta en su primera sentencia:
--
--       ERROR: insert or update on table "materia_prima" violates foreign key
--              constraint "fk_materia_prima_unidad"
--       DETAIL: Key (id_unidad_medida)=(6) is not present in table "unidad_medida".
--
--   En la base de desarrollo el problema no se veia porque alli habia NUEVE
--   unidades, dadas de alta por la aplicacion a lo largo del proyecto y que
--   nunca llegaron a ningun script. Es una dependencia que solo existia en la
--   base viva, igual que la que documenta DEUDA_TECNICA.md sobre el DDL base.
--   Se destapo al construir el entorno desde cero en un cluster limpio.
--
-- POR QUE CON ID EXPLICITO Y NO DEJANDO QUE LA SECUENCIA DECIDA
--   fase31 referencia las unidades POR NUMERO, no por nombre. Si estas filas se
--   insertaran en cualquier orden, la 6 podria acabar siendo 'Litro' y las
--   materias primas quedarian medidas en unidades absurdas: la clave foranea
--   estaria satisfecha y nadie se enteraria. Es exactamente la clase de fallo
--   que las restricciones no ven, asi que el id se fija aqui.
--
--   Los ids 1..3 los ocupa el seed (Unidad, Par, Caja). Estos completan hasta 9
--   conservando el significado que fase31 espera para la 4 y la 6.
-- ============================================================================

\echo ''
\echo '--- Unidades de medida que faltan tras el seed ---'

-- OVERRIDING SYSTEM VALUE es obligatorio: las 37 claves primarias del esquema
-- son GENERATED ALWAYS AS IDENTITY, y sin esa clausula PostgreSQL rechaza el
-- id explicito con "cannot insert a non-DEFAULT value into column".
INSERT INTO unidad_medida (id_unidad_medida, nombre, abreviatura)
OVERRIDING SYSTEM VALUE
VALUES
    (4, 'Litro',      'L'),    -- fase31: tintas de estampado
    (5, 'Gramo',      'G'),
    (6, 'Metro',      'M'),    -- fase31: cinta elastica
    (7, 'Kilogramo',  'KG'),
    (8, 'Mililitro',  'ML'),
    (9, 'Paquete',    'PQT')
ON CONFLICT (id_unidad_medida) DO NOTHING;

-- La secuencia se queda en 3 tras el seed, asi que el proximo INSERT sin id
-- explicito intentaria el 4 y chocaria con la fila recien creada. Se reposiciona
-- al maximo real. GREATEST protege el caso de reejecucion sobre una base que ya
-- tenga mas filas.
SELECT setval(
    pg_get_serial_sequence('unidad_medida', 'id_unidad_medida'),
    GREATEST((SELECT max(id_unidad_medida) FROM unidad_medida), 1)
) AS secuencia_reposicionada;

-- Verificacion: las 9 unidades, y las dos que fase31 necesita por numero.
SELECT count(*) AS unidades,
       (SELECT nombre FROM unidad_medida WHERE id_unidad_medida = 4) AS id_4_debe_ser_litro,
       (SELECT nombre FROM unidad_medida WHERE id_unidad_medida = 6) AS id_6_debe_ser_metro
FROM unidad_medida;
