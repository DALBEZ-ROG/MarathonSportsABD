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

    /**
     * Estados de pedido desde los que se puede emitir un comprobante (L6, D-11).
     *
     * <p>Se factura lo que ya salio del almacen. Si el negocio decidiera facturar
     * por adelantado, aqui se anadiria {@code "procesado"} — es una regla de
     * negocio, y este es el unico sitio donde se declara.
     */
    private static final java.util.List<String> ESTADOS_FACTURABLES =
            java.util.List.of("enviado", "entregado");

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

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

        // --------------------------------------------------------------------
        // L6 (D-11): no se factura un pedido en cualquier estado.
        // --------------------------------------------------------------------
        // Antes se emitia sobre pedidos 'pendiente' —sin picking, sin despacho,
        // sin stock movido— e incluso sobre pedidos 'anulado'. La facturacion
        // estaba desligada del ciclo de la venta.
        if (!ESTADOS_FACTURABLES.contains(pedido.getEstado())) {
            throw new ValidationException("No se puede emitir un comprobante de un pedido en estado '"
                    + pedido.getEstado() + "'. Solo se factura lo que ya salió: "
                    + String.join(" o ", ESTADOS_FACTURABLES) + ".");
        }

        // --------------------------------------------------------------------
        // L6 (D-06): tras anular se puede volver a emitir.
        // --------------------------------------------------------------------
        // Esta comprobacion no filtraba por estado, y 'anular' solo marca el
        // comprobante como anulado sin desvincularlo. Resultado: anular una
        // factura por un error de emision dejaba ese pedido sin poder facturarse
        // NUNCA mas. Ahora solo bloquea un comprobante vigente.
        if (comprobanteRepository.findByPedidoIdPedido(idPedido)
                .filter(c -> "emitido".equals(c.getEstado()))
                .isPresent()) {
            throw new ValidationException("El pedido ya tiene un comprobante emitido y vigente");
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

    /**
     * Correlativo del comprobante (L6, D-07).
     *
     * <p>Antes era {@code comprobanteRepository.count() + 1}: dos emisiones
     * simultaneas leian el mismo recuento, generaban el mismo numero, y la
     * segunda chocaba contra {@code uq_comprobante_numero} devolviendo un 500.
     * Un contador de documentos no puede salir de un COUNT(*).
     *
     * <p>Ahora lo da {@code seq_comprobante_interno} (fase 46), que PostgreSQL
     * garantiza unico aunque veinte peticiones lleguen a la vez. La secuencia se
     * inicializo por encima del mayor correlativo ya emitido.
     */
    private String generarNumero() {
        int anio = Year.now().getValue();
        Number siguiente = (Number) entityManager
                .createNativeQuery("SELECT nextval('seq_comprobante_interno')")
                .getSingleResult();
        return String.format("COMP-%d-%06d", anio, siguiente.longValue());
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
