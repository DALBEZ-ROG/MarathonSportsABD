package com.marathon.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {

    Page<Categoria> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Optional<Categoria> findByNombreIgnoreCase(String nombre);
}
