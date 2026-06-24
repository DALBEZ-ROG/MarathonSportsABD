package com.marathon.service;

import java.time.Year;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.comprobante.ComprobanteResponseDTO;
import com.marathon.dto.pedido.DetallePedidoResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.Cliente;
import com.marathon.model.ComprobanteInterno;
import com.marathon.model.DetallePedido;
import com.marathon.model.Pedido;
import com.marathon.model.Usuario;
import com.marathon.repository.ComprobanteInternoRepository;
import com.marathon.repository.DetallePedidoRepository;
import com.marathon.repository.PedidoRepository;
import com.marathon.repository.UsuarioRepository;

@Service
public class ComprobanteService {

    private final ComprobanteInternoRepository comprobanteRepository;
    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PdfService pdfService;
    private final LogService logService;

    public ComprobanteService(ComprobanteInternoRepository comprobanteRepository,
                              PedidoRepository pedidoRepository,
                              DetallePedidoRepository detallePedidoRepository,
                              UsuarioRepository usuarioRepository,
                              PdfService pdfService,
                              LogService logService) {
        this.comprobanteRepository = comprobanteRepository;
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.usuarioRepository = usuarioRepository;
        this.pdfService = pdfService;
        this.logService = logService;
    }

    @Transactional
    public ComprobanteResponseDTO generarComprobante(Integer idPedido, Integer idUsuarioActual) {
        Pedido pedido = pedidoRepository.findById(idPedido)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", idPedido));

        if (comprobanteRepository.findByPedidoIdPedido(idPedido).isPresent()) {
            throw new ValidationException("El pedido ya tiene un comprobante emitido");
        }

        Usuario usuario = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        ComprobanteInterno comprobante = new ComprobanteInterno();
        comprobante.setPedido(pedido);
        comprobante.setUsuario(usuario);
        comprobante.setNumeroComprobante(generarNumero());
        // CRÍTICO: el total debe ser exactamente pedido.total (trigger valida)
        comprobante.setTotal(pedido.getTotal());
        comprobante.setEstado("emitido");

        comprobante = comprobanteRepository.save(comprobante);
        logService.registrar(idUsuarioActual, "comprobantes", "generar",
                "Comprobante " + comprobante.getNumeroComprobante() + " generado para pedido #" + idPedido, null);
        return toDTO(comprobante);
    }

    public ComprobanteResponseDTO obtener(Integer id) {
        ComprobanteInterno c = comprobanteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comprobante", id));
        return toDTO(c);
    }

    public ComprobanteResponseDTO obtenerPorPedido(Integer idPedido) {
        ComprobanteInterno c = comprobanteRepository.findByPedidoIdPedido(idPedido)
                .orElseThrow(() -> new ResourceNotFoundException("Comprobante para el pedido", idPedido));
        return toDTO(c);
    }

    public PageResponseDTO<ComprobanteResponseDTO> listar(int page, int size, String numero) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ComprobanteInterno> result;
        if (numero != null && !numero.isEmpty()) {
            result = comprobanteRepository.findByNumeroComprobanteContainingIgnoreCase(numero, pageable);
        } else {
            result = comprobanteRepository.findAll(pageable);
        }
        List<ComprobanteResponseDTO> content = result.getContent().stream()
                .map(this::toDTO).collect(Collectors.toList());
        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    @Transactional
    public ComprobanteResponseDTO anular(Integer idComprobante, Integer idUsuarioActual) {
        ComprobanteInterno c = comprobanteRepository.findById(idComprobante)
                .orElseThrow(() -> new ResourceNotFoundException("Comprobante", idComprobante));
        c.setEstado("anulado");
        comprobanteRepository.save(c);
        logService.registrar(idUsuarioActual, "comprobantes", "anular",
                "Comprobante " + c.getNumeroComprobante() + " anulado", null);
        return toDTO(c);
    }

    public byte[] descargarPDF(Integer idComprobante) {
        ComprobanteResponseDTO dto = obtener(idComprobante);
        return pdfService.generarComprobanteInternoPDF(dto);
    }

    private String generarNumero() {
        int anio = Year.now().getValue();
        long count = comprobanteRepository.count() + 1;
        return String.format("COMP-%d-%06d", anio, count);
    }

    private ComprobanteResponseDTO toDTO(ComprobanteInterno c) {
        ComprobanteResponseDTO dto = new ComprobanteResponseDTO();
        dto.setIdComprobante(c.getIdComprobante());
        dto.setNumeroComprobante(c.getNumeroComprobante());
        dto.setFechaEmision(c.getFechaEmision());
        dto.setTotal(c.getTotal());
        dto.setEstado(c.getEstado());
        dto.setCreatedAt(c.getCreatedAt());

        Pedido pedido = c.getPedido();
        if (pedido != null) {
            dto.setIdPedido(pedido.getIdPedido());
            dto.setFechaPedido(pedido.getFechaPedido());
            dto.setDescuento(pedido.getDescuento());
            dto.setEstadoPedido(pedido.getEstado());
            dto.setEsPedidoEspecial(pedido.getEsPedidoEspecial());
            dto.setTipoEspecial(pedido.getTipoEspecial());
            dto.setNotaEspecial(pedido.getNotaEspecial());
            dto.setFechaLimiteEntrega(pedido.getFechaLimiteEntrega());

            Cliente cliente = pedido.getCliente();
            if (cliente != null) {
                dto.setClienteNombre(cliente.getNombre());
                dto.setClienteApellido(cliente.getApellido());
                dto.setClienteCorreo(cliente.getCorreo());
                if (cliente.getCiudad() != null) {
                    dto.setClienteCiudad(cliente.getCiudad().getNombre());
                }
            }

            // Detalles del pedido
            List<DetallePedido> detalles = detallePedidoRepository.findByPedidoIdPedido(pedido.getIdPedido());
            List<DetallePedidoResponseDTO> detallesDTO = detalles.stream().map(d -> {
                DetallePedidoResponseDTO dd = new DetallePedidoResponseDTO();
                dd.setIdDetalle(d.getIdDetalle());
                dd.setCantidad(d.getCantidad());
                dd.setPrecioUnitario(d.getPrecioUnitario());
                dd.setSubtotal(d.getSubtotal());
                if (d.getProducto() != null) {
                    dd.setProductoId(d.getProducto().getIdProducto());
                    dd.setProductoNombre(d.getProducto().getNombre());
                }
                return dd;
            }).collect(Collectors.toList());
            dto.setDetalles(detallesDTO);
        }

        Usuario usuario = c.getUsuario();
        if (usuario != null) {
            dto.setUsuarioNombre(usuario.getNombre());
            dto.setUsuarioApellido(usuario.getApellido());
        }

        return dto;
    }
}
