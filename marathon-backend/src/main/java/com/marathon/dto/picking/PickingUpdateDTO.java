package com.marathon.dto.picking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class PickingUpdateDTO {

    @NotNull
    private Integer idDetalle;

    @NotNull
    @Min(0)
    private Integer cantidadRecogida;

    @NotNull
    private Boolean pickingCompletado;

    /** Bodega de la que se recoge. Obligatoria si cantidadRecogida > 0 (L4). */
    private Integer idBodega;

    public PickingUpdateDTO() {}

    public Integer getIdDetalle() { return idDetalle; }
    public void setIdDetalle(Integer idDetalle) { this.idDetalle = idDetalle; }

    public Integer getCantidadRecogida() { return cantidadRecogida; }
    public void setCantidadRecogida(Integer cantidadRecogida) { this.cantidadRecogida = cantidadRecogida; }

    public Integer getIdBodega() { return idBodega; }
    public void setIdBodega(Integer idBodega) { this.idBodega = idBodega; }

    public Boolean getPickingCompletado() { return pickingCompletado; }
    public void setPickingCompletado(Boolean pickingCompletado) { this.pickingCompletado = pickingCompletado; }
}
