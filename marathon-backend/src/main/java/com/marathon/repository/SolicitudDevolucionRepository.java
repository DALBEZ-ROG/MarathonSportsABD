package com.marathon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.SolicitudDevolucion;

public interface SolicitudDevolucionRepository extends JpaRepository<SolicitudDevolucion, Integer> {

    Page<SolicitudDevolucion> findByEstado(String estado, Pageable pageable);

    Page<SolicitudDevolucion> findByPedidoIdPedido(Integer idPedido, Pageable pageable);

    Page<SolicitudDevolucion> findByEstadoAndPedidoIdPedido(String estado, Integer idPedido, Pageable pageable);

    long countByEstado(String estado);
}
