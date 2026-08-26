package com.marathon.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Las consultas agregadas del dashboard (D1).
 *
 * <p><b>Por que SQL a mano y no repositorios JPA.</b> Cada cifra de aqui es un
 * {@code count}, un {@code sum} o un {@code avg} sobre cientos de miles de
 * filas. Con JPA habria que elegir entre proyecciones de una sola columna —que
 * obligan a una consulta por numero— o traerse entidades para contarlas en
 * memoria, que es justo lo que se vino a quitar (el navegador se descargaba la
 * lista entera de materia prima bajo minimo <i>solo para hacer
 * {@code res.length}</i>). Aqui <b>ninguna consulta devuelve filas de
 * detalle</b>: todas vuelven con una fila y dos o tres columnas, salvo el top de
 * productos, que devuelve cinco por definicion.
 *
 * <p><b>Las fechas se pasan como parametros, nunca se concatenan.</b> Ademas de
 * lo obvio, permite que la prueba ejecute exactamente la misma ventana temporal
 * que el servicio y compare cifra contra cifra.
 *
 * <p><b>Cada rol consulta solo lo que su usuario de base de datos puede leer.</b>
 * Las conexiones van por {@code RoleRoutingDataSource} (F37) y los permisos son
 * por tabla y por columna (F34): el operador de bodega no tiene {@code SELECT}
 * sobre {@code orden_produccion} ni sobre {@code cuenta_por_pagar}. Por eso el
 * reparto de indicadores por rol del servicio no es solo una decision de
 * producto — es tambien lo unico que la base le deja ejecutar.
 */
@Repository
public class DashboardConsultas {

    /** Un numerador con su denominador. El numerador nunca viaja solo. */
    public record Par(BigDecimal valor, BigDecimal denominador) {

        /** {@code true} si no hay nada sobre lo que calcular. */
        public boolean sinBase() {
            return denominador == null || denominador.compareTo(BigDecimal.ZERO) == 0;
        }
    }

    private final JdbcTemplate jdbc;

    public DashboardConsultas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------
    //  Ayudas
    // ------------------------------------------------------------------

    private BigDecimal numero(String sql, Object... args) {
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, args);
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Dos contadores. Los {@code count} nunca son nulos, asi que cero es cero de verdad. */
    private Par par(String sql, Object... args) {
        return jdbc.queryForObject(sql, (rs, i) -> new Par(
                rs.getBigDecimal(1) == null ? BigDecimal.ZERO : rs.getBigDecimal(1),
                rs.getBigDecimal(2) == null ? BigDecimal.ZERO : rs.getBigDecimal(2)), args);
    }

    /**
     * Un promedio con su <i>n</i>. Aqui el valor <b>si</b> puede ser nulo, y se
     * respeta: {@code avg} sobre cero filas es nulo, no cero, y esa diferencia
     * es exactamente la que el dashboard tiene que mostrar.
     */
    private Par promedioConN(String sql, Object... args) {
        return jdbc.queryForObject(sql, (rs, i) -> new Par(
                rs.getBigDecimal(1),
                rs.getBigDecimal(2) == null ? BigDecimal.ZERO : rs.getBigDecimal(2)), args);
    }

    // ------------------------------------------------------------------
    //  Venta - pedidos
    // ------------------------------------------------------------------

    /** Pedidos creados en la ventana, todos los estados. */
    public BigDecimal pedidosCreados(LocalDate desde, LocalDate hastaExcl) {
        return numero("""
                select count(*) from pedido
                 where fecha_pedido >= ? and fecha_pedido < ?
                """, desde, hastaExcl);
    }

    /** Importe pedido (valor) y numero de pedidos (denominador): juntos dan el ticket medio. */
    public Par importeYPedidos(LocalDate desde, LocalDate hastaExcl) {
        return par("""
                select coalesce(sum(total), 0), count(*) from pedido
                 where fecha_pedido >= ? and fecha_pedido < ? and estado <> 'anulado'
                """, desde, hastaExcl);
    }

    /** Anulados sobre creados. El porcentaje que importa, no el numero suelto. */
    public Par anuladosSobreCreados(LocalDate desde, LocalDate hastaExcl) {
        return par("""
                select count(*) filter (where estado = 'anulado'), count(*) from pedido
                 where fecha_pedido >= ? and fecha_pedido < ?
                """, desde, hastaExcl);
    }

    /** Pedidos en {@code procesado} con mas de {@code dias} dias. Estado actual, sin ventana. */
    public BigDecimal pedidosAtascados(int dias) {
        return numero("""
                select count(*) from pedido
                 where estado = 'procesado' and fecha_pedido < current_date - cast(? as integer)
                """, dias);
    }

    /**
     * Pedidos especiales realmente abiertos: {@code pendiente} o {@code procesado}.
     * El dashboard anterior contaba «distinto de anulado», que incluye los ya
     * entregados e inflaba la cifra mas de seis veces.
     */
    public BigDecimal especialesAbiertos() {
        return numero("""
                select count(*) from pedido
                 where es_pedido_especial and estado in ('pendiente', 'procesado')
                """);
    }

