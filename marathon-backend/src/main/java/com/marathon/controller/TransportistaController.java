package com.marathon.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.repository.TransportistaRepository;

/**
 * El catálogo de transportistas, para el buscador del empaque (F77).
 *
 * <p>Solo lectura y un solo endpoint: es lo único que hace falta, y añadir
 * altas o bajas obligaría a conceder INSERT y UPDATE sobre la tabla a un rol de
 * almacén. Cuando haya que administrar el catálogo desde la interfaz será otra
 * fase, con su permiso y sus privilegios.
 */
@RestController
@RequestMapping("/api/transportistas")
public class TransportistaController {

    private final TransportistaRepository transportistaRepository;

    public TransportistaController(TransportistaRepository transportistaRepository) {
        this.transportistaRepository = transportistaRepository;
    }

    @GetMapping("/activos")
    @PreAuthorize("hasAuthority('transportistas:ver')")
    public ResponseEntity<List<Map<String, Object>>> activos() {
        List<Map<String, Object>> lista = transportistaRepository
                .activosConCobertura().stream()
                .map(t -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("idTransportista", t.getIdTransportista());
                    m.put("nombre", t.getNombre());
                    // F84: la cobertura era una frase ("Nacional, incluye
                    // Oriente"). Ahora son las regiones, ordenadas, mas la nota
                    // con lo que una lista de regiones no sabe decir.
                    m.put("regiones", t.getRegiones().stream().sorted().toList());
                    m.put("nota", t.getNota());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }
}
