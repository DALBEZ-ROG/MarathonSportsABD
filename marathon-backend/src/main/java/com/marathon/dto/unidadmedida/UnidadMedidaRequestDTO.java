package com.marathon.dto.unidadmedida;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UnidadMedidaRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 50, message = "El nombre no puede exceder 50 caracteres")
    private String nombre;

    @NotBlank(message = "La abreviatura es obligatoria")
    @Size(max = 10, message = "La abreviatura no puede exceder 10 caracteres")
    private String abreviatura;

    public UnidadMedidaRequestDTO() {}

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getAbreviatura() { return abreviatura; }
    public void setAbreviatura(String abreviatura) { this.abreviatura = abreviatura; }
}
