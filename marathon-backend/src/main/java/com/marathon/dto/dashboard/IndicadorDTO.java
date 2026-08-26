package com.marathon.dto.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Un indicador del dashboard, con todo lo necesario para pintarlo bien (D1).
 *
 * <p><b>Por que no basta con devolver un numero.</b> El dashboard anterior
 * devolvia cifras sueltas, y eso hacia imposible distinguir tres situaciones que
 * se leen igual en pantalla pero significan cosas opuestas:
 *
 * <ul>
 *   <li>«el valor es cero» — no hubo pedidos;</li>
 *   <li>«no hay datos en este periodo» — puede que si hubiera, pero no en esta
 *       ventana;</li>
 *   <li>«no se pudo calcular» — fallo la consulta, o la base no guarda el dato.</li>
 * </ul>
 *
 * <p>Las tres acababan mostrando un 0 grande. El campo {@link #estado()} las
 * separa, y el navegador solo tiene que elegir plantilla: no calcula nada ni
 * decide nada.
 *
 * <p>{@link #periodo()} y {@link #base()} viajan con el valor a proposito.
 * «Ventas: 4.300» no dice nada; «Ventas: 4.300 · ultimos 30 dias · suma de
 * pedido.total de pedidos no anulados» si.
 */
public record IndicadorDTO(

        /** Identificador estable, para que el frontend no dependa del titulo. */
        String clave,

        /** Titulo visible. */
        String titulo,

        /** Unidad: "pedidos", "referencias", "%", "$"... Vacio si no aplica. */
        String unidad,

        /** El valor. {@code null} cuando el estado no es {@code ok} ni {@code parcial}. */
        BigDecimal valor,

        /**
         * Sobre cuantos. Es lo que convierte «8.106 anulados» en «3,5% de los
         * creados». {@code null} cuando el indicador no es una proporcion.
         */
        BigDecimal denominador,

        /** Periodo legible: «Ultimos 30 dias (26 jul - 25 ago)» o «Ahora mismo». */
        String periodo,

        /** De donde sale la cifra, en una linea. Se muestra en la tarjeta. */
        String base,

        /** Contra que se compara. {@code null} si no hay comparacion. */
        ComparacionDTO comparacion,

        /** {@code ok} | {@code vacio} | {@code sin_dato} | {@code parcial} | {@code error} */
        String estado,

        /** Motivo, cuando el estado no es {@code ok}. Se muestra en lugar del valor. */
        String nota,

        /** Pantalla donde se actua sobre este indicador. Un numero sin accion no sirve. */
        String enlace
) {

    public static final String OK = "ok";
    public static final String VACIO = "vacio";
    public static final String SIN_DATO = "sin_dato";
    public static final String PARCIAL = "parcial";
    public static final String ERROR = "error";

    /** Valor normal. Si es cero, se marca como {@code vacio} con su explicacion. */
    public static IndicadorDTO ok(String clave, String titulo, String unidad, BigDecimal valor,
                                  String periodo, String base, String enlace) {
        boolean sinNada = valor == null || valor.compareTo(BigDecimal.ZERO) == 0;
        return new IndicadorDTO(clave, titulo, unidad, valor, null, periodo, base, null,
                sinNada ? VACIO : OK,
                sinNada ? "Ninguno en este período" : null,
                enlace);
    }

    /**
     * Valor con su denominador: «220 referencias» sobre «1.999 con mínimo
     * definido». El navegador escribe «220 de 1.999» y no divide nada.
     */
    public static IndicadorDTO sobre(String clave, String titulo, String unidad,
                                     BigDecimal valor, BigDecimal denominador,
                                     String periodo, String base, String enlace) {
        boolean sinBase = denominador == null || denominador.compareTo(BigDecimal.ZERO) == 0;
        return new IndicadorDTO(clave, titulo, unidad, valor, denominador, periodo, base, null,
                sinBase ? VACIO : OK,
                sinBase ? "Sin datos sobre los que calcular en este período" : null,
                enlace);
    }

    /**
     * Porcentaje <b>calculado en el servidor</b>. Se pasan numerador y
     * denominador y sale el tanto por ciento ya hecho, con el tamaño de la
     * muestra en {@link #denominador()}: un 50% sobre 2 casos y un 50% sobre
     * 18.114 no se leen igual, y la tarjeta enseña los dos datos.
     *
     * <p>Si el denominador es cero no se devuelve «0%» —que se leeria como «no
     * se anula nada»— sino {@code vacio} con su explicacion.
     */
    public static IndicadorDTO porcentaje(String clave, String titulo,
                                          BigDecimal numerador, BigDecimal denominador,
                                          String periodo, String base, String enlace) {
        if (denominador == null || denominador.compareTo(BigDecimal.ZERO) == 0) {
            return new IndicadorDTO(clave, titulo, "%", null, denominador, periodo, base, null,
                    VACIO, "Sin datos sobre los que calcular en este período", enlace);
        }
        BigDecimal pct = (numerador == null ? BigDecimal.ZERO : numerador)
                .multiply(BigDecimal.valueOf(100))
                .divide(denominador, 1, RoundingMode.HALF_UP);
        return new IndicadorDTO(clave, titulo, "%", pct, denominador, periodo, base, null,
                OK, null, enlace);
    }

    /**
     * El dato no se puede calcular con lo que hay en la base. Distinto de cero, y
     * se pinta distinto: nunca se rellena con un 0 para que la tarjeta «quede
     * bonita».
     */
    public static IndicadorDTO sinDato(String clave, String titulo, String motivo, String enlace) {
        // La base va vacia y no repitiendo el motivo: el motivo ya ocupa el sitio
        // del valor, y verlo dos veces en la misma tarjeta solo hace ruido.
        return new IndicadorDTO(clave, titulo, "", null, null, "—", "", null,
                SIN_DATO, motivo, enlace);
    }

    /**
     * Se calcula, pero no sobre todos los registros. La cobertura se dice en la
     * tarjeta para que nadie lea el numero como si fuera completo.
     */
    public static IndicadorDTO parcial(String clave, String titulo, String unidad, BigDecimal valor,
                                       BigDecimal denominador, String periodo, String base,
                                       String cobertura, String enlace) {
        return new IndicadorDTO(clave, titulo, unidad, valor, denominador, periodo, base, null,
                PARCIAL, cobertura, enlace);
    }

    /** La consulta fallo. El usuario ve el motivo, no un cero. */
    public static IndicadorDTO error(String clave, String titulo, String motivo, String enlace) {
        return new IndicadorDTO(clave, titulo, "", null, null, "—", "", null,
                ERROR, motivo, enlace);
    }

    /** Devuelve una copia con la comparacion añadida. */
    public IndicadorDTO conComparacion(ComparacionDTO c) {
        return new IndicadorDTO(clave, titulo, unidad, valor, denominador, periodo, base, c,
                estado, nota, enlace);
    }
}
