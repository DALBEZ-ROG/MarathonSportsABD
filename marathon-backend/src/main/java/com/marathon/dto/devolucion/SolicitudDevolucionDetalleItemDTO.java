package com.marathon.dto.devolucion;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class SolicitudDevolucionDetalleItemDTO {

    @NotNull(message = "El detalle del pedido es obligatorio")
    private Integer idDetallePedido;

    @NotNull(message = "La cantidad a devolver es obligatoria")
    @Min(value = 1, message = "La cantidad a devolver debe ser mayor a 0")
    private Integer cantidadDevuelta;

    public SolicitudDevolucionDetalleItemDTO() {}

    public Integer getIdDetallePedido() { return idDetallePedido; }
    public void setIdDetallePedido(Integer idDetallePedido) { this.idDetallePedido = idDetallePedido; }

    public Integer getCantidadDevuelta() { return cantidadDevuelta; }
    public void setCantidadDevuelta(Integer cantidadDevuelta) { this.cantidadDevuelta = cantidadDevuelta; }
}
