package com.marathon.dto.empaque;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Lo que hace falta para confirmar un empaque.
 *
 * <p><b>F84 — dos campos menos, y por dos razones distintas.</b>
 *
 * <p>El transportista <b>era su nombre</b> ({@code String}) y ahora es su clave
 * ({@code idTransportista}). La pantalla ya elegía de una lista desde la F77,
 * pero devolvía el texto elegido: si mañana el catálogo corrige «Servientrega»,
 * los pedidos guardados se quedan con el nombre viejo. Se manda la clave.
 *
 * <p>La región de destino <b>ya no se pide</b>. Se pedía como obligatoria, y era
 * un dato que el servidor ya tenía: el pedido conoce a su cliente, el cliente su
 * ciudad, y la ciudad su región. Pedirlo era darle a quien empaca la oportunidad
 * de contradecir a la base sin querer.
 */
public class EmpaqueRequestDTO {

    @NotBlank(message = "El número HU es obligatorio")
    @Size(max = 50, message = "El número HU no puede superar los 50 caracteres")
    private String numeroHu;

    @NotNull(message = "Hay que elegir un transportista de la lista")
    private Integer idTransportista;

    private String observacion;

    public EmpaqueRequestDTO() {}

    public String getNumeroHu() { return numeroHu; }
    public void setNumeroHu(String numeroHu) { this.numeroHu = numeroHu; }

    public Integer getIdTransportista() { return idTransportista; }
    public void setIdTransportista(Integer idTransportista) { this.idTransportista = idTransportista; }

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
