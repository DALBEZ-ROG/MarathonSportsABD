package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.dto.facturacompra.FacturaCompraRequestDTO;
import com.marathon.exception.ValidationException;
import com.marathon.model.Bodega;
import com.marathon.soporte.FixturaVenta;

/**
 * F60 (D-36) — el importe de la factura se contrasta con lo recibido.
 *
 * <p>El cotejo de compras estaba cojo de un lado. La recepción no deja recibir
 * más de lo pedido y el pago no deja pagar más del saldo, pero <b>el subtotal lo
 * escribía quien registraba la factura y nadie lo comparaba con nada</b>: una
 * factura de 50.000 sobre una orden de la que entraron 500 se aceptaba y
 * generaba una cuenta por pagar de 50.000.
 *
 * <p><b>La regla la decidió el dueño del proyecto el 2026-08-28 y es la
 * estricta:</b> sin flete, sin otros cargos por encima y sin tolerancia. Estas
 * pruebas existen para que nadie la afloje sin darse cuenta.
 *
 * <p>Lo que NO se toca son las 1.649 facturas históricas que la incumplen: es
 * poblado masivo, no se puede distinguir de lo que escribió la aplicación, y
 * repararlo sería inventarse hechos (PENDIENTE.md §5). La validación mira solo
 * hacia adelante.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("F60 · D-36 · la factura de compra no puede superar lo recibido")
class FacturaCotejadaTest {

    @Autowired private FacturaCompraService facturaCompraService;
    @Autowired private FixturaVenta fixtura;
    @Autowired private JdbcTemplate jdbc;

    /** 10 unidades a 25,00 = 250,00 recibidos. */
    private static final BigDecimal PRECIO = new BigDecimal("25.00");
    private static final int CANTIDAD = 10;
    private static final BigDecimal RECIBIDO = new BigDecimal("250.00");

    private Integer idOrden;

    @BeforeEach
    void preparar() {
        fixtura.empezar();
        Bodega bodega = fixtura.bodegaConStock("compras", 0);
        idOrden = fixtura.ordenCompraRecibida(CANTIDAD, PRECIO, bodega);
    }

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private FacturaCompraRequestDTO facturaDe(BigDecimal subtotal, String numero) {
        FacturaCompraRequestDTO dto = new FacturaCompraRequestDTO();
        dto.setIdOrdenCompra(idOrden);
        dto.setNumeroFacturaProveedor(numero);
        dto.setFechaFactura(LocalDate.now());
        dto.setFechaVencimiento(LocalDate.now().plusDays(30));
        dto.setSubtotal(subtotal);
        dto.setImpuesto(BigDecimal.ZERO);
        return dto;
    }

    @Test
    @DisplayName("un subtotal por encima de lo recibido se rechaza")
    void porEncimaSeRechaza() {
        assertThatThrownBy(() -> facturaCompraService.crear(
                facturaDe(new BigDecimal("400.00"), "F-EXCESO"), fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("supera el valor")
                // El mensaje trae las dos cifras: quien lo lee tiene que poder
                // ver de cuanto es el descuadre sin ir a mirar la base.
                .hasMessageContaining("400.00")
                .hasMessageContaining("250.00");
    }

    @Test
    @DisplayName("un subtotal igual a lo recibido se acepta")
    void exactamenteLoRecibidoSeAcepta() {
        var factura = facturaCompraService.crear(
                facturaDe(RECIBIDO, "F-EXACTA"), fixtura.getIdUsuario());

        assertThat(factura.getSubtotal()).isEqualByComparingTo(RECIBIDO);
    }

    @Test
    @DisplayName("un subtotal por debajo se acepta: facturar de menos no es el defecto")
    void porDebajoSeAcepta() {
        // Un proveedor puede facturar en varias veces lo que entrego de golpe.
        // Lo que se persigue es que cobre de MAS, no que cobre por partes.
        var factura = facturaCompraService.crear(
                facturaDe(new BigDecimal("100.00"), "F-PARCIAL"), fixtura.getIdUsuario());

        assertThat(factura.getSubtotal()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("un céntimo de más también se rechaza: la regla no lleva tolerancia")
    void niUnCentimoDeMas() {
        // Esta es la prueba que hay que mirar si algun dia negocio admite flete.
        // No se rompe por accidente: se rompe porque alguien cambio la regla.
        assertThatThrownBy(() -> facturaCompraService.crear(
                facturaDe(new BigDecimal("250.01"), "F-CENTIMO"), fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("supera el valor");
    }

    @Test
    @DisplayName("el rechazo deja rastro en la bitácora, y sobrevive a la excepción")
    void elRechazoQuedaRegistrado() {
        // Esta prueba nació de un fallo real de la propia F60. La primera
        // versión rechazaba la factura correctamente y escribía el apunte...
        // dentro de la misma transacción que la excepción deshacía dos líneas
        // después. Resultado: se rechazaba bien y `log_accion` quedaba vacía —
        // justo en el caso que se quería poder rastrear.
        //
        // Se vio mirando la base después de probar contra la aplicación en
        // marcha, no aquí. Ahora se comprueba aquí para que no vuelva.
        long antes = cuantosRechazosHay();

        assertThatThrownBy(() -> facturaCompraService.crear(
                facturaDe(new BigDecimal("400.00"), "F-RASTRO"), fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class);

        assertThat(cuantosRechazosHay())
                .as("sin registrarAparte (REQUIRES_NEW), el apunte se va con la transacción deshecha")
                .isEqualTo(antes + 1);
    }

    private long cuantosRechazosHay() {
        Long n = jdbc.queryForObject(
                "select count(*) from log_accion where modulo = 'compras' "
                + "and accion = 'factura_rechazada_descuadre'", Long.class);
        return n == null ? 0 : n;
    }
}
