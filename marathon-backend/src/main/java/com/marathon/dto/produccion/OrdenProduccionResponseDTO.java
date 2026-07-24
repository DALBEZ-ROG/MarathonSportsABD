package com.marathon.dto.produccion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrdenProduccionResponseDTO {

    private Integer idOrdenProduccion;
    private Integer idProducto;
    private String productoNombre;
    private Integer idBodegaDestino;
    private String bodegaNombre;
    private Integer idUsuarioRegistro;
    private String usuarioRegistroNombre;
    private Integer idUsuarioCompleta;
    private String usuarioCompletaNombre;
    private Integer cantidadPlanificada;
    private Integer cantidadProducida;
    private String estado;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private String observaciones;
    private BigDecimal costoMateriaPrima;
    private BigDecimal costoManoObra;
    private BigDecimal costoIndirecto;
    private BigDecimal costoTotal;
    private BigDecimal costoUnitarioProducido;
    private List<ConsumoDTO> consumos;

    public OrdenProduccionResponseDTO() {}

    public Integer getIdOrdenProduccion() { return idOrdenProduccion; }
    public void setIdOrdenProduccion(Integer v) { this.idOrdenProduccion = v; }

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer v) { this.idProducto = v; }

    public String getProductoNombre() { return productoNombre; }
    public void setProductoNombre(String v) { this.productoNombre = v; }

    public Integer getIdBodegaDestino() { return idBodegaDestino; }
    public void setIdBodegaDestino(Integer v) { this.idBodegaDestino = v; }

    public String getBodegaNombre() { return bodegaNombre; }
    public void setBodegaNombre(String v) { this.bodegaNombre = v; }

    public Integer getIdUsuarioRegistro() { return idUsuarioRegistro; }
    public void setIdUsuarioRegistro(Integer v) { this.idUsuarioRegistro = v; }

    public String getUsuarioRegistroNombre() { return usuarioRegistroNombre; }
    public void setUsuarioRegistroNombre(String v) { this.usuarioRegistroNombre = v; }

    public Integer getIdUsuarioCompleta() { return idUsuarioCompleta; }
    public void setIdUsuarioCompleta(Integer v) { this.idUsuarioCompleta = v; }

    public String getUsuarioCompletaNombre() { return usuarioCompletaNombre; }
    public void setUsuarioCompletaNombre(String v) { this.usuarioCompletaNombre = v; }

    public Integer getCantidadPlanificada() { return cantidadPlanificada; }
    public void setCantidadPlanificada(Integer v) { this.cantidadPlanificada = v; }

    public Integer getCantidadProducida() { return cantidadProducida; }
    public void setCantidadProducida(Integer v) { this.cantidadProducida = v; }

    public String getEstado() { return estado; }
    public void setEstado(String v) { this.estado = v; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime v) { this.fechaCreacion = v; }

    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDateTime v) { this.fechaInicio = v; }

    public LocalDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDateTime v) { this.fechaFin = v; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String v) { this.observaciones = v; }

    public List<ConsumoDTO> getConsumos() { return consumos; }
    public void setConsumos(List<ConsumoDTO> consumos) { this.consumos = consumos; }

    public BigDecimal getCostoMateriaPrima() { return costoMateriaPrima; }
    public void setCostoMateriaPrima(BigDecimal v) { this.costoMateriaPrima = v; }

    public BigDecimal getCostoManoObra() { return costoManoObra; }
    public void setCostoManoObra(BigDecimal v) { this.costoManoObra = v; }

    public BigDecimal getCostoIndirecto() { return costoIndirecto; }
    public void setCostoIndirecto(BigDecimal v) { this.costoIndirecto = v; }

    public BigDecimal getCostoTotal() { return costoTotal; }
    public void setCostoTotal(BigDecimal v) { this.costoTotal = v; }

    public BigDecimal getCostoUnitarioProducido() { return costoUnitarioProducido; }
    public void setCostoUnitarioProducido(BigDecimal v) { this.costoUnitarioProducido = v; }

    public static class ConsumoDTO {
        private Integer idConsumo;
        private Integer idMateriaPrima;
        private String materiaPrimaNombre;
        private String unidadMedida;
        private BigDecimal cantidadTeorica;
        private BigDecimal cantidadReal;
        private BigDecimal merma;
        private BigDecimal costoUnitarioSnapshot;
        private BigDecimal costoLinea;

        public ConsumoDTO() {}

        public BigDecimal getCostoUnitarioSnapshot() { return costoUnitarioSnapshot; }
        public void setCostoUnitarioSnapshot(BigDecimal v) { this.costoUnitarioSnapshot = v; }

        public BigDecimal getCostoLinea() { return costoLinea; }
        public void setCostoLinea(BigDecimal v) { this.costoLinea = v; }

        public Integer getIdConsumo() { return idConsumo; }
        public void setIdConsumo(Integer v) { this.idConsumo = v; }

        public Integer getIdMateriaPrima() { return idMateriaPrima; }
        public void setIdMateriaPrima(Integer v) { this.idMateriaPrima = v; }

        public String getMateriaPrimaNombre() { return materiaPrimaNombre; }
        public void setMateriaPrimaNombre(String v) { this.materiaPrimaNombre = v; }

        public String getUnidadMedida() { return unidadMedida; }
        public void setUnidadMedida(String v) { this.unidadMedida = v; }

        public BigDecimal getCantidadTeorica() { return cantidadTeorica; }
        public void setCantidadTeorica(BigDecimal v) { this.cantidadTeorica = v; }

        public BigDecimal getCantidadReal() { return cantidadReal; }
        public void setCantidadReal(BigDecimal v) { this.cantidadReal = v; }

        public BigDecimal getMerma() { return merma; }
        public void setMerma(BigDecimal v) { this.merma = v; }
    }
}
