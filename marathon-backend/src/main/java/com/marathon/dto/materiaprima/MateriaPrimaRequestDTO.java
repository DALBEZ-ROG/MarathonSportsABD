package com.marathon.dto.materiaprima;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class MateriaPrimaRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
    private String nombre;

    private String descripcion;

    @NotNull(message = "La unidad de medida es obligatoria")
    private Integer idUnidadMedida;

    @Pattern(regexp = "activo|inactivo", message = "El estado debe ser 'activo' o 'inactivo'")
    private String estado;

    public MateriaPrimaRequestDTO() {}

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Integer getIdUnidadMedida() { return idUnidadMedida; }
    public void setIdUnidadMedida(Integer idUnidadMedida) { this.idUnidadMedida = idUnidadMedida; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
