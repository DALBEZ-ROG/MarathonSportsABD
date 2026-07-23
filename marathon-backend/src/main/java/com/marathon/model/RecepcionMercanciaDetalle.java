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
@Table(name = "recepcion_mercancia_detalle")
public class RecepcionMercanciaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_rm")
    private Integer idDetalleRm;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_recepcion", nullable = false)
    private RecepcionMercancia recepcion;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_detalle_oc", nullable = false)
    private OrdenCompraDetalle detalleOc;

    @Column(name = "cantidad_recibida_ahora", nullable = false)
    private Integer cantidadRecibidaAhora;

    @Column(name = "cantidad_defectuosa", nullable = false)
    private Integer cantidadDefectuosa = 0;

    @Column(name = "observacion", columnDefinition = "text")
    private String observacion;

    public RecepcionMercanciaDetalle() {}

    public Integer getIdDetalleRm() { return idDetalleRm; }
    public void setIdDetalleRm(Integer idDetalleRm) { this.idDetalleRm = idDetalleRm; }

    public RecepcionMercancia getRecepcion() { return recepcion; }
    public void setRecepcion(RecepcionMercancia recepcion) { this.recepcion = recepcion; }

    public OrdenCompraDetalle getDetalleOc() { return detalleOc; }
    public void setDetalleOc(OrdenCompraDetalle detalleOc) { this.detalleOc = detalleOc; }

    public Integer getCantidadRecibidaAhora() { return cantidadRecibidaAhora; }
    public void setCantidadRecibidaAhora(Integer cantidadRecibidaAhora) { this.cantidadRecibidaAhora = cantidadRecibidaAhora; }

    public Integer getCantidadDefectuosa() { return cantidadDefectuosa; }
    public void setCantidadDefectuosa(Integer cantidadDefectuosa) { this.cantidadDefectuosa = cantidadDefectuosa; }

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
