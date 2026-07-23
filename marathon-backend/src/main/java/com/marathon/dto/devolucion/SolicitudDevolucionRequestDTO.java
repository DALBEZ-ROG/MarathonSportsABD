package com.marathon.dto.devolucion;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class SolicitudDevolucionRequestDTO {

    @NotNull(message = "El pedido es obligatorio")
    private Integer idPedido;

    @NotNull(message = "El motivo es obligatorio")
    @Pattern(regexp = "producto_defectuoso|talla_incorrecta|no_esperado|cambio_opinion|producto_incompleto|otro",
             message = "Motivo invalido")
    private String motivo;

    private String descripcion;

    @NotEmpty(message = "La solicitud debe tener al menos una linea")
    @Valid
    private List<SolicitudDevolucionDetalleItemDTO> detalles;

    public SolicitudDevolucionRequestDTO() {}

    public Integer getIdPedido() { return idPedido; }
    public void setIdPedido(Integer idPedido) { this.idPedido = idPedido; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public List<SolicitudDevolucionDetalleItemDTO> getDetalles() { return detalles; }
    public void setDetalles(List<SolicitudDevolucionDetalleItemDTO> detalles) { this.detalles = detalles; }
}
