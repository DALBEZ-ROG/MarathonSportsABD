package com.marathon.config;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Construye un pool de conexiones por rol y los pone detras de
 * {@link RoleRoutingDataSource} (fase 37).
 *
 * <p>Definir aqui el bean {@code DataSource} retira la autoconfiguracion de
 * Spring Boot, asi que el pool por defecto se sigue construyendo a partir de
 * {@code spring.datasource.*} — el mismo {@code usr_admin_marathon} de la F34 —
 * para no cambiar el arranque ni la ruta de autenticacion.
 *
 * <p>Con {@code app.datasource.roles.enabled=false} no se crea ningun pool
 * adicional y la aplicacion se comporta igual que antes de esta fase. Es el
 * interruptor para volver atras sin tocar codigo.
 */
@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    /**
     * {@code porDefecto} lo aporta {@code DataSourceAutoConfiguration} a partir
     * de {@code spring.datasource.*}: definir aqui el bean {@code DataSource}
     * retira el pool que esa autoconfiguracion habria creado, pero no sus
     * propiedades, que se siguen inyectando.
     */
    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties porDefecto, RoleDataSourceProperties roles) {

        HikariDataSource poolAdministrador = porDefecto
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        poolAdministrador.setPoolName("pool-" + RoleRoutingDataSource.CLAVE_ADMINISTRADOR);

        if (!roles.isEnabled()) {
            log.warn("Enrutado por rol DESACTIVADO (app.datasource.roles.enabled=false): "
                   + "toda la aplicacion se conecta como '{}' y los otros cinco roles de "
                   + "PostgreSQL quedan fuera del camino de ejecucion.", porDefecto.getUsername());
            return poolAdministrador;
        }

        Map<Object, Object> pools = new LinkedHashMap<>();
        pools.put(RoleRoutingDataSource.CLAVE_ADMINISTRADOR, poolAdministrador);

        roles.getCredenciales().forEach((clave, credencial) -> {
            if (credencial.getUsername() == null || credencial.getUsername().isBlank()
                    || credencial.getPassword() == null || credencial.getPassword().isBlank()) {
                // Fallar aqui, al arrancar, y no en la primera peticion del rol.
                throw new IllegalStateException(
                    "El pool '" + clave + "' no tiene usuario o contrasena. Completar "
                    + "app.datasource.roles.credenciales." + clave + ".username/password "
                    + "en application-local.properties.");
            }

            HikariDataSource pool = porDefecto
                    .initializeDataSourceBuilder()
                    .type(HikariDataSource.class)
                    .username(credencial.getUsername())
                    .password(credencial.getPassword())
                    .build();
            pool.setPoolName("pool-" + clave);
            // Los pools de rol son de apoyo: el grueso del trafico sigue pasando
            // por el pool por defecto (login y filtro JWT de CADA peticion).
            // Sin recortarlos, seis pools con el tamano de Hikari por defecto
            // pedirian 60 conexiones y agotarian max_connections.
            pool.setMaximumPoolSize(roles.getMaximumPoolSize());
            pool.setMinimumIdle(roles.getMinimumIdle());

            pools.put(clave, pool);
            log.info("Pool '{}' -> usuario de base de datos '{}'", clave, credencial.getUsername());
        });

        RoleRoutingDataSource routing = new RoleRoutingDataSource(roles.getCredenciales().keySet());
        routing.setTargetDataSources(pools);
        routing.setDefaultTargetDataSource(poolAdministrador);
        routing.afterPropertiesSet();
        return routing;
    }
}
