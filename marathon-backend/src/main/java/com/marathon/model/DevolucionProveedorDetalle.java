package com.marathon.model;

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
@Table(name = "devolucion_proveedor_detalle")
public class DevolucionProveedorDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_dp")
    private Integer idDetalleDp;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_devolucion_prov", nullable = false)
    private DevolucionProveedor devolucionProveedor;

    @Column(name = "origen", nullable = false)
    private String origen;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_solicitud_devolucion_detalle")
    private SolicitudDevolucionDetalle solicitudDevolucionDetalle;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_recepcion_detalle")
    private RecepcionMercanciaDetalle recepcionDetalle;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "motivo", columnDefinition = "text")
    private String motivo;

    public DevolucionProveedorDetalle() {}

    public Integer getIdDetalleDp() { return idDetalleDp; }
    public void setIdDetalleDp(Integer id) { this.idDetalleDp = id; }
    public DevolucionProveedor getDevolucionProveedor() { return devolucionProveedor; }
    public void setDevolucionProveedor(DevolucionProveedor d) { this.devolucionProveedor = d; }
    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
    public SolicitudDevolucionDetalle getSolicitudDevolucionDetalle() { return solicitudDevolucionDetalle; }
    public void setSolicitudDevolucionDetalle(SolicitudDevolucionDetalle s) { this.solicitudDevolucionDetalle = s; }
    public RecepcionMercanciaDetalle getRecepcionDetalle() { return recepcionDetalle; }
    public void setRecepcionDetalle(RecepcionMercanciaDetalle r) { this.recepcionDetalle = r; }
    public Producto getProducto() { return producto; }
    public void setProducto(Producto p) { this.producto = p; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer c) { this.cantidad = c; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String m) { this.motivo = m; }
}
