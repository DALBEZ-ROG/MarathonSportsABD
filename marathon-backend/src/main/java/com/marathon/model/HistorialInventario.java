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
@Table(name = "historial_inventario")
public class HistorialInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Integer idHistorial;

    @ManyToOne
    @JoinColumn(name = "id_inventario")
    private Inventario inventario;

    @Column(name = "cantidad_anterior")
    private Integer cantidadAnterior;

    @Column(name = "cantidad_nueva")
    private Integer cantidadNueva;

    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "fecha_cambio", insertable = false, updatable = false)
    private LocalDateTime fechaCambio;

    @Column(name = "tipo_operacion")
    private String tipoOperacion;

    public HistorialInventario() {}

    public Integer getIdHistorial() { return idHistorial; }
    public void setIdHistorial(Integer idHistorial) { this.idHistorial = idHistorial; }

    public Inventario getInventario() { return inventario; }
    public void setInventario(Inventario inventario) { this.inventario = inventario; }

    public Integer getCantidadAnterior() { return cantidadAnterior; }
    public void setCantidadAnterior(Integer cantidadAnterior) { this.cantidadAnterior = cantidadAnterior; }

    public Integer getCantidadNueva() { return cantidadNueva; }
    public void setCantidadNueva(Integer cantidadNueva) { this.cantidadNueva = cantidadNueva; }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public LocalDateTime getFechaCambio() { return fechaCambio; }
    public void setFechaCambio(LocalDateTime fechaCambio) { this.fechaCambio = fechaCambio; }

    public String getTipoOperacion() { return tipoOperacion; }
    public void setTipoOperacion(String tipoOperacion) { this.tipoOperacion = tipoOperacion; }
}
