package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.SolicitudDevolucionDetalle;

public interface SolicitudDevolucionDetalleRepository extends JpaRepository<SolicitudDevolucionDetalle, Integer> {

    List<SolicitudDevolucionDetalle> findBySolicitudIdSolicitud(Integer idSolicitud);
}
