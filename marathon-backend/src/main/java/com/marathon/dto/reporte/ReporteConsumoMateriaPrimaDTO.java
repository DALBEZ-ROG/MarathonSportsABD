package com.marathon.dto.reporte;

import java.math.BigDecimal;

/**
 * F30 — Consumo de materia prima por producción en un período.
 * Agrupa movimiento_materia_prima ('salida_produccion' y 'merma').
 */
public class ReporteConsumoMateriaPrimaDTO {

    private Integer idMateriaPrima;
    private String nombreMateriaPrima;
    private String unidadMedida;
    private BigDecimal cantidadConsumidaTotal;
    private BigDecimal costoConsumidoTotal;
    private Long numeroOrdenes;

    public ReporteConsumoMateriaPrimaDTO() {}

    public ReporteConsumoMateriaPrimaDTO(Integer idMateriaPrima, String nombreMateriaPrima, String unidadMedida,
                                        BigDecimal cantidadConsumidaTotal, BigDecimal costoConsumidoTotal,
                                        Long numeroOrdenes) {
        this.idMateriaPrima = idMateriaPrima;
        this.nombreMateriaPrima = nombreMateriaPrima;
        this.unidadMedida = unidadMedida;
        this.cantidadConsumidaTotal = cantidadConsumidaTotal;
        this.costoConsumidoTotal = costoConsumidoTotal;
        this.numeroOrdenes = numeroOrdenes;
    }

    public Integer getIdMateriaPrima() { return idMateriaPrima; }
    public void setIdMateriaPrima(Integer v) { this.idMateriaPrima = v; }

    public String getNombreMateriaPrima() { return nombreMateriaPrima; }
    public void setNombreMateriaPrima(String v) { this.nombreMateriaPrima = v; }

    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String v) { this.unidadMedida = v; }

    public BigDecimal getCantidadConsumidaTotal() { return cantidadConsumidaTotal; }
    public void setCantidadConsumidaTotal(BigDecimal v) { this.cantidadConsumidaTotal = v; }

    public BigDecimal getCostoConsumidoTotal() { return costoConsumidoTotal; }
    public void setCostoConsumidoTotal(BigDecimal v) { this.costoConsumidoTotal = v; }

    public Long getNumeroOrdenes() { return numeroOrdenes; }
    public void setNumeroOrdenes(Long v) { this.numeroOrdenes = v; }
}
