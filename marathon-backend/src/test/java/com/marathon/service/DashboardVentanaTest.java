package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.marathon.dto.dashboard.DashboardResumenDTO;
import com.marathon.dto.dashboard.IndicadorDTO;
import com.marathon.repository.DashboardConsultas;
import com.marathon.service.DashboardResumenService.Ventana;

/**
 * D1 — la aritmetica del dashboard, sin base de datos.
 *
 * <p>Aqui va todo lo que se puede comprobar de forma exacta: los limites de la
 * ventana temporal, el calculo del porcentaje y de la variacion, y —lo que mas
 * importa— que <b>ninguna de las situaciones «no hay dato» acabe siendo un
 * cero</b>. Las cifras contra datos reales se comprueban en
 * {@link DashboardResumenTest}.
 */
@DisplayName("D1 - ventana, porcentajes y estados del dashboard")
class DashboardVentanaTest {

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    // ==================================================================
    @Nested
    @DisplayName("la ventana temporal")
    class DeLaVentana {

        @Test
        @DisplayName("30d abarca hoy incluido y 30 dias en total")
        void treintaDiasIncluyeHoy() {
            LocalDate hoy = LocalDate.now();
            Ventana v = Ventana.de("30d");

            assertThat(v.desde).isEqualTo(hoy.minusDays(29));
            assertThat(v.hastaExcl)
                    .as("el limite superior es exclusivo y vale mañana, para que el dia "
                      + "en curso entre entero sin depender de la hora")
                    .isEqualTo(hoy.plusDays(1));
            assertThat(v.desde.datesUntil(v.hastaExcl).count()).isEqualTo(30);
        }

        @Test
        @DisplayName("el periodo previo se toca con el actual: ni se solapa ni deja un dia fuera")
        void elPrevioEncajaJustoAntes() {
            Ventana v = Ventana.de("30d");

            assertThat(v.previoDesde.datesUntil(v.desde).count())
                    .as("el previo mide lo mismo que el actual, o la comparacion engaña")
                    .isEqualTo(30);
            assertThat(v.previoDesde.plusDays(30))
                    .as("el previo termina justo donde empieza el actual")
                    .isEqualTo(v.desde);
        }

        @Test
        @DisplayName("7d y 90d se reconocen; cualquier otra cosa cae a 30d sin fallar")
        void clavesReconocidas() {
            assertThat(Ventana.de("7d").dias).isEqualTo(7);
            assertThat(Ventana.de("90d").dias).isEqualTo(90);
            assertThat(Ventana.de("  30D  ").dias).isEqualTo(30);

            // Un parametro mal escrito en la barra de direcciones no puede dejar
            // al usuario sin tablero.
            assertThat(Ventana.de("ayer").dias).isEqualTo(30);
            assertThat(Ventana.de(null).dias).isEqualTo(30);
        }

