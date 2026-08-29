package com.marathon.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.ia.IAConsultaRequestDTO;
import com.marathon.dto.ia.IAResponseDTO;
import com.marathon.model.Usuario;
import com.marathon.service.IAService;

@RestController
@RequestMapping("/api/ia")
public class IAController {

    private final IAService iaService;

    public IAController(IAService iaService) {
        this.iaService = iaService;
    }

    @PostMapping("/consultar")
    @PreAuthorize("hasAuthority('ia:consultar')")
    public ResponseEntity<IAResponseDTO> consultar(@RequestBody IAConsultaRequestDTO request) {
        // Interruptor de la L2. Con app.ia.enabled=false ni siquiera se llama a
        // Anthropic ni se toca la base: el modulo esta apagado y lo dice.
        if (!iaService.estaHabilitado()) {
            IAResponseDTO apagado = new IAResponseDTO();
            apagado.setPregunta(request.getPregunta());
            apagado.setError("El asistente IA está deshabilitado en esta instalación. "
                    + "Se activa con app.ia.enabled=true.");
            apagado.setTimestamp(LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apagado);
        }

        Usuario usuario = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(iaService.consultar(request.getPregunta(), usuario.getIdUsuario()));
    }

    /**
     * Si el asistente esta encendido en esta instalacion (F82).
     *
     * <p>Antes solo se sabia <b>despues</b> de escribir una pregunta y enviarla:
     * la pantalla ofrecia ejemplos y una caja de texto como si funcionara, y el
     * 503 llegaba al final. Preguntarlo al entrar permite decirlo antes de que
     * alguien escriba.
     *
     * <p>No devuelve ni la clave ni el modelo: eso es configuracion del
     * servidor, y el navegador no tiene por que verla.
     */
    @GetMapping("/estado")
    @PreAuthorize("hasAuthority('ia:consultar')")
    public ResponseEntity<java.util.Map<String, Object>> estado() {
        return ResponseEntity.ok(java.util.Map.of("habilitado", iaService.estaHabilitado()));
    }

    @GetMapping("/ejemplos")
    @PreAuthorize("hasAuthority('ia:consultar')")
    public ResponseEntity<List<String>> ejemplos() {
        List<String> ejemplos = List.of(
            "¿Cuántos pedidos están pendientes hoy?",
            "¿Qué productos tienen stock bajo mínimo?",
            "¿Cuáles son los 5 productos más vendidos este mes?",
            "¿Cuánto se ha vendido en total esta semana?",
            "¿Qué pedidos especiales tienen fecha límite próxima?",
            "¿Cuántos movimientos de inventario hubo hoy?",
            "¿Qué clientes tienen más pedidos este mes?",
            "¿Cuál es el estado actual del inventario por bodega?"
        );
        return ResponseEntity.ok(ejemplos);
    }
}
