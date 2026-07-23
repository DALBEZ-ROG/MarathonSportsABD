package com.marathon.dto.recepcion;

import java.time.LocalDateTime;
import java.util.List;

public class RecepcionMercanciaResponseDTO {

    private Integer idRecepcion;
    private Integer idOrdenCompra;
    private String estadoOrden;
    private Integer idBodega;
    private String bodegaNombre;
    private Integer idUsuarioReceptor;
    private String receptorNombre;
    private LocalDateTime fechaRecepcion;
    private String numeroGuiaRemision;
    private String observaciones;
    private List<DetalleDTO> detalles;

    public RecepcionMercanciaResponseDTO() {}

    public Integer getIdRecepcion() { return idRecepcion; }
    public void setIdRecepcion(Integer idRecepcion) { this.idRecepcion = idRecepcion; }

    public Integer getIdOrdenCompra() { return idOrdenCompra; }
    public void setIdOrdenCompra(Integer idOrdenCompra) { this.idOrdenCompra = idOrdenCompra; }

    public String getEstadoOrden() { return estadoOrden; }
    public void setEstadoOrden(String estadoOrden) { this.estadoOrden = estadoOrden; }

    public Integer getIdBodega() { return idBodega; }
    public void setIdBodega(Integer idBodega) { this.idBodega = idBodega; }

    public String getBodegaNombre() { return bodegaNombre; }
    public void setBodegaNombre(String bodegaNombre) { this.bodegaNombre = bodegaNombre; }

    public Integer getIdUsuarioReceptor() { return idUsuarioReceptor; }
    public void setIdUsuarioReceptor(Integer idUsuarioReceptor) { this.idUsuarioReceptor = idUsuarioReceptor; }

    public String getReceptorNombre() { return receptorNombre; }
    public void setReceptorNombre(String receptorNombre) { this.receptorNombre = receptorNombre; }

    public LocalDateTime getFechaRecepcion() { return fechaRecepcion; }
    public void setFechaRecepcion(LocalDateTime fechaRecepcion) { this.fechaRecepcion = fechaRecepcion; }

    public String getNumeroGuiaRemision() { return numeroGuiaRemision; }
    public void setNumeroGuiaRemision(String numeroGuiaRemision) { this.numeroGuiaRemision = numeroGuiaRemision; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public List<DetalleDTO> getDetalles() { return detalles; }
    public void setDetalles(List<DetalleDTO> detalles) { this.detalles = detalles; }

    public static class DetalleDTO {
        private Integer idDetalleRm;
        private Integer idDetalleOc;
        private String tipoItem;
        private String itemNombre;
        private Integer cantidadRecibidaAhora;
        private Integer cantidadDefectuosa;
        private String observacion;

        public DetalleDTO() {}

        public Integer getIdDetalleRm() { return idDetalleRm; }
        public void setIdDetalleRm(Integer idDetalleRm) { this.idDetalleRm = idDetalleRm; }

        public Integer getIdDetalleOc() { return idDetalleOc; }
        public void setIdDetalleOc(Integer idDetalleOc) { this.idDetalleOc = idDetalleOc; }

        public String getTipoItem() { return tipoItem; }
        public void setTipoItem(String tipoItem) { this.tipoItem = tipoItem; }

        public String getItemNombre() { return itemNombre; }
        public void setItemNombre(String itemNombre) { this.itemNombre = itemNombre; }

        public Integer getCantidadRecibidaAhora() { return cantidadRecibidaAhora; }
        public void setCantidadRecibidaAhora(Integer cantidadRecibidaAhora) { this.cantidadRecibidaAhora = cantidadRecibidaAhora; }

        public Integer getCantidadDefectuosa() { return cantidadDefectuosa; }
        public void setCantidadDefectuosa(Integer cantidadDefectuosa) { this.cantidadDefectuosa = cantidadDefectuosa; }

        public String getObservacion() { return observacion; }
        public void setObservacion(String observacion) { this.observacion = observacion; }
    }
}
