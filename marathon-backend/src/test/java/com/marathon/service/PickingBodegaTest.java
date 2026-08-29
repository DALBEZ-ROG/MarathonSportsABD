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
import com.marathon.dto.picking.PickingUpdateDTO;
import com.marathon.exception.ValidationException;
import com.marathon.model.Bodega;
import com.marathon.model.Pedido;
import com.marathon.soporte.FixturaVenta;

/**
 * L4 — el picking registra la bodega y el despacho descuenta de ella
 * (D-14 y parte 1 de D-01).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("L4 - picking con bodega")
class PickingBodegaTest {

    @Autowired private PickingService pickingService;
    @Autowired private EmpaqueService empaqueService;
    @Autowired private FixturaVenta fixtura;

    @BeforeEach
    void prepararDatos() {
        fixtura.empezar();
    }

    @AfterEach
    void borrarDatos() {
        fixtura.limpiar();
    }

    private EmpaqueRequestDTO datosDeEmpaque() {
        EmpaqueRequestDTO dto = new EmpaqueRequestDTO();
        dto.setNumeroHu("HU-L4-001");
        dto.setIdTransportista(1); // Servientrega, del catalogo que siembra la F77
        return dto;
    }

    @Test
    @DisplayName("el despacho descuenta de la bodega del picking, no de la que tenga mas stock")
    void descuentaDeLaBodegaDelPicking() {
        Bodega bodegaGrande = fixtura.bodegaConStock("A_grande", 100);
        Bodega bodegaPequena = fixtura.bodegaConStock("B_pequena", 10);

        // El operario recogio de la bodega pequena.
        Pedido pedido = fixtura.pedidoRecogidoDesde(5, bodegaPequena);

        empaqueService.confirmarEmpaque(pedido.getIdPedido(), datosDeEmpaque(), fixtura.getIdUsuario());

        assertThat(fixtura.stockEnBaseDe(bodegaPequena.getIdBodega()))
                .as("se descuenta de donde se recogio")
                .isEqualTo(5);

        assertThat(fixtura.stockEnBaseDe(bodegaGrande.getIdBodega()))
                .as("la otra bodega no se toca; antes de la L4 era la primera de la lista y se llevaba el descuento")
                .isEqualTo(100);
    }

    @Test
    @DisplayName("lineas antiguas sin bodega siguen despachandose con el reparto de la L1")
    void lineasSinBodegaUsanElRepartoAnterior() {
        Bodega bodega = fixtura.bodegaConStock("A", 20);
        // pedidoListoParaEmpacar no fija bodega: simula una linea anterior a la F45.
        Pedido pedido = fixtura.pedidoListoParaEmpacar(6);

        empaqueService.confirmarEmpaque(pedido.getIdPedido(), datosDeEmpaque(), fixtura.getIdUsuario());

        assertThat(fixtura.stockEnBaseDe(bodega.getIdBodega())).isEqualTo(14);
        assertThat(fixtura.estadoEnBaseDe(pedido.getIdPedido())).isEqualTo("enviado");
    }

    @Test
    @DisplayName("recoger de una bodega sin stock se rechaza en el picking, no en el empaque")
    void noSePuedeRecogerDeUnaBodegaSinStock() {
        fixtura.bodegaConStock("A_llena", 100);
        Bodega vacia = fixtura.bodegaConStock("B_vacia", 0);
        Pedido pedido = fixtura.pedidoConLineaSinRecoger(5);
        Integer idLinea = fixtura.idPrimeraLineaDe(pedido.getIdPedido());

        PickingUpdateDTO dto = new PickingUpdateDTO();
        dto.setIdDetalle(idLinea);
        dto.setCantidadRecogida(5);
        dto.setPickingCompletado(true);
        dto.setIdBodega(vacia.getIdBodega());

        assertThatThrownBy(() -> pickingService.actualizarLinea(
                    pedido.getIdPedido(), dto, fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No hay stock suficiente");
    }

    @Test
    @DisplayName("recoger sin indicar bodega se rechaza")
    void hayQueIndicarLaBodega() {
        fixtura.bodegaConStock("A", 100);
        Pedido pedido = fixtura.pedidoConLineaSinRecoger(5);
        Integer idLinea = fixtura.idPrimeraLineaDe(pedido.getIdPedido());

        PickingUpdateDTO dto = new PickingUpdateDTO();
        dto.setIdDetalle(idLinea);
        dto.setCantidadRecogida(5);
        dto.setPickingCompletado(true);
        dto.setIdBodega(null);

        assertThatThrownBy(() -> pickingService.actualizarLinea(
                    pedido.getIdPedido(), dto, fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("de qué bodega");
    }

    @Test
    @DisplayName("el picking normal guarda la bodega y el despacho la respeta")
    void caminoCompletoPickingYDespacho() {
        fixtura.bodegaConStock("A", 50);
        Bodega bodegaB = fixtura.bodegaConStock("B", 50);
        Pedido pedido = fixtura.pedidoConLineaSinRecoger(8);
        Integer idLinea = fixtura.idPrimeraLineaDe(pedido.getIdPedido());

        PickingUpdateDTO dto = new PickingUpdateDTO();
        dto.setIdDetalle(idLinea);
        dto.setCantidadRecogida(8);
        dto.setPickingCompletado(true);
        dto.setIdBodega(bodegaB.getIdBodega());

        assertThat(pickingService.actualizarLinea(pedido.getIdPedido(), dto, fixtura.getIdUsuario())
                    .getBodegaPickingNombre())
                .as("la respuesta del picking dice de que bodega se recogio")
                .contains("bodega_B");

        empaqueService.confirmarEmpaque(pedido.getIdPedido(), datosDeEmpaque(), fixtura.getIdUsuario());

        assertThat(fixtura.stockEnBaseDe(bodegaB.getIdBodega())).isEqualTo(42);
    }
}
