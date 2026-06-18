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
@Table(name = "historial_inventario")
public class HistorialInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Integer idHistorial;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_inventario", nullable = false)
    private Inventario inventario;

    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "stock_anterior", nullable = false)
    private Integer stockAnterior;

    @Column(name = "stock_nuevo", nullable = false)
    private Integer stockNuevo;

    @Column(name = "motivo", nullable = false)
    private String motivo;

    @Column(name = "fecha", insertable = false, updatable = false)
    private LocalDateTime fecha;

    public HistorialInventario() {}

    public Integer getIdHistorial() { return idHistorial; }
    public void setIdHistorial(Integer idHistorial) { this.idHistorial = idHistorial; }

    public Inventario getInventario() { return inventario; }
    public void setInventario(Inventario inventario) { this.inventario = inventario; }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public Integer getStockAnterior() { return stockAnterior; }
    public void setStockAnterior(Integer stockAnterior) { this.stockAnterior = stockAnterior; }

    public Integer getStockNuevo() { return stockNuevo; }
    public void setStockNuevo(Integer stockNuevo) { this.stockNuevo = stockNuevo; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public LocalDateTime getFecha() { return fecha; }
}
