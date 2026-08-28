package com.marathon.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
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
@Table(name = "orden_compra")
// F49 (D-39): @DynamicInsert es OBLIGATORIO aqui, no una optimizacion.
// Sin el, Hibernate nombra en el INSERT TODAS las columnas mapeadas, incluidas
// las que solo se rellenan en una etapa POSTERIOR del flujo:
//   id_usuario_aprobador, fecha_aprobacion — las pone la aprobacion.
// La fase 34 concede privilegios columna por columna, asi que el rol que
// ARRANCA el flujo
// no las tiene y la base rechaza el INSERT entero con "permiso denegado".
// Con @DynamicInsert solo se nombran las columnas con valor, y el INSERT pasa.
// Ver marathon-backend/sql/fase49_privilegios_de_creacion.sql.
@DynamicInsert
@DynamicUpdate
public class OrdenCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orden_compra")
    private Integer idOrdenCompra;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_proveedor", nullable = false)
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario_solicitante", nullable = false)
    private Usuario usuarioSolicitante;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario_aprobador")
    private Usuario usuarioAprobador;

    @Column(name = "fecha_orden", insertable = false, updatable = false)
    private LocalDateTime fechaOrden;

    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;

    @Column(name = "estado", nullable = false)
    private String estado;

    // Calculado por trigger fn_recalcular_total_orden_compra_stmt — NUNCA escribir desde la app
    @Column(name = "total", insertable = false, updatable = false)
    private BigDecimal total;

    @Column(name = "observaciones", columnDefinition = "text")
    private String observaciones;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * ¿Es una reposición del proveedor por una reclamación? (F69)
     *
     * <p>Si lo es, <b>no se factura</b>: la mercancía ya se pagó cuando se
     * compró la que salió defectuosa. La línea lleva precio real de todos modos,
     * para que la recepción no falsee el costo promedio ponderado.
     */
    @Column(name = "es_reposicion", nullable = false)
    private Boolean esReposicion = false;

    /** La devolución que originó esta reposición. Solo si es reposición. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_devolucion_prov")
    private DevolucionProveedor devolucionProveedor;

    public OrdenCompra() {}

    public Boolean getEsReposicion() { return esReposicion != null && esReposicion; }
    public void setEsReposicion(Boolean esReposicion) { this.esReposicion = esReposicion; }

    public DevolucionProveedor getDevolucionProveedor() { return devolucionProveedor; }
    public void setDevolucionProveedor(DevolucionProveedor d) { this.devolucionProveedor = d; }

    public Integer getIdOrdenCompra() { return idOrdenCompra; }
    public void setIdOrdenCompra(Integer idOrdenCompra) { this.idOrdenCompra = idOrdenCompra; }

    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }

    public Usuario getUsuarioSolicitante() { return usuarioSolicitante; }
    public void setUsuarioSolicitante(Usuario usuarioSolicitante) { this.usuarioSolicitante = usuarioSolicitante; }

    public Usuario getUsuarioAprobador() { return usuarioAprobador; }
    public void setUsuarioAprobador(Usuario usuarioAprobador) { this.usuarioAprobador = usuarioAprobador; }

    public LocalDateTime getFechaOrden() { return fechaOrden; }

    public LocalDateTime getFechaAprobacion() { return fechaAprobacion; }
    public void setFechaAprobacion(LocalDateTime fechaAprobacion) { this.fechaAprobacion = fechaAprobacion; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public BigDecimal getTotal() { return total; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
