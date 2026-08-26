package com.marathon.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un dia de la serie del grafico (D1/D2).
 *
 * <p>La serie llega <b>completa</b>: un dia sin pedidos viaja con ceros en vez
 * de faltar. Un hueco en el eje se lee como «no se midio»; un cero, como «no
 * hubo pedidos». Aqui el dia existe y la cifra es cero de verdad, asi que se
 * dice cero.
 */
public record SerieDiaDTO(LocalDate dia, long pedidos, BigDecimal importe) {
}
