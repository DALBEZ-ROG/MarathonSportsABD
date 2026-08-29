package com.marathon.service.ia;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Google Gemini como traductor de preguntas a SQL (F83).
 *
 * <p><b>Tres diferencias con Anthropic</b>, y ninguna es un detalle:
 *
 * <ul>
 *   <li>La clave va en la cabecera <code>X-goog-api-key</code>, y <b>el modelo
 *       va en la URL</b>, no en el cuerpo. Por eso la URL se compone aquí.
 *   <li>Las instrucciones de sistema no son un campo <code>system</code> sino un
 *       objeto <code>systemInstruction</code> con la misma forma que un mensaje.
 *   <li>Se le pide <b>JSON directamente</b> con
 *       <code>responseMimeType: application/json</code>. Anthropic devolvía el
 *       JSON envuelto en un bloque markdown y había que quitarle las comillas
 *       triples a mano; aquí llega limpio, y quien llama sigue limpiándolo por si
 *       acaso.
 * </ul>
 *
 * <p><b>La clave nunca se registra ni viaja de vuelta al navegador.</b> Si falta,
 * se dice que falta —no se intenta la llamada— porque el error de red que
 * devolvería Google es mucho menos claro que la verdad.
 */
@Component
public class ProveedorGemini implements ProveedorIA {

    private static final Logger log = LoggerFactory.getLogger(ProveedorGemini.class);

    /** Cuantas veces se intenta cuando el modelo esta saturado. */
    private static final int INTENTOS = 3;

    /** Espera base entre intentos; crece con el numero de intento. */
    private static final long ESPERA_MS = 1500;

    @Value("${gemini.api.key:}")
    private String apiKey;

    /** Base sin el modelo: se compone en {@link #preguntar}. */
    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String apiUrl;

    @Value("${gemini.api.model:gemini-3.6-flash}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebClient webClient;

    @Override
    public String nombre() {
        return "gemini";
    }

    @Override
    public boolean estaConfigurado() {
        return apiKey != null && !apiKey.isBlank() && !"TU_API_KEY_AQUI".equals(apiKey);
    }

    @Override
    public String preguntar(String instrucciones, String pregunta) {
        Map<String, Object> parteUsuario = Map.of("parts", List.of(Map.of("text", pregunta)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(parteUsuario));
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", instrucciones))));
        // Se pide JSON de verdad, no JSON dentro de un bloque de markdown.
        body.put("generationConfig", Map.of(
                "responseMimeType", "application/json",
                "temperature", 0));

        return extraerTexto(llamarConReintento(body));
    }

    /**
     * Llama a Gemini y reintenta si el modelo esta saturado.
     *
     * <p><b>Por que hace falta.</b> El servicio devuelve 503 UNAVAILABLE con
     * bastante frecuencia -"this model is currently experiencing high demand"- y
     * casi siempre se le pasa en segundos. Sin reintento, esa pregunta se pierde
     * y el usuario tiene que volver a escribirla: un fallo del proveedor pagado
     * por quien pregunta.
     *
     * <p>Se reintenta <b>solo</b> lo que tiene sentido reintentar: saturacion
     * (503) y limite de ritmo (429). Una clave invalida o un modelo que no existe
     * no mejoran esperando, y reintentarlos solo hace perder tiempo.
     */
    private String llamarConReintento(Map<String, Object> body) {
        WebClientResponseException ultimo = null;
        for (int intento = 1; intento <= INTENTOS; intento++) {
            try {
                return getWebClient().post()
                        .uri(apiUrl + "/" + model + ":generateContent")
                        .header("X-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        // 30 s se quedaban cortos: el contexto del esquema es
                        // largo y la primera respuesta del dia tarda mas.
                        .timeout(Duration.ofSeconds(60))
                        .block();
            } catch (WebClientResponseException e) {
                int codigo = e.getStatusCode().value();
                // El cuerpo del error de Google dice QUE pasa -clave invalida,
                // modelo inexistente, cuota agotada- y "400 Bad Request" a secas
                // no dice nada. Va al log del servidor, no al navegador.
                log.warn("Gemini respondio {} al modelo {} (intento {}/{}): {}",
                         e.getStatusCode(), model, intento, INTENTOS,
                         e.getResponseBodyAsString());
                boolean vaAMejorar = codigo == 503 || codigo == 429;
                if (!vaAMejorar || intento == INTENTOS) {
                    throw new IllegalStateException(mensajeDeGoogle(e), e);
                }
                ultimo = e;
                esperar(ESPERA_MS * intento);
            }
        }
        throw new IllegalStateException(mensajeDeGoogle(ultimo));
    }

    private void esperar(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpio la espera al reintentar.", ie);
        }
    }

    /**
     * Saca el texto de la primera respuesta.
     *
     * <p>Si Gemini corta la respuesta —por filtro de seguridad o por longitud—
     * no hay <code>parts</code> y el texto sale vacío. Se dice con palabras en
     * lugar de devolver una cadena vacía que más adelante fallaría al
     * interpretarse como JSON, con un error que no señalaría a la causa.
     */
    private String extraerTexto(String crudo) {
        try {
            JsonNode root = objectMapper.readTree(crudo);
            JsonNode candidato = root.path("candidates").path(0);
            JsonNode texto = candidato.path("content").path("parts").path(0).path("text");
            if (texto.isMissingNode() || texto.isNull() || texto.asText().isBlank()) {
                String motivo = candidato.path("finishReason").asText("");
                throw new IllegalStateException(
                        "El modelo no devolvió ninguna respuesta"
                        + (motivo.isBlank() ? "." : " (motivo: " + motivo + ")."));
            }
            return texto.asText();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se entendió la respuesta del modelo.", e);
        }
    }

    /**
     * Traduce el error de Google a algo que se pueda leer y arreglar.
     *
     * <p>Los tres que salen de verdad son: la clave no vale, el modelo no
     * existe, y el modelo esta saturado. Los tres se arreglan de forma distinta
     * y ninguno se entiende leyendo "400 Bad Request".
     */
    private String mensajeDeGoogle(WebClientResponseException e) {
        String cuerpo = e.getResponseBodyAsString();
        int codigo = e.getStatusCode().value();
        if (codigo == 400 && cuerpo.contains("API key not valid")) {
            return "La clave de Gemini no es valida. Revisala en la configuracion local del servidor.";
        }
        if (codigo == 404) {
            return "El modelo «" + model + "» no existe o no esta disponible para esta clave.";
        }
        if (codigo == 429) {
            return "Se agoto la cuota de la clave de Gemini por ahora.";
        }
        if (codigo == 503) {
            return "El modelo esta saturado en este momento. Vuelve a intentarlo en un minuto.";
        }
        return "Gemini rechazo la peticion (" + codigo + ").";
    }

    private WebClient getWebClient() {
        if (webClient == null) {
            // El esquema de esta base es largo: el limite por defecto de 256 kB
            // se queda corto en cuanto la respuesta trae varias filas.
            webClient = WebClient.builder()
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                    .build();
        }
        return webClient;
    }
}
