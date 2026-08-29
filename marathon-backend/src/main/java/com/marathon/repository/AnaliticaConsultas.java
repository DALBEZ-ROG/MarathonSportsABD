package com.marathon.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Las preguntas que se hace quien analiza el negocio (F80).
 *
 * <p><b>Por qué existe esta clase aparte de {@link DashboardConsultas}.</b>
 * Aquella responde «¿cómo va todo <i>ahora</i>?» con cifras de una fila; ésta
 * responde «¿qué se vende, quién compra y dónde?» con <b>rankings</b>. Son
 * consultas de otra forma —{@code group by} sobre cientos de miles de filas con
 * {@code order by} y {@code limit}— y mezclarlas habría hecho de la otra clase
 * un cajón.
 *
 * <p><b>Todo lleva ventana de fechas.</b> Un «producto más vendido» sin período
 * es el acumulado de dos años, que no sirve para decidir nada: lo que se quiere
 * saber es qué se está vendiendo <i>ahora</i>. Las fechas van como parámetros,
 * nunca concatenadas.
 *
 * <p><b>Los pedidos anulados no cuentan en ningún sitio.</b> Un pedido anulado
 * no se vendió; contarlo en «lo más vendido» o en «el mejor cliente» sería
 * premiar una venta que no ocurrió.
 *
 * <p><b>Nada se rellena con ceros.</b> Si una consulta no devuelve filas, la
 * lista viene vacía y la pantalla lo dice; un ranking de ceros parece un dato y
 * es un hueco.
 */
@Repository
public class AnaliticaConsultas {

    private final JdbcTemplate jdbc;

    public AnaliticaConsultas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------
    //  Qué se vende
    // ------------------------------------------------------------------

    /**
     * Lo más vendido de la ventana, en unidades y en dinero.
     *
     * <p>Se ordena por <b>unidades</b>, no por importe: «lo que más sale» es una
     * pregunta de rotación. El importe va al lado porque el producto que más
     * unidades mueve no siempre es el que más factura, y esa diferencia es
     * justamente lo que hay que poder ver.
     */
    public List<Map<String, Object>> productosMasVendidos(LocalDate desde, LocalDate hastaExcl, int limite) {
        return jdbc.queryForList("""
                select p.nombre                        as nombre,
                       sum(d.cantidad)                 as unidades,
                       sum(d.cantidad * d.precio_unitario) as importe
                  from detalle_pedido d
                  join pedido pe    on pe.id_pedido = d.id_pedido
                  join producto p   on p.id_producto = d.id_producto
                 where pe.fecha_pedido >= ? and pe.fecha_pedido < ?
                   and pe.estado <> 'anulado'
                 group by p.nombre
                 order by unidades desc, importe desc
                 limit ?
                """, desde, hastaExcl, limite);
    }

    /**
     * Lo más comprado a proveedores, en unidades y en dinero.
     *
     * <p>Cuenta solo lo que <b>llegó</b>, y cuenta la cantidad <b>recibida</b>,
     * no la pedida: una orden aprobada todavía no es mercancía, y en una
     * {@code recibida_parcial} lo que hay en el almacén es lo que llegó, no lo
     * que se encargó. Contar lo pedido sería contar una intención.
     *
     * <p>Se filtra {@code id_producto is not null} porque una línea de orden de
     * compra puede ser <b>materia prima</b>, que no es un producto del catálogo.
     */
    public List<Map<String, Object>> productosMasComprados(LocalDate desde, LocalDate hastaExcl, int limite) {
        return jdbc.queryForList("""
                select p.nombre                                            as nombre,
                       sum(coalesce(d.cantidad_recibida, 0))               as unidades,
                       sum(coalesce(d.cantidad_recibida, 0) * d.precio_unitario) as importe
                  from orden_compra_detalle d
                  join orden_compra oc on oc.id_orden_compra = d.id_orden_compra
                  join producto p      on p.id_producto = d.id_producto
                 where oc.fecha_orden >= ? and oc.fecha_orden < ?
                   and oc.estado in ('recibida_completa', 'recibida_parcial')
                   and d.id_producto is not null
                 group by p.nombre
                having sum(coalesce(d.cantidad_recibida, 0)) > 0
                 order by unidades desc, importe desc
                 limit ?
                """, desde, hastaExcl, limite);
    }

