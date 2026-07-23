package com.marathon.dto.devolucion;

import java.time.LocalDateTime;
import java.util.List;

public class SolicitudDevolucionResponseDTO {

    private Integer idSolicitud;
    private Integer idPedido;
    private String clienteNombre;
    private String motivo;
    private String descripcion;
    private String estado;
    private LocalDateTime fechaSolicitud;
    private LocalDateTime fechaInspeccion;
    private String inspectorNombre;
    private String registradoPor;
    private List<DetalleDTO> detalles;
    private ReembolsoDTO reembolso;

    public SolicitudDevolucionResponseDTO() {}

    public Integer getIdSolicitud() { return idSolicitud; }
    public void setIdSolicitud(Integer idSolicitud) { this.idSolicitud = idSolicitud; }
    public Integer getIdPedido() { return idPedido; }
    public void setIdPedido(Integer idPedido) { this.idPedido = idPedido; }
    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(LocalDateTime fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }
    public LocalDateTime getFechaInspeccion() { return fechaInspeccion; }
    public void setFechaInspeccion(LocalDateTime fechaInspeccion) { this.fechaInspeccion = fechaInspeccion; }
    public String getInspectorNombre() { return inspectorNombre; }
    public void setInspectorNombre(String inspectorNombre) { this.inspectorNombre = inspectorNombre; }
    public String getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(String registradoPor) { this.registradoPor = registradoPor; }
    public List<DetalleDTO> getDetalles() { return detalles; }
    public void setDetalles(List<DetalleDTO> detalles) { this.detalles = detalles; }
    public ReembolsoDTO getReembolso() { return reembolso; }
    public void setReembolso(ReembolsoDTO reembolso) { this.reembolso = reembolso; }

    public static class DetalleDTO {
        private Integer idDetalleSd;
        private Integer idDetallePedido;
        private String productoNombre;
        private Integer cantidadOriginal;
        private Integer cantidadDevuelta;
        private String resultadoInspeccion;
        private String observacionInspeccion;

        public DetalleDTO() {}
        public Integer getIdDetalleSd() { return idDetalleSd; }
        public void setIdDetalleSd(Integer idDetalleSd) { this.idDetalleSd = idDetalleSd; }
        public Integer getIdDetallePedido() { return idDetallePedido; }
        public void setIdDetallePedido(Integer idDetallePedido) { this.idDetallePedido = idDetallePedido; }
        public String getProductoNombre() { return productoNombre; }
        public void setProductoNombre(String productoNombre) { this.productoNombre = productoNombre; }
        public Integer getCantidadOriginal() { return cantidadOriginal; }
        public void setCantidadOriginal(Integer cantidadOriginal) { this.cantidadOriginal = cantidadOriginal; }
        public Integer getCantidadDevuelta() { return cantidadDevuelta; }
        public void setCantidadDevuelta(Integer cantidadDevuelta) { this.cantidadDevuelta = cantidadDevuelta; }
        public String getResultadoInspeccion() { return resultadoInspeccion; }
        public void setResultadoInspeccion(String resultadoInspeccion) { this.resultadoInspeccion = resultadoInspeccion; }
        public String getObservacionInspeccion() { return observacionInspeccion; }
        public void setObservacionInspeccion(String observacionInspeccion) { this.observacionInspeccion = observacionInspeccion; }
    }

    public static class ReembolsoDTO {
        private Integer idReembolso;
        private java.math.BigDecimal monto;
        private String metodo;
        private LocalDateTime fechaReembolso;
        private String observaciones;

        public ReembolsoDTO() {}
        public Integer getIdReembolso() { return idReembolso; }
        public void setIdReembolso(Integer idReembolso) { this.idReembolso = idReembolso; }
        public java.math.BigDecimal getMonto() { return monto; }
        public void setMonto(java.math.BigDecimal monto) { this.monto = monto; }
        public String getMetodo() { return metodo; }
        public void setMetodo(String metodo) { this.metodo = metodo; }
        public LocalDateTime getFechaReembolso() { return fechaReembolso; }
        public void setFechaReembolso(LocalDateTime fechaReembolso) { this.fechaReembolso = fechaReembolso; }
        public String getObservaciones() { return observaciones; }
        public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    }
}
