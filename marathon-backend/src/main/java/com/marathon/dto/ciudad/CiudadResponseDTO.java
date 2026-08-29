package com.marathon.dto.ciudad;

public class CiudadResponseDTO {

    private Integer idCiudad;
    private String nombre;
    private String region;
    private String estado;

    public CiudadResponseDTO() {}

    public CiudadResponseDTO(Integer idCiudad, String nombre, String region, String estado) {
        this.idCiudad = idCiudad;
        this.nombre = nombre;
        this.region = region;
        this.estado = estado;
    }

    public Integer getIdCiudad() { return idCiudad; }
    public void setIdCiudad(Integer idCiudad) { this.idCiudad = idCiudad; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
