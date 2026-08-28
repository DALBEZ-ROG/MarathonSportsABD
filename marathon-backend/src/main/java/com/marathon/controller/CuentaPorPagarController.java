package com.marathon.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.cuentapagar.CuentaPorPagarResponseDTO;
import com.marathon.service.CuentaPorPagarService;

@RestController
@RequestMapping("/api/cuentas-por-pagar")
public class CuentaPorPagarController {

    private final CuentaPorPagarService cuentaService;

    public CuentaPorPagarController(CuentaPorPagarService cuentaService) {
        this.cuentaService = cuentaService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('cuentas_por_pagar:ver')")
    public ResponseEntity<PageResponseDTO<CuentaPorPagarResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer idProveedor,
            @RequestParam(required = false) String busqueda) {
        return ResponseEntity.ok(cuentaService.listar(page, size, estado, idProveedor, busqueda));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('cuentas_por_pagar:ver')")
    public ResponseEntity<CuentaPorPagarResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(cuentaService.obtener(id));
    }

    @GetMapping("/resumen-proveedor/{idProveedor}")
    @PreAuthorize("hasAuthority('cuentas_por_pagar:ver')")
    public ResponseEntity<CuentaPorPagarService.ResumenProveedorDTO> resumenPorProveedor(
            @PathVariable Integer idProveedor) {
        return ResponseEntity.ok(cuentaService.resumenPorProveedor(idProveedor));
    }
}
