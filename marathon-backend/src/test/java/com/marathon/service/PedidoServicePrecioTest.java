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
 * L3 — el precio de venta sale del catalogo (D-34) y no se venden productos
 * dados de baja (D-24).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("L3 - precio y estado del producto al crear un pedido")
class PedidoServicePrecioTest {

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

    private PedidoRequestDTO pedidoCon(BigDecimal precioQueMandaElCliente, int cantidad) {
        DetallePedidoItemDTO item = new DetallePedidoItemDTO();
        item.setIdProducto(fixtura.getIdProducto());
        item.setCantidad(cantidad);
        item.setPrecioUnitario(precioQueMandaElCliente);

        PedidoRequestDTO dto = new PedidoRequestDTO();
        dto.setIdCliente(fixtura.getIdCliente());
        dto.setDescuento(BigDecimal.ZERO);
        dto.setDetalles(List.of(item));
        return dto;
    }

    @Test
    @DisplayName("un precio inventado en la peticion se ignora: manda el catalogo")
    void elPrecioDeLaPeticionSeIgnora() {
        BigDecimal precioCatalogo = fixtura.precioDeCatalogo();   // 100.00

        PedidoResponseDTO creado = pedidoService.crear(
                pedidoCon(new BigDecimal("0.01"), 2), fixtura.getIdUsuario());
        fixtura.seguirPedido(creado.getIdPedido());

        assertThat(fixtura.preciosPersistidosDe(creado.getIdPedido()))
                .as("el precio persistido debe ser el de catalogo, no el 0.01 que mando el cliente")
                .containsExactly(precioCatalogo.setScale(2));

        assertThat(fixtura.totalEnBaseDe(creado.getIdPedido()))
                .as("el trigger calcula el total sobre el precio real")
                .isEqualByComparingTo(precioCatalogo.multiply(BigDecimal.valueOf(2)));
    }

    @Test
    @DisplayName("un precio inflado tampoco cuela")
    void elPrecioInfladoTampocoCuela() {
        PedidoResponseDTO creado = pedidoService.crear(
                pedidoCon(new BigDecimal("99999.00"), 1), fixtura.getIdUsuario());
        fixtura.seguirPedido(creado.getIdPedido());

        assertThat(fixtura.preciosPersistidosDe(creado.getIdPedido()))
                .containsExactly(fixtura.precioDeCatalogo().setScale(2));
    }

    @Test
    @DisplayName("no se puede vender un producto dado de baja")
    void noSeVendeUnProductoInactivo() {
        fixtura.desactivarProducto();

        assertThatThrownBy(() -> pedidoService.crear(
                    pedidoCon(fixtura.precioDeCatalogo(), 1), fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no está activo");
    }
}
