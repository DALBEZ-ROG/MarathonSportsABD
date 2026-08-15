package com.marathon.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.cliente.ClienteRequestDTO;
import com.marathon.dto.cliente.ClienteResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.model.Ciudad;
import com.marathon.model.Cliente;
import com.marathon.repository.CiudadRepository;
import com.marathon.repository.ClienteRepository;
import com.marathon.repository.PedidoRepository;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final CiudadRepository ciudadRepository;
    private final PedidoRepository pedidoRepository;

    private final LogService logService;

    public ClienteService(ClienteRepository clienteRepository,
                          CiudadRepository ciudadRepository,
                          PedidoRepository pedidoRepository,
                      LogService logService) {
        this.clienteRepository = clienteRepository;
        this.ciudadRepository = ciudadRepository;
        this.pedidoRepository = pedidoRepository;
        this.logService = logService;
    }

    public PageResponseDTO<ClienteResponseDTO> listar(int page, int size, String nombre, String estado) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Cliente> result;

        if (nombre != null && !nombre.isEmpty() && estado != null && !estado.isEmpty()) {
            result = clienteRepository.findByNombreOrApellidoAndEstado(nombre, estado, pageable);
        } else if (nombre != null && !nombre.isEmpty()) {
            result = clienteRepository.findByNombreOrApellido(nombre, pageable);
        } else if (estado != null && !estado.isEmpty()) {
            result = clienteRepository.findByEstado(estado, pageable);
        } else {
            result = clienteRepository.findAll(pageable);
        }

        List<ClienteResponseDTO> content = result.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public ClienteResponseDTO obtener(Integer id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", id));
        return toDTO(cliente);
    }

    public List<ClienteResponseDTO> listarActivos() {
        return clienteRepository.findByEstadoOrderByApellidoAsc("activo").stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public ClienteResponseDTO crear(ClienteRequestDTO dto) {
        logService.fijarContextoUsuario();
        Cliente cliente = new Cliente();
        mapearDatos(cliente, dto);
        if (cliente.getEstado() == null || cliente.getEstado().isEmpty()) {
            cliente.setEstado("activo");
        }

        cliente = clienteRepository.save(cliente);

        logService.registrarAccion("clientes", "crear",
                "Cliente #" + cliente.getIdCliente() + " '" + cliente.getNombre() + " "
                + cliente.getApellido() + "' creado");

        return toDTO(cliente);
    }

    @Transactional
    public ClienteResponseDTO actualizar(Integer id, ClienteRequestDTO dto) {
        logService.fijarContextoUsuario();
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", id));

        mapearDatos(cliente, dto);
        cliente = clienteRepository.save(cliente);

        logService.registrarAccion("clientes", "actualizar",
                "Cliente #" + id + " '" + cliente.getNombre() + " " + cliente.getApellido() + "' modificado");

        return toDTO(cliente);
    }

    @Transactional
    public void eliminar(Integer id) {
        logService.fijarContextoUsuario();
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", id));

        Page<com.marathon.model.Pedido> pedidosCliente = pedidoRepository.findByClienteIdCliente(id, PageRequest.of(0, 1));
        boolean tienePedidos = pedidosCliente.getTotalElements() > 0;
        if (tienePedidos) {
            cliente.setEstado("inactivo");
            clienteRepository.save(cliente);
        } else {
            clienteRepository.delete(cliente);
        }

        logService.registrarAccion("clientes", "eliminar",
                "Cliente #" + id + " '" + cliente.getNombre() + " " + cliente.getApellido() + "' "
                + (tienePedidos ? "dado de baja (tiene pedidos)" : "eliminado"));
    }

    private void mapearDatos(Cliente cliente, ClienteRequestDTO dto) {
        cliente.setNombre(dto.getNombre());
        cliente.setApellido(dto.getApellido());
        cliente.setCorreo(dto.getEmail());
        cliente.setTelefono(dto.getTelefono());
        cliente.setDireccion(dto.getDireccion());
        if (dto.getEstado() != null && !dto.getEstado().isEmpty()) {
            cliente.setEstado(dto.getEstado());
        }

        if (dto.getIdCiudad() != null) {
            Ciudad ciudad = ciudadRepository.findById(dto.getIdCiudad())
                    .orElseThrow(() -> new ResourceNotFoundException("Ciudad", dto.getIdCiudad()));
            cliente.setCiudad(ciudad);
        }
    }

    private ClienteResponseDTO toDTO(Cliente cliente) {
        ClienteResponseDTO dto = new ClienteResponseDTO();
        dto.setIdCliente(cliente.getIdCliente());
        dto.setNombre(cliente.getNombre());
        dto.setApellido(cliente.getApellido());
        dto.setEmail(cliente.getCorreo());
        dto.setTelefono(cliente.getTelefono());
        dto.setDireccion(cliente.getDireccion());
        dto.setEstado(cliente.getEstado());
        if (cliente.getCiudad() != null) {
            dto.setIdCiudad(cliente.getCiudad().getIdCiudad());
            dto.setCiudadNombre(cliente.getCiudad().getNombre());
        }
        return dto;
    }
}
