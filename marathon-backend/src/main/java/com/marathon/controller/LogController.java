package com.marathon.controller;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.log.LogAccionResponseDTO;
import com.marathon.service.LogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<LogAccionResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer idUsuario,
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return ResponseEntity.ok(logService.listar(page, size, idUsuario, modulo, desde, hasta));
    }

    @GetMapping("/modulos")
    public ResponseEntity<List<String>> listarModulos() {
        return ResponseEntity.ok(logService.listarModulos());
    }
}
