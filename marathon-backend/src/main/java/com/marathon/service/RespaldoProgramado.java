package com.marathon.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.marathon.model.Respaldo;

/**
 * El respaldo de las 02:00 (F92).
 *
 * <p><b>Por que a las dos de la manana.</b> Es la hora con menos escrituras, y
 * eso importa para algo concreto: {@code pg_dump} toma una instantanea
 * coherente y mantiene abierta una transaccion mientras dura. Con trafico de
 * escritura, esa transaccion larga retrasa el {@code VACUUM} y las tablas se
 * hinchan. Con la base parada, no.
 *
 * <p><b>Por que aqui y no en el Programador de tareas de Windows.</b> Los
 * respaldos fisicos SI van en el Programador de tareas
 * ({@code scripts/backup/registrar_tareas.ps1}), y esta bien que sea asi:
 * tienen que correr aunque la aplicacion este caida. Este otro es distinto —
 * alimenta una pantalla de la aplicacion, escribe en su diario y comparte el
 * cerrojo con el boton manual. Sacarlo fuera obligaria a que dos procesos se
 * coordinaran para no pisarse, y ese es justo el problema que el cerrojo de
 * {@link RespaldoService} resuelve estando dentro.
 *
 * <p>La contrapartida, dicha en voz alta: <b>si el backend esta apagado a las
 * 02:00, no hay respaldo automatico esa noche</b>. El de recuperacion ante
 * desastres sigue siendo el fisico, que no depende de esto.
 */
@Component
public class RespaldoProgramado {

    private static final Logger log = LoggerFactory.getLogger(RespaldoProgramado.class);

    private final RespaldoService respaldoService;

    @Value("${app.respaldo.automatico.enabled:true}")
    private boolean activo;

    public RespaldoProgramado(RespaldoService respaldoService) {
        this.respaldoService = respaldoService;
    }

    /**
     * {@code zone} explicito: sin el, la expresion se interpreta en la zona por
     * omision del JVM, que en un servicio de Windows no tiene por que ser la de
     * quien lo configuro. «A las 2» tiene que ser a las 2 de aqui.
     */
    @Scheduled(cron = "${app.respaldo.automatico.cron:0 0 2 * * *}", zone = "America/Guayaquil")
    public void respaldoNocturno() {
        if (!activo) {
            return;
        }

        // Si a las 02:00 hay algo corriendo —una restauracion larga, un respaldo
        // manual que alguien lanzo a las 01:59— se deja pasar la noche en lugar
        // de encolar. Un respaldo tomado en mitad de una restauracion no es un
        // punto de recuperacion, es una foto de una base a medio reemplazar.
        if (respaldoService.hayTareaEnCurso()) {
            log.warn("Respaldo automatico omitido: hay otra operacion en curso.");
            return;
        }

        // Si ya hay uno de hace menos de doce horas, no se repite. Cubre el caso
        // de un reinicio del backend cerca de la hora, que con algunas
        // configuraciones dispara la tarea otra vez.
        var ultimo = respaldoService.ultimoCompletado();
        if (ultimo.isPresent()) {
            Duration desde = Duration.between(ultimo.get().getFechaInicio(), LocalDateTime.now());
            if (desde.toHours() < 12) {
                log.info("Respaldo automatico omitido: ya hay uno de hace {} h.", desde.toHours());
                return;
            }
        }

        try {
            log.info("Lanzando el respaldo automatico de las {}.", LocalDateTime.now().toLocalTime());
            // Sin usuario: no lo pidio nadie, lo pidio el reloj. Rellenarlo con
            // una cuenta de sistema seria mentir sobre quien estaba delante.
            respaldoService.respaldar(Respaldo.ORIGEN_AUTOMATICO,
                    "Respaldo automatico programado", null, null);
        } catch (Exception e) {
            // Que falle el respaldo de una noche no puede tumbar el programador
            // y dejar sin respaldo todas las noches siguientes. Queda el aviso
            // en el registro y la fila FALLIDO en el diario, que es lo que ve la
            // pantalla.
            log.error("El respaldo automatico fallo: {}", e.getMessage());
        }
    }
}
