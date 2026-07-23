package com.marathon.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.ReembolsoCliente;

public interface ReembolsoClienteRepository extends JpaRepository<ReembolsoCliente, Integer> {

    Optional<ReembolsoCliente> findBySolicitudIdSolicitud(Integer idSolicitud);

    boolean existsBySolicitudIdSolicitud(Integer idSolicitud);
}
