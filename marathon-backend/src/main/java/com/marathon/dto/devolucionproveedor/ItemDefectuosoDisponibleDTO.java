package com.marathon.dto.devolucionproveedor;

import java.time.LocalDateTime;

public class ItemDefectuosoDisponibleDTO {
    private String origen;
    private Integer idOrigenDetalle;
    private Integer idProducto;
    private String nombreProducto;
    private Integer cantidad;
    private Integer idProveedorSugerido;
    private String nombreProveedorSugerido;
    private LocalDateTime fechaOrigen;
    private String referenciaOrigen;

    public ItemDefectuosoDisponibleDTO() {}

    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
    public Integer getIdOrigenDetalle() { return idOrigenDetalle; }
    public void setIdOrigenDetalle(Integer id) { this.idOrigenDetalle = id; }
    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer id) { this.idProducto = id; }
    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String n) { this.nombreProducto = n; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer c) { this.cantidad = c; }
    public Integer getIdProveedorSugerido() { return idProveedorSugerido; }
    public void setIdProveedorSugerido(Integer id) { this.idProveedorSugerido = id; }
    public String getNombreProveedorSugerido() { return nombreProveedorSugerido; }
    public void setNombreProveedorSugerido(String n) { this.nombreProveedorSugerido = n; }
    public LocalDateTime getFechaOrigen() { return fechaOrigen; }
    public void setFechaOrigen(LocalDateTime f) { this.fechaOrigen = f; }
    public String getReferenciaOrigen() { return referenciaOrigen; }
    public void setReferenciaOrigen(String r) { this.referenciaOrigen = r; }
}
