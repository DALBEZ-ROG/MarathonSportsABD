package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.dto.empaque.EmpaqueRequestDTO;
import com.marathon.model.Bodega;
import com.marathon.model.Pedido;
import com.marathon.model.Usuario;
import com.marathon.repository.UsuarioRepository;
import com.marathon.soporte.FixturaVenta;

/**
 * L1 — actualizaciones perdidas al descontar stock (D-03).
 *
 * <p>Dos despachos simultaneos de 5 unidades sobre un stock de 10 deben dejar el
 * saldo en 0. Con el patron leer-calcular-escribir sin bloqueo, las dos
 * transacciones leen 10, las dos calculan 5, y el saldo final queda en 5: un
 * descuento se pierde, pero los dos movimientos quedan grabados en el kardex.
 *
 * <p>Es la prueba que separa "funciona" de "funciona cuando hay dos operarios
 * despachando a la vez", que es justamente como se usa un modulo de bodega.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("L1 - concurrencia en el descuento de stock")
class EmpaqueServiceConcurrenciaTest {

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

    private EmpaqueRequestDTO datosDeEmpaque(String hu) {
        EmpaqueRequestDTO dto = new EmpaqueRequestDTO();
        dto.setNumeroHu(hu);
        dto.setTransportista("Transportista de prueba");
        dto.setRegionDestino("Region de prueba");
        return dto;
    }

    @Test
    @DisplayName("dos despachos a la vez sobre el mismo producto no pierden un descuento")
    void dosDespachosSimultaneosNoSePisan() throws Exception {
        Bodega bodega = fixtura.bodegaConStock("A", 10);
        Pedido primero = fixtura.pedidoListoParaEmpacar(5);
        Pedido segundo = fixtura.pedidoListoParaEmpacar(5);

        // La barrera hace que los dos hilos entren al servicio a la vez; sin
        // ella, uno terminaria antes de que el otro empezara y la prueba pasaria
        // incluso con el defecto presente.
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Callable<String> despacho1 = despachar(primero, "HU-CONC-1", salida);
            Callable<String> despacho2 = despachar(segundo, "HU-CONC-2", salida);

            Future<String> f1 = hilos.submit(despacho1);
            Future<String> f2 = hilos.submit(despacho2);
            salida.countDown();

            String r1 = f1.get(30, TimeUnit.SECONDS);
            String r2 = f2.get(30, TimeUnit.SECONDS);

            assertThat(List.of(r1, r2))
                    .as("los dos despachos caben en el stock disponible; ninguno deberia fallar")
                    .containsExactly("ok", "ok");
        } finally {
            hilos.shutdownNow();
        }

        assertThat(fixtura.stockEnBaseDe(bodega.getIdBodega()))
                .as("10 - 5 - 5 = 0; si queda 5 es que un descuento se perdio")
                .isZero();

        assertThat(fixtura.cantidadesMovidasDe(primero.getIdPedido())).containsExactly(5);
        assertThat(fixtura.cantidadesMovidasDe(segundo.getIdPedido())).containsExactly(5);
    }

    private Callable<String> despachar(Pedido pedido, String hu, CountDownLatch salida) {
        return () -> {
            salida.await();
            empaqueService.confirmarEmpaque(pedido.getIdPedido(), datosDeEmpaque(hu), idAdmin);
            return "ok";
        };
    }
}
