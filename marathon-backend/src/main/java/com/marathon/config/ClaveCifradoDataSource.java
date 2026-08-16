package com.marathon.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Publica la clave de cifrado en cada conexion que entrega el pool (fase 41).
 *
 * <p>Sin esto, {@code fn_descifrar()} devuelve {@code null} y la aplicacion
 * mostraria los correos y telefonos vacios: la clave no vive en la base, asi
 * que cada sesion tiene que traerla consigo.
 *
 * <p><b>Por que un parametro enlazado y no {@code connectionInitSql}.</b> Hikari
 * permite fijar un SQL de inicializacion, pero solo como texto literal, y eso
 * significaria mandar la clave escrita dentro de la sentencia. Con
 * {@code log_statement = mod} y {@code log_parameter_max_length = -1}, una
 * sentencia asi puede acabar registrada en {@code postgresql-%a.log} en texto
 * plano y quedarse ahi siete dias. Con {@code set_config('app.crypto_key', ?,
 * false)} el texto de la sentencia no contiene la clave: viaja como parametro
 * del protocolo, y lo unico que podria verse en un registro es el signo de
 * interrogacion.
 *
 * <p><b>Por que en la conexion y no con {@code SET LOCAL} por transaccion.</b>
 * La mayor parte de las lecturas de cliente y proveedor (los listados) no son
 * transaccionales, y {@code SET LOCAL} fuera de una transaccion no tiene efecto
 * ninguno. Un listado sin clave devolveria una tabla llena de huecos. Se fija
 * por sesion, que es el alcance que cubre a todas.
 *
 * <p><b>Que pasa si no hay clave configurada.</b> No se publica nada y la
 * aplicacion arranca igual, con los campos cifrados en {@code null}. Es
 * deliberado: preferimos una aplicacion que arranca y no muestra datos
 * personales a una que no arranca. Se avisa una vez en el registro, con nivel
 * WARN, para que no pase inadvertido.
 */
public class ClaveCifradoDataSource extends DelegatingDataSource {

    private static final Logger log = LoggerFactory.getLogger(ClaveCifradoDataSource.class);

    private static final String PUBLICAR_CLAVE = "SELECT set_config('app.crypto_key', ?, false)";

    private final String clave;

    public ClaveCifradoDataSource(DataSource destino, String clave) {
        super(destino);
        this.clave = clave;
        if (clave == null || clave.isBlank()) {
            log.warn("No hay clave de cifrado (app.cifrado.clave / MARATHON_CRYPTO_KEY). "
                   + "Los datos de contacto de cliente y proveedor se mostraran VACIOS. "
                   + "Arrancar con scripts\\cifrado\\iniciar_backend.ps1, que la descifra del almacen DPAPI.");
        } else {
            log.info("Clave de cifrado cargada ({} caracteres). Se publicara en app.crypto_key "
                   + "en cada conexion mediante parametro enlazado.", clave.length());
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return publicarClave(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return publicarClave(super.getConnection(username, password));
    }

    private Connection publicarClave(Connection conexion) throws SQLException {
        if (clave == null || clave.isBlank()) {
            return conexion;
        }
        try (PreparedStatement ps = conexion.prepareStatement(PUBLICAR_CLAVE)) {
            ps.setString(1, clave);
            ps.execute();
        } catch (SQLException e) {
            // Se cierra la conexion antes de propagar: devolverla al pool sin
            // la clave publicada la dejaria disponible para otra peticion, que
            // leeria los datos personales vacios sin ningun aviso.
            try { conexion.close(); } catch (SQLException ignorada) { /* ya vamos a fallar */ }
            throw e;
        }
        return conexion;
    }
}
