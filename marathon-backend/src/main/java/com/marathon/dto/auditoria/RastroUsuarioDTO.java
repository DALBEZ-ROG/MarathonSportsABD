package com.marathon.dto.auditoria;

import java.time.LocalDateTime;
import java.util.List;

/**
 * «¿En qué partes del sistema tocó algo esta persona, y qué tocó?»
 *
 * <p>Es el resumen que contesta la pregunta con la que se entra a una
 * auditoría. Las tres listas vienen de las tres bitácoras del sistema, que no
 * son intercambiables (AUDITORIA.md §1):
 *
 * <ul>
 *   <li>{@code porModulo} — de {@code log_accion}: qué <i>hizo</i>, en lenguaje
 *       de negocio (aprobó, anuló, reembolsó).</li>
 *   <li>{@code porTabla} — de {@code auditoria_cambios}: qué <i>dato</i> cambió,
 *       campo a campo.</li>
 *   <li>{@code porBodega} — de {@code historial_inventario}: cuánto stock movió
 *       y dónde.</li>
 * </ul>
 *
 * <p>Cada fila lleva su recuento y sus fechas extremas, que es lo que permite
 * saltar desde aquí a la pestaña de detalle ya filtrada.
 */
public class RastroUsuarioDTO {

    /** Una línea del desglose: «en X hizo N cosas, entre estas dos fechas». */
    public static class Linea {
        private String clave;
        private String detalle;
        private long veces;
        private LocalDateTime primera;
        private LocalDateTime ultima;

        public Linea() {}

        public Linea(String clave, String detalle, long veces,
                     LocalDateTime primera, LocalDateTime ultima) {
            this.clave = clave;
            this.detalle = detalle;
            this.veces = veces;
            this.primera = primera;
            this.ultima = ultima;
        }

        public String getClave() { return clave; }
        public void setClave(String clave) { this.clave = clave; }

        public String getDetalle() { return detalle; }
        public void setDetalle(String detalle) { this.detalle = detalle; }

        public long getVeces() { return veces; }
        public void setVeces(long veces) { this.veces = veces; }

        public LocalDateTime getPrimera() { return primera; }
        public void setPrimera(LocalDateTime primera) { this.primera = primera; }

        public LocalDateTime getUltima() { return ultima; }
        public void setUltima(LocalDateTime ultima) { this.ultima = ultima; }
    }

    private Integer idUsuario;
    private String usuarioNombre;
    private long totalAcciones;
    private long totalCambios;
    private long totalMovimientos;
    private LocalDateTime primeraHuella;
    private LocalDateTime ultimaHuella;
    private List<Linea> porModulo;
    private List<Linea> porTabla;
    private List<Linea> porBodega;

    public RastroUsuarioDTO() {}

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }

    public long getTotalAcciones() { return totalAcciones; }
    public void setTotalAcciones(long totalAcciones) { this.totalAcciones = totalAcciones; }

    public long getTotalCambios() { return totalCambios; }
    public void setTotalCambios(long totalCambios) { this.totalCambios = totalCambios; }

    public long getTotalMovimientos() { return totalMovimientos; }
    public void setTotalMovimientos(long totalMovimientos) { this.totalMovimientos = totalMovimientos; }

    public LocalDateTime getPrimeraHuella() { return primeraHuella; }
    public void setPrimeraHuella(LocalDateTime primeraHuella) { this.primeraHuella = primeraHuella; }

    public LocalDateTime getUltimaHuella() { return ultimaHuella; }
    public void setUltimaHuella(LocalDateTime ultimaHuella) { this.ultimaHuella = ultimaHuella; }

    public List<Linea> getPorModulo() { return porModulo; }
    public void setPorModulo(List<Linea> porModulo) { this.porModulo = porModulo; }

    public List<Linea> getPorTabla() { return porTabla; }
    public void setPorTabla(List<Linea> porTabla) { this.porTabla = porTabla; }

    public List<Linea> getPorBodega() { return porBodega; }
    public void setPorBodega(List<Linea> porBodega) { this.porBodega = porBodega; }
}
