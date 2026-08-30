package com.marathon.dto.respaldo;

/**
 * Lo que esta pasando ahora mismo, para que la pantalla no se quede muda
 * durante medio minuto.
 *
 * <p>{@code porcentaje} vale -1 cuando no se puede saber. Es deliberado y no un
 * caso a rellenar con un cero: durante el volcado se puede estimar comparando
 * los bytes ya escritos con lo que ocupo el respaldo anterior, pero durante la
 * restauracion {@code pg_restore} no dice por donde va, y una barra inventada
 * que se queda clavada en el 40 % es peor que un reloj que cuenta segundos.
 */
public class TareaEnCursoDTO {

    /** RESPALDO, RESTAURACION o BORRADO. */
    private String tipo;
    /** Una frase para la pantalla: «Volcando la base a disco...». */
    private String descripcion;
    /** El paso concreto dentro de la tarea. */
    private String fase;
    /** 0..100, o -1 si no se puede estimar. */
    private int porcentaje;
    /** Segundos desde que empezo. */
    private long segundos;
    /** Bytes escritos hasta ahora, cuando aplica. */
    private long bytes;
    /** Contra cuanto se compara para estimar el porcentaje. */
    private long bytesEsperados;

    public TareaEnCursoDTO() {}

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getFase() { return fase; }
    public void setFase(String fase) { this.fase = fase; }

    public int getPorcentaje() { return porcentaje; }
    public void setPorcentaje(int porcentaje) { this.porcentaje = porcentaje; }

    public long getSegundos() { return segundos; }
    public void setSegundos(long segundos) { this.segundos = segundos; }

    public long getBytes() { return bytes; }
    public void setBytes(long bytes) { this.bytes = bytes; }

    public long getBytesEsperados() { return bytesEsperados; }
    public void setBytesEsperados(long bytesEsperados) { this.bytesEsperados = bytesEsperados; }
}
