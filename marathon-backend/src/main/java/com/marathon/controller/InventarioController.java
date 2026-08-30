package com.marathon.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.inventario.HistorialResponseDTO;
import com.marathon.dto.inventario.InventarioResponseDTO;
import com.marathon.dto.inventario.MovimientoRequestDTO;
import com.marathon.dto.inventario.MovimientoResponseDTO;
import com.marathon.dto.inventario.ReservaStockResponseDTO;
import com.marathon.model.Usuario;
import com.marathon.service.InventarioService;
import com.marathon.service.ReservaStockService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/inventario")
public class InventarioController {

    private final InventarioService inventarioService;
    private final ReservaStockService reservaStockService;

    public InventarioController(InventarioService inventarioService,
                                ReservaStockService reservaStockService) {
        this.inventarioService = inventarioService;
        this.reservaStockService = reservaStockService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inventario:ver')")
    public ResponseEntity<PageResponseDTO<InventarioResponseDTO>> listar(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "idBodega", required = false) Integer idBodega,
            @RequestParam(name = "busqueda", required = false) String busqueda) {
        return ResponseEntity.ok(inventarioService.listar(page, size, idBodega, busqueda));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('inventario:ver')")
    public ResponseEntity<InventarioResponseDTO> obtener(@PathVariable(name = "id") Integer id) {
        return ResponseEntity.ok(inventarioService.obtener(id));
    }

    /**
     * Las referencias bajo mínimos. Acotada a propósito: es una lista de
     * trabajo, y el total exacto lo da {@code /stock-bajo/conteo}.
     */
    @GetMapping("/stock-bajo")
    @PreAuthorize("hasAuthority('inventario:ver')")
    public ResponseEntity<List<InventarioResponseDTO>> stockBajo(
            @RequestParam(name = "limite", defaultValue = "200") int limite) {
        return ResponseEntity.ok(inventarioService.stockBajo(limite));
    }

    /** Solo cuántas hay. Es lo que necesita el aviso de la pantalla (F94). */
    @GetMapping("/stock-bajo/conteo")
    @PreAuthorize("hasAuthority('inventario:ver')")
    public ResponseEntity<Long> conteoStockBajo() {
        return ResponseEntity.ok(inventarioService.contarStockBajo());
    }

    // ------------------------------------------------------------------
    // Reservas de stock (F47, D-02)
    // ------------------------------------------------------------------
    // Van declaradas ANTES de /{id} solo por legibilidad: Spring prefiere el
    // patron literal al de plantilla, asi que /reservas no se confunde con un id.

    /**
     * Reservas que llevan mas de {@code ReservaStockService.DIAS_VIGENCIA} dias
     * reteniendo mercancia.
     *
     * <p>Es un informe, no una tarea: <b>no libera nada</b>. La decision de
     * negocio del 2026-08-27 es explicita en que soltar una reserva sin que
     * nadie mire es peor que el problema que resuelve, asi que lo que hace este
     * endpoint es poner delante de una persona la lista para que decida.
     */
    @GetMapping("/reservas/vencidas")
    @PreAuthorize("hasAuthority('inventario:ver')")
    public ResponseEntity<List<ReservaStockResponseDTO>> reservasVencidas() {
        return ResponseEntity.ok(reservaStockService.informeDeVencidas());
    }

    /** Las reservas activas de un pedido concreto. */
    @GetMapping("/reservas/pedido/{idPedido}")
    @PreAuthorize("hasAuthority('inventario:ver')")
    public ResponseEntity<List<ReservaStockResponseDTO>> reservasDePedido(@PathVariable(name = "idPedido") Integer idPedido) {
        return ResponseEntity.ok(reservaStockService.activasDe(idPedido));
    }

    /**
     * Lo que de verdad se puede comprometer de un producto: existencias menos
     * reservas activas. Lo consulta la pantalla de pedidos antes de dejar
     * escribir una cantidad que el backend va a rechazar despues.
     */
    @GetMapping("/disponible/{idProducto}")
    @PreAuthorize("hasAuthority('inventario:ver')")
    public ResponseEntity<Map<String, Integer>> disponible(@PathVariable(name = "idProducto") Integer idProducto) {
        return ResponseEntity.ok(Map.of(
                "stockTotal", reservaStockService.stockTotal(idProducto),
                "reservado", reservaStockService.reservado(idProducto),
                "disponible", reservaStockService.disponible(idProducto)));
    }

    /**
     * Suelta una reserva vencida. Exige motivo: una reserva liberada a mano y
     * sin explicacion no se puede auditar despues.
     */
    @PostMapping("/reservas/{idReserva}/liberar")
    @PreAuthorize("hasAuthority('inventario:editar')")
    public ResponseEntity<ReservaStockResponseDTO> liberarReserva(
            @PathVariable(name = "idReserva") Integer idReserva,
            @RequestBody(required = false) Map<String, String> cuerpo) {
        String motivo = cuerpo != null ? cuerpo.get("motivo") : null;
        return ResponseEntity.ok(reservaStockService.aDTO(
                reservaStockService.liberarManualmente(idReserva, motivo)));
    }

    @GetMapping("/{idProducto}/{idBodega}/movimientos")
    @PreAuthorize("hasAuthority('inventario:ver')")
    public ResponseEntity<PageResponseDTO<MovimientoResponseDTO>> listarMovimientos(
            @PathVariable(name = "idProducto") Integer idProducto,
            @PathVariable(name = "idBodega") Integer idBodega,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(inventarioService.listarMovimientos(idProducto, idBodega, page, size));
    }

    @GetMapping("/{idInventario}/historial")
    @PreAuthorize("hasAuthority('inventario:ver')")
    public ResponseEntity<List<HistorialResponseDTO>> listarHistorial(@PathVariable(name = "idInventario") Integer idInventario) {
        return ResponseEntity.ok(inventarioService.listarHistorial(idInventario));
    }

    @PostMapping("/movimiento")
    @PreAuthorize("hasAuthority('inventario:editar')")
    public ResponseEntity<MovimientoResponseDTO> registrarMovimiento(@Valid @RequestBody MovimientoRequestDTO dto) {
        Usuario usuario = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return new ResponseEntity<>(inventarioService.registrarMovimiento(dto, usuario.getIdUsuario()), HttpStatus.CREATED);
    }
}
