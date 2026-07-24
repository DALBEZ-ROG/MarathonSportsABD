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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "cuenta_por_pagar")
public class CuentaPorPagar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cuenta_pagar")
    private Integer idCuentaPagar;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_factura_compra", nullable = false, unique = true)
    private FacturaCompra facturaCompra;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_proveedor", nullable = false)
    private Proveedor proveedor;

    @Column(name = "monto_total", nullable = false)
    private BigDecimal montoTotal;

    // Calculado por trigger — NUNCA insertar/actualizar manualmente
    @Column(name = "monto_pagado", insertable = false, updatable = false)
    private BigDecimal montoPagado;

    // GENERATED ALWAYS AS (monto_total - monto_pagado) — NUNCA insertar/actualizar
    @org.hibernate.annotations.Generated(event = { org.hibernate.generator.EventType.INSERT, org.hibernate.generator.EventType.UPDATE })
    @Column(name = "saldo_pendiente", insertable = false, updatable = false)
    private BigDecimal saldoPendiente;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "estado", nullable = false)
    private String estado;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public CuentaPorPagar() {}

    public Integer getIdCuentaPagar() { return idCuentaPagar; }
    public void setIdCuentaPagar(Integer idCuentaPagar) { this.idCuentaPagar = idCuentaPagar; }

    public FacturaCompra getFacturaCompra() { return facturaCompra; }
    public void setFacturaCompra(FacturaCompra facturaCompra) { this.facturaCompra = facturaCompra; }

    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }

    public BigDecimal getMontoTotal() { return montoTotal; }
    public void setMontoTotal(BigDecimal montoTotal) { this.montoTotal = montoTotal; }

    public BigDecimal getMontoPagado() { return montoPagado; }

    public BigDecimal getSaldoPendiente() { return saldoPendiente; }

    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
