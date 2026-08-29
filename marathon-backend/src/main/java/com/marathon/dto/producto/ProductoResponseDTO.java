package com.marathon.dto.producto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ProductoResponseDTO {

    private Integer idProducto;
    private String codigo;
    private String nombre;
    private String descripcion;
    private BigDecimal precioCompra;
    private BigDecimal precioVenta;
    private Integer idCategoria;
    private String categoriaNombre;
    private Integer idUnidadMedida;
    private String unidadMedidaNombre;
    private String estado;
    private String origen;
    private Boolean tieneBom;
    private LocalDateTime createdAt;
    private List<ProveedorSimpleDTO> proveedores;

    public ProductoResponseDTO() {}

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public BigDecimal getPrecioCompra() { return precioCompra; }
    public void setPrecioCompra(BigDecimal precioCompra) { this.precioCompra = precioCompra; }

    public BigDecimal getPrecioVenta() { return precioVenta; }
    public void setPrecioVenta(BigDecimal precioVenta) { this.precioVenta = precioVenta; }

    public Integer getIdCategoria() { return idCategoria; }
    public void setIdCategoria(Integer idCategoria) { this.idCategoria = idCategoria; }

    public String getCategoriaNombre() { return categoriaNombre; }
    public void setCategoriaNombre(String categoriaNombre) { this.categoriaNombre = categoriaNombre; }

    public Integer getIdUnidadMedida() { return idUnidadMedida; }
    public void setIdUnidadMedida(Integer idUnidadMedida) { this.idUnidadMedida = idUnidadMedida; }

    public String getUnidadMedidaNombre() { return unidadMedidaNombre; }
    public void setUnidadMedidaNombre(String unidadMedidaNombre) { this.unidadMedidaNombre = unidadMedidaNombre; }


    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }

    public Boolean getTieneBom() { return tieneBom; }
    public void setTieneBom(Boolean tieneBom) { this.tieneBom = tieneBom; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<ProveedorSimpleDTO> getProveedores() { return proveedores; }
    public void setProveedores(List<ProveedorSimpleDTO> proveedores) { this.proveedores = proveedores; }

    public static class ProveedorSimpleDTO {
        private Integer idProveedor;
        private String nombre;
        private String ruc;

        public ProveedorSimpleDTO() {}

        public ProveedorSimpleDTO(Integer idProveedor, String nombre, String ruc) {
            this.idProveedor = idProveedor;
            this.nombre = nombre;
            this.ruc = ruc;
        }

        public Integer getIdProveedor() { return idProveedor; }
        public void setIdProveedor(Integer idProveedor) { this.idProveedor = idProveedor; }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public String getRuc() { return ruc; }
        public void setRuc(String ruc) { this.ruc = ruc; }
    }
}
