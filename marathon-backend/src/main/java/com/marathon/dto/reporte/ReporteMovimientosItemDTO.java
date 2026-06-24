package com.marathon.dto.reporte;

import java.time.LocalDateTime;

public class ReporteMovimientosItemDTO {

    private Integer idMovimiento;
    private String tipoMovimiento;
    private Integer cantidad;
    private LocalDateTime fecha;
    private String observacion;
    private String producto;
    private String bodega;
    private String bodegaDestino;
    private String usuario;

    public ReporteMovimientosItemDTO() {}

    public ReporteMovimientosItemDTO(Integer idMovimiento, String tipoMovimiento, Integer cantidad,
                                     LocalDateTime fecha, String observacion, String producto, String bodega,
                                     String bodegaDestino, String usuario) {
        this.idMovimiento = idMovimiento;
        this.tipoMovimiento = tipoMovimiento;
        this.cantidad = cantidad;
        this.fecha = fecha;
        this.observacion = observacion;
        this.producto = producto;
        this.bodega = bodega;
        this.bodegaDestino = bodegaDestino;
        this.usuario = usuario;
    }

    public Integer getIdMovimiento() { return idMovimiento; }
    public void setIdMovimiento(Integer idMovimiento) { this.idMovimiento = idMovimiento; }

    public String getTipoMovimiento() { return tipoMovimiento; }
    public void setTipoMovimiento(String tipoMovimiento) { this.tipoMovimiento = tipoMovimiento; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }

    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }

    public String getBodega() { return bodega; }
    public void setBodega(String bodega) { this.bodega = bodega; }

    public String getBodegaDestino() { return bodegaDestino; }
    public void setBodegaDestino(String bodegaDestino) { this.bodegaDestino = bodegaDestino; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
}
