package com.marathon.dto.devolucionproveedor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class DevolucionProveedorResponseDTO {
    private Integer idDevolucionProv;
    private String proveedorNombre;
    private Integer idProveedor;
    private String estado;
    private String tipoResolucion;
    private BigDecimal montoReembolso;
    private String observaciones;
    private LocalDateTime fechaDevolucion;
    private String registradoPor;
    private List<DetalleDTO> detalles;

    public DevolucionProveedorResponseDTO() {}

    public Integer getIdDevolucionProv() { return idDevolucionProv; }
    public void setIdDevolucionProv(Integer id) { this.idDevolucionProv = id; }
    public String getProveedorNombre() { return proveedorNombre; }
    public void setProveedorNombre(String n) { this.proveedorNombre = n; }
    public Integer getIdProveedor() { return idProveedor; }
    public void setIdProveedor(Integer id) { this.idProveedor = id; }
    public String getEstado() { return estado; }
    public void setEstado(String e) { this.estado = e; }
    public String getTipoResolucion() { return tipoResolucion; }
    public void setTipoResolucion(String t) { this.tipoResolucion = t; }
    public BigDecimal getMontoReembolso() { return montoReembolso; }
    public void setMontoReembolso(BigDecimal m) { this.montoReembolso = m; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String o) { this.observaciones = o; }
    public LocalDateTime getFechaDevolucion() { return fechaDevolucion; }
    public void setFechaDevolucion(LocalDateTime f) { this.fechaDevolucion = f; }
    public String getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(String r) { this.registradoPor = r; }
    public List<DetalleDTO> getDetalles() { return detalles; }
    public void setDetalles(List<DetalleDTO> d) { this.detalles = d; }

    public static class DetalleDTO {
        private Integer idDetalleDp;
        private String origen;
        private String productoNombre;
        private Integer cantidad;
        private String motivo;
        private String referenciaOrigen;

        public DetalleDTO() {}
        public Integer getIdDetalleDp() { return idDetalleDp; }
        public void setIdDetalleDp(Integer id) { this.idDetalleDp = id; }
        public String getOrigen() { return origen; }
        public void setOrigen(String o) { this.origen = o; }
        public String getProductoNombre() { return productoNombre; }
        public void setProductoNombre(String n) { this.productoNombre = n; }
        public Integer getCantidad() { return cantidad; }
        public void setCantidad(Integer c) { this.cantidad = c; }
        public String getMotivo() { return motivo; }
        public void setMotivo(String m) { this.motivo = m; }
        public String getReferenciaOrigen() { return referenciaOrigen; }
        public void setReferenciaOrigen(String r) { this.referenciaOrigen = r; }
    }
}
