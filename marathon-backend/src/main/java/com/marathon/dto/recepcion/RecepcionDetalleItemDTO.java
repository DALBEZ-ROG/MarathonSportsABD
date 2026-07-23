package com.marathon.dto.recepcion;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class RecepcionDetalleItemDTO {

    @NotNull(message = "El detalle de la orden de compra es obligatorio")
    private Integer idDetalleOc;

    @NotNull(message = "La cantidad recibida es obligatoria")
    @Min(value = 1, message = "La cantidad recibida debe ser mayor a 0")
    private Integer cantidadRecibidaAhora;

    @Min(value = 0, message = "La cantidad defectuosa no puede ser negativa")
    private Integer cantidadDefectuosa = 0;

    private String observacion;

    public RecepcionDetalleItemDTO() {}

    public Integer getIdDetalleOc() { return idDetalleOc; }
    public void setIdDetalleOc(Integer idDetalleOc) { this.idDetalleOc = idDetalleOc; }

    public Integer getCantidadRecibidaAhora() { return cantidadRecibidaAhora; }
    public void setCantidadRecibidaAhora(Integer cantidadRecibidaAhora) { this.cantidadRecibidaAhora = cantidadRecibidaAhora; }

    public Integer getCantidadDefectuosa() { return cantidadDefectuosa; }
    public void setCantidadDefectuosa(Integer cantidadDefectuosa) { this.cantidadDefectuosa = cantidadDefectuosa; }

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
