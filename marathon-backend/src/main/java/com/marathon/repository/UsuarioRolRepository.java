package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.UsuarioRol;

public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, Integer> {

    /** Administradores en estado activo (L14, D-31). */
    @org.springframework.data.jpa.repository.Query(
        "SELECT count(ur) FROM UsuarioRol ur WHERE ur.rol.nombre = 'Administrador' "
      + "AND ur.usuario.estado = 'activo'")
    long contarAdministradoresActivos();

    /** Para impedir borrar un rol que todavia tiene usuarios (L14, D-21). */
    boolean existsByRolIdRol(Integer idRol);

    List<UsuarioRol> findByUsuarioIdUsuario(Integer idUsuario);
}
