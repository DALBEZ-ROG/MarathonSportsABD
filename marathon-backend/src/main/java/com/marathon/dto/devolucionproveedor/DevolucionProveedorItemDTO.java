package com.marathon.dto.devolucionproveedor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class DevolucionProveedorItemDTO {

    @NotNull(message = "El origen es obligatorio")
    @Pattern(regexp = "rma_cliente|recepcion_compra", message = "Origen invalido")
    private String origen;

    @NotNull(message = "El id del origen es obligatorio")
    private Integer idOrigenDetalle;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser mayor a 0")
    private Integer cantidad;

    private String motivo;

    public DevolucionProveedorItemDTO() {}

    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
    public Integer getIdOrigenDetalle() { return idOrigenDetalle; }
    public void setIdOrigenDetalle(Integer id) { this.idOrigenDetalle = id; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer c) { this.cantidad = c; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String m) { this.motivo = m; }
}
