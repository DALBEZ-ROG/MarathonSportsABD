package com.marathon.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "comprobante_interno")
public class ComprobanteInterno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comprobante")
    private Integer idComprobante;

    @Column(name = "numero_comprobante", insertable = false, updatable = false)
    private String numeroComprobante;

    @Column(name = "tipo", nullable = false)
    private String tipo;

    @ManyToOne
    @JoinColumn(name = "id_bodega_origen")
    private Bodega bodegaOrigen;

    @ManyToOne
    @JoinColumn(name = "id_bodega_destino")
    private Bodega bodegaDestino;

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Column(name = "fecha", insertable = false, updatable = false)
    private LocalDateTime fecha;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "estado")
    private String estado;

    public ComprobanteInterno() {}

    public Integer getIdComprobante() { return idComprobante; }
    public void setIdComprobante(Integer idComprobante) { this.idComprobante = idComprobante; }

    public String getNumeroComprobante() { return numeroComprobante; }
    public void setNumeroComprobante(String numeroComprobante) { this.numeroComprobante = numeroComprobante; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Bodega getBodegaOrigen() { return bodegaOrigen; }
    public void setBodegaOrigen(Bodega bodegaOrigen) { this.bodegaOrigen = bodegaOrigen; }

    public Bodega getBodegaDestino() { return bodegaDestino; }
    public void setBodegaDestino(Bodega bodegaDestino) { this.bodegaDestino = bodegaDestino; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
