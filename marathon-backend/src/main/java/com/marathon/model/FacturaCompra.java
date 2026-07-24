package com.marathon.model;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table(name = "factura_compra")
public class FacturaCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_factura_compra")
    private Integer idFacturaCompra;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_orden_compra", nullable = false)
    private OrdenCompra ordenCompra;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario_registro", nullable = false)
    private Usuario usuarioRegistro;

    @Column(name = "numero_factura_proveedor", nullable = false)
    private String numeroFacturaProveedor;

    @Column(name = "fecha_factura", nullable = false)
    private LocalDate fechaFactura;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "subtotal", nullable = false)
    private BigDecimal subtotal;

    @Column(name = "impuesto", nullable = false)
    private BigDecimal impuesto;

    // GENERATED ALWAYS AS (subtotal + impuesto) — NUNCA insertar/actualizar
    @org.hibernate.annotations.Generated(event = { org.hibernate.generator.EventType.INSERT, org.hibernate.generator.EventType.UPDATE })
    @Column(name = "total", insertable = false, updatable = false)
    private BigDecimal total;

    @Column(name = "estado", nullable = false)
    private String estado;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public FacturaCompra() {}

    public Integer getIdFacturaCompra() { return idFacturaCompra; }
    public void setIdFacturaCompra(Integer idFacturaCompra) { this.idFacturaCompra = idFacturaCompra; }

    public OrdenCompra getOrdenCompra() { return ordenCompra; }
    public void setOrdenCompra(OrdenCompra ordenCompra) { this.ordenCompra = ordenCompra; }

    public Usuario getUsuarioRegistro() { return usuarioRegistro; }
    public void setUsuarioRegistro(Usuario usuarioRegistro) { this.usuarioRegistro = usuarioRegistro; }

    public String getNumeroFacturaProveedor() { return numeroFacturaProveedor; }
    public void setNumeroFacturaProveedor(String numeroFacturaProveedor) { this.numeroFacturaProveedor = numeroFacturaProveedor; }

    public LocalDate getFechaFactura() { return fechaFactura; }
    public void setFechaFactura(LocalDate fechaFactura) { this.fechaFactura = fechaFactura; }

    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getImpuesto() { return impuesto; }
    public void setImpuesto(BigDecimal impuesto) { this.impuesto = impuesto; }

    public BigDecimal getTotal() { return total; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
