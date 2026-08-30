package com.marathon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @EnableScheduling lo estrena la F92, para el respaldo de las 02:00
 * ({@code RespaldoProgramado}). Antes no habia ninguna tarea periodica en la
 * aplicacion; las que existian —los respaldos fisicos— viven en el Programador
 * de tareas de Windows y siguen ahi.
 */
@SpringBootApplication
@EnableScheduling
public class MarathonBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarathonBackendApplication.class, args);
    }
}
