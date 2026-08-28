package com.marathon.dto.ordencompra;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrdenCompraResponseDTO {

    private Integer idOrdenCompra;
    private LocalDateTime fechaOrden;
    private LocalDateTime fechaAprobacion;
    private String estado;
    private BigDecimal total;
    private String observaciones;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private ProveedorSimpleDTO proveedor;
    private UsuarioSimpleDTO usuarioSolicitante;
    private UsuarioSimpleDTO usuarioAprobador;

    /** F69: true = el proveedor repone por una reclamacion. NO se factura. */
    private Boolean esReposicion;
    /** La devolucion que la origino, si es reposicion. */
    private Integer idDevolucionProv;

    private List<DetalleDTO> detalles;

    public OrdenCompraResponseDTO() {}

    public Boolean getEsReposicion() { return esReposicion; }
    public void setEsReposicion(Boolean v) { this.esReposicion = v; }
    public Integer getIdDevolucionProv() { return idDevolucionProv; }
    public void setIdDevolucionProv(Integer v) { this.idDevolucionProv = v; }

    public Integer getIdOrdenCompra() { return idOrdenCompra; }
    public void setIdOrdenCompra(Integer idOrdenCompra) { this.idOrdenCompra = idOrdenCompra; }

    public LocalDateTime getFechaOrden() { return fechaOrden; }
    public void setFechaOrden(LocalDateTime fechaOrden) { this.fechaOrden = fechaOrden; }

    public LocalDateTime getFechaAprobacion() { return fechaAprobacion; }
    public void setFechaAprobacion(LocalDateTime fechaAprobacion) { this.fechaAprobacion = fechaAprobacion; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public ProveedorSimpleDTO getProveedor() { return proveedor; }
    public void setProveedor(ProveedorSimpleDTO proveedor) { this.proveedor = proveedor; }

    public UsuarioSimpleDTO getUsuarioSolicitante() { return usuarioSolicitante; }
    public void setUsuarioSolicitante(UsuarioSimpleDTO usuarioSolicitante) { this.usuarioSolicitante = usuarioSolicitante; }

    public UsuarioSimpleDTO getUsuarioAprobador() { return usuarioAprobador; }
    public void setUsuarioAprobador(UsuarioSimpleDTO usuarioAprobador) { this.usuarioAprobador = usuarioAprobador; }

    public List<DetalleDTO> getDetalles() { return detalles; }
    public void setDetalles(List<DetalleDTO> detalles) { this.detalles = detalles; }

    // ---- Objetos anidados ----

    public static class ProveedorSimpleDTO {
        private Integer idProveedor;
        private String nombre;

        public ProveedorSimpleDTO() {}
        public ProveedorSimpleDTO(Integer idProveedor, String nombre) {
            this.idProveedor = idProveedor;
            this.nombre = nombre;
        }

        public Integer getIdProveedor() { return idProveedor; }
        public void setIdProveedor(Integer idProveedor) { this.idProveedor = idProveedor; }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
    }

    public static class UsuarioSimpleDTO {
        private Integer idUsuario;
        private String nombre;
        private String apellido;

        public UsuarioSimpleDTO() {}
        public UsuarioSimpleDTO(Integer idUsuario, String nombre, String apellido) {
            this.idUsuario = idUsuario;
            this.nombre = nombre;
            this.apellido = apellido;
        }

        public Integer getIdUsuario() { return idUsuario; }
        public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public String getApellido() { return apellido; }
        public void setApellido(String apellido) { this.apellido = apellido; }
    }

    public static class DetalleDTO {
        private Integer idDetalleOc;
        private String tipoItem;
        private Integer idProducto;
        private Integer idMateriaPrima;
        private String itemNombre;
        private Integer cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal subtotal;
        private Integer cantidadRecibida;

        public DetalleDTO() {}

        public Integer getIdDetalleOc() { return idDetalleOc; }
        public void setIdDetalleOc(Integer idDetalleOc) { this.idDetalleOc = idDetalleOc; }

        public String getTipoItem() { return tipoItem; }
        public void setTipoItem(String tipoItem) { this.tipoItem = tipoItem; }

        public Integer getIdProducto() { return idProducto; }
        public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }

        public Integer getIdMateriaPrima() { return idMateriaPrima; }
        public void setIdMateriaPrima(Integer idMateriaPrima) { this.idMateriaPrima = idMateriaPrima; }

        public String getItemNombre() { return itemNombre; }
        public void setItemNombre(String itemNombre) { this.itemNombre = itemNombre; }

        public Integer getCantidad() { return cantidad; }
        public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

        public BigDecimal getPrecioUnitario() { return precioUnitario; }
        public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }

        public BigDecimal getSubtotal() { return subtotal; }
        public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

        public Integer getCantidadRecibida() { return cantidadRecibida; }
        public void setCantidadRecibida(Integer cantidadRecibida) { this.cantidadRecibida = cantidadRecibida; }
    }
}
