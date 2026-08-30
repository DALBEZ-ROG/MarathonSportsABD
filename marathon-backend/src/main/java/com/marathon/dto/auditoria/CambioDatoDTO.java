package com.marathon.dto.auditoria;

import java.time.LocalDateTime;

/**
 * Una fila de {@code auditoria_cambios}: un campo que cambió de valor.
 *
 * <p>Es el grano más fino de la auditoría del sistema. Un UPDATE que toca tres
 * columnas produce tres de estas, no un volcado JSON de la fila; el
 * {@code txid} es lo que permite volver a juntarlas y ver que fueron un mismo
 * acto. Ver AUDITORIA.md §2.
 *
 * <p>{@code usuarioNombre} puede venir nulo con {@code usuarioBd} lleno, y eso
 * <b>significa algo</b>: es un cambio hecho fuera de la aplicación (psql, un
 * script, alguien con la credencial). No se rellena con un valor por defecto.
 */
public class CambioDatoDTO {

    private Long id;
    private LocalDateTime fecha;
    private String tabla;
    private String pkValor;
    private String operacion;
    private String campo;
    private String valorAnterior;
    private String valorNuevo;
    private String usuarioBd;
    private Integer idUsuario;
    private String usuarioNombre;
    private Long txid;

    public CambioDatoDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public String getTabla() { return tabla; }
    public void setTabla(String tabla) { this.tabla = tabla; }

    public String getPkValor() { return pkValor; }
    public void setPkValor(String pkValor) { this.pkValor = pkValor; }

    public String getOperacion() { return operacion; }
    public void setOperacion(String operacion) { this.operacion = operacion; }

    public String getCampo() { return campo; }
    public void setCampo(String campo) { this.campo = campo; }

    public String getValorAnterior() { return valorAnterior; }
    public void setValorAnterior(String valorAnterior) { this.valorAnterior = valorAnterior; }

    public String getValorNuevo() { return valorNuevo; }
    public void setValorNuevo(String valorNuevo) { this.valorNuevo = valorNuevo; }

    public String getUsuarioBd() { return usuarioBd; }
    public void setUsuarioBd(String usuarioBd) { this.usuarioBd = usuarioBd; }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }

    public Long getTxid() { return txid; }
    public void setTxid(Long txid) { this.txid = txid; }
}
