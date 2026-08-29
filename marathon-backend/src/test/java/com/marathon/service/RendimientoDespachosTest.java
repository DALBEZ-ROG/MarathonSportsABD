package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.dto.empaque.EmpaqueRequestDTO;
import com.marathon.model.Bodega;
import com.marathon.model.Pedido;
import com.marathon.soporte.FixturaVenta;

import jakarta.persistence.EntityManagerFactory;

/**
 * L16 — el listado de despachos deja de ser un N+1 (D-28).
 *
 * <p>La prueba no mide tiempo: cuenta consultas. El tiempo depende de la maquina
 * y haria la prueba inestable; el numero de consultas es una propiedad del
 * codigo y no cambia sola.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@DisplayName("L16 - rendimiento del listado de despachos")
class RendimientoDespachosTest {

    @Autowired private EmpaqueService empaqueService;
    @Autowired private FixturaVenta fixtura;
    @Autowired private EntityManagerFactory emf;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Bodega bodega;

    @BeforeEach
    void prepararDatos() {
        fixtura.empezar();
        bodega = fixtura.bodegaConStock("A", 500);
        // F84: el filtro por region ya no mira una columna del pedido —no
        // existe—, sino la region de la CIUDAD del cliente. Antes valia
        // cualquier texto inventado ("RegionL16"); ahora tiene que ser una de
        // las cuatro de verdad, y la de este cliente se fija aqui.
        jdbc.update("update ciudad set region = 'Oriente' where id_ciudad = "
                  + "(select id_ciudad from cliente where id_cliente = ?)",
                  fixtura.getIdCliente());
    }

    @AfterEach
    void borrarDatos() {
        fixtura.limpiar();
    }

    @Test
    @DisplayName("una pagina de 10 despachos no dispara una consulta por pedido")
    void elListadoNoEsUnNMasUno() {
        EmpaqueRequestDTO empaque = new EmpaqueRequestDTO();
        empaque.setNumeroHu("HU-L16");
        empaque.setIdTransportista(1); // Servientrega, del catalogo que siembra la F77

        for (int i = 0; i < 10; i++) {
            Pedido p = fixtura.pedidoRecogidoDesde(1, bodega);
            empaqueService.confirmarEmpaque(p.getIdPedido(), empaque, fixtura.getIdUsuario());
        }

        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        var pagina = empaqueService.listarDespachados(0, 10, "Oriente", null, null);

        long consultas = stats.getPrepareStatementCount();

        assertThat(pagina.getContent()).hasSize(10);
        assertThat(consultas)
                .as("antes eran 1 (pagina) + 10 x 2 (obtener por pedido) = 21; "
                  + "ahora la pagina y los detalles de todos los pedidos van en bloque")
                .isLessThanOrEqualTo(6);
    }
}
