# Sistema de Gestión de Pedidos — Marathon Sports

Plataforma web interna que cubre **cuatro ciclos de negocio completos** sobre una única base de datos PostgreSQL: venta (Order-to-Cash), compra (Procure-to-Pay), manufactura (BOM → producción → costeo) y calidad (devoluciones de cliente y a proveedor). Incluye control de inventario con trazabilidad por trigger, dashboards segmentados por rol, reportes exportables a Excel/PDF y asistente IA para consultas en lenguaje natural.

**Estado: proyecto completo — 32 de 32 fases.**

| Métrica | Valor |
|---|---|
| Tablas | 37 |
| Funciones PL/pgSQL | 17 |
| Triggers | 24 |
| Claves foráneas | 70 |
| Módulos frontend | 21 |
| Roles | 6 |
| Ciclos de negocio | 4 |

## Los 4 Ciclos de Negocio

### 1. Order-to-Cash (venta)
`crear pedido → procesar → picking → empaque → enviado → entregado → comprobante PDF`

El total del pedido lo calcula un trigger (`fn_recalcular_total_pedido_stmt`) y lo protege otro (`fn_proteger_total_pedido`): la aplicación nunca escribe ese campo. El total es **neto** (suma de subtotales − descuento) y el comprobante debe cuadrar con él.

### 2. Procure-to-Pay (compra)
`orden de compra → aprobación → recepción de mercancía → factura → cuenta por pagar → pago`

Con **separación de funciones**: quien crea la orden no puede aprobarla. La recepción admite entregas parciales acumulativas, sube stock de producto por bodega o el stock global de materia prima, y recalcula el **costo promedio ponderado** del material. El pago salda la cuenta y, por cascada de trigger, marca la factura como pagada.

### 3. Manufactura
`producto fabricado + BOM → verificar disponibilidad → orden de producción → iniciar (consume MP) → completar (produce + costea)`

Un producto es `comprado` o `fabricado`. Solo los fabricados admiten lista de materiales (BOM) y órdenes de producción, garantizado por triggers. Al iniciar se captura un **snapshot inmutable** del costo de cada material, de modo que el costo histórico de una orden no cambia cuando el promedio se mueve. Al completar se calculan mermas, costo total y costo unitario.

### 4. Calidad (devoluciones)
`RMA de cliente sobre pedido entregado → inspección por línea → devolución a proveedor del defectuoso → resolución`

La inspección decide el destino de cada línea: `apto_reventa` reingresa el stock, `defectuoso` alimenta la bandeja de devolución a proveedor, `rechazado` no toca nada. Constraints UNIQUE impiden que un mismo item defectuoso se devuelva dos veces.

## Stack Técnico

| Componente | Tecnología | Versión |
|------------|-----------|---------|
| Backend | Spring Boot | 3.2.2 |
| Lenguaje Backend | Java | 17 |
| Frontend | Angular (standalone components) | 17 |
| Base de Datos | PostgreSQL | 15+ |
| Autenticación | JWT (jjwt) | 0.11.5 |
| Documentación API | SpringDoc OpenAPI | 2.3.0 |
| PDF | iTextPDF | 8.x |
| Exportación | Apache POI (Excel) | 5.x |
| Gráficos | Chart.js | 4.x |

## Documentación del Proyecto

| Documento | Contenido |
|---|---|
| **[SETUP_COMPLETO.md](./SETUP_COMPLETO.md)** | Orden exacto para levantar la BD desde cero, script por script, con query de verificación |
| **[MATRIZ_ROLES.md](./MATRIZ_ROLES.md)** | Matriz de navegación de los 6 roles × 3 capas (navbar / guard de ruta / backend) |
| **[DEUDA_TECNICA.md](./DEUDA_TECNICA.md)** | Registro incremental por fase, inventario consolidado y trabajo futuro |
| **[DEMO_CHECKLIST.md](./DEMO_CHECKLIST.md)** | Guion de demostración recorriendo los 4 ciclos, indicando el usuario de cada parte |
| **[RESUMEN_EJECUTIVO.md](./RESUMEN_EJECUTIVO.md)** | Cifras y características distintivas para la sustentación |

