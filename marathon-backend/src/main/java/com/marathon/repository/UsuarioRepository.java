package com.marathon.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    Optional<Usuario> findByCorreoAndEstado(String correo, String estado);

    Page<Usuario> findByEstado(String estado, Pageable pageable);

    @Query("SELECT u FROM Usuario u WHERE (LOWER(u.nombre) LIKE LOWER(CONCAT('%',:nombre,'%')) OR LOWER(u.apellido) LIKE LOWER(CONCAT('%',:nombre,'%')))")
    Page<Usuario> findByNombreOrApellido(@Param("nombre") String nombre, Pageable pageable);

    @Query("SELECT u FROM Usuario u WHERE (LOWER(u.nombre) LIKE LOWER(CONCAT('%',:nombre,'%')) OR LOWER(u.apellido) LIKE LOWER(CONCAT('%',:nombre,'%'))) AND u.estado = :estado")
    Page<Usuario> findByNombreOrApellidoAndEstado(@Param("nombre") String nombre, @Param("estado") String estado, Pageable pageable);
}
