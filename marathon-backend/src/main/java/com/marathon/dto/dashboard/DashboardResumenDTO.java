package com.marathon.dto.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que devuelve {@code GET /api/dashboard/resumen} (D1).
 *
 * <p>Viaja el periodo una sola vez a nivel de resumen — ademas de dentro de cada
 * indicador — para que la pantalla pueda encabezar el dashboard con «Ultimos 30
 * dias (28 jul - 26 ago)» sin tener que deducirlo de las tarjetas.
 *
 * <p>{@link #rol()} es informativo: el servidor ya ha decidido que indicadores
 * corresponden. El navegador no filtra nada.
 */
public record DashboardResumenDTO(

        /** Rol funcional cuyo tablero se devuelve, tal como esta en la tabla {@code rol}. */
        String rol,

        /** Titulo del tablero: «Tablero de Operador de Bodega». */
        String titulo,

        /** Clave del periodo pedido: {@code 7d}, {@code 30d} o {@code 90d}. */
        String periodo,

        /** Periodo en texto: «Ultimos 30 dias (28 jul - 26 ago de 2026)». */
        String periodoEtiqueta,

        LocalDate desde,

        LocalDate hasta,

        /** Momento del calculo. Una cifra sin hora no se sabe si esta fresca. */
        LocalDateTime generadoEn,

        List<IndicadorDTO> indicadores,

        /**
         * Ranking de productos del periodo. Lista vacia cuando el rol no lo
         * recibe o cuando no hubo ventas en la ventana — la pantalla distingue
         * las dos cosas por el estado del resto de indicadores del periodo.
         */
        List<TopProductoPeriodoDTO> topProductos,

        /**
         * Serie diaria del periodo, un punto por dia sin huecos. Lista vacia
         * cuando el rol no la recibe.
         */
        List<SerieDiaDTO> serie
) {
}