## Requisitos Previos

- Java 17+
- Node.js 18+ y npm 9+
- PostgreSQL 15+
- Maven 3.9+
- Angular CLI 17: `npm install -g @angular/cli@17`

## Instalación

> **Para levantar la BD desde cero sigue [SETUP_COMPLETO.md](./SETUP_COMPLETO.md)**, que documenta el orden exacto (DDL base → fases 21–32 → backend → seed) e incluye la query de verificación. Lo de abajo es el resumen.

### 1. Crear la base de datos y aplicar los scripts

```sql
CREATE DATABASE mod_venta_inve;
```

Los scripts SQL son **versionados por fase e idempotentes**, y deben aplicarse **en orden**:

```bash
psql -U postgres -d mod_venta_inve -f marathon-backend/sql/fase00_ddl_base.sql
psql -U postgres -d mod_venta_inve -f marathon-backend/sql/fase21_ordenes_compra.sql
psql -U postgres -d mod_venta_inve -f marathon-backend/sql/fase22_recepcion_mercancia.sql
psql -U postgres -d mod_venta_inve -f marathon-backend/sql/fase23_factura_compra_cxp.sql
psql -U postgres -d mod_venta_inve -f marathon-backend/sql/fase24_devolucion_cliente.sql
psql -U postgres -d mod_venta_inve -f marathon-backend/sql/fase25_devolucion_proveedor.sql
psql -U postgres -d mod_venta_inve -f marathon-backend/sql/fase26_kardex_materia_prima.sql
psql -U postgres -d mod_venta_inve -f marathon-backend/sql/fase27_origen_producto_bom.sql
psql -U postgres -d mod_venta_inve -f marathon-backend/sql/fase28_ordenes_produccion.sql
psql -U postgres -d mod_venta_inve -f marathon-backend/sql/fase29_costeo_produccion.sql
psql -U postgres -d mod_venta_inve -f marathon-backend/sql/fase32_fixes.sql
```

> Hibernate **no** gestiona el esquema (`spring.jpa.hibernate.ddl-auto=none`). El esquema se crea y evoluciona solo con estos scripts. No hay Flyway ni Liquibase.

### 2. Configurar la conexión

Crear `marathon-backend/src/main/resources/application-local.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/mod_venta_inve
spring.datasource.username=postgres
spring.datasource.password=TU_PASSWORD

jwt.secret=clave-secreta-muy-larga-de-al-menos-32-caracteres
jwt.expiration=86400000

# API Key de Anthropic (módulo IA — opcional)
anthropic.api.key=sk-ant-...
```

> `application-local.properties` está en `.gitignore` — nunca se sube al repositorio.

### 3. Arrancar el backend

```bash
cd marathon-backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Arranca en `http://localhost:8080`. En el primer inicio, `DataInitializer` crea los 6 roles, los permisos y los 6 usuarios demo (incluido `admin@marathon.com`, que el seed requiere).

### 4. Cargar los datos de demostración

```bash
# Datos de negocio base (requiere que admin@marathon.com exista)
psql -U postgres -d mod_venta_inve -f marathon-backend/sql/seed_marathon_sports.sql

# Datos demo de compras, devoluciones y manufactura (idempotente)
psql -U postgres -d mod_venta_inve -f marathon-backend/sql/fase31_seed_demo_bloques_nuevos.sql
```

> El primer seed **no es idempotente**: ejecutarlo dos veces duplica datos. El de F31 sí lo es.

### 5. Arrancar el frontend

```bash
cd marathon-frontend
npm install
ng serve
```

Disponible en `http://localhost:4200`.

## Credenciales de Usuarios Demo

| Correo | Contraseña | Rol |
|--------|-----------|-----|
| `admin@marathon.com` | `Admin1234!` | Administrador |
| `supervisor@marathon.com` | `Demo1234!` | Supervisor E-Commerce |
| `bodega@marathon.com` | `Demo1234!` | Operador de Bodega |
| `pedidos@marathon.com` | `Demo1234!` | Operador de Pedidos |
| `compras@marathon.com` | `Demo1234!` | Encargado de Compras |
| `produccion@marathon.com` | `Demo1234!` | Encargado de Producción |

