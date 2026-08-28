package com.marathon.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.cliente.ClienteRequestDTO;
import com.marathon.dto.cliente.ClienteResponseDTO;
import com.marathon.service.ClienteService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('clientes:ver')")
    public ResponseEntity<PageResponseDTO<ClienteResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(clienteService.listar(page, size, nombre, estado));
    }

    @GetMapping("/activos")
    @PreAuthorize("hasAuthority('clientes:ver')")
    public ResponseEntity<List<ClienteResponseDTO>> listarActivos() {
        return ResponseEntity.ok(clienteService.listarActivos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('clientes:ver')")
    public ResponseEntity<ClienteResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(clienteService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('clientes:crear')")
    public ResponseEntity<ClienteResponseDTO> crear(@Valid @RequestBody ClienteRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('clientes:editar')")
    public ResponseEntity<ClienteResponseDTO> actualizar(@PathVariable Integer id,
                                                          @Valid @RequestBody ClienteRequestDTO dto) {
        return ResponseEntity.ok(clienteService.actualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('clientes:eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        clienteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
