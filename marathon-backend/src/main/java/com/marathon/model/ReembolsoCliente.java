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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "reembolso_cliente")
public class ReembolsoCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reembolso")
    private Integer idReembolso;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_solicitud", nullable = false, unique = true)
    private SolicitudDevolucion solicitud;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario_registro", nullable = false)
    private Usuario usuarioRegistro;

    @Column(name = "monto", nullable = false)
    private BigDecimal monto;

    @Column(name = "metodo", nullable = false)
    private String metodo;

    @Column(name = "fecha_reembolso", insertable = false, updatable = false)
    private LocalDateTime fechaReembolso;

    @Column(name = "observaciones", columnDefinition = "text")
    private String observaciones;

    public ReembolsoCliente() {}

    public Integer getIdReembolso() { return idReembolso; }
    public void setIdReembolso(Integer idReembolso) { this.idReembolso = idReembolso; }

    public SolicitudDevolucion getSolicitud() { return solicitud; }
    public void setSolicitud(SolicitudDevolucion solicitud) { this.solicitud = solicitud; }

    public Usuario getUsuarioRegistro() { return usuarioRegistro; }
    public void setUsuarioRegistro(Usuario usuarioRegistro) { this.usuarioRegistro = usuarioRegistro; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }

    public LocalDateTime getFechaReembolso() { return fechaReembolso; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}
