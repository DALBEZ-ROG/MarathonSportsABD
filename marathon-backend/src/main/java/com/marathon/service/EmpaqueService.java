package com.marathon.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.empaque.EmpaqueRequestDTO;
import com.marathon.dto.pedido.PedidoResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.DetallePedido;
import com.marathon.model.Inventario;
import com.marathon.model.MovimientoInventario;
import com.marathon.model.Pedido;
import com.marathon.model.Usuario;
import com.marathon.repository.DetallePedidoRepository;
import com.marathon.repository.InventarioRepository;
import com.marathon.repository.MovimientoInventarioRepository;
import com.marathon.repository.PedidoRepository;
import com.marathon.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class EmpaqueService {

    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final InventarioRepository inventarioRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PickingService pickingService;
    private final PedidoService pedidoService;
    private final LogService logService;

    @PersistenceContext
    private EntityManager entityManager;

    public EmpaqueService(PedidoRepository pedidoRepository,
                          DetallePedidoRepository detallePedidoRepository,
                          InventarioRepository inventarioRepository,
                          MovimientoInventarioRepository movimientoRepository,
                          UsuarioRepository usuarioRepository,
                          PickingService pickingService,
                          PedidoService pedidoService,
                          LogService logService) {
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.inventarioRepository = inventarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.usuarioRepository = usuarioRepository;
        this.pickingService = pickingService;
        this.pedidoService = pedidoService;
        this.logService = logService;
    }

    @Transactional
    public PedidoResponseDTO confirmarEmpaque(Integer idPedido, EmpaqueRequestDTO dto, Integer idUsuarioActual) {
        Pedido pedido = pedidoRepository.findById(idPedido)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", idPedido));

        String estadoPicking = pickingService.verificarEstadoPicking(idPedido);
        if (!"completo".equals(estadoPicking)) {
            long total = detallePedidoRepository.countByPedidoIdPedido(idPedido);
            long completadas = detallePedidoRepository.countByPedidoIdPedidoAndPickingCompletadoTrue(idPedido);
            throw new ValidationException("No se puede empacar: el picking no está completo. Líneas pendientes: "
                    + completadas + " de " + total);
        }

        if (!"procesado".equals(pedido.getEstado())) {
            throw new ValidationException("El pedido debe estar en estado procesado para empacar");
        }

        Usuario usuario = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        pedido.setNumeroHu(dto.getNumeroHu());
        pedido.setTransportista(dto.getTransportista());
        pedido.setRegionDestino(dto.getRegionDestino());
        pedido.setFechaEmpaque(LocalDateTime.now());
        pedido.setEstado("enviado");
        pedidoRepository.save(pedido);

        List<DetallePedido> detalles = detallePedidoRepository.findByPedidoIdPedidoOrderByIdDetalleAsc(idPedido);
        for (DetallePedido detalle : detalles) {
            if (detalle.getProducto() == null) {
                continue;
            }
            Integer idProducto = detalle.getProducto().getIdProducto();
            List<Inventario> inventarios = inventarioRepository.findByProductoIdProducto(idProducto);
            Inventario inv = inventarios.stream()
                    .filter(i -> i.getStockActual() != null && i.getStockActual() > 0)
                    .findFirst()
                    .orElse(null);

            if (inv == null) {
                continue;
            }

            int cantidad = detalle.getCantidad() != null ? detalle.getCantidad() : 0;
            int nuevoStock = inv.getStockActual() - cantidad;
            if (nuevoStock < 0) {
                nuevoStock = 0;
            }

            entityManager.createNativeQuery("SET LOCAL app.current_user_id = '" + idUsuarioActual + "'")
                    .executeUpdate();
            inv.setStockActual(nuevoStock);
            inventarioRepository.save(inv);

            MovimientoInventario mov = new MovimientoInventario();
            mov.setInventario(inv);
            mov.setTipoMovimiento("salida");
            mov.setCantidad(cantidad);
            mov.setUsuario(usuario);
            mov.setIdPedido(idPedido);
            mov.setObservacion("Despacho pedido #" + idPedido + " - HU: " + dto.getNumeroHu());
            movimientoRepository.save(mov);
        }

        logService.registrar(idUsuarioActual, "empaque", "confirmar",
                "Pedido #" + idPedido + " empacado. HU: " + dto.getNumeroHu()
                        + ". Transportista: " + dto.getTransportista(), null);

        return pedidoService.obtener(idPedido);
    }

    public PageResponseDTO<PedidoResponseDTO> listarDespachados(int page, int size, String regionDestino,
                                                                LocalDateTime desde, LocalDateTime hasta) {
        Pageable pageable = PageRequest.of(page, size);
        String region = (regionDestino != null && !regionDestino.isEmpty()) ? regionDestino : "";
        LocalDateTime desdeFiltro = (desde != null) ? desde : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime hastaFiltro = (hasta != null) ? hasta : LocalDateTime.of(2999, 12, 31, 23, 59);
        Page<Pedido> result = pedidoRepository.findDespachados(region, desdeFiltro, hastaFiltro, pageable);

        List<PedidoResponseDTO> content = result.getContent().stream()
                .map(p -> pedidoService.obtener(p.getIdPedido()))
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }
}
