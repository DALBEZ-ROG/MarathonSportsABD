package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.dto.inventario.MovimientoRequestDTO;
import com.marathon.exception.ValidationException;
import com.marathon.model.Bodega;
import com.marathon.soporte.FixturaVenta;

/**
 * L5 — el traslado funciona (D-35) y el ajuste no miente en el kardex (D-15).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("L5 - movimientos de inventario")
class InventarioMovimientoTest {

    @Autowired private InventarioService inventarioService;
    @Autowired private FixturaVenta fixtura;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void prepararDatos() {
        fixtura.empezar();
    }

    @AfterEach
    void borrarDatos() {
        fixtura.limpiar();
    }

    private MovimientoRequestDTO movimiento(String tipo, Bodega origen, int cantidad) {
        MovimientoRequestDTO dto = new MovimientoRequestDTO();
        dto.setIdProducto(fixtura.getIdProducto());
        dto.setIdBodega(origen.getIdBodega());
        dto.setTipoMovimiento(tipo);
        dto.setCantidad(cantidad);
        return dto;
    }

    // ---------------------------------------------------------------- D-35 ---

    @Test
    @DisplayName("el traslado entre bodegas funciona y deja el destino informado")
    void elTrasladoFunciona() {
        Bodega origen = fixtura.bodegaConStock("origen", 20);
        Bodega destino = fixtura.bodegaConStock("destino", 3);

        MovimientoRequestDTO dto = movimiento("traslado", origen, 5);
        dto.setIdBodegaDestino(destino.getIdBodega());

        // Antes de la L5 esto lanzaba un 500: el INSERT del movimiento violaba
        // chk_traslado_requiere_destino porque nadie llamaba nunca a
        // setInventarioDestino.
        inventarioService.registrarMovimiento(dto, fixtura.getIdUsuario());

        assertThat(fixtura.stockEnBaseDe(origen.getIdBodega())).isEqualTo(15);
        assertThat(fixtura.stockEnBaseDe(destino.getIdBodega())).isEqualTo(8);

        Integer conDestino = jdbc.queryForObject(
                "select count(*) from movimiento_inventario m "
              + " join inventario i on i.id_inventario = m.id_inventario "
              + "where i.id_producto = ? and m.tipo_movimiento = 'traslado' "
              + "  and m.id_inventario_destino is not null",
                Integer.class, fixtura.getIdProducto());

        assertThat(conDestino)
                .as("el movimiento de traslado debe llevar la bodega destino")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("un traslado sin stock suficiente se rechaza y no deja rastro")
    void trasladoSinStockSeRechaza() {
        Bodega origen = fixtura.bodegaConStock("origen", 2);
        Bodega destino = fixtura.bodegaConStock("destino", 0);

        MovimientoRequestDTO dto = movimiento("traslado", origen, 10);
        dto.setIdBodegaDestino(destino.getIdBodega());

        assertThatThrownBy(() -> inventarioService.registrarMovimiento(dto, fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class);

        assertThat(fixtura.stockEnBaseDe(origen.getIdBodega())).isEqualTo(2);
        assertThat(fixtura.stockEnBaseDe(destino.getIdBodega())).isZero();
    }

    // ---------------------------------------------------------------- D-15 ---

    @Test
    @DisplayName("el ajuste graba la diferencia, no el saldo nuevo")
    void elAjusteGrabaLaDiferencia() {
        Bodega bodega = fixtura.bodegaConStock("A", 10);

        // Ajustar de 10 a 4: el movimiento debe decir 6, no 4.
        inventarioService.registrarMovimiento(movimiento("ajuste", bodega, 4), fixtura.getIdUsuario());

        assertThat(fixtura.stockEnBaseDe(bodega.getIdBodega())).isEqualTo(4);

        Integer cantidad = jdbc.queryForObject(
                "select m.cantidad from movimiento_inventario m "
              + " join inventario i on i.id_inventario = m.id_inventario "
              + "where i.id_producto = ? and m.tipo_movimiento = 'ajuste'",
                Integer.class, fixtura.getIdProducto());

        assertThat(cantidad)
                .as("10 -> 4 es un movimiento de 6, no de 4")
                .isEqualTo(6);
    }

    @Test
    @DisplayName("un ajuste que no cambia nada se rechaza")
    void ajusteSinCambioSeRechaza() {
        Bodega bodega = fixtura.bodegaConStock("A", 7);

        assertThatThrownBy(() -> inventarioService.registrarMovimiento(
                    movimiento("ajuste", bodega, 7), fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no cambia el stock");
    }

    @Test
    @DisplayName("entrada y salida siguen funcionando igual")
    void entradaYSalidaNoCambian() {
        Bodega bodega = fixtura.bodegaConStock("A", 10);

        inventarioService.registrarMovimiento(movimiento("entrada", bodega, 5), fixtura.getIdUsuario());
        assertThat(fixtura.stockEnBaseDe(bodega.getIdBodega())).isEqualTo(15);

        inventarioService.registrarMovimiento(movimiento("salida", bodega, 3), fixtura.getIdUsuario());
        assertThat(fixtura.stockEnBaseDe(bodega.getIdBodega())).isEqualTo(12);

        assertThatThrownBy(() -> inventarioService.registrarMovimiento(
                    movimiento("salida", bodega, 999), fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class);
    }
}
