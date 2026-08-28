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
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * F48 (D-13) — la matriz de permisos existe, esta completa y se aplica.
 *
 * <p>El defecto era que 49 permisos no los consultaba nadie, y la prueba de que
 * no servian era que el rol «Encargado de Produccion» tenia 0 de 49 y funcionaba
 * igual. Estas pruebas cierran las tres puertas por las que eso volveria a
 * pasar:
 *
 * <ol>
 *   <li>que un rol se quede sin permisos (con la comprobacion encendida, eso ya
 *       no es un detalle cosmetico: es un rol sin acceso a nada);</li>
 *   <li>que una anotacion {@code @PreAuthorize} nombre un permiso que no existe
 *       en la tabla — un permiso mal escrito no falla al compilar, falla en
 *       produccion como un 403 que nadie entiende;</li>
 *   <li>que se anada un endpoint nuevo sin permiso y vuelva a haber una parte
 *       del sistema que la matriz no cubre.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("F48 - matriz de permisos (D-13)")
class MatrizPermisosTest {

    @Autowired private JdbcTemplate jdbc;

    private static final Path CONTROLADORES =
            Path.of("src", "main", "java", "com", "marathon", "controller");

    /** Extrae el 'modulo:accion' de cada @PreAuthorize("hasAuthority('...')"). */
    private static final Pattern PERMISO_EN_ANOTACION =
            Pattern.compile("@PreAuthorize\\(\"hasAuthority\\('([^']+)'\\)\"\\)");

    @Test
    @DisplayName("ningun rol se queda con cero permisos")
    void ningunRolEnCero() {
        List<String> vacios = jdbc.queryForList(
                "SELECT r.nombre FROM rol r "
                + "WHERE NOT EXISTS (SELECT 1 FROM rol_permiso rp WHERE rp.id_rol = r.id_rol) "
                + "ORDER BY r.nombre",
                String.class);

        assertThat(vacios)
                .as("un rol sin permisos ya no puede hacer NADA: es el estado en el que "
                    + "estaba 'Encargado de Produccion' antes de la F48")
                .isEmpty();
    }

    @Test
    @DisplayName("Encargado de Produccion tiene permisos de verdad, no cero de 49")
    void produccionYaNoEstaEnCero() {
        Integer suyos = jdbc.queryForObject(
                "SELECT count(*) FROM rol_permiso rp JOIN rol r ON r.id_rol = rp.id_rol "
                + "WHERE r.nombre = 'Encargado de Producción'", Integer.class);

        assertThat(suyos).isNotNull();
        assertThat(suyos).isGreaterThan(0);

        // Y no cualquier permiso: los de su trabajo.
        List<String> deProduccion = jdbc.queryForList(
                "SELECT p.modulo || ':' || p.accion FROM rol_permiso rp "
                + "JOIN rol r ON r.id_rol = rp.id_rol JOIN permiso p ON p.id_permiso = rp.id_permiso "
                + "WHERE r.nombre = 'Encargado de Producción'", String.class);

        assertThat(deProduccion).contains(
                "produccion:crear", "produccion:iniciar", "produccion:completar",
                "materia_prima:movimiento", "bom:editar", "productos:ver");
    }

    @Test
    @DisplayName("todo permiso nombrado en un @PreAuthorize existe en la tabla permiso")
    void ningunPermisoInventado() throws IOException {
        Set<String> enElCodigo = permisosUsadosEnControladores();
        assertThat(enElCodigo)
                .as("si esto sale vacio, el que esta roto es el test: no encontro los controladores")
                .isNotEmpty();

        List<String> enLaBase = jdbc.queryForList(
                "SELECT modulo || ':' || accion FROM permiso", String.class);

        assertThat(enLaBase)
                .as("un @PreAuthorize con un permiso que no existe es un 403 permanente "
                    + "para todo el mundo, y no falla al compilar")
                .containsAll(enElCodigo);
    }

