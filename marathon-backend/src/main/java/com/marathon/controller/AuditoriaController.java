package com.marathon.controller;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.auditoria.AuditoriaHistorialDTO;
import com.marathon.dto.auditoria.ResumenHistorialDTO;
import com.marathon.service.AuditoriaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/inventario")
    public ResponseEntity<PageResponseDTO<AuditoriaHistorialDTO>> listarHistorial(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer idProducto,
            @RequestParam(required = false) Integer idBodega,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return ResponseEntity.ok(auditoriaService.listarHistorial(page, size, idProducto, idBodega, desde, hasta));
    }

    @GetMapping("/inventario/resumen")
    public ResponseEntity<ResumenHistorialDTO> resumen(@RequestParam Integer idProducto) {
        return ResponseEntity.ok(auditoriaService.resumenProducto(idProducto));
    }
}
