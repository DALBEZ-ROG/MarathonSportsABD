package com.marathon.dto.dashboard;

import java.math.BigDecimal;

/**
 * Una fila del top de productos <b>del periodo</b> (D1).
 *
 * <p>Existe separado de {@link TopProductoDTO} porque aquel se calculaba sobre
 * el historico completo —dos años— y se presentaba en el dashboard como si
 * fuera del periodo en curso. Aqui la ventana es la misma que la del resto de
 * indicadores y viaja declarada en el resumen.
 */
public record TopProductoPeriodoDTO(String nombre, BigDecimal unidades) {
}
