package com.marathon.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.MateriaPrima;

public interface MateriaPrimaRepository extends JpaRepository<MateriaPrima, Integer> {

    /** Para impedir el borrado fisico de una unidad de medida en uso (L9, D-20). */
    boolean existsByUnidadMedidaIdUnidadMedida(Integer idUnidadMedida);

    Page<MateriaPrima> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Page<MateriaPrima> findByEstado(String estado, Pageable pageable);

    Page<MateriaPrima> findByNombreContainingIgnoreCaseAndEstado(String nombre, String estado, Pageable pageable);

    Optional<MateriaPrima> findByNombreIgnoreCase(String nombre);

    /**
     * Las materias primas bajo mínimos, filtradas POR LA BASE (F94).
     *
     * <p>Antes esto se resolvía con {@code findAll(Sort...)} y un
     * {@code .filter()} de Java. Con las 300 filas de entonces daba igual; con
     * el 1,5 millones de la F91 significa traerse <b>la tabla entera a memoria
     * del servidor</b> —287 MB— para quedarse con unas pocas miles de filas.
     * Medido: <b>10 segundos</b> por carga de la pantalla.
     *
     * <p>La condición compara dos columnas de la misma fila
     * ({@code stock_actual <= stock_minimo}), que ningún índice normal puede
     * resolver. Lo resuelve el índice PARCIAL {@code idx_materia_prima_stock_bajo}
     * de la F94: lleva la condición dentro, así que contiene exactamente las
     * filas bajo mínimos y se lee entero.
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT mp FROM MateriaPrima mp "
      + "WHERE mp.stockMinimo IS NOT NULL AND mp.stockMinimo > 0 "
      + "  AND mp.stockActual <= mp.stockMinimo "
      + "ORDER BY mp.nombre ASC")
    List<MateriaPrima> findBajoMinimo(Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
        "SELECT count(mp) FROM MateriaPrima mp "
      + "WHERE mp.stockMinimo IS NOT NULL AND mp.stockMinimo > 0 "
      + "  AND mp.stockActual <= mp.stockMinimo")
    long contarBajoMinimo();
}
