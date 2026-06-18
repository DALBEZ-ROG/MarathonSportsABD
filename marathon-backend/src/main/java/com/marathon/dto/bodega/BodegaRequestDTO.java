package com.marathon.dto.bodega;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class BodegaRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    private String direccion;

    @NotNull(message = "La ciudad es obligatoria")
    private Integer idCiudad;

    private String responsable;

    private String estado;

    public BodegaRequestDTO() {}

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public Integer getIdCiudad() { return idCiudad; }
    public void setIdCiudad(Integer idCiudad) { this.idCiudad = idCiudad; }

    public String getResponsable() { return responsable; }
    public void setResponsable(String responsable) { this.responsable = responsable; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
