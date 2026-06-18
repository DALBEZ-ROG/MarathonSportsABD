package com.marathon.dto.permiso;

public class PermisoResponseDTO {

    private Integer idPermiso;
    private String modulo;
    private String accion;
    private String descripcion;

    public PermisoResponseDTO() {}

    public PermisoResponseDTO(Integer idPermiso, String modulo, String accion, String descripcion) {
        this.idPermiso = idPermiso;
        this.modulo = modulo;
        this.accion = accion;
        this.descripcion = descripcion;
    }

    public Integer getIdPermiso() { return idPermiso; }
    public void setIdPermiso(Integer idPermiso) { this.idPermiso = idPermiso; }
    public String getModulo() { return modulo; }
    public void setModulo(String modulo) { this.modulo = modulo; }
    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
