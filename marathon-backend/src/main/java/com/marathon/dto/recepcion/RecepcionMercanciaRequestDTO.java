package com.marathon.dto.recepcion;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class RecepcionMercanciaRequestDTO {

    @NotNull(message = "La orden de compra es obligatoria")
    private Integer idOrdenCompra;

    // Bodega destino: solo aplica a líneas tipo producto. Las líneas de
    // materia prima ignoran este campo (su stock es global sin bodega).
    @NotNull(message = "La bodega destino es obligatoria")
    private Integer idBodega;

    private String numeroGuiaRemision;

    private String observaciones;

    @NotEmpty(message = "La recepción debe tener al menos una línea")
    @Valid
    private List<RecepcionDetalleItemDTO> detalles;

    public RecepcionMercanciaRequestDTO() {}

    public Integer getIdOrdenCompra() { return idOrdenCompra; }
    public void setIdOrdenCompra(Integer idOrdenCompra) { this.idOrdenCompra = idOrdenCompra; }

    public Integer getIdBodega() { return idBodega; }
    public void setIdBodega(Integer idBodega) { this.idBodega = idBodega; }

    public String getNumeroGuiaRemision() { return numeroGuiaRemision; }
    public void setNumeroGuiaRemision(String numeroGuiaRemision) { this.numeroGuiaRemision = numeroGuiaRemision; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public List<RecepcionDetalleItemDTO> getDetalles() { return detalles; }
    public void setDetalles(List<RecepcionDetalleItemDTO> detalles) { this.detalles = detalles; }
}
