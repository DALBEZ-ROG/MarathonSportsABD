package com.marathon.dto.pedido;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CambioEstadoDTO {

    @NotBlank(message = "El estado es obligatorio")
    @Pattern(regexp = "pendiente|procesado|enviado|entregado|anulado", message = "Estado no válido")
    private String estado;

    private String observacion;

    public CambioEstadoDTO() {}

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
