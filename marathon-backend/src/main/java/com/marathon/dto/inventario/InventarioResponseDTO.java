package com.marathon.dto.inventario;

import java.time.LocalDateTime;

public class InventarioResponseDTO {

    private Integer idInventario;
    private Integer productoId;
    private String productoNombre;
    private Integer bodegaId;
    private String bodegaNombre;
    private Integer cantidad;
    private LocalDateTime updatedAt;

    public InventarioResponseDTO() {}

    public Integer getIdInventario() { return idInventario; }
    public void setIdInventario(Integer idInventario) { this.idInventario = idInventario; }

    public Integer getProductoId() { return productoId; }
    public void setProductoId(Integer productoId) { this.productoId = productoId; }

    public String getProductoNombre() { return productoNombre; }
    public void setProductoNombre(String productoNombre) { this.productoNombre = productoNombre; }

    public Integer getBodegaId() { return bodegaId; }
    public void setBodegaId(Integer bodegaId) { this.bodegaId = bodegaId; }

    public String getBodegaNombre() { return bodegaNombre; }
    public void setBodegaNombre(String bodegaNombre) { this.bodegaNombre = bodegaNombre; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
