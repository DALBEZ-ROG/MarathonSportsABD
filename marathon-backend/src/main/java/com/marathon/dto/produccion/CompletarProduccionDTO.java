package com.marathon.dto.produccion;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class CompletarProduccionDTO {

    @NotNull(message = "La cantidad producida es obligatoria")
    @Min(value = 0, message = "La cantidad producida no puede ser negativa")
    private Integer cantidadProducida;

    // Si viene null, se asume consumo real = teorico (merma 0).
    @Valid
    private List<ConsumoRealItemDTO> consumosReales;

    // F29 — costos globales de la orden (default 0)
    @DecimalMin(value = "0", message = "El costo de mano de obra no puede ser negativo")
    private BigDecimal costoManoObra = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "El costo indirecto no puede ser negativo")
    private BigDecimal costoIndirecto = BigDecimal.ZERO;

    private String observaciones;

    public CompletarProduccionDTO() {}

    public BigDecimal getCostoManoObra() { return costoManoObra; }
    public void setCostoManoObra(BigDecimal costoManoObra) { this.costoManoObra = costoManoObra; }

    public BigDecimal getCostoIndirecto() { return costoIndirecto; }
    public void setCostoIndirecto(BigDecimal costoIndirecto) { this.costoIndirecto = costoIndirecto; }

    public Integer getCantidadProducida() { return cantidadProducida; }
    public void setCantidadProducida(Integer cantidadProducida) { this.cantidadProducida = cantidadProducida; }

    public List<ConsumoRealItemDTO> getConsumosReales() { return consumosReales; }
    public void setConsumosReales(List<ConsumoRealItemDTO> consumosReales) { this.consumosReales = consumosReales; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}
