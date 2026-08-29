package com.marathon.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.dashboard.AnaliticaDTO;
import com.marathon.repository.AnaliticaConsultas;

/**
 * El análisis del negocio: qué se vende, quién compra y dónde (F80).
 *
 * <p><b>La ventana la decide aquí, no la pantalla.</b> El navegador manda una
 * clave —{@code 30d}, {@code 90d}, {@code 12m}, {@code todo}— y el servidor la
 * traduce a dos fechas que <b>viajan de vuelta</b>. Si las fechas las calculara
 * el navegador, dos pestañas abiertas en distinto huso horario enseñarían
 * períodos distintos llamándolos igual.
 *
 * <p><b>Un ranking vacío se devuelve vacío.</b> Ninguna lista se rellena con
 * ceros ni con filas inventadas: si en la ventana no hubo compras, la lista de
 * lo más comprado viene sin elementos y la pantalla dice «no hubo». Un cero
 * parece un dato; un hueco declarado es la verdad.
 */
@Service
public class AnaliticaService {

    /** Cuántas filas trae cada ranking. Más de diez deja de ser un ranking. */
    private static final int TOPE = 10;

    private final AnaliticaConsultas consultas;

    public AnaliticaService(AnaliticaConsultas consultas) {
        this.consultas = consultas;
    }

    @Transactional(readOnly = true)
    public AnaliticaDTO analitica(String periodo) {
        LocalDate hoy = LocalDate.now();
        // La consulta usa un limite EXCLUSIVO para que el dia de hoy entre
        // entero: con `<= hoy` se perderian los pedidos de esta misma tarde,
        // porque fecha_pedido lleva hora.
        LocalDate hastaExcl = hoy.plusDays(1);
        LocalDate desde;
        String etiqueta;

        switch (periodo == null ? "" : periodo) {
            case "90d" -> { desde = hoy.minusDays(89); etiqueta = "Últimos 90 días"; }
            case "12m" -> { desde = hoy.minusMonths(12).plusDays(1); etiqueta = "Últimos 12 meses"; }
            case "todo" -> { desde = LocalDate.of(2000, 1, 1); etiqueta = "Todo el histórico"; }
            default -> { desde = hoy.minusDays(29); etiqueta = "Últimos 30 días"; }
        }

        AnaliticaDTO dto = new AnaliticaDTO();
        dto.setDesde(desde);
        dto.setHasta(hoy);
        dto.setPeriodoEtiqueta(etiqueta);

        Map<String, Object> resumen = consultas.resumen(desde, hastaExcl);
        dto.setPedidos(numeroLargo(resumen.get("pedidos")));
        dto.setImporte(decimal(resumen.get("importe")));
        dto.setClientes(numeroLargo(resumen.get("clientes")));
        dto.setTicketMedio(decimal(resumen.get("ticket")));

        dto.setProductosMasVendidos(consultas.productosMasVendidos(desde, hastaExcl, TOPE));
        dto.setProductosMasComprados(consultas.productosMasComprados(desde, hastaExcl, TOPE));
        dto.setMejoresClientes(consultas.mejoresClientes(desde, hastaExcl, TOPE));
        dto.setVentasPorRegion(consultas.ventasPorRegion(desde, hastaExcl));
        dto.setVentasPorCiudad(consultas.ventasPorCiudad(desde, hastaExcl, TOPE));
        dto.setVentasPorCategoria(consultas.ventasPorCategoria(desde, hastaExcl, TOPE));
        dto.setDevolucionesPorMotivo(consultas.devolucionesPorMotivo(desde, hastaExcl));
        // Dia o mes segun lo ancha que sea la ventana: en 30 dias una serie
        // mensual son dos puntos, y dos puntos unidos no son una tendencia.
        boolean porDia = desde.plusDays(120).isAfter(hoy);
        dto.setGranularidad(porDia ? "dia" : "mes");
        dto.setSerie(porDia ? consultas.ventasPorDia(desde, hastaExcl)
                            : consultas.ventasPorMes(desde, hastaExcl));

        return dto;
    }

    private long numeroLargo(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private BigDecimal decimal(Object v) {
        if (v instanceof BigDecimal b) { return b; }
        return v instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
    }
}
