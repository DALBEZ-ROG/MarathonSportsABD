package com.marathon.service.ia;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;

/**
 * Ejecuta la consulta del asistente IA dentro de una transaccion de solo
 * lectura (L2, defecto D-04).
 *
 * <p>Es la <b>segunda barrera</b>, independiente de {@link ValidadorSqlIA}. La
 * primera decide si el texto parece una lectura; esta hace que PostgreSQL lo
 * garantice: dentro de una transaccion marcada de solo lectura, el motor rechaza
 * INSERT, UPDATE, DELETE, TRUNCATE, COPY, CREATE, ALTER, DROP, GRANT y REVOKE,
 * incluidos los que vinieran desde dentro de una funcion. Aunque el validador se
 * dejara enganar algun dia, la escritura no llegaria a ocurrir.
 *
 * <p>Vive en su propia clase, y no como un metodo de {@code IAService}, por dos
 * motivos:
 *
 * <ul>
 *   <li>Spring aplica {@code @Transactional} a traves del proxy: una llamada
 *       interna desde {@code IAService} a un metodo suyo propio se saltaria la
 *       anotacion y la transaccion de solo lectura no existiria.</li>
 *   <li>La llamada HTTP a Anthropic tarda hasta 30 segundos. Envolverla en la
 *       misma transaccion mantendria una conexion del pool retenida todo ese
 *       rato para nada.</li>
 * </ul>
 */
@Component
public class EjecutorConsultaIA {

    /** Tope de filas que se devuelven al usuario. */
    private static final int MAX_FILAS = 500;

    /** Tope de tiempo de la consulta, para que una lectura pesada no bloquee el pool. */
    private static final String TIEMPO_MAXIMO = "10s";

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> ejecutar(String sql) {
        // readOnly=true ya pide al driver una conexion de solo lectura; esto lo
        // hace explicito a nivel de transaccion y no depende de como el pool
        // interprete la sugerencia.
        entityManager.createNativeQuery("SET LOCAL transaction_read_only = on").executeUpdate();
        entityManager.createNativeQuery("SET LOCAL statement_timeout = '" + TIEMPO_MAXIMO + "'")
                .executeUpdate();

        @SuppressWarnings("unchecked")
        List<Tuple> filas = entityManager.createNativeQuery(sql, Tuple.class)
                .setMaxResults(MAX_FILAS)
                .getResultList();

        List<Map<String, Object>> resultados = new ArrayList<>();
        for (Tuple fila : filas) {
            Map<String, Object> mapa = new LinkedHashMap<>();
            for (TupleElement<?> elemento : fila.getElements()) {
                String alias = elemento.getAlias();
                mapa.put(alias, fila.get(alias));
            }
            resultados.add(mapa);
        }
        return resultados;
    }
}
