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
import com.marathon.dto.cliente.ClienteRequestDTO;
import com.marathon.dto.cliente.ClienteResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
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
    private final CifradoService cifradoService;

    public ClienteService(ClienteRepository clienteRepository,
                          CiudadRepository ciudadRepository,
                          PedidoRepository pedidoRepository,
                      LogService logService,
                      CifradoService cifradoService) {
        this.clienteRepository = clienteRepository;
        this.ciudadRepository = ciudadRepository;
        this.pedidoRepository = pedidoRepository;
        this.logService = logService;
        this.cifradoService = cifradoService;
    }

    public PageResponseDTO<ClienteResponseDTO> listar(int page, int size, String nombre, String estado) {
        // F51 (D-41): el ORDEN es obligatorio en una lista paginada.
        // Sin ORDER BY, PostgreSQL devuelve las filas en el orden del monton, y
        // un UPDATE reescribe la fila al final: la que acabas de editar
        // desaparece de su pagina. Ademas, dos paginas consecutivas pueden
        // repetir una fila y esconder otra.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "idCliente"));
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

    /**
     * Clientes activos para el selector de "Pedido nuevo".
     *
     * <p>F41: NO devuelve correo, telefono ni direccion, y es intencionado. La
     * pantalla que consume esto solo muestra "nombre apellido (cedula)", asi
     * que descifrar los tres campos de las 4.620 filas activas costaba 6
     * segundos de respuesta para no enseñar ninguno. Ver
     * {@code ClienteRepository.listarActivosSinContacto} para las mediciones.
     */
    public List<ClienteResponseDTO> listarActivos() {
        return clienteRepository.listarActivosSinContacto("activo").stream()
                .map(fila -> {
                    ClienteResponseDTO dto = new ClienteResponseDTO();
                    dto.setIdCliente((Integer) fila[0]);
                    dto.setNombre((String) fila[1]);
                    dto.setApellido((String) fila[2]);
                    dto.setEstado((String) fila[3]);
                    dto.setIdCiudad((Integer) fila[4]);
                    dto.setCiudadNombre((String) fila[5]);
                    return dto;
                })
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

        // saveAndFlush y no save: el UPDATE de cifrado necesita que la fila ya
        // exista y que id_cliente este asignado. Con save() a secas, Hibernate
        // podria retrasar el INSERT hasta el commit y el UPDATE no encontraria
        // nada que actualizar.
        cliente = clienteRepository.saveAndFlush(cliente);
        cifradoService.guardarDatosCliente(cliente, dto.getEmail(), dto.getTelefono(), dto.getDireccion());

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
        cliente = clienteRepository.saveAndFlush(cliente);
        cifradoService.guardarDatosCliente(cliente, dto.getEmail(), dto.getTelefono(), dto.getDireccion());

        logService.registrarAccion("clientes", "actualizar",
                "Cliente #" + id + " '" + cliente.getNombre() + " " + cliente.getApellido() + "' modificado");

        return toDTO(cliente);
    }

    @Transactional
    public void eliminar(Integer id) {
        logService.fijarContextoUsuario();
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", id));

        // No es un listado: solo pregunta "¿este cliente tiene algun pedido?".
        // El orden da igual porque nunca se mira el contenido (F51, D-41).
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
        // F73: el documento, que antes se pedia y se tiraba. Se normaliza a
        // digitos sin puntos ni guiones y en mayusculas para el pasaporte,
        // porque el indice unico compara textos y "1712345678" y "171234567-8"
        // son la misma cedula para una persona pero dos para PostgreSQL.
        String tipoDoc = vacioComoNulo(dto.getTipoDocumento());
        String numDoc = normalizarDocumento(tipoDoc, dto.getNumeroDocumento());
        validarDocumento(tipoDoc, numDoc, vacioComoNulo(dto.getNumeroDocumento()));
        comprobarDocumentoLibre(numDoc, cliente.getIdCliente());
        cliente.setTipoDocumento(tipoDoc);
        cliente.setNumeroDocumento(numDoc);
        // F41: correo, telefono y direccion NO se asignan aqui. Son campos
        // @Formula de solo lectura sobre columnas cifradas; asignarlos daria la
        // falsa impresion de que se guardan. Los persiste CifradoService, que
        // es quien puede emitir el fn_cifrar().
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
        dto.setTipoDocumento(cliente.getTipoDocumento());
        dto.setNumeroDocumento(cliente.getNumeroDocumento());
        if (cliente.getCiudad() != null) {
            dto.setIdCiudad(cliente.getCiudad().getIdCiudad());
            dto.setCiudadNombre(cliente.getCiudad().getNombre());
        }
        return dto;
    }

    /**
     * Comprueba el documento ANTES de que lo haga la base (F73).
     *
     * <p>Los CHECK de `cliente` ya lo rechazarian, pero el mensaje que sale de
     * ahi es el generico de integridad: «la operacion entra en conflicto con
     * datos existentes, puede que el registro ya exista». Eso manda a buscar un
     * duplicado cuando lo que pasa es que la cedula tiene tres digitos. Un
     * mensaje cierto que apunta al sitio equivocado cuesta mas tiempo que uno
     * claro.
     */
    private void validarDocumento(String tipo, String numero, String original) {
        // Se mira el ORIGINAL, no el normalizado: normalizarDocumento() devuelve
        // nulo cuando falta el tipo, asi que comprobar el normalizado dejaba
        // pasar "numero sin tipo" y el numero se perdia en silencio — que es
        // justo el defecto que esta fase vino a arreglar.
        if (tipo == null && original == null) {
            return;   // sin documento: es valido, los 5.000 antiguos no tienen
        }
        if (tipo == null) {
            throw new ValidationException("Has escrito el documento «" + original
                    + "» pero no has dicho de qué tipo es: cédula, RUC o pasaporte.");
        }
        if (numero == null || numero.isBlank()) {
            throw new ValidationException("Has elegido " + tipo + " pero no has escrito el número.");
        }
        switch (tipo) {
            case "cedula":
                if (!numero.matches("[0-9]{10}")) {
                    throw new ValidationException("Una cédula son 10 dígitos, y «" + original
                            + "» tiene " + numero.length() + " una vez quitados los guiones.");
                }
                break;
            case "ruc":
                if (!numero.matches("[0-9]{13}")) {
                    throw new ValidationException("Un RUC son 13 dígitos —la cédula o el RUC de la "
                            + "empresa, terminado en 001—, y «" + original + "» tiene "
                            + numero.length() + ".");
                }
                break;
            case "pasaporte":
                if (numero.length() < 5 || numero.length() > 20) {
                    throw new ValidationException("El pasaporte debe tener entre 5 y 20 caracteres.");
                }
                break;
            default:
                throw new ValidationException("Tipo de documento desconocido: «" + tipo
                        + "». Solo valen cédula, RUC o pasaporte.");
        }
    }

    private String vacioComoNulo(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /**
     * Deja el documento como lo espera el indice unico (F73).
     *
     * <p>Cedula y RUC se quedan solo con los digitos: quien teclea
     * {@code 171234567-8} y quien teclea {@code 1712345678} estan escribiendo la
     * misma cedula, pero para el indice serian dos clientes distintos. El
     * pasaporte va en mayusculas por lo mismo, que es alfanumerico.
     *
     * <p>Si el tipo viene vacio, no hay documento: se devuelve nulo y el CHECK
     * de la base se encarga de que el tipo tampoco quede suelto.
     */
    /**
     * Avisa del documento repetido diciendo DE QUIEN es (F73).
     *
     * <p>El indice unico de la base ya lo impide; esto solo se adelanta para
     * que el mensaje sirva de algo. Al editar hay que excluirse a uno mismo, o
     * guardar un cliente sin tocarle el documento fallaria contra si mismo.
     *
     * @param idActual id del cliente que se esta guardando, o {@code null} si es nuevo
     */
    private void comprobarDocumentoLibre(String numero, Integer idActual) {
        if (numero == null) { return; }
        clienteRepository.findByNumeroDocumento(numero).ifPresent(otro -> {
            if (idActual != null && idActual.equals(otro.getIdCliente())) { return; }
            throw new ValidationException("Ese documento ya lo tiene "
                    + otro.getNombre() + " " + otro.getApellido()
                    + " (cliente #" + otro.getIdCliente() + "). Dos clientes con el "
                    + "mismo documento son el mismo cliente dos veces.");
        });
    }

    private String normalizarDocumento(String tipo, String numero) {
        String limpio = vacioComoNulo(numero);
        if (limpio == null || vacioComoNulo(tipo) == null) {
            return null;
        }
        if ("pasaporte".equals(tipo)) {
            return limpio.toUpperCase().replaceAll("\s+", "");
        }
        return limpio.replaceAll("[^0-9]", "");
    }
}