    /** Pedidos pendientes creados dentro de la ventana: la cola real, no el historico. */
    public BigDecimal colaPendiente(LocalDate desde, LocalDate hastaExcl) {
        return numero("""
                select count(*) from pedido
                 where estado = 'pendiente' and fecha_pedido >= ? and fecha_pedido < ?
                """, desde, hastaExcl);
    }

    /** Pedidos creados en la ventana por un usuario concreto. */
    public BigDecimal pedidosCreadosPor(Integer idUsuario, LocalDate desde, LocalDate hastaExcl) {
        return numero("""
                select count(*) from pedido
                 where id_usuario = ? and fecha_pedido >= ? and fecha_pedido < ?
                """, idUsuario, desde, hastaExcl);
    }

    /**
     * Dias medios entre pedido y empaque, con el total de despachados de la
     * ventana como denominador. La diferencia entre ese total y los que si
     * tienen {@code fecha_empaque} es la cobertura que se declara en la tarjeta:
     * el dato no esta en todos los pedidos y el numero no puede leerse como si
     * lo estuviera.
     */
    public Par diasHastaDespacho(LocalDate desde, LocalDate hastaExcl) {
        return promedioConN("""
                select round(avg(extract(epoch from (fecha_empaque - fecha_pedido)) / 86400)::numeric, 2),
                       count(*)
                  from pedido
                 where fecha_pedido >= ? and fecha_pedido < ? and estado in ('enviado', 'entregado')
                """, desde, hastaExcl);
    }

    /** Pedidos despachados de la ventana que si tienen {@code fecha_empaque}. */
    public BigDecimal despachadosConFecha(LocalDate desde, LocalDate hastaExcl) {
        return numero("""
                select count(fecha_empaque) from pedido
                 where fecha_pedido >= ? and fecha_pedido < ? and estado in ('enviado', 'entregado')
                """, desde, hastaExcl);
    }

    /**
     * Pedidos e importe por dia dentro de la ventana, para el grafico.
     *
     * <p>Cuenta <b>todos los pedidos no anulados</b>. El grafico anterior
     * filtraba por {@code estado = 'entregado'}, con lo que un dia no mostraba
     * lo que se pidio ese dia: mostraba lo que se pidio ese dia <i>y ademas ya
     * esta entregado hoy</i>. Los dias recientes salian sistematicamente bajos
     * porque sus pedidos todavia no han llegado a entregarse, y la curva
     * parecia una caida de ventas que no existia.
     *
     * <p>Devuelve una fila por dia <b>con actividad</b>: los huecos los rellena
     * el navegador con cero, que ahi si es cero de verdad —el dia existe y no
     * hubo pedidos—, no un dato que falta.
     */
    public List<Map<String, Object>> serieDiaria(LocalDate desde, LocalDate hastaExcl) {
        return jdbc.queryForList("""
                select cast(fecha_pedido as date) as dia,
                       count(*) as pedidos,
                       coalesce(sum(total), 0) as importe
                  from pedido
                 where fecha_pedido >= ? and fecha_pedido < ? and estado <> 'anulado'
                 group by 1
                 order by 1
                """, desde, hastaExcl);
    }

    /** Los mas vendidos <b>de la ventana</b>, no el acumulado de dos años. */
    public List<Map<String, Object>> topProductos(LocalDate desde, LocalDate hastaExcl, int limite) {
        return jdbc.queryForList("""
                select p.nombre as nombre, sum(d.cantidad) as unidades
                  from detalle_pedido d
                  join pedido pe on pe.id_pedido = d.id_pedido
                  join producto p on p.id_producto = d.id_producto
                 where pe.fecha_pedido >= ? and pe.fecha_pedido < ? and pe.estado <> 'anulado'
                 group by p.nombre
                 order by 2 desc
                 limit ?
                """, desde, hastaExcl, limite);
    }

    // ------------------------------------------------------------------
    //  Almacen
    // ------------------------------------------------------------------

    /** Pedidos procesados con alguna linea sin recoger. */
    public BigDecimal esperandoPicking() {
        return numero("""
                select count(*) from pedido pe
                 where pe.estado = 'procesado'
                   and exists (select 1 from detalle_pedido d
                                where d.id_pedido = pe.id_pedido
                                  and coalesce(d.picking_completado, false) = false)
                """);
    }

    /** Pedidos procesados con todas las lineas recogidas: los que se pueden empacar ya. */
    public BigDecimal listosParaEmpacar() {
        return numero("""
                select count(*) from pedido pe
                 where pe.estado = 'procesado'
                   and not exists (select 1 from detalle_pedido d
                                    where d.id_pedido = pe.id_pedido
                                      and coalesce(d.picking_completado, false) = false)
                """);
    }

    /** Lineas sin recoger sobre el total de lineas en picking. */
    public Par lineasPorRecoger() {
        return par("""
                select count(*) filter (where coalesce(d.picking_completado, false) = false),
                       count(*)
                  from detalle_pedido d
                  join pedido pe on pe.id_pedido = d.id_pedido
                 where pe.estado = 'procesado'
                """);
    }

