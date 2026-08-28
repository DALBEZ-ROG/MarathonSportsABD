package com.marathon.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.facturacompra.FacturaCompraRequestDTO;
import com.marathon.dto.facturacompra.FacturaCompraResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.CuentaPorPagar;
import com.marathon.model.FacturaCompra;
import com.marathon.model.OrdenCompra;
import com.marathon.model.Usuario;
import com.marathon.repository.CuentaPorPagarRepository;
import com.marathon.repository.FacturaCompraRepository;
import com.marathon.repository.OrdenCompraDetalleRepository;
import com.marathon.repository.OrdenCompraRepository;
import com.marathon.repository.RecepcionMercanciaRepository;
import com.marathon.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class FacturaCompraService {

    private final FacturaCompraRepository facturaRepository;
    private final CuentaPorPagarRepository cuentaRepository;
    private final OrdenCompraRepository ordenCompraRepository;
    private final RecepcionMercanciaRepository recepcionRepository;
    private final OrdenCompraDetalleRepository ordenCompraDetalleRepository;
    private final UsuarioRepository usuarioRepository;
    private final LogService logService;

    @PersistenceContext
    private EntityManager entityManager;

    public FacturaCompraService(FacturaCompraRepository facturaRepository,
                                CuentaPorPagarRepository cuentaRepository,
                                OrdenCompraRepository ordenCompraRepository,
                                RecepcionMercanciaRepository recepcionRepository,
                                OrdenCompraDetalleRepository ordenCompraDetalleRepository,
                                UsuarioRepository usuarioRepository,
                                LogService logService) {
        this.facturaRepository = facturaRepository;
        this.cuentaRepository = cuentaRepository;
        this.ordenCompraRepository = ordenCompraRepository;
        this.recepcionRepository = recepcionRepository;
        this.ordenCompraDetalleRepository = ordenCompraDetalleRepository;
        this.usuarioRepository = usuarioRepository;
        this.logService = logService;
    }

    @Transactional
    public FacturaCompraResponseDTO crear(FacturaCompraRequestDTO dto, Integer idUsuarioActual) {
        // 1. Validar orden de compra
        OrdenCompra orden = ordenCompraRepository.findById(dto.getIdOrdenCompra())
                .orElseThrow(() -> new ResourceNotFoundException("Orden de compra", dto.getIdOrdenCompra()));

        // 2. Validar que la orden tenga al menos una recepción
        List<?> recepciones = recepcionRepository
                .findByOrdenCompraIdOrdenCompraOrderByFechaRecepcionDesc(orden.getIdOrdenCompra());
        if (recepciones.isEmpty()) {
            throw new ValidationException("No se puede registrar factura: la orden de compra no tiene recepciones registradas");
        }

        // 3. Validar UNIQUE antes de insertar (mensaje claro)
        if (facturaRepository.existsByOrdenCompraIdOrdenCompraAndNumeroFacturaProveedor(
                dto.getIdOrdenCompra(), dto.getNumeroFacturaProveedor())) {
            throw new ValidationException("Ya existe una factura con el número '"
                    + dto.getNumeroFacturaProveedor() + "' para esta orden de compra");
        }

        // 4. Validar fechas
        if (dto.getFechaVencimiento().isBefore(dto.getFechaFactura())) {
            throw new ValidationException("La fecha de vencimiento no puede ser anterior a la fecha de factura");
        }

        Usuario usuario = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        // 4-bis. El importe de la factura NO se contrasta con lo recibido.
        //
        // Se comprueba que la orden exista, que tenga recepciones, que el numero
        // no se repita y que las fechas sean coherentes — pero el subtotal lo
        // pone quien registra la factura y nadie lo compara con la mercancia que
        // de verdad entro. Una factura de 50.000 sobre una orden de la que se
        // recibieron 500 se acepta, y genera una cuenta por pagar de 50.000.
        //
        // No se BLOQUEA aqui, y es deliberado: decidir si el subtotal puede
        // llevar flete u otros cargos por encima de lo recibido, y con que
        // tolerancia, es una decision de negocio, no de codigo — la misma clase
        // de decision que PENDIENTE.md exigia tomar antes de tocar D-02. Ademas,
        // 1.649 de las 2.287 facturas que hay en la base estan por encima de lo
        // recibido (casi todas del poblado masivo de la F38), asi que un bloqueo
        // duro tampoco se puede contrastar contra el historico.
        //
        // Lo que si se hace es dejar de callarlo: el descuadre queda en la
        // bitacora, con las dos cifras, para que se pueda medir cuanto ocurre de
        // verdad ANTES de decidir la regla. Ver docs/PENDIENTE.md, D-36.
        java.math.BigDecimal valorRecibido = ordenCompraDetalleRepository
                .findByOrdenCompraIdOrdenCompra(orden.getIdOrdenCompra()).stream()
                .map(d -> d.getPrecioUnitario().multiply(
                        java.math.BigDecimal.valueOf(
                                d.getCantidadRecibida() != null ? d.getCantidadRecibida() : 0)))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        if (dto.getSubtotal() != null && dto.getSubtotal().compareTo(valorRecibido) > 0) {
            logService.registrar(idUsuarioActual, "compras", "factura_descuadre",
                    "Factura '" + dto.getNumeroFacturaProveedor() + "' de la OC #"
                            + orden.getIdOrdenCompra() + ": subtotal " + dto.getSubtotal()
                            + " por encima del valor recibido " + valorRecibido
                            + " (diferencia " + dto.getSubtotal().subtract(valorRecibido) + ")", null);
        }

        // 5. Insertar factura (total es GENERATED — no se asigna)
        FacturaCompra factura = new FacturaCompra();
        factura.setOrdenCompra(orden);
        factura.setUsuarioRegistro(usuario);
        factura.setNumeroFacturaProveedor(dto.getNumeroFacturaProveedor());
        factura.setFechaFactura(dto.getFechaFactura());
        factura.setFechaVencimiento(dto.getFechaVencimiento());
        factura.setSubtotal(dto.getSubtotal());
        factura.setImpuesto(dto.getImpuesto() != null ? dto.getImpuesto() : java.math.BigDecimal.ZERO);
        factura.setEstado("pendiente");
        factura = facturaRepository.save(factura);

        // 6. Recargar para obtener el total calculado por BD
        entityManager.flush();
        entityManager.refresh(factura);

        // 7. Crear cuenta por pagar
        CuentaPorPagar cuenta = new CuentaPorPagar();
        cuenta.setFacturaCompra(factura);
        cuenta.setProveedor(orden.getProveedor());
        cuenta.setMontoTotal(factura.getTotal());
        cuenta.setFechaVencimiento(dto.getFechaVencimiento());
        cuenta.setEstado("vigente");
        cuenta = cuentaRepository.save(cuenta);

        entityManager.flush();
        entityManager.refresh(cuenta);

        logService.registrar(idUsuarioActual, "compras", "factura_crear",
                "Factura '" + dto.getNumeroFacturaProveedor() + "' registrada para OC #"
                        + orden.getIdOrdenCompra() + ". CxP generada: $" + factura.getTotal(), null);

        return toDTO(factura, cuenta);
    }

    public PageResponseDTO<FacturaCompraResponseDTO> listar(int page, int size, String estado, Integer idProveedor) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "idFacturaCompra"));
        Page<FacturaCompra> result;

        boolean hasEstado = estado != null && !estado.isEmpty();
        boolean hasProveedor = idProveedor != null;

        if (hasEstado && hasProveedor) {
            result = facturaRepository.findByEstadoAndOrdenCompraProveedorIdProveedor(estado, idProveedor, pageable);
        } else if (hasEstado) {
            result = facturaRepository.findByEstado(estado, pageable);
        } else if (hasProveedor) {
            result = facturaRepository.findByOrdenCompraProveedorIdProveedor(idProveedor, pageable);
        } else {
            result = facturaRepository.findAll(pageable);
        }

        List<FacturaCompraResponseDTO> content = result.getContent().stream()
                .map(f -> {
                    CuentaPorPagar cxp = cuentaRepository.findByFacturaCompraIdFacturaCompra(f.getIdFacturaCompra())
                            .orElse(null);
                    return toDTO(f, cxp);
                })
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public FacturaCompraResponseDTO obtener(Integer id) {
        FacturaCompra factura = facturaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Factura de compra", id));
        CuentaPorPagar cxp = cuentaRepository.findByFacturaCompraIdFacturaCompra(id).orElse(null);
        return toDTO(factura, cxp);
    }

    @Transactional
    public FacturaCompraResponseDTO anular(Integer id, Integer idUsuarioActual) {
        FacturaCompra factura = facturaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Factura de compra", id));

        if ("anulada".equals(factura.getEstado())) {
            throw new ValidationException("La factura ya está anulada");
        }

        CuentaPorPagar cuenta = cuentaRepository.findByFacturaCompraIdFacturaCompra(id).orElse(null);
        if (cuenta != null && cuenta.getMontoPagado() != null
                && cuenta.getMontoPagado().compareTo(java.math.BigDecimal.ZERO) > 0) {
            throw new ValidationException("No se puede anular una factura con pagos registrados");
        }

        factura.setEstado("anulada");
        facturaRepository.save(factura);

        if (cuenta != null) {
            cuenta.setEstado("pagada"); // Marcamos como cerrada (no hay deuda)
            cuentaRepository.save(cuenta);
        }

        logService.registrar(idUsuarioActual, "compras", "factura_anular",
                "Factura #" + id + " anulada", null);

        return toDTO(factura, cuenta);
    }

    private FacturaCompraResponseDTO toDTO(FacturaCompra f, CuentaPorPagar cxp) {
        FacturaCompraResponseDTO dto = new FacturaCompraResponseDTO();
        dto.setIdFacturaCompra(f.getIdFacturaCompra());
        dto.setNumeroFacturaProveedor(f.getNumeroFacturaProveedor());
        dto.setFechaFactura(f.getFechaFactura());
        dto.setFechaVencimiento(f.getFechaVencimiento());
        dto.setSubtotal(f.getSubtotal());
        dto.setImpuesto(f.getImpuesto());
        dto.setTotal(f.getTotal());
        dto.setEstado(f.getEstado());
        dto.setCreatedAt(f.getCreatedAt());

        if (f.getOrdenCompra() != null) {
            dto.setIdOrdenCompra(f.getOrdenCompra().getIdOrdenCompra());
            if (f.getOrdenCompra().getProveedor() != null) {
                dto.setProveedorNombre(f.getOrdenCompra().getProveedor().getNombre());
            }
        }
        if (f.getUsuarioRegistro() != null) {
            dto.setUsuarioRegistroNombre(f.getUsuarioRegistro().getNombre() + " " + f.getUsuarioRegistro().getApellido());
        }

        if (cxp != null) {
            FacturaCompraResponseDTO.CuentaPorPagarAnidadaDTO cxpDto = new FacturaCompraResponseDTO.CuentaPorPagarAnidadaDTO();
            cxpDto.setIdCuentaPagar(cxp.getIdCuentaPagar());
            cxpDto.setMontoTotal(cxp.getMontoTotal());
            cxpDto.setMontoPagado(cxp.getMontoPagado());
            cxpDto.setSaldoPendiente(cxp.getSaldoPendiente());
            cxpDto.setFechaVencimiento(cxp.getFechaVencimiento());
            cxpDto.setEstado(cxp.getEstado());
            dto.setCuentaPorPagar(cxpDto);
        }

        return dto;
    }
}
