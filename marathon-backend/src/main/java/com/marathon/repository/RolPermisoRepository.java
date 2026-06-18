package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.marathon.model.RolPermiso;

@Repository
public interface RolPermisoRepository extends JpaRepository<RolPermiso, Integer> {

    List<RolPermiso> findByRolIdRol(Integer idRol);
}
