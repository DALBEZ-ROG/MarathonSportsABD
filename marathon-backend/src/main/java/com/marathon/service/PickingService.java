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
import com.marathon.dto.picking.PickingLineaDTO;
import com.marathon.dto.picking.PickingPedidoDTO;
import com.marathon.dto.picking.PickingUpdateDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.Cliente;
import com.marathon.model.DetallePedido;
import com.marathon.model.Pedido;
import com.marathon.model.Producto;
import com.marathon.repository.DetallePedidoRepository;
import com.marathon.repository.PedidoRepository;

@Service
public class PickingService {

    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;

    private final LogService logService;

    public PickingService(PedidoRepository pedidoRepository,
                          DetallePedidoRepository detallePedidoRepository,
                      LogService logService) {
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.logService = logService;
    }

    public PickingPedidoDTO obtenerPickingPedido(Integer idPedido) {
        Pedido pedido = pedidoRepository.findById(idPedido)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", idPedido));

        if (!"procesado".equals(pedido.getEstado())) {
            throw new ValidationException("El pedido debe estar en estado procesado para ejecutar picking");
        }

        List<DetallePedido> detalles = detallePedidoRepository.findByPedidoIdPedidoOrderByIdDetalleAsc(idPedido);
        return toPedidoDTO(pedido, detalles);
    }

    public PageResponseDTO<PickingPedidoDTO> listarPedidosParaPicking(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("fechaPedido").ascending());
        Page<Pedido> result = pedidoRepository.findByEstado("procesado", pageable);

        List<PickingPedidoDTO> content = result.getContent().stream().map(pedido -> {
            List<DetallePedido> detalles = detallePedidoRepository.findByPedidoIdPedidoOrderByIdDetalleAsc(pedido.getIdPedido());
            return toPedidoDTO(pedido, detalles);
        }).collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    @Transactional
    public PickingLineaDTO actualizarLinea(Integer idPedido, PickingUpdateDTO dto, Integer idUsuarioActual) {
        Pedido pedido = pedidoRepository.findById(idPedido)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", idPedido));

        if (!"procesado".equals(pedido.getEstado())) {
            throw new ValidationException("El pedido debe estar en estado procesado para ejecutar picking");
        }

        DetallePedido detalle = detallePedidoRepository.findById(dto.getIdDetalle())
                .orElseThrow(() -> new ResourceNotFoundException("Detalle de pedido", dto.getIdDetalle()));

        if (detalle.getPedido() == null || !idPedido.equals(detalle.getPedido().getIdPedido())) {
            throw new ValidationException("La línea no pertenece al pedido indicado");
        }

        if (dto.getCantidadRecogida() > detalle.getCantidad()) {
            throw new ValidationException("Cantidad recogida no puede superar la cantidad del pedido (máximo: "
                    + detalle.getCantidad() + ")");
        }

        detalle.setCantidadRecogida(dto.getCantidadRecogida());
        detalle.setPickingCompletado(dto.getPickingCompletado());
        detalle = detallePedidoRepository.save(detalle);

        logService.registrar(idUsuarioActual, "picking", "actualizar_linea",
                "Pedido #" + idPedido + ", linea #" + dto.getIdDetalle() + ": recogidas "
                + dto.getCantidadRecogida() + " de " + detalle.getCantidad()
                + (Boolean.TRUE.equals(dto.getPickingCompletado()) ? " (linea completada)" : ""), null);

        return toLineaDTO(detalle);
    }

    public String verificarEstadoPicking(Integer idPedido) {
        long total = detallePedidoRepository.countByPedidoIdPedido(idPedido);
        long completadas = detallePedidoRepository.countByPedidoIdPedidoAndPickingCompletadoTrue(idPedido);
        return calcularEstadoPicking(total, completadas);
    }

    private String calcularEstadoPicking(long total, long completadas) {
        if (total > 0 && completadas == total) {
            return "completo";
        }
        if (completadas == 0) {
            return "pendiente";
        }
        return "en_progreso";
    }

    private PickingPedidoDTO toPedidoDTO(Pedido pedido, List<DetallePedido> detalles) {
        PickingPedidoDTO dto = new PickingPedidoDTO();
        dto.setIdPedido(pedido.getIdPedido());
        Cliente cliente = pedido.getCliente();
        if (cliente != null) {
            dto.setClienteNombre(cliente.getNombre());
            dto.setClienteApellido(cliente.getApellido());
        }
        dto.setFechaPedido(pedido.getFechaPedido());
        dto.setEstado(pedido.getEstado());
        dto.setEsPedidoEspecial(pedido.getEsPedidoEspecial());
        dto.setTipoEspecial(pedido.getTipoEspecial());
        dto.setNotaEspecial(pedido.getNotaEspecial());
        dto.setFechaLimiteEntrega(pedido.getFechaLimiteEntrega());

        List<PickingLineaDTO> lineas = detalles.stream().map(this::toLineaDTO).collect(Collectors.toList());
        dto.setLineas(lineas);

        int totalLineas = lineas.size();
        int completadas = (int) lineas.stream().filter(l -> Boolean.TRUE.equals(l.getPickingCompletado())).count();
        dto.setTotalLineas(totalLineas);
        dto.setLineasCompletadas(completadas);
        dto.setEstadoPicking(calcularEstadoPicking(totalLineas, completadas));

        return dto;
    }

    private PickingLineaDTO toLineaDTO(DetallePedido detalle) {
        PickingLineaDTO dto = new PickingLineaDTO();
        dto.setIdDetalle(detalle.getIdDetalle());
        Producto producto = detalle.getProducto();
        if (producto != null) {
            dto.setIdProducto(producto.getIdProducto());
            dto.setProductoNombre(producto.getNombre());
            dto.setProductoDescripcion(producto.getDescripcion());
            if (producto.getUnidadMedida() != null) {
                dto.setUnidadMedidaNombre(producto.getUnidadMedida().getNombre());
            }
        }
        Integer cantidad = detalle.getCantidad();
        Integer cantidadRecogida = detalle.getCantidadRecogida() != null ? detalle.getCantidadRecogida() : 0;
        dto.setCantidad(cantidad);
        dto.setCantidadRecogida(cantidadRecogida);
        dto.setPickingCompletado(detalle.getPickingCompletado());
        dto.setPendiente((cantidad != null ? cantidad : 0) - cantidadRecogida);
        return dto;
    }
}
