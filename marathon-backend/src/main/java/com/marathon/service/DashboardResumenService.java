package com.marathon.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.marathon.config.RoleRoutingDataSource;
import com.marathon.dto.dashboard.ComparacionDTO;
import com.marathon.dto.dashboard.DashboardResumenDTO;
import com.marathon.dto.dashboard.IndicadorDTO;
import com.marathon.dto.dashboard.SerieDiaDTO;
import com.marathon.dto.dashboard.TopProductoPeriodoDTO;
import com.marathon.model.Usuario;
import com.marathon.repository.DashboardConsultas;
import com.marathon.repository.DashboardConsultas.Par;

/**
 * Arma el tablero del rol que pide la peticion (D1).
 *
 * <p><b>Que problema resuelve.</b> El dashboard anterior era el mismo para
 * todos y estaba hecho de contadores historicos: «162.367 entregados» desde
 * 2024, «16.099 pendientes» de los que dos tercios tenian mas de seis meses.
 * Ninguna de esas cifras cambia lo que alguien hace hoy. Aqui cada rol recibe
 * entre cuatro y siete indicadores <b>sobre los que puede actuar</b>, cada uno
 * con su periodo, su base de calculo y la pantalla donde se actua.
 *
 * <p><b>Por rol, no por pantalla.</b> El reparto no es solo de producto: las
 * conexiones van por {@code RoleRoutingDataSource} y los permisos de lectura
 * son por tabla (F34/F37). El operador de bodega no puede leer
 * {@code cuenta_por_pagar} ni {@code orden_produccion} aunque quisiera, asi que
 * su tablero no las consulta. Lo que se le muestra y lo que su usuario de base
 * de datos puede leer son la misma lista.
 *
 * <p><b>Un indicador que falla no tumba el tablero.</b> Cada cifra se calcula
 * dentro de {@link #intentar}: si la consulta revienta —permiso, tiempo de
 * espera, lo que sea— esa tarjeta sale en estado {@code error} con el motivo, y
 * las demas se pintan igual. Lo que <i>no</i> se hace nunca es devolver cero:
 * un cero se lee como «no hay nada», y «no se pudo calcular» no es «no hay
 * nada».
 *
 * <p><b>El ultimo argumento de cada indicador es una RUTA DEL FRONTEND</b>, la
 * del enlace «Ver detalle» de su tarjeta. No es la ruta de la API, y confundir
 * las dos es facil porque se parecen: cinco de las catorce estaban puestas con
 * el nombre del endpoint y llevaban a una pantalla que no existe (F53, D-43).
 * El resultado era un «Ver detalle» que dejaba al usuario en el inicio:
 *
 * <pre>
 *   API (aqui)                pantalla (app.routes.ts)
 *   /api/ordenes-compra       ->  /compras
 *   /api/ordenes-produccion   ->  /produccion
 *   /api/analisis-costos      ->  /produccion/costos
 *   /api/recepciones          ->  /compras       (no hay pantalla de recepciones
 *                                                 suelta: se entra desde la orden)
 *   —                         ->  /pedidos/especiales, no /pedidos-especiales
 * </pre>
 *
 * <p>Antes de anadir un indicador nuevo, comprueba su ruta contra
 * {@code marathon-frontend/src/app/app.routes.ts}. Nada en el compilador lo
 * comprueba por ti.
 */
@Service
public class DashboardResumenService {

    private static final Logger log = LoggerFactory.getLogger(DashboardResumenService.class);

    /** Dias que un pedido puede estar en {@code procesado} antes de considerarse atascado. */
    private static final int DIAS_ATASCO = 7;

    /** Ventana de aviso de vencimientos: lo que vence dentro de una semana. */
    private static final int DIAS_AVISO_VENCIMIENTO = 7;

    private static final int TOP_PRODUCTOS = 5;

    private static final String AHORA = "Ahora mismo";

    private final DashboardConsultas consultas;

    public DashboardResumenService(DashboardConsultas consultas) {
        this.consultas = consultas;
    }

