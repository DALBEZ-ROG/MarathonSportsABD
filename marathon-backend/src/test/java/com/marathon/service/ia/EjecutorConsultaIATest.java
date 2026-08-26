package com.marathon.service.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * L2 — la segunda barrera: aunque el validador dejara pasar una escritura,
 * PostgreSQL la rechaza porque la transaccion es de solo lectura (D-04).
 *
 * <p>Estas pruebas llaman al ejecutor <b>saltandose</b> al validador a
 * proposito. Es la unica forma de comprobar que las dos barreras son de verdad
 * independientes: si solo se probara el camino completo, un fallo del validador
 * dejaria la segunda barrera sin verificar.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("L2 - ejecucion en transaccion de solo lectura")
class EjecutorConsultaIATest {

    @Autowired private EjecutorConsultaIA ejecutor;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("una lectura normal funciona y devuelve los nombres de columna")
    void lecturaNormalFunciona() {
        List<Map<String, Object>> filas =
                ejecutor.ejecutar("SELECT count(*) AS total FROM categoria");

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0)).containsKey("total");
    }

    @Test
    @DisplayName("un INSERT es rechazado por el motor, no por la aplicacion")
    void elMotorRechazaLaEscritura() {
        long antes = contarCategorias();

        assertThatThrownBy(() -> ejecutor.ejecutar(
                    "INSERT INTO categoria (nombre, descripcion) VALUES ('__colada__', 'x')"))
                .as("la transaccion de solo lectura debe impedir la escritura")
                .isInstanceOf(Exception.class);

        assertThat(contarCategorias())
                .as("no puede haberse insertado nada")
                .isEqualTo(antes);
    }

    @Test
    @DisplayName("un UPDATE tampoco pasa")
    void elMotorRechazaElUpdate() {
        assertThatThrownBy(() -> ejecutor.ejecutar("UPDATE categoria SET nombre = nombre || 'x'"))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("un bloque DO con una escritura dentro tampoco pasa")
    void elMotorRechazaElBloqueDo() {
        long antes = contarCategorias();

        assertThatThrownBy(() -> ejecutor.ejecutar(
                    "DO $$ BEGIN EXECUTE 'INS' || 'ERT INTO categoria (nombre) VALUES (''__colada2__'')'; END $$"))
                .isInstanceOf(Exception.class);

        assertThat(contarCategorias()).isEqualTo(antes);
    }

    private long contarCategorias() {
        Long n = jdbc.queryForObject("select count(*) from categoria", Long.class);
        return n == null ? -1 : n;
    }
}
