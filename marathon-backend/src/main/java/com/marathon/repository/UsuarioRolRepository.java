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

    /**
     * Solo los NOMBRES de los roles de una persona (F94).
     *
     * <p>Como {@link RolPermisoRepository#permisosDeUsuario}, esto lo paga cada
     * peticion. Traer las entidades {@code UsuarioRol} completas para leerles el
     * nombre del rol obliga a cargar tambien el {@code Rol}; una proyeccion de
     * cadenas resuelve lo mismo con una consulta y sin entidades en el contexto
     * de persistencia.
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT r.nombre FROM UsuarioRol ur JOIN ur.rol r WHERE ur.usuario.idUsuario = :idUsuario")
    List<String> nombresDeRol(
        @org.springframework.data.repository.query.Param("idUsuario") Integer idUsuario);
}
