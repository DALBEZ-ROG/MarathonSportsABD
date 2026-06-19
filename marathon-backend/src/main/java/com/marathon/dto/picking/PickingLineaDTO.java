package com.marathon.dto.picking;

public class PickingLineaDTO {

    private Integer idDetalle;
    private Integer idProducto;
    private String productoNombre;
    private String productoDescripcion;
    private String unidadMedidaNombre;
    private Integer cantidad;
    private Integer cantidadRecogida;
    private Boolean pickingCompletado;
    private Integer pendiente;

    public PickingLineaDTO() {}

    public Integer getIdDetalle() { return idDetalle; }
    public void setIdDetalle(Integer idDetalle) { this.idDetalle = idDetalle; }

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }

    public String getProductoNombre() { return productoNombre; }
    public void setProductoNombre(String productoNombre) { this.productoNombre = productoNombre; }

    public String getProductoDescripcion() { return productoDescripcion; }
    public void setProductoDescripcion(String productoDescripcion) { this.productoDescripcion = productoDescripcion; }

    public String getUnidadMedidaNombre() { return unidadMedidaNombre; }
    public void setUnidadMedidaNombre(String unidadMedidaNombre) { this.unidadMedidaNombre = unidadMedidaNombre; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public Integer getCantidadRecogida() { return cantidadRecogida; }
    public void setCantidadRecogida(Integer cantidadRecogida) { this.cantidadRecogida = cantidadRecogida; }

    public Boolean getPickingCompletado() { return pickingCompletado; }
    public void setPickingCompletado(Boolean pickingCompletado) { this.pickingCompletado = pickingCompletado; }

    public Integer getPendiente() { return pendiente; }
    public void setPendiente(Integer pendiente) { this.pendiente = pendiente; }
}
