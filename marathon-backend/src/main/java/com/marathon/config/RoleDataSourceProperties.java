package com.marathon.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Credenciales de los pools de conexion por rol (fase 37).
 *
 * <p>Cada rol funcional de la aplicacion tiene su propio usuario de login en
 * PostgreSQL. Las claves del mapa son las de {@link RoleRoutingDataSource}:
 * {@code supervisor}, {@code operador-bodega}, {@code operador-pedidos},
 * {@code encargado-compras} y {@code encargado-produccion}.
 *
 * <p>El administrador no aparece aqui: su credencial es la de
 * {@code spring.datasource.*}, que ademas es el pool por defecto para todo lo
 * que ocurre antes de haber autenticado a nadie.
 *
 * <p>Los valores reales viven en {@code application-local.properties}, que esta
 * en {@code .gitignore}. Con {@code enabled=false} la aplicacion vuelve al
 * comportamiento anterior a la F37: un unico pool para todo.
 */
@Component
@ConfigurationProperties(prefix = "app.datasource.roles")
public class RoleDataSourceProperties {

    /** Interruptor general del enrutado por rol. */
    private boolean enabled = false;

    /** Tamano maximo de cada pool de rol. Son pools de apoyo, no el principal. */
    private int maximumPoolSize = 5;

    /** Conexiones que cada pool de rol mantiene abiertas en reposo. */
    private int minimumIdle = 1;

    private Map<String, Credencial> credenciales = new LinkedHashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaximumPoolSize() { return maximumPoolSize; }
    public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }

    public int getMinimumIdle() { return minimumIdle; }
    public void setMinimumIdle(int minimumIdle) { this.minimumIdle = minimumIdle; }

    /**
     * Spring rellena este mapa con toda propiedad
     * {@code app.datasource.roles.<clave>.username|password}. Las propiedades
     * escalares de arriba ({@code enabled}, {@code maximum-pool-size},
     * {@code minimum-idle}) no entran aqui porque tienen su propio setter.
     */
    public Map<String, Credencial> getCredenciales() { return credenciales; }
    public void setCredenciales(Map<String, Credencial> credenciales) { this.credenciales = credenciales; }

    public static class Credencial {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
