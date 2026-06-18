package com.marathon.dto.inventario;

import java.time.LocalDateTime;

public class MovimientoResponseDTO {

    private Integer idMovimiento;
    private Integer idProducto;
    private String productoNombre;
    private Integer idBodega;
    private String bodegaNombre;
    private String tipoMovimiento;
    private Integer cantidad;
    private Integer idUsuario;
    private String usuarioNombre;
    private LocalDateTime fecha;

    public MovimientoResponseDTO() {}

    public Integer getIdMovimiento() { return idMovimiento; }
    public void setIdMovimiento(Integer idMovimiento) { this.idMovimiento = idMovimiento; }

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }

    public String getProductoNombre() { return productoNombre; }
    public void setProductoNombre(String productoNombre) { this.productoNombre = productoNombre; }

    public Integer getIdBodega() { return idBodega; }
    public void setIdBodega(Integer idBodega) { this.idBodega = idBodega; }

    public String getBodegaNombre() { return bodegaNombre; }
    public void setBodegaNombre(String bodegaNombre) { this.bodegaNombre = bodegaNombre; }

    public String getTipoMovimiento() { return tipoMovimiento; }
    public void setTipoMovimiento(String tipoMovimiento) { this.tipoMovimiento = tipoMovimiento; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
