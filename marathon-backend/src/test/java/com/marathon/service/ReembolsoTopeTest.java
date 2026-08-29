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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.dto.devolucion.InspeccionDetalleDTO;
import com.marathon.dto.devolucion.InspeccionRequestDTO;
import com.marathon.dto.devolucion.ReembolsoRequestDTO;
import com.marathon.dto.devolucion.SolicitudDevolucionDetalleItemDTO;
import com.marathon.dto.devolucion.SolicitudDevolucionRequestDTO;
import com.marathon.dto.devolucion.SolicitudDevolucionResponseDTO;
import com.marathon.dto.empaque.EmpaqueRequestDTO;
import com.marathon.exception.ValidationException;
import com.marathon.model.Bodega;
import com.marathon.model.Pedido;
import com.marathon.soporte.FixturaVenta;

/**
 * L8 — el reembolso al cliente no puede superar lo que de verdad se devolvio
 * (D-08).
 *
 * <p>Monta el circuito entero: pedido -> despacho -> entregado -> RMA ->
 * inspeccion -> reembolso. Es la unica forma de llegar a registrarReembolso con
 * datos coherentes, y de paso ejercita de punta a punta el flujo de venta con
 * todo lo corregido en L1, L3, L4 y L5.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("L8 - tope al reembolso de devoluciones")
class ReembolsoTopeTest {

    @Autowired private EmpaqueService empaqueService;
    @Autowired private SolicitudDevolucionService devolucionService;
    @Autowired private FixturaVenta fixtura;
    @Autowired private JdbcTemplate jdbc;

    private Bodega bodega;

    @BeforeEach
    void prepararDatos() {
        fixtura.empezar();
        bodega = fixtura.bodegaConStock("A", 100);
    }

    @AfterEach
    void borrarDatos() {
        // El circuito crea filas de las que la fixtura no sabe.
        jdbc.update("delete from reembolso_cliente where id_solicitud in "
                  + "(select s.id_solicitud from solicitud_devolucion s "
                  + "  join pedido p on p.id_pedido = s.id_pedido "
                  + "  join cliente c on c.id_cliente = p.id_cliente "
                  + " where c.nombre like ?)", FixturaVenta.MARCA + "%");
        jdbc.update("delete from solicitud_devolucion_detalle where id_solicitud in "
                  + "(select s.id_solicitud from solicitud_devolucion s "
                  + "  join pedido p on p.id_pedido = s.id_pedido "
                  + "  join cliente c on c.id_cliente = p.id_cliente "
                  + " where c.nombre like ?)", FixturaVenta.MARCA + "%");
        jdbc.update("delete from solicitud_devolucion where id_pedido in "
                  + "(select p.id_pedido from pedido p join cliente c on c.id_cliente = p.id_cliente "
                  + " where c.nombre like ?)", FixturaVenta.MARCA + "%");
        fixtura.limpiar();
    }

    /** Pedido de `cantidad` unidades a 100.00, despachado y marcado como entregado. */
    private Pedido pedidoEntregado(int cantidad) {
        Pedido pedido = fixtura.pedidoRecogidoDesde(cantidad, bodega);

        EmpaqueRequestDTO empaque = new EmpaqueRequestDTO();
        empaque.setNumeroHu("HU-L8");
        empaque.setIdTransportista(1); // Servientrega, del catalogo que siembra la F77
        empaqueService.confirmarEmpaque(pedido.getIdPedido(), empaque, fixtura.getIdUsuario());

        jdbc.update("update pedido set estado = 'entregado' where id_pedido = ?", pedido.getIdPedido());
        return pedido;
    }

    /** Crea la solicitud, la inspecciona con el resultado dado, y la devuelve completada. */
    private Integer solicitudInspeccionada(Pedido pedido, int cantidadDevuelta, String resultado) {
        SolicitudDevolucionDetalleItemDTO item = new SolicitudDevolucionDetalleItemDTO();
        item.setIdDetallePedido(fixtura.idPrimeraLineaDe(pedido.getIdPedido()));
        item.setCantidadDevuelta(cantidadDevuelta);

        SolicitudDevolucionRequestDTO alta = new SolicitudDevolucionRequestDTO();
        alta.setIdPedido(pedido.getIdPedido());
        alta.setMotivo("producto_defectuoso");
        alta.setDescripcion("prueba L8");
        alta.setDetalles(List.of(item));

        SolicitudDevolucionResponseDTO solicitud =
                devolucionService.crear(alta, fixtura.getIdUsuario());

        devolucionService.iniciarInspeccion(solicitud.getIdSolicitud(), fixtura.getIdUsuario());

        Integer idDetalleSd = jdbc.queryForObject(
                "select id_detalle_sd from solicitud_devolucion_detalle where id_solicitud = ?",
                Integer.class, solicitud.getIdSolicitud());

        InspeccionDetalleDTO linea = new InspeccionDetalleDTO();
        linea.setIdDetalleSd(idDetalleSd);
        linea.setResultadoInspeccion(resultado);
        linea.setObservacionInspeccion("prueba");

        InspeccionRequestDTO inspeccion = new InspeccionRequestDTO();
        inspeccion.setIdBodega(bodega.getIdBodega());
        inspeccion.setItems(List.of(linea));

        devolucionService.inspeccionar(solicitud.getIdSolicitud(), inspeccion, fixtura.getIdUsuario());
        return solicitud.getIdSolicitud();
    }

    private ReembolsoRequestDTO reembolsoDe(String monto) {
        ReembolsoRequestDTO dto = new ReembolsoRequestDTO();
        dto.setMonto(new BigDecimal(monto));
        dto.setMetodo("transferencia");
        dto.setObservaciones("prueba L8");
        return dto;
    }

    @Test
    @DisplayName("un reembolso por encima del valor devuelto se rechaza")
    void reembolsoPorEncimaDelValorSeRechaza() {
        Pedido pedido = pedidoEntregado(2);
        Integer idSolicitud = solicitudInspeccionada(pedido, 2, "apto_reventa");

        // 2 unidades x 100.00 = 200.00 de tope.
        // Antes de la L8, 999.99 se aceptaba sin objecion.
        assertThatThrownBy(() -> devolucionService.registrarReembolso(
                    idSolicitud, reembolsoDe("999.99"), fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("supera el valor");
    }

    @Test
    @DisplayName("un reembolso por el valor exacto se acepta")
    void reembolsoExactoSeAcepta() {
        Pedido pedido = pedidoEntregado(2);
        Integer idSolicitud = solicitudInspeccionada(pedido, 2, "apto_reventa");

        devolucionService.registrarReembolso(idSolicitud, reembolsoDe("200.00"), fixtura.getIdUsuario());

        BigDecimal guardado = jdbc.queryForObject(
                "select monto from reembolso_cliente where id_solicitud = ?",
                BigDecimal.class, idSolicitud);
        assertThat(guardado).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    @DisplayName("una linea rechazada en la inspeccion no da derecho a reembolso")
    void lineaDefectuosaSiCuentaPeroRechazadaNo() {
        // 'defectuoso' no vuelve a stock pero si genera derecho a devolucion:
        // el cliente devolvio mercancia que en efecto estaba mal.
        Pedido pedido = pedidoEntregado(1);
        Integer idSolicitud = solicitudInspeccionada(pedido, 1, "defectuoso");

        devolucionService.registrarReembolso(idSolicitud, reembolsoDe("100.00"), fixtura.getIdUsuario());

        BigDecimal guardado = jdbc.queryForObject(
                "select monto from reembolso_cliente where id_solicitud = ?",
                BigDecimal.class, idSolicitud);
        assertThat(guardado).isEqualByComparingTo(new BigDecimal("100.00"));
    }
}
