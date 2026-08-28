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

import com.marathon.dto.empaque.EmpaqueRequestDTO;
import com.marathon.dto.inventario.MovimientoRequestDTO;
import com.marathon.dto.inventario.ReservaStockResponseDTO;
import com.marathon.dto.pedido.CambioEstadoDTO;
import com.marathon.dto.pedido.DetallePedidoItemDTO;
import com.marathon.dto.pedido.PedidoRequestDTO;
import com.marathon.dto.pedido.PedidoResponseDTO;
import com.marathon.exception.ValidationException;
import com.marathon.model.Bodega;
import com.marathon.model.Pedido;
import com.marathon.soporte.FixturaVenta;

/**
 * F47 (D-02) — la reserva de stock.
 *
 * <p>El defecto: crear un pedido no miraba el inventario, y nada impedia que dos
 * pedidos comprometieran las mismas unidades. Estas pruebas fijan las tres
 * decisiones de negocio del 2026-08-27, que son las que hacen que el codigo diga
 * una cosa y no otra:
 *
 * <ol>
 *   <li>se reserva al pasar a {@code procesado}, no al crear;</li>
 *   <li>anular libera y despachar consume;</li>
 *   <li>a los 7 dias la reserva <b>aparece en un informe</b>, y no se suelta
 *       sola.</li>
 * </ol>
 *
 * <p>Sin {@code @Transactional}, por el mismo motivo que
 * {@link EmpaqueServiceDespachoTest}: lo que se mide es que la transaccion del
 * servicio revierte, y una transaccion envolvente de la prueba lo taparia.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("F47 - reserva de stock (D-02)")
class ReservaStockTest {

    @Autowired private PedidoService pedidoService;
    @Autowired private EmpaqueService empaqueService;
    @Autowired private InventarioService inventarioService;
    @Autowired private ReservaStockService reservaStockService;
    @Autowired private FixturaVenta fixtura;

    @BeforeEach
    void prepararDatos() {
        fixtura.empezar();
    }

    @AfterEach
    void borrarDatos() {
        fixtura.limpiar();
    }

    private CambioEstadoDTO a(String estado) {
        CambioEstadoDTO dto = new CambioEstadoDTO();
        dto.setEstado(estado);
        return dto;
    }

    private EmpaqueRequestDTO datosDeEmpaque() {
        EmpaqueRequestDTO dto = new EmpaqueRequestDTO();
        dto.setNumeroHu("HU-RESERVA-001");
        dto.setTransportista("Transportista de prueba");
        dto.setRegionDestino("Region de prueba");
        return dto;
    }

    private PedidoRequestDTO pedidoDe(int cantidad, boolean especial) {
        PedidoRequestDTO dto = new PedidoRequestDTO();
        dto.setIdCliente(fixtura.getIdCliente());
        dto.setDescuento(BigDecimal.ZERO);
        dto.setEsPedidoEspecial(especial);
        if (especial) {
            dto.setTipoEspecial("personalizado");
        }
        DetallePedidoItemDTO item = new DetallePedidoItemDTO();
        item.setIdProducto(fixtura.getIdProducto());
        item.setCantidad(cantidad);
        item.setPrecioUnitario(fixtura.precioDeCatalogo());
        dto.setDetalles(List.of(item));
        return dto;
    }

    // ------------------------------------------------------------------
    // Decision 1: se comprueba al crear, se retiene al procesar
    // ------------------------------------------------------------------

    @Test
    @DisplayName("crear un pedido por encima del stock ahora falla, en vez de crearse tan campante")
    void crearPorEncimaDelStockFalla() {
        fixtura.bodegaConStock("A", 3);

        // Este era el defecto literal de D-02: se podian crear cien pedidos de
        // un articulo con tres unidades y ninguno protestaba.
        assertThatThrownBy(() -> pedidoService.crear(pedidoDe(10, false), fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No hay existencias disponibles")
                .hasMessageContaining("faltan 7");
    }

    @Test
    @DisplayName("crear dentro del stock funciona y NO retiene nada todavia")
    void crearDentroDelStockNoReserva() {
        fixtura.bodegaConStock("A", 10);

        PedidoResponseDTO creado = pedidoService.crear(pedidoDe(4, false), fixtura.getIdUsuario());
        fixtura.seguirPedido(creado.getIdPedido());

        assertThat(creado.getEstado()).isEqualTo("pendiente");
        // Comprobar no es reservar: mientras el pedido este en 'pendiente' las
        // unidades siguen disponibles para cualquier otro.
        assertThat(fixtura.reservadoActivoDe(creado.getIdPedido())).isZero();
        assertThat(reservaStockService.disponible(fixtura.getIdProducto())).isEqualTo(10);
    }

    @Test
    @DisplayName("un pedido ESPECIAL si se crea sin stock: se fabrica, no se coge de la estanteria")
    void pedidoEspecialSeCreaSinStock() {
        fixtura.bodegaConStock("A", 1);

        PedidoResponseDTO creado = pedidoService.crear(pedidoDe(50, true), fixtura.getIdUsuario());
        fixtura.seguirPedido(creado.getIdPedido());

        assertThat(creado.getIdPedido()).isNotNull();
        assertThat(creado.getEsPedidoEspecial()).isTrue();
    }

    @Test
    @DisplayName("pasar a procesado retiene las unidades")
    void procesarReserva() {
        fixtura.bodegaConStock("A", 10);
        Pedido pedido = fixtura.pedidoPendiente(4);

        pedidoService.cambiarEstado(pedido.getIdPedido(), a("procesado"));

        assertThat(fixtura.reservadoActivoDe(pedido.getIdPedido())).isEqualTo(4);
        assertThat(reservaStockService.stockTotal(fixtura.getIdProducto())).isEqualTo(10);
        assertThat(reservaStockService.disponible(fixtura.getIdProducto())).isEqualTo(6);
    }

    @Test
    @DisplayName("dos pedidos no pueden comprometer las mismas unidades")
    void dosPedidosNoSeSolapan() {
        fixtura.bodegaConStock("A", 10);
        Pedido primero = fixtura.pedidoPendiente(7);
        Pedido segundo = fixtura.pedidoPendiente(7);

        pedidoService.cambiarEstado(primero.getIdPedido(), a("procesado"));

        // Este es el corazon de D-02. Antes de la F47 los dos pasaban, los dos
        // se recogian, y el segundo se enteraba en el muelle.
        assertThatThrownBy(() -> pedidoService.cambiarEstado(segundo.getIdPedido(), a("procesado")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("solo hay 3 disponibles");

        // Y el que falla no queda a medias: ni reserva ni cambio de estado.
        assertThat(fixtura.reservadoActivoDe(segundo.getIdPedido())).isZero();
        assertThat(fixtura.estadoEnBaseDe(segundo.getIdPedido())).isEqualTo("pendiente");
    }

    @Test
    @DisplayName("si una linea no cabe, no se reserva NINGUNA del pedido")
    void reservaEsTodoONada() {
        fixtura.bodegaConStock("A", 5);
        Pedido pedido = fixtura.pedidoPendiente(9);

        assertThatThrownBy(() -> pedidoService.cambiarEstado(pedido.getIdPedido(), a("procesado")))
                .isInstanceOf(ValidationException.class);

        assertThat(fixtura.estadosReservaDe(pedido.getIdPedido())).isEmpty();
    }

    // ------------------------------------------------------------------
    // Decision 2: anular libera, despachar consume
    // ------------------------------------------------------------------

    @Test
    @DisplayName("anular el pedido devuelve las unidades al disponible")
    void anularLibera() {
        fixtura.bodegaConStock("A", 10);
        Pedido pedido = fixtura.pedidoPendiente(6);
        pedidoService.cambiarEstado(pedido.getIdPedido(), a("procesado"));
        assertThat(reservaStockService.disponible(fixtura.getIdProducto())).isEqualTo(4);

        pedidoService.cambiarEstado(pedido.getIdPedido(), a("anulado"));

        assertThat(fixtura.estadosReservaDe(pedido.getIdPedido())).containsExactly("liberada");
        assertThat(reservaStockService.disponible(fixtura.getIdProducto())).isEqualTo(10);
    }

    @Test
    @DisplayName("el despacho consume la reserva y baja el stock: no la descuenta dos veces")
    void despachoConsume() {
        Bodega bodega = fixtura.bodegaConStock("A", 10);
        Pedido pedido = fixtura.pedidoRecogidoDesde(4, bodega);
        // pedidoRecogidoDesde deja el pedido ya en 'procesado' sin pasar por
        // cambiarEstado, asi que la reserva se crea aqui a mano — es el caso de
        // un pedido que venia de antes de la F47 y luego se reserva.
        reservaStockService.reservarPara(pedido);
        assertThat(reservaStockService.disponible(fixtura.getIdProducto())).isEqualTo(6);

        empaqueService.confirmarEmpaque(pedido.getIdPedido(), datosDeEmpaque(), fixtura.getIdUsuario());

        assertThat(fixtura.estadosReservaDe(pedido.getIdPedido())).containsExactly("consumida");
        // El stock bajo de 10 a 6; la reserva ya no retiene nada. Si el despacho
        // dejara la reserva activa, el disponible seria 2 y habriamos descontado
        // las mismas 4 unidades dos veces.
        assertThat(reservaStockService.stockTotal(fixtura.getIdProducto())).isEqualTo(6);
        assertThat(reservaStockService.disponible(fixtura.getIdProducto())).isEqualTo(6);
    }

    @Test
    @DisplayName("un despacho no se lleva lo que otro pedido tiene reservado")
    void despachoNoInvadeReservaAjena() {
        Bodega bodega = fixtura.bodegaConStock("A", 10);

        Pedido conReserva = fixtura.pedidoPendiente(8);
        pedidoService.cambiarEstado(conReserva.getIdPedido(), a("procesado"));

        // Un pedido "antiguo" (de antes de la F47): esta en procesado y recogido,
        // pero no tiene reserva. Sin la comprobacion de la F47 se llevaria las 10
        // unidades y dejaria la reserva del otro sin respaldo.
        Pedido antiguo = fixtura.pedidoRecogidoDesde(10, bodega);

        assertThatThrownBy(() -> empaqueService.confirmarEmpaque(
                        antiguo.getIdPedido(), datosDeEmpaque(), fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reservadas por otros pedidos");

        assertThat(fixtura.stockEnBaseDe(bodega.getIdBodega())).isEqualTo(10);
        assertThat(fixtura.estadoEnBaseDe(antiguo.getIdPedido())).isEqualTo("procesado");
    }

    @Test
    @DisplayName("una salida manual de inventario tampoco invade la reserva")
    void salidaManualNoInvadeReserva() {
        Bodega bodega = fixtura.bodegaConStock("A", 10);
        Pedido pedido = fixtura.pedidoPendiente(8);
        pedidoService.cambiarEstado(pedido.getIdPedido(), a("procesado"));

        MovimientoRequestDTO salida = new MovimientoRequestDTO();
        salida.setIdProducto(fixtura.getIdProducto());
        salida.setIdBodega(bodega.getIdBodega());
        salida.setTipoMovimiento("salida");
        salida.setCantidad(5);

        assertThatThrownBy(() -> inventarioService.registrarMovimiento(salida, fixtura.getIdUsuario()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reservadas por pedidos ya procesados");

        assertThat(fixtura.stockEnBaseDe(bodega.getIdBodega())).isEqualTo(10);
    }

    // ------------------------------------------------------------------
    // Decision 3: a los 7 dias se avisa, no se suelta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("una reserva de mas de 7 dias sale en el informe pero SIGUE reteniendo")
    void vencidaSeInformaPeroNoSeLibera() {
        fixtura.bodegaConStock("A", 10);
        Pedido pedido = fixtura.pedidoPendiente(6);
        pedidoService.cambiarEstado(pedido.getIdPedido(), a("procesado"));

        fixtura.envejecerReservasDe(pedido.getIdPedido(), ReservaStockService.DIAS_VIGENCIA + 1);

        List<ReservaStockResponseDTO> vencidas = reservaStockService.informeDeVencidas();
        assertThat(vencidas).extracting(ReservaStockResponseDTO::getIdPedido)
                .contains(pedido.getIdPedido());
        assertThat(vencidas).filteredOn(r -> pedido.getIdPedido().equals(r.getIdPedido()))
                .allSatisfy(r -> {
                    assertThat(r.getDiasRetenida()).isGreaterThanOrEqualTo(ReservaStockService.DIAS_VIGENCIA);
                    assertThat(r.getEstado()).isEqualTo("activa");
                });

        // Vencer NO libera: sigue apartando las 6 unidades hasta que una persona
        // decida. Es exactamente la decision de negocio del 2026-08-27.
        assertThat(reservaStockService.disponible(fixtura.getIdProducto())).isEqualTo(4);
    }

    @Test
    @DisplayName("liberar a mano exige un motivo")
    void liberarAManoExigeMotivo() {
        fixtura.bodegaConStock("A", 10);
        Pedido pedido = fixtura.pedidoPendiente(6);
        pedidoService.cambiarEstado(pedido.getIdPedido(), a("procesado"));
        Integer idReserva = reservaStockService.activasDe(pedido.getIdPedido()).get(0).getIdReserva();

        assertThatThrownBy(() -> reservaStockService.liberarManualmente(idReserva, "  "))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("por que se libera");

        reservaStockService.liberarManualmente(idReserva, "Cliente desistio, confirmado por telefono");

        assertThat(fixtura.estadosReservaDe(pedido.getIdPedido())).containsExactly("liberada");
        assertThat(reservaStockService.disponible(fixtura.getIdProducto())).isEqualTo(10);
    }
}
