package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.RecepcionMercanciaDetalle;

public interface RecepcionMercanciaDetalleRepository extends JpaRepository<RecepcionMercanciaDetalle, Integer> {

    List<RecepcionMercanciaDetalle> findByRecepcionIdRecepcion(Integer idRecepcion);
}