    /**
     * Referencias bajo minimo sobre las que tienen minimo definido.
     * El denominador importa: «220» asusta, «220 de 1.999» se dimensiona.
     */
    public Par referenciasBajoMinimo() {
        return par("""
                select count(*) filter (where stock_actual <= stock_minimo), count(*)
                  from inventario where stock_minimo > 0
                """);
    }

    public BigDecimal movimientos(LocalDate desde, LocalDate hastaExcl) {
        return numero("""
                select count(*) from movimiento_inventario where fecha >= ? and fecha < ?
                """, desde, hastaExcl);
    }

    // ------------------------------------------------------------------
    //  Posventa
    // ------------------------------------------------------------------

    /** Devoluciones de cliente solicitadas o en inspeccion: las que esperan a alguien. */
    public BigDecimal devolucionesEsperandoInspeccion() {
        return numero("""
                select count(*) from solicitud_devolucion
                 where estado in ('solicitada', 'en_inspeccion')
                """);
    }

    // ------------------------------------------------------------------
    //  Abastecimiento
    // ------------------------------------------------------------------

    public BigDecimal ordenesPendientesAprobacion() {
        return numero("select count(*) from orden_compra where estado = 'pendiente_aprobacion'");
    }

    public BigDecimal ordenesAprobadasSinRecibir() {
        return numero("select count(*) from orden_compra where estado = 'aprobada'");
    }

    /**
     * Vencidas por fecha y saldo, no por la etiqueta {@code estado}.
     * Son 1.479 por fecha frente a 1.444 etiquetadas: 35 filas cuya etiqueta se
     * quedo atras. La fecha es el hecho; la etiqueta, una copia que puede
     * desactualizarse.
     */
    public Par cuentasVencidas() {
        return par("""
                select coalesce(sum(saldo_pendiente), 0), count(*)
                  from cuenta_por_pagar
                 where saldo_pendiente > 0 and fecha_vencimiento < current_date
                """);
    }

    /** Lo que vence en los proximos {@code dias} dias: da tiempo a reaccionar. */
    public Par cuentasQueVencen(int dias) {
        return par("""
                select coalesce(sum(saldo_pendiente), 0), count(*)
                  from cuenta_por_pagar
                 where saldo_pendiente > 0
                   and fecha_vencimiento >= current_date
                   and fecha_vencimiento <= current_date + cast(? as integer)
                """, dias);
    }

    public BigDecimal devolucionesProveedorAbiertas() {
        return numero("""
                select count(*) from devolucion_proveedor where estado in ('pendiente', 'enviada')
                """);
    }

    // ------------------------------------------------------------------
    //  Produccion
    // ------------------------------------------------------------------

    /** OP en proceso sobre el total de abiertas (planificadas + en proceso). */
    public Par ordenesEnProceso() {
        return par("""
                select count(*) filter (where estado = 'en_proceso'),
                       count(*) filter (where estado in ('planificada', 'en_proceso'))
                  from orden_produccion
                """);
    }

    /**
     * OP planificadas para las que alguna materia prima de su lista no alcanza.
     * El dato existe en la base desde siempre y no se mostraba en ningun sitio:
     * es la razon por la que una OP se queda parada el dia que toca lanzarla.
     */
    public BigDecimal ordenesSinMaterial() {
        return numero("""
                select count(*) from orden_produccion op
                 where op.estado = 'planificada'
                   and exists (
                        select 1
                          from lista_materiales lm
                          join materia_prima mp on mp.id_materia_prima = lm.id_materia_prima
                         where lm.id_producto = op.id_producto
                           and lm.estado = 'activo'
                           and mp.stock_actual < lm.cantidad_necesaria * op.cantidad_planificada)
                """);
    }

    public Par materiaPrimaBajoMinimo() {
        return par("""
                select count(*) filter (where stock_actual <= stock_minimo), count(*)
                  from materia_prima where stock_minimo > 0
                """);
    }

    /**
     * Coste medio por OP completada en la ventana, y sobre cuantas.
     * Un promedio sin su <i>n</i> no se puede interpretar: 5.125 sobre 147
     * ordenes y 5.125 sobre 2 no significan lo mismo.
     */
    public Par costoMedioOrden(LocalDate desde, LocalDate hastaExcl) {
        return promedioConN("""
                select round(avg(costo_total), 2), count(*)
                  from orden_produccion
                 where estado = 'completada' and fecha_fin >= ? and fecha_fin < ?
                """, desde, hastaExcl);
    }

    /** Merma media en porcentaje sobre lo planificado, y sobre cuantas ordenes. */
    public Par mermaMedia(LocalDate desde, LocalDate hastaExcl) {
        return promedioConN("""
                select round(avg((cantidad_planificada - cantidad_producida)::numeric * 100
                                 / nullif(cantidad_planificada, 0)), 2),
                       count(*)
                  from orden_produccion
                 where estado = 'completada' and fecha_fin >= ? and fecha_fin < ?
                   and cantidad_planificada > 0
                """, desde, hastaExcl);
    }
}
