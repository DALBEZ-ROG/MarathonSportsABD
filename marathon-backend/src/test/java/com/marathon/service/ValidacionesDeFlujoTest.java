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

import com.marathon.dto.inventario.MovimientoRequestDTO;
import com.marathon.exception.ValidationException;
import com.marathon.model.Bodega;
import com.marathon.soporte.FixturaVenta;

/**
 * Huecos de validacion encontrados en el repaso de flujos del 2026-08-27.
 *
 * <p>No venian de la lista de defectos: salieron de recorrer flujo por flujo
 * preguntando "¿y esto quien lo comprueba?" y de contrastar cada respuesta
 * contra la base. Los dos que cubren estas pruebas tenian CERO casos en
 * {@code mod_venta_inve}, asi que cerrarlos no rompe ningun historico — se
 * comprobo antes de tocar nada.
 *
 * <p>El tercero que salio del repaso, el importe de la factura de compra sin
 * contrastar contra lo recibido, NO se cierra aqui: necesita una decision de
 * negocio y ademas 1.649 de 2.287 facturas de la base lo incumplen. Queda
 * anotado como D-36 y, de momento, deja rastro en la bitacora en vez de pasar
 * en silencio.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Repaso de flujos - huecos de validacion")
class ValidacionesDeFlujoTest {

    @Autowired private InventarioService inventarioService;
    @Autowired private FixturaVenta fixtura;

    @BeforeEach
    void prepararDatos() {
        fixtura.empezar();
    }

    @AfterEach
    void borrarDatos() {
        fixtura.limpiar();
    }

    @Test
    @DisplayName("un traslado con la misma bodega de origen y destino se rechaza")
    void trasladoASiMismaSeRechaza() {
        Bodega bodega = fixtura.bodegaConStock("A", 20);

        MovimientoRequestDTO traslado = new MovimientoRequestDTO();
        traslado.setIdProducto(fixtura.getIdProducto());
        traslado.setIdBodega(bodega.getIdBodega());
        traslado.setIdBodegaDestino(bodega.getIdBodega());
        traslado.setTipoMovimiento("traslado");
        traslado.setCantidad(5);

        // Antes pasaba: origen y destino son la MISMA fila, el -5 y el +5 se
        // anulan, y en el kardex quedaba un traslado que no traslado nada.
        assertThatThrownBy(() -> inventarioService.registrarMovimiento(traslado, fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("misma bodega de origen y destino");

        assertThat(fixtura.stockEnBaseDe(bodega.getIdBodega())).isEqualTo(20);
    }

    @Test
    @DisplayName("un traslado entre dos bodegas distintas sigue funcionando")
    void trasladoNormalSigueFuncionando() {
        Bodega origen = fixtura.bodegaConStock("A", 20);
        Bodega destino = fixtura.bodegaConStock("B", 0);

        MovimientoRequestDTO traslado = new MovimientoRequestDTO();
        traslado.setIdProducto(fixtura.getIdProducto());
        traslado.setIdBodega(origen.getIdBodega());
        traslado.setIdBodegaDestino(destino.getIdBodega());
        traslado.setTipoMovimiento("traslado");
        traslado.setCantidad(5);

        inventarioService.registrarMovimiento(traslado, fixtura.getIdUsuario());

        assertThat(fixtura.stockEnBaseDe(origen.getIdBodega())).isEqualTo(15);
        assertThat(fixtura.stockEnBaseDe(destino.getIdBodega())).isEqualTo(5);
    }
}
