package com.marathon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.ComprobanteInterno;

public interface ComprobanteInternoRepository extends JpaRepository<ComprobanteInterno, Integer> {

    Page<ComprobanteInterno> findByEstado(String estado, Pageable pageable);

    Page<ComprobanteInterno> findByTipo(String tipo, Pageable pageable);

    Page<ComprobanteInterno> findByTipoAndEstado(String tipo, String estado, Pageable pageable);
}
