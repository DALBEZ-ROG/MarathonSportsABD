package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.dto.comprobante.ComprobanteResponseDTO;
import com.marathon.dto.empaque.EmpaqueRequestDTO;
import com.marathon.exception.ValidationException;
import com.marathon.model.Bodega;
import com.marathon.model.Pedido;
import com.marathon.soporte.FixturaVenta;

/**
 * L6 — facturacion coherente: estado del pedido (D-11), reemision tras anular
 * (D-06) y numeracion a prueba de concurrencia (D-07).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("L6 - facturacion")
class ComprobanteFacturacionTest {

    @Autowired private ComprobanteService comprobanteService;
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

    /** Lleva un pedido hasta 'enviado' pasando por el despacho real. */
    private Pedido pedidoDespachado(int cantidad) {
        Bodega bodega = fixtura.bodegaConStock("A", cantidad + 50);
        Pedido pedido = fixtura.pedidoRecogidoDesde(cantidad, bodega);

        EmpaqueRequestDTO dto = new EmpaqueRequestDTO();
        dto.setNumeroHu("HU-L6");
        dto.setTransportista("T");
        dto.setRegionDestino("R");
        empaqueService.confirmarEmpaque(pedido.getIdPedido(), dto, fixtura.getIdUsuario());
        return pedido;
    }

    // ---------------------------------------------------------------- D-11 ---

    @Test
    @DisplayName("no se factura un pedido que todavia no ha salido")
    void noSeFacturaUnPedidoEnProcesado() {
        fixtura.bodegaConStock("A", 100);
        Pedido pedido = fixtura.pedidoListoParaEmpacar(3);   // queda en 'procesado'

        assertThatThrownBy(() -> comprobanteService.generarComprobante(
                    pedido.getIdPedido(), fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("procesado");
    }

    @Test
    @DisplayName("un pedido despachado si se factura")
    void unPedidoDespachadoSeFactura() {
        Pedido pedido = pedidoDespachado(3);

        ComprobanteResponseDTO c = comprobanteService.generarComprobante(
                pedido.getIdPedido(), fixtura.getIdUsuario());

        assertThat(c.getNumeroComprobante()).startsWith("COMP-");
        assertThat(c.getEstado()).isEqualTo("emitido");
        assertThat(c.getTotal()).isEqualByComparingTo(fixtura.totalEnBaseDe(pedido.getIdPedido()));
    }

    // ---------------------------------------------------------------- D-06 ---

    @Test
    @DisplayName("tras anular se puede volver a emitir, con un numero nuevo")
    void trasAnularSePuedeReemitir() {
        Pedido pedido = pedidoDespachado(2);

        ComprobanteResponseDTO primero = comprobanteService.generarComprobante(
                pedido.getIdPedido(), fixtura.getIdUsuario());

        // Con un comprobante vigente no se puede emitir otro.
        assertThatThrownBy(() -> comprobanteService.generarComprobante(
                    pedido.getIdPedido(), fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class);

        comprobanteService.anular(primero.getIdComprobante(), fixtura.getIdUsuario());

        // Antes de la L6 esto seguia fallando para siempre: el pedido quedaba
        // sin poder facturarse nunca mas.
        ComprobanteResponseDTO segundo = comprobanteService.generarComprobante(
                pedido.getIdPedido(), fixtura.getIdUsuario());

        assertThat(segundo.getNumeroComprobante())
                .isNotEqualTo(primero.getNumeroComprobante());
        assertThat(segundo.getEstado()).isEqualTo("emitido");
    }

    // ---------------------------------------------------------------- D-07 ---

    @Test
    @DisplayName("veinte emisiones simultaneas dan veinte numeros distintos")
    void laNumeracionAguantaLaConcurrencia() throws Exception {
        int n = 20;
        List<Pedido> pedidos = new java.util.ArrayList<>();
        Bodega bodega = fixtura.bodegaConStock("A", 500);
        EmpaqueRequestDTO empaque = new EmpaqueRequestDTO();
        empaque.setNumeroHu("HU-L6-C");
        empaque.setTransportista("T");
        empaque.setRegionDestino("R");
        for (int i = 0; i < n; i++) {
            Pedido p = fixtura.pedidoRecogidoDesde(1, bodega);
            empaqueService.confirmarEmpaque(p.getIdPedido(), empaque, fixtura.getIdUsuario());
            pedidos.add(p);
        }

        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(n);
        try {
            List<Future<String>> futuros = pedidos.stream()
                    .map(p -> hilos.submit((Callable<String>) () -> {
                        salida.await();
                        return comprobanteService
                                .generarComprobante(p.getIdPedido(), fixtura.getIdUsuario())
                                .getNumeroComprobante();
                    }))
                    .collect(Collectors.toList());

            salida.countDown();

            Set<String> numeros = new java.util.HashSet<>();
            for (Future<String> f : futuros) {
                numeros.add(f.get(60, TimeUnit.SECONDS));
            }

            assertThat(numeros)
                    .as("con count()+1 varias emisiones repetian numero y chocaban contra uq_comprobante_numero")
                    .hasSize(n);
        } finally {
            hilos.shutdownNow();
        }
    }
}
