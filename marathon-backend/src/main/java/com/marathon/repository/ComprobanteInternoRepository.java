package com.marathon.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.ComprobanteInterno;

public interface ComprobanteInternoRepository extends JpaRepository<ComprobanteInterno, Integer> {

    Optional<ComprobanteInterno> findByPedidoIdPedido(Integer idPedido);

    Optional<ComprobanteInterno> findByNumeroComprobante(String numeroComprobante);

    Page<ComprobanteInterno> findByNumeroComprobanteContainingIgnoreCase(String numero, Pageable pageable);
}
