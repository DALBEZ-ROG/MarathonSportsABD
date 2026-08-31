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
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "idProveedor", required = false) Integer idProveedor,
            @RequestParam(name = "busqueda", required = false) String busqueda) {
        return ResponseEntity.ok(cuentaService.listar(page, size, estado, idProveedor, busqueda));
    }

    /**
     * Cuántas cuentas están vencidas y cuánto suman (F94c).
     *
     * <p>Lo pide el aviso rojo de la pantalla. Antes lo calculaba el navegador
     * sumando las primeras mil filas y lo enseñaba como si fuera el total: con
     * 1,5 millones de cuentas, decía 43 millones donde había 46 mil millones.
     * Sumar es trabajo de la base.
     */
    @GetMapping("/resumen-vencidas")
    @PreAuthorize("hasAuthority('cuentas_por_pagar:ver')")
    public ResponseEntity<java.util.Map<String, Object>> resumenVencidas() {
        return ResponseEntity.ok(cuentaService.resumenVencidas());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('cuentas_por_pagar:ver')")
    public ResponseEntity<CuentaPorPagarResponseDTO> obtener(@PathVariable(name = "id") Integer id) {
        return ResponseEntity.ok(cuentaService.obtener(id));
    }

    @GetMapping("/resumen-proveedor/{idProveedor}")
    @PreAuthorize("hasAuthority('cuentas_por_pagar:ver')")
    public ResponseEntity<CuentaPorPagarService.ResumenProveedorDTO> resumenPorProveedor(
            @PathVariable(name = "idProveedor") Integer idProveedor) {
        return ResponseEntity.ok(cuentaService.resumenPorProveedor(idProveedor));
    }
}
