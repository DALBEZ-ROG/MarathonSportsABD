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

    /**
     * Lo maximo que puede tardar la pregunta entera, reintentos incluidos (F95).
     *
     * <p><b>Por que un presupuesto y no un plazo por intento.</b> Antes cada
     * intento tenia sus propios 60 s y no habia limite al conjunto: tres
     * intentos lentos sumaban mas de tres minutos de reloj de arena. Medido el
     * 2026-09-01: 32,9 s de 503 + 2,6 s de 503 + 60 s agotados = 100 s para
     * acabar sin respuesta.
     *
     * <p>Lo que se le puede pedir a alguien que espera es un numero, y ese
     * numero es este. Cada intento usa lo que quede del presupuesto, nunca mas,
     * asi que la pregunta termina —bien o mal— dentro del plazo.
     *
     * <p>90 s parece mucho y lo es, pero acortarlo cortaria respuestas buenas:
     * una traduccion correcta ha tardado 57 s con Google saturado. Quien manda
     * aqui es la latencia real de Google, no el gusto.
     */
    private static final long PRESUPUESTO_MS = 90_000;

    /** Ningun intento suelto espera mas que esto, aunque sobre presupuesto. */
    private static final long PLAZO_INTENTO_MS = 60_000;

    /** Por debajo de esto no merece la pena empezar otro intento. */
    private static final long MINIMO_PARA_REINTENTAR_MS = 8_000;

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
     * (503), limite de ritmo (429) <b>y que se acabe el plazo</b>. Una clave
     * invalida o un modelo que no existe no mejoran esperando, y reintentarlos
     * solo hace perder tiempo.
     *
     * <p><b>F95 — el plazo agotado tambien es saturacion, y no se reintentaba.</b>
     * Un plazo agotado no llega como {@link WebClientResponseException} sino como
     * {@code TimeoutException} envuelta por Reactor, asi que se escapaba por
     * fuera del bucle: el intento se perdia sin reintentar y, peor, el motivo se
     * perdia con el. Quien preguntaba veia «No se pudo hablar con el asistente»
     * —el mensaje de un fallo desconocido— cuando lo que pasaba era justo lo que
     * este metodo sabe explicar: que el modelo esta saturado.
     *
     * <p>Es el mismo hecho contado de dos formas. Cuando Google va sobrecargado,
     * unas veces contesta 503 enseguida y otras no contesta a tiempo; tratar una
     * y no la otra era una distincion sin diferencia.
     */
    private String llamarConReintento(Map<String, Object> body) {
        long limite = System.nanoTime() + PRESUPUESTO_MS * 1_000_000L;
        RuntimeException ultimo = null;
        for (int intento = 1; intento <= INTENTOS; intento++) {
            long queda = (limite - System.nanoTime()) / 1_000_000L;
            if (queda < MINIMO_PARA_REINTENTAR_MS) {
                break;   // no da tiempo a otro intento; se responde con lo ultimo que se supo
            }
            long plazo = Math.min(queda, PLAZO_INTENTO_MS);
            // Cuanto tarda CADA intento. Sin esto, «el asistente tardo 95
            // segundos» no distingue una llamada lenta de tres reintentos
            // rapidos, y son dos problemas distintos: uno es Google yendo lento
            // y el otro es Google rechazando la peticion una y otra vez.
            long arranque = System.nanoTime();
            try {
                String respuesta = getWebClient().post()
                        .uri(apiUrl + "/" + model + ":generateContent")
                        .header("X-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        // 30 s se quedaban cortos: el contexto del esquema es
                        // largo y la primera respuesta del dia tarda mas.
                        .timeout(Duration.ofMillis(plazo))
                        .block();
                log.info("Gemini contesto en {} ms (intento {}/{})",
                         (System.nanoTime() - arranque) / 1_000_000, intento, INTENTOS);
                return respuesta;
            } catch (WebClientResponseException e) {
                int codigo = e.getStatusCode().value();
                // El cuerpo del error de Google dice QUE pasa -clave invalida,
                // modelo inexistente, cuota agotada- y "400 Bad Request" a secas
                // no dice nada. Va al log del servidor, no al navegador.
                log.warn("Gemini respondio {} al modelo {} tras {} ms (intento {}/{}): {}",
                         e.getStatusCode(), model, (System.nanoTime() - arranque) / 1_000_000,
                         intento, INTENTOS, e.getResponseBodyAsString());
                if (codigo != 503 && codigo != 429) {
                    throw new IllegalStateException(mensajeDeGoogle(e), e);
                }
                ultimo = new IllegalStateException(mensajeDeGoogle(e), e);
            } catch (RuntimeException e) {
                if (!esPlazoAgotado(e)) {
                    throw e;
                }
                log.warn("Gemini no contesto en {} ms al modelo {} (intento {}/{})",
                         plazo, model, intento, INTENTOS);
                ultimo = new IllegalStateException(
                        "El modelo tardo demasiado en contestar. Suele ser saturacion pasajera: "
                        + "vuelve a intentarlo en un minuto.", e);
            }
            if (intento < INTENTOS) {
                esperar(Math.min(ESPERA_MS * intento,
                                 Math.max(0, (limite - System.nanoTime()) / 1_000_000L)));
            }
        }
        throw ultimo != null ? ultimo
                : new IllegalStateException("El modelo esta saturado en este momento. "
                        + "Vuelve a intentarlo en un minuto.");
    }

    /**
     * Si la excepcion es «se acabo el plazo», mirando tambien lo que envuelve.
     *
     * <p>Reactor no propaga la {@code TimeoutException} tal cual: la envuelve en
     * una {@code ReactiveException} suya. Preguntar por el tipo de la de fuera
     * daba siempre que no, que es como este caso se colaba.
     */
    static boolean esPlazoAgotado(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
        }
        return false;
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