    // ==================================================================
    //  Entrada
    // ==================================================================

    /**
     * @param periodoClave {@code 7d}, {@code 30d} o {@code 90d}; cualquier otra
     *                     cosa cae a 30 dias en lugar de fallar, porque un
     *                     parametro mal escrito en la barra de direcciones no
     *                     deberia dejar al usuario sin tablero.
     */
    public DashboardResumenDTO resumen(String periodoClave) {
        Ventana v = Ventana.de(periodoClave);
        String rol = rolDelUsuario();

        List<IndicadorDTO> indicadores;
        List<TopProductoPeriodoDTO> top = List.of();
        List<SerieDiaDTO> serie = List.of();

        switch (rol) {
            case "ADMINISTRADOR" -> {
                indicadores = tableroAdministrador(v);
                top = intentarTop(v);
                serie = intentarSerie(v);
            }
            case "SUPERVISOR E-COMMERCE" -> {
                indicadores = tableroSupervisor(v);
                top = intentarTop(v);
                serie = intentarSerie(v);
            }
            case "OPERADOR DE PEDIDOS" -> indicadores = tableroOperadorPedidos(v);
            case "OPERADOR DE BODEGA" -> indicadores = tableroOperadorBodega(v);
            case "ENCARGADO DE COMPRAS" -> indicadores = tableroCompras(v);
            case "ENCARGADO DE PRODUCCION" -> indicadores = tableroProduccion(v);
            default -> indicadores = List.of(IndicadorDTO.sinDato(
                    "sin_tablero",
                    "Sin tablero para este rol",
                    "El rol «" + rol + "» no tiene indicadores definidos. "
                            + "Es un rol nuevo o mal escrito en la tabla rol.",
                    null));
        }

        return new DashboardResumenDTO(
                rol, "Tablero de " + enBonito(rol),
                v.clave, v.etiqueta, v.desde, v.hastaExcl.minusDays(1),
                LocalDateTime.now(), indicadores, top, serie);
    }

    // ==================================================================
    //  Los seis tableros
    // ==================================================================

    /** El unico que ve el negocio entero: venta, almacen y dinero por pagar. */
    private List<IndicadorDTO> tableroAdministrador(Ventana v) {
        List<IndicadorDTO> is = new ArrayList<>();
        is.add(pedidosCreados(v));
        is.add(valorPedido(v));
        is.add(tasaAnulacion(v));
        is.add(pedidosAtascados());
        is.add(referenciasBajoMinimo());
        is.add(cuentasVencidas());
        is.add(ventasEntregadasSinDato());
        return is;
    }

    /** Analisis de venta: cuanto entra, a que precio medio, y cuanto se cae. */
    private List<IndicadorDTO> tableroSupervisor(Ventana v) {
        List<IndicadorDTO> is = new ArrayList<>();
        is.add(pedidosCreados(v));
        is.add(valorPedido(v));
        is.add(tasaAnulacion(v));
        is.add(diasHastaDespacho(v));
        is.add(ventasEntregadasSinDato());
        return is;
    }

