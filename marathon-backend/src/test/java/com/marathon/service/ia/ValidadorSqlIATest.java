package com.marathon.service.ia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * L2 — el asistente IA no puede escribir en la base (D-04) ni rechaza consultas
 * legitimas (D-30).
 *
 * <p>No necesita contexto de Spring: el validador es una funcion pura sobre el
 * texto del SQL.
 */
@DisplayName("L2 - validador del SQL del asistente IA")
class ValidadorSqlIATest {

    private final ValidadorSqlIA validador = new ValidadorSqlIA();

    // ---------------------------------------------------------------- D-04 ---

    @Test
    @DisplayName("el bloque DO que burlaba la lista de palabras prohibidas ahora se rechaza")
    void rechazaElBloqueDoQueBurlabaLaListaDePalabras() {
        // Ni "DELETE" ni ninguna otra palabra vigilada aparece literalmente:
        // la comprobacion anterior (upper.contains(...)) lo dejaba pasar y
        // PostgreSQL lo ejecutaba, borrando la tabla pedido.
        String ataque = "DO $$ BEGIN EXECUTE 'DEL' || 'ETE FROM pedido'; END $$;";

        ValidadorSqlIA.Veredicto veredicto = validador.validar(ataque);

        assertThat(veredicto.permitido()).isFalse();
    }

    @ParameterizedTest
    @DisplayName("ninguna sentencia de escritura o de administracion pasa")
    @ValueSource(strings = {
        "DELETE FROM pedido",
        "UPDATE pedido SET total = 0",
        "INSERT INTO categoria (nombre) VALUES ('x')",
        "DROP TABLE pedido",
        "TRUNCATE TABLE pedido",
        "ALTER TABLE pedido ADD COLUMN x int",
        "CREATE TABLE trampa (id int)",
        "GRANT ALL ON pedido TO PUBLIC",          // no estaba en la lista anterior
        "REVOKE ALL ON pedido FROM PUBLIC",       // tampoco
        "COPY pedido TO '/tmp/fuga.csv'",         // tampoco
    })
    void rechazaEscriturasYAdministracion(String sql) {
        assertThat(validador.validar(sql).permitido())
                .as("no deberia permitirse: %s", sql)
                .isFalse();
    }

    @Test
    @DisplayName("no se admite mas de una sentencia por consulta")
    void rechazaVariasSentencias() {
        assertThat(validador.validar("SELECT 1 FROM pedido; DELETE FROM pedido").permitido())
                .isFalse();
    }

    @Test
    @DisplayName("no se pueden leer las tablas del modelo de seguridad")
    void rechazaTablasFueraDeLaListaBlanca() {
        ValidadorSqlIA.Veredicto veredicto =
                validador.validar("SELECT correo, password FROM usuario");

        assertThat(veredicto.permitido()).isFalse();
        assertThat(veredicto.motivo()).contains("usuario");
    }

    @ParameterizedTest
    @DisplayName("tampoco por la puerta de atras de un JOIN o una subconsulta")
    @ValueSource(strings = {
        "SELECT p.id_pedido, u.correo FROM pedido p JOIN usuario u ON u.id_usuario = p.id_usuario",
        "SELECT * FROM pedido WHERE id_usuario IN (SELECT id_usuario FROM usuario)",
        "SELECT * FROM auditoria_cambios",
        "SELECT * FROM log_accion",
    })
    void rechazaTablasProhibidasEnJoinsYSubconsultas(String sql) {
        assertThat(validador.validar(sql).permitido())
                .as("no deberia permitirse: %s", sql)
                .isFalse();
    }

    // ---------------------------------------------------------------- D-30 ---

    @ParameterizedTest
    @DisplayName("las consultas legitimas con created_at y updated_at vuelven a funcionar")
    @ValueSource(strings = {
        "SELECT created_at FROM producto",
        "SELECT updated_at FROM pedido",
        "SELECT p.nombre, p.created_at, p.updated_at FROM producto p WHERE p.estado = 'activo'",
    })
    void permiteConsultasConCreatedAtYUpdatedAt(String sql) {
        // CREATED_AT contiene "CREATE" y UPDATED_AT contiene "UPDATE": la
        // comprobacion por subcadenas rechazaba las tres con el mensaje
        // "Query no permitida por seguridad".
        assertThat(validador.validar(sql).permitido())
                .as("deberia permitirse: %s", sql)
                .isTrue();
    }

    @ParameterizedTest
    @DisplayName("las consultas de negocio normales pasan")
    @ValueSource(strings = {
        "SELECT count(*) FROM pedido WHERE estado = 'pendiente'",
        "SELECT p.nombre, sum(d.cantidad) FROM detalle_pedido d "
            + "JOIN producto p ON p.id_producto = d.id_producto GROUP BY p.nombre",
        "WITH ventas AS (SELECT id_pedido, total FROM pedido) SELECT sum(total) FROM ventas",
        "SELECT i.stock_actual, b.nombre FROM inventario i JOIN bodega b ON b.id_bodega = i.id_bodega",
    })
    void permiteConsultasDeNegocio(String sql) {
        assertThat(validador.validar(sql).permitido())
                .as("deberia permitirse: %s", sql)
                .isTrue();
    }

    @Test
    @DisplayName("un texto que no es SQL no se ejecuta")
    void rechazaTextoQueNoEsSql() {
        assertThat(validador.validar("esto no es una consulta").permitido()).isFalse();
        assertThat(validador.validar("").permitido()).isFalse();
        assertThat(validador.validar(null).permitido()).isFalse();
    }
}
