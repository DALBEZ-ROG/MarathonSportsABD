package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.exception.ValidationException;
import com.marathon.model.Bodega;
import com.marathon.soporte.FixturaVenta;

/**
 * F66 — documentar la compra a partir de <b>lo que de verdad entró</b>.
 *
 * <p>Antes, registrar la factura llevaba a un formulario donde había que teclear
 * número, fechas, subtotal e impuesto. Los cuatro se pueden deducir de la orden
 * y de sus recepciones, y deducirlos tiene un efecto que va más allá de ahorrar
 * teclas: <b>elimina de raíz el descuadre que D-36 tenía que vigilar</b>. Si el
 * subtotal se calcula de lo recibido, no puede superarlo.
 *
 * <p><b>La regla que fijan estas pruebas</b>, que es la que pidió el dueño del
 * proyecto —«si se recibe parcial, que también se facture solo lo recibido»—:
 *
 * <pre>
 *     importe = valor recibido − lo ya documentado (sin contar las anuladas)
 * </pre>
 *
 * <p>Esa resta es lo que impide que una orden recibida en dos veces se documente
 * dos veces por el total y genere una cuenta por pagar del doble. Es el error
 * que resulta más caro de descubrir tarde, porque no falla: cuadra mal.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("F66 · documentar solo lo recibido")
class DocumentoDeCompraTest {

    @Autowired private FacturaCompraService facturaCompraService;
    @Autowired private FixturaVenta fixtura;
    @Autowired private JdbcTemplate jdbc;

    /** 10 unidades a 10,00: la orden vale 100,00 si llega entera. */
    private static final BigDecimal PRECIO = new BigDecimal("10.00");
    private static final int PEDIDAS = 10;

    private Integer idOrden;

    @BeforeEach
    void preparar() {
        fixtura.empezar();
        Bodega bodega = fixtura.bodegaConStock("f66", 0);
        // La fixtura marca la orden como recibida al completo; para probar lo
        // parcial se baja la cantidad recibida a mano.
        idOrden = fixtura.ordenCompraRecibida(PEDIDAS, PRECIO, bodega);
    }

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    /** Deja la orden con {@code recibidas} de las 10 pedidas. */
    private void recibirSolo(int recibidas) {
        jdbc.update("update orden_compra_detalle set cantidad_recibida = ? where id_orden_compra = ?",
                recibidas, idOrden);
    }

    @Test
    @DisplayName("con recepción parcial se documenta solo lo recibido, no lo pedido")
    void conRecepcionParcialSoloLoRecibido() {
        recibirSolo(4);

        var doc = facturaCompraService.crearDesdeRecepcion(idOrden, fixtura.getIdUsuario());

        assertThat(doc.getSubtotal())
                .as("entraron 4 de 10 a 10,00: son 40,00, no los 100,00 de la orden")
                .isEqualByComparingTo(new BigDecimal("40.00"));
    }

    @Test
    @DisplayName("al recibir el resto, el segundo documento cubre solo la diferencia")
    void elSegundoDocumentoCubreSoloLaDiferencia() {
        recibirSolo(4);
        facturaCompraService.crearDesdeRecepcion(idOrden, fixtura.getIdUsuario());

        recibirSolo(PEDIDAS);   // llega el resto
        var segundo = facturaCompraService.crearDesdeRecepcion(idOrden, fixtura.getIdUsuario());

        assertThat(segundo.getSubtotal())
                .as("sin restar lo ya documentado saldrían 100,00 y la cuenta por pagar "
                    + "quedaría al doble: 140,00 por una compra de 100,00")
                .isEqualByComparingTo(new BigDecimal("60.00"));
    }

    @Test
    @DisplayName("documentar dos veces sin que entre nada más se rechaza")
    void sinMercanciaNuevaNoSeVuelveADocumentar() {
        recibirSolo(4);
        facturaCompraService.crearDesdeRecepcion(idOrden, fixtura.getIdUsuario());

        assertThatThrownBy(() -> facturaCompraService.crearDesdeRecepcion(idOrden, fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No queda nada por documentar");
    }

    @Test
    @DisplayName("sin recepción no hay nada que documentar")
    void sinRecepcionNoHayNadaQueDocumentar() {
        recibirSolo(0);

        assertThatThrownBy(() -> facturaCompraService.crearDesdeRecepcion(idOrden, fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Todavía no ha entrado mercancía");
    }

    @Test
    @DisplayName("el importe nunca puede superar lo recibido: D-36 se cumple por construcción")
    void nuncaSupraLoRecibido() {
        recibirSolo(7);

        var doc = facturaCompraService.crearDesdeRecepcion(idOrden, fixtura.getIdUsuario());

        BigDecimal recibido = PRECIO.multiply(BigDecimal.valueOf(7));
        assertThat(doc.getSubtotal())
                .as("calcularlo de lo recibido hace imposible el descuadre que D-36 vigila")
                .isLessThanOrEqualTo(recibido)
                .isEqualByComparingTo(recibido);
    }

    @Test
    @DisplayName("el PDF se genera y es un PDF de verdad")
    void elPdfSeGenera() {
        recibirSolo(4);
        var doc = facturaCompraService.crearDesdeRecepcion(idOrden, fixtura.getIdUsuario());

        byte[] pdf = facturaCompraService.pdf(doc.getIdFacturaCompra());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1))
                .as("un PDF empieza por %%PDF-")
                .startsWith("%PDF-");
    }
}
