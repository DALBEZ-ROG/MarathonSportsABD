package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.RecepcionMercancia;

public interface RecepcionMercanciaRepository extends JpaRepository<RecepcionMercancia, Integer> {

    List<RecepcionMercancia> findByOrdenCompraIdOrdenCompraOrderByFechaRecepcionDesc(Integer idOrdenCompra);
}
