package com.marathon.service.ia;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Anthropic como traductor de preguntas a SQL.
 *
 * <p>Es el proveedor con el que nació el asistente; en la F83 se sacó del
 * servicio a una clase propia para poder cambiar de proveedor sin tocar la parte
 * que valida y ejecuta el SQL. El comportamiento es el mismo que tenía.
 */
@Component
public class ProveedorAnthropic implements ProveedorIA {

    @Value("${anthropic.api.key:}")
    private String apiKey;

    @Value("${anthropic.api.url:https://api.anthropic.com/v1/messages}")
    private String apiUrl;

    @Value("${anthropic.api.model:claude-sonnet-4-6}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebClient webClient;

    @Override
    public String nombre() {
        return "anthropic";
    }

    @Override
    public boolean estaConfigurado() {
        return apiKey != null && !apiKey.isBlank() && !"TU_API_KEY_AQUI".equals(apiKey);
    }

    @Override
    public String preguntar(String instrucciones, String pregunta) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 1000);
        body.put("system", instrucciones);
        body.put("messages", List.of(Map.of("role", "user", "content", pregunta)));

        String crudo = getWebClient().post()
                .uri(apiUrl)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();

        try {
            JsonNode root = objectMapper.readTree(crudo);
            return root.path("content").path(0).path("text").asText();
        } catch (Exception e) {
            throw new IllegalStateException("No se entendió la respuesta del modelo.", e);
        }
    }

    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = WebClient.builder()
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                    .build();
        }
        return webClient;
    }
}