    /** Su cola de trabajo, no el historico de la empresa. */
    private List<IndicadorDTO> tableroOperadorPedidos(Ventana v) {
        Integer idUsuario = idDelUsuario();
        List<IndicadorDTO> is = new ArrayList<>();

        is.add(intentar("mis_pedidos", "Pedidos que registré", () -> {
            if (idUsuario == null) {
                return IndicadorDTO.sinDato("mis_pedidos", "Pedidos que registré",
                        "No se pudo identificar al usuario de la sesión", "/pedidos");
            }
            BigDecimal actual = consultas.pedidosCreadosPor(idUsuario, v.desde, v.hastaExcl);
            BigDecimal previo = consultas.pedidosCreadosPor(idUsuario, v.previoDesde, v.desde);
            return IndicadorDTO.ok("mis_pedidos", "Pedidos que registré", "pedidos", actual,
                    v.etiqueta, "pedido.fecha_pedido de los pedidos con mi id de usuario",
                    "/pedidos")
                    .conComparacion(ComparacionDTO.de(actual, previo, v.etiquetaPrevio));
        }));

        is.add(intentar("cola_pendiente", "Mi cola: pedidos pendientes", () -> {
            BigDecimal actual = consultas.colaPendiente(v.desde, v.hastaExcl);
            return IndicadorDTO.ok("cola_pendiente", "Mi cola: pedidos pendientes", "pedidos",
                    actual, v.etiqueta,
                    "estado = pendiente, creados dentro del período (los anteriores están "
                            + "abandonados, no pendientes)",
                    "/pedidos");
        }));

        is.add(intentar("dev_inspeccion", "Devoluciones esperando inspección",
                () -> IndicadorDTO.ok("dev_inspeccion", "Devoluciones esperando inspección",
                        "solicitudes", consultas.devolucionesEsperandoInspeccion(), AHORA,
                        "solicitud_devolucion en estado solicitada o en_inspeccion",
                        "/devoluciones")));

        is.add(intentar("especiales_abiertos", "Pedidos especiales abiertos",
                () -> IndicadorDTO.ok("especiales_abiertos", "Pedidos especiales abiertos",
                        "pedidos", consultas.especialesAbiertos(), AHORA,
                        "es_pedido_especial y estado pendiente o procesado — el tablero anterior "
                                + "contaba «distinto de anulado» e incluía los ya entregados",
                        "/pedidos/especiales")));
        return is;
    }

    /** Lo que tiene delante: recoger, empacar y reponer. */
    private List<IndicadorDTO> tableroOperadorBodega(Ventana v) {
        List<IndicadorDTO> is = new ArrayList<>();

        is.add(intentar("esperando_picking", "Pedidos esperando picking",
                () -> IndicadorDTO.ok("esperando_picking", "Pedidos esperando picking", "pedidos",
                        consultas.esperandoPicking(), AHORA,
                        "pedidos en procesado con alguna línea sin recoger", "/picking")));

        is.add(intentar("lineas_por_recoger", "Líneas por recoger", () -> {
            Par p = consultas.lineasPorRecoger();
            return IndicadorDTO.sobre("lineas_por_recoger", "Líneas por recoger", "líneas",
                    p.valor(), p.denominador(), AHORA,
                    "detalle_pedido sin picking_completado, sobre el total de líneas de pedidos "
                            + "en procesado",
                    "/picking");
        }));

        is.add(intentar("listos_empacar", "Listos para empacar",
                () -> IndicadorDTO.ok("listos_empacar", "Listos para empacar", "pedidos",
                        consultas.listosParaEmpacar(), AHORA,
                        "pedidos en procesado con todas sus líneas recogidas", "/empaque")));

        is.add(referenciasBajoMinimo());

        is.add(intentar("movimientos", "Movimientos de inventario", () -> {
            BigDecimal actual = consultas.movimientos(v.desde, v.hastaExcl);
            BigDecimal previo = consultas.movimientos(v.previoDesde, v.desde);
            return IndicadorDTO.ok("movimientos", "Movimientos de inventario", "movimientos",
                    actual, v.etiqueta,
                    "movimiento_inventario.fecha — entradas, salidas, ajustes y traslados",
                    "/inventario")
                    .conComparacion(ComparacionDTO.de(actual, previo, v.etiquetaPrevio));
        }));
        return is;
    }

