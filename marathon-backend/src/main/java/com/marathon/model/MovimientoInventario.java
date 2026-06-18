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
@Table(name = "movimiento_inventario")
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Integer idMovimiento;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_inventario", nullable = false)
    private Inventario inventario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proveedor")
    private Proveedor proveedor;

    @Column(name = "id_pedido")
    private Integer idPedido;

    @Column(name = "id_comprobante")
    private Integer idComprobante;

    @Column(name = "tipo_movimiento", nullable = false)
    private String tipoMovimiento;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "fecha", insertable = false, updatable = false)
    private LocalDateTime fecha;

    @Column(name = "observacion", columnDefinition = "text")
    private String observacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_inventario_destino")
    private Inventario inventarioDestino;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public MovimientoInventario() {}

    public Integer getIdMovimiento() { return idMovimiento; }
    public void setIdMovimiento(Integer idMovimiento) { this.idMovimiento = idMovimiento; }

    public Inventario getInventario() { return inventario; }
    public void setInventario(Inventario inventario) { this.inventario = inventario; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }

    public Integer getIdPedido() { return idPedido; }
    public void setIdPedido(Integer idPedido) { this.idPedido = idPedido; }

    public Integer getIdComprobante() { return idComprobante; }
    public void setIdComprobante(Integer idComprobante) { this.idComprobante = idComprobante; }

    public String getTipoMovimiento() { return tipoMovimiento; }
    public void setTipoMovimiento(String tipoMovimiento) { this.tipoMovimiento = tipoMovimiento; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public LocalDateTime getFecha() { return fecha; }

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }

    public Inventario getInventarioDestino() { return inventarioDestino; }
    public void setInventarioDestino(Inventario inventarioDestino) { this.inventarioDestino = inventarioDestino; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
