package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.controller.PedidoController;
import com.marathon.dto.pedido.CambioEstadoDTO;
import com.marathon.dto.pedido.DetallePedidoItemDTO;
import com.marathon.dto.pedido.PedidoRequestDTO;
import com.marathon.exception.ValidationException;
import com.marathon.model.Pedido;
import com.marathon.model.Usuario;
import com.marathon.repository.UsuarioRepository;
import com.marathon.soporte.FixturaVenta;

/**
 * F48 (D-13) — la comprobacion de permisos MUERDE.
 *
 * <p>{@link MatrizPermisosTest} comprueba que la matriz esta completa y bien
 * escrita. Esto es lo otro, y es lo que de verdad cierra el defecto: que quitarle
 * un permiso a alguien le quita la capacidad de hacer eso. Antes de la F48
 * quitarlo no hacia absolutamente nada — el rol «Encargado de Produccion» tenia
 * cero permisos y trabajaba con normalidad.
 *
 * <p><b>Como se autentica sin librerias nuevas.</b> El proyecto no tiene
 * {@code spring-security-test} y la regla 3 de PENDIENTE.md dice que no se
 * anaden librerias si con lo que hay alcanza. Con lo que hay alcanza: se pone la
 * autenticacion a mano en el {@code SecurityContextHolder}, que es exactamente lo
 * que hace {@code JwtAuthenticationFilter} en cada peticion real. Como
 * {@code @PreAuthorize} actua sobre el bean de Spring, llamar al controlador
 * inyectado pasa por la misma comprobacion que pasaria una llamada HTTP.
 *
 * <p>El enrutado por rol esta apagado en el perfil de pruebas
 * ({@code app.datasource.roles.enabled=false}), asi que una autenticacion sin
 * {@code ROLE_*} no confunde a {@code RoleRoutingDataSource}.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("F48 - los permisos deciden (D-13)")
class PermisosSeAplicanTest {

    @Autowired private PedidoController pedidoController;
    @Autowired private PedidoService pedidoService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FixturaVenta fixtura;

    private Usuario admin;

    @BeforeEach
    void prepararDatos() {
        fixtura.empezar();
        fixtura.bodegaConStock("A", 100);
        admin = usuarioRepository.findByCorreo("admin@marathon.com").orElseThrow();
    }

    @AfterEach
    void borrarDatos() {
        SecurityContextHolder.clearContext();
        fixtura.limpiar();
    }

    /** Deja autenticado al admin, pero SOLO con los permisos que se le pasen. */
    private void entrarCon(String... permisos) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String permiso : permisos) {
            authorities.add(new SimpleGrantedAuthority(permiso));
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, authorities));
    }

    private PedidoRequestDTO pedidoDe(int cantidad) {
        PedidoRequestDTO dto = new PedidoRequestDTO();
        dto.setIdCliente(fixtura.getIdCliente());
        dto.setDescuento(BigDecimal.ZERO);
        dto.setEsPedidoEspecial(false);
        DetallePedidoItemDTO item = new DetallePedidoItemDTO();
        item.setIdProducto(fixtura.getIdProducto());
        item.setCantidad(cantidad);
        item.setPrecioUnitario(fixtura.precioDeCatalogo());
        dto.setDetalles(List.of(item));
        return dto;
    }

    private CambioEstadoDTO a(String estado) {
        CambioEstadoDTO dto = new CambioEstadoDTO();
        dto.setEstado(estado);
        return dto;
    }

    @Test
    @DisplayName("sin 'pedidos:crear' no se crea un pedido, por mucha sesion que se tenga")
    void sinPermisoNoSeCrea() {
        entrarCon("pedidos:ver");   // tiene sesion y ve pedidos, pero no puede crearlos

        assertThatThrownBy(() -> pedidoController.crear(
                        pedidoDe(2), SecurityContextHolder.getContext().getAuthentication()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("con 'pedidos:crear' si se crea")
    void conPermisoSiSeCrea() {
        entrarCon("pedidos:ver", "pedidos:crear");

        var respuesta = pedidoController.crear(
                pedidoDe(2), SecurityContextHolder.getContext().getAuthentication());

        assertThat(respuesta.getBody()).isNotNull();
        fixtura.seguirPedido(respuesta.getBody().getIdPedido());
        assertThat(respuesta.getBody().getEstado()).isEqualTo("pendiente");
    }

    @Test
    @DisplayName("'pedidos:editar' no sirve para anular: son dos permisos y la matriz los separa")
    void anularNecesitaSuPropioPermiso() {
        Pedido pedido = fixtura.pedidoPendiente(2);
        entrarCon("pedidos:ver", "pedidos:editar");

        // Procesar si puede: eso es 'editar'.
        assertThatCode(() -> pedidoService.cambiarEstado(pedido.getIdPedido(), a("procesado")))
                .doesNotThrowAnyException();

        // Anular no, y el mensaje dice exactamente que le falta.
        assertThatThrownBy(() -> pedidoService.cambiarEstado(pedido.getIdPedido(), a("anulado")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("pedidos:anular");

        assertThat(fixtura.estadoEnBaseDe(pedido.getIdPedido())).isEqualTo("procesado");
    }

    @Test
    @DisplayName("con 'pedidos:anular' si se anula, y la reserva se suelta")
    void conPermisoDeAnularSeAnula() {
        Pedido pedido = fixtura.pedidoPendiente(2);
        entrarCon("pedidos:ver", "pedidos:editar", "pedidos:anular");

        pedidoService.cambiarEstado(pedido.getIdPedido(), a("procesado"));
        assertThat(fixtura.reservadoActivoDe(pedido.getIdPedido())).isEqualTo(2);

        pedidoService.cambiarEstado(pedido.getIdPedido(), a("anulado"));

        assertThat(fixtura.estadoEnBaseDe(pedido.getIdPedido())).isEqualTo("anulado");
        assertThat(fixtura.reservadoActivoDe(pedido.getIdPedido())).isZero();
    }

    @Test
    @DisplayName("sin sesion los servicios siguen funcionando: la puerta es SecurityConfig, no esto")
    void sinSesionNoSeComprueba() {
        SecurityContextHolder.clearContext();
        Pedido pedido = fixtura.pedidoPendiente(2);

        // Es lo que permite que el resto del arnes de pruebas —y cualquier tarea
        // interna sin usuario— siga llamando a los servicios. El acceso ya lo
        // filtro SecurityConfig antes de llegar aqui.
        assertThatCode(() -> pedidoService.cambiarEstado(pedido.getIdPedido(), a("procesado")))
                .doesNotThrowAnyException();
    }
}
