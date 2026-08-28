package com.marathon.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.Bodega;

public interface BodegaRepository extends JpaRepository<Bodega, Integer> {

    Page<Bodega> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Page<Bodega> findByEstado(String estado, Pageable pageable);

    Page<Bodega> findByNombreContainingIgnoreCaseAndEstado(String nombre, String estado, Pageable pageable);

    /**
     * Bodegas activas para los desplegables, EN ORDEN ALFABETICO.
     *
     * <p>Sin el OrderBy, PostgreSQL las devolvia en orden de monton: bastaba
     * editar una bodega para que saltara al final de la lista del picking
     * (F51, D-41). Se ordena por nombre y no por id porque quien elige una
     * bodega en un desplegable de 20 la busca por su nombre.
     */
    List<Bodega> findByEstadoOrderByNombreAsc(String estado);
}
