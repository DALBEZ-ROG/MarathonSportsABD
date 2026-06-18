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

    List<Bodega> findByEstado(String estado);
}
