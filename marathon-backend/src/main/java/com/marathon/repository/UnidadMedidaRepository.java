package com.marathon.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.marathon.model.UnidadMedida;

@Repository
public interface UnidadMedidaRepository extends JpaRepository<UnidadMedida, Integer> {

    Page<UnidadMedida> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Optional<UnidadMedida> findByNombreIgnoreCase(String nombre);

    Optional<UnidadMedida> findByAbreviaturaIgnoreCase(String abreviatura);
}
