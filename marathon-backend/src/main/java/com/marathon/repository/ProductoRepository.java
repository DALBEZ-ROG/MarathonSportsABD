package com.marathon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    Page<Producto> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Page<Producto> findByEstado(String estado, Pageable pageable);

    Page<Producto> findByNombreContainingIgnoreCaseAndEstado(String nombre, String estado, Pageable pageable);

    Page<Producto> findByCategoriaIdCategoria(Integer idCategoria, Pageable pageable);

    Page<Producto> findByNombreContainingIgnoreCaseAndCategoriaIdCategoria(String nombre, Integer idCategoria, Pageable pageable);

    Page<Producto> findByEstadoAndCategoriaIdCategoria(String estado, Integer idCategoria, Pageable pageable);

    Page<Producto> findByNombreContainingIgnoreCaseAndEstadoAndCategoriaIdCategoria(String nombre, String estado, Integer idCategoria, Pageable pageable);
}
