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
            return; // Ya hay datos, no insertar
        }

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
        Usuario adminUser = new Usuario();
        adminUser.setNombre("Admin");
        adminUser.setApellido("Marathon");
        adminUser.setCorreo("admin@marathon.com");
        adminUser.setPassword(passwordEncoder.encode("Admin1234!"));
        adminUser.setEstado("activo");
        usuarioRepository.save(adminUser);

        // 5. Asignar rol
        usuarioRolRepository.save(new UsuarioRol(adminUser, admin));

        System.out.println("✅ Datos iniciales cargados correctamente");
        System.out.println("   Usuario: admin@marathon.com / Admin1234!");
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
}