    /** Lo que hay que aprobar, lo que no llega y lo que hay que pagar. */
    private List<IndicadorDTO> tableroCompras(Ventana v) {
        List<IndicadorDTO> is = new ArrayList<>();

        is.add(intentar("oc_pendientes", "Órdenes pendientes de aprobación",
                () -> IndicadorDTO.ok("oc_pendientes", "Órdenes pendientes de aprobación",
                        "órdenes", consultas.ordenesPendientesAprobacion(), AHORA,
                        "orden_compra en estado pendiente_aprobacion", "/compras")));

        is.add(intentar("oc_sin_recibir", "Aprobadas sin recibir",
                () -> IndicadorDTO.ok("oc_sin_recibir", "Aprobadas sin recibir", "órdenes",
                        consultas.ordenesAprobadasSinRecibir(), AHORA,
                        "orden_compra en estado aprobada: ya comprometidas y aún sin mercancía",
                        "/compras")));

        is.add(cuentasVencidas());

        is.add(intentar("cxp_proximas", "Vence en los próximos 7 días", () -> {
            Par p = consultas.cuentasQueVencen(DIAS_AVISO_VENCIMIENTO);
            return IndicadorDTO.sobre("cxp_proximas", "Vence en los próximos 7 días", "$",
                    p.valor(), p.denominador(), AHORA,
                    "cuenta_por_pagar con saldo y vencimiento entre hoy y dentro de 7 días — "
                            + "el importe, sobre el número de cuentas",
                    "/cuentas-por-pagar");
        }));

        is.add(intentar("dev_proveedor", "Devoluciones a proveedor sin resolver",
                () -> IndicadorDTO.ok("dev_proveedor", "Devoluciones a proveedor sin resolver",
                        "devoluciones", consultas.devolucionesProveedorAbiertas(), AHORA,
                        "devolucion_proveedor en estado pendiente o enviada",
                        "/devoluciones-proveedor")));
        return is;
    }

    /** Lo que se esta fabricando, lo que no se puede fabricar y lo que cuesta. */
    private List<IndicadorDTO> tableroProduccion(Ventana v) {
        List<IndicadorDTO> is = new ArrayList<>();

        is.add(intentar("op_en_proceso", "Órdenes en proceso", () -> {
            Par p = consultas.ordenesEnProceso();
            return IndicadorDTO.sobre("op_en_proceso", "Órdenes en proceso", "órdenes",
                    p.valor(), p.denominador(), AHORA,
                    "orden_produccion en en_proceso, sobre el total de abiertas "
                            + "(planificadas + en proceso)",
                    "/produccion");
        }));

        is.add(intentar("op_sin_material", "Planificadas sin material suficiente",
                () -> IndicadorDTO.ok("op_sin_material", "Planificadas sin material suficiente",
                        "órdenes", consultas.ordenesSinMaterial(), AHORA,
                        "órdenes planificadas con alguna materia prima de su lista por debajo de "
                                + "lo que exige la cantidad planificada",
                        "/produccion")));

        is.add(intentar("mp_bajo_minimo", "Materia prima bajo mínimo", () -> {
            Par p = consultas.materiaPrimaBajoMinimo();
            return IndicadorDTO.sobre("mp_bajo_minimo", "Materia prima bajo mínimo", "referencias",
                    p.valor(), p.denominador(), AHORA,
                    "materia_prima con stock_actual <= stock_minimo, sobre las que tienen mínimo "
                            + "definido",
                    "/materia-prima");
        }));

        is.add(intentar("costo_medio_op", "Costo medio por orden", () -> {
            Par p = consultas.costoMedioOrden(v.desde, v.hastaExcl);
            return IndicadorDTO.sobre("costo_medio_op", "Costo medio por orden", "$",
                    p.valor(), p.denominador(), v.etiqueta,
                    "media de costo_total de las órdenes completadas en el período, sobre el "
                            + "número de órdenes — un promedio sin su n no se puede interpretar",
                    "/produccion/costos");
        }));

        is.add(intentar("merma_media", "Merma media", () -> {
            Par p = consultas.mermaMedia(v.desde, v.hastaExcl);
            return IndicadorDTO.sobre("merma_media", "Merma media", "%",
                    p.valor(), p.denominador(), v.etiqueta,
                    "media de (planificado − producido) / planificado en las órdenes completadas "
                            + "del período, sobre el número de órdenes",
                    "/produccion");
        }));
        return is;
    }

    // ==================================================================
    //  Indicadores compartidos por varios tableros
    // ==================================================================

