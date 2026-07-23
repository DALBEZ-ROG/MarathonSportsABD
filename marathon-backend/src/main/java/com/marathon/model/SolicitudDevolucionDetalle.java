package com.marathon.model;

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
@Table(name = "solicitud_devolucion_detalle")
public class SolicitudDevolucionDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_sd")
    private Integer idDetalleSd;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_solicitud", nullable = false)
    private SolicitudDevolucion solicitud;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_detalle_pedido", nullable = false)
    private DetallePedido detallePedido;

    @Column(name = "cantidad_devuelta", nullable = false)
    private Integer cantidadDevuelta;

    @Column(name = "resultado_inspeccion")
    private String resultadoInspeccion;

    @Column(name = "observacion_inspeccion", columnDefinition = "text")
    private String observacionInspeccion;

    public SolicitudDevolucionDetalle() {}

    public Integer getIdDetalleSd() { return idDetalleSd; }
    public void setIdDetalleSd(Integer idDetalleSd) { this.idDetalleSd = idDetalleSd; }

    public SolicitudDevolucion getSolicitud() { return solicitud; }
    public void setSolicitud(SolicitudDevolucion solicitud) { this.solicitud = solicitud; }

    public DetallePedido getDetallePedido() { return detallePedido; }
    public void setDetallePedido(DetallePedido detallePedido) { this.detallePedido = detallePedido; }

    public Integer getCantidadDevuelta() { return cantidadDevuelta; }
    public void setCantidadDevuelta(Integer cantidadDevuelta) { this.cantidadDevuelta = cantidadDevuelta; }

    public String getResultadoInspeccion() { return resultadoInspeccion; }
    public void setResultadoInspeccion(String resultadoInspeccion) { this.resultadoInspeccion = resultadoInspeccion; }

    public String getObservacionInspeccion() { return observacionInspeccion; }
    public void setObservacionInspeccion(String observacionInspeccion) { this.observacionInspeccion = observacionInspeccion; }
}
