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
import jakarta.persistence.Table;

@Entity
@Table(name = "pedido")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Integer idPedido;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_pedido", insertable = false, updatable = false)
    private LocalDateTime fechaPedido;

    @Column(name = "total", insertable = false, updatable = false)
    private BigDecimal total;

    @Column(name = "descuento", nullable = false)
    private BigDecimal descuento;

    @Column(name = "estado", nullable = false)
    private String estado;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "es_pedido_especial", nullable = false)
    private Boolean esPedidoEspecial = false;

    @Column(name = "tipo_especial")
    private String tipoEspecial;

    @Column(name = "nota_especial")
    private String notaEspecial;

    @Column(name = "fecha_limite_entrega")
    private LocalDateTime fechaLimiteEntrega;

    @Column(name = "numero_hu")
    private String numeroHu;

    @Column(name = "transportista")
    private String transportista;

    @Column(name = "region_destino")
    private String regionDestino;

    @Column(name = "fecha_empaque")
    private LocalDateTime fechaEmpaque;

    public Pedido() {}

    public Integer getIdPedido() { return idPedido; }
    public void setIdPedido(Integer idPedido) { this.idPedido = idPedido; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public LocalDateTime getFechaPedido() { return fechaPedido; }

    public BigDecimal getTotal() { return total; }

    public BigDecimal getDescuento() { return descuento; }
    public void setDescuento(BigDecimal descuento) { this.descuento = descuento; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public Boolean getEsPedidoEspecial() { return esPedidoEspecial; }
    public void setEsPedidoEspecial(Boolean esPedidoEspecial) { this.esPedidoEspecial = esPedidoEspecial; }

    public String getTipoEspecial() { return tipoEspecial; }
    public void setTipoEspecial(String tipoEspecial) { this.tipoEspecial = tipoEspecial; }

    public String getNotaEspecial() { return notaEspecial; }
    public void setNotaEspecial(String notaEspecial) { this.notaEspecial = notaEspecial; }

    public LocalDateTime getFechaLimiteEntrega() { return fechaLimiteEntrega; }
    public void setFechaLimiteEntrega(LocalDateTime fechaLimiteEntrega) { this.fechaLimiteEntrega = fechaLimiteEntrega; }

    public String getNumeroHu() { return numeroHu; }
    public void setNumeroHu(String numeroHu) { this.numeroHu = numeroHu; }

    public String getTransportista() { return transportista; }
    public void setTransportista(String transportista) { this.transportista = transportista; }

    public String getRegionDestino() { return regionDestino; }
    public void setRegionDestino(String regionDestino) { this.regionDestino = regionDestino; }

    public LocalDateTime getFechaEmpaque() { return fechaEmpaque; }
    public void setFechaEmpaque(LocalDateTime fechaEmpaque) { this.fechaEmpaque = fechaEmpaque; }
}
