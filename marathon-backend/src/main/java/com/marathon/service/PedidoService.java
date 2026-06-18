package com.marathon.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.pedido.CambioEstadoDTO;
import com.marathon.dto.pedido.DetallePedidoItemDTO;
import com.marathon.dto.pedido.DetallePedidoResponseDTO;
import com.marathon.dto.pedido.PedidoRequestDTO;
import com.marathon.dto.pedido.PedidoResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.Cliente;
import com.marathon.model.DetallePedido;
import com.marathon.model.Pedido;
import com.marathon.model.Producto;
import com.marathon.model.Usuario;
import com.marathon.repository.ClienteRepository;
import com.marathon.repository.DetallePedidoRepository;
import com.marathon.repository.PedidoRepository;
import com.marathon.repository.ProductoRepository;
import com.marathon.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoRepository productoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PedidoService(PedidoRepository pedidoRepository,
                         DetallePedidoRepository detallePedidoRepository,
                         ClienteRepository clienteRepository,
                         UsuarioRepository usuarioRepository,
                         ProductoRepository productoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.productoRepository = productoRepository;
    }

    public PageResponseDTO<PedidoResponseDTO> listar(int page, int size, String estado,
                                                      String fechaDesde, String fechaHasta) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "idPedido"));
        Page<Pedido> result;

        LocalDateTime desde = null;
        LocalDateTime hasta = null;

        if (fechaDesde != null && !fechaDesde.isEmpty()) {
            desde = LocalDate.parse(fechaDesde).atStartOfDay();
        }
        if (fechaHasta != null && !fechaHasta.isEmpty()) {
            hasta = LocalDate.parse(fechaHasta).atTime(LocalTime.MAX);
        }

        if (estado != null && !estado.isEmpty() && desde != null && hasta != null) {
            result = pedidoRepository.findByEstadoAndFechaPedidoBetween(estado, desde, hasta, pageable);
        } else if (estado != null && !estado.isEmpty()) {
            result = pedidoRepository.findByEstado(estado, pageable);
        } else if (desde != null && hasta != null) {
            result = pedidoRepository.findByFechaPedidoBetween(desde, hasta, pageable);
        } else {
            result = pedidoRepository.findAll(pageable);
        }

        List<PedidoResponseDTO> content = result.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public PedidoResponseDTO obtener(Integer id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", id));
        PedidoResponseDTO dto = toDTO(pedido);
        List<DetallePedido> detalles = detallePedidoRepository.findByPedidoIdPedido(id);
        dto.setDetalles(detalles.stream().map(this::toDetalleDTO).collect(Collectors.toList()));
        return dto;
    }

    @Transactional
    public PedidoResponseDTO crear(PedidoRequestDTO dto, Integer idUsuarioActual) {
        Cliente cliente = clienteRepository.findById(dto.getIdCliente())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", dto.getIdCliente()));
        if (!"activo".equals(cliente.getEstado())) {
            throw new ValidationException("El cliente no está activo");
        }

        Usuario usuario = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setUsuario(usuario);
        pedido.setEstado("pendiente");
        pedido.setDescuento(dto.getDescuento() != null ? dto.getDescuento() : BigDecimal.ZERO);
        pedido = pedidoRepository.save(pedido);

        for (DetallePedidoItemDTO item : dto.getDetalles()) {
            Producto producto = productoRepository.findById(item.getIdProducto())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", item.getIdProducto()));

            DetallePedido detalle = new DetallePedido();
            detalle.setPedido(pedido);
            detalle.setProducto(producto);
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(item.getPrecioUnitario());
            detallePedidoRepository.save(detalle);
        }

        entityManager.flush();
        entityManager.clear();
        pedido = pedidoRepository.findById(pedido.getIdPedido()).orElseThrow();

        PedidoResponseDTO response = toDTO(pedido);
        List<DetallePedido> detalles = detallePedidoRepository.findByPedidoIdPedido(pedido.getIdPedido());
        response.setDetalles(detalles.stream().map(this::toDetalleDTO).collect(Collectors.toList()));
        return response;
    }

    @Transactional
    public PedidoResponseDTO cambiarEstado(Integer id, CambioEstadoDTO dto) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", id));

        String estadoActual = pedido.getEstado();
        String nuevoEstado = dto.getEstado();

        validarTransicion(estadoActual, nuevoEstado);

        pedido.setEstado(nuevoEstado);
        pedido = pedidoRepository.save(pedido);

        return toDTO(pedido);
    }

    private void validarTransicion(String estadoActual, String nuevoEstado) {
        boolean valida = false;

        switch (estadoActual) {
            case "pendiente":
                valida = "procesado".equals(nuevoEstado) || "anulado".equals(nuevoEstado);
                break;
            case "procesado":
                valida = "enviado".equals(nuevoEstado) || "anulado".equals(nuevoEstado);
                break;
            case "enviado":
                valida = "entregado".equals(nuevoEstado);
                break;
            case "entregado":
            case "anulado":
                valida = false;
                break;
            default:
                valida = false;
        }

        if (!valida) {
            throw new ValidationException(
                    "No se puede cambiar el estado de '" + estadoActual + "' a '" + nuevoEstado + "'");
        }
    }

    private PedidoResponseDTO toDTO(Pedido pedido) {
        PedidoResponseDTO dto = new PedidoResponseDTO();
        dto.setIdPedido(pedido.getIdPedido());
        dto.setFechaPedido(pedido.getFechaPedido());
        dto.setTotal(pedido.getTotal());
        dto.setEstado(pedido.getEstado());
        if (pedido.getCliente() != null) {
            dto.setIdCliente(pedido.getCliente().getIdCliente());
            dto.setClienteNombre(pedido.getCliente().getNombre() + " " + pedido.getCliente().getApellido());
        }
        if (pedido.getUsuario() != null) {
            dto.setIdUsuario(pedido.getUsuario().getIdUsuario());
            dto.setUsuarioNombre(pedido.getUsuario().getNombre() + " " + pedido.getUsuario().getApellido());
        }
        return dto;
    }

    private DetallePedidoResponseDTO toDetalleDTO(DetallePedido detalle) {
        DetallePedidoResponseDTO dto = new DetallePedidoResponseDTO();
        dto.setIdDetalle(detalle.getIdDetalle());
        dto.setCantidad(detalle.getCantidad());
        dto.setPrecioUnitario(detalle.getPrecioUnitario());
        dto.setSubtotal(detalle.getSubtotal());
        if (detalle.getProducto() != null) {
            dto.setProductoId(detalle.getProducto().getIdProducto());
            dto.setProductoNombre(detalle.getProducto().getNombre());
        }
        return dto;
    }
}
