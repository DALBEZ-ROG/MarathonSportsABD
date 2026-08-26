package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.dto.empaque.EmpaqueRequestDTO;
import com.marathon.exception.ValidationException;
import com.marathon.model.Bodega;
import com.marathon.model.Pedido;
import com.marathon.model.Usuario;
import com.marathon.repository.UsuarioRepository;
import com.marathon.soporte.FixturaVenta;

/**
 * L1 — el despacho no puede destruir inventario. Cubre D-01 y D-03.
 *
 * <p>Estas pruebas <b>no</b> llevan {@code @Transactional} a proposito. Lo que
 * se comprueba es que la transaccion de {@code confirmarEmpaque} revierte sola
 * cuando la mercancia no alcanza; si la prueba abriera su propia transaccion
 * envolvente, el servicio se uniria a ella y el rollback que se quiere medir
 * seria el de la prueba, no el del servicio. La limpieza la hace
 * {@link FixturaVenta#limpiar()}.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("L1 - despacho de pedidos")
class EmpaqueServiceDespachoTest {

    @Autowired private EmpaqueService empaqueService;
    @Autowired private FixturaVenta fixtura;
    @Autowired private UsuarioRepository usuarioRepository;

    private Integer idAdmin;

    @BeforeEach
    void prepararDatos() {
        fixtura.empezar();
        Usuario admin = usuarioRepository.findByCorreo("admin@marathon.com").orElseThrow();
        idAdmin = admin.getIdUsuario();
    }

    @AfterEach
    void borrarDatos() {
        fixtura.limpiar();
    }

    private EmpaqueRequestDTO datosDeEmpaque() {
        EmpaqueRequestDTO dto = new EmpaqueRequestDTO();
        dto.setNumeroHu("HU-PRUEBA-001");
        dto.setTransportista("Transportista de prueba");
        dto.setRegionDestino("Region de prueba");
        return dto;
    }

    @Test
    @DisplayName("stock insuficiente: rechaza el despacho y no escribe nada")
    void stockInsuficienteAbortaElDespachoEntero() {
        Bodega bodega = fixtura.bodegaConStock("A", 3);
        Pedido pedido = fixtura.pedidoListoParaEmpacar(10);

        assertThatThrownBy(() -> empaqueService.confirmarEmpaque(
                    pedido.getIdPedido(), datosDeEmpaque(), idAdmin))
                .as("un pedido de 10 sobre un stock de 3 no puede despacharse")
                .isInstanceOf(ValidationException.class);

        assertThat(fixtura.stockEnBaseDe(bodega.getIdBodega()))
                .as("el stock no puede haberse tocado")
                .isEqualTo(3);

        assertThat(fixtura.movimientosDe(pedido.getIdPedido()))
                .as("no puede quedar ningun movimiento de un despacho que no ocurrio")
                .isZero();

        assertThat(fixtura.estadoEnBaseDe(pedido.getIdPedido()))
                .as("el pedido debe seguir en procesado, no marcado como enviado")
                .isEqualTo("procesado");
    }

    @Test
    @DisplayName("sin stock en ninguna bodega: rechaza, en vez de despachar sin mover inventario")
    void sinStockEnNingunaBodegaRechaza() {
        Bodega bodega = fixtura.bodegaConStock("A", 0);
        Pedido pedido = fixtura.pedidoListoParaEmpacar(5);

        assertThatThrownBy(() -> empaqueService.confirmarEmpaque(
                    pedido.getIdPedido(), datosDeEmpaque(), idAdmin))
                .as("sin existencias no se despacha")
                .isInstanceOf(ValidationException.class);

        assertThat(fixtura.stockEnBaseDe(bodega.getIdBodega())).isZero();
        assertThat(fixtura.movimientosDe(pedido.getIdPedido()))
                .as("la linea no puede saltarse en silencio")
                .isZero();
        assertThat(fixtura.estadoEnBaseDe(pedido.getIdPedido())).isEqualTo("procesado");
    }

    @Test
    @DisplayName("stock repartido entre dos bodegas: descuenta de ambas y cuadra")
    void repartoEntreVariasBodegas() {
        Bodega bodegaA = fixtura.bodegaConStock("A", 6);
        Bodega bodegaB = fixtura.bodegaConStock("B", 4);
        Pedido pedido = fixtura.pedidoListoParaEmpacar(10);

        empaqueService.confirmarEmpaque(pedido.getIdPedido(), datosDeEmpaque(), idAdmin);

        assertThat(fixtura.stockEnBaseDe(bodegaA.getIdBodega())).isZero();
        assertThat(fixtura.stockEnBaseDe(bodegaB.getIdBodega())).isZero();

        assertThat(fixtura.cantidadesMovidasDe(pedido.getIdPedido()))
                .as("un movimiento por bodega, con la cantidad realmente descontada de cada una")
                .containsExactlyInAnyOrder(6, 4);

        assertThat(fixtura.estadoEnBaseDe(pedido.getIdPedido())).isEqualTo("enviado");
    }

    @Test
    @DisplayName("caso normal: descuenta exactamente lo pedido y registra un solo movimiento")
    void despachoNormal() {
        Bodega bodega = fixtura.bodegaConStock("A", 20);
        Pedido pedido = fixtura.pedidoListoParaEmpacar(7);

        empaqueService.confirmarEmpaque(pedido.getIdPedido(), datosDeEmpaque(), idAdmin);

        assertThat(fixtura.stockEnBaseDe(bodega.getIdBodega())).isEqualTo(13);
        assertThat(fixtura.cantidadesMovidasDe(pedido.getIdPedido())).containsExactly(7);
        assertThat(fixtura.estadoEnBaseDe(pedido.getIdPedido())).isEqualTo("enviado");
    }
}
