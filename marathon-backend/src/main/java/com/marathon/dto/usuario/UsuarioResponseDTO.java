package com.marathon.dto.usuario;

import com.marathon.dto.rol.RolResponseDTO;
import java.time.LocalDateTime;
import java.util.List;

public class UsuarioResponseDTO {

    private Integer idUsuario;
    private String nombre;
    private String apellido;
    private String correo;
    private String estado;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<RolResponseDTO> roles;

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<RolResponseDTO> getRoles() { return roles; }
    public void setRoles(List<RolResponseDTO> roles) { this.roles = roles; }
}
