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

    /**
     * El número que hay dentro de lo escrito, o {@code null} si no hay ninguno (F94).
     *
     * <p><b>Por qué existe.</b> Los buscadores de pedido, orden de compra,
     * devolución y cuenta por pagar comparaban la clave numérica convertida a
     * texto:
     *
     * <pre>CAST(o.idOrdenCompra AS string) LIKE '%' || :texto || '%'</pre>
     *
     * <p>Eso <b>no puede usar ningún índice, nunca</b>: la base tiene que
     * convertir a texto la clave de cada una de las filas para poder comparar.
     * Medido en Órdenes de compra con 1,5 millones de filas: <b>15 segundos</b>
     * por búsqueda.
     *
     * <p>Comparando el número contra la columna numérica, la misma búsqueda usa
     * la clave primaria y es inmediata.
     *
     * <p><b>Lo que cambia para quien busca.</b> Antes «149» encontraba también
     * la 1490 y la 21497, porque era una búsqueda por subcadena sobre el número.
     * Ahora «149» encuentra la 149. Es lo que la gente quiere decir al escribir
     * un número de documento, y es la única forma de que sea rápido. El prefijo
     * se ignora, así que «OC-149», «PED-149» y «149» hacen lo mismo.
     */
    public static Long numeroDeDocumento(String busqueda) {
        String texto = vacioComoNulo(busqueda);
        if (texto == null) {
            return null;
        }
        // Se admite un prefijo de letras («OC-», «PED »), pero el RESTO tiene
        // que ser todo dígitos. Quedarse con los dígitos sueltos de cualquier
        // texto convertiría «Juan 2» en «el documento 2», que no es lo que esa
        // persona quiso decir.
        String limpio = texto.replaceFirst("^(?i)[a-záéíóúñ]{1,6}[-\\s]*", "").trim();
        if (!limpio.matches("\\d{1,18}")) {
            return null;
        }
        try {
            return Long.parseLong(limpio);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Lo escrito, salvo que sea un número de documento (F94).
     *
     * <p>Va en pareja con {@link #numeroDeDocumento}: uno de los dos sale nulo
     * SIEMPRE, y eso es lo que hace rápidas estas búsquedas.
     *
     * <p><b>Por qué importa que solo viva una.</b> La consulta pregunta por el
     * número O por el nombre. Un `OR` entre una condición indexable y otra que
     * no lo es obliga a la base a descartar el índice y recorrer la tabla
     * entera: con el número y el nombre vivos a la vez, buscar «PED-1499» en
     * 1,5 millones de pedidos tardaba <b>12,7 segundos</b> — encontraba el
     * pedido 1499 al momento y seguía recorriéndolo todo por si algún cliente se
     * llamaba así.
     *
     * <p>Separándolos, cada búsqueda usa su índice: la del número, la clave
     * primaria; la del nombre, el índice de trigramas.
     */
    public static String textoSiNoEsNumero(String busqueda) {
        return numeroDeDocumento(busqueda) != null ? null : vacioComoNulo(busqueda);
    }
}
