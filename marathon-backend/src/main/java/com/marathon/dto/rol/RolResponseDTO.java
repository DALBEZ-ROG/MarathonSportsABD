package com.marathon.dto.rol;

import com.marathon.dto.permiso.PermisoResponseDTO;
import java.time.LocalDateTime;
import java.util.List;

public class RolResponseDTO {

    private Integer idRol;
    private String nombre;
    private String descripcion;
    private LocalDateTime createdAt;
    private List<PermisoResponseDTO> permisos;

    public Integer getIdRol() { return idRol; }
    public void setIdRol(Integer idRol) { this.idRol = idRol; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<PermisoResponseDTO> getPermisos() { return permisos; }
    public void setPermisos(List<PermisoResponseDTO> permisos) { this.permisos = permisos; }
}
