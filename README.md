# Sistema de Gestión de Pedidos — Marathon Sports

Plataforma web interna para gestionar el ciclo completo de pedidos e-commerce: recepción, picking, empaque y despacho. Incluye control de inventario en tiempo real, dashboards operativos por rol, reportes exportables y asistente IA integrado.

## Stack Técnico

| Componente | Tecnología | Versión |
|------------|-----------|---------|
| Backend | Spring Boot | 3.2.2 |
| Lenguaje Backend | Java | 17 |
| Frontend | Angular | 17 |
| Base de Datos | PostgreSQL | 15 |
| Autenticación | JWT (jjwt) | 0.11.5 |
| PDF | iTextPDF | 8.x |
| Exportación | Apache POI (Excel) | 5.x |

## Requisitos Previos

- Java 17+
- Node.js 18+ y npm 9+
- PostgreSQL 15
- Maven 3.9+
- Angular CLI 17: `npm install -g @angular/cli@17`

## Pasos de Instalación

> **📋 Para levantar la BD desde cero en el orden EXACTO (DDL base → fases 21–27 → backend → seed), sigue la guía completa: [SETUP_COMPLETO.md](./SETUP_COMPLETO.md).** Incluye la query de verificación (35 tablas + conteos de negocio). Los pasos de abajo son un resumen.

### 1. Crear la base de datos y ejecutar el DDL

```sql
CREATE DATABASE mod_venta_inve;
```

Luego conéctate a `mod_venta_inve` y ejecuta, **en orden**, el DDL base y las migraciones de fase (ver [SETUP_COMPLETO.md](./SETUP_COMPLETO.md) pasos 2–9):

```bash
psql -U postgres -d mod_venta_inve -f marathon-backend/sql/fase00_ddl_base.sql
psql -U postgres -d mod_venta_inve -f marathon-backend/sql/fase21_ordenes_compra.sql
# ... fase22 … fase27 (ver SETUP_COMPLETO.md)
```

### 2. Cargar datos de demostración

Primero arranca el backend una vez para que `DataInitializer` cree roles, permisos y los 6 usuarios demo (incluido `admin@marathon.com`, requerido por el seed). Luego:

```bash
psql -U postgres -d mod_venta_inve -f marathon-backend/sql/seed_marathon_sports.sql
```

> El seed incluye: 88 ciudades, 105 productos, 40 clientes, 25 pedidos, bodegas, proveedores, categorías e inventario. Los roles/permisos/usuarios los crea el `DataInitializer`, no el seed. Ver [SETUP_COMPLETO.md](./SETUP_COMPLETO.md).

### 3. Configurar la conexión al backend

Copiar y editar `marathon-backend/src/main/resources/application-local.properties`:

```properties
# Conexión PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/mod_venta_inve
spring.datasource.username=postgres
spring.datasource.password=TU_PASSWORD

# JWT
jwt.secret=clave-secreta-muy-larga-de-al-menos-32-caracteres
jwt.expiration=86400000

# API Key de Anthropic (módulo IA — opcional)
anthropic.api.key=sk-ant-...
```

> `application-local.properties` está en `.gitignore` — nunca se sube al repositorio.

### 4. Arrancar el backend

```bash
cd marathon-backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

El servidor arranca en `http://localhost:8080`. Al primer inicio crea automáticamente el usuario admin y los usuarios demo.

### 5. Arrancar el frontend

```bash
cd marathon-frontend
npm install
ng serve
```

La aplicación queda disponible en `http://localhost:4200`.

## Credenciales de Usuarios de Prueba

| Correo | Contraseña | Rol |
|--------|-----------|-----|
| `admin@marathon.com` | `Admin1234!` | Administrador |
| `supervisor@marathon.com` | `Demo1234!` | Supervisor E-Commerce |
| `bodega@marathon.com` | `Demo1234!` | Operador de Bodega |
| `pedidos@marathon.com` | `Demo1234!` | Operador de Pedidos |

## Módulos del Sistema

| # | Módulo | Descripción |
|---|--------|-------------|
| 1 | **Autenticación** | Login JWT, refresh token, gestión de perfil |
| 2 | **Datos Maestros** | Ciudades, categorías, unidades de medida, proveedores |
| 3 | **Usuarios y Roles** | CRUD de usuarios, roles y permisos |
| 4 | **Productos e Inventario** | Catálogo de productos, stock por bodega, movimientos |
| 5 | **Pedidos** | Ciclo completo: creación, estados, pedidos especiales |
| 6 | **Picking y Empaque** | Recolección de productos y confirmación de despacho |
| 7 | **Comprobantes** | Generación de comprobantes PDF descargables |
| 8 | **Dashboard y Reportes** | KPIs operativos, reportes Excel/PDF, asistente IA |

## Documentación de la API

Swagger UI disponible en: `http://localhost:8080/swagger-ui.html`

## Nota sobre el Módulo IA

El asistente IA (Fase 18) requiere una API key de Anthropic configurada en `application-local.properties`. Sin esta clave el módulo muestra un error al consultar. El resto del sistema funciona sin la API key.
