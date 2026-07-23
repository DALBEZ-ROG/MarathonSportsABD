package com.marathon.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.pago.PagoProveedorRequestDTO;
import com.marathon.dto.pago.PagoProveedorResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.CuentaPorPagar;
import com.marathon.model.PagoProveedor;
import com.marathon.model.Usuario;
import com.marathon.repository.CuentaPorPagarRepository;
import com.marathon.repository.PagoProveedorRepository;
import com.marathon.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class PagoProveedorService {

    private final PagoProveedorRepository pagoRepository;
    private final CuentaPorPagarRepository cuentaRepository;
    private final UsuarioRepository usuarioRepository;
    private final LogService logService;

    @PersistenceContext
    private EntityManager entityManager;

    public PagoProveedorService(PagoProveedorRepository pagoRepository,
                                CuentaPorPagarRepository cuentaRepository,
                                UsuarioRepository usuarioRepository,
                                LogService logService) {
        this.pagoRepository = pagoRepository;
        this.cuentaRepository = cuentaRepository;
        this.usuarioRepository = usuarioRepository;
        this.logService = logService;
    }

    @Transactional
    public PagoProveedorResponseDTO registrarPago(PagoProveedorRequestDTO dto, Integer idUsuarioActual) {
        // 1. Validar cuenta
        CuentaPorPagar cuenta = cuentaRepository.findById(dto.getIdCuentaPagar())
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta por pagar", dto.getIdCuentaPagar()));

        if ("pagada".equals(cuenta.getEstado())) {
            throw new ValidationException("La cuenta ya está completamente pagada");
        }

        // 2. Validar que el monto no exceda el saldo pendiente
        BigDecimal saldoActual = cuenta.getSaldoPendiente();
        if (saldoActual == null) {
            saldoActual = cuenta.getMontoTotal().subtract(
                    cuenta.getMontoPagado() != null ? cuenta.getMontoPagado() : BigDecimal.ZERO);
        }
        if (dto.getMonto().compareTo(saldoActual) > 0) {
            throw new ValidationException("El monto excede el saldo pendiente (saldo: $" + saldoActual + ")");
        }

        Usuario usuario = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        // 3. Insertar pago
        PagoProveedor pago = new PagoProveedor();
        pago.setCuentaPorPagar(cuenta);
        pago.setUsuarioRegistro(usuario);
        pago.setMonto(dto.getMonto());
        pago.setMetodoPago(dto.getMetodoPago());
        pago.setReferencia(dto.getReferencia());
        pago.setObservaciones(dto.getObservaciones());
        pago = pagoRepository.save(pago);

        // 4. Flush para que el trigger recalcule monto_pagado y estado
        entityManager.flush();
        entityManager.refresh(cuenta);

        logService.registrar(idUsuarioActual, "compras", "pago_registrar",
                "Pago de $" + dto.getMonto() + " registrado en CxP #" + cuenta.getIdCuentaPagar()
                        + ". Saldo restante: $" + cuenta.getSaldoPendiente(), null);

        return toDTO(pago, cuenta.getSaldoPendiente());
    }

    public List<PagoProveedorResponseDTO> listarPorCuenta(Integer idCuentaPagar) {
        return pagoRepository.findByCuentaPorPagarIdCuentaPagarOrderByFechaPagoDesc(idCuentaPagar).stream()
                .map(p -> toDTO(p, null))
                .collect(Collectors.toList());
    }

    private PagoProveedorResponseDTO toDTO(PagoProveedor p, BigDecimal saldoResultante) {
        PagoProveedorResponseDTO dto = new PagoProveedorResponseDTO();
        dto.setIdPago(p.getIdPago());
        dto.setIdCuentaPagar(p.getCuentaPorPagar().getIdCuentaPagar());
        dto.setMonto(p.getMonto());
        dto.setFechaPago(p.getFechaPago());
        dto.setMetodoPago(p.getMetodoPago());
        dto.setReferencia(p.getReferencia());
        dto.setObservaciones(p.getObservaciones());
        dto.setSaldoResultante(saldoResultante);
        if (p.getUsuarioRegistro() != null) {
            dto.setUsuarioRegistroNombre(p.getUsuarioRegistro().getNombre() + " " + p.getUsuarioRegistro().getApellido());
        }
        return dto;
    }
}
