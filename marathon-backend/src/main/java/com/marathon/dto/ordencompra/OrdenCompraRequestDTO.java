package com.marathon.dto.ordencompra;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class OrdenCompraRequestDTO {

    @NotNull(message = "El proveedor es obligatorio")
    private Integer idProveedor;

    private String observaciones;

    @NotEmpty(message = "La orden de compra debe tener al menos una línea")
    @Valid
    private List<OrdenCompraDetalleItemDTO> detalles;

    public OrdenCompraRequestDTO() {}

    public Integer getIdProveedor() { return idProveedor; }
    public void setIdProveedor(Integer idProveedor) { this.idProveedor = idProveedor; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public List<OrdenCompraDetalleItemDTO> getDetalles() { return detalles; }
    public void setDetalles(List<OrdenCompraDetalleItemDTO> detalles) { this.detalles = detalles; }
}
