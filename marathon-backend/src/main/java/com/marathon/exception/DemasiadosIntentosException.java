package com.marathon.exception;

/**
 * Se han agotado los intentos de login permitidos (L10, defecto D-25).
 *
 * <p>Se traduce a HTTP 429 en {@link GlobalExceptionHandler}. Es distinto de
 * {@link ValidationException} (400) a proposito: al cliente le interesa saber
 * que el problema no son sus datos sino el ritmo.
 */
public class DemasiadosIntentosException extends RuntimeException {

    public DemasiadosIntentosException(String mensaje) {
        super(mensaje);
    }
}
