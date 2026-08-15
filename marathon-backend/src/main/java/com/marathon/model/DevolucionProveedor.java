package com.marathon.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicUpdate;

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
@Table(name = "devolucion_proveedor")
@DynamicUpdate
public class DevolucionProveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_devolucion_prov")
    private Integer idDevolucionProv;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_proveedor", nullable = false)
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario_registro", nullable = false)
    private Usuario usuarioRegistro;

    @Column(name = "fecha_devolucion", insertable = false, updatable = false)
    private LocalDateTime fechaDevolucion;

    @Column(name = "estado", nullable = false)
    private String estado;

    @Column(name = "tipo_resolucion")
    private String tipoResolucion;

    @Column(name = "monto_reembolso")
    private BigDecimal montoReembolso;

    @Column(name = "observaciones", columnDefinition = "text")
    private String observaciones;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public DevolucionProveedor() {}

    public Integer getIdDevolucionProv() { return idDevolucionProv; }
    public void setIdDevolucionProv(Integer id) { this.idDevolucionProv = id; }
    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }
    public Usuario getUsuarioRegistro() { return usuarioRegistro; }
    public void setUsuarioRegistro(Usuario u) { this.usuarioRegistro = u; }
    public LocalDateTime getFechaDevolucion() { return fechaDevolucion; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getTipoResolucion() { return tipoResolucion; }
    public void setTipoResolucion(String t) { this.tipoResolucion = t; }
    public BigDecimal getMontoReembolso() { return montoReembolso; }
    public void setMontoReembolso(BigDecimal m) { this.montoReembolso = m; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String o) { this.observaciones = o; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
