package com.marathon.model;

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
@Table(name = "recepcion_mercancia")
public class RecepcionMercancia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_recepcion")
    private Integer idRecepcion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_orden_compra", nullable = false)
    private OrdenCompra ordenCompra;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario_receptor", nullable = false)
    private Usuario usuarioReceptor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_bodega", nullable = false)
    private Bodega bodega;

    @Column(name = "fecha_recepcion", insertable = false, updatable = false)
    private LocalDateTime fechaRecepcion;

    @Column(name = "numero_guia_remision")
    private String numeroGuiaRemision;

    @Column(name = "observaciones", columnDefinition = "text")
    private String observaciones;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public RecepcionMercancia() {}

    public Integer getIdRecepcion() { return idRecepcion; }
    public void setIdRecepcion(Integer idRecepcion) { this.idRecepcion = idRecepcion; }

    public OrdenCompra getOrdenCompra() { return ordenCompra; }
    public void setOrdenCompra(OrdenCompra ordenCompra) { this.ordenCompra = ordenCompra; }

    public Usuario getUsuarioReceptor() { return usuarioReceptor; }
    public void setUsuarioReceptor(Usuario usuarioReceptor) { this.usuarioReceptor = usuarioReceptor; }

    public Bodega getBodega() { return bodega; }
    public void setBodega(Bodega bodega) { this.bodega = bodega; }

    public LocalDateTime getFechaRecepcion() { return fechaRecepcion; }

    public String getNumeroGuiaRemision() { return numeroGuiaRemision; }
    public void setNumeroGuiaRemision(String numeroGuiaRemision) { this.numeroGuiaRemision = numeroGuiaRemision; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
