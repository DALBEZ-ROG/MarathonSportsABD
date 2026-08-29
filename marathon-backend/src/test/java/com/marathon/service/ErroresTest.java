package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.exception.ErrorResponse;
import com.marathon.exception.GlobalExceptionHandler;
import com.marathon.exception.ValidationException;
import com.marathon.soporte.FixturaVenta;

/**
 * L9 — los errores dicen lo justo y con el codigo correcto (D-12, D-20).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("L9 - manejo de errores")
class ErroresTest {

    @Autowired private CategoriaService categoriaService;
    @Autowired private UnidadMedidaService unidadMedidaService;
    @Autowired private FixturaVenta fixtura;
    @Autowired private GlobalExceptionHandler manejador;

    @BeforeEach
    void prepararDatos() {
        fixtura.empezar();
    }

    @AfterEach
    void borrarDatos() {
        fixtura.limpiar();
    }

    // ---------------------------------------------------------------- D-20 ---

    @Test
    @DisplayName("borrar una categoria en uso da un error legible, no un 500 de PostgreSQL")
    void borrarCategoriaEnUsoDaErrorLegible() {
        Integer idCategoria = fixtura.getIdCategoriaEnUso();

        assertThatThrownBy(() -> categoriaService.eliminar(idCategoria))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("hay productos que la usan");
    }

    @Test
    @DisplayName("borrar una unidad de medida en uso tambien")
    void borrarUnidadEnUsoDaErrorLegible() {
        Integer idUnidad = fixtura.getIdUnidadEnUso();

        assertThatThrownBy(() -> unidadMedidaService.eliminar(idUnidad))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("productos o materias primas");
    }

    // ---------------------------------------------------------------- D-12 ---

    @Test
    @DisplayName("un 500 no revela el mensaje interno")
    void unErrorInternoNoRevelaElDetalle() {
        // Se simula lo que llegaria desde el driver de PostgreSQL.
        Exception fallo = new IllegalStateException(
                "ERROR: no se pudo ejecutar la sentencia [insert into usuario (correo, password) "
              + "values (?, ?)]; viola la restriccion unica uq_usuario_correo");

        ErrorResponse respuesta = manejador.handleGeneral(fallo).getBody();

        assertThat(respuesta).isNotNull();
        assertThat(respuesta.getStatus()).isEqualTo(500);
        assertThat(respuesta.getMessage())
                .as("el cuerpo NO puede contener SQL, tablas, columnas ni nombres de constraint")
                .doesNotContainIgnoringCase("insert")
                .doesNotContainIgnoringCase("usuario")
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("uq_")
                .doesNotContainIgnoringCase("restriccion");
        assertThat(respuesta.getMessage())
                .as("pero si una referencia para cruzarlo con el registro del servidor")
                .contains("Referencia:");
    }

    @Test
    @DisplayName("una violacion de integridad es 409, no 500")
    void violacionDeIntegridadEs409() {
        var ex = new org.springframework.dao.DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_producto_nombre\"");

        var respuesta = manejador.handleIntegridad(ex);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        var cuerpo = respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.getMessage())
                .doesNotContainIgnoringCase("uq_producto_nombre")
                .doesNotContainIgnoringCase("duplicate key");
    }

    @Test
    @DisplayName("un cuerpo JSON ilegible es 400, no 500")
    void cuerpoIlegibleEs400() {
        var ex = new org.springframework.http.converter.HttpMessageNotReadableException(
                "JSON parse error: Unexpected character",
                new org.springframework.mock.http.client.MockClientHttpResponse(new byte[0], 400));

        var respuesta = manejador.handleCuerpoIlegible(ex);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
    }

    // ---------------------------------------------------------------- F63 ---

    @Test
    @DisplayName("una dirección que no existe es 404, no 500")
    void unaRutaInexistenteEs404() {
        // Apareció recorriendo el flujo en el navegador: pedir una URL de API
        // que no existe devolvía «Error interno del servidor» y dejaba en el
        // registro un ERROR «Error no controlado» con su traza entera.
        //
        // Lo segundo es lo que de verdad estorba: un registro lleno de errores
        // que no son errores es un registro que nadie mira, y el día que caiga
        // un 500 de los de verdad estará enterrado entre ellos.
        var respuesta = manejador.handleRutaInexistente(
                new org.springframework.web.servlet.resource.NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "api/picking/pendientes"));

        assertThat(respuesta.getStatusCode().value())
                .as("la dirección es lo que está mal, no el servidor")
                .isEqualTo(404);

        ErrorResponse cuerpo = respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.getMessage()).doesNotContain("Referencia:");
        assertThat(cuerpo.getMessage())
                .as("y no debe seguir diciendo que el fallo es interno del servidor")
                .doesNotContainIgnoringCase("interno del servidor");
    }

    @Test
    @DisplayName("un parámetro que falta es un 400 que lo nombra, no un 500 anónimo")
    void unParametroQueFaltaEsDelCliente() {
        // F86, y es la misma historia que el 404 de arriba con otro disfraz.
        // Llamar a /api/auditoria/inventario/resumen sin su idProducto devolvía
        // 500 «Error interno del servidor» y dejaba en el registro un
        // ERROR «Error no controlado» con la traza entera. El servidor está
        // perfectamente: es la petición la que no trae lo que el endpoint pide.
        var falta = new org.springframework.web.bind.MissingServletRequestParameterException(
                "idProducto", "Integer");

        var respuesta = manejador.handleParametro(falta);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        var cuerpo = respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.getMessage())
                .as("tiene que decir QUÉ parámetro falta; si no, hay que adivinarlo")
                .contains("idProducto");
        assertThat(cuerpo.getMessage())
                .doesNotContainIgnoringCase("interno del servidor")
                .doesNotContain("Referencia:");
    }
}
