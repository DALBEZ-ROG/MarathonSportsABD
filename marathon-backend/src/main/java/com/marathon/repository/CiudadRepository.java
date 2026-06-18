package com.marathon.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.marathon.model.Ciudad;

@Repository
public interface CiudadRepository extends JpaRepository<Ciudad, Integer> {

    Page<Ciudad> findByEstado(String estado, Pageable pageable);

    Page<Ciudad> findByNombreContainingIgnoreCaseAndEstado(String nombre, String estado, Pageable pageable);

    Page<Ciudad> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Optional<Ciudad> findByNombreIgnoreCase(String nombre);
}
