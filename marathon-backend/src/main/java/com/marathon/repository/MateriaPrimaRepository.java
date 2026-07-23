package com.marathon.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.MateriaPrima;

public interface MateriaPrimaRepository extends JpaRepository<MateriaPrima, Integer> {

    Page<MateriaPrima> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Page<MateriaPrima> findByEstado(String estado, Pageable pageable);

    Page<MateriaPrima> findByNombreContainingIgnoreCaseAndEstado(String nombre, String estado, Pageable pageable);

    Optional<MateriaPrima> findByNombreIgnoreCase(String nombre);
}
