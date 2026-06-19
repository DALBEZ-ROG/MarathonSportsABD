package com.marathon.controller;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.empaque.EmpaqueRequestDTO;
import com.marathon.dto.pedido.PedidoResponseDTO;
import com.marathon.model.Usuario;
import com.marathon.service.EmpaqueService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/empaque")
public class EmpaqueController {

    private final EmpaqueService empaqueService;

    public EmpaqueController(EmpaqueService empaqueService) {
        this.empaqueService = empaqueService;
    }

    @PostMapping("/pedidos/{idPedido}/confirmar")
    public ResponseEntity<PedidoResponseDTO> confirmarEmpaque(@PathVariable Integer idPedido,
                                                              @Valid @RequestBody EmpaqueRequestDTO dto) {
        Usuario usuario = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(empaqueService.confirmarEmpaque(idPedido, dto, usuario.getIdUsuario()));
    }

    @GetMapping("/pedidos")
    public ResponseEntity<PageResponseDTO<PedidoResponseDTO>> listarDespachados(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String regionDestino,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return ResponseEntity.ok(empaqueService.listarDespachados(page, size, regionDestino, desde, hasta));
    }
}
