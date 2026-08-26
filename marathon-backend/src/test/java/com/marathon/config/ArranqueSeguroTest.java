package com.marathon.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L10 — la aplicacion se niega a arrancar con un secreto publico (D-26), y el
 * login tiene freno (D-25).
 *
 * <p>Se prueba la clase directamente en vez de levantar contextos: montar un
 * {@code ApplicationContext} por cada variante del secreto costaria ~8 segundos
 * cada uno para comprobar cuatro condiciones de una sola clase sin estado.
 */
@DisplayName("L10 - arranque seguro y freno al login")
class ArranqueSeguroTest {

    private ComprobacionesDeArranque conSecreto(String secreto) {
        ComprobacionesDeArranque c = new ComprobacionesDeArranque();
        establecer(c, "secretoJwt", secreto);
        establecer(c, "datosDemo", false);
        return c;
    }

    private static void establecer(Object destino, String campo, Object valor) {
        try {
            var f = destino.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(destino, valor);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("no se pudo preparar la prueba", e);
        }
    }

    // ---------------------------------------------------------------- D-26 ---

    @Test
    @DisplayName("el secreto de ejemplo del repositorio impide arrancar")
    void elSecretoDeEjemploImpideArrancar() {
        // Es el valor literal de application.properties, que esta versionado.
        // Con el, cualquiera puede firmarse un token de administrador.
        assertThatThrownBy(() -> conSecreto("defaultDevSecretChangeInProduction").comprobar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("publicado en el repositorio");
    }

    @Test
    @DisplayName("un secreto vacio impide arrancar")
    void secretoVacioImpideArrancar() {
        assertThatThrownBy(() -> conSecreto("").comprobar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no esta definido");
    }

    @Test
    @DisplayName("un secreto demasiado corto impide arrancar")
    void secretoCortoImpideArrancar() {
        assertThatThrownBy(() -> conSecreto("corto").comprobar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("al menos 32");
    }

    @Test
    @DisplayName("un secreto propio y suficientemente largo deja arrancar")
    void secretoValidoDejaArrancar() {
        conSecreto("un-secreto-propio-de-mas-de-treinta-y-dos-caracteres").comprobar();
    }

    // ---------------------------------------------------------------- D-25 ---

    @Test
    @DisplayName("tras N fallos se bloquea, y un acierto limpia el contador")
    void elLimitadorCuenta() {
        LimitadorDeIntentos limitador = new LimitadorDeIntentos();
        establecer(limitador, "maxIntentos", 3);
        establecer(limitador, "ventanaMinutos", 15L);

        String correo = "victima@marathon.com";
        String ip = "10.0.0.1";

        assertThat(limitador.permitido(correo, ip)).isTrue();
        limitador.registrarFallo(correo, ip);
        limitador.registrarFallo(correo, ip);
        assertThat(limitador.permitido(correo, ip))
                .as("con 2 de 3 todavia se puede intentar")
                .isTrue();

        limitador.registrarFallo(correo, ip);
        assertThat(limitador.permitido(correo, ip))
                .as("al tercer fallo se cierra la puerta")
                .isFalse();

        limitador.registrarExito(correo, ip);
        assertThat(limitador.permitido(correo, ip))
                .as("un login correcto borra el historial")
                .isTrue();
    }

    @Test
    @DisplayName("el contador es por correo Y por IP")
    void elContadorEsPorCorreoYPorIp() {
        LimitadorDeIntentos limitador = new LimitadorDeIntentos();
        establecer(limitador, "maxIntentos", 2);
        establecer(limitador, "ventanaMinutos", 15L);

        limitador.registrarFallo("a@marathon.com", "10.0.0.1");
        limitador.registrarFallo("a@marathon.com", "10.0.0.1");

        assertThat(limitador.permitido("a@marathon.com", "10.0.0.1")).isFalse();
        assertThat(limitador.permitido("a@marathon.com", "10.0.0.2"))
                .as("otra IP no hereda el bloqueo: si no, se podria bloquear a cualquiera a proposito")
                .isTrue();
        assertThat(limitador.permitido("b@marathon.com", "10.0.0.1"))
                .as("otro correo desde la misma IP tampoco: una oficina comparte salida")
                .isTrue();
    }
}