La matriz completa de qué ve y qué puede hacer cada rol está en **[MATRIZ_ROLES.md](./MATRIZ_ROLES.md)**.

## Módulos del Sistema

### Base (F1–F20)
| Módulo | Descripción |
|--------|-------------|
| **Autenticación** | Login JWT, perfil de usuario |
| **Datos Maestros** | Ciudades, categorías, unidades de medida |
| **Usuarios y Roles** | CRUD de usuarios, roles y permisos |
| **Proveedores y Bodegas** | Catálogo de proveedores y bodegas por ciudad |
| **Productos** | Catálogo, origen (comprado/fabricado) y BOM |
| **Inventario** | Stock por bodega, movimientos e historial auditado |
| **Clientes** | CRUD de clientes |
| **Pedidos** | Creación, estados, pedidos especiales (personalizado/regalo/corporativo) |
| **Comprobantes** | Comprobantes internos con PDF descargable |
| **Picking** | Recolección línea por línea |
| **Empaque y Despacho** | HU, transportista, región destino |
| **Dashboard** | KPIs segmentados por rol |
| **Reportes** | 6 reportes con exportación Excel y PDF |
| **Asistente IA** | Consultas en lenguaje natural (SELECT-only) |
| **Auditoría** | Log de acciones e historial de inventario |

### Compras (F21–F23)
| Módulo | Descripción |
|--------|-------------|
| **Órdenes de Compra** | Ciclo borrador → aprobación → recepción, con separación de funciones |
| **Recepción de Mercancía** | Entregas parciales acumulativas, control de defectuosos |
| **Facturas y Cuentas por Pagar** | Factura del proveedor, CxP y pagos con cascada de estado |

### Calidad (F24–F25)
| Módulo | Descripción |
|--------|-------------|
| **Devoluciones de Cliente (RMA)** | Solicitud, inspección por línea y reembolso |
| **Devoluciones a Proveedor** | Bandeja de defectuosos de RMA y de recepción, resolución |

### Manufactura (F26–F30)
| Módulo | Descripción |
|--------|-------------|
| **Materia Prima** | Catálogo, stock global, kardex de movimientos y costo promedio |
| **Órdenes de Producción** | Consumo de MP según BOM, mermas y alta de producto terminado |
| **Análisis de Costos** | Costeo por orden, costo estimado por BOM, fabricar vs comprar |
| **Dashboard de Producción** | 7 KPIs, top productos, distribución por estado, semáforo de merma |

## Arquitectura de Seguridad

Cuatro capas, con la base de datos como última palabra:

1. **Frontend** — `authGuard` valida sesión y `rolGuard` valida rol por ruta. Si un rol llega a una ruta prohibida se le redirige a su dashboard con un aviso. Esto evita ofrecer pantallas que fallarían.
2. **Backend** — `SecurityConfig` restringe cada endpoint por rol. En esa configuración **el orden importa**: las reglas específicas van antes de las generales, porque gana la primera coincidencia.
3. **Integridad de la base** — constraints CHECK, columnas GENERATED y triggers de protección impiden estados inválidos incluso si se escribiera saltándose la aplicación.
4. **Privilegios de la base (F34 + F37)** — cada rol se conecta a PostgreSQL con **su propio usuario**: un operador de bodega llega a `mod_venta_inve` como `usr_bodega_marathon` y solo puede lo que sus `GRANT` permiten. La capa 4 no depende de que las tres anteriores funcionen. Detalle en `SEGURIDAD_ROLES.md`.

## Documentación de la API

Swagger UI: `http://localhost:8080/swagger-ui.html`

## Nota sobre el Módulo IA

El asistente IA (F18) requiere una API key de Anthropic en `application-local.properties`. Sin la clave el módulo muestra un error al consultar; el resto del sistema funciona con normalidad. El servicio valida que el SQL generado sea **solo SELECT** (rechaza INSERT/UPDATE/DELETE/DROP/TRUNCATE/ALTER/CREATE) y limita a 500 resultados por consulta.
