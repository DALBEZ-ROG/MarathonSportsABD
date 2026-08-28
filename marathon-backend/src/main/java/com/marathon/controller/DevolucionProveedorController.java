package com.marathon.controller;

import java.util.List;
import java.util.Map;

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
import com.marathon.dto.devolucionproveedor.DevolucionProveedorRequestDTO;
import com.marathon.dto.devolucionproveedor.DevolucionProveedorResponseDTO;
import com.marathon.dto.devolucionproveedor.ItemDefectuosoDisponibleDTO;
import com.marathon.dto.devolucionproveedor.ResolucionDevolucionDTO;
import com.marathon.model.Usuario;
import com.marathon.service.DevolucionProveedorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/devoluciones-proveedor")
public class DevolucionProveedorController {

    private final DevolucionProveedorService service;

    public DevolucionProveedorController(DevolucionProveedorService service) {
        this.service = service;
    }

    @GetMapping("/items-disponibles")
    @PreAuthorize("hasAuthority('devoluciones_proveedor:ver')")
    public ResponseEntity<List<ItemDefectuosoDisponibleDTO>> itemsDisponibles(
            @RequestParam(required = false) Integer idProveedor,
            @RequestParam(required = false) String busqueda) {
        return ResponseEntity.ok(service.listarItemsDefectuososDisponibles(idProveedor));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('devoluciones_proveedor:ver')")
    public ResponseEntity<PageResponseDTO<DevolucionProveedorResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer idProveedor,
            @RequestParam(required = false) String busqueda) {
        return ResponseEntity.ok(service.listar(page, size, estado, idProveedor, busqueda));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('devoluciones_proveedor:ver')")
    public ResponseEntity<DevolucionProveedorResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('devoluciones_proveedor:crear')")
    public ResponseEntity<DevolucionProveedorResponseDTO> crear(
            @Valid @RequestBody DevolucionProveedorRequestDTO dto,
            Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.crear(dto, usuario.getIdUsuario()));
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('devoluciones_proveedor:resolver')")
    public ResponseEntity<DevolucionProveedorResponseDTO> cambiarEstado(
            @PathVariable Integer id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(service.cambiarEstado(id, body.get("estado"), usuario.getIdUsuario()));
    }

    @PostMapping("/{id}/resolver")
    @PreAuthorize("hasAuthority('devoluciones_proveedor:resolver')")
    public ResponseEntity<DevolucionProveedorResponseDTO> resolver(
            @PathVariable Integer id,
            @Valid @RequestBody ResolucionDevolucionDTO dto,
            Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(service.resolver(id, dto, usuario.getIdUsuario()));
    }
}
