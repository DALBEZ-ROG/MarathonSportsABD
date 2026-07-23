package com.marathon.dto.bom;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public class ListaMaterialesRequestDTO {

    @NotNull(message = "La materia prima es obligatoria")
    private Integer idMateriaPrima;

    @NotNull(message = "La cantidad necesaria es obligatoria")
    @DecimalMin(value = "0.001", message = "La cantidad necesaria debe ser mayor a 0")
    private BigDecimal cantidadNecesaria;

    public ListaMaterialesRequestDTO() {}

    public Integer getIdMateriaPrima() { return idMateriaPrima; }
    public void setIdMateriaPrima(Integer idMateriaPrima) { this.idMateriaPrima = idMateriaPrima; }

    public BigDecimal getCantidadNecesaria() { return cantidadNecesaria; }
    public void setCantidadNecesaria(BigDecimal cantidadNecesaria) { this.cantidadNecesaria = cantidadNecesaria; }
}
