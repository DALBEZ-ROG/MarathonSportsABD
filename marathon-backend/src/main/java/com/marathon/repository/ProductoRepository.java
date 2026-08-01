package com.marathon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    Page<Producto> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Page<Producto> findByEstado(String estado, Pageable pageable);

    Page<Producto> findByNombreContainingIgnoreCaseAndEstado(String nombre, String estado, Pageable pageable);

    Page<Producto> findByCategoriaIdCategoria(Integer idCategoria, Pageable pageable);

    Page<Producto> findByNombreContainingIgnoreCaseAndCategoriaIdCategoria(String nombre, Integer idCategoria, Pageable pageable);

    Page<Producto> findByEstadoAndCategoriaIdCategoria(String estado, Integer idCategoria, Pageable pageable);

    Page<Producto> findByNombreContainingIgnoreCaseAndEstadoAndCategoriaIdCategoria(String nombre, String estado, Integer idCategoria, Pageable pageable);

    // F27: filtro unificado que incluye origen (comprado/fabricado)
    // F32 — BUG CORREGIDO: sin los CAST explícitos, PostgreSQL no puede inferir el
    // tipo de los parámetros cuando llegan en NULL, los asume `bytea` y falla con
    // "no existe la función lower(bytea)" (error 500). Rompía el filtro por origen,
    // que usan el listado de productos y los dropdowns del módulo de Producción.
    @Query("SELECT p FROM Producto p WHERE "
        + "(CAST(:nombre AS string) IS NULL OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', CAST(:nombre AS string), '%'))) AND "
        + "(CAST(:estado AS string) IS NULL OR p.estado = CAST(:estado AS string)) AND "
        + "(:idCategoria IS NULL OR p.categoria.idCategoria = :idCategoria) AND "
        + "(CAST(:origen AS string) IS NULL OR p.origen = CAST(:origen AS string))")
    Page<Producto> buscarConFiltros(@Param("nombre") String nombre,
                                    @Param("estado") String estado,
                                    @Param("idCategoria") Integer idCategoria,
                                    @Param("origen") String origen,
                                    Pageable pageable);

    // F27: conteo de productos fabricados (para dashboard)
    long countByOrigen(String origen);

    // F29: listado paginado por origen (para análisis de costos)
    Page<Producto> findByOrigen(String origen, Pageable pageable);
}
