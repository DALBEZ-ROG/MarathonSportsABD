package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.marathon.model.UsuarioRol;

@Repository
public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, Integer> {

    List<UsuarioRol> findByUsuarioIdUsuario(Integer idUsuario);
}
