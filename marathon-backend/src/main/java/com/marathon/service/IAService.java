package com.marathon.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marathon.dto.ia.IAResponseDTO;
import com.marathon.service.ia.EjecutorConsultaIA;
import com.marathon.service.ia.ProveedorIA;
import com.marathon.service.ia.ValidadorSqlIA;


@Service
public class IAService {

    private static final Logger log = LoggerFactory.getLogger(IAService.class);

    /**
     * Quien contesta: {@code gemini} o {@code anthropic} (F83).
     *
     * <p>El proveedor es una pieza intercambiable porque lo que importa de este
     * servicio no es quien traduce la pregunta, sino lo que se hace despues con
     * lo que devuelve: validarlo, y ejecutarlo en solo lectura.
     */
    @Value("${app.ia.proveedor:gemini}")
    private String proveedorElegido;

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
    private final List<ProveedorIA> proveedores;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public IAService(IAContextService iaContextService,
                     ValidadorSqlIA validadorSql,
                     EjecutorConsultaIA ejecutor,
                     List<ProveedorIA> proveedores) {
        this.iaContextService = iaContextService;
        this.validadorSql = validadorSql;
        this.ejecutor = ejecutor;
        this.proveedores = proveedores;
    }

    /** El proveedor configurado, o vacio si el nombre no corresponde a ninguno. */
    /**
     * Traducciones ya hechas: pregunta en minúsculas -> respuesta cruda del modelo.
     *
     * <p>Se guarda la TRADUCCIÓN, no el resultado. Los datos se vuelven a
     * consultar en cada pregunta, así que nadie ve una cifra vieja; lo único que
     * se ahorra es volver a pedirle al modelo que traduzca una frase idéntica.
     *
     * <p>El tope existe porque esto vive en memoria del proceso y no debe crecer
     * sin límite. Al llenarse se vacía entero: es una caché de conveniencia, no
     * un índice, y un desalojo fino no compensa la complejidad.
     */
    private static final int MAX_TRADUCCIONES = 200;

    private final java.util.Map<String, String> traducciones =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());

    private void recordarTraduccion(String clave, String respuesta) {
        if (clave == null || clave.isEmpty() || respuesta == null) {
            return;
        }
        if (traducciones.size() >= MAX_TRADUCCIONES) {
            traducciones.clear();
        }
        traducciones.put(clave, respuesta);
    }

    private java.util.Optional<ProveedorIA> proveedor() {
        return proveedores.stream()
                .filter(p -> p.nombre().equalsIgnoreCase(proveedorElegido))
                .findFirst();
    }

    /** Para que el controlador pueda responder 503 sin construir la respuesta entera. */
    public boolean estaHabilitado() {
        return habilitado;
    }

    public IAResponseDTO consultar(String pregunta, Integer idUsuarioActual) {
        IAResponseDTO response = new IAResponseDTO();
        response.setPregunta(pregunta);

        // 1. Que haya un proveedor, y que tenga su clave
        java.util.Optional<ProveedorIA> elegido = proveedor();
        if (elegido.isEmpty()) {
            response.setError("El asistente está configurado con un proveedor que no existe: «"
                    + proveedorElegido + "». Los que hay son: "
                    + proveedores.stream().map(ProveedorIA::nombre).sorted().toList() + ".");
            response.setTimestamp(LocalDateTime.now());
            return response;
        }
        ProveedorIA proveedor = elegido.get();
        if (!proveedor.estaConfigurado()) {
            response.setError("Falta la clave del proveedor «" + proveedor.nombre()
                    + "». Se pone en la configuración local del servidor, nunca en el código.");
            response.setTimestamp(LocalDateTime.now());
            return response;
        }

        // 2 y 3. Preguntarle. Lo unico que vuelve es texto: interpretarlo,
        // validarlo y ejecutarlo es cosa de aqui, y no cambia con el proveedor.
        //
        // F94: si esta misma pregunta ya se tradujo hace poco, se reutiliza el
        // SQL en vez de volver a preguntar.
        //
        // POR QUE. La llamada a Google es lo que domina el tiempo del asistente,
        // y NO ES ESTABLE: midiendo la misma pregunta doce veces salieron desde
        // 0,28 s hasta 57 s. Eso no se arregla desde aqui — pero repetirla sí se
        // puede evitar. Y se repite mucho: quien prueba el asistente hace la
        // misma pregunta varias veces, y las preguntas de ejemplo de la pantalla
        // las pulsa todo el mundo.
        //
        // Se recuerda la TRADUCCION (pregunta -> SQL), no el resultado: los
        // datos se vuelven a consultar siempre, asi que la respuesta nunca es
        // vieja. Lo unico que se ahorra es volver a pedirle al modelo que
        // traduzca una frase que ya tradujo.
        String rawResponse;
        String clave = pregunta == null ? "" : pregunta.trim().toLowerCase();
        String recordado = traducciones.get(clave);
        if (recordado != null) {
            rawResponse = recordado;
        } else {
        try {
            rawResponse = proveedor.preguntar(iaContextService.getSchemaContext(), pregunta);
            recordarTraduccion(clave, rawResponse);
        } catch (Exception e) {
            log.warn("Fallo al hablar con el proveedor {}", proveedor.nombre(), e);
            response.setError(e instanceof IllegalStateException && e.getMessage() != null
                    ? e.getMessage()
                    : "No se pudo hablar con el asistente. Vuelve a intentarlo.");
            response.setTimestamp(LocalDateTime.now());
            return response;
        }
        }

        // 4. Parsear la respuesta de Anthropic
        String sql = null;
        String explicacion = null;
        try {
            // El proveedor ya devolvio el texto del modelo; aqui solo se le
            // quitan las comillas triples por si vinieran (Gemini las evita
            // pidiendo JSON, Anthropic no siempre).
            JsonNode inner = objectMapper.readTree(limpiarMarkdown(rawResponse));
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
