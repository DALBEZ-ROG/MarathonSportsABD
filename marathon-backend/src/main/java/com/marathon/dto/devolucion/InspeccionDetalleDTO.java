package com.marathon.dto.devolucion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class InspeccionDetalleDTO {

    @NotNull(message = "El detalle de la solicitud es obligatorio")
    private Integer idDetalleSd;

    @NotNull(message = "El resultado de inspeccion es obligatorio")
    @Pattern(regexp = "apto_reventa|defectuoso|rechazado",
             message = "Resultado invalido. Opciones: apto_reventa, defectuoso, rechazado")
    private String resultadoInspeccion;

    private String observacionInspeccion;

    public InspeccionDetalleDTO() {}

    public Integer getIdDetalleSd() { return idDetalleSd; }
    public void setIdDetalleSd(Integer idDetalleSd) { this.idDetalleSd = idDetalleSd; }

    public String getResultadoInspeccion() { return resultadoInspeccion; }
    public void setResultadoInspeccion(String resultadoInspeccion) { this.resultadoInspeccion = resultadoInspeccion; }

    public String getObservacionInspeccion() { return observacionInspeccion; }
    public void setObservacionInspeccion(String observacionInspeccion) { this.observacionInspeccion = observacionInspeccion; }
}
