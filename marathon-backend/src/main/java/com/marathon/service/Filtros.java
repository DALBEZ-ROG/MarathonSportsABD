package com.marathon.service;

/**
 * Preparacion de los filtros que llegan de las pantallas (F54).
 *
 * <p>Las consultas de listado se anulan con {@code :x IS NULL}: cada filtro
 * desaparece cuando su parametro llega a null. Eso permite tener UNA consulta
 * por listado en vez de una rama por combinacion de filtros — que es lo que
 * habia, y por eso ninguna admitia buscar por texto.
 *
 * <p>El detalle que hace falta acertar es que un formulario no manda null
 * cuando el usuario borra un campo: manda la cadena vacia. Sin normalizarla,
 * la consulta filtraria por "" y no devolveria nada.
 */
public final class Filtros {

    private Filtros() {}

    /** Un filtro vacio o en blanco es «sin filtro», no «igual a la cadena vacia». */
    public static String vacioComoNulo(String valor) {
        return (valor == null || valor.trim().isEmpty()) ? null : valor.trim();
    }

    /**
     * Prepara lo que el usuario escribio para buscar un pedido.
     *
     * <p>La pantalla enseña «PED-230005» pero en la base el pedido es el 230005:
     * el numero con formato lo compone el DTO, no existe como columna. Si se
     * buscara tal cual lo copiado de la pantalla no se encontraria nada, que es
     * la forma mas segura de que alguien concluya que su pedido se perdio.
     *
     * <p>Se quita el prefijo y los ceros de relleno; lo que quede se busca
     * tambien contra el nombre del cliente. Asi «230005», «PED-230005» y
     * «Doris» funcionan los tres.
     */
    public static String numeroDePedido(String busqueda) {
        String texto = vacioComoNulo(busqueda);
        if (texto == null) {
            return null;
        }
        String limpio = texto.replaceFirst("(?i)^ped[-\\s]*", "");
        if (limpio.matches("0*\\d+")) {
            return limpio.replaceFirst("^0+(?=\\d)", "");
        }
        return texto;
    }
}