    // ------------------------------------------------------------------
    //  Quién compra
    // ------------------------------------------------------------------

    /**
     * Los clientes que más facturan en la ventana.
     *
     * <p>Se ordena por <b>importe</b> y no por número de pedidos: el mejor
     * cliente es el que más deja, no el que más veces viene. El número de
     * pedidos va al lado para distinguir al que hace una compra grande del que
     * vuelve cada semana — son dos clientes distintos y se tratan distinto.
     */
    public List<Map<String, Object>> mejoresClientes(LocalDate desde, LocalDate hastaExcl, int limite) {
        return jdbc.queryForList("""
                select c.nombre || ' ' || c.apellido as nombre,
                       ci.nombre                     as ciudad,
                       count(*)                      as pedidos,
                       coalesce(sum(pe.total), 0)    as importe
                  from pedido pe
                  join cliente c  on c.id_cliente = pe.id_cliente
                  join ciudad ci  on ci.id_ciudad = c.id_ciudad
                 where pe.fecha_pedido >= ? and pe.fecha_pedido < ?
                   and pe.estado <> 'anulado'
                 group by c.id_cliente, c.nombre, c.apellido, ci.nombre
                 order by importe desc
                 limit ?
                """, desde, hastaExcl, limite);
    }

    // ------------------------------------------------------------------
    //  Dónde se vende
    // ------------------------------------------------------------------

    /**
     * Lo vendido por región natural (F77: la región vive en la ciudad).
     *
     * <p>Las ciudades sin clasificar salen agrupadas como «sin región» en vez de
     * desaparecer: si un día quedara una sin clasificar, esta fila es la que lo
     * delata.
     */
    public List<Map<String, Object>> ventasPorRegion(LocalDate desde, LocalDate hastaExcl) {
        return jdbc.queryForList("""
                select coalesce(ci.region, 'Sin región') as nombre,
                       count(*)                          as pedidos,
                       coalesce(sum(pe.total), 0)        as importe
                  from pedido pe
                  join cliente c on c.id_cliente = pe.id_cliente
                  join ciudad ci on ci.id_ciudad = c.id_ciudad
                 where pe.fecha_pedido >= ? and pe.fecha_pedido < ?
                   and pe.estado <> 'anulado'
                 group by coalesce(ci.region, 'Sin región')
                 order by importe desc
                """, desde, hastaExcl);
    }

    /** Las ciudades que más facturan. */
    public List<Map<String, Object>> ventasPorCiudad(LocalDate desde, LocalDate hastaExcl, int limite) {
        return jdbc.queryForList("""
                select ci.nombre                   as nombre,
                       coalesce(ci.region, '—')    as region,
                       count(*)                    as pedidos,
                       coalesce(sum(pe.total), 0)  as importe
                  from pedido pe
                  join cliente c on c.id_cliente = pe.id_cliente
                  join ciudad ci on ci.id_ciudad = c.id_ciudad
                 where pe.fecha_pedido >= ? and pe.fecha_pedido < ?
                   and pe.estado <> 'anulado'
                 group by ci.id_ciudad, ci.nombre, ci.region
                 order by importe desc
                 limit ?
                """, desde, hastaExcl, limite);
    }

    // ------------------------------------------------------------------
    //  Qué marcas y qué se devuelve
    // ------------------------------------------------------------------

    /**
     * Lo vendido por categoría, para ver de qué se vive.
     *
     * <p>Es <b>categoría</b> y no marca porque en esta base no hay tabla de
     * marcas: la marca vive dentro del texto de la descripción del producto
     * («Marca: NIKE»), y agrupar por un trozo de texto libre sería inventarse
     * una dimensión que el modelo no tiene.
     */
    public List<Map<String, Object>> ventasPorCategoria(LocalDate desde, LocalDate hastaExcl, int limite) {
        return jdbc.queryForList("""
                select coalesce(ca.nombre, 'Sin categoría')      as nombre,
                       sum(d.cantidad)                          as unidades,
                       sum(d.cantidad * d.precio_unitario)      as importe
                  from detalle_pedido d
                  join pedido pe    on pe.id_pedido = d.id_pedido
                  join producto p   on p.id_producto = d.id_producto
                  left join categoria ca on ca.id_categoria = p.id_categoria
                 where pe.fecha_pedido >= ? and pe.fecha_pedido < ?
                   and pe.estado <> 'anulado'
                 group by coalesce(ca.nombre, 'Sin categoría')
                 order by importe desc
                 limit ?
                """, desde, hastaExcl, limite);
    }

