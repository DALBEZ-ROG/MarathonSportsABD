package com.marathon.dto.bom;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ListaMaterialesResponseDTO {

    private Integer idBom;
    private BigDecimal cantidadNecesaria;
    private String estado;
    private LocalDateTime createdAt;
    private MateriaPrimaSimpleDTO materiaPrima;

    public ListaMaterialesResponseDTO() {}

    public Integer getIdBom() { return idBom; }
    public void setIdBom(Integer idBom) { this.idBom = idBom; }

    public BigDecimal getCantidadNecesaria() { return cantidadNecesaria; }
    public void setCantidadNecesaria(BigDecimal cantidadNecesaria) { this.cantidadNecesaria = cantidadNecesaria; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public MateriaPrimaSimpleDTO getMateriaPrima() { return materiaPrima; }
    public void setMateriaPrima(MateriaPrimaSimpleDTO materiaPrima) { this.materiaPrima = materiaPrima; }

    public static class MateriaPrimaSimpleDTO {
        private Integer idMateriaPrima;
        private String nombre;
        private String unidadMedida;

        public MateriaPrimaSimpleDTO() {}

        public MateriaPrimaSimpleDTO(Integer idMateriaPrima, String nombre, String unidadMedida) {
            this.idMateriaPrima = idMateriaPrima;
            this.nombre = nombre;
            this.unidadMedida = unidadMedida;
        }

        public Integer getIdMateriaPrima() { return idMateriaPrima; }
        public void setIdMateriaPrima(Integer idMateriaPrima) { this.idMateriaPrima = idMateriaPrima; }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public String getUnidadMedida() { return unidadMedida; }
        public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }
    }
}
