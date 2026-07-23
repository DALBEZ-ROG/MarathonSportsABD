package com.marathon.dto.devolucionproveedor;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class DevolucionProveedorRequestDTO {

    @NotNull(message = "El proveedor es obligatorio")
    private Integer idProveedor;

    private String observaciones;

    @NotEmpty(message = "La devolucion debe tener al menos una linea")
    @Valid
    private List<DevolucionProveedorItemDTO> items;

    public DevolucionProveedorRequestDTO() {}

    public Integer getIdProveedor() { return idProveedor; }
    public void setIdProveedor(Integer id) { this.idProveedor = id; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String o) { this.observaciones = o; }
    public List<DevolucionProveedorItemDTO> getItems() { return items; }
    public void setItems(List<DevolucionProveedorItemDTO> items) { this.items = items; }
}