    /**
     * Por qué devuelven, en unidades.
     *
     * <p>Se cuentan las <b>unidades</b> devueltas y no las solicitudes: una
     * solicitud de veinte prendas y otra de una no son el mismo problema.
     */
    public List<Map<String, Object>> devolucionesPorMotivo(LocalDate desde, LocalDate hastaExcl) {
        return jdbc.queryForList("""
                select s.motivo                        as nombre,
                       count(distinct s.id_solicitud)  as solicitudes,
                       coalesce(sum(d.cantidad_devuelta), 0) as unidades
                  from solicitud_devolucion s
                  join solicitud_devolucion_detalle d on d.id_solicitud = s.id_solicitud
                 where s.fecha_solicitud >= ? and s.fecha_solicitud < ?
                   and s.estado <> 'rechazada'
                 group by s.motivo
                 order by unidades desc
                """, desde, hastaExcl);
    }

    // ------------------------------------------------------------------
    //  Cómo evoluciona
    // ------------------------------------------------------------------

    /**
     * Ventas por dia de la ventana, para ventanas cortas.
     *
     * <p>En 30 dias una serie mensual son DOS puntos, y dos puntos unidos por
     * una recta no son una tendencia: son una recta. La granularidad la elige el
     * servicio segun el ancho de la ventana.
     */
    public List<Map<String, Object>> ventasPorDia(LocalDate desde, LocalDate hastaExcl) {
        // El generate_series NO es adorno: un dia sin ventas vale CERO -el dia
        // existio y no se vendio-, y dejarlo fuera de la serie junta el dia 3
        // con el dia 7 como si fueran consecutivos, que es deformar la linea.
        // Esto es distinto de inventar un dato: aqui el hueco se conoce.
        return jdbc.queryForList("""
                select to_char(d.dia, 'YYYY-MM-DD')        as periodo,
                       count(pe.id_pedido)                  as pedidos,
                       coalesce(sum(pe.total), 0)           as importe
                  from generate_series(?::date, (?::date - interval '1 day'), interval '1 day') as d(dia)
                  left join pedido pe
                         on pe.fecha_pedido >= d.dia
                        and pe.fecha_pedido <  d.dia + interval '1 day'
                        and pe.estado <> 'anulado'
                 group by d.dia
                 order by d.dia
                """, desde, hastaExcl);
    }

    /**
     * Ventas por mes de la ventana.
     *
     * <p>Un mes sin ventas <b>no aparece</b>: la serie sale de los pedidos que
     * hay. La pantalla dibuja los meses que vengan y dice desde cuándo — no se
     * inventa un cero para un mes del que no se sabe nada.
     */
    public List<Map<String, Object>> ventasPorMes(LocalDate desde, LocalDate hastaExcl) {
        return jdbc.queryForList("""
                select to_char(pe.fecha_pedido, 'YYYY-MM') as periodo,
                       count(*)                            as pedidos,
                       coalesce(sum(pe.total), 0)          as importe
                  from pedido pe
                 where pe.fecha_pedido >= ? and pe.fecha_pedido < ?
                   and pe.estado <> 'anulado'
                 group by to_char(pe.fecha_pedido, 'YYYY-MM')
                 order by 1
                """, desde, hastaExcl);
    }

    /** Las cifras de cabecera de la ventana, en una sola consulta. */
    public Map<String, Object> resumen(LocalDate desde, LocalDate hastaExcl) {
        return jdbc.queryForMap("""
                select count(*)                             as pedidos,
                       coalesce(sum(pe.total), 0)           as importe,
                       count(distinct pe.id_cliente)        as clientes,
                       coalesce(avg(pe.total), 0)           as ticket
                  from pedido pe
                 where pe.fecha_pedido >= ? and pe.fecha_pedido < ?
                   and pe.estado <> 'anulado'
                """, desde, hastaExcl);
    }
}
