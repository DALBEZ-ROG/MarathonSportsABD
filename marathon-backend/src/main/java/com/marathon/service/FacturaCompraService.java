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

        // 4-bis. El importe de la factura SE CONTRASTA con lo recibido (D-36).
        //
        // La regla la decidio el dueno del proyecto el 2026-08-28, y es la
        // estricta: el subtotal NO puede superar el valor de la mercancia que
        // de verdad entro. Sin flete ni otros cargos por encima, y sin margen
        // de tolerancia.
        //
        // Hasta aqui el cotejo estaba cojo de un lado: la recepcion no deja
        // recibir mas de lo pedido y el pago no deja pagar mas del saldo, pero
        // el subtotal lo escribia quien registraba la factura y no se comparaba
        // con nada. Una factura de 50.000 sobre una orden de la que se
        // recibieron 500 se aceptaba, y generaba una cuenta por pagar de 50.000.
        //
        // La comparacion es EXACTA y no lleva tolerancia: precio_unitario tiene
        // dos decimales y cantidad_recibida es entera, asi que el producto y su
        // suma son exactos en BigDecimal. No hay error de redondeo que absorber.
        // Si algun dia negocio admite flete, el cambio es una constante aqui.
        //
        // OJO CON EL HISTORICO: 1.649 de las 2.287 facturas que ya estan en la
        // base incumplen esta regla (poblado masivo de la F38). NO se tocan
        // —regla de PENDIENTE.md §5: no se reparan datos historicos, porque no
        // se puede distinguir lo que escribio la aplicacion de lo que
        // escribieron los scripts de poblado—. Esta validacion mira solo hacia
        // adelante: rige para las facturas que se registren desde ahora.
        java.math.BigDecimal valorRecibido = ordenCompraDetalleRepository
                .findByOrdenCompraIdOrdenCompra(orden.getIdOrdenCompra()).stream()
                .map(d -> d.getPrecioUnitario().multiply(
                        java.math.BigDecimal.valueOf(
                                d.getCantidadRecibida() != null ? d.getCantidadRecibida() : 0)))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        if (dto.getSubtotal() != null && dto.getSubtotal().compareTo(valorRecibido) > 0) {
            java.math.BigDecimal exceso = dto.getSubtotal().subtract(valorRecibido);

            // Queda en la bitacora TAMBIEN cuando se rechaza: un proveedor que
            // insiste en facturar de mas es informacion, y un rechazo sin
            // rastro no la deja en ningun sitio.
            //
            // registrarAparte y no registrar, y es la diferencia entre que esto
            // funcione o no: crear() es @Transactional, asi que la excepcion de
            // dos lineas mas abajo desharia la transaccion entera —incluido el
            // apunte que se acaba de escribir—. Se comprobo: la primera version
            // rechazaba bien y dejaba log_accion vacia.
            logService.registrarAparte(idUsuarioActual, "compras", "factura_rechazada_descuadre",
                    "Factura '" + dto.getNumeroFacturaProveedor() + "' de la OC #"
                            + orden.getIdOrdenCompra() + ": subtotal " + dto.getSubtotal()
                            + " por encima del valor recibido " + valorRecibido
                            + " (exceso " + exceso + "). Rechazada.", null);

            throw new ValidationException(
                    "El subtotal de la factura (" + dto.getSubtotal() + ") supera el valor de la "
                    + "mercancía recibida (" + valorRecibido + ") en " + exceso + ". No se admiten "
                    + "cargos por encima de lo recibido: revisa la factura del proveedor, o "
                    + "registra primero la recepción que falte.");
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
