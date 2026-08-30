package com.marathon.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un punto de recuperacion: el volcado logico de la base en un instante.
 *
 * <p>Vive en el esquema {@code control} y no en {@code public}, y eso NO es
 * organizacion: es lo que hace que la fila sobreviva a las dos operaciones que
 * registra. El borrado vacia las tablas de negocio; la restauracion reemplaza
 * {@code public} entero con el contenido del volcado. Un diario guardado ahi
 * dentro se borraria a si mismo en el primer caso y volveria atras en el
 * segundo, perdiendo justo la fila que dice quien lo hizo.
 *
 * <p>Ver la cabecera de {@code sql/fase92_control_respaldos.sql}.
 */
@Entity
@Table(name = "respaldo", schema = "control")
public class Respaldo {

    /** Tomado a mano desde la pantalla. */
    public static final String ORIGEN_MANUAL = "MANUAL";
    /** Tomado por el programador a las 02:00. */
    public static final String ORIGEN_AUTOMATICO = "AUTOMATICO";

    public static final String EN_CURSO = "EN_CURSO";
    public static final String COMPLETADO = "COMPLETADO";
    public static final String FALLIDO = "FALLIDO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_respaldo")
    private Long idRespaldo;

    @Column(name = "nombre", nullable = false, unique = true)
    private String nombre;

    @Column(name = "ruta", nullable = false)
    private String ruta;

    @Column(name = "origen", nullable = false)
    private String origen;

    @Column(name = "estado", nullable = false)
    private String estado;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Column(name = "duracion_ms")
    private Long duracionMs;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    @Column(name = "filas")
    private Long filas;

    /**
     * Quien lo pidio. Nulo en el automatico: no lo pidio nadie, lo pidio el
     * reloj, y rellenarlo con un usuario de sistema seria mentir sobre quien
     * estaba delante.
     */
    @Column(name = "id_usuario")
    private Integer idUsuario;

    /**
     * El nombre, COPIADO. No hay clave ajena contra {@code usuario} a proposito:
     * esa tabla la vacia el borrado y la reemplaza la restauracion, y una FK
     * convertiria el diario en rehen de los datos que vigila.
     */
    @Column(name = "usuario_nombre")
    private String usuarioNombre;

    @Column(name = "nota")
    private String nota;

    @Column(name = "mensaje")
    private String mensaje;

    public Respaldo() {}

    public Long getIdRespaldo() { return idRespaldo; }
    public void setIdRespaldo(Long idRespaldo) { this.idRespaldo = idRespaldo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getRuta() { return ruta; }
    public void setRuta(String ruta) { this.ruta = ruta; }

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
}
