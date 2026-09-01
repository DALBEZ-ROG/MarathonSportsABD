package com.marathon.service.ia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import reactor.core.Exceptions;

/**
 * F95 — un plazo agotado tiene que reconocerse aunque venga envuelto.
 *
 * <p><b>El fallo que prueba.</b> El 2026-09-01, con Gemini saturado, una
 * pregunta tardo 100 s y acabo en «No se pudo hablar con el asistente. Vuelve a
 * intentarlo», que es el mensaje de un fallo desconocido. No era desconocido: el
 * tercer intento se habia quedado sin plazo, y eso el sistema sabe explicarlo.
 *
 * <p>Se colaba porque Reactor no propaga la {@link TimeoutException} tal cual —la
 * envuelve en una excepcion suya—, asi que preguntar por el tipo de la de fuera
 * daba siempre que no. La comprobacion tiene que mirar la cadena de causas
 * entera, y eso es lo unico que se verifica aqui.
 *
 * <p>No necesita contexto de Spring ni red: es una funcion sobre una excepcion.
 */
@DisplayName("F95 - reconocer el plazo agotado de Gemini")
class ProveedorGeminiTest {

    @Test
    @DisplayName("la TimeoutException desnuda se reconoce")
    void reconoceLaDirecta() {
        assertThat(ProveedorGemini.esPlazoAgotado(new TimeoutException("se acabo"))).isTrue();
    }

    @Test
    @DisplayName("envuelta por Reactor tambien: es como llega de verdad")
    void reconoceLaEnvueltaPorReactor() {
        // Exactamente lo que aparecio en el log:
        //   reactor.core.Exceptions$ReactiveException:
        //     java.util.concurrent.TimeoutException: Did not observe any item or
        //     terminal signal within 60000ms in 'flatMap'
        RuntimeException comoLlega = Exceptions.propagate(
                new TimeoutException("Did not observe any item or terminal signal within 60000ms"));

        assertThat(comoLlega).isNotInstanceOf(TimeoutException.class);   // por eso se escapaba
        assertThat(ProveedorGemini.esPlazoAgotado(comoLlega)).isTrue();
    }

    @Test
    @DisplayName("aunque este a varias capas de profundidad")
    void reconoceLaAnidada() {
        Throwable hondo = new IllegalStateException("arriba",
                new RuntimeException("en medio", new TimeoutException("abajo")));

        assertThat(ProveedorGemini.esPlazoAgotado(hondo)).isTrue();
    }

    @Test
    @DisplayName("lo que NO es un plazo agotado sigue sin serlo")
    void noConfundeOtrosFallos() {
        assertThat(ProveedorGemini.esPlazoAgotado(new IOException("conexion cerrada"))).isFalse();
        assertThat(ProveedorGemini.esPlazoAgotado(
                new IllegalStateException("clave invalida", new IOException("nada que ver")))).isFalse();
    }

    @Test
    @DisplayName("una cadena de causas circular no cuelga la comprobacion")
    void noSeQuedaDandoVueltas() {
        // Una excepcion que se tiene a si misma por causa es legal en Java, y un
        // recorrido ingenuo de la cadena se quedaria dando vueltas para siempre.
        RuntimeException seMuerdeLaCola = new RuntimeException("yo mismo") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        assertThat(ProveedorGemini.esPlazoAgotado(seMuerdeLaCola)).isFalse();
    }
}
