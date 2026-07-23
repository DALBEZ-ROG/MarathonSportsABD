-- =====================================================================
-- Fase 00 — DDL BASE (tablas F1–F20 + funciones/triggers de base)
-- =====================================================================
-- GENERADO con pg_dump --schema-only sobre las 20 tablas base, porque el
-- repo no incluia un DDL base (pendiente critico detectado en F27).
--
-- Contenido:
--   1) Funciones de los triggers de las tablas base (pg_dump con -t NO
--      exporta funciones, por eso se anteponen aqui para que el archivo
--      sea autoejecutable en una BD vacia).
--   2) Las 20 tablas base con sus secuencias, indices, constraints y
--      triggers.
--
-- NOTA (estado real de la BD): la tabla `producto` ya incluye la columna
-- `origen` y el trigger `trg_validar_cambio_origen_producto` de la F27,
-- porque esta fase ALTERo una tabla base. El cuerpo de esa funcion
-- referencia `lista_materiales` (que se crea en F27); PostgreSQL NO valida
-- tablas dentro del cuerpo plpgsql al crear la funcion, asi que fase00
-- corre sin problema en una BD vacia. Ademas fase27 es idempotente
-- (ADD COLUMN IF NOT EXISTS / CREATE OR REPLACE / DROP TRIGGER IF EXISTS),
-- por lo que volver a aplicarla no genera conflicto.
--
-- Orden de ejecucion completo: ver SETUP_COMPLETO.md en la raiz.
-- =====================================================================

SET client_encoding = 'UTF8';

-- ---------------------------------------------------------------------
-- 1) Funciones de triggers de las tablas base
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.fn_proteger_total_pedido()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    IF OLD.total IS DISTINCT FROM NEW.total
       AND pg_trigger_depth() = 0 THEN
        RAISE EXCEPTION 'El campo total del pedido es calculado automáticamente. No puede modificarse directamente.';
    END IF;
    RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.fn_recalcular_total_pedido_delete()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    UPDATE pedido p
    SET total = GREATEST(
        (SELECT COALESCE(SUM(d.subtotal), 0)
         FROM detalle_pedido d
         WHERE d.id_pedido = p.id_pedido) - p.descuento,
        0)
    WHERE p.id_pedido IN (SELECT DISTINCT id_pedido FROM affected_rows);
    RETURN NULL;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.fn_recalcular_total_pedido_stmt()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    UPDATE pedido p
    SET total = GREATEST(
        (SELECT COALESCE(SUM(d.subtotal), 0)
         FROM detalle_pedido d
         WHERE d.id_pedido = p.id_pedido) - p.descuento,
        0)
    WHERE p.id_pedido IN (SELECT DISTINCT id_pedido FROM affected_rows);
    RETURN NULL;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.fn_recalcular_total_por_descuento()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    NEW.total := GREATEST(
        (SELECT COALESCE(SUM(d.subtotal), 0)
         FROM detalle_pedido d
         WHERE d.id_pedido = NEW.id_pedido) - NEW.descuento,
        0);
    RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.fn_set_updated_at()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.fn_trg_historial_inventario()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_usuario_id INTEGER;
BEGIN
    BEGIN
        v_usuario_id := current_setting('app.current_user_id', true)::INTEGER;
    EXCEPTION WHEN others THEN
        v_usuario_id := NULL;
    END;

    INSERT INTO historial_inventario
        (id_inventario, id_usuario, stock_anterior, stock_nuevo, motivo)
    VALUES
        (NEW.id_inventario, v_usuario_id, OLD.stock_actual, NEW.stock_actual, 'actualizacion_stock');

    RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.fn_validar_cambio_origen_producto()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$
;

CREATE OR REPLACE FUNCTION public.fn_validar_total_comprobante()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE v_total_pedido NUMERIC(10,2);
BEGIN
    SELECT total INTO v_total_pedido FROM pedido WHERE id_pedido = NEW.id_pedido;
    IF NEW.total <> v_total_pedido THEN
        RAISE EXCEPTION 'El total del comprobante (%) no coincide con el total del pedido (%).',
            NEW.total, v_total_pedido;
    END IF;
    RETURN NEW;
END;
$function$
;


-- ---------------------------------------------------------------------
-- 2) Tablas base (pg_dump --schema-only) + indices, constraints, triggers
-- ---------------------------------------------------------------------
--
-- PostgreSQL database dump
--

