package com.marathon.config;


import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.marathon.model.Permiso;
import com.marathon.model.Rol;
import com.marathon.model.Usuario;
import com.marathon.model.UsuarioRol;
import com.marathon.repository.PermisoRepository;
import com.marathon.repository.RolRepository;
import com.marathon.repository.UsuarioRepository;
import com.marathon.repository.UsuarioRolRepository;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "app.datos-demo.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RolRepository rolRepository,
                           PermisoRepository permisoRepository,
                           UsuarioRepository usuarioRepository,
                           UsuarioRolRepository usuarioRolRepository,
                           PasswordEncoder passwordEncoder) {
        this.rolRepository = rolRepository;
        this.permisoRepository = permisoRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (rolRepository.count() > 0) {
            // Datos base ya existen — garantizar roles/permisos F21 y usuarios demo
            ensureComprasFase21();
            crearUsuariosDemo();
            return;
        }

        // Primera ejecución: crear roles, permisos y usuario admin
        // 1. Crear roles
        Rol admin = crearRol("Administrador", "Gestión total del sistema");
        Rol supervisor = crearRol("Supervisor E-Commerce", "Dashboard, KPIs y reportes");
        Rol operadorBodega = crearRol("Operador de Bodega", "Picking, empaque y stock");
        Rol operadorPedidos = crearRol("Operador de Pedidos", "Registro y seguimiento de pedidos");

        // 2 y 3. El reparto de permisos ya NO se hace aqui. Lo hace
        //        sql/fase48_matriz_permisos.sql, y hay un motivo concreto.
        //
        // Lo que habia aqui repartia 49 permisos con un criterio propio, y solo
        // corria con la base VACIA. Desde la F48 el reparto bueno son 94
        // permisos derivados de SecurityConfig. Es decir: este equipo, que venia
        // de antes, tenia la matriz buena, pero cualquier INSTALACION NUEVA
        // nacia con la vieja —que ademas se contradice con el propio
        // SecurityConfig, por ejemplo colgando compras:aprobar de quien no
        // aprueba—. No se habria visto hasta montar el sistema en otra maquina.
        //
        // Es la misma regla que ya estaba escrita en PENDIENTE.md §5 («no
        // devuelvas la asignacion de permisos a DataInitializer») aplicada al
        // unico rincon donde todavia no se cumplia: el primer arranque.
        //
        // Orden en una base nueva: fase61 (roles) -> resto -> fase48 (matriz).

        // 4. Crear usuario administrador
        if (!usuarioRepository.existsByCorreo("admin@marathon.com")) {
            Usuario adminUser = new Usuario();
            adminUser.setNombre("Admin");
            adminUser.setApellido("Marathon");
            adminUser.setCorreo("admin@marathon.com");
            adminUser.setPassword(passwordEncoder.encode("Admin1234!"));
            adminUser.setEstado("activo");
            usuarioRepository.save(adminUser);
            usuarioRolRepository.save(new UsuarioRol(adminUser, admin));
            System.out.println("✅ Usuario admin creado: admin@marathon.com / Admin1234!");
        }

        // 5. Roles y permisos de Compras (Fase 21)
        ensureComprasFase21();

        // 6. Crear usuarios demo para los demás roles
        crearUsuariosDemo();

        System.out.println("✅ Datos iniciales cargados correctamente");
    }

    /**
     * Fase 21 — Compras. Idempotente: crea (si no existen) los roles
     * 'Encargado de Compras' y 'Encargado de Producción' y los permisos del
     * módulo 'compras'. Se ejecuta en cada arranque.
     *
     * <p><b>F48 (D-13): ya no asigna esos permisos a ningún rol.</b> Hasta la
     * F48 este método volvía a colgar los cinco permisos de 'compras' —incluidos
     * {@code aprobar} y {@code rechazar}— del Encargado de Compras en cada
     * arranque. Eso contradecía a {@code OrdenCompraService.cambiarEstado()},
     * que exige Administrador para aprobar o rechazar, y ahora que los permisos
     * <i>deciden</i> deshacía la matriz de {@code fase48_matriz_permisos.sql} en
     * el siguiente reinicio.
     *
     * <p>El reparto vive en ese script, y ahí es donde se toca. Los permisos
     * sí se siguen creando aquí porque el script los da por existentes cuando
     * ya están, y así un arranque en limpio no depende del orden.
     */
    private void ensureComprasFase21() {
        ensureRol("Encargado de Compras",
                "Gestiona órdenes de compra, recepciones, facturas y cuentas por pagar");
        ensureRol("Encargado de Producción",
                "Gestiona materia prima, BOM y órdenes de producción");

        String[] acciones = {"ver", "crear", "aprobar", "rechazar", "cancelar"};
        for (String accion : acciones) {
            ensurePermiso("compras", accion);
        }
    }

    private Rol ensureRol(String nombre, String descripcion) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre(nombre);
            rol.setDescripcion(descripcion);
            Rol guardado = rolRepository.save(rol);
            System.out.println("✅ Rol creado: " + nombre);
            return guardado;
        });
    }

    private Permiso ensurePermiso(String modulo, String accion) {
        return permisoRepository.findByModuloAndAccion(modulo, accion)
                .orElseGet(() -> permisoRepository.save(new Permiso(modulo, accion, modulo + ":" + accion)));
    }

    private Rol crearRol(String nombre, String descripcion) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        rol.setDescripcion(descripcion);
        return rolRepository.save(rol);
    }



    private void crearUsuariosDemo() {
        // Roles con nombres exactos tal como están en la tabla rol (con espacios)
        crearUsuarioDemoSiNoExiste("supervisor@marathon.com", "Supervisor", "Demo",    "Supervisor E-Commerce");
        crearUsuarioDemoSiNoExiste("bodega@marathon.com",     "Operador",   "Bodega",  "Operador de Bodega");
        crearUsuarioDemoSiNoExiste("pedidos@marathon.com",    "Operador",   "Pedidos", "Operador de Pedidos");
        // Fase 21 — Compras
        crearUsuarioDemoSiNoExiste("compras@marathon.com",    "Encargado",  "Compras",    "Encargado de Compras");
        crearUsuarioDemoSiNoExiste("produccion@marathon.com", "Encargado",  "Producción", "Encargado de Producción");
    }

    private void crearUsuarioDemoSiNoExiste(String correo, String nombre, String apellido, String rolNombre) {
        // Verificar individualmente por correo — independiente de otros usuarios
        if (usuarioRepository.existsByCorreo(correo)) {
            System.out.println("   [demo] Usuario ya existe, omitiendo: " + correo);
            return;
        }
        Rol rol = rolRepository.findByNombre(rolNombre).orElse(null);
        if (rol == null) {
            System.out.println("   [demo] ⚠️  Rol '" + rolNombre + "' no encontrado en BD — omitiendo usuario " + correo);
            return;
        }
        try {
            Usuario u = new Usuario();
            u.setNombre(nombre);
            u.setApellido(apellido);
            u.setCorreo(correo);
            u.setPassword(passwordEncoder.encode("Demo1234!"));
            u.setEstado("activo");
            u = usuarioRepository.save(u);
            usuarioRolRepository.save(new UsuarioRol(u, rol));
            System.out.println("✅ Usuario demo creado: " + correo + " → " + rolNombre);
        } catch (Exception e) {
            System.out.println("   [demo] ❌ Error creando " + correo + ": " + e.getMessage());
        }
    }
}
