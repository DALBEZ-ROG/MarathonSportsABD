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
import org.springframework.test.context.ActiveProfiles;

import com.marathon.dto.pedido.DetallePedidoItemDTO;
import com.marathon.dto.pedido.PedidoRequestDTO;
import com.marathon.dto.pedido.PedidoResponseDTO;
import com.marathon.exception.ValidationException;
import com.marathon.soporte.FixturaVenta;

/**
 * L8 — tope al dinero que sale. Cubre el descuento del pedido (D-19).
 * El tope del reembolso (D-08) se prueba en {@link ReembolsoTopeTest}, que
 * necesita montar el circuito completo de devolucion.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("L8 - tope al descuento del pedido")
class TopeDineroTest {

    @Autowired private PedidoService pedidoService;
    @Autowired private FixturaVenta fixtura;

    @BeforeEach
    void prepararDatos() {
        fixtura.empezar();
        // F47 (D-02): desde que crear() comprueba el disponible, un pedido sin
        // existencias detras se rechaza. Esta prueba no va de stock, asi que se
        // le pone holgura de sobra y sigue midiendo lo suyo.
        fixtura.bodegaConStock("A", 1000);
    }

    @AfterEach
    void borrarDatos() {
        fixtura.limpiar();
    }

    private PedidoRequestDTO pedido(int cantidad, BigDecimal descuento) {
        DetallePedidoItemDTO item = new DetallePedidoItemDTO();
        item.setIdProducto(fixtura.getIdProducto());
        item.setCantidad(cantidad);
        item.setPrecioUnitario(fixtura.precioDeCatalogo());

        PedidoRequestDTO dto = new PedidoRequestDTO();
        dto.setIdCliente(fixtura.getIdCliente());
        dto.setDescuento(descuento);
        dto.setDetalles(List.of(item));
        return dto;
    }

    @Test
    @DisplayName("un descuento mayor que el subtotal se rechaza, en vez de dejar el total en 0")
    void descuentoMayorQueElSubtotalSeRechaza() {
        // Subtotal = 1 x 100 = 100. Descuento 150.
        // Antes: se aceptaba, el trigger aplicaba GREATEST(100-150, 0) y el
        // pedido quedaba en total 0 sin que nadie se enterara.
        assertThatThrownBy(() -> pedidoService.crear(
                    pedido(1, new BigDecimal("150.00")), fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no puede superar el subtotal");
    }

    @Test
    @DisplayName("un descuento negativo se rechaza con 400, no con un 500 de la base")
    void descuentoNegativoSeRechaza() {
        assertThatThrownBy(() -> pedidoService.crear(
                    pedido(1, new BigDecimal("-10.00")), fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("negativo");
    }

    @Test
    @DisplayName("un descuento valido se aplica")
    void descuentoValidoSeAplica() {
        // Subtotal = 2 x 100 = 200. Descuento 30 -> total 170.
        PedidoResponseDTO creado = pedidoService.crear(
                pedido(2, new BigDecimal("30.00")), fixtura.getIdUsuario());
        fixtura.seguirPedido(creado.getIdPedido());

        assertThat(fixtura.totalEnBaseDe(creado.getIdPedido()))
                .isEqualByComparingTo(new BigDecimal("170.00"));
    }

    @Test
    @DisplayName("un descuento igual al subtotal es valido y deja el total en 0")
    void descuentoIgualAlSubtotalEsValido() {
        PedidoResponseDTO creado = pedidoService.crear(
                pedido(1, new BigDecimal("100.00")), fixtura.getIdUsuario());
        fixtura.seguirPedido(creado.getIdPedido());

        assertThat(fixtura.totalEnBaseDe(creado.getIdPedido()))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}
