package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.dto.picking.PickingPedidoDTO;
import com.marathon.model.Pedido;
import com.marathon.soporte.FixturaVenta;

/**
 * F77 — la región de destino sale de la ciudad, no del teclado.
 *
 * <p><b>De dónde sale.</b> Lo preguntó el dueño mirando la ventana de confirmar
 * empaque:
 *
 * <blockquote>«¿región de destino qué es? ¿No sería la ciudad o dirección de
 * casa o empresa del cliente que lo pidió?»</blockquote>
 *
 * <p>Tenía razón, y era el fallo de modelado más gordo de la pantalla: la región
 * se <b>tecleaba</b> en cada empaque cuando ya se sabía —el pedido tiene
 * cliente, el cliente tiene ciudad, y la ciudad está en una región—. Pedir a
 * mano un dato deducible es la forma segura de que acabe mal escrito: en 19.000
 * pedidos había un solo valor, «Sierra», de una prueba.
 *
 * <p>Esta prueba fija las dos mitades del arreglo: que la región <b>viaja</b>
 * desde la ciudad del cliente hasta la cola de empaque, y que la base <b>no
 * admite</b> una región inventada.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("F77 · el destino del empaque sale de la ciudad del cliente")
class DestinoDelEmpaqueTest {

    @Autowired private PickingService pickingService;
    @Autowired private FixturaVenta fixtura;
    @Autowired private JdbcTemplate jdbc;

    private Integer idCiudad;

    @BeforeEach
    void preparar() {
        fixtura.empezar();
        idCiudad = jdbc.queryForObject(
                "select id_ciudad from cliente where id_cliente = ?", Integer.class, fixtura.getIdCliente());
    }

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    /** Busca el pedido en la cola de empaque, que es donde lo lee la pantalla. */
    private PickingPedidoDTO enLaColaDeEmpaque(Integer idPedido) {
        List<PickingPedidoDTO> cola = pickingService.listarPedidosParaEmpacar(0, 50).getContent();
        return cola.stream().filter(p -> idPedido.equals(p.getIdPedido())).findFirst()
                .orElseThrow(() -> new AssertionError(
                        "El pedido " + idPedido + " no aparece en la cola de empaque"));
    }

    @Test
    @DisplayName("la ciudad y su región llegan a la cola de empaque sin teclearlas")
    void elDestinoViajaSolo() {
        jdbc.update("update ciudad set region = 'Oriente' where id_ciudad = ?", idCiudad);
        Pedido pedido = fixtura.pedidoListoParaEmpacar(2);

        PickingPedidoDTO enCola = enLaColaDeEmpaque(pedido.getIdPedido());

        String nombreCiudad = jdbc.queryForObject(
                "select nombre from ciudad where id_ciudad = ?", String.class, idCiudad);
        assertThat(enCola.getCiudadDestino()).isEqualTo(nombreCiudad);
        assertThat(enCola.getRegionDestino()).isEqualTo("Oriente");
    }

    @Test
    @DisplayName("una ciudad sin clasificar no inventa región: llega nula y la pantalla lo dice")
    void sinRegionNoSeInventa() {
        jdbc.update("update ciudad set region = null where id_ciudad = ?", idCiudad);
        Pedido pedido = fixtura.pedidoListoParaEmpacar(1);

        PickingPedidoDTO enCola = enLaColaDeEmpaque(pedido.getIdPedido());

        // La ciudad sí se sabe; la región no, y no se rellena con nada.
        assertThat(enCola.getCiudadDestino()).isNotBlank();
        assertThat(enCola.getRegionDestino()).isNull();
    }

    @Test
    @DisplayName("la base no admite una región inventada")
    void elCheckDefiendeLasCuatroRegiones() {
        for (String buena : new String[] { "Costa", "Sierra", "Oriente", "Insular" }) {
            jdbc.update("update ciudad set region = ? where id_ciudad = ?", buena, idCiudad);
        }

        assertThatThrownBy(() ->
                jdbc.update("update ciudad set region = 'Amazonia' where id_ciudad = ?", idCiudad))
                .hasMessageContaining("chk_ciudad_region");

        // Y no vale cualquier mayúscula: el valor es exactamente uno de los cuatro.
        assertThatThrownBy(() ->
                jdbc.update("update ciudad set region = 'costa' where id_ciudad = ?", idCiudad))
                .hasMessageContaining("chk_ciudad_region");
    }

    @Test
    @DisplayName("el transportista deja de ser texto libre, y el catálogo es de solo lectura")
    void elCatalogoDeTransportistas() {
        Integer activos = jdbc.queryForObject(
                "select count(*) from transportista where estado = 'activo'", Integer.class);
        assertThat(activos).isGreaterThan(0);

        // La F77 concede SELECT y NADA MAS, a proposito: dar de alta un
        // transportista es una decision de negocio, no una casilla de la
        // pantalla de almacen. La base lo rechaza aunque quien lo intente sea
        // el usuario administrador de la aplicacion.
        //
        // (PostgreSQL devuelve 42501 y Spring lo traduce a BadSqlGrammar, asi
        // que lo que se comprueba es que NO pasa, no el texto del error.)
        String yaExiste = jdbc.queryForObject(
                "select nombre from transportista order by id_transportista limit 1", String.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into transportista (nombre, estado) values (?, 'activo')", yaExiste + " bis"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);

        // Y las defensas del catalogo estan puestas, aunque desde aqui no se
        // puedan provocar: unicidad del nombre y estado cerrado.
        List<String> reglas = jdbc.queryForList(
                "select conname from pg_constraint where conrelid = 'transportista'::regclass",
                String.class);
        assertThat(reglas).contains("uq_transportista_nombre", "chk_transportista_estado");
    }
}
