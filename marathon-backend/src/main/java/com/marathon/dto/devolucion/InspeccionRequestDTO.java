package com.marathon.dto.devolucion;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class InspeccionRequestDTO {

    @NotNull(message = "La bodega destino es obligatoria")
    private Integer idBodega;

    @NotEmpty(message = "Debe inspeccionar al menos una linea")
    @Valid
    private List<InspeccionDetalleDTO> items;

    public InspeccionRequestDTO() {}

    public Integer getIdBodega() { return idBodega; }
    public void setIdBodega(Integer idBodega) { this.idBodega = idBodega; }

    public List<InspeccionDetalleDTO> getItems() { return items; }
    public void setItems(List<InspeccionDetalleDTO> items) { this.items = items; }
}
