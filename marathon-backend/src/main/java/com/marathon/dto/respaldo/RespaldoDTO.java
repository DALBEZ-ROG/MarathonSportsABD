package com.marathon.dto.respaldo;

import java.time.LocalDateTime;

/** Un punto de recuperacion, tal como lo ve la pantalla. */
public class RespaldoDTO {

    private Long idRespaldo;
    private String nombre;
    private String origen;
    private String estado;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Long duracionMs;
    private Long tamanoBytes;
    private Long filas;
    private Integer idUsuario;
    private String usuarioNombre;
    private String nota;
    private String mensaje;

    /**
     * Si el directorio del volcado sigue estando en el disco.
     *
     * <p>Se comprueba en cada consulta y no se guarda en la base a proposito:
     * la fila dice que el respaldo <i>se hizo</i>, el disco dice si <i>todavia
     * esta</i>, y son dos cosas distintas. Alguien puede haber borrado la
     * carpeta a mano, o haberla movido a otro disco. Un boton de restaurar que
     * apunta a algo que ya no existe es peor que no tener boton.
     */
    private boolean disponible;

    public RespaldoDTO() {}

    public Long getIdRespaldo() { return idRespaldo; }
    public void setIdRespaldo(Long idRespaldo) { this.idRespaldo = idRespaldo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDateTime fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDateTime fechaFin) { this.fechaFin = fechaFin; }

    public Long getDuracionMs() { return duracionMs; }
    public void setDuracionMs(Long duracionMs) { this.duracionMs = duracionMs; }

    public Long getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(Long tamanoBytes) { this.tamanoBytes = tamanoBytes; }

    public Long getFilas() { return filas; }
    public void setFilas(Long filas) { this.filas = filas; }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }

    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }
}
