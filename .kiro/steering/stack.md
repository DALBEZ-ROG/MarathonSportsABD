# Stack Técnico — Marathon Sports

## Tecnologías y Versiones
| Componente | Tecnología | Versión |
|------------|-----------|---------|
| Backend | Spring Boot | 3.2.2 |
| Lenguaje Backend | Java | 17 |
| Build Backend | Maven | 3.9+ |
| Frontend | Angular | 17 |
| Lenguaje Frontend | TypeScript | 5.4 |
| Base de Datos | PostgreSQL | 15 |
| Autenticación | JWT (jjwt) | 0.11.5 |
| Documentación API | SpringDoc OpenAPI | 2.3.0 |
| Contenedores | Docker + Docker Compose | latest |

## Puertos
| Servicio | Puerto |
|----------|--------|
| Frontend Angular (dev) | 4200 |
| Backend Spring Boot | 8080 |
| PostgreSQL | 5432 |
| Swagger UI | 8080/swagger-ui.html |

## Estructura del Backend
```
marathon-backend/
├── src/main/java/com/marathon/
│   ├── config/          → Configuración Spring, CORS, Security
│   ├── controller/      → Endpoints REST (@RestController)
│   ├── service/         → Lógica de negocio (@Service)
│   ├── repository/      → Interfaces JPA (@Repository)
│   ├── model/           → Entidades JPA (@Entity)
│   ├── dto/             → Data Transfer Objects
│   └── exception/       → Manejo global de errores
├── src/main/resources/
│   ├── application.properties
│   └── application-dev.properties
└── pom.xml
```

## Estructura del Frontend
```
marathon-frontend/
├── src/app/
│   ├── core/
│   │   ├── interceptors/   → HTTP interceptor para JWT
│   │   ├── guards/         → Route guards por rol
│   │   └── services/       → Servicios compartidos (ApiService)
│   ├── shared/
│   │   ├── components/     → Componentes reutilizables
│   │   └── models/         → Interfaces TypeScript
│   └── modules/
│       ├── auth/                    → Login + perfil (F2)
│       ├── dashboard/               → Dashboard principal (F16, segmentado por rol en F31)
│       ├── datos-maestros/          → Ciudad, Categoría, Unidad Medida (F3)
│       ├── usuarios/ roles/         → Usuarios, roles y permisos (F4)
│       ├── proveedores/ bodegas/    → Proveedores (F3) y Bodegas (F5)
│       ├── productos/               → CRUD Productos + origen y BOM (F6, F27)
│       ├── inventario/              → Inventario y movimientos (F7)
│       ├── clientes/                → CRUD Clientes (F8)
│       ├── pedidos/                 → Pedidos, especiales y detalle (F9–F12.1)
│       ├── comprobantes/            → Comprobantes internos + PDF (F12, F13.1)
│       ├── picking/                 → Picking (F14)
│       ├── empaque/                 → Empaque y despachos (F15)
│       ├── reportes/                → 6 reportes con Excel/PDF (F17, F29, F30)
│       ├── ia/                      → Asistente IA (F18)
│       ├── auditoria/               → Auditoría y logs (F19b)
│       ├── compras/                 → Órdenes de compra, recepción, factura, CxP (F21–F23)
│       ├── devoluciones/            → Devolución de cliente / RMA (F24)
│       ├── devoluciones-proveedor/  → Devolución a proveedor (F25)
│       ├── materia-prima/           → Materia prima + kardex (F21, F26)
│       └── produccion/              → Órdenes de producción, costos, dashboard (F28–F30)
├── src/environments/
└── angular.json
```

## Roles del Sistema (6)
`Administrador`, `Supervisor E-Commerce`, `Operador de Bodega`, `Operador de Pedidos`,
`Encargado de Compras` (F21), `Encargado de Producción` (F21).

> La matriz completa de navegación por rol (navbar / guard de ruta / backend) está en
> **`MATRIZ_ROLES.md`** en la raíz del proyecto.

## Convenciones de Nombres
| Contexto | Convención | Ejemplo |
|----------|-----------|---------|
| Java: clases | PascalCase | `CiudadController` |
| Java: métodos/variables | camelCase | `findAll()`, `idCiudad` |
| Java: constantes | UPPER_SNAKE | `MAX_PAGE_SIZE` |
| Angular: archivos | kebab-case | `api.service.ts` |
| Angular: componentes | PascalCase (clase) | `HomeComponent` |
| Angular: selectores | kebab-case prefijado | `app-home` |
| BD: tablas/columnas | snake_case | `detalle_pedido`, `id_ciudad` |
| Endpoints REST | kebab-case plural | `/api/ciudades`, `/api/detalle-pedidos` |

## Formato Estándar de Error (JSON)
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Ciudad no encontrado con id: 99",
  "timestamp": "2024-01-15T10:30:00"
}
```

## Restricciones Inamovibles
- `spring.jpa.hibernate.ddl-auto` = **none** (NUNCA create, update, create-drop).
  Hibernate no gestiona el esquema: **el esquema se crea y evoluciona con los scripts
  SQL versionados** de `marathon-backend/sql/` (ver `SETUP_COMPLETO.md`).
- NO crear migraciones Flyway ni Liquibase.
- **Cambios de esquema solo mediante script SQL idempotente versionado por fase**
  (`fase21_*.sql` … `fase32_*.sql`). Nunca desde la aplicación ni a mano en la BD.
  A partir de F21 sí se crean tablas y columnas nuevas, pero siempre por esa vía.
- NO modificar las 20 tablas base F1–F20 salvo retrofit explícito y documentado
  (los únicos hechos: `producto.origen` en F27, columnas de costo en F29).
- Angular con **standalone components**, sin NgModules.
- Todos los endpoints bajo el prefijo **/api/**.
- Doble capa de seguridad: `rolGuard` en el frontend **y** `SecurityConfig` en el
  backend. El backend es la defensa efectiva; el guard evita pantallas que fallarían.
  En `SecurityConfig` **el orden importa**: las reglas específicas van ANTES de las
  generales (gana la primera coincidencia).

## Reglas de oro al tocar la BD
- Columnas **GENERATED** y las calculadas por trigger **nunca** se escriben desde la app.
  En JPA: `@Column(insertable=false, updatable=false)` y además
  `@Generated(event = {INSERT, UPDATE})` si se leen en la misma transacción.
- Antes de modificar `inventario.stock_actual`: `SET LOCAL app.current_user_id = '<id>'`
  para que el trigger de historial registre al usuario.
- Los triggers de protección deben comparar contra el **valor real recalculado**.
  NO usar `pg_trigger_depth() = 0`: dentro de un trigger vale 1, así que esa condición
  nunca se cumple y la protección no protege nada (bug detectado en F29, corregido en F32).
