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
│       ├── auth/           → Login (Fase 2)
│       ├── dashboard/      → Dashboard (Fase 16)
│       ├── usuarios/       → CRUD Usuarios (Fase 4)
│       ├── productos/      → CRUD Productos (Fase 6)
│       ├── pedidos/        → Gestión Pedidos (Fase 10)
│       ├── inventario/     → Inventario (Fase 7)
│       ├── picking/        → Picking (Fase 14)
│       └── reportes/       → Reportes (Fase 17)
├── src/environments/
└── angular.json
```

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
- `spring.jpa.hibernate.ddl-auto` = **validate** (NUNCA create, update, create-drop)
- NO crear migraciones Flyway ni Liquibase
- NO modificar tablas, columnas ni constraints de la BD
- Angular con **standalone components**, sin NgModules
- Todos los endpoints bajo el prefijo **/api/**
- Spring Security solo como dependencia hasta Fase 2
