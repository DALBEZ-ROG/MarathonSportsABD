package com.marathon.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.Proveedor;

public interface ProveedorRepository extends JpaRepository<Proveedor, Integer> {

    Page<Proveedor> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Page<Proveedor> findByEstado(String estado, Pageable pageable);

    Page<Proveedor> findByNombreContainingIgnoreCaseAndEstado(String nombre, String estado, Pageable pageable);

    Optional<Proveedor> findByNombreIgnoreCase(String nombre);
}
