package com.marathon.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un borrado total o una restauracion: las dos operaciones que destruyen datos.
 *
 * <p>Igual que {@link Respaldo}, vive en el esquema {@code control} para que la
 * fila sobreviva a la operacion que describe. Sin eso, «quien borro la base»
 * seria la unica pregunta que el sistema no podria contestar, que es
 * precisamente la que mas falta hace.
 */
@Entity
@Table(name = "operacion", schema = "control")
public class OperacionControl {

    public static final String BORRADO_TOTAL = "BORRADO_TOTAL";
    public static final String RESTAURACION = "RESTAURACION";

    public static final String EN_CURSO = "EN_CURSO";
    public static final String COMPLETADO = "COMPLETADO";
    public static final String FALLIDO = "FALLIDO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_operacion")
    private Long idOperacion;

    @Column(name = "tipo", nullable = false)
    private String tipo;

    @Column(name = "id_respaldo")
    private Long idRespaldo;

    @Column(name = "estado", nullable = false)
    private String estado;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Column(name = "duracion_ms")
    private Long duracionMs;

    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "usuario_nombre")
    private String usuarioNombre;

    @Column(name = "ip")
    private String ip;

    @Column(name = "filas_afectadas")
    private Long filasAfectadas;

    @Column(name = "detalle")
    private String detalle;

    public OperacionControl() {}

    public Long getIdOperacion() { return idOperacion; }
    public void setIdOperacion(Long idOperacion) { this.idOperacion = idOperacion; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Long getIdRespaldo() { return idRespaldo; }
    public void setIdRespaldo(Long idRespaldo) { this.idRespaldo = idRespaldo; }

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
