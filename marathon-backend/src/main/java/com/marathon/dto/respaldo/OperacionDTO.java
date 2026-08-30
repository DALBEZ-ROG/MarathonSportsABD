package com.marathon.dto.respaldo;

import java.time.LocalDateTime;

/** Un borrado o una restauracion ya ocurridos, para el diario de la pantalla. */
public class OperacionDTO {

    private Long idOperacion;
    private String tipo;
    private Long idRespaldo;
    /** El nombre del respaldo usado, resuelto aqui para no obligar a la pantalla a cruzarlo. */
    private String respaldoNombre;
    private String estado;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Long duracionMs;
    private Integer idUsuario;
    private String usuarioNombre;
    private String ip;
    private Long filasAfectadas;
    private String detalle;

    public OperacionDTO() {}

    public Long getIdOperacion() { return idOperacion; }
    public void setIdOperacion(Long idOperacion) { this.idOperacion = idOperacion; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Long getIdRespaldo() { return idRespaldo; }
    public void setIdRespaldo(Long idRespaldo) { this.idRespaldo = idRespaldo; }

    public String getRespaldoNombre() { return respaldoNombre; }
    public void setRespaldoNombre(String respaldoNombre) { this.respaldoNombre = respaldoNombre; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDateTime fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDateTime fechaFin) { this.fechaFin = fechaFin; }

    public Long getDuracionMs() { return duracionMs; }
    public void setDuracionMs(Long duracionMs) { this.duracionMs = duracionMs; }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public Long getFilasAfectadas() { return filasAfectadas; }
    public void setFilasAfectadas(Long filasAfectadas) { this.filasAfectadas = filasAfectadas; }

    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }
}
