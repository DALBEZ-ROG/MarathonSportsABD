package com.marathon;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prueba de humo del arnes (L0 del plan).
 *
 * <p>Es la primera prueba automatizada del proyecto. Comprueba dos cosas que
 * hasta ahora no comprobaba nadie:
 *
 * <ol>
 *   <li><b>Que las pruebas no apuntan a la base real.</b> application.properties
 *       trae {@code spring.profiles.active=local}, y ese perfil apunta a
 *       mod_venta_inve. Si la activacion del perfil {@code test} dejara de tener
 *       precedencia, las pruebas escribirian en produccion sin avisar. Esta
 *       prueba convierte esa suposicion en un hecho verificado.</li>
 *   <li><b>Que las entidades JPA cuadran con el esquema real.</b> Con
 *       {@code ddl-auto=validate}, si una {@code @Entity} deja de coincidir con
 *       su tabla el contexto ni se construye. Como la aplicacion no migra el
 *       esquema (lo hacen scripts SQL a mano), es la unica red contra un
 *       desajuste entre codigo y base.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class ContextoAplicacionTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("las pruebas apuntan a la base de pruebas, nunca a la real")
    void nuncaApuntaALaBaseReal() throws Exception {
        try (Connection conexion = dataSource.getConnection()) {
            String url = conexion.getMetaData().getURL();

            assertThat(url)
                    .as("URL del datasource durante las pruebas")
                    .contains("mod_venta_inve_test");

            // No basta con que contenga el nombre correcto: "mod_venta_inve_test"
            // contiene a "mod_venta_inve", asi que se comprueba ademas que la
            // base a la que conecta el driver es exactamente la de pruebas.
            assertThat(conexion.getCatalog())
                    .as("base de datos a la que conecta el driver")
                    .isEqualTo("mod_venta_inve_test");
        }
    }

    @Test
    @DisplayName("el contexto arranca y las 36 entidades JPA validan contra el esquema")
    void contextoArranca() {
        // Sin cuerpo a proposito. El trabajo lo hace el arranque del contexto:
        // con ddl-auto=validate, llegar hasta aqui significa que Hibernate
        // comprobo cada @Entity contra su tabla y no encontro discrepancias.
        assertThat(dataSource).isNotNull();
    }
}
