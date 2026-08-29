package com.marathon.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.Permiso;

public interface PermisoRepository extends JpaRepository<Permiso, Integer> {

    Optional<Permiso> findByModuloAndAccion(String modulo, String accion);
}
