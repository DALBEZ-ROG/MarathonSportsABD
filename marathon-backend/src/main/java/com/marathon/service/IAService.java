package com.marathon.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marathon.dto.ia.IAResponseDTO;
import com.marathon.service.ia.EjecutorConsultaIA;
import com.marathon.service.ia.ValidadorSqlIA;


@Service
public class IAService {

    private static final Logger log = LoggerFactory.getLogger(IAService.class);

    @Value("${anthropic.api.key}")
    private String apiKey;

    @Value("${anthropic.api.url}")
    private String apiUrl;

    @Value("${anthropic.api.model}")
    private String model;

    /**
     * Interruptor del asistente. Por defecto <b>apagado</b>: hasta la L2 este
     * modulo ejecutaba contra la base el SQL que devolvia el modelo, con una
     * lista de palabras prohibidas como unica defensa (D-04). Encenderlo es una
     * decision explicita de quien despliega.
     */
    @Value("${app.ia.enabled:false}")
    private boolean habilitado;

    private final IAContextService iaContextService;
    private final ValidadorSqlIA validadorSql;
    private final EjecutorConsultaIA ejecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebClient webClient;

    public IAService(IAContextService iaContextService,
                     ValidadorSqlIA validadorSql,
                     EjecutorConsultaIA ejecutor) {
        this.iaContextService = iaContextService;
        this.validadorSql = validadorSql;
        this.ejecutor = ejecutor;
    }

    /** Para que el controlador pueda responder 503 sin construir la respuesta entera. */
    public boolean estaHabilitado() {
        return habilitado;
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

        // 6. Seguridad: el SQL se analiza sintacticamente, no se compara por
        //    subcadenas. Ver ValidadorSqlIA para por que la comprobacion
        //    anterior fallaba en las dos direcciones a la vez (D-04 y D-30).
        if (sql != null) {
            ValidadorSqlIA.Veredicto veredicto = validadorSql.validar(sql);
            if (!veredicto.permitido()) {
                response.setError(veredicto.motivo());
                response.setTimestamp(LocalDateTime.now());
                return response;
            }

            // 7. Ejecutar en una transaccion de solo lectura (segunda barrera).
            try {
                List<Map<String, Object>> resultados = ejecutor.ejecutar(sql);

                // 8. Asignar resultados
                response.setResultados(resultados);
                response.setTotalResultados(resultados.size());
            } catch (Exception e) {
                // El detalle NO se devuelve al cliente: el mensaje crudo de
                // PostgreSQL convertia este endpoint en un oraculo para explorar
                // el esquema a base de consultas mal formadas (D-12).
                log.warn("Fallo al ejecutar la consulta del asistente IA. SQL: {}", sql, e);
                response.setError("No se pudo ejecutar la consulta. "
                        + "Prueba a reformular la pregunta.");
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
