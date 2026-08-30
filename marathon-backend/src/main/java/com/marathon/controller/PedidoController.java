package com.marathon.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.pedido.CambioEstadoDTO;
import com.marathon.dto.pedido.PedidoRequestDTO;
import com.marathon.dto.pedido.PedidoResponseDTO;
import com.marathon.model.Usuario;
import com.marathon.service.PedidoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('pedidos:ver')")
    public ResponseEntity<PageResponseDTO<PedidoResponseDTO>> listar(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "fechaDesde", required = false) String fechaDesde,
            @RequestParam(name = "busqueda", required = false) String busqueda,
            @RequestParam(name = "fechaHasta", required = false) String fechaHasta) {
        return ResponseEntity.ok(pedidoService.listar(page, size, estado, fechaDesde, fechaHasta, busqueda));
    }

    @GetMapping("/especiales")
    @PreAuthorize("hasAuthority('pedidos:ver')")
    public ResponseEntity<PageResponseDTO<PedidoResponseDTO>> listarEspeciales(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "tipoEspecial", required = false) String tipoEspecial) {
        return ResponseEntity.ok(pedidoService.listarEspeciales(page, size, tipoEspecial));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pedidos:ver')")
    public ResponseEntity<PedidoResponseDTO> obtener(@PathVariable(name = "id") Integer id) {
        return ResponseEntity.ok(pedidoService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('pedidos:crear')")
    public ResponseEntity<PedidoResponseDTO> crear(@Valid @RequestBody PedidoRequestDTO dto,
                                                    Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pedidoService.crear(dto, usuario.getIdUsuario()));
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<PedidoResponseDTO> cambiarEstado(@PathVariable(name = "id") Integer id,
                                                            @Valid @RequestBody CambioEstadoDTO dto) {
        return ResponseEntity.ok(pedidoService.cambiarEstado(id, dto));
    }
}
