package com.marathon.dto.reporte;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ReporteCostosProduccionItemDTO {

    private Integer idOrdenProduccion;
    private String producto;
    private Integer cantidadProducida;
    private BigDecimal costoMateriaPrima;
    private BigDecimal costoManoObra;
    private BigDecimal costoIndirecto;
    private BigDecimal costoTotal;
    private BigDecimal costoUnitario;
    private LocalDateTime fecha;

    public ReporteCostosProduccionItemDTO() {}

    public ReporteCostosProduccionItemDTO(Integer idOrdenProduccion, String producto, Integer cantidadProducida,
                                          BigDecimal costoMateriaPrima, BigDecimal costoManoObra,
                                          BigDecimal costoIndirecto, BigDecimal costoTotal,
                                          BigDecimal costoUnitario, LocalDateTime fecha) {
        this.idOrdenProduccion = idOrdenProduccion;
        this.producto = producto;
        this.cantidadProducida = cantidadProducida;
        this.costoMateriaPrima = costoMateriaPrima;
        this.costoManoObra = costoManoObra;
        this.costoIndirecto = costoIndirecto;
        this.costoTotal = costoTotal;
        this.costoUnitario = costoUnitario;
        this.fecha = fecha;
    }

    public Integer getIdOrdenProduccion() { return idOrdenProduccion; }
    public void setIdOrdenProduccion(Integer v) { this.idOrdenProduccion = v; }

    public String getProducto() { return producto; }
    public void setProducto(String v) { this.producto = v; }

    public Integer getCantidadProducida() { return cantidadProducida; }
    public void setCantidadProducida(Integer v) { this.cantidadProducida = v; }

    public BigDecimal getCostoMateriaPrima() { return costoMateriaPrima; }
    public void setCostoMateriaPrima(BigDecimal v) { this.costoMateriaPrima = v; }

    public BigDecimal getCostoManoObra() { return costoManoObra; }
    public void setCostoManoObra(BigDecimal v) { this.costoManoObra = v; }

    public BigDecimal getCostoIndirecto() { return costoIndirecto; }
    public void setCostoIndirecto(BigDecimal v) { this.costoIndirecto = v; }

    public BigDecimal getCostoTotal() { return costoTotal; }
    public void setCostoTotal(BigDecimal v) { this.costoTotal = v; }

    public BigDecimal getCostoUnitario() { return costoUnitario; }
    public void setCostoUnitario(BigDecimal v) { this.costoUnitario = v; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime v) { this.fecha = v; }
}
