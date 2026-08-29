package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.Transportista;

public interface TransportistaRepository extends JpaRepository<Transportista, Integer> {

    /** Los que se pueden elegir hoy, por nombre. Es todo lo que necesita el empaque. */
    List<Transportista> findByEstadoOrderByNombreAsc(String estado);
}
