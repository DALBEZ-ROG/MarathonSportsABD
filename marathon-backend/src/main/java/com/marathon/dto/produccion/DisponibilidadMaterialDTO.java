package com.marathon.dto.produccion;

import java.math.BigDecimal;

public class DisponibilidadMaterialDTO {

    private Integer idMateriaPrima;
    private String nombreMateriaPrima;
    private String unidadMedida;
    private BigDecimal cantidadNecesaria;
    private BigDecimal stockDisponible;
    private Boolean suficiente;
    private BigDecimal faltante;

    public DisponibilidadMaterialDTO() {}

    public Integer getIdMateriaPrima() { return idMateriaPrima; }
    public void setIdMateriaPrima(Integer idMateriaPrima) { this.idMateriaPrima = idMateriaPrima; }

    public String getNombreMateriaPrima() { return nombreMateriaPrima; }
    public void setNombreMateriaPrima(String nombreMateriaPrima) { this.nombreMateriaPrima = nombreMateriaPrima; }

    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }

    public BigDecimal getCantidadNecesaria() { return cantidadNecesaria; }
    public void setCantidadNecesaria(BigDecimal cantidadNecesaria) { this.cantidadNecesaria = cantidadNecesaria; }

    public BigDecimal getStockDisponible() { return stockDisponible; }
    public void setStockDisponible(BigDecimal stockDisponible) { this.stockDisponible = stockDisponible; }

    public Boolean getSuficiente() { return suficiente; }
    public void setSuficiente(Boolean suficiente) { this.suficiente = suficiente; }

    public BigDecimal getFaltante() { return faltante; }
    public void setFaltante(BigDecimal faltante) { this.faltante = faltante; }
}