    private IndicadorDTO pedidosCreados(Ventana v) {
        return intentar("pedidos_creados", "Pedidos creados", () -> {
            BigDecimal actual = consultas.pedidosCreados(v.desde, v.hastaExcl);
            BigDecimal previo = consultas.pedidosCreados(v.previoDesde, v.desde);
            return IndicadorDTO.ok("pedidos_creados", "Pedidos creados", "pedidos", actual,
                    v.etiqueta, "pedido.fecha_pedido, todos los estados", "/pedidos")
                    .conComparacion(ComparacionDTO.de(actual, previo, v.etiquetaPrevio));
        });
    }

    private IndicadorDTO valorPedido(Ventana v) {
        return intentar("valor_pedido", "Valor de lo pedido", () -> {
            Par p = consultas.importeYPedidos(v.desde, v.hastaExcl);
            Par prev = consultas.importeYPedidos(v.previoDesde, v.desde);
            return IndicadorDTO.sobre("valor_pedido", "Valor de lo pedido", "$",
                    p.valor(), p.denominador(), v.etiqueta,
                    "suma de pedido.total de los pedidos no anulados, sobre el número de pedidos: "
                            + "el cociente es el ticket medio",
                    "/pedidos")
                    .conComparacion(ComparacionDTO.de(p.valor(), prev.valor(), v.etiquetaPrevio));
        });
    }

    private IndicadorDTO tasaAnulacion(Ventana v) {
        return intentar("tasa_anulacion", "Tasa de anulación", () -> {
            Par p = consultas.anuladosSobreCreados(v.desde, v.hastaExcl);
            return IndicadorDTO.porcentaje("tasa_anulacion", "Tasa de anulación",
                    p.valor(), p.denominador(), v.etiqueta,
                    p.valor().toBigInteger() + " pedidos anulados sobre "
                            + p.denominador().toBigInteger() + " creados en el período",
                    "/pedidos");
        });
    }

    private IndicadorDTO pedidosAtascados() {
        return intentar("pedidos_atascados", "Pedidos atascados",
                () -> IndicadorDTO.ok("pedidos_atascados", "Pedidos atascados", "pedidos",
                        consultas.pedidosAtascados(DIAS_ATASCO), AHORA,
                        "pedidos en estado procesado con más de " + DIAS_ATASCO
                                + " días desde que se registraron",
                        "/picking"));
    }

    private IndicadorDTO referenciasBajoMinimo() {
        return intentar("ref_bajo_minimo", "Referencias bajo mínimo", () -> {
            Par p = consultas.referenciasBajoMinimo();
            return IndicadorDTO.sobre("ref_bajo_minimo", "Referencias bajo mínimo", "referencias",
                    p.valor(), p.denominador(), AHORA,
                    "inventario con stock_actual <= stock_minimo, sobre las filas que tienen "
                            + "mínimo definido",
                    "/inventario");
        });
    }

    private IndicadorDTO cuentasVencidas() {
        return intentar("cxp_vencidas", "Cuentas por pagar vencidas", () -> {
            Par p = consultas.cuentasVencidas();
            return IndicadorDTO.sobre("cxp_vencidas", "Cuentas por pagar vencidas", "$",
                    p.valor(), p.denominador(), AHORA,
                    "importe pendiente de las cuentas con vencimiento anterior a hoy, sobre el "
                            + "número de cuentas — se cuenta por fecha, no por la etiqueta estado",
                    "/cuentas-por-pagar");
        });
    }

    /**
     * La unica tarjeta que existe para decir que <b>no hay dato</b>.
     *
     * <p>El dashboard anterior mostraba «Ventas hoy» y «Ventas del mes» sumando
     * el total de los pedidos <i>creados</i> en la ventana que ademas estaban
     * <i>entregados</i>. Eso mezcla dos fechas distintas y tiende a cero por
     * construccion. La cifra correcta —lo entregado en un periodo— no se puede
     * calcular: la tabla {@code pedido} no guarda ninguna fecha de entrega.
     * Antes que rellenarlo con {@code updated_at} y dar por bueno un numero que
     * nadie podria auditar, la tarjeta lo dice.
     */
    private IndicadorDTO ventasEntregadasSinDato() {
        return IndicadorDTO.sinDato("ventas_entregadas", "Ventas entregadas",
                "La base no guarda fecha de entrega: pedido solo tiene fecha_pedido, "
                        + "fecha_limite_entrega y fecha_empaque. Para medir esto haría falta una "
                        + "columna nueva y su migración.",
                null);
    }

