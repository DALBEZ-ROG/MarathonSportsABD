package com.marathon.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Todo lo que pinta la pantalla de análisis, en una sola respuesta (F80).
 *
 * <p><b>Por qué un solo endpoint y no ocho.</b> Los ocho bloques comparten la
 * misma ventana de fechas y se miran juntos: partirlos obligaría a ocho
 * peticiones que pueden llegar desincronizadas —un gráfico de agosto al lado de
 * otro de julio— y a conceder ocho permisos donde hace falta uno.
 *
 * <p><b>La ventana viaja en la respuesta.</b> {@code desde} y {@code hasta} no
 * son decoración: sin ellos la pantalla no puede decir de cuándo son las cifras,
 * y un ranking sin período es un número que no significa nada.
 */
public class AnaliticaDTO {

    /** Inicio de la ventana, inclusive. */
    private LocalDate desde;
    /** Fin de la ventana, inclusive (lo que se enseña; la consulta usa el día siguiente). */
    private LocalDate hasta;
    private String periodoEtiqueta;

    // Cifras de cabecera de la ventana
    private long pedidos;
    private BigDecimal importe;
    private long clientes;
    private BigDecimal ticketMedio;

    private List<Map<String, Object>> productosMasVendidos;
    private List<Map<String, Object>> productosMasComprados;
    private List<Map<String, Object>> mejoresClientes;
    private List<Map<String, Object>> ventasPorRegion;
    private List<Map<String, Object>> ventasPorCiudad;
    private List<Map<String, Object>> ventasPorCategoria;
    private List<Map<String, Object>> devolucionesPorMotivo;
    private List<Map<String, Object>> serie;
    /** {@code dia} o {@code mes}: como esta agrupada la serie. */
    private String granularidad;

    public LocalDate getDesde() { return desde; }
    public void setDesde(LocalDate v) { this.desde = v; }

    public LocalDate getHasta() { return hasta; }
    public void setHasta(LocalDate v) { this.hasta = v; }

    public String getPeriodoEtiqueta() { return periodoEtiqueta; }
    public void setPeriodoEtiqueta(String v) { this.periodoEtiqueta = v; }

    public long getPedidos() { return pedidos; }
    public void setPedidos(long v) { this.pedidos = v; }

    public BigDecimal getImporte() { return importe; }
    public void setImporte(BigDecimal v) { this.importe = v; }

    public long getClientes() { return clientes; }
    public void setClientes(long v) { this.clientes = v; }

    public BigDecimal getTicketMedio() { return ticketMedio; }
    public void setTicketMedio(BigDecimal v) { this.ticketMedio = v; }

    public List<Map<String, Object>> getProductosMasVendidos() { return productosMasVendidos; }
    public void setProductosMasVendidos(List<Map<String, Object>> v) { this.productosMasVendidos = v; }

    public List<Map<String, Object>> getProductosMasComprados() { return productosMasComprados; }
    public void setProductosMasComprados(List<Map<String, Object>> v) { this.productosMasComprados = v; }

    public List<Map<String, Object>> getMejoresClientes() { return mejoresClientes; }
    public void setMejoresClientes(List<Map<String, Object>> v) { this.mejoresClientes = v; }

    public List<Map<String, Object>> getVentasPorRegion() { return ventasPorRegion; }
    public void setVentasPorRegion(List<Map<String, Object>> v) { this.ventasPorRegion = v; }

    public List<Map<String, Object>> getVentasPorCiudad() { return ventasPorCiudad; }
    public void setVentasPorCiudad(List<Map<String, Object>> v) { this.ventasPorCiudad = v; }

    public List<Map<String, Object>> getVentasPorCategoria() { return ventasPorCategoria; }
    public void setVentasPorCategoria(List<Map<String, Object>> v) { this.ventasPorCategoria = v; }

    public List<Map<String, Object>> getDevolucionesPorMotivo() { return devolucionesPorMotivo; }
    public void setDevolucionesPorMotivo(List<Map<String, Object>> v) { this.devolucionesPorMotivo = v; }

    public List<Map<String, Object>> getSerie() { return serie; }
    public void setSerie(List<Map<String, Object>> v) { this.serie = v; }

    public String getGranularidad() { return granularidad; }
    public void setGranularidad(String v) { this.granularidad = v; }
}
