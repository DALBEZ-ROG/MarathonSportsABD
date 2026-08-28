package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F53 (D-43) — las rutas del tablero llevan a alguna parte.
 *
 * <p>Cada tarjeta de indicador trae la ruta de su enlace «Ver detalle», y esa
 * ruta es del FRONTEND, no de la API. Cinco de las catorce estaban puestas con
 * el nombre del endpoint ({@code /ordenes-compra}, {@code /ordenes-produccion},
 * {@code /analisis-costos}, {@code /recepciones}, {@code /pedidos-especiales})
 * y no existen como pantalla: el enlace dejaba al usuario en el inicio. Afectaba
 * al inicio de Pedidos, Compras y Produccion, que es casi todo lo que ven esos
 * tres roles al entrar.
 *
 * <p>Nada en el compilador ata las dos cosas: son un literal de Java y un
 * literal de TypeScript en repositorios distintos del mismo proyecto. Esta
 * prueba los ata.
 *
 * <p>No arranca Spring a proposito: son dos ficheros de texto, y montar el
 * contexto entero para leerlos costaria veinte segundos por nada.
 */
@DisplayName("F53 - las rutas del tablero existen en el frontend (D-43)")
class RutasDelTableroTest {

    private static final Path SERVICIO =
            Path.of("src", "main", "java", "com", "marathon", "service", "DashboardResumenService.java");
    private static final Path RUTAS_ANGULAR =
            Path.of("..", "marathon-frontend", "src", "app", "app.routes.ts");

    @Test
    @DisplayName("toda ruta que emite un indicador existe en app.routes.ts")
    void ningunaRutaLlevaANingunSitio() throws IOException {
        Set<String> delTablero = rutasQueEmiteElTablero();
        assertThat(delTablero)
                .as("si esto sale vacio, el roto es el test: no encontro las rutas del servicio")
                .isNotEmpty();

        Set<String> delFrontend = rutasQueExistenEnAngular();
        assertThat(delFrontend)
                .as("si esto sale vacio, el roto es el test: no encontro app.routes.ts")
                .isNotEmpty();

        List<String> huerfanas = new ArrayList<>();
        for (String ruta : delTablero) {
            if (!delFrontend.contains(ruta.substring(1))) {
                huerfanas.add(ruta);
            }
        }

        assertThat(huerfanas)
                .as("estas rutas las emite el tablero y NO existen en app.routes.ts: el enlace "
                    + "«Ver detalle» de esas tarjetas deja al usuario en /inicio. Rutas conocidas "
                    + "en el frontend: " + delFrontend)
                .isEmpty();
    }

    /** El ultimo argumento de cada IndicadorDTO: un literal que empieza por '/'. */
    private Set<String> rutasQueEmiteElTablero() throws IOException {
        String fuente = Files.readString(SERVICIO, StandardCharsets.UTF_8);
        // Solo el cuerpo: el javadoc de la clase cita a proposito rutas de API
        // como ejemplo de lo que NO hay que poner.
        int cuerpo = fuente.indexOf("public class DashboardResumenService");
        Matcher m = Pattern.compile("\"(/[a-z0-9][a-z0-9/-]*)\"").matcher(fuente.substring(cuerpo));
        Set<String> rutas = new LinkedHashSet<>();
        while (m.find()) {
            rutas.add(m.group(1));
        }
        return rutas;
    }

    /** Los {@code path: '...'} de app.routes.ts, sin comodines ni vacios. */
    private Set<String> rutasQueExistenEnAngular() throws IOException {
        String fuente = Files.readString(RUTAS_ANGULAR, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("path:\\s*'([^']*)'").matcher(fuente);
        Set<String> rutas = new LinkedHashSet<>();
        String padre = null;
        while (m.find()) {
            String p = m.group(1);
            if (p.isEmpty() || p.equals("**")) {
                continue;
            }
            rutas.add(p);
            // Las rutas hijas se declaran sueltas ('categorias') y viven bajo su
            // padre ('datos-maestros/categorias'). Se registran las dos formas:
            // aqui no se puede saber cual es cual sin analizar el arbol, y para
            // lo que hace falta —¿existe esta pantalla?— basta.
            if (padre != null && !p.contains("/")) {
                rutas.add(padre + "/" + p);
            }
            if (!p.contains("/") && !p.contains(":")) {
                padre = p;
            }
        }
        return rutas;
    }
}
