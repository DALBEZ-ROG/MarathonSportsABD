package com.marathon.dto.inventario;

import java.time.LocalDateTime;

public class HistorialResponseDTO {

    private Integer idHistorial;
    private Integer cantidadAnterior;
    private Integer cantidadNueva;
    private LocalDateTime fechaCambio;
    private String tipoOperacion;

    public HistorialResponseDTO() {}

    public Integer getIdHistorial() { return idHistorial; }
    public void setIdHistorial(Integer idHistorial) { this.idHistorial = idHistorial; }

    public Integer getCantidadAnterior() { return cantidadAnterior; }
    public void setCantidadAnterior(Integer cantidadAnterior) { this.cantidadAnterior = cantidadAnterior; }

    public Integer getCantidadNueva() { return cantidadNueva; }
    public void setCantidadNueva(Integer cantidadNueva) { this.cantidadNueva = cantidadNueva; }

    public LocalDateTime getFechaCambio() { return fechaCambio; }
    public void setFechaCambio(LocalDateTime fechaCambio) { this.fechaCambio = fechaCambio; }

    public String getTipoOperacion() { return tipoOperacion; }
    public void setTipoOperacion(String tipoOperacion) { this.tipoOperacion = tipoOperacion; }
}