\restrict AEbIu4Ktcf3EX1yifKhQwmKtxaivrC0gbXi42GtOASnm1xqcwbtFsFxXQo7AdM7

-- Dumped from database version 18.3
-- Dumped by pg_dump version 18.3

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: bodega; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bodega (
    id_bodega integer NOT NULL,
    id_ciudad integer NOT NULL,
    nombre character varying(100) NOT NULL,
    direccion character varying(200),
    estado character varying(20) DEFAULT 'activo'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_bodega_estado CHECK (((estado)::text = ANY (ARRAY[('activo'::character varying)::text, ('inactivo'::character varying)::text])))
);


--
-- Name: bodega_id_bodega_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.bodega ALTER COLUMN id_bodega ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.bodega_id_bodega_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: categoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categoria (
    id_categoria integer NOT NULL,
    nombre character varying(100) NOT NULL,
    descripcion character varying(255),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: categoria_id_categoria_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.categoria ALTER COLUMN id_categoria ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.categoria_id_categoria_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: ciudad; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ciudad (
    id_ciudad integer NOT NULL,
    nombre character varying(100) NOT NULL,
    estado character varying(20) DEFAULT 'activo'::character varying NOT NULL,
    CONSTRAINT chk_ciudad_estado CHECK (((estado)::text = ANY (ARRAY[('activo'::character varying)::text, ('inactivo'::character varying)::text])))
);


--
-- Name: ciudad_id_ciudad_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.ciudad ALTER COLUMN id_ciudad ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.ciudad_id_ciudad_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: cliente; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cliente (
    id_cliente integer NOT NULL,
    id_ciudad integer NOT NULL,
    nombre character varying(100) NOT NULL,
    apellido character varying(100) NOT NULL,
    correo character varying(150),
    telefono character varying(20),
    direccion character varying(200),
    estado character varying(20) DEFAULT 'activo'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    CONSTRAINT chk_cliente_correo CHECK (((correo)::text ~* '^[^@]+@[^@]+\.[^@]+$'::text)),
    CONSTRAINT chk_cliente_estado CHECK (((estado)::text = ANY (ARRAY[('activo'::character varying)::text, ('inactivo'::character varying)::text])))
);


--
-- Name: cliente_id_cliente_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.cliente ALTER COLUMN id_cliente ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.cliente_id_cliente_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: comprobante_interno; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.comprobante_interno (
    id_comprobante integer NOT NULL,
    id_pedido integer NOT NULL,
    id_usuario integer NOT NULL,
    numero_comprobante character varying(50) NOT NULL,
    fecha_emision timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    total numeric(10,2) NOT NULL,
    estado character varying(20) DEFAULT 'emitido'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_comprobante_estado CHECK (((estado)::text = ANY (ARRAY[('emitido'::character varying)::text, ('anulado'::character varying)::text]))),
    CONSTRAINT chk_comprobante_total CHECK ((total >= (0)::numeric))
);


--
-- Name: comprobante_interno_id_comprobante_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.comprobante_interno ALTER COLUMN id_comprobante ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.comprobante_interno_id_comprobante_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: detalle_pedido; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.detalle_pedido (
    id_detalle integer NOT NULL,
    id_pedido integer NOT NULL,
    id_producto integer NOT NULL,
    cantidad integer NOT NULL,
    precio_unitario numeric(10,2) NOT NULL,
    subtotal numeric(10,2) GENERATED ALWAYS AS (((cantidad)::numeric * precio_unitario)) STORED,
    picking_completado boolean DEFAULT false NOT NULL,
    cantidad_recogida integer DEFAULT 0 NOT NULL,
    CONSTRAINT chk_detalle_cantidad CHECK ((cantidad > 0)),
    CONSTRAINT chk_detalle_cantidad_recogida CHECK ((cantidad_recogida >= 0)),
    CONSTRAINT chk_detalle_precio CHECK ((precio_unitario > (0)::numeric))
);


--
-- Name: detalle_pedido_id_detalle_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.detalle_pedido ALTER COLUMN id_detalle ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.detalle_pedido_id_detalle_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: historial_inventario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.historial_inventario (
    id_historial integer NOT NULL,
    id_inventario integer NOT NULL,
    id_usuario integer,
    stock_anterior integer NOT NULL,
    stock_nuevo integer NOT NULL,
    motivo character varying(50) NOT NULL,
    fecha timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_historial_motivo CHECK (((motivo)::text = ANY (ARRAY[('actualizacion_stock'::character varying)::text, ('ajuste_manual'::character varying)::text, ('correccion'::character varying)::text, ('importacion'::character varying)::text, ('traslado'::character varying)::text])))
);


--
-- Name: historial_inventario_id_historial_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.historial_inventario_id_historial_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: historial_inventario_id_historial_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.historial_inventario_id_historial_seq OWNED BY public.historial_inventario.id_historial;


--
-- Name: historial_inventario_id_historial_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.historial_inventario ALTER COLUMN id_historial ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.historial_inventario_id_historial_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: inventario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventario (
    id_inventario integer NOT NULL,
    id_producto integer NOT NULL,
    id_bodega integer NOT NULL,
    stock_actual integer DEFAULT 0 NOT NULL,
    stock_minimo integer DEFAULT 0 NOT NULL,
    fecha_actualizacion timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_inventario_stock_actual CHECK ((stock_actual >= 0)),
    CONSTRAINT chk_inventario_stock_minimo CHECK ((stock_minimo >= 0))
);


--
-- Name: inventario_id_inventario_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.inventario ALTER COLUMN id_inventario ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.inventario_id_inventario_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: log_accion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.log_accion (
    id_log integer NOT NULL,
    id_usuario integer,
    modulo character varying(50) NOT NULL,
    accion character varying(50) NOT NULL,
    descripcion text,
    ip_address character varying(45),
    fecha timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: log_accion_id_log_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.log_accion ALTER COLUMN id_log ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.log_accion_id_log_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: movimiento_inventario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.movimiento_inventario (
    id_movimiento integer NOT NULL,
    id_inventario integer NOT NULL,
    id_usuario integer NOT NULL,
    id_proveedor integer,
    id_pedido integer,
    id_comprobante integer,
    tipo_movimiento character varying(20) NOT NULL,
    cantidad integer NOT NULL,
    fecha timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    observacion text,
    id_inventario_destino integer,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_movimiento_cantidad CHECK ((cantidad > 0)),
    CONSTRAINT chk_movimiento_tipo CHECK (((tipo_movimiento)::text = ANY (ARRAY[('entrada'::character varying)::text, ('salida'::character varying)::text, ('ajuste'::character varying)::text, ('traslado'::character varying)::text]))),
    CONSTRAINT chk_traslado_origen_distinto_destino CHECK ((((tipo_movimiento)::text <> 'traslado'::text) OR (id_inventario <> id_inventario_destino))),
    CONSTRAINT chk_traslado_requiere_destino CHECK ((((tipo_movimiento)::text <> 'traslado'::text) OR (id_inventario_destino IS NOT NULL)))
);


--
-- Name: movimiento_inventario_id_movimiento_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.movimiento_inventario ALTER COLUMN id_movimiento ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.movimiento_inventario_id_movimiento_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: pedido; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pedido (
    id_pedido integer NOT NULL,
    id_cliente integer NOT NULL,
    id_usuario integer NOT NULL,
    fecha_pedido timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    total numeric(10,2) DEFAULT 0 NOT NULL,
    descuento numeric(10,2) DEFAULT 0 NOT NULL,
    estado character varying(20) DEFAULT 'pendiente'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    es_pedido_especial boolean DEFAULT false NOT NULL,
    tipo_especial character varying(50),
    nota_especial text,
    fecha_limite_entrega timestamp without time zone,
    numero_hu character varying(50),
    transportista character varying(100),
    region_destino character varying(100),
    fecha_empaque timestamp without time zone,
    CONSTRAINT chk_pedido_descuento CHECK ((descuento >= (0)::numeric)),
    CONSTRAINT chk_pedido_estado CHECK (((estado)::text = ANY (ARRAY[('pendiente'::character varying)::text, ('procesado'::character varying)::text, ('enviado'::character varying)::text, ('entregado'::character varying)::text, ('anulado'::character varying)::text]))),
    CONSTRAINT chk_pedido_tipo_especial CHECK (((tipo_especial)::text = ANY (ARRAY[('personalizado'::character varying)::text, ('regalo'::character varying)::text, ('corporativo'::character varying)::text]))),
    CONSTRAINT chk_pedido_total CHECK ((total >= (0)::numeric))
);


--
-- Name: pedido_id_pedido_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.pedido ALTER COLUMN id_pedido ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.pedido_id_pedido_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: permiso; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permiso (
    id_permiso integer NOT NULL,
    modulo character varying(50) NOT NULL,
    accion character varying(50) NOT NULL,
    descripcion character varying(255)
);


--
-- Name: permiso_id_permiso_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.permiso ALTER COLUMN id_permiso ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.permiso_id_permiso_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: producto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.producto (
    id_producto integer NOT NULL,
    id_categoria integer NOT NULL,
    nombre character varying(150) NOT NULL,
    descripcion text,
    precio numeric(10,2) NOT NULL,
    estado character varying(20) DEFAULT 'activo'::character varying NOT NULL,
    id_unidad_medida integer NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    origen character varying(20) DEFAULT 'comprado'::character varying NOT NULL,
    CONSTRAINT chk_producto_estado CHECK (((estado)::text = ANY (ARRAY[('activo'::character varying)::text, ('inactivo'::character varying)::text]))),
    CONSTRAINT chk_producto_origen CHECK (((origen)::text = ANY ((ARRAY['comprado'::character varying, 'fabricado'::character varying])::text[]))),
    CONSTRAINT chk_producto_precio CHECK ((precio > (0)::numeric))
);


--
-- Name: producto_id_producto_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.producto ALTER COLUMN id_producto ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.producto_id_producto_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: producto_proveedor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.producto_proveedor (
    id_producto_proveedor integer NOT NULL,
    id_producto integer NOT NULL,
    id_proveedor integer NOT NULL,
    precio_compra numeric(10,2),
    es_proveedor_principal boolean DEFAULT false NOT NULL,
    estado character varying(20) DEFAULT 'activo'::character varying NOT NULL,
    fecha_registro timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_pp_precio_compra CHECK (((precio_compra IS NULL) OR (precio_compra > (0)::numeric))),
    CONSTRAINT chk_producto_proveedor_estado CHECK (((estado)::text = ANY (ARRAY[('activo'::character varying)::text, ('inactivo'::character varying)::text])))
);


--
-- Name: producto_proveedor_id_producto_proveedor_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.producto_proveedor_id_producto_proveedor_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: producto_proveedor_id_producto_proveedor_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.producto_proveedor_id_producto_proveedor_seq OWNED BY public.producto_proveedor.id_producto_proveedor;


--
-- Name: producto_proveedor_id_producto_proveedor_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.producto_proveedor ALTER COLUMN id_producto_proveedor ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.producto_proveedor_id_producto_proveedor_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: proveedor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.proveedor (
    id_proveedor integer NOT NULL,
    nombre character varying(150) NOT NULL,
    contacto character varying(100),
    correo character varying(150),
    telefono character varying(20),
    direccion character varying(200),
    estado character varying(20) DEFAULT 'activo'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    CONSTRAINT chk_proveedor_correo CHECK (((correo)::text ~* '^[^@]+@[^@]+\.[^@]+$'::text)),
    CONSTRAINT chk_proveedor_estado CHECK (((estado)::text = ANY (ARRAY[('activo'::character varying)::text, ('inactivo'::character varying)::text])))
);


--
-- Name: proveedor_id_proveedor_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.proveedor ALTER COLUMN id_proveedor ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.proveedor_id_proveedor_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: rol; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rol (
    id_rol integer NOT NULL,
    nombre character varying(50) NOT NULL,
    descripcion character varying(255),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: rol_id_rol_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.rol ALTER COLUMN id_rol ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.rol_id_rol_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: rol_permiso; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rol_permiso (
    id_rol_permiso integer NOT NULL,
    id_rol integer NOT NULL,
    id_permiso integer NOT NULL
);


--
-- Name: rol_permiso_id_rol_permiso_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.rol_permiso ALTER COLUMN id_rol_permiso ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.rol_permiso_id_rol_permiso_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: unidad_medida; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.unidad_medida (
    id_unidad_medida integer NOT NULL,
    nombre character varying(50) NOT NULL,
    abreviatura character varying(10) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: unidad_medida_id_unidad_medida_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.unidad_medida ALTER COLUMN id_unidad_medida ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.unidad_medida_id_unidad_medida_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: usuario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario (
    id_usuario integer NOT NULL,
    nombre character varying(100) NOT NULL,
    apellido character varying(100) NOT NULL,
    correo character varying(150) NOT NULL,
    password character varying(255) NOT NULL,
    estado character varying(20) DEFAULT 'activo'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    CONSTRAINT chk_usuario_correo CHECK (((correo)::text ~* '^[^@]+@[^@]+\.[^@]+$'::text)),
    CONSTRAINT chk_usuario_estado CHECK (((estado)::text = ANY (ARRAY[('activo'::character varying)::text, ('inactivo'::character varying)::text]))),
    CONSTRAINT chk_usuario_password_longitud CHECK ((length((password)::text) >= 60))
);


--
-- Name: usuario_id_usuario_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.usuario ALTER COLUMN id_usuario ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.usuario_id_usuario_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: usuario_rol; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario_rol (
    id_usuario_rol integer NOT NULL,
    id_usuario integer NOT NULL,
    id_rol integer NOT NULL
);


--
-- Name: usuario_rol_id_usuario_rol_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.usuario_rol ALTER COLUMN id_usuario_rol ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.usuario_rol_id_usuario_rol_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: bodega bodega_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bodega
    ADD CONSTRAINT bodega_pkey PRIMARY KEY (id_bodega);


--
-- Name: categoria categoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categoria
    ADD CONSTRAINT categoria_pkey PRIMARY KEY (id_categoria);


--
-- Name: ciudad ciudad_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ciudad
    ADD CONSTRAINT ciudad_pkey PRIMARY KEY (id_ciudad);


--
-- Name: cliente cliente_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cliente
    ADD CONSTRAINT cliente_pkey PRIMARY KEY (id_cliente);


--
-- Name: comprobante_interno comprobante_interno_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comprobante_interno
    ADD CONSTRAINT comprobante_interno_pkey PRIMARY KEY (id_comprobante);


--
-- Name: detalle_pedido detalle_pedido_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.detalle_pedido
    ADD CONSTRAINT detalle_pedido_pkey PRIMARY KEY (id_detalle);


--
-- Name: historial_inventario historial_inventario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historial_inventario
    ADD CONSTRAINT historial_inventario_pkey PRIMARY KEY (id_historial);


--
-- Name: inventario inventario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventario
    ADD CONSTRAINT inventario_pkey PRIMARY KEY (id_inventario);


--
-- Name: log_accion log_accion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.log_accion
    ADD CONSTRAINT log_accion_pkey PRIMARY KEY (id_log);


--
-- Name: movimiento_inventario movimiento_inventario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimiento_inventario
    ADD CONSTRAINT movimiento_inventario_pkey PRIMARY KEY (id_movimiento);


--
-- Name: pedido pedido_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pedido
    ADD CONSTRAINT pedido_pkey PRIMARY KEY (id_pedido);


--
-- Name: permiso permiso_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permiso
    ADD CONSTRAINT permiso_pkey PRIMARY KEY (id_permiso);


--
-- Name: producto producto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.producto
    ADD CONSTRAINT producto_pkey PRIMARY KEY (id_producto);


--
-- Name: producto_proveedor producto_proveedor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.producto_proveedor
    ADD CONSTRAINT producto_proveedor_pkey PRIMARY KEY (id_producto_proveedor);


--
-- Name: proveedor proveedor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proveedor
    ADD CONSTRAINT proveedor_pkey PRIMARY KEY (id_proveedor);


--
-- Name: rol_permiso rol_permiso_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rol_permiso
    ADD CONSTRAINT rol_permiso_pkey PRIMARY KEY (id_rol_permiso);


--
-- Name: rol rol_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rol
    ADD CONSTRAINT rol_pkey PRIMARY KEY (id_rol);


--
-- Name: unidad_medida unidad_medida_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidad_medida
    ADD CONSTRAINT unidad_medida_pkey PRIMARY KEY (id_unidad_medida);


--
-- Name: categoria uq_categoria_nombre; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categoria
    ADD CONSTRAINT uq_categoria_nombre UNIQUE (nombre);


--
-- Name: ciudad uq_ciudad_nombre; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ciudad
    ADD CONSTRAINT uq_ciudad_nombre UNIQUE (nombre);


--
-- Name: cliente uq_cliente_correo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cliente
    ADD CONSTRAINT uq_cliente_correo UNIQUE (correo);


--
-- Name: comprobante_interno uq_comprobante_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comprobante_interno
    ADD CONSTRAINT uq_comprobante_numero UNIQUE (numero_comprobante);


--
-- Name: inventario uq_inventario_producto_bodega; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventario
    ADD CONSTRAINT uq_inventario_producto_bodega UNIQUE (id_producto, id_bodega);


--
-- Name: permiso uq_permiso_modulo_accion; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permiso
    ADD CONSTRAINT uq_permiso_modulo_accion UNIQUE (modulo, accion);


--
-- Name: producto_proveedor uq_pp_producto_proveedor; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.producto_proveedor
    ADD CONSTRAINT uq_pp_producto_proveedor UNIQUE (id_producto, id_proveedor);


--
-- Name: producto uq_producto_nombre; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.producto
    ADD CONSTRAINT uq_producto_nombre UNIQUE (nombre);


--
-- Name: rol uq_rol_nombre; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rol
    ADD CONSTRAINT uq_rol_nombre UNIQUE (nombre);


--
-- Name: rol_permiso uq_rol_permiso; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rol_permiso
    ADD CONSTRAINT uq_rol_permiso UNIQUE (id_rol, id_permiso);


--
-- Name: unidad_medida uq_unidad_medida_abreviatura; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidad_medida
    ADD CONSTRAINT uq_unidad_medida_abreviatura UNIQUE (abreviatura);


--
-- Name: unidad_medida uq_unidad_medida_nombre; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidad_medida
    ADD CONSTRAINT uq_unidad_medida_nombre UNIQUE (nombre);


--
-- Name: usuario uq_usuario_correo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT uq_usuario_correo UNIQUE (correo);


--
-- Name: usuario_rol uq_usuario_rol; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_rol
    ADD CONSTRAINT uq_usuario_rol UNIQUE (id_usuario, id_rol);


--
-- Name: usuario usuario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_pkey PRIMARY KEY (id_usuario);


--
-- Name: usuario_rol usuario_rol_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_rol
    ADD CONSTRAINT usuario_rol_pkey PRIMARY KEY (id_usuario_rol);


--
-- Name: idx_bodega_ciudad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bodega_ciudad ON public.bodega USING btree (id_ciudad);


--
-- Name: idx_cliente_ciudad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cliente_ciudad ON public.cliente USING btree (id_ciudad);


--
-- Name: idx_comprobante_pedido; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comprobante_pedido ON public.comprobante_interno USING btree (id_pedido);


--
-- Name: idx_comprobante_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comprobante_usuario ON public.comprobante_interno USING btree (id_usuario);


--
-- Name: idx_detalle_pedido; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_detalle_pedido ON public.detalle_pedido USING btree (id_pedido);


--
-- Name: idx_detalle_producto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_detalle_producto ON public.detalle_pedido USING btree (id_producto);


--
-- Name: idx_detalle_subtotal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_detalle_subtotal ON public.detalle_pedido USING btree (subtotal);


--
-- Name: idx_historial_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_historial_fecha ON public.historial_inventario USING btree (fecha);


--
-- Name: idx_historial_inventario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_historial_inventario ON public.historial_inventario USING btree (id_inventario);


--
-- Name: idx_inventario_bodega; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inventario_bodega ON public.inventario USING btree (id_bodega);


--
-- Name: idx_inventario_producto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inventario_producto ON public.inventario USING btree (id_producto);


--
-- Name: idx_log_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_log_fecha ON public.log_accion USING btree (fecha);


--
-- Name: idx_log_modulo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_log_modulo ON public.log_accion USING btree (modulo);


--
-- Name: idx_log_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_log_usuario ON public.log_accion USING btree (id_usuario);


--
-- Name: idx_movimiento_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_movimiento_fecha ON public.movimiento_inventario USING btree (fecha);


--
-- Name: idx_movimiento_inventario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_movimiento_inventario ON public.movimiento_inventario USING btree (id_inventario);


--
-- Name: idx_movimiento_inventario_destino; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_movimiento_inventario_destino ON public.movimiento_inventario USING btree (id_inventario_destino);


--
-- Name: idx_movimiento_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_movimiento_usuario ON public.movimiento_inventario USING btree (id_usuario);


--
-- Name: idx_pedido_cliente; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pedido_cliente ON public.pedido USING btree (id_cliente);


--
-- Name: idx_pedido_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pedido_fecha ON public.pedido USING btree (fecha_pedido);


--
-- Name: idx_pedido_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pedido_usuario ON public.pedido USING btree (id_usuario);


--
-- Name: idx_pp_producto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pp_producto ON public.producto_proveedor USING btree (id_producto);


--
-- Name: idx_pp_proveedor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pp_proveedor ON public.producto_proveedor USING btree (id_proveedor);


--
-- Name: idx_producto_categoria; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_producto_categoria ON public.producto USING btree (id_categoria);


--
-- Name: idx_producto_unidad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_producto_unidad ON public.producto USING btree (id_unidad_medida);


--
-- Name: idx_usuario_rol_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_usuario_rol_usuario ON public.usuario_rol USING btree (id_usuario);


--
-- Name: cliente trg_cliente_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_cliente_updated_at BEFORE UPDATE ON public.cliente FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();


--
-- Name: inventario trg_historial_inventario; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_historial_inventario AFTER UPDATE OF stock_actual ON public.inventario FOR EACH ROW WHEN ((old.stock_actual IS DISTINCT FROM new.stock_actual)) EXECUTE FUNCTION public.fn_trg_historial_inventario();


--
-- Name: pedido trg_pedido_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_pedido_updated_at BEFORE UPDATE ON public.pedido FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();


--
-- Name: producto trg_producto_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_producto_updated_at BEFORE UPDATE ON public.producto FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();


--
-- Name: pedido trg_proteger_total_pedido; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_proteger_total_pedido BEFORE UPDATE OF total ON public.pedido FOR EACH ROW EXECUTE FUNCTION public.fn_proteger_total_pedido();


--
-- Name: proveedor trg_proveedor_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_proveedor_updated_at BEFORE UPDATE ON public.proveedor FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();


--
-- Name: detalle_pedido trg_recalcular_total_pedido_delete; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_recalcular_total_pedido_delete AFTER DELETE ON public.detalle_pedido REFERENCING OLD TABLE AS affected_rows FOR EACH STATEMENT EXECUTE FUNCTION public.fn_recalcular_total_pedido_delete();


--
-- Name: detalle_pedido trg_recalcular_total_pedido_insert; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_recalcular_total_pedido_insert AFTER INSERT ON public.detalle_pedido REFERENCING NEW TABLE AS affected_rows FOR EACH STATEMENT EXECUTE FUNCTION public.fn_recalcular_total_pedido_stmt();


--
-- Name: detalle_pedido trg_recalcular_total_pedido_update; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_recalcular_total_pedido_update AFTER UPDATE ON public.detalle_pedido REFERENCING NEW TABLE AS affected_rows FOR EACH STATEMENT EXECUTE FUNCTION public.fn_recalcular_total_pedido_stmt();


--
-- Name: pedido trg_recalcular_total_por_descuento; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_recalcular_total_por_descuento BEFORE UPDATE OF descuento ON public.pedido FOR EACH ROW EXECUTE FUNCTION public.fn_recalcular_total_por_descuento();


--
-- Name: usuario trg_usuario_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_usuario_updated_at BEFORE UPDATE ON public.usuario FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();


--
-- Name: producto trg_validar_cambio_origen_producto; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validar_cambio_origen_producto BEFORE UPDATE OF origen ON public.producto FOR EACH ROW EXECUTE FUNCTION public.fn_validar_cambio_origen_producto();


--
-- Name: comprobante_interno trg_validar_total_comprobante; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validar_total_comprobante BEFORE INSERT ON public.comprobante_interno FOR EACH ROW EXECUTE FUNCTION public.fn_validar_total_comprobante();


--
-- Name: bodega fk_bodega_ciudad; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bodega
    ADD CONSTRAINT fk_bodega_ciudad FOREIGN KEY (id_ciudad) REFERENCES public.ciudad(id_ciudad) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: cliente fk_cliente_ciudad; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cliente
    ADD CONSTRAINT fk_cliente_ciudad FOREIGN KEY (id_ciudad) REFERENCES public.ciudad(id_ciudad) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: comprobante_interno fk_comprobante_pedido; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comprobante_interno
    ADD CONSTRAINT fk_comprobante_pedido FOREIGN KEY (id_pedido) REFERENCES public.pedido(id_pedido) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: comprobante_interno fk_comprobante_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comprobante_interno
    ADD CONSTRAINT fk_comprobante_usuario FOREIGN KEY (id_usuario) REFERENCES public.usuario(id_usuario) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: detalle_pedido fk_detalle_pedido; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.detalle_pedido
    ADD CONSTRAINT fk_detalle_pedido FOREIGN KEY (id_pedido) REFERENCES public.pedido(id_pedido) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: detalle_pedido fk_detalle_producto; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.detalle_pedido
    ADD CONSTRAINT fk_detalle_producto FOREIGN KEY (id_producto) REFERENCES public.producto(id_producto) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: historial_inventario fk_historial_inventario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historial_inventario
    ADD CONSTRAINT fk_historial_inventario FOREIGN KEY (id_inventario) REFERENCES public.inventario(id_inventario) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: historial_inventario fk_historial_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historial_inventario
    ADD CONSTRAINT fk_historial_usuario FOREIGN KEY (id_usuario) REFERENCES public.usuario(id_usuario) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: inventario fk_inventario_bodega; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventario
    ADD CONSTRAINT fk_inventario_bodega FOREIGN KEY (id_bodega) REFERENCES public.bodega(id_bodega) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: inventario fk_inventario_producto; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventario
    ADD CONSTRAINT fk_inventario_producto FOREIGN KEY (id_producto) REFERENCES public.producto(id_producto) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: movimiento_inventario fk_movimiento_comprobante; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimiento_inventario
    ADD CONSTRAINT fk_movimiento_comprobante FOREIGN KEY (id_comprobante) REFERENCES public.comprobante_interno(id_comprobante) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: movimiento_inventario fk_movimiento_inventario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimiento_inventario
    ADD CONSTRAINT fk_movimiento_inventario FOREIGN KEY (id_inventario) REFERENCES public.inventario(id_inventario) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: movimiento_inventario fk_movimiento_inventario_destino; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimiento_inventario
    ADD CONSTRAINT fk_movimiento_inventario_destino FOREIGN KEY (id_inventario_destino) REFERENCES public.inventario(id_inventario) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: movimiento_inventario fk_movimiento_pedido; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimiento_inventario
    ADD CONSTRAINT fk_movimiento_pedido FOREIGN KEY (id_pedido) REFERENCES public.pedido(id_pedido) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: movimiento_inventario fk_movimiento_proveedor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimiento_inventario
    ADD CONSTRAINT fk_movimiento_proveedor FOREIGN KEY (id_proveedor) REFERENCES public.proveedor(id_proveedor) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: movimiento_inventario fk_movimiento_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimiento_inventario
    ADD CONSTRAINT fk_movimiento_usuario FOREIGN KEY (id_usuario) REFERENCES public.usuario(id_usuario) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: pedido fk_pedido_cliente; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pedido
    ADD CONSTRAINT fk_pedido_cliente FOREIGN KEY (id_cliente) REFERENCES public.cliente(id_cliente) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: pedido fk_pedido_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pedido
    ADD CONSTRAINT fk_pedido_usuario FOREIGN KEY (id_usuario) REFERENCES public.usuario(id_usuario) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: producto_proveedor fk_pp_producto; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.producto_proveedor
    ADD CONSTRAINT fk_pp_producto FOREIGN KEY (id_producto) REFERENCES public.producto(id_producto) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: producto_proveedor fk_pp_proveedor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.producto_proveedor
    ADD CONSTRAINT fk_pp_proveedor FOREIGN KEY (id_proveedor) REFERENCES public.proveedor(id_proveedor) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: producto fk_producto_categoria; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.producto
    ADD CONSTRAINT fk_producto_categoria FOREIGN KEY (id_categoria) REFERENCES public.categoria(id_categoria) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: producto fk_producto_unidad_medida; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.producto
    ADD CONSTRAINT fk_producto_unidad_medida FOREIGN KEY (id_unidad_medida) REFERENCES public.unidad_medida(id_unidad_medida) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: rol_permiso fk_rp_permiso; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rol_permiso
    ADD CONSTRAINT fk_rp_permiso FOREIGN KEY (id_permiso) REFERENCES public.permiso(id_permiso) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: rol_permiso fk_rp_rol; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rol_permiso
    ADD CONSTRAINT fk_rp_rol FOREIGN KEY (id_rol) REFERENCES public.rol(id_rol) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: usuario_rol fk_usuario_rol_rol; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_rol
    ADD CONSTRAINT fk_usuario_rol_rol FOREIGN KEY (id_rol) REFERENCES public.rol(id_rol) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: usuario_rol fk_usuario_rol_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_rol
    ADD CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY (id_usuario) REFERENCES public.usuario(id_usuario) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: log_accion log_accion_id_usuario_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.log_accion
    ADD CONSTRAINT log_accion_id_usuario_fkey FOREIGN KEY (id_usuario) REFERENCES public.usuario(id_usuario) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- PostgreSQL database dump complete
--

\unrestrict AEbIu4Ktcf3EX1yifKhQwmKtxaivrC0gbXi42GtOASnm1xqcwbtFsFxXQo7AdM7

