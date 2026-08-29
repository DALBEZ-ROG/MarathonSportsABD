package com.marathon.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.model.TokenRevocado;

public interface TokenRevocadoRepository extends JpaRepository<TokenRevocado, String> {

    /**
     * Borra las filas de tokens que ya habrían caducado por sí mismos.
     *
     * <p>Es seguro y por eso los seis roles tienen DELETE sobre la tabla: quitar
     * de la lista de denegación un token <b>ya expirado</b> no le devuelve la
     * vida a nada, porque la comprobación de expiración es anterior e
     * independiente de esta lista. Sin esto la tabla crecería una fila por cada
     * cierre de sesión, para siempre.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM TokenRevocado t WHERE t.fechaExpiracion < :ahora")
    int purgarExpirados(@Param("ahora") LocalDateTime ahora);
}