        @Test
        @DisplayName("la etiqueta lleva el rango de fechas, no solo «ultimos 30 dias»")
        void laEtiquetaDiceLasFechas() {
            Ventana v = Ventana.de("30d");
            assertThat(v.etiqueta)
                    .contains("30")
                    .contains(String.valueOf(v.desde.getDayOfMonth()))
                    .contains(String.valueOf(LocalDate.now().getYear()));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("cero, «no hay dato» y «no se pudo calcular» son tres cosas distintas")
    class DeLosEstados {

        @Test
        @DisplayName("un valor de cero sale como vacio con su explicacion, no como ok")
        void ceroNoEsOk() {
            IndicadorDTO i = IndicadorDTO.ok("x", "X", "pedidos", BigDecimal.ZERO,
                    "Últimos 30 días", "base", "/x");

            assertThat(i.estado()).isEqualTo(IndicadorDTO.VACIO);
            assertThat(i.nota()).isNotBlank();
        }

        @Test
        @DisplayName("«no hay dato» no lleva valor: no se rellena con cero")
        void sinDatoNoLlevaCero() {
            IndicadorDTO i = IndicadorDTO.sinDato("y", "Y", "la base no lo guarda", null);

            assertThat(i.estado()).isEqualTo(IndicadorDTO.SIN_DATO);
            assertThat(i.valor()).isNull();
            assertThat(i.nota()).isEqualTo("la base no lo guarda");
        }

        @Test
        @DisplayName("un porcentaje sin denominador no es 0%, es vacio")
        void porcentajeSinBase() {
            IndicadorDTO i = IndicadorDTO.porcentaje("z", "Z", BigDecimal.ZERO, BigDecimal.ZERO,
                    "Últimos 30 días", "base", "/z");

            assertThat(i.estado()).isEqualTo(IndicadorDTO.VACIO);
            assertThat(i.valor())
                    .as("un 0% se leeria como «no se anula nada», que no es lo mismo que "
                      + "«no hubo pedidos que anular»")
                    .isNull();
        }

        @Test
        @DisplayName("el porcentaje se calcula en el servidor y conserva el tamaño de la muestra")
        void porcentajeCalculado() {
            IndicadorDTO i = IndicadorDTO.porcentaje("tasa", "Tasa",
                    new BigDecimal("654"), new BigDecimal("18114"),
                    "Últimos 30 días", "base", "/p");

            assertThat(i.valor()).isEqualByComparingTo("3.6");
            assertThat(i.unidad()).isEqualTo("%");
            assertThat(i.denominador())
                    .as("un 50% sobre 2 casos y un 50% sobre 18.114 no se leen igual")
                    .isEqualByComparingTo("18114");
        }

        @Test
        @DisplayName("la variacion no se calcula cuando el periodo anterior fue cero")
        void variacionNoComparable() {
            assertThat(com.marathon.dto.dashboard.ComparacionDTO
                    .de(new BigDecimal("10"), BigDecimal.ZERO, "previo").variacion())
                    .as("dividir entre cero no da «infinito por ciento», da «no comparable»")
                    .isNull();

            assertThat(com.marathon.dto.dashboard.ComparacionDTO
                    .de(new BigDecimal("18114"), new BigDecimal("16218"), "previo").variacion())
                    .isEqualByComparingTo("11.7");
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("una consulta rota no tumba el tablero")
    class DelFallo {

        /**
         * Sustituye una sola consulta por una que revienta. Las demas devuelven
         * cifras normales, para que la prueba distinga «el tablero aguanta» de
         * «el tablero no llego a construirse».
         */
        private DashboardConsultas consultasConUnaRota() {
            return new DashboardConsultas(null) {
                @Override public BigDecimal esperandoPicking() { return new BigDecimal("7"); }
                @Override public BigDecimal listosParaEmpacar() { return new BigDecimal("3"); }
                @Override public Par lineasPorRecoger() {
                    return new Par(new BigDecimal("4"), new BigDecimal("10"));
                }
                @Override public BigDecimal movimientos(LocalDate d, LocalDate h) {
                    return new BigDecimal("2");
                }
                @Override public Par referenciasBajoMinimo() {
                    throw new IllegalStateException("ERROR: permiso denegado a la tabla inventario");
                }
            };
        }

        @Test
        @DisplayName("la tarjeta que falla sale en error y las otras cuatro se pintan igual")
        void elFalloSeAisla() {
            autenticar("ROLE_OPERADOR DE BODEGA");
            DashboardResumenDTO r = new DashboardResumenService(consultasConUnaRota()).resumen("30d");

            assertThat(r.indicadores()).hasSize(5);

            IndicadorDTO rota = buscar(r, "ref_bajo_minimo");
            assertThat(rota.estado()).isEqualTo(IndicadorDTO.ERROR);
            assertThat(rota.valor())
                    .as("un cero se leeria como «no hay nada bajo minimo», que es justo "
                      + "lo contrario de lo que puede estar pasando")
                    .isNull();
            assertThat(rota.nota())
                    .as("al usuario se le dice el tipo de fallo, no el mensaje del motor: "
                      + "«permiso denegado a la tabla inventario» es un mapa de la base")
                    .doesNotContain("inventario")
                    .doesNotContain("permiso denegado");

            assertThat(buscar(r, "esperando_picking").valor()).isEqualByComparingTo("7");
            assertThat(r.indicadores())
                    .filteredOn(i -> IndicadorDTO.ERROR.equals(i.estado()))
                    .hasSize(1);
        }

        @Test
        @DisplayName("un rol sin tablero definido lo dice; no devuelve una pantalla vacia")
        void rolDesconocido() {
            autenticar("ROLE_JEFE DE ALGO");
            DashboardResumenDTO r = new DashboardResumenService(consultasConUnaRota()).resumen("30d");

            assertThat(r.indicadores()).hasSize(1);
            assertThat(r.indicadores().get(0).estado()).isEqualTo(IndicadorDTO.SIN_DATO);
        }
    }

    // ==================================================================

    private static void autenticar(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("prueba", null,
                        List.of(new SimpleGrantedAuthority(authority))));
    }

    private static IndicadorDTO buscar(DashboardResumenDTO r, String clave) {
        return r.indicadores().stream()
                .filter(i -> clave.equals(i.clave()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no hay indicador '" + clave + "'"));
    }
}
