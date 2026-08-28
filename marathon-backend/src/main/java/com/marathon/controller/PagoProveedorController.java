package com.marathon.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.pago.PagoProveedorRequestDTO;
import com.marathon.dto.pago.PagoProveedorResponseDTO;
import com.marathon.model.Usuario;
import com.marathon.service.PagoProveedorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/pagos-proveedor")
public class PagoProveedorController {

    private final PagoProveedorService pagoService;

    public PagoProveedorController(PagoProveedorService pagoService) {
        this.pagoService = pagoService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('pagos_proveedor:registrar')")
    public ResponseEntity<PagoProveedorResponseDTO> registrarPago(
            @Valid @RequestBody PagoProveedorRequestDTO dto,
            Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pagoService.registrarPago(dto, usuario.getIdUsuario()));
    }

    @GetMapping("/cuenta/{idCuentaPagar}")
    @PreAuthorize("hasAuthority('pagos_proveedor:ver')")
    public ResponseEntity<List<PagoProveedorResponseDTO>> listarPorCuenta(
            @PathVariable Integer idCuentaPagar) {
        return ResponseEntity.ok(pagoService.listarPorCuenta(idCuentaPagar));
    }
}
