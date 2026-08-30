package com.marathon.dto.respaldo;

/**
 * El cuerpo de las tres peticiones del modulo.
 *
 * <p><b>Por que la confirmacion se comprueba en el servidor y no solo en el
 * dialogo del navegador.</b> Un dialogo de «¿estas seguro?» protege del clic
 * despistado, no de un POST hecho a mano contra la API. Borrar la base entera
 * es la operacion mas destructiva del sistema: la palabra escrita viaja al
 * servidor y el servidor la compara, igual que el resto de reglas de negocio.
 * La pantalla la recibe de {@code /api/respaldos/estado}, asi que no esta
 * duplicada en dos sitios que se puedan desincronizar.
 */
public class ConfirmacionRequestDTO {

    /** El texto que ha tecleado la persona. */
    private String confirmacion;

    /** De que respaldo se restaura. Solo lo usa la restauracion. */
    private Long idRespaldo;

    /** Para que sirve este respaldo, en las palabras de quien lo toma. Opcional. */
    private String nota;

    /**
     * Tomar un respaldo antes de borrar. Por omision {@code true}: borrar sin
     * red no deberia ser lo comodo.
     */
    private boolean respaldarAntes = true;

    public ConfirmacionRequestDTO() {}

    public String getConfirmacion() { return confirmacion; }
    public void setConfirmacion(String confirmacion) { this.confirmacion = confirmacion; }

    public Long getIdRespaldo() { return idRespaldo; }
    public void setIdRespaldo(Long idRespaldo) { this.idRespaldo = idRespaldo; }

    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }

    public boolean isRespaldarAntes() { return respaldarAntes; }
    public void setRespaldarAntes(boolean respaldarAntes) { this.respaldarAntes = respaldarAntes; }
}
