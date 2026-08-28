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
 * F69 — una reposición del proveedor no se paga.
 *
 * <p><b>De dónde sale.</b> Lo preguntó el dueño del proyecto el 2026-08-28, al
 * registrar que un proveedor «manda otra igual»:
 *
 * <blockquote>«¿yo no tendría que pagar eso, no? ¿y cómo sé que me va a
 * llegar?»</blockquote>
 *
 * <p>Tenía razón en las dos. Antes, la reposición llegaba y se recibía como una
 * compra cualquiera — con su factura y su cuenta por pagar—, es decir
 * <b>pagando dos veces la misma mercancía</b>: una al comprar la que salió
 * defectuosa y otra al recibir el reemplazo.
 *
 * <p><b>Esta es la prueba que protege el dinero</b>, y por eso comprueba las
 * DOS vías: la automática (el botón de la pantalla) y la manual (el endpoint,
 * que cualquiera con {@code facturas_compra:registrar} puede llamar sin pasar
 * por la interfaz). Blindar solo la pantalla no habría servido de nada.
 *
 * <p>Lo que sí sigue funcionando es recibirla: el stock entra igual, con su
 * movimiento y su rastro. Lo único que se corta es el cobro.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("F69 · una reposición del proveedor no se factura")
class ReposicionNoSePagaTest {

    @Autowired private FacturaCompraService facturaCompraService;
    @Autowired private FixturaVenta fixtura;
    @Autowired private JdbcTemplate jdbc;

    private Integer idOrden;
    private Bodega bodega;

    @BeforeEach
    void preparar() {
        fixtura.empezar();
        bodega = fixtura.bodegaConStock("f69", 0);
        idOrden = fixtura.ordenCompraRecibida(3, new BigDecimal("15.00"), bodega);
    }

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    /**
     * Sustituye la orden por una que NACE marcada como reposición.
     *
     * <p>La primera versión de esta prueba hacía un {@code UPDATE} de
     * {@code es_reposicion} y <b>PostgreSQL lo rechazó</b> — que es exactamente
     * lo que tenía que pasar: la F69 concede INSERT sobre esa columna y nunca
     * UPDATE, para que nadie pueda volver no facturable una compra que sí había
     * que pagar. El candado cerró antes de que nadie lo probara a mano.
     */
    private void marcarComoReposicion() {
        idOrden = fixtura.ordenCompraDeReposicionRecibida(3, new BigDecimal("15.00"), bodega);
    }

    private FacturaCompraRequestDTO facturaDe(BigDecimal subtotal) {
        FacturaCompraRequestDTO dto = new FacturaCompraRequestDTO();
        dto.setIdOrdenCompra(idOrden);
        dto.setNumeroFacturaProveedor("F69-COLADA");
        dto.setFechaFactura(LocalDate.now());
        dto.setFechaVencimiento(LocalDate.now().plusDays(30));
        dto.setSubtotal(subtotal);
        dto.setImpuesto(BigDecimal.ZERO);
        return dto;
    }

    @Test
    @DisplayName("por la vía automática, la del botón, se rechaza")
    void laViaAutomaticaSeRechaza() {
        marcarComoReposicion();

        assertThatThrownBy(() -> facturaCompraService.crearDesdeRecepcion(idOrden, fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reposición")
                .hasMessageContaining("No se factura ni genera cuenta por pagar");
    }

    @Test
    @DisplayName("y por la vía manual también, que es la que se puede llamar sin pantalla")
    void laViaManualTambienSeRechaza() {
        // Si solo se hubiera blindado la pantalla, este endpoint seguiría abierto
        // para cualquiera con 'facturas_compra:registrar'. El dinero no se
        // protege escondiendo un botón.
        marcarComoReposicion();

        assertThatThrownBy(() -> facturaCompraService.crear(
                facturaDe(new BigDecimal("45.00")), fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reposición");
    }

    @Test
    @DisplayName("el mensaje explica POR QUÉ, no solo que no se puede")
    void elMensajeExplicaElMotivo() {
        marcarComoReposicion();

        assertThatThrownBy(() -> facturaCompraService.crear(
                facturaDe(new BigDecimal("45.00")), fixtura.getIdUsuario()))
                .as("quien lo lee tiene que entender que ya pagó, no creer que es un fallo")
                .hasMessageContaining("ya se pagó");
    }

    @Test
    @DisplayName("una orden normal sigue facturándose: el corte es solo para las reposiciones")
    void unaOrdenNormalSigueFacturandose() {
        // La otra mitad, y no es de adorno: un guardia demasiado ancho que
        // bloqueara compras de verdad sería peor que el problema original.
        var doc = facturaCompraService.crearDesdeRecepcion(idOrden, fixtura.getIdUsuario());

        assertThat(doc.getSubtotal())
                .as("3 unidades a 15,00")
                .isEqualByComparingTo(new BigDecimal("45.00"));
    }

    @Test
    @DisplayName("la marca no se puede poner después: una orden normal no se convierte en reposición")
    void laMarcaNoSePonePorLaAplicacion() {
        // Se comprueba en los privilegios, no en el código: la F69 concede
        // INSERT sobre es_reposicion pero NUNCA UPDATE. La marca se pone al
        // nacer la orden o no se pone, y así nadie puede volver no facturable
        // una compra que sí había que pagar.
        var privilegios = jdbc.queryForList(
                "SELECT a.privilege_type FROM pg_class c "
                + "JOIN pg_attribute at ON at.attrelid = c.oid AND at.attnum > 0 "
                + "CROSS JOIN LATERAL aclexplode(at.attacl) a "
                + "WHERE c.relname = 'orden_compra' AND at.attname = 'es_reposicion' "
                + "AND a.grantee::regrole::text = 'rol_encargado_compras'",
                String.class);

        assertThat(privilegios).contains("INSERT");
        assertThat(privilegios)
                .as("con UPDATE, cualquiera podría dejar de pagar una compra de verdad")
                .doesNotContain("UPDATE");
    }
}
