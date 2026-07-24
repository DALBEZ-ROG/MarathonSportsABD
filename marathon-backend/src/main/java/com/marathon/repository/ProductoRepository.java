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
    @Query("SELECT p FROM Producto p WHERE "
        + "(:nombre IS NULL OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :nombre, '%'))) AND "
        + "(:estado IS NULL OR p.estado = :estado) AND "
        + "(:idCategoria IS NULL OR p.categoria.idCategoria = :idCategoria) AND "
        + "(:origen IS NULL OR p.origen = :origen)")
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
