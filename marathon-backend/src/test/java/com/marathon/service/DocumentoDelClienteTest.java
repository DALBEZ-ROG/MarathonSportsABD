package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.dto.cliente.ClienteRequestDTO;
import com.marathon.dto.cliente.ClienteResponseDTO;
import com.marathon.exception.ValidationException;
import com.marathon.soporte.FixturaVenta;

/**
 * F73 — el cliente tiene documento, y no solo cédula.
 *
 * <p><b>De dónde sale.</b> Lo pidió el dueño del proyecto el 2026-08-28:
 *
 * <blockquote>«lo de cliente no solo tiene cédula sino también RUC»</blockquote>
 *
 * <p>Al ir a añadirlo apareció algo peor que una carencia: el formulario pedía
 * la cédula, la marcaba como <b>obligatoria</b>, el DTO la llevaba de ida y
 * vuelta… y la tabla {@code cliente} <b>no tenía ninguna columna donde
 * guardarla</b>. Se exigía un dato para tirarlo. Por eso la columna del listado
 * salía vacía en los 5.000 clientes.
 *
 * <p><b>Lo que esta prueba fija</b> —y que no se ve desde la interfaz— son las
 * cuatro reglas que hacen que el dato valga de algo:
 *
 * <ol>
 *   <li>cada tipo tiene su formato, y el que no lo cumple se rechaza <i>diciendo
 *       por qué</i>;
 *   <li>el número se normaliza antes de guardarlo: {@code 17-1234-5620} y
 *       {@code 1712345620} son la misma cédula para una persona, pero dos textos
 *       distintos para el índice único;
 *   <li>no se repite, y el aviso dice <b>de quién</b> es;
 *   <li>y sigue siendo <b>opcional</b>, porque los 5.000 clientes anteriores no
 *       lo tienen y exigirlo impediría editarles el teléfono.
 * </ol>
 *
 * <p>La regla 2 es la que menos se ve y más importa: sin normalizar, dos
 * empleados escribiendo la misma cédula con y sin guiones crearían dos clientes
 * y el índice único no se enteraría.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("F73 · el documento del cliente se guarda, se limpia y no se repite")
class DocumentoDelClienteTest {

    @Autowired private ClienteService clienteService;
    @Autowired private FixturaVenta fixtura;
    @Autowired private JdbcTemplate jdbc;

    private Integer idCiudad;
    private final List<Integer> creados = new ArrayList<>();

    @BeforeEach
    void preparar() {
        fixtura.empezar();
        idCiudad = jdbc.queryForObject(
                "select id_ciudad from cliente where id_cliente = ?", Integer.class, fixtura.getIdCliente());
    }

    @AfterEach
    void limpiar() {
        // Los clientes que crea la prueba se borran ANTES que la fixtura: cuelgan
        // de su ciudad, y si quedan vivos la ciudad no se puede eliminar.
        for (Integer id : creados) {
            jdbc.update("delete from cliente where id_cliente = ?", id);
        }
        creados.clear();
        fixtura.limpiar();
    }

    private ClienteRequestDTO peticion(String tipo, String numero) {
        ClienteRequestDTO dto = new ClienteRequestDTO();
        dto.setNombre("F73");
        dto.setApellido("Documento");
        dto.setTipoDocumento(tipo);
        dto.setNumeroDocumento(numero);
        dto.setIdCiudad(idCiudad);
        dto.setEstado("activo");
        return dto;
    }

    private ClienteResponseDTO crear(String tipo, String numero) {
        ClienteResponseDTO r = clienteService.crear(peticion(tipo, numero));
        creados.add(r.getIdCliente());
        return r;
    }

    @Test
    @DisplayName("los tres tipos se guardan de verdad, no como antes")
    void losTresTiposSeGuardan() {
        assertThat(crear("cedula", "1712345620").getNumeroDocumento()).isEqualTo("1712345620");
        assertThat(crear("ruc", "1791234567001").getNumeroDocumento()).isEqualTo("1791234567001");
        assertThat(crear("pasaporte", "AB12345X").getNumeroDocumento()).isEqualTo("AB12345X");

        // Y estan en la BASE, no solo en la respuesta: es justo lo que fallaba
        // antes, que el dato viajaba de vuelta sin haberse escrito en ningun sitio.
        Integer enBase = jdbc.queryForObject(
                "select count(*) from cliente where numero_documento in "
                + "('1712345620', '1791234567001', 'AB12345X')", Integer.class);
        assertThat(enBase).isEqualTo(3);
    }

    @Test
    @DisplayName("el numero se limpia antes de guardarlo: los guiones no crean un cliente nuevo")
    void elNumeroSeNormaliza() {
        assertThat(crear("cedula", "17-1234-5620").getNumeroDocumento()).isEqualTo("1712345620");
        assertThat(crear("pasaporte", "ab12345x").getNumeroDocumento()).isEqualTo("AB12345X");

        // Sin normalizar, esta segunda alta pasaria: para el indice unico
        // "1712345620" y "171234 5620" son dos textos distintos. Para una
        // persona son la misma cedula, y este es el fallo que se evita.
        assertThatThrownBy(() -> crear("cedula", "171234 5620"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("F73 Documento");
    }

    @Test
    @DisplayName("cada tipo tiene su formato, y el error dice por que")
    void cadaTipoTieneSuFormato() {
        assertThatThrownBy(() -> crear("cedula", "123"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("10 dígitos");

        assertThatThrownBy(() -> crear("ruc", "1791234567"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("13 dígitos");

        assertThatThrownBy(() -> crear("licencia", "12345"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("licencia");
    }

    @Test
    @DisplayName("un numero sin tipo no se traga en silencio")
    void numeroSinTipoAvisa() {
        // Este fallo lo tuvo ESTA MISMA fase: la comprobacion miraba el numero
        // ya normalizado, que es nulo cuando falta el tipo, asi que el numero se
        // perdia calladamente — exactamente el defecto que la fase venia a
        // arreglar. La prueba existe para que no vuelva.
        assertThatThrownBy(() -> crear(null, "1712345620"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("de qué tipo es");
    }

    @Test
    @DisplayName("el documento repetido dice de quien es")
    void elDocumentoNoSeRepite() {
        ClienteResponseDTO primero = crear("ruc", "1791234567001");

        assertThatThrownBy(() -> crear("ruc", "1791234567001"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cliente #" + primero.getIdCliente());
    }

    @Test
    @DisplayName("sigue siendo opcional: los 5.000 de antes no tienen, y se pueden editar")
    void elDocumentoEsOpcional() {
        ClienteResponseDTO sinDoc = crear(null, null);
        assertThat(sinDoc.getNumeroDocumento()).isNull();
        assertThat(sinDoc.getTipoDocumento()).isNull();

        // Ponerselo despues funciona, y quitarselo tambien: editar un cliente no
        // puede chocar contra su propio documento.
        ClienteRequestDTO conDoc = peticion("cedula", "1712345620");
        assertThat(clienteService.actualizar(sinDoc.getIdCliente(), conDoc).getNumeroDocumento())
                .isEqualTo("1712345620");
        assertThat(clienteService.actualizar(sinDoc.getIdCliente(), peticion("cedula", "1712345620"))
                .getNumeroDocumento()).isEqualTo("1712345620");
        assertThat(clienteService.actualizar(sinDoc.getIdCliente(), peticion(null, null))
                .getNumeroDocumento()).isNull();
    }
}
