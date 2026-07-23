package com.marathon.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.marathon.model.Permiso;
import com.marathon.model.Rol;
import com.marathon.model.RolPermiso;
import com.marathon.model.Usuario;
import com.marathon.model.UsuarioRol;
import com.marathon.repository.PermisoRepository;
import com.marathon.repository.RolPermisoRepository;
import com.marathon.repository.RolRepository;
import com.marathon.repository.UsuarioRepository;
import com.marathon.repository.UsuarioRolRepository;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final RolPermisoRepository rolPermisoRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RolRepository rolRepository,
                           PermisoRepository permisoRepository,
                           RolPermisoRepository rolPermisoRepository,
                           UsuarioRepository usuarioRepository,
                           UsuarioRolRepository usuarioRolRepository,
                           PasswordEncoder passwordEncoder) {
        this.rolRepository = rolRepository;
        this.permisoRepository = permisoRepository;
        this.rolPermisoRepository = rolPermisoRepository;
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

        // 2. Crear permisos (modulo + accion)
        List<Permiso> todosPermisos = new ArrayList<>();
        todosPermisos.addAll(crearPermisosModulo("usuarios", new String[]{"ver", "crear", "editar", "eliminar"}));
        todosPermisos.addAll(crearPermisosModulo("roles", new String[]{"ver", "crear", "editar", "eliminar"}));
        todosPermisos.addAll(crearPermisosModulo("productos", new String[]{"ver", "crear", "editar", "eliminar"}));
        todosPermisos.addAll(crearPermisosModulo("categorias", new String[]{"ver", "crear", "editar", "eliminar"}));
        todosPermisos.addAll(crearPermisosModulo("proveedores", new String[]{"ver", "crear", "editar", "eliminar"}));
        todosPermisos.addAll(crearPermisosModulo("bodegas", new String[]{"ver", "crear", "editar", "eliminar"}));
        todosPermisos.addAll(crearPermisosModulo("inventario", new String[]{"ver", "crear", "editar", "eliminar"}));
        todosPermisos.addAll(crearPermisosModulo("pedidos", new String[]{"ver", "crear", "editar", "eliminar", "anular"}));
        todosPermisos.addAll(crearPermisosModulo("picking", new String[]{"ver", "ejecutar", "confirmar"}));
        todosPermisos.addAll(crearPermisosModulo("comprobantes", new String[]{"ver", "emitir", "anular"}));
        todosPermisos.addAll(crearPermisosModulo("reportes", new String[]{"ver", "exportar"}));
        todosPermisos.addAll(crearPermisosModulo("dashboard", new String[]{"ver"}));
        todosPermisos.addAll(crearPermisosModulo("clientes", new String[]{"ver", "crear"}));

        // 3. Asignar permisos por rol
        // Administrador: TODOS
        for (Permiso p : todosPermisos) {
            rolPermisoRepository.save(new RolPermiso(admin, p));
        }

        // Supervisor E-Commerce
        asignarPermiso(supervisor, "dashboard", "ver");
        asignarPermiso(supervisor, "reportes", "ver");
        asignarPermiso(supervisor, "reportes", "exportar");
        asignarPermiso(supervisor, "pedidos", "ver");
        asignarPermiso(supervisor, "inventario", "ver");
        asignarPermiso(supervisor, "picking", "ver");

        // Operador de Bodega
        asignarPermiso(operadorBodega, "picking", "ver");
        asignarPermiso(operadorBodega, "picking", "ejecutar");
        asignarPermiso(operadorBodega, "picking", "confirmar");
        asignarPermiso(operadorBodega, "inventario", "ver");
        asignarPermiso(operadorBodega, "inventario", "editar");
        asignarPermiso(operadorBodega, "pedidos", "ver");

        // Operador de Pedidos
        asignarPermiso(operadorPedidos, "pedidos", "ver");
        asignarPermiso(operadorPedidos, "pedidos", "crear");
        asignarPermiso(operadorPedidos, "pedidos", "editar");
        asignarPermiso(operadorPedidos, "pedidos", "anular");
        asignarPermiso(operadorPedidos, "clientes", "ver");
        asignarPermiso(operadorPedidos, "clientes", "crear");
        asignarPermiso(operadorPedidos, "comprobantes", "ver");
        asignarPermiso(operadorPedidos, "comprobantes", "emitir");
        asignarPermiso(operadorPedidos, "productos", "ver");

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
     * 'Encargado de Compras' y 'Encargado de Producción', los permisos del
     * módulo 'compras' y las asignaciones rol_permiso para Encargado de
     * Compras y Administrador. Se ejecuta en cada arranque.
     */
    private void ensureComprasFase21() {
        Rol encargadoCompras = ensureRol("Encargado de Compras",
                "Gestiona órdenes de compra, recepciones, facturas y cuentas por pagar");
        ensureRol("Encargado de Producción",
                "Gestiona materia prima, BOM y órdenes de producción");

        Rol admin = rolRepository.findByNombre("Administrador").orElse(null);

        String[] acciones = {"ver", "crear", "aprobar", "rechazar", "cancelar"};
        List<Permiso> comprasPermisos = new ArrayList<>();
        for (String accion : acciones) {
            comprasPermisos.add(ensurePermiso("compras", accion));
        }

        for (Permiso p : comprasPermisos) {
            ensureRolPermiso(encargadoCompras, p);
            if (admin != null) {
                ensureRolPermiso(admin, p);
            }
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

    private void ensureRolPermiso(Rol rol, Permiso permiso) {
        List<RolPermiso> existentes = rolPermisoRepository.findByRolIdRol(rol.getIdRol());
        boolean yaAsignado = existentes.stream()
                .anyMatch(rp -> rp.getPermiso().getIdPermiso().equals(permiso.getIdPermiso()));
        if (!yaAsignado) {
            rolPermisoRepository.save(new RolPermiso(rol, permiso));
        }
    }

    private Rol crearRol(String nombre, String descripcion) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        rol.setDescripcion(descripcion);
        return rolRepository.save(rol);
    }

    private List<Permiso> crearPermisosModulo(String modulo, String[] acciones) {
        List<Permiso> permisos = new ArrayList<>();
        for (String accion : acciones) {
            Permiso p = new Permiso(modulo, accion, modulo + ":" + accion);
            permisos.add(permisoRepository.save(p));
        }
        return permisos;
    }

    private void asignarPermiso(Rol rol, String modulo, String accion) {
        List<Permiso> todos = permisoRepository.findAll();
        for (Permiso p : todos) {
            if (modulo.equals(p.getModulo()) && accion.equals(p.getAccion())) {
                rolPermisoRepository.save(new RolPermiso(rol, p));
                break;
            }
        }
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
