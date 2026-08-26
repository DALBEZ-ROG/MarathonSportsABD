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
    private final LogService logService;
    private final PickingService pickingService;

    @PersistenceContext
    private EntityManager entityManager;

    public PedidoService(PedidoRepository pedidoRepository,
                         DetallePedidoRepository detallePedidoRepository,
                         ClienteRepository clienteRepository,
                         UsuarioRepository usuarioRepository,
                         ProductoRepository productoRepository,
                         LogService logService,
                         PickingService pickingService) {
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.productoRepository = productoRepository;
        this.logService = logService;
        this.pickingService = pickingService;
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

    public PageResponseDTO<PedidoResponseDTO> listarEspeciales(int page, int size, String tipoEspecial) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("fechaLimiteEntrega").nullsLast()));
        Page<Pedido> result;

        if (tipoEspecial != null && !tipoEspecial.isEmpty()) {
            result = pedidoRepository.findByEsPedidoEspecialTrueAndTipoEspecial(tipoEspecial, pageable);
        } else {
            result = pedidoRepository.findByEsPedidoEspecialTrue(pageable);
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

        // --------------------------------------------------------------------
        // L8 (D-19): el descuento tiene tope, y pasarse es un error, no un 0.
        // --------------------------------------------------------------------
        // El DTO no valida 'descuento' y el trigger aplica GREATEST(..., 0), asi
        // que un descuento mayor que el subtotal no daba error: dejaba el pedido
        // en total 0 y nadie se enteraba. Uno negativo inflaba el total y moria
        // como un 500 desde chk_pedido_descuento.
        //
        // El subtotal se calcula aqui, a precio de catalogo, porque es el mismo
        // precio que la L3 va a persistir en las lineas.
        BigDecimal descuento = dto.getDescuento() != null ? dto.getDescuento() : BigDecimal.ZERO;
        if (descuento.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("El descuento no puede ser negativo");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        for (DetallePedidoItemDTO item : dto.getDetalles()) {
            Producto p = productoRepository.findById(item.getIdProducto())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", item.getIdProducto()));
            subtotal = subtotal.add(p.getPrecio().multiply(BigDecimal.valueOf(item.getCantidad())));
        }
        if (descuento.compareTo(subtotal) > 0) {
            throw new ValidationException("El descuento (" + descuento
                    + ") no puede superar el subtotal del pedido (" + subtotal + ")");
        }

        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setUsuario(usuario);
        pedido.setEstado("pendiente");
        pedido.setDescuento(descuento);

        boolean esEspecial = Boolean.TRUE.equals(dto.getEsPedidoEspecial());
        if (esEspecial && (dto.getTipoEspecial() == null || dto.getTipoEspecial().trim().isEmpty())) {
            throw new ValidationException("Debe especificar el tipo de pedido especial");
        }
        pedido.setEsPedidoEspecial(esEspecial);
        pedido.setTipoEspecial(esEspecial ? dto.getTipoEspecial() : null);
        pedido.setNotaEspecial(esEspecial ? dto.getNotaEspecial() : null);
        pedido.setFechaLimiteEntrega(esEspecial ? dto.getFechaLimiteEntrega() : null);

        pedido = pedidoRepository.save(pedido);

        for (DetallePedidoItemDTO item : dto.getDetalles()) {
            Producto producto = productoRepository.findById(item.getIdProducto())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", item.getIdProducto()));

            // Se comprueba el estado del producto igual que arriba se comprueba
            // el del cliente. Sin esto se podia vender un producto dado de baja
            // (D-24).
            if (!"activo".equals(producto.getEstado())) {
                throw new ValidationException(
                        "El producto '" + producto.getNombre() + "' no está activo y no se puede vender");
            }

            DetallePedido detalle = new DetallePedido();
            detalle.setPedido(pedido);
            detalle.setProducto(producto);
            detalle.setCantidad(item.getCantidad());
            // ------------------------------------------------------------------
            // L3 (D-34): el precio lo pone el CATALOGO, no quien llama.
            // ------------------------------------------------------------------
            // Hasta aqui era detalle.setPrecioUnitario(item.getPrecioUnitario()):
            // el producto se cargaba de la base solo para asociarlo por id, y su
            // precio se ignoraba. Un POST con "precioUnitario": 0.01 sobre un
            // articulo de 200 creaba un pedido valido de 0.01, y las tres
            // defensas del motor (fn_recalcular_total_pedido_stmt,
            // fn_proteger_total_pedido, fn_validar_total_comprobante) confirmaban
            // fielmente ese importe inventado, porque no tienen forma de saber
            // cual era el precio de catalogo.
            //
            // item.getPrecioUnitario() se sigue aceptando en el DTO pero se
            // ignora: quitarlo del contrato ahora romperia al frontend, que
            // todavia lo envia. Se retira cuando el front deje de mandarlo.
            detalle.setPrecioUnitario(producto.getPrecio());
            detallePedidoRepository.save(detalle);
        }

        entityManager.flush();
        entityManager.clear();
        pedido = pedidoRepository.findById(pedido.getIdPedido()).orElseThrow();

        PedidoResponseDTO response = toDTO(pedido);
        List<DetallePedido> detalles = detallePedidoRepository.findByPedidoIdPedido(pedido.getIdPedido());
        response.setDetalles(detalles.stream().map(this::toDetalleDTO).collect(Collectors.toList()));

        logService.registrar(idUsuarioActual, "pedidos", "crear",
                "Pedido #" + pedido.getIdPedido() + " creado. Total: $" + pedido.getTotal(), null);

        return response;
    }

    @Transactional
    public PedidoResponseDTO cambiarEstado(Integer id, CambioEstadoDTO dto) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", id));

        String estadoActual = pedido.getEstado();
        String nuevoEstado = dto.getEstado();

        validarTransicion(estadoActual, nuevoEstado);

        // La transición procesado→enviado requiere picking completo.
        // El flujo normal pasa por EmpaqueService, que valida y asigna HU/transportista.
        if ("procesado".equals(estadoActual) && "enviado".equals(nuevoEstado)) {
            String estadoPicking = pickingService.verificarEstadoPicking(id);
            if (!"completo".equals(estadoPicking)) {
                throw new ValidationException(
                    "No se puede enviar el pedido: el picking no está completo. " +
                    "Usa el módulo de empaque para procesar el despacho correctamente.");
            }
        }

        pedido.setEstado(nuevoEstado);
        pedido = pedidoRepository.save(pedido);

        // L11 (D-18): antes se pasaba null literal, y la transición de estado
        // —incluida la anulación— quedaba en la bitácora sin responsable.
        // idUsuarioActual() lo resuelve del contexto de seguridad; no hizo falta
        // cambiar ninguna firma.
        logService.registrar(logService.idUsuarioActual(), "pedidos", "cambio_estado",
                "Pedido #" + id + ": " + estadoActual + " → " + nuevoEstado, null);

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

    /**
     * Construye el DTO con detalles YA cargados, sin volver a la base (L16, D-28).
     *
     * <p>obtener(id) sigue existiendo para el caso de un solo pedido; esto es
     * para cuando quien llama ya trae la pagina entera y sus lineas.
     */
    public PedidoResponseDTO aDTOConDetalles(Pedido pedido, List<DetallePedido> detalles) {
        PedidoResponseDTO dto = toDTO(pedido);
        dto.setDetalles(detalles.stream().map(this::toDetalleDTO).collect(Collectors.toList()));
        return dto;
    }

    private PedidoResponseDTO toDTO(Pedido pedido) {
        PedidoResponseDTO dto = new PedidoResponseDTO();
        dto.setIdPedido(pedido.getIdPedido());
        dto.setNumeroPedido(String.format("PED-%06d", pedido.getIdPedido()));
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
        dto.setEsPedidoEspecial(pedido.getEsPedidoEspecial());
        dto.setTipoEspecial(pedido.getTipoEspecial());
        dto.setNotaEspecial(pedido.getNotaEspecial());
        dto.setFechaLimiteEntrega(pedido.getFechaLimiteEntrega());
        dto.setNumeroHu(pedido.getNumeroHu());
        dto.setTransportista(pedido.getTransportista());
        dto.setRegionDestino(pedido.getRegionDestino());
        dto.setFechaEmpaque(pedido.getFechaEmpaque());
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
