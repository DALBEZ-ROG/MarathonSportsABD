package com.marathon.dto.produccion;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public class ConsumoRealItemDTO {

    @NotNull(message = "La materia prima es obligatoria")
    private Integer idMateriaPrima;

    @NotNull(message = "La cantidad real es obligatoria")
    @DecimalMin(value = "0", message = "La cantidad real no puede ser negativa")
    private BigDecimal cantidadReal;

    public ConsumoRealItemDTO() {}

    public Integer getIdMateriaPrima() { return idMateriaPrima; }
    public void setIdMateriaPrima(Integer idMateriaPrima) { this.idMateriaPrima = idMateriaPrima; }

    public BigDecimal getCantidadReal() { return cantidadReal; }
    public void setCantidadReal(BigDecimal cantidadReal) { this.cantidadReal = cantidadReal; }
}
