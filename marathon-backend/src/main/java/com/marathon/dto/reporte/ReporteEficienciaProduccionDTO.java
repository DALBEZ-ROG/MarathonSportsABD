package com.marathon.dto.reporte;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * F30 — Eficiencia por orden de producción completada:
 * producidas/planificadas y merma total de materia prima.
 */
public class ReporteEficienciaProduccionDTO {

    private Integer idOrdenProduccion;
    private String producto;
    private Integer cantidadPlanificada;
    private Integer cantidadProducida;
    private BigDecimal eficienciaProduccion;
    private BigDecimal mermaTotalMateriaPrima;
    private BigDecimal costoTotal;
    private BigDecimal costoUnitario;
    private LocalDate fechaFin;

    public ReporteEficienciaProduccionDTO() {}

    public Integer getIdOrdenProduccion() { return idOrdenProduccion; }
    public void setIdOrdenProduccion(Integer v) { this.idOrdenProduccion = v; }

    public String getProducto() { return producto; }
    public void setProducto(String v) { this.producto = v; }

    public Integer getCantidadPlanificada() { return cantidadPlanificada; }
    public void setCantidadPlanificada(Integer v) { this.cantidadPlanificada = v; }

    public Integer getCantidadProducida() { return cantidadProducida; }
    public void setCantidadProducida(Integer v) { this.cantidadProducida = v; }

    public BigDecimal getEficienciaProduccion() { return eficienciaProduccion; }
    public void setEficienciaProduccion(BigDecimal v) { this.eficienciaProduccion = v; }

    public BigDecimal getMermaTotalMateriaPrima() { return mermaTotalMateriaPrima; }
    public void setMermaTotalMateriaPrima(BigDecimal v) { this.mermaTotalMateriaPrima = v; }

    public BigDecimal getCostoTotal() { return costoTotal; }
    public void setCostoTotal(BigDecimal v) { this.costoTotal = v; }

    public BigDecimal getCostoUnitario() { return costoUnitario; }
    public void setCostoUnitario(BigDecimal v) { this.costoUnitario = v; }

    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate v) { this.fechaFin = v; }
}
