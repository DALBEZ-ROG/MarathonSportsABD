package com.marathon.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    Optional<Cliente> findByCedulaIgnoreCase(String cedula);

    Page<Cliente> findByEstado(String estado, Pageable pageable);

    @Query("SELECT c FROM Cliente c WHERE (LOWER(c.nombre) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(c.apellido) LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<Cliente> findByNombreOrApellido(@Param("search") String search, Pageable pageable);

    @Query("SELECT c FROM Cliente c WHERE (LOWER(c.nombre) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(c.apellido) LIKE LOWER(CONCAT('%',:search,'%'))) AND c.estado = :estado")
    Page<Cliente> findByNombreOrApellidoAndEstado(@Param("search") String search, @Param("estado") String estado, Pageable pageable);

    List<Cliente> findByEstadoOrderByApellidoAsc(String estado);
}
