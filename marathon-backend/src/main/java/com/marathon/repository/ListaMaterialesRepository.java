package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.ListaMateriales;

public interface ListaMaterialesRepository extends JpaRepository<ListaMateriales, Integer> {

    List<ListaMateriales> findByProductoIdProductoAndEstado(Integer idProducto, String estado);

    boolean existsByProductoIdProductoAndEstado(Integer idProducto, String estado);
}
