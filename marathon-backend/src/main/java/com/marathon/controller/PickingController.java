package com.marathon.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.picking.PickingLineaDTO;
import com.marathon.dto.picking.PickingPedidoDTO;
import com.marathon.dto.picking.PickingUpdateDTO;
import com.marathon.model.Usuario;
import com.marathon.service.PickingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/picking")
public class PickingController {

    private final PickingService pickingService;

    public PickingController(PickingService pickingService) {
        this.pickingService = pickingService;
    }

    @GetMapping("/pedidos")
    @PreAuthorize("hasAuthority('picking:ver')")
    public ResponseEntity<PageResponseDTO<PickingPedidoDTO>> listarPedidos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(pickingService.listarPedidosParaPicking(page, size));
    }

    @GetMapping("/pedidos/{idPedido}")
    @PreAuthorize("hasAuthority('picking:ver')")
    public ResponseEntity<PickingPedidoDTO> obtenerPedido(@PathVariable Integer idPedido) {
        return ResponseEntity.ok(pickingService.obtenerPickingPedido(idPedido));
    }

    @PutMapping("/pedidos/{idPedido}/lineas")
    @PreAuthorize("hasAuthority('picking:ejecutar')")
    public ResponseEntity<PickingLineaDTO> actualizarLinea(@PathVariable Integer idPedido,
                                                           @Valid @RequestBody PickingUpdateDTO dto) {
        Usuario usuario = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(pickingService.actualizarLinea(idPedido, dto, usuario.getIdUsuario()));
    }

    @GetMapping("/pedidos/{idPedido}/estado")
    @PreAuthorize("hasAuthority('picking:ver')")
    public ResponseEntity<Map<String, Object>> verificarEstado(@PathVariable Integer idPedido) {
        PickingPedidoDTO pedido = pickingService.obtenerPickingPedido(idPedido);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("estadoPicking", pedido.getEstadoPicking());
        response.put("lineasCompletadas", pedido.getLineasCompletadas());
        response.put("totalLineas", pedido.getTotalLineas());
        return ResponseEntity.ok(response);
    }
}
