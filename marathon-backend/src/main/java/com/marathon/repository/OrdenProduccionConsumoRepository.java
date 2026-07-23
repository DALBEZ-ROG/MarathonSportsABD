package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.OrdenProduccionConsumo;

public interface OrdenProduccionConsumoRepository extends JpaRepository<OrdenProduccionConsumo, Integer> {

    List<OrdenProduccionConsumo> findByOrdenProduccionIdOrdenProduccion(Integer idOrdenProduccion);
}
