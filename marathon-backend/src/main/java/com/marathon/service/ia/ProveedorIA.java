package com.marathon.service.ia;

/**
 * Quien traduce la pregunta en castellano a la consulta SQL (F83).
 *
 * <p><b>Por qué existe esta interfaz.</b> Hasta la F83 el servicio hablaba
 * directamente con Anthropic: el cuerpo de la petición, las cabeceras y el
 * camino dentro del JSON de respuesta estaban escritos a mano en medio de la
 * lógica. Cambiar de proveedor obligaba a tocar el mismo método que valida y
 * ejecuta el SQL, que es la parte que no debe moverse.
 *
 * <p>Ahora el proveedor es una pieza aparte con una sola responsabilidad:
 * <b>recibe dos textos y devuelve uno</b>. Todo lo demás —validar que el SQL es
 * un SELECT, ejecutarlo en solo lectura, no filtrar el error de PostgreSQL al
 * cliente— sigue igual y no depende de quién conteste.
 */
public interface ProveedorIA {

    /**
     * @param instrucciones el contexto del esquema y el formato de salida
     * @param pregunta      lo que escribió la persona
     * @return el texto crudo del modelo, tal cual; interpretarlo es de quien llama
     */
    String preguntar(String instrucciones, String pregunta);

    /** Nombre corto para la configuración: {@code anthropic}, {@code gemini}. */
    String nombre();

    /**
     * Si este proveedor tiene lo que necesita para trabajar.
     *
     * <p>Sin clave no se intenta la llamada: el error de red que devolvería el
     * servicio ajeno es mucho menos claro que decir que falta la clave.
     */
    boolean estaConfigurado();
}
