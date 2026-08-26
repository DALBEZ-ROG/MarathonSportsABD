package com.marathon.dto.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * El valor del periodo anterior, para que la cifra actual se pueda leer (D1).
 *
 * <p>«18.114 pedidos» no dice si vamos bien o mal. «18.114, un 11,7% mas que los
 * 16.218 de los 30 dias previos» si. La comparacion es opcional: solo la llevan
 * los indicadores que miden un periodo, nunca los que miden el estado actual
 * («pedidos atascados ahora mismo» no tiene periodo anterior contra el que
 * compararse).
 */
public record ComparacionDTO(

        /** El valor del periodo anterior. */
        BigDecimal valor,

        /** Que periodo es: «30 dias previos (26 jun - 26 jul)». */
        String etiqueta,

        /**
         * Variacion porcentual respecto al periodo anterior, con un decimal.
         * {@code null} cuando el periodo anterior fue cero: dividir entre cero no
         * da «infinito por ciento», da «no comparable», y se dice asi.
         */
        BigDecimal variacion
) {

    /**
     * Construye la comparacion calculando la variacion.
     *
     * @param actual   valor del periodo en curso
     * @param anterior valor del periodo anterior
     */
    public static ComparacionDTO de(BigDecimal actual, BigDecimal anterior, String etiqueta) {
        BigDecimal base = anterior == null ? BigDecimal.ZERO : anterior;
        BigDecimal variacion = null;
        if (actual != null && base.compareTo(BigDecimal.ZERO) != 0) {
            variacion = actual.subtract(base)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(base.abs(), 1, RoundingMode.HALF_UP);
        }
        return new ComparacionDTO(base, etiqueta, variacion);
    }
}
