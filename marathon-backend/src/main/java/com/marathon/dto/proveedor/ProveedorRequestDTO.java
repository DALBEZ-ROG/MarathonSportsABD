package com.marathon.dto.proveedor;

import jakarta.validation.constraints.NotBlank;

public class ProveedorRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    private String ruc;
    private String direccion;
    private String telefono;
    private String email;
    private Integer idCiudad;
    private String estado;

    public ProveedorRequestDTO() {}

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getRuc() { return ruc; }
    public void setRuc(String ruc) { this.ruc = ruc; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Integer getIdCiudad() { return idCiudad; }
    public void setIdCiudad(Integer idCiudad) { this.idCiudad = idCiudad; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
