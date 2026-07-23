package com.marathon.dto.produccion;

import java.util.List;

public class VerificacionDisponibilidadDTO {

    private Boolean puedeProducir;
    private List<DisponibilidadMaterialDTO> materiales;
    private Integer cantidadMaximaProducible;

    public VerificacionDisponibilidadDTO() {}

    public Boolean getPuedeProducir() { return puedeProducir; }
    public void setPuedeProducir(Boolean puedeProducir) { this.puedeProducir = puedeProducir; }

    public List<DisponibilidadMaterialDTO> getMateriales() { return materiales; }
    public void setMateriales(List<DisponibilidadMaterialDTO> materiales) { this.materiales = materiales; }

    public Integer getCantidadMaximaProducible() { return cantidadMaximaProducible; }
    public void setCantidadMaximaProducible(Integer cantidadMaximaProducible) { this.cantidadMaximaProducible = cantidadMaximaProducible; }
}
