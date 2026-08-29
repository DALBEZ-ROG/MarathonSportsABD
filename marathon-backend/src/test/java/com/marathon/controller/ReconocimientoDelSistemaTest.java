package com.marathon.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.dto.ia.IAResponseDTO;

/**
 * F87 — lo que se puede aprender del sistema sin haber entrado.
 *
 * <p><b>De dónde sale.</b> El dueño pidió sacar todo el SQL del código y
 * meterlo en procedimientos almacenados, porque «un hacker ve la petición y ya
 * sabe cómo está mi base de datos». Se comprobó primero, y el SQL <b>no viaja</b>:
 * lo que sale del servidor son objetos JSON que ni siquiera se parecen al
 * esquema —{@code precioVenta} es la columna {@code producto.precio}, y
 * {@code precioCompra} vive en otra tabla—. Mover las consultas a
 * procedimientos no habría cambiado un solo byte de lo que ve un atacante, y
 * habría saltado los 2.407 privilegios por columna de los seis roles, que es la
 * mejor defensa que tiene este sistema.
 *
 * <p><b>Pero la sospecha encontró dos agujeros de verdad</b>, y son los que
 * fijan estas pruebas. Los dos son de <i>reconocimiento</i>: no dejan entrar,
 * pero le dibujan el mapa a quien lo intente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("F87 · lo que se puede aprender del sistema sin entrar")
class ReconocimientoDelSistemaTest {

    @LocalServerPort private int puerto;
    @Autowired private TestRestTemplate http;
    @Autowired private IAController iaController;

    private String url(String ruta) {
        return "http://localhost:" + puerto + ruta;
    }

    // -----------------------------------------------------------------------
    // 1. La documentación de la API estaba abierta de par en par
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("la documentación de la API no se sirve sin haber entrado")
    void laDocumentacionNoEsPublica() {
        // Lo que habia: /v3/api-docs devolvia 200 y 117 KB con los 130
        // endpoints, sus parametros y los nombres de TODOS los campos, incluidos
        // password, numeroDocumento y tipoDocumento. Sin ninguna contrasena.
        // Es el trabajo de reconocimiento que un atacante hace primero, hecho.
        for (String ruta : List.of("/v3/api-docs", "/swagger-ui/index.html", "/swagger-ui.html")) {
            ResponseEntity<String> respuesta = http.getForEntity(url(ruta), String.class);

            assertThat(respuesta.getStatusCode().value())
                    .as("%s no puede devolver 200 a quien no ha entrado", ruta)
                    .isNotEqualTo(200);

            assertThat(respuesta.getBody() == null ? "" : respuesta.getBody())
                    .as("%s no puede enseñar el mapa de la aplicacion", ruta)
                    .doesNotContain("/api/usuarios")
                    .doesNotContain("numeroDocumento");
        }
    }

    @Test
    @DisplayName("y tampoco los endpoints de diagnóstico")
    void elDiagnosticoTampoco() {
        for (String ruta : List.of("/actuator", "/actuator/env", "/actuator/mappings")) {
            assertThat(http.getForEntity(url(ruta), String.class).getStatusCode().value())
                    .as("%s expondria la configuracion del servidor", ruta)
                    .isNotEqualTo(200);
        }
    }

    // -----------------------------------------------------------------------
    // 2. El asistente mandaba el SQL a quien no debía verlo
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("el SQL del asistente NO viaja a quien no es administrador")
    void elSqlNoViajaAlSupervisor() {
        IAResponseDTO respuesta = new IAResponseDTO();
        respuesta.setSql("SELECT p.nombre, SUM(d.cantidad) FROM detalle_pedido d "
                       + "JOIN producto p ON p.id_producto = d.id_producto GROUP BY p.nombre");
        respuesta.setExplicacion("Los productos mas vendidos");

        var supervisor = new UsernamePasswordAuthenticationToken("sup", null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR E-COMMERCE"),
                        new SimpleGrantedAuthority("ia:consultar")));

        IAResponseDTO filtrada = iaController.ocultarSqlSalvoAlAdministrador(respuesta, supervisor);

        assertThat(filtrada.getSql())
                .as("la pantalla ya lo escondia, pero el servidor lo mandaba igual: "
                  + "esconder en el navegador no es esconder")
                .isNull();
        assertThat(filtrada.getExplicacion())
                .as("la explicacion si se queda: es la respuesta, no el esquema")
                .isNotNull();
    }

    @Test
    @DisplayName("pero el administrador sí lo recibe, que es quien debe poder comprobarlo")
    void elAdministradorSiLoRecibe() {
        IAResponseDTO respuesta = new IAResponseDTO();
        respuesta.setSql("SELECT 1");

        var admin = new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));

        assertThat(iaController.ocultarSqlSalvoAlAdministrador(respuesta, admin).getSql())
                .isEqualTo("SELECT 1");
    }

    @Test
    @DisplayName("sin sesión tampoco, aunque ahí no debería llegarse nunca")
    void sinSesionTampoco() {
        IAResponseDTO respuesta = new IAResponseDTO();
        respuesta.setSql("SELECT 1");

        assertThat(iaController.ocultarSqlSalvoAlAdministrador(respuesta, null).getSql())
                .as("un nulo no puede acabar en «no es administrador, luego se lo doy»")
                .isNull();
    }
}
