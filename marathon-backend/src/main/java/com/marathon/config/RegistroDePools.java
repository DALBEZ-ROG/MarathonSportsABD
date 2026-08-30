package com.marathon.config;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Guarda una referencia a los seis pools de Hikari para poder reciclarlos
 * despues de una restauracion (F92).
 *
 * <p><b>Por que hace falta este registro.</b> Los pools los crea
 * {@link DataSourceConfig} y quedan escondidos dentro de
 * {@code RoleRoutingDataSource}, que los guarda en un mapa privado de
 * {@code AbstractRoutingDataSource}. Desde fuera solo se ve un
 * {@code DataSource}. Sin una lista explicita habria que llegar a ellos por
 * reflexion, que es exactamente el tipo de atajo que se rompe en la siguiente
 * version de Spring sin avisar.
 *
 * <p><b>Por que hay que reciclarlos.</b> El driver de PostgreSQL prepara las
 * sentencias <i>en el servidor</i> a partir de la quinta ejecucion
 * ({@code prepareThreshold=5}). Una restauracion borra y vuelve a crear las
 * tablas, y esos planes preparados quedan apuntando a tablas que ya no son las
 * mismas: la siguiente consulta falla con
 * {@code cached plan must not change result type}, un error que no se parece en
 * nada a su causa y que se arrastraria hasta el siguiente reinicio.
 *
 * <p>{@code softEvictConnections()} cierra las conexiones ociosas al momento y
 * marca las que estan en uso para que se cierren al devolverse. Las nuevas
 * nacen sin plan cacheado. No corta ninguna peticion en curso.
 */
@Component
public class RegistroDePools {

    private static final Logger log = LoggerFactory.getLogger(RegistroDePools.class);

    private final List<HikariDataSource> pools = new CopyOnWriteArrayList<>();

    public void registrar(HikariDataSource pool) {
        pools.add(pool);
    }

    public int cuantos() {
        return pools.size();
    }

    /** Fuerza a que todas las conexiones se renueven. */
    public void reciclarTodo() {
        for (HikariDataSource pool : pools) {
            try {
                if (pool.isRunning() && pool.getHikariPoolMXBean() != null) {
                    pool.getHikariPoolMXBean().softEvictConnections();
                    log.info("Pool '{}' reciclado tras la restauracion.", pool.getPoolName());
                }
            } catch (Exception e) {
                // Que falle el reciclado de un pool no puede tumbar la
                // restauracion, que a estas alturas ya termino bien. Lo peor que
                // pasa es que ese pool arrastre conexiones viejas hasta que las
                // cierre por inactividad.
                log.warn("No se pudo reciclar el pool '{}': {}", pool.getPoolName(), e.getMessage());
            }
        }
    }
}
