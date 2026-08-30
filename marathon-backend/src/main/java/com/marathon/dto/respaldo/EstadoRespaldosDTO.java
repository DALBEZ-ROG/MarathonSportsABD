package com.marathon.dto.respaldo;

import java.time.LocalDateTime;

/**
 * Lo que la pantalla necesita saber nada mas abrirse, y cada dos segundos
 * mientras algo esta corriendo.
 *
 * <p>{@code disponible} en false con {@code motivo} lleno es el caso normal en
 * un equipo recien clonado: falta la credencial de respaldo. La pantalla lo
 * explica en lugar de ofrecer botones que van a fallar.
 */
public class EstadoRespaldosDTO {

    /** ¿Esta el modulo operativo? (herramientas de PostgreSQL + credencial + carpeta) */
    private boolean disponible;
    /** Si no lo esta, por que, en una frase que diga que hacer. */
    private String motivo;

    /** Hay una tarea corriendo ahora mismo. Nulo si el sistema esta libre. */
    private TareaEnCursoDTO tarea;

    private RespaldoDTO ultimoRespaldo;
    private int totalRespaldos;
    private int totalDisponibles;
    private long bytesOcupados;
    private Long bytesLibresDisco;

    /** El programador automatico. */
    private boolean automaticoActivo;
    private String automaticoCron;
    private String automaticoDescripcion;
    private LocalDateTime proximoAutomatico;

    /** Si la aplicacion esta en modo mantenimiento (durante una restauracion). */
    private boolean mantenimiento;

    /** La frase que hay que teclear para confirmar un borrado. La fija el servidor. */
    private String palabraBorrado;
    /** La frase que hay que teclear para confirmar una restauracion. */
    private String palabraRestauracion;

    public EstadoRespaldosDTO() {}

    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public TareaEnCursoDTO getTarea() { return tarea; }
    public void setTarea(TareaEnCursoDTO tarea) { this.tarea = tarea; }

    public RespaldoDTO getUltimoRespaldo() { return ultimoRespaldo; }
    public void setUltimoRespaldo(RespaldoDTO ultimoRespaldo) { this.ultimoRespaldo = ultimoRespaldo; }

    public int getTotalRespaldos() { return totalRespaldos; }
    public void setTotalRespaldos(int totalRespaldos) { this.totalRespaldos = totalRespaldos; }

    public int getTotalDisponibles() { return totalDisponibles; }
    public void setTotalDisponibles(int totalDisponibles) { this.totalDisponibles = totalDisponibles; }

    public long getBytesOcupados() { return bytesOcupados; }
    public void setBytesOcupados(long bytesOcupados) { this.bytesOcupados = bytesOcupados; }

    public Long getBytesLibresDisco() { return bytesLibresDisco; }
    public void setBytesLibresDisco(Long bytesLibresDisco) { this.bytesLibresDisco = bytesLibresDisco; }

    public boolean isAutomaticoActivo() { return automaticoActivo; }
    public void setAutomaticoActivo(boolean automaticoActivo) { this.automaticoActivo = automaticoActivo; }

    public String getAutomaticoCron() { return automaticoCron; }
    public void setAutomaticoCron(String automaticoCron) { this.automaticoCron = automaticoCron; }

    public String getAutomaticoDescripcion() { return automaticoDescripcion; }
    public void setAutomaticoDescripcion(String automaticoDescripcion) { this.automaticoDescripcion = automaticoDescripcion; }

    public LocalDateTime getProximoAutomatico() { return proximoAutomatico; }
    public void setProximoAutomatico(LocalDateTime proximoAutomatico) { this.proximoAutomatico = proximoAutomatico; }

    public boolean isMantenimiento() { return mantenimiento; }
    public void setMantenimiento(boolean mantenimiento) { this.mantenimiento = mantenimiento; }

    public String getPalabraBorrado() { return palabraBorrado; }
    public void setPalabraBorrado(String palabraBorrado) { this.palabraBorrado = palabraBorrado; }

    public String getPalabraRestauracion() { return palabraRestauracion; }
    public void setPalabraRestauracion(String palabraRestauracion) { this.palabraRestauracion = palabraRestauracion; }
}
