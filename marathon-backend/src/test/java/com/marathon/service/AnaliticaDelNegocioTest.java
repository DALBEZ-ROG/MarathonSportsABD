package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.dto.dashboard.AnaliticaDTO;
import com.marathon.model.Pedido;
import com.marathon.soporte.FixturaVenta;

/**
 * F80 — el análisis del negocio: qué se vende, quién compra y dónde.
 *
 * <p><b>De dónde sale.</b> Lo pidió el dueño: «métele otros gráficos extra de
 * producto más vendido, más comprado, mejor cliente… qué ciudad o región vende
 * más».
 *
 * <p><b>Qué fija esta prueba.</b> Tres cosas que no se ven en la pantalla y que,
 * si se rompen, la dejan mintiendo con buena cara:
 *
 * <ol>
 *   <li><b>Un pedido anulado no cuenta en ningún sitio.</b> Ni en el facturado,
 *       ni en el ranking de clientes. Contarlo sería premiar una venta que no
 *       ocurrió, y el error no se notaría: el gráfico saldría igual de bonito.
 *   <li><b>La ventana se traduce en el servidor</b>, y vuelve con la respuesta.
 *       Sin eso, la pantalla no puede decir de cuándo son las cifras.
 *   <li><b>La serie diaria trae un punto por día</b>, incluidos los días sin
 *       ventas. Un día que falta junta el 3 con el 7 como si fueran seguidos, y
 *       eso deforma la línea sin avisar.
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("F80 · el análisis del negocio cuenta lo que se vendió, y solo eso")
class AnaliticaDelNegocioTest {

    @Autowired private AnaliticaService analiticaService;
    @Autowired private FixturaVenta fixtura;
    @Autowired private JdbcTemplate jdbc;

    private Pedido pedido;

    @BeforeEach
    void preparar() {
        fixtura.empezar();
        pedido = fixtura.pedidoPendiente(3);
    }

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    /** Lo que la fixtura acaba de crear, buscado por el nombre del cliente. */
    private Map<String, Object> filaDelCliente(AnaliticaDTO dto) {
        String marca = jdbc.queryForObject(
                "select c.nombre || ' ' || c.apellido from cliente c "
                + "join pedido p on p.id_cliente = c.id_cliente where p.id_pedido = ?",
                String.class, pedido.getIdPedido());
        return dto.getMejoresClientes().stream()
                .filter(f -> marca.equals(f.get("nombre")))
                .findFirst().orElse(null);
    }

    @Test
    @DisplayName("un pedido recién creado entra en el resumen y en el ranking de clientes")
    void loQueSeVendeCuenta() {
        AnaliticaDTO dto = analiticaService.analitica("30d");

        assertThat(dto.getPedidos()).isGreaterThan(0);
        assertThat(filaDelCliente(dto))
                .as("el cliente del pedido tiene que salir en el ranking")
                .isNotNull();
    }

    @Test
    @DisplayName("un pedido anulado desaparece del facturado y del ranking")
    void loAnuladoNoCuenta() {
        AnaliticaDTO antes = analiticaService.analitica("30d");
        long pedidosAntes = antes.getPedidos();
        assertThat(filaDelCliente(antes)).isNotNull();

        jdbc.update("update pedido set estado = 'anulado' where id_pedido = ?", pedido.getIdPedido());

        AnaliticaDTO despues = analiticaService.analitica("30d");
        assertThat(despues.getPedidos())
                .as("el anulado deja de contarse")
                .isEqualTo(pedidosAntes - 1);
        assertThat(filaDelCliente(despues))
                .as("y el cliente, que no tenía otro pedido, sale del ranking")
                .isNull();
    }

    @Test
    @DisplayName("la ventana la traduce el servidor y vuelve con la respuesta")
    void laVentanaViajaEnLaRespuesta() {
        LocalDate hoy = LocalDate.now();

        AnaliticaDTO treinta = analiticaService.analitica("30d");
        assertThat(treinta.getDesde()).isEqualTo(hoy.minusDays(29));
        assertThat(treinta.getHasta()).isEqualTo(hoy);
        assertThat(treinta.getPeriodoEtiqueta()).isEqualTo("Últimos 30 días");

        // Una clave que no existe no revienta: cae en la de por defecto.
        AnaliticaDTO inventada = analiticaService.analitica("ayer por la tarde");
        assertThat(inventada.getDesde()).isEqualTo(hoy.minusDays(29));
    }

    @Test
    @DisplayName("ventana corta: un punto por día, contando los días sin ventas")
    void laSerieDiariaNoDejaHuecos() {
        AnaliticaDTO dto = analiticaService.analitica("30d");

        assertThat(dto.getGranularidad()).isEqualTo("dia");
        assertThat(dto.getSerie())
                .as("treinta días son treinta puntos, aunque en algunos no se venda")
                .hasSize(30);

        List<String> dias = dto.getSerie().stream().map(f -> String.valueOf(f.get("periodo"))).toList();
        assertThat(dias).isSorted();
        assertThat(dias.get(0)).isEqualTo(LocalDate.now().minusDays(29).toString());
        assertThat(dias.get(29)).isEqualTo(LocalDate.now().toString());
    }

    @Test
    @DisplayName("ventana larga: la serie pasa a meses, que es lo que se puede leer")
    void laVentanaLargaAgrupaPorMes() {
        AnaliticaDTO dto = analiticaService.analitica("12m");

        assertThat(dto.getGranularidad()).isEqualTo("mes");
        assertThat(dto.getPeriodoEtiqueta()).isEqualTo("Últimos 12 meses");
        // Con 365 días, agrupar por día daría 365 puntos ilegibles en un panel
        // de 300 px: la granularidad es una decisión de lectura, no un detalle.
        assertThat(dto.getSerie().size()).isLessThanOrEqualTo(14);
    }
}
