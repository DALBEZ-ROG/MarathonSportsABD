package com.marathon.controller;

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

import com.marathon.config.RoleRoutingDataSource;
import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.usuario.UsuarioCambiarPasswordDTO;
import com.marathon.dto.usuario.UsuarioRequestDTO;
import com.marathon.dto.usuario.UsuarioResponseDTO;
import com.marathon.dto.usuario.UsuarioUpdateDTO;
import com.marathon.service.UsuarioService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('usuarios:ver')")
    public ResponseEntity<PageResponseDTO<UsuarioResponseDTO>> listar(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "nombre", required = false) String nombre,
            @RequestParam(name = "estado", required = false) String estado) {
        return ResponseEntity.ok(usuarioService.listar(page, size, nombre, estado));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('usuarios:ver')")
    public ResponseEntity<UsuarioResponseDTO> obtener(@PathVariable(name = "id") Integer id) {
        return ResponseEntity.ok(usuarioService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('usuarios:crear')")
    public ResponseEntity<UsuarioResponseDTO> crear(@Valid @RequestBody UsuarioRequestDTO dto) {
        return new ResponseEntity<>(usuarioService.crear(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('usuarios:editar')")
    public ResponseEntity<UsuarioResponseDTO> actualizar(@PathVariable(name = "id") Integer id,
                                                          @Valid @RequestBody UsuarioUpdateDTO dto) {
        return ResponseEntity.ok(usuarioService.actualizar(id, dto));
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> cambiarPassword(@PathVariable(name = "id") Integer id,
                                                 @Valid @RequestBody UsuarioCambiarPasswordDTO dto) {
        // Este endpoint lo puede llamar cualquier usuario autenticado sobre su
        // propia cuenta, pero escribe en la tabla usuario, que en la base solo
        // el administrador puede modificar (F34). Se resuelve por el pool de
        // autenticacion en vez de abriendo ese UPDATE a los cinco roles, que
        // les permitiria cambiar la contrasena de cualquier cuenta.
        RoleRoutingDataSource.conPoolDeAutenticacion(() -> usuarioService.cambiarPassword(id, dto));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('usuarios:eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable(name = "id") Integer id) {
        usuarioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
