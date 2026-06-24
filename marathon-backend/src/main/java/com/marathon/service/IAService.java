package com.marathon.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marathon.dto.ia.IAResponseDTO;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;

@Service
public class IAService {

    @Value("${anthropic.api.key}")
    private String apiKey;

    @Value("${anthropic.api.url}")
    private String apiUrl;

    @Value("${anthropic.api.model}")
    private String model;

    private final IAContextService iaContextService;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebClient webClient;

    public IAService(IAContextService iaContextService) {
        this.iaContextService = iaContextService;
    }

    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = WebClient.builder().build();
        }
        return webClient;
    }

    public IAResponseDTO consultar(String pregunta, Integer idUsuarioActual) {
        IAResponseDTO response = new IAResponseDTO();
        response.setPregunta(pregunta);

        // 1. Validar configuración de API key
        if (apiKey == null || apiKey.trim().isEmpty() || "TU_API_KEY_AQUI".equals(apiKey)) {
            response.setError("El asistente IA no está configurado. Falta la API key de Anthropic.");
            response.setTimestamp(LocalDateTime.now());
            return response;
        }

        // 2. Construir cuerpo de la petición
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 1000);
        body.put("system", iaContextService.getSchemaContext());
        Map<String, Object> mensaje = new LinkedHashMap<>();
        mensaje.put("role", "user");
        mensaje.put("content", pregunta);
        body.put("messages", List.of(mensaje));

        // 3. Llamar a la API de Anthropic
        String rawResponse;
        try {
            rawResponse = getWebClient().post()
                .uri(apiUrl)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
        } catch (Exception e) {
            response.setError("Error al conectar con el asistente IA: " + e.getMessage());
            response.setTimestamp(LocalDateTime.now());
            return response;
        }

        // 4. Parsear la respuesta de Anthropic
        String sql = null;
        String explicacion = null;
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String texto = root.path("content").path(0).path("text").asText();
            texto = limpiarMarkdown(texto);
            JsonNode inner = objectMapper.readTree(texto);
            JsonNode sqlNode = inner.path("sql");
            if (!sqlNode.isMissingNode() && !sqlNode.isNull()) {
                sql = sqlNode.asText();
                if (sql != null && sql.trim().isEmpty()) {
                    sql = null;
                }
            }
            explicacion = inner.path("explicacion").asText(null);
        } catch (Exception e) {
            response.setError("Error al interpretar la respuesta del asistente IA: " + e.getMessage());
            response.setTimestamp(LocalDateTime.now());
            return response;
        }

        // 5. Asignar sql y explicacion
        response.setSql(sql);
        response.setExplicacion(explicacion);

        // 6. Seguridad: solo se permiten consultas SELECT
        if (sql != null) {
            String upper = sql.toUpperCase();
            String[] prohibidas = {"INSERT", "UPDATE", "DELETE", "DROP", "TRUNCATE", "ALTER", "CREATE"};
            for (String palabra : prohibidas) {
                if (upper.contains(palabra)) {
                    response.setError("Query no permitida por seguridad");
                    response.setTimestamp(LocalDateTime.now());
                    return response;
                }
            }

            // 7. Ejecutar la consulta usando Tuple para obtener nombres de columna
            try {
                @SuppressWarnings("unchecked")
                List<Tuple> rows = entityManager.createNativeQuery(sql, Tuple.class)
                    .setMaxResults(500)
                    .getResultList();

                List<Map<String, Object>> resultados = new ArrayList<>();
                for (Tuple fila : rows) {
                    Map<String, Object> mapa = new LinkedHashMap<>();
                    for (TupleElement<?> elemento : fila.getElements()) {
                        String alias = elemento.getAlias();
                        mapa.put(alias, fila.get(alias));
                    }
                    resultados.add(mapa);
                }

                // 8. Asignar resultados
                response.setResultados(resultados);
                response.setTotalResultados(resultados.size());
            } catch (Exception e) {
                response.setError("Error al ejecutar la consulta: " + e.getMessage());
            }
        }

        // 9. Timestamp y retorno
        response.setTimestamp(LocalDateTime.now());
        return response;
    }

    private String limpiarMarkdown(String texto) {
        if (texto == null) {
            return "";
        }
        String limpio = texto.trim();
        if (limpio.startsWith("```")) {
            // Quitar la primera línea de apertura (```json o ```)
            int primerSalto = limpio.indexOf('\n');
            if (primerSalto != -1) {
                limpio = limpio.substring(primerSalto + 1);
            } else {
                limpio = limpio.replaceFirst("^```[a-zA-Z]*", "");
            }
            // Quitar el cierre ```
            int cierre = limpio.lastIndexOf("```");
            if (cierre != -1) {
                limpio = limpio.substring(0, cierre);
            }
        }
        return limpio.trim();
    }
}
