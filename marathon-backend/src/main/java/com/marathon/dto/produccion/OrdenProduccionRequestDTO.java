package com.marathon.dto.produccion;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class OrdenProduccionRequestDTO {

    @NotNull(message = "El producto es obligatorio")
    private Integer idProducto;

    @NotNull(message = "La bodega destino es obligatoria")
    private Integer idBodegaDestino;

    @NotNull(message = "La cantidad planificada es obligatoria")
    @Min(value = 1, message = "La cantidad planificada debe ser al menos 1")
    private Integer cantidadPlanificada;

    private String observaciones;

    public OrdenProduccionRequestDTO() {}

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }

    public Integer getIdBodegaDestino() { return idBodegaDestino; }
    public void setIdBodegaDestino(Integer idBodegaDestino) { this.idBodegaDestino = idBodegaDestino; }

    public Integer getCantidadPlanificada() { return cantidadPlanificada; }
    public void setCantidadPlanificada(Integer cantidadPlanificada) { this.cantidadPlanificada = cantidadPlanificada; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}
