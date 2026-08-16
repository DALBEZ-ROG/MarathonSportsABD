package com.marathon.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.model.Cliente;
import com.marathon.model.Proveedor;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Escritura de los datos personales cifrados (fase 41).
 *
 * <p><b>Por que hace falta un servicio aparte y no basta con
 * {@code repository.save()}.</b> Las columnas en claro de {@code cliente} y
 * {@code proveedor} ya no existen; en su lugar hay columnas {@code bytea} que
 * solo PostgreSQL sabe rellenar, porque <b>la clave nunca entra en el JVM para
 * cifrar</b>. Los campos de las entidades son {@code @Formula} de solo lectura,
 * asi que Hibernate no puede persistirlos: el {@code UPDATE} con
 * {@code fn_cifrar()} tiene que emitirse a mano.
 *
 * <p><b>Por que parametros enlazados y no concatenacion.</b> Los valores viajan
 * como parametros del prepared statement. La alternativa —construir el SQL con
 * el correo dentro— lo dejaria escrito en {@code postgresql-%a.log}, porque
 * {@code log_statement = mod} registra toda sentencia de modificacion. Cifrar
 * la columna y despues escribir el valor en claro en el registro del servidor
 * seria trabajo perdido.
 *
 * <p><b>Por que se refresca la entidad al terminar.</b> El {@code UPDATE}
 * nativo no lo ve el contexto de persistencia, asi que los campos
 * {@code @Formula} de la instancia en memoria seguirian con el valor anterior
 * (o {@code null} en un alta) y el DTO de respuesta devolveria eso. El
 * {@code refresh} vuelve a leer la fila y descifra, de modo que <b>lo que se le
 * devuelve al cliente es lo que quedo realmente guardado</b>, no lo que se
 * pretendia guardar.
 *
 * <p>{@code app.crypto_key} no se publica aqui: la publica
 * {@code ClaveCifradoDataSource} al entregar cada conexion, de modo que
 * cualquier consulta —y no solo estas— pueda descifrar.
 */
@Service
public class CifradoService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Cifra y guarda los datos de contacto de un cliente.
     *
     * <p>{@code correo_hash} no se toca: lo calcula el trigger
     * {@code trg_cliente_hash_correo} dentro de la base a partir del texto
     * cifrado. Asi no hay forma de que el hash y el correo se desincronicen
     * por una ruta de escritura que se olvide de actualizarlo.
     */
    @Transactional
    public void guardarDatosCliente(Cliente cliente, String correo, String telefono, String direccion) {
        entityManager.createNativeQuery(
                "UPDATE cliente SET correo_enc = fn_cifrar(CAST(? AS text)), "
                + "telefono_enc = fn_cifrar(CAST(? AS text)), "
                + "direccion_enc = fn_cifrar(CAST(? AS text)) "
                + "WHERE id_cliente = ?")
            .setParameter(1, correo)
            .setParameter(2, telefono)
            .setParameter(3, direccion)
            .setParameter(4, cliente.getIdCliente())
            .executeUpdate();

        entityManager.refresh(cliente);
    }

    /** Cifra y guarda los datos de contacto de un proveedor. */
    @Transactional
    public void guardarDatosProveedor(Proveedor proveedor, String contacto, String correo,
                                      String telefono, String direccion) {
        entityManager.createNativeQuery(
                "UPDATE proveedor SET contacto_enc = fn_cifrar(CAST(? AS text)), "
                + "correo_enc = fn_cifrar(CAST(? AS text)), "
                + "telefono_enc = fn_cifrar(CAST(? AS text)), "
                + "direccion_enc = fn_cifrar(CAST(? AS text)) "
                + "WHERE id_proveedor = ?")
            .setParameter(1, contacto)
            .setParameter(2, correo)
            .setParameter(3, telefono)
            .setParameter(4, direccion)
            .setParameter(5, proveedor.getIdProveedor())
            .executeUpdate();

        entityManager.refresh(proveedor);
    }
}
