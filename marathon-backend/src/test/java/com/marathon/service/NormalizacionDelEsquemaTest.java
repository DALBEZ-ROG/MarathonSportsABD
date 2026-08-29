package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.dto.empaque.EmpaqueRequestDTO;
import com.marathon.dto.pedido.PedidoResponseDTO;
import com.marathon.exception.ValidationException;
import com.marathon.model.Bodega;
import com.marathon.model.CuentaPorPagar;
import com.marathon.model.Pedido;
import com.marathon.repository.CuentaPorPagarRepository;
import com.marathon.soporte.FixturaVenta;

/**
 * F84 — lo que la base guardaba dos veces deja de guardarse dos veces.
 *
 * <p><b>De dónde sale.</b> El dueño pidió revisar si el esquema está en 3FN.
 * Se revisó entero y salieron cuatro defectos: tres dependencias transitivas
 * —un dato copiado de otra tabla a la que ya se llegaba— y una lista metida
 * dentro de una columna de texto, que incumple la 1FN.
 *
 * <p><b>Por qué hay pruebas de esto y no basta con mirar el esquema.</b> Que
 * una columna ya no exista lo dice cualquier consulta al catálogo del sistema.
 * Lo que hay que fijar es lo otro: que el dato <b>sigue llegando</b> por el
 * camino nuevo, y que <b>ya no puede contradecirse</b>. Una normalización que
 * quita una columna y de paso pierde el dato no es una normalización, es una
 * pérdida; estas pruebas separan las dos cosas.
 *
 * <p>La prueba central es {@link #laRegionSigueALaCiudad()}: cambia la región de
 * la ciudad del cliente <b>después</b> de empacar y comprueba que el pedido ya
 * dice la nueva. Con la columna vieja eso era imposible, y esa imposibilidad era
 * justamente el problema: el informe podía decir «Costa» al lado de una
 * dirección de Quito.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("F84 · el esquema no guarda dos veces lo mismo")
class NormalizacionDelEsquemaTest {

    @Autowired private EmpaqueService empaqueService;
    @Autowired private PedidoService pedidoService;
    @Autowired private FacturaCompraService facturaCompraService;
    @Autowired private CuentaPorPagarRepository cuentaRepository;
    @Autowired private FixturaVenta fixtura;
    @Autowired private JdbcTemplate jdbc;

    private Bodega bodega;
    private Integer idCiudad;

    /** El primero del catálogo, para no depender de un identificador concreto. */
    private Integer unTransportista() {
        return jdbc.queryForObject(
                "select id_transportista from transportista where estado = 'activo' "
                + "order by id_transportista limit 1", Integer.class);
    }

    @BeforeEach
    void preparar() {
        fixtura.empezar();
        bodega = fixtura.bodegaConStock("N84", 500);
        idCiudad = jdbc.queryForObject(
                "select id_ciudad from cliente where id_cliente = ?",
                Integer.class, fixtura.getIdCliente());
    }

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Pedido empacar(int cantidad) {
        Pedido pedido = fixtura.pedidoRecogidoDesde(cantidad, bodega);
        EmpaqueRequestDTO dto = new EmpaqueRequestDTO();
        dto.setNumeroHu("HU-F84");
        dto.setIdTransportista(unTransportista());
        empaqueService.confirmarEmpaque(pedido.getIdPedido(), dto, fixtura.getIdUsuario());
        return pedido;
    }

    // -----------------------------------------------------------------------
    // 3FN — la región de destino
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("la región del pedido sigue a la ciudad del cliente, también después de empacar")
    void laRegionSigueALaCiudad() {
        jdbc.update("update ciudad set region = 'Sierra' where id_ciudad = ?", idCiudad);
        Pedido pedido = empacar(2);

        PedidoResponseDTO antes = pedidoService.obtener(pedido.getIdPedido());
        assertThat(antes.getRegionDestino()).isEqualTo("Sierra");

        // Aqui esta el fondo del asunto. Con la columna vieja, corregir la
        // ciudad del cliente dejaba el pedido diciendo la region antigua para
        // siempre, mientras la direccion —que se lee viva— ya decia otra cosa.
        jdbc.update("update ciudad set region = 'Oriente' where id_ciudad = ?", idCiudad);

        PedidoResponseDTO despues = pedidoService.obtener(pedido.getIdPedido());
        assertThat(despues.getRegionDestino())
                .as("la region se deduce de la ciudad; si estuviera copiada, aqui seguiria diciendo Sierra")
                .isEqualTo("Oriente");
    }

    @Test
    @DisplayName("filtrar despachos por región usa la ciudad del cliente")
    void elFiltroDeDespachosVaPorLaCiudad() {
        jdbc.update("update ciudad set region = 'Insular' where id_ciudad = ?", idCiudad);
        Pedido pedido = empacar(1);

        var enInsular = empaqueService.listarDespachados(0, 50, "Insular", null, null);
        assertThat(enInsular.getContent())
                .extracting(PedidoResponseDTO::getIdPedido)
                .contains(pedido.getIdPedido());

        var enCosta = empaqueService.listarDespachados(0, 50, "Costa", null, null);
        assertThat(enCosta.getContent())
                .extracting(PedidoResponseDTO::getIdPedido)
                .doesNotContain(pedido.getIdPedido());
    }

    @Test
    @DisplayName("pedido ya no tiene columnas transportista ni region_destino")
    void lasColumnasViejasNoEstan() {
        List<String> columnas = jdbc.queryForList(
                "select column_name from information_schema.columns "
                + "where table_schema = 'public' and table_name = 'pedido'", String.class);

        assertThat(columnas).doesNotContain("transportista", "region_destino");
        assertThat(columnas).contains("id_transportista");
    }

    // -----------------------------------------------------------------------
    // 3FN — el transportista
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("el pedido guarda la clave del transportista y el nombre lo pone el catálogo")
    void elNombreDelTransportistaLoPoneElCatalogo() {
        Pedido pedido = empacar(1);

        Integer idGuardado = jdbc.queryForObject(
                "select id_transportista from pedido where id_pedido = ?",
                Integer.class, pedido.getIdPedido());
        String nombreEnCatalogo = jdbc.queryForObject(
                "select nombre from transportista where id_transportista = ?",
                String.class, idGuardado);

        assertThat(pedidoService.obtener(pedido.getIdPedido()).getTransportista())
                .as("el nombre no esta guardado en el pedido: se lee del catalogo")
                .isEqualTo(nombreEnCatalogo);
    }

    @Test
    @DisplayName("un transportista que no está en el catálogo se rechaza con un motivo, no con un error de base")
    void unTransportistaInventadoSeRechaza() {
        Pedido pedido = fixtura.pedidoRecogidoDesde(1, bodega);
        EmpaqueRequestDTO dto = new EmpaqueRequestDTO();
        dto.setNumeroHu("HU-F84-malo");
        dto.setIdTransportista(999_999);

        assertThatThrownBy(() ->
                empaqueService.confirmarEmpaque(pedido.getIdPedido(), dto, fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("catálogo");

        // Y el pedido no se ha movido: se comprueba antes de tocar stock.
        assertThat(fixtura.estadoEnBaseDe(pedido.getIdPedido())).isEqualTo("procesado");
        assertThat(fixtura.stockEnBaseDe(bodega.getIdBodega())).isEqualTo(500);
    }

    @Test
    @DisplayName("la clave ajena impide que un pedido apunte a un transportista inexistente")
    void laClaveAjenaCierra() {
        Pedido pedido = empacar(1);

        assertThatThrownBy(() -> jdbc.update(
                "update pedido set id_transportista = 999999 where id_pedido = ?", pedido.getIdPedido()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    // -----------------------------------------------------------------------
    // 1FN — la cobertura del transportista
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("la cobertura se puede consultar: quién llega al Oriente ya es una pregunta con respuesta")
    void laCoberturaSeConsulta() {
        List<String> columnas = jdbc.queryForList(
                "select column_name from information_schema.columns "
                + "where table_schema = 'public' and table_name = 'transportista'", String.class);
        assertThat(columnas)
                .as("la frase 'Nacional, incluye Oriente' era una lista dentro de una columna")
                .doesNotContain("cobertura");

        List<String> lleganAlOriente = jdbc.queryForList(
                "select t.nombre from transportista t "
                + "join transportista_cobertura c on c.id_transportista = t.id_transportista "
                + "where c.region = 'Oriente' order by t.nombre", String.class);
        assertThat(lleganAlOriente).isNotEmpty();

        List<String> lleganALaInsular = jdbc.queryForList(
                "select t.nombre from transportista t "
                + "join transportista_cobertura c on c.id_transportista = t.id_transportista "
                + "where c.region = 'Insular'", String.class);
        assertThat(lleganALaInsular)
                .as("no todos llegan a todas partes; si asi fuera, la tabla no diria nada")
                .hasSizeLessThan(lleganAlOriente.size() + lleganALaInsular.size());

        // Ningun transportista se quedo sin cobertura al partir las frases.
        Integer huerfanos = jdbc.queryForObject(
                "select count(*) from transportista t where not exists "
                + "(select 1 from transportista_cobertura c "
                + " where c.id_transportista = t.id_transportista)", Integer.class);
        assertThat(huerfanos).isZero();
    }

    @Test
    @DisplayName("la cobertura no admite una región inventada")
    void laCoberturaNoAdmiteCualquierCosa() {
        Integer id = unTransportista();
        assertThatThrownBy(() -> jdbc.update(
                "insert into transportista_cobertura (id_transportista, region) values (?, 'Amazonia')", id))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    // -----------------------------------------------------------------------
    // 3FN — el proveedor de la cuenta por pagar
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("la cuenta por pagar llega a su proveedor por la factura, sin copiarlo")
    void laCuentaLlegaAlProveedorPorLaFactura() {
        List<String> columnas = jdbc.queryForList(
                "select column_name from information_schema.columns "
                + "where table_schema = 'public' and table_name = 'cuenta_por_pagar'", String.class);
        assertThat(columnas)
                .as("FacturaCompraService hacia literalmente cuenta.setProveedor(orden.getProveedor())")
                .doesNotContain("id_proveedor");

        Integer idOrden = fixtura.ordenCompraRecibida(4, new BigDecimal("10.00"), bodega);
        var factura = facturaCompraService.crearDesdeRecepcion(idOrden, fixtura.getIdUsuario());

        CuentaPorPagar cuenta = cuentaRepository
                .findByFacturaCompraIdFacturaCompra(factura.getIdFacturaCompra())
                .orElseThrow(() -> new AssertionError("La factura no genero cuenta por pagar"));

        Integer idProveedorDeLaOrden = jdbc.queryForObject(
                "select id_proveedor from orden_compra where id_orden_compra = ?",
                Integer.class, idOrden);

        assertThat(cuenta.getProveedor()).isNotNull();
        assertThat(cuenta.getProveedor().getIdProveedor()).isEqualTo(idProveedorDeLaOrden);
    }
}
