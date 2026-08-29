package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.service.ia.EjecutorConsultaIA;

/**
 * F83 — la consulta del asistente se ejecuta aunque traiga su propio LIMIT.
 *
 * <p><b>El fallo que fija esta prueba.</b> El ejecutor acotaba las filas con
 * {@code setMaxResults(500)}. Sobre una consulta <b>nativa</b>, eso hace que
 * Hibernate le pegue al final un {@code fetch first ? rows only}; si el SQL ya
 * traía su propio {@code LIMIT} —y lo trae en cuanto alguien pregunta «los 3
 * productos más vendidos»— quedaban las dos cláusulas seguidas y PostgreSQL
 * respondía <i>«error de sintaxis en o cerca de fetch»</i>.
 *
 * <p>Dicho de otro modo: <b>la pregunta más natural que se le puede hacer al
 * asistente era justo la que no funcionaba</b>, y el mensaje que le llegaba al
 * usuario era «prueba a reformular la pregunta», que apunta a donde no es.
 *
 * <p>Ahora el tope se pone envolviendo la consulta, que respeta el LIMIT de
 * dentro. Estas pruebas no llaman a ningún modelo: ejecutan SQL como el que
 * devuelve, que es la parte que tiene que aguantar.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("F83 · el ejecutor del asistente respeta el LIMIT que trae la consulta")
class ConsultaDelAsistenteTest {

    @Autowired private EjecutorConsultaIA ejecutor;

    @Test
    @DisplayName("una consulta con LIMIT propio se ejecuta, que es lo que se rompía")
    void conLimitePropio() {
        List<Map<String, Object>> filas = ejecutor.ejecutar(
                "SELECT id_producto, nombre FROM producto ORDER BY id_producto LIMIT 3");

        assertThat(filas).hasSizeLessThanOrEqualTo(3);
        assertThat(filas).allSatisfy(f -> assertThat(f).containsKeys("id_producto", "nombre"));
    }

    @Test
    @DisplayName("y con GROUP BY y ORDER BY, que es la forma de un ranking")
    void unRankingCompleto() {
        List<Map<String, Object>> filas = ejecutor.ejecutar("""
                SELECT p.nombre AS producto, SUM(d.cantidad) AS unidades
                  FROM detalle_pedido d
                  JOIN producto p ON p.id_producto = d.id_producto
                 GROUP BY p.nombre
                 ORDER BY unidades DESC
                 LIMIT 3
                """);

        assertThat(filas).hasSizeLessThanOrEqualTo(3);
        assertThat(filas).allSatisfy(f -> assertThat(f).containsKeys("producto", "unidades"));
    }

    @Test
    @DisplayName("un punto y coma final no rompe la envoltura")
    void conPuntoYComaFinal() {
        assertThatCode(() -> ejecutor.ejecutar("SELECT 1 AS uno;"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sin LIMIT propio sigue habiendo tope: el de la casa")
    void sinLimitePropioSigueAcotado() {
        // Si el tope hubiera desaparecido al quitar setMaxResults, esta consulta
        // se traeria las 230.000 filas de pedido a memoria.
        List<Map<String, Object>> filas = ejecutor.ejecutar("SELECT id_pedido FROM pedido");

        assertThat(filas)
                .as("el tope de la casa son 500 filas")
                .hasSizeLessThanOrEqualTo(500);
    }
}