    private IndicadorDTO diasHastaDespacho(Ventana v) {
        return intentar("dias_despacho", "Días de pedido a despacho", () -> {
            Par p = consultas.diasHastaDespacho(v.desde, v.hastaExcl);
            BigDecimal conFecha = consultas.despachadosConFecha(v.desde, v.hastaExcl);
            if (p.sinBase() || p.valor() == null) {
                return IndicadorDTO.ok("dias_despacho", "Días de pedido a despacho", "días",
                        null, v.etiqueta, "pedido.fecha_empaque − pedido.fecha_pedido", "/despachos");
            }
            String cobertura = "Calculado sobre " + conFecha.toBigInteger() + " de "
                    + p.denominador().toBigInteger() + " pedidos despachados: el resto no tiene "
                    + "fecha de empaque registrada";
            return IndicadorDTO.parcial("dias_despacho", "Días de pedido a despacho", "días",
                    p.valor(), p.denominador(), v.etiqueta,
                    "media de fecha_empaque − fecha_pedido en los pedidos enviados o entregados",
                    cobertura, "/despachos");
        });
    }

    /**
     * La serie del grafico, rellenando los dias sin actividad con ceros.
     *
     * <p>El relleno se hace aqui y no en SQL —{@code generate_series} habria
     * servido— porque el hueco es una decision de presentacion: la consulta
     * dice que dias tuvieron pedidos, y el tablero decide que un dia sin
     * pedidos se dibuja en cero y no como un salto en la linea.
     */
    private List<SerieDiaDTO> intentarSerie(Ventana v) {
        try {
            Map<LocalDate, Map<String, Object>> porDia = new HashMap<>();
            for (Map<String, Object> f : consultas.serieDiaria(v.desde, v.hastaExcl)) {
                porDia.put(((java.sql.Date) f.get("dia")).toLocalDate(), f);
            }
            List<SerieDiaDTO> serie = new ArrayList<>(v.dias);
            for (LocalDate d = v.desde; d.isBefore(v.hastaExcl); d = d.plusDays(1)) {
                Map<String, Object> f = porDia.get(d);
                serie.add(f == null
                        ? new SerieDiaDTO(d, 0L, BigDecimal.ZERO)
                        : new SerieDiaDTO(d,
                                ((Number) f.get("pedidos")).longValue(),
                                (BigDecimal) f.get("importe")));
            }
            return serie;
        } catch (RuntimeException e) {
            log.warn("No se pudo calcular la serie diaria del dashboard", e);
            return List.of();
        }
    }

    private List<TopProductoPeriodoDTO> intentarTop(Ventana v) {
        try {
            List<Map<String, Object>> filas = consultas.topProductos(v.desde, v.hastaExcl, TOP_PRODUCTOS);
            List<TopProductoPeriodoDTO> top = new ArrayList<>(filas.size());
            for (Map<String, Object> f : filas) {
                top.add(new TopProductoPeriodoDTO(
                        String.valueOf(f.get("nombre")),
                        new BigDecimal(String.valueOf(f.get("unidades")))));
            }
            return top;
        } catch (RuntimeException e) {
            log.warn("No se pudo calcular el top de productos del dashboard", e);
            return List.of();
        }
    }

    // ==================================================================
    //  Infraestructura
    // ==================================================================

