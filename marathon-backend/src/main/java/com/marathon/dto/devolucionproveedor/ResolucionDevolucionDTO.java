package com.marathon.dto.devolucionproveedor;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class ResolucionDevolucionDTO {

    @NotNull(message = "El tipo de resolucion es obligatorio")
    @Pattern(regexp = "reembolso|reposicion", message = "Tipo invalido. Opciones: reembolso, reposicion")
    private String tipoResolucion;

    private BigDecimal montoReembolso;

    private String observaciones;

    public ResolucionDevolucionDTO() {}

    public String getTipoResolucion() { return tipoResolucion; }
    public void setTipoResolucion(String t) { this.tipoResolucion = t; }
    public BigDecimal getMontoReembolso() { return montoReembolso; }
    public void setMontoReembolso(BigDecimal m) { this.montoReembolso = m; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String o) { this.observaciones = o; }
}