    @Test
    @DisplayName("todo permiso usado en el codigo lo tiene al menos un rol")
    void ningunPermisoHuerfano() throws IOException {
        Set<String> enElCodigo = permisosUsadosEnControladores();

        List<String> concedidos = jdbc.queryForList(
                "SELECT DISTINCT p.modulo || ':' || p.accion FROM permiso p "
                + "JOIN rol_permiso rp ON rp.id_permiso = p.id_permiso", String.class);

        List<String> sinDueno = new ArrayList<>(enElCodigo);
        sinDueno.removeAll(concedidos);

        assertThat(sinDueno)
                .as("un permiso que no tiene ningun rol cierra ese endpoint para todos, "
                    + "administrador incluido")
                .isEmpty();
    }

    @Test
    @DisplayName("el Administrador tiene todos los permisos que existen")
    void administradorLoTieneTodo() {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM permiso", Integer.class);
        Integer delAdmin = jdbc.queryForObject(
                "SELECT count(*) FROM rol_permiso rp JOIN rol r ON r.id_rol = rp.id_rol "
                + "WHERE r.nombre = 'Administrador'", Integer.class);

        assertThat(delAdmin)
                .as("si al Administrador le falta un permiso, hay una pantalla que nadie "
                    + "puede abrir y no hay a quien pedirsela")
                .isEqualTo(total);
    }

    @Test
    @DisplayName("cada metodo de controlador esta cubierto, o esta en la lista de excepciones razonadas")
    void ningunEndpointSinPermiso() throws IOException {
        // Las excepciones son deliberadas y estan documentadas en SecurityConfig:
        //  - login/refresh/logout son publicos por definicion;
        //  - /api/dashboard/resumen y el cambio de contrasena propia son de los
        //    seis roles, y darles un permiso obligaria a concederselo a todos,
        //    que es una forma retorcida de escribir "cualquiera con sesion";
        //  - los dos cambios de estado se comprueban DENTRO del servicio, porque
        //    una misma llamada hace varias cosas con permisos distintos
        //    (ver com.marathon.config.Permisos).
        Set<String> exentos = Set.of(
                "AuthController.login", "AuthController.refresh", "AuthController.logout",
                "DashboardController.getResumen",
                "UsuarioController.cambiarPassword",
                "PedidoController.cambiarEstado",
                "OrdenCompraController.cambiarEstado");

        List<String> sinCubrir = new ArrayList<>();
        try (Stream<Path> ficheros = Files.list(CONTROLADORES)) {
            for (Path fichero : ficheros.filter(f -> f.toString().endsWith("Controller.java")).toList()) {
                String clase = fichero.getFileName().toString().replace(".java", "");
                String texto = Files.readString(fichero, StandardCharsets.UTF_8);
                String[] lineas = texto.split("\r?\n");

                boolean anotadoArriba = false;
                for (String linea : lineas) {
                    if (linea.contains("@PreAuthorize")) {
                        anotadoArriba = true;
                        continue;
                    }
                    Matcher m = Pattern.compile("^\\s+public\\s+.*?\\b(\\w+)\\(").matcher(linea);
                    if (m.find()) {
                        String metodo = m.group(1);
                        if (metodo.equals(clase)) {
                            continue;   // el constructor
                        }
                        if (!anotadoArriba && !exentos.contains(clase + "." + metodo)) {
                            sinCubrir.add(clase + "." + metodo);
                        }
                        anotadoArriba = false;
                    }
                }
            }
        }

        assertThat(sinCubrir)
                .as("un endpoint sin permiso vuelve a dejar una parte del sistema fuera de "
                    + "la matriz, que es el defecto D-13. Si la exencion es correcta, "
                    + "anadela a la lista 'exentos' de esta prueba y di por que")
                .isEmpty();
    }

    private Set<String> permisosUsadosEnControladores() throws IOException {
        Set<String> encontrados = new LinkedHashSet<>();
        try (Stream<Path> ficheros = Files.list(CONTROLADORES)) {
            for (Path fichero : ficheros.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = PERMISO_EN_ANOTACION.matcher(
                        Files.readString(fichero, StandardCharsets.UTF_8));
                while (m.find()) {
                    encontrados.add(m.group(1));
                }
            }
        }
        // Los cuatro que se comprueban dentro de un servicio y por tanto no
        // aparecen en ninguna anotacion.
        encontrados.add("pedidos:editar");
        encontrados.add("pedidos:anular");
        encontrados.add("compras:aprobar");
        encontrados.add("compras:rechazar");
        encontrados.add("compras:cancelar");
        return encontrados;
    }
}