    /**
     * Calcula un indicador dejando que falle sin arrastrar a los demas.
     *
     * <p>Al usuario se le da el tipo de fallo, no el mensaje del motor: un
     * «ERROR: permiso denegado a la tabla cuenta_por_pagar» en pantalla es a la
     * vez incomprensible y un mapa de la base para quien no deberia tenerlo. El
     * detalle va al log del servidor, que es donde se mira.
     */
    private IndicadorDTO intentar(String clave, String titulo, Supplier<IndicadorDTO> calculo) {
        try {
            return calculo.get();
        } catch (RuntimeException e) {
            log.warn("Fallo al calcular el indicador '{}' del dashboard", clave, e);
            return IndicadorDTO.error(clave, titulo,
                    "No se pudo calcular. Inténtalo de nuevo; si sigue fallando, avisa a soporte.",
                    null);
        }
    }

    /** {@code "ROLE_ENCARGADO DE PRODUCCIÓN"} -> {@code "ENCARGADO DE PRODUCCION"}. */
    private String rolDelUsuario() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "";
        }
        String primero = "";
        for (GrantedAuthority a : auth.getAuthorities()) {
            String rol = RoleRoutingDataSource.normalizarAuthority(a.getAuthority());
            if (rol == null) {
                continue;   // es un permiso "modulo:accion", no un rol
            }
            // Igual que en el enrutado de conexiones: si alguien es administrador
            // ademas de otra cosa, manda administrador. De lo contrario el tablero
            // que ve y la conexion con la que se calcula podrian no coincidir.
            if ("ADMINISTRADOR".equals(rol)) {
                return rol;
            }
            if (primero.isEmpty()) {
                primero = rol;
            }
        }
        return primero;
    }

    private Integer idDelUsuario() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Usuario u ? u.getIdUsuario() : null;
    }

    /** {@code "OPERADOR DE BODEGA"} -> {@code "Operador de bodega"}. */
    private static String enBonito(String rol) {
        if (rol == null || rol.isEmpty()) {
            return "usuario";
        }
        return rol.charAt(0) + rol.substring(1).toLowerCase();
    }

    // ==================================================================
    //  La ventana temporal
    // ==================================================================

    /**
     * El periodo, y el periodo inmediatamente anterior de la misma longitud.
     *
     * <p>El limite superior es <b>exclusivo</b> y vale mañana: asi el dia en
     * curso entra entero sin depender de la hora, y las dos ventanas —actual y
     * previa— se tocan sin solaparse ni dejar un dia fuera.
     */
    static final class Ventana {

        private static final String[] MES = {
            "ene", "feb", "mar", "abr", "may", "jun",
            "jul", "ago", "sep", "oct", "nov", "dic"
        };

        final String clave;
        final int dias;
        final LocalDate desde;
        final LocalDate hastaExcl;
        final LocalDate previoDesde;
        final String etiqueta;
        final String etiquetaPrevio;

        private Ventana(String clave, int dias, LocalDate hoy) {
            this.clave = clave;
            this.dias = dias;
            this.hastaExcl = hoy.plusDays(1);
            this.desde = hoy.minusDays(dias - 1L);
            this.previoDesde = this.desde.minusDays(dias);
            this.etiqueta = "Últimos " + dias + " días (" + rango(desde, hoy) + ")";
            this.etiquetaPrevio = dias + " días previos ("
                    + rango(previoDesde, desde.minusDays(1)) + ")";
        }

        static Ventana de(String clave) {
            LocalDate hoy = LocalDate.now();
            return switch (clave == null ? "" : clave.trim().toLowerCase()) {
                case "7d" -> new Ventana("7d", 7, hoy);
                case "90d" -> new Ventana("90d", 90, hoy);
                default -> new Ventana("30d", 30, hoy);
            };
        }

        /** «28 jul – 26 ago de 2026», sin depender del Locale de la maquina. */
        private static String rango(LocalDate a, LocalDate b) {
            return a.getDayOfMonth() + " " + MES[a.getMonthValue() - 1]
                    + " – " + b.getDayOfMonth() + " " + MES[b.getMonthValue() - 1]
                    + " de " + b.getYear();
        }
    }
}
