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
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "nombre", required = false) String nombre,
            @RequestParam(name = "estado", required = false) String estado) {
        return ResponseEntity.ok(clienteService.listar(page, size, nombre, estado));
    }

    @GetMapping("/activos")
    @PreAuthorize("hasAuthority('clientes:ver')")
    public ResponseEntity<List<ClienteResponseDTO>> listarActivos() {
        return ResponseEntity.ok(clienteService.listarActivos());
    }

    /**
     * Buscador de cliente para los selectores (F93).
     *
     * <p>Es lo que hay que usar desde un desplegable. {@code /activos} devuelve
     * la lista COMPLETA, y con el millon y medio de clientes de la F91 eso son
     * 299 MB en una respuesta: el navegador se queda inservible descargandolos,
     * parseandolos y despues recorriendolos en cada tecla.
     */
    @GetMapping("/buscar")
    @PreAuthorize("hasAuthority('clientes:ver')")
    public ResponseEntity<List<ClienteResponseDTO>> buscar(
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "limite", defaultValue = "20") int limite) {
        return ResponseEntity.ok(clienteService.buscarParaSelector(q, limite));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('clientes:ver')")
    public ResponseEntity<ClienteResponseDTO> obtener(@PathVariable(name = "id") Integer id) {
        return ResponseEntity.ok(clienteService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('clientes:crear')")
    public ResponseEntity<ClienteResponseDTO> crear(@Valid @RequestBody ClienteRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('clientes:editar')")
    public ResponseEntity<ClienteResponseDTO> actualizar(@PathVariable(name = "id") Integer id,
                                                          @Valid @RequestBody ClienteRequestDTO dto) {
        return ResponseEntity.ok(clienteService.actualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('clientes:eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable(name = "id") Integer id) {
        clienteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
