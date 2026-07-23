package com.marathon.dto.produccion;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class CompletarProduccionDTO {

    @NotNull(message = "La cantidad producida es obligatoria")
    @Min(value = 0, message = "La cantidad producida no puede ser negativa")
    private Integer cantidadProducida;

    // Si viene null, se asume consumo real = teorico (merma 0).
    @Valid
    private List<ConsumoRealItemDTO> consumosReales;

    private String observaciones;

    public CompletarProduccionDTO() {}

    public Integer getCantidadProducida() { return cantidadProducida; }
    public void setCantidadProducida(Integer cantidadProducida) { this.cantidadProducida = cantidadProducida; }

    public List<ConsumoRealItemDTO> getConsumosReales() { return consumosReales; }
    public void setConsumosReales(List<ConsumoRealItemDTO> consumosReales) { this.consumosReales = consumosReales; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}
