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
import com.marathon.model.Bodega;
import com.marathon.model.Cliente;
import com.marathon.model.DetallePedido;
import com.marathon.model.Pedido;
import com.marathon.model.Producto;
import com.marathon.repository.BodegaRepository;
import com.marathon.repository.DetallePedidoRepository;
import com.marathon.repository.InventarioRepository;
import com.marathon.repository.PedidoRepository;

@Service
public class PickingService {

    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final BodegaRepository bodegaRepository;
    private final InventarioRepository inventarioRepository;

    private final LogService logService;

    public PickingService(PedidoRepository pedidoRepository,
                          DetallePedidoRepository detallePedidoRepository,
                          BodegaRepository bodegaRepository,
                          InventarioRepository inventarioRepository,
                      LogService logService) {
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.bodegaRepository = bodegaRepository;
        this.inventarioRepository = inventarioRepository;
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
        // F55: del MAS RECIENTE al mas antiguo, a peticion del dueño del
        // proyecto. Antes era al reves, por criterio FIFO —lo que lleva mas
        // esperando se atiende primero—, que es la convencion de almacen. Con
        // 19.059 pedidos en 'procesado', casi todos del poblado masivo y de
        // meses atras, esa cola no era una cola de trabajo: era un archivo, y
        // dejaba el pedido de hoy a 1.900 paginas de distancia.
        //
        // Si algun dia hace falta atender por antiguedad, el sitio es esta
        // linea y el buscador de la pantalla cubre el resto.
        Pageable pageable = PageRequest.of(page, size, Sort.by("fechaPedido").descending());
        Page<Pedido> result = pedidoRepository.findByEstado("procesado", pageable);

        List<PickingPedidoDTO> content = result.getContent().stream().map(pedido -> {
            List<DetallePedido> detalles = detallePedidoRepository.findByPedidoIdPedidoOrderByIdDetalleAsc(pedido.getIdPedido());
            return toPedidoDTO(pedido, detalles);
        }).collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    /**
     * Pedidos listos para EMPACAR (F52, D-42).
     *
     * <p>Dos diferencias con la cola de picking, y las dos importan:
     *
     * <ul>
     *   <li><b>Filtra en la base</b>, no en el navegador. La pantalla de Empaque
     *       pedia los 100 primeros pedidos procesados y descartaba en el cliente
     *       los que no tenian el picking completo; todo lo que estuviera mas
     *       alla del pedido 100 era invisible.</li>
     *   <li><b>Ordena del mas RECIENTE al mas antiguo</b>, al reves que la cola
     *       de picking. La cola de picking es trabajo por hacer y se atiende por
     *       orden de llegada; el empaque es lo que <i>acabas</i> de recoger, y lo
     *       que buscas es lo ultimo. Con el orden antiguo, un pedido recogido
     *       hace un minuto quedaba el ultimo de 9.002 y no habia forma de
     *       llegar a el.</li>
     * </ul>
     */
    public PageResponseDTO<PickingPedidoDTO> listarPedidosParaEmpacar(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("fechaPedido").descending());
        Page<Pedido> result = pedidoRepository.buscarListosParaEmpacar(pageable);

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

        // --------------------------------------------------------------------
        // L4 (D-14): de que bodega se recoge.
        // --------------------------------------------------------------------
        // Hasta la F45 el picking solo anotaba cuanto se habia recogido, nunca
        // de donde, y el despacho tenia que adivinar la bodega. Ahora se exige
        // el dato y se comprueba contra el inventario en el momento de recoger,
        // que es cuando el operario tiene la mercancia delante: descubrir que no
        // hay stock en el empaque, media hora despues, no le sirve a nadie.
        if (dto.getCantidadRecogida() > 0) {
            if (dto.getIdBodega() == null) {
                throw new ValidationException("Debe indicar de qué bodega se recogió la línea");
            }
            Bodega bodega = bodegaRepository.findById(dto.getIdBodega())
                    .orElseThrow(() -> new ResourceNotFoundException("Bodega", dto.getIdBodega()));

            Integer idProducto = detalle.getProducto() != null
                    ? detalle.getProducto().getIdProducto() : null;
            int disponible = inventarioRepository
                    .findByProductoIdProductoAndBodegaIdBodega(idProducto, dto.getIdBodega())
                    .map(i -> i.getStockActual() != null ? i.getStockActual() : 0)
                    .orElse(0);

            if (disponible < dto.getCantidadRecogida()) {
                throw new ValidationException("No hay stock suficiente en la bodega '" + bodega.getNombre()
                        + "': se recogen " + dto.getCantidadRecogida() + " y hay " + disponible + ".");
            }
            detalle.setBodegaPicking(bodega);
        } else {
            detalle.setBodegaPicking(null);
        }

        detalle.setCantidadRecogida(dto.getCantidadRecogida());
        // La linea queda completa solamente al recoger exactamente el total.
        // No se confia en el booleano enviado por el cliente.
        detalle.setPickingCompletado(detalle.getCantidad() != null
                && detalle.getCantidad() > 0
                && detalle.getCantidad().equals(dto.getCantidadRecogida()));
        detalle = detallePedidoRepository.save(detalle);

        logService.registrar(idUsuarioActual, "picking", "actualizar_linea",
                "Pedido #" + idPedido + ", linea #" + dto.getIdDetalle() + ": recogidas "
                + dto.getCantidadRecogida() + " de " + detalle.getCantidad()
                + (Boolean.TRUE.equals(detalle.getPickingCompletado()) ? " (linea completada)" : ""), null);

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
        dto.setNumeroPedido(String.format("PED-%06d", pedido.getIdPedido()));
        Cliente cliente = pedido.getCliente();
        if (cliente != null) {
            dto.setClienteNombre(cliente.getNombre());
            dto.setClienteApellido(cliente.getApellido());
            // F77: a donde va. El empaque lo proponia preguntandolo; ya estaba
            // en la ficha del cliente desde que se creo el pedido.
            if (cliente.getCiudad() != null) {
                dto.setCiudadDestino(cliente.getCiudad().getNombre());
                dto.setRegionDestino(cliente.getCiudad().getRegion());
            }
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
        if (detalle.getBodegaPicking() != null) {
            dto.setIdBodegaPicking(detalle.getBodegaPicking().getIdBodega());
            dto.setBodegaPickingNombre(detalle.getBodegaPicking().getNombre());
        }
        return dto;
    }
}
