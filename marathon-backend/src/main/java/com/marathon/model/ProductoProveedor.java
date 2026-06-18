package com.marathon.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "producto_proveedor")
public class ProductoProveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto_proveedor")
    private Integer idProductoProveedor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_proveedor", nullable = false)
    private Proveedor proveedor;

    @Column(name = "precio_compra")
    private BigDecimal precioCompra;

    @Column(name = "es_proveedor_principal", nullable = false)
    private Boolean esProveedorPrincipal;

    @Column(name = "estado", nullable = false)
    private String estado;

    @Column(name = "fecha_registro", insertable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    public ProductoProveedor() {}

    public Integer getIdProductoProveedor() { return idProductoProveedor; }
    public void setIdProductoProveedor(Integer idProductoProveedor) { this.idProductoProveedor = idProductoProveedor; }

    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }

    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }

    public BigDecimal getPrecioCompra() { return precioCompra; }
    public void setPrecioCompra(BigDecimal precioCompra) { this.precioCompra = precioCompra; }

    public Boolean getEsProveedorPrincipal() { return esProveedorPrincipal; }
    public void setEsProveedorPrincipal(Boolean esProveedorPrincipal) { this.esProveedorPrincipal = esProveedorPrincipal; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
