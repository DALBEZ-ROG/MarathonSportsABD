package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.model.Usuario;
import com.marathon.soporte.FixturaVenta;

/**
 * L11 — la bitacora vuelve a saber quien (D-16, D-18) y diagnostico de D-17.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("L11 - trazabilidad")
class TrazabilidadTest {

    @Autowired private ProductoService productoService;
    @Autowired private FixturaVenta fixtura;
    @Autowired private UsuarioDetailsService usuarioDetailsService;
    @Autowired private JdbcTemplate jdbc;

    private Usuario admin;

    @BeforeEach
    void prepararDatos() {
        fixtura.empezar();
        admin = (Usuario) usuarioDetailsService.loadUserByUsername("admin@marathon.com");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
    }

    @AfterEach
    void borrarDatos() {
        SecurityContextHolder.clearContext();
        fixtura.limpiar();
    }

    // ---------------------------------------------------------------- D-16 ---

    @Test
    @DisplayName("dar de baja un producto queda auditado CON el usuario que lo hizo")
    void laBajaDeProductoQuedaAuditadaConAutor() {
        Integer idProducto = fixtura.getIdProducto();

        productoService.eliminar(idProducto);

        List<Map<String, Object>> filas = jdbc.queryForList(
                "select campo, valor_anterior, valor_nuevo, usuario_app, usuario_bd "
              + "  from auditoria_cambios "
              + " where tabla = 'producto' and pk_valor = ? and operacion = 'UPDATE'",
                String.valueOf(idProducto));

        assertThat(filas)
                .as("el trigger trg_auditoria_producto debe haber registrado el cambio de estado")
                .isNotEmpty();

        assertThat(filas)
                .as("y con el id del usuario: es lo que aporta fijarContextoUsuario() "
                  + "dentro de la misma transaccion")
                .allSatisfy(f -> assertThat(String.valueOf(f.get("usuario_app")))
                        .isEqualTo(String.valueOf(admin.getIdUsuario())));

        assertThat(filas)
                .anySatisfy(f -> {
                    assertThat(f.get("campo")).isEqualTo("estado");
                    assertThat(f.get("valor_nuevo")).isEqualTo("inactivo");
                });
    }

    // ---------------------------------------------------------------- D-17 ---

    @Test
    @DisplayName("diagnostico: el trigger de auditoria SI escribe; la tabla estaba vacia por otro motivo")
    void diagnosticoDeLaTablaVacia() {
        // La auditoria decia que auditoria_cambios tenia 0 filas con 5 triggers
        // activos, y no se podia distinguir entre "los triggers no escriben"
        // (defecto) y "la tabla se vacio tras las pruebas de la F40" (dato).
        //
        // Esta prueba lo resuelve: provoca un cambio auditable y comprueba que
        // aparece la fila. Si pasa, los triggers funcionan y la tabla estaba
        // vacia porque nadie habia hecho cambios a traves de la aplicacion —
        // coherente con que los 230.000 pedidos y 108 productos los insertaran
        // los scripts de poblado, que corren como postgres y no por la app.
        long antes = contarAuditoria();

        productoService.eliminar(fixtura.getIdProducto());

        assertThat(contarAuditoria())
                .as("si esto no crece, el defecto es real y hay que mirar los triggers")
                .isGreaterThan(antes);
    }

    private long contarAuditoria() {
        Long n = jdbc.queryForObject("select count(*) from auditoria_cambios", Long.class);
        return n == null ? 0 : n;
    }
}
