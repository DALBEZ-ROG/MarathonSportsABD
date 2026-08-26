package com.marathon.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.model.Categoria;

/**
 * Prueba de que el arnes puede escribir en la base de pruebas y que lo escrito
 * se revierte al terminar (L0 del plan).
 *
 * <p>Importa mas de lo que parece: casi todos los lotes siguientes necesitan
 * montar datos (un producto con stock, un pedido con lineas, una devolucion) y
 * dejar la base como estaba. Si el rollback de {@code @Transactional} no
 * funcionara, cada ejecucion de {@code mvn test} iria dejando basura y las
 * pruebas empezarian a interferir entre si.
 *
 * <p>Se usa {@code categoria} por ser la tabla mas simple del esquema: cuatro
 * columnas, sin claves foraneas de entrada y sin triggers.
 *
 * <p>Que la reversion ocurre de verdad no se puede afirmar desde dentro de la
 * misma transaccion. Se comprueba desde fuera, despues de {@code mvn test}:
 *
 * <pre>
 *   psql -d mod_venta_inve_test -c "select count(*) from categoria where nombre like '\_\_arnes\_\_%'"
 *   -- debe devolver 0
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoriaRepositoryTest {

    /** Prefijo reconocible, para poder buscar restos desde fuera del arnes. */
    private static final String NOMBRE_SONDA = "__arnes__categoria_temporal";

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Test
    @DisplayName("inserta una fila, la lee, y la transaccion se revierte al terminar")
    void insertaYLee() {
        long antes = categoriaRepository.count();

        Categoria nueva = new Categoria();
        nueva.setNombre(NOMBRE_SONDA);
        nueva.setDescripcion("fila temporal del arnes de pruebas; no deberia sobrevivir");

        Categoria guardada = categoriaRepository.save(nueva);

        Integer idAsignado = Objects.requireNonNull(guardada.getIdCategoria(),
                "la base debe asignar la clave primaria al guardar");

        assertThat(categoriaRepository.count())
                .as("la fila debe existir dentro de la transaccion")
                .isEqualTo(antes + 1);

        Categoria releida = categoriaRepository.findById(idAsignado)
                .orElseThrow(() -> new AssertionError("la fila recien guardada deberia poder releerse"));

        assertThat(releida.getNombre()).isEqualTo(NOMBRE_SONDA);
    }
}
