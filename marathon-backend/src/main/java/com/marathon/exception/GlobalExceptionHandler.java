package com.marathon.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** PostgreSQL: insufficient_privilege. */
    private static final String SQLSTATE_PRIVILEGIO_INSUFICIENTE = "42501";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                message,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "No tienes permisos para realizar esta acción",
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    /**
     * Privilegio denegado por la propia base de datos (fase 37).
     *
     * <p>Desde que cada rol se conecta con su propio usuario de PostgreSQL, la
     * ultima palabra sobre que puede hacer cada quien la tiene la base y no
     * {@code SecurityConfig}. Un privilegio que falte llega aqui como
     * {@code SQLSTATE 42501}, y sin esta traduccion se le presentaria al usuario
     * como un 500, que describe mal lo que paso: no es un fallo del servidor,
     * es una denegacion.
     *
     * <p>Es una red de seguridad, no la defensa: lo esperado es que
     * {@code SecurityConfig} corte antes estas peticiones. Si este 403 aparece
     * en el registro, es que las dos capas se han desalineado.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException ex) {
        if (!SQLSTATE_PRIVILEGIO_INSUFICIENTE.equals(sqlStateDe(ex))) {
            return handleGeneral(ex);
        }

        log.warn("La base de datos denego la operacion por falta de privilegios (SQLSTATE {}). "
               + "SecurityConfig deberia haber cortado esta peticion antes: {}",
                 SQLSTATE_PRIVILEGIO_INSUFICIENTE, ex.getMostSpecificCause().getMessage());

        ErrorResponse error = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "Tu rol no tiene permisos sobre estos datos",
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    /** Recorre la cadena de causas buscando el SQLSTATE que reporto PostgreSQL. */
    private static String sqlStateDe(Throwable ex) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql) {
                return sql.getSQLState();
            }
            if (causa.getCause() == causa) {
                break;
            }
        }
        return null;
    }

    /**
     * Violacion de integridad: nombre duplicado, clave foranea que impide un
     * borrado, CHECK que no se cumple. Es un conflicto con el estado actual de
     * los datos, no un fallo del servidor (L9, D-20).
     *
     * <p>Antes caia en {@code handleGeneral} y salia como un 500 con el texto
     * crudo de PostgreSQL. Borrar una categoria en uso devolvia
     * "could not execute statement... viola la llave foranea fk_producto_categoria".
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegridad(DataIntegrityViolationException ex) {
        String referencia = referenciaDe(ex);
        log.warn("Violacion de integridad [{}]: {}", referencia,
                 ex.getMostSpecificCause().getMessage());

        ErrorResponse error = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                "La operación entra en conflicto con datos existentes. "
                        + "Puede que el registro ya exista o que otro lo esté usando. "
                        + "Referencia: " + referencia,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    /** Demasiados intentos de login (L10, D-25). */
    @ExceptionHandler(DemasiadosIntentosException.class)
    public ResponseEntity<ErrorResponse> handleDemasiadosIntentos(DemasiadosIntentosException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.TOO_MANY_REQUESTS);
    }

    /** Cuerpo de la peticion ilegible o mal formado: es del cliente, no del servidor. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleCuerpoIlegible(HttpMessageNotReadableException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "El cuerpo de la petición no se pudo interpretar. Revisa el formato del JSON.",
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Ultimo recurso (L9, D-12).
     *
     * <p>Antes devolvia {@code "Error interno del servidor: " + ex.getMessage()}.
     * En un fallo de base de datos ese mensaje arrastra la sentencia SQL, nombres
     * de tablas y columnas, nombres de constraints y a veces valores: el
     * endpoint se convertia en un mapa del esquema para cualquiera que supiera
     * provocar errores.
     *
     * <p>Ahora el cliente recibe una referencia y el detalle vive en el registro
     * del servidor, que es donde sirve para depurar.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        String referencia = referenciaDe(ex);
        log.error("Error no controlado [{}]", referencia, ex);

        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "Error interno del servidor. Referencia: " + referencia,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Identificador corto que aparece en la respuesta y en el registro, para
     * poder cruzar lo que vio el usuario con la traza completa del servidor.
     */
    private static String referenciaDe(Throwable ex) {
        return Integer.toHexString(System.identityHashCode(ex)).toUpperCase();
    }
}
