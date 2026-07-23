package com.marathon.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.cuentapagar.CuentaPorPagarResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.model.CuentaPorPagar;
import com.marathon.model.PagoProveedor;
import com.marathon.repository.CuentaPorPagarRepository;
import com.marathon.repository.PagoProveedorRepository;

@Service
public class CuentaPorPagarService {

    private final CuentaPorPagarRepository cuentaRepository;
    private final PagoProveedorRepository pagoRepository;

    public CuentaPorPagarService(CuentaPorPagarRepository cuentaRepository,
                                 PagoProveedorRepository pagoRepository) {
        this.cuentaRepository = cuentaRepository;
        this.pagoRepository = pagoRepository;
    }

    @Transactional
    public PageResponseDTO<CuentaPorPagarResponseDTO> listar(int page, int size, String estado, Integer idProveedor) {
        // Actualizar vencidas antes de listar
        cuentaRepository.actualizarVencidas(LocalDate.now());

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "idCuentaPagar"));
        Page<CuentaPorPagar> result;

        boolean hasEstado = estado != null && !estado.isEmpty();
        boolean hasProveedor = idProveedor != null;

        if (hasEstado && hasProveedor) {
            result = cuentaRepository.findByEstadoAndProveedorIdProveedor(estado, idProveedor, pageable);
        } else if (hasEstado) {
            result = cuentaRepository.findByEstado(estado, pageable);
        } else if (hasProveedor) {
            result = cuentaRepository.findByProveedorIdProveedor(idProveedor, pageable);
        } else {
            result = cuentaRepository.findAll(pageable);
        }

        List<CuentaPorPagarResponseDTO> content = result.getContent().stream()
                .map(c -> toDTO(c, false))
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public CuentaPorPagarResponseDTO obtener(Integer id) {
        CuentaPorPagar cuenta = cuentaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta por pagar", id));
        return toDTO(cuenta, true);
    }

    public ResumenProveedorDTO resumenPorProveedor(Integer idProveedor) {
        BigDecimal totalAdeudado = cuentaRepository.totalAdeudadoPorProveedor(idProveedor);
        List<CuentaPorPagar> cuentasActivas = cuentaRepository
                .findByProveedorIdProveedorAndEstadoIn(idProveedor, List.of("vigente", "vencida"));

        ResumenProveedorDTO resumen = new ResumenProveedorDTO();
        resumen.setIdProveedor(idProveedor);
        resumen.setTotalAdeudado(totalAdeudado);
        resumen.setCuentasVigentes((int) cuentasActivas.stream().filter(c -> "vigente".equals(c.getEstado())).count());
        resumen.setCuentasVencidas((int) cuentasActivas.stream().filter(c -> "vencida".equals(c.getEstado())).count());
        return resumen;
    }

    private CuentaPorPagarResponseDTO toDTO(CuentaPorPagar c, boolean incluirPagos) {
        CuentaPorPagarResponseDTO dto = new CuentaPorPagarResponseDTO();
        dto.setIdCuentaPagar(c.getIdCuentaPagar());
        dto.setMontoTotal(c.getMontoTotal());
        dto.setMontoPagado(c.getMontoPagado());
        dto.setSaldoPendiente(c.getSaldoPendiente());
        dto.setFechaVencimiento(c.getFechaVencimiento());
        dto.setEstado(c.getEstado());
        dto.setCreatedAt(c.getCreatedAt());

        if (c.getFacturaCompra() != null) {
            dto.setIdFacturaCompra(c.getFacturaCompra().getIdFacturaCompra());
            dto.setNumeroFacturaProveedor(c.getFacturaCompra().getNumeroFacturaProveedor());
        }
        if (c.getProveedor() != null) {
            dto.setIdProveedor(c.getProveedor().getIdProveedor());
            dto.setProveedorNombre(c.getProveedor().getNombre());
        }

        if (incluirPagos) {
            List<PagoProveedor> pagos = pagoRepository
                    .findByCuentaPorPagarIdCuentaPagarOrderByFechaPagoDesc(c.getIdCuentaPagar());
            dto.setPagos(pagos.stream().map(this::toPagoDTO).collect(Collectors.toList()));
        }

        return dto;
    }

    private CuentaPorPagarResponseDTO.PagoDTO toPagoDTO(PagoProveedor p) {
        CuentaPorPagarResponseDTO.PagoDTO dto = new CuentaPorPagarResponseDTO.PagoDTO();
        dto.setIdPago(p.getIdPago());
        dto.setMonto(p.getMonto());
        dto.setFechaPago(p.getFechaPago());
        dto.setMetodoPago(p.getMetodoPago());
        dto.setReferencia(p.getReferencia());
        dto.setObservaciones(p.getObservaciones());
        if (p.getUsuarioRegistro() != null) {
            dto.setUsuarioNombre(p.getUsuarioRegistro().getNombre() + " " + p.getUsuarioRegistro().getApellido());
        }
        return dto;
    }

    // DTO para resumen por proveedor
    public static class ResumenProveedorDTO {
        private Integer idProveedor;
        private BigDecimal totalAdeudado;
        private int cuentasVigentes;
        private int cuentasVencidas;

        public Integer getIdProveedor() { return idProveedor; }
        public void setIdProveedor(Integer idProveedor) { this.idProveedor = idProveedor; }

        public BigDecimal getTotalAdeudado() { return totalAdeudado; }
        public void setTotalAdeudado(BigDecimal totalAdeudado) { this.totalAdeudado = totalAdeudado; }

        public int getCuentasVigentes() { return cuentasVigentes; }
        public void setCuentasVigentes(int cuentasVigentes) { this.cuentasVigentes = cuentasVigentes; }

        public int getCuentasVencidas() { return cuentasVencidas; }
        public void setCuentasVencidas(int cuentasVencidas) { this.cuentasVencidas = cuentasVencidas; }
    }
}
