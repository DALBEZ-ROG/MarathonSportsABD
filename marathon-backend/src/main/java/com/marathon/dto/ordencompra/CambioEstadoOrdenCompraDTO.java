package com.marathon.dto.ordencompra;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class CambioEstadoOrdenCompraDTO {

    @NotNull(message = "El estado es obligatorio")
    @Pattern(regexp = "borrador|pendiente_aprobacion|aprobada|rechazada|recibida_parcial|recibida_completa|cancelada",
             message = "Estado inválido")
    private String estado;

    private String observacion;

    public CambioEstadoOrdenCompraDTO() {}

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
