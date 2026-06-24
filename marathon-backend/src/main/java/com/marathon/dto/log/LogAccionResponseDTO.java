package com.marathon.dto.log;

import java.time.LocalDateTime;

public class LogAccionResponseDTO {

    private Integer idLog;
    private String modulo;
    private String accion;
    private String descripcion;
    private String ipAddress;
    private LocalDateTime fecha;
    private Integer idUsuario;
    private String usuarioNombre;
    private String usuarioApellido;

    public LogAccionResponseDTO() {}

    public Integer getIdLog() { return idLog; }
    public void setIdLog(Integer idLog) { this.idLog = idLog; }

    public String getModulo() { return modulo; }
    public void setModulo(String modulo) { this.modulo = modulo; }

    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }

    public String getUsuarioApellido() { return usuarioApellido; }
    public void setUsuarioApellido(String usuarioApellido) { this.usuarioApellido = usuarioApellido; }
}
