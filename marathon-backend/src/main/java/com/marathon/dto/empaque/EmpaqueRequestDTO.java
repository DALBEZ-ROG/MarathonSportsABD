package com.marathon.dto.empaque;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EmpaqueRequestDTO {

    @NotBlank(message = "El número HU es obligatorio")
    @Size(max = 50, message = "El número HU no puede superar los 50 caracteres")
    private String numeroHu;

    @NotBlank(message = "El transportista es obligatorio")
    @Size(max = 100, message = "El transportista no puede superar los 100 caracteres")
    private String transportista;

    @NotBlank(message = "La región de destino es obligatoria")
    @Size(max = 100, message = "La región de destino no puede superar los 100 caracteres")
    private String regionDestino;

    private String observacion;

    public EmpaqueRequestDTO() {}

    public String getNumeroHu() { return numeroHu; }
    public void setNumeroHu(String numeroHu) { this.numeroHu = numeroHu; }

    public String getTransportista() { return transportista; }
    public void setTransportista(String transportista) { this.transportista = transportista; }

    public String getRegionDestino() { return regionDestino; }
    public void setRegionDestino(String regionDestino) { this.regionDestino = regionDestino; }

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
