# Requirements Document

**Fase 31 — Consolidación · Marathon Sports**

## Introduction

La Fase 31 cierra el proyecto Marathon Sports antes de la Fase 32 (Cierre de Deuda Técnica y Verificación Integral Final). No agrega módulos de negocio: **consolida** lo construido en las fases 21–30.

El problema concreto que resuelve: las 37 tablas existen y los scripts DDL de F21–F29 están aplicados, pero **las tablas de los bloques nuevos (Compras y Manufactura) están vacías**. Un evaluador que hoy inicie sesión como Encargado de Compras o Encargado de Producción encuentra pantallas sin datos, dashboards en cero y reportes sin filas. Los ciclos Procure-to-Pay, Manufactura y Calidad funcionan pero no se pueden demostrar sin capturar datos a mano.

La fase entrega cinco resultados:

1. **Auditoría de línea base** (solo lectura) de las tablas de los bloques nuevos, antes de escribir nada.
2. **Un seed de demostración permanente e idempotente** (`fase31_seed_demo_bloques_nuevos.sql`) que puebla los tres ciclos con datos coherentes y respetando todas las columnas calculadas y triggers de integridad.
3. **Coherencia de la experiencia por rol**: cada uno de los 6 roles ve en el dashboard y en la navegación exactamente lo que le corresponde, alineado con la autorización del backend.
4. **Documentación de acceso y setup**: `MATRIZ_ROLES.md` nuevo y `SETUP_COMPLETO.md` ampliado.
5. **Inventario de deuda técnica** consolidado, con una única fuente de verdad, para que la Fase 32 arranque con el panorama completo.

La decisión de diseño de primer orden que esta fase debe resolver de forma explícita es **cómo sembrar recepciones de mercancía sin descuadrar el costo promedio ponderado de la materia prima** (Requisito 3).

## Glossary

- **Seed_Demo**: el script `marathon-backend/sql/fase31_seed_demo_bloques_nuevos.sql`, ejecutable por `psql`, permanente e idempotente.
- **Seed_Base**: el conjunto de datos ya cargados por `marathon-backend/sql/seed_marathon_sports.sql` (88 ciudades, 3 categorías, 105 productos, 6 proveedores, 20 bodegas, 265 filas de inventario, 40 clientes, 25 pedidos, 68 detalles de pedido). Es intocable.
- **Tablas_Bloques_Nuevos**: las 17 tablas de los bloques 8 y 9: `materia_prima`, `orden_compra`, `orden_compra_detalle`, `recepcion_mercancia`, `recepcion_mercancia_detalle`, `factura_compra`, `cuenta_por_pagar`, `pago_proveedor`, `solicitud_devolucion`, `solicitud_devolucion_detalle`, `reembolso_cliente`, `devolucion_proveedor`, `devolucion_proveedor_detalle`, `movimiento_materia_prima`, `lista_materiales`, `orden_produccion`, `orden_produccion_consumo`.
- **Linea_Base**: el conjunto de conteos de filas de las Tablas_Bloques_Nuevos más el conteo de productos con `origen='fabricado'`, medido ANTES de ejecutar el Seed_Demo.
- **Auditor_BD**: el procedimiento de consulta de solo lectura, ejecutado vía el servidor MCP `postgres-marathon`, que produce la Linea_Base y la verificación posterior.
- **Materia_Prima_De_Estreno**: una fila de `materia_prima` que, en el instante previo a una recepción, cumple `stock_actual = 0 AND costo_unitario_promedio = 0`.
- **Costo_Promedio_Ponderado**: el valor de `materia_prima.costo_unitario_promedio`, definido por la fórmula `((stock_anterior × costo_anterior) + (cantidad_buena × precio_compra)) / (stock_anterior + cantidad_buena)`, cuya única implementación autorizada en la aplicación vive en `RecepcionMercanciaService.java`.
- **Marca_Demo**: el prefijo literal `DEMO-F31-` usado en campos de texto de cada fila creada por el Seed_Demo, que permite identificarla, verificarla y hacer la comprobación de idempotencia.
- **Dashboard_Rol**: el componente `DashboardComponent` (`marathon-frontend/src/app/modules/dashboard/dashboard.component.ts`), en su comportamiento condicionado por rol.
- **Matriz_Roles**: el archivo `MATRIZ_ROLES.md` en la raíz del repositorio.
- **Guard_Rutas**: el conjunto formado por `rolGuard` (`marathon-frontend/src/app/core/guards/rol.guard.ts`) y las declaraciones `data.rol` / `data.roles` de `marathon-frontend/src/app/app.routes.ts`.
- **Navbar**: el componente `NavbarComponent` (`marathon-frontend/src/app/shared/components/navbar/navbar.component.ts`).
- **Autorizacion_Backend**: las reglas de `authorizeHttpRequests` de `marathon-backend/src/main/java/com/marathon/config/SecurityConfig.java`.
- **Inicializador_Usuarios**: la clase `marathon-backend/src/main/java/com/marathon/config/DataInitializer.java`.
- **Documento_Setup**: el archivo `SETUP_COMPLETO.md` en la raíz del repositorio.
- **Inventario_Deuda**: la sección `## Fase 31 — Consolidación` a agregar en `DEUDA_TECNICA.md` (raíz).
- **Steering_Proyecto**: el archivo `.kiro/steering/project.md`.
- **Los_Seis_Roles**: Administrador, Supervisor E-Commerce, Operador de Bodega, Operador de Pedidos, Encargado de Compras, Encargado de Producción.

---

## Requirements

### Requirement 1: Marcar el inicio de la fase en el steering

**User Story:** Como responsable del proyecto, quiero que el steering refleje que la Fase 30 quedó completada y que la Fase 31 está en progreso, para que cualquier sesión de trabajo posterior conozca el estado real del proyecto.

#### Acceptance Criteria

1. WHEN comienza la ejecución de la Fase 31, THE Steering_Proyecto SHALL registrar la Fase 30 con estado `✅ Completada`.
2. WHEN comienza la ejecución de la Fase 31, THE Steering_Proyecto SHALL registrar la Fase 31 con nombre `Consolidación` y estado `🔄 En progreso`.
3. THE Steering_Proyecto SHALL conservar sin modificación todas las notas de fases anteriores a la Fase 31.

---

### Requirement 2: Auditoría de línea base antes de escribir datos

**User Story:** Como responsable del proyecto, quiero medir cuántas filas hay hoy en las tablas de los bloques nuevos antes de sembrar, para poder demostrar con números qué agregó exactamente esta fase.

#### Acceptance Criteria

1. WHEN se ejecuta la auditoría previa, THE Auditor_BD SHALL emitir el conteo de filas de cada una de las 17 tablas listadas en Tablas_Bloques_Nuevos.
2. WHEN se ejecuta la auditoría previa, THE Auditor_BD SHALL emitir el conteo de filas de `producto` con `origen = 'fabricado'`.
3. THE Auditor_BD SHALL emitir la Linea_Base usando exclusivamente sentencias `SELECT`.
4. WHEN la auditoría previa termina, THE Auditor_BD SHALL registrar la Linea_Base en el documento de resultados de la fase antes de que se ejecute el Seed_Demo.
5. IF la auditoría previa detecta filas preexistentes con Marca_Demo en cualquiera de las Tablas_Bloques_Nuevos, THEN THE Auditor_BD SHALL reportar esas filas como una ejecución previa del Seed_Demo.

---

### Requirement 3: Decisión sobre la siembra de recepciones y el costo promedio ponderado

**User Story:** Como responsable del proyecto, quiero que el seed deje datos reales de costeo sin arriesgar la integridad del costo promedio ponderado de la materia prima, para que los reportes de costos de F29 y F30 muestren cifras verificables y correctas.

> **Contexto de la decisión.** La única implementación autorizada del Costo_Promedio_Ponderado vive en `RecepcionMercanciaService.java` (Java), no en SQL. Sembrar recepciones desde SQL obliga a replicar esa fórmula. Se evaluaron tres alternativas: (A) replicar la fórmula completa en SQL para cualquier materia prima; (B) no sembrar recepciones y dejar las órdenes en `aprobada`, documentando que la recepción se hace desde la interfaz; (A-acotada) sembrar recepciones únicamente sobre Materia_Prima_De_Estreno, caso en el que la fórmula ponderada se reduce algebraicamente a `precio_compra` y no existe posibilidad de descuadre.

#### Acceptance Criteria

1. THE Seed_Demo SHALL registrar recepciones de materia prima únicamente sobre filas que sean Materia_Prima_De_Estreno en el instante previo a la recepción.
2. WHEN el Seed_Demo registra una recepción sobre una Materia_Prima_De_Estreno con cantidad buena `C` y precio unitario `P`, THE Seed_Demo SHALL dejar `materia_prima.costo_unitario_promedio` con valor exactamente `P` y `materia_prima.stock_actual` con valor exactamente `C`.
3. IF una materia prima destino de una recepción del Seed_Demo tiene `stock_actual > 0` o `costo_unitario_promedio > 0` en el instante previo, THEN THE Seed_Demo SHALL omitir esa recepción y dejar la orden de compra asociada en estado `aprobada`.
4. WHEN el Seed_Demo omite una recepción por la condición del criterio 3, THE Seed_Demo SHALL emitir un mensaje en español que identifique la materia prima omitida y el motivo.
5. WHEN el Seed_Demo registra una recepción, THE Seed_Demo SHALL insertar la fila correspondiente en `movimiento_materia_prima` con `tipo_movimiento = 'entrada_compra'`, `stock_anterior = 0`, `stock_nuevo = C` y la referencia `id_recepcion` de esa recepción.
6. WHEN el Seed_Demo termina, THE Seed_Demo SHALL emitir una comparación por materia prima entre el `costo_unitario_promedio` almacenado y el resultado de recalcular la fórmula del Costo_Promedio_Ponderado a partir de las filas de `movimiento_materia_prima` con `tipo_movimiento = 'entrada_compra'` y los precios de `orden_compra_detalle`.
7. WHERE el Seed_Demo omite recepciones, THE Inventario_Deuda SHALL registrar qué materias primas quedaron sin costo y que su recepción se completa desde la interfaz.

---

### Requirement 4: Idempotencia y permanencia del seed de demostración

**User Story:** Como responsable del proyecto, quiero poder ejecutar el seed de demostración más de una vez sin duplicar datos ni provocar errores, para que el procedimiento de setup sea repetible en cualquier máquina.

#### Acceptance Criteria

1. THE Seed_Demo SHALL residir en `marathon-backend/sql/fase31_seed_demo_bloques_nuevos.sql`.
2. WHEN el Seed_Demo se ejecuta por segunda vez consecutiva sobre la misma base de datos, THE Seed_Demo SHALL terminar con código de salida 0.
3. WHEN el Seed_Demo se ejecuta por segunda vez consecutiva sobre la misma base de datos, THE Seed_Demo SHALL dejar los conteos de filas de las 17 Tablas_Bloques_Nuevos idénticos a los obtenidos tras la primera ejecución.
4. THE Seed_Demo SHALL condicionar cada inserción mediante `WHERE NOT EXISTS` o `ON CONFLICT DO NOTHING` sobre una clave que identifique la fila de forma única.
5. THE Seed_Demo SHALL escribir el prefijo Marca_Demo en un campo de texto de cada fila que cree, y THE Seed_Demo SHALL documentar en un comentario de cabecera qué campo cumple ese papel en cada tabla.
6. THE Seed_Demo SHALL dejar sin modificación las filas del Seed_Base en `ciudad`, `categoria`, `producto`, `proveedor`, `bodega`, `cliente`, `pedido` y `detalle_pedido`, con la única excepción de `producto.origen` sobre los productos fabricados que la Fase 31 crea.
7. THE Seed_Demo SHALL usar sentencias `INSERT`, `UPDATE` y `SELECT` exclusivamente, sin `DELETE`, `TRUNCATE`, `DROP` ni `ALTER`.
8. WHEN el Seed_Demo necesita crear un producto fabricado, THE Seed_Demo SHALL insertar un producto nuevo con código propio con Marca_Demo, en lugar de convertir a `fabricado` un producto del Seed_Base.
9. IF el Inicializador_Usuarios no ha creado todavía el usuario `admin@marathon.com`, THEN THE Seed_Demo SHALL abortar con un mensaje en español que indique que se debe arrancar el backend primero.

---

### Requirement 5: Materias primas y productos fabricados de demostración

**User Story:** Como Encargado de Producción, quiero encontrar un catálogo de materias primas y productos fabricados propios de una tienda deportiva, para poder recorrer el módulo de manufactura con datos que tengan sentido.

#### Acceptance Criteria

1. THE Seed_Demo SHALL crear entre 8 y 10 filas en `materia_prima`, con nombres de insumos textiles y de confección deportiva.
2. THE Seed_Demo SHALL asignar a cada materia prima creada un `id_unidad_medida` existente y un `stock_minimo` mayor que cero.
3. THE Seed_Demo SHALL crear entre 3 y 4 filas en `producto` con `origen = 'fabricado'`, `codigo` con Marca_Demo, `precio_compra` y `precio_venta` mayores que cero, y `id_categoria` e `id_unidad` existentes.
4. WHEN el Seed_Demo crea un producto con `origen = 'fabricado'`, THE Seed_Demo SHALL crear para ese producto al menos dos filas en `lista_materiales` con `estado = 'activo'` y `cantidad_necesaria` mayor que cero.
5. THE Seed_Demo SHALL insertar las filas de `lista_materiales` después de que exista el producto con `origen = 'fabricado'`, de modo que el trigger `trg_validar_bom_producto_fabricado` no rechace la inserción.
6. THE Seed_Demo SHALL dejar al menos una materia prima con `stock_actual` menor que su `stock_minimo`, para que el indicador de materia prima bajo mínimo del Dashboard_Rol devuelva un valor mayor que cero.

---

### Requirement 6: Ciclo Procure-to-Pay de demostración

**User Story:** Como Encargado de Compras, quiero encontrar órdenes de compra en distintos estados, con recepción, factura, cuenta por pagar y pagos, para poder demostrar el ciclo Procure-to-Pay completo sin capturar datos a mano.

#### Acceptance Criteria

1. THE Seed_Demo SHALL crear entre 3 y 4 filas en `orden_compra`, cada una con al menos dos filas en `orden_compra_detalle`.
2. THE Seed_Demo SHALL dejar al menos una orden de compra en estado `recibida_completa`, al menos una en estado `aprobada` y al menos una en estado `borrador`.
3. THE Seed_Demo SHALL omitir las columnas `orden_compra.total` y `orden_compra_detalle.subtotal` en toda sentencia `INSERT` y `UPDATE`, dejando su valor al trigger y a la definición `GENERATED`.
4. WHEN el Seed_Demo crea una fila en `orden_compra_detalle`, THE Seed_Demo SHALL informar `id_producto` o `id_materia_prima`, nunca ambos y nunca ninguno, en coherencia con el valor de `tipo_item`.
5. WHEN el Seed_Demo registra la recepción de la orden en estado `recibida_completa`, THE Seed_Demo SHALL acumular la cantidad recibida en `orden_compra_detalle.cantidad_recibida` sin que el valor resultante supere `orden_compra_detalle.cantidad`.
6. THE Seed_Demo SHALL crear entre 1 y 2 filas en `factura_compra`, cada una asociada a una orden de compra que tenga al menos una recepción registrada.
7. THE Seed_Demo SHALL crear una fila en `cuenta_por_pagar` por cada factura de compra creada, con `monto_total` igual al `total` de esa factura.
8. THE Seed_Demo SHALL omitir las columnas `factura_compra.total`, `cuenta_por_pagar.monto_pagado` y `cuenta_por_pagar.saldo_pendiente` en toda sentencia `INSERT` y `UPDATE`.
9. THE Seed_Demo SHALL registrar en `pago_proveedor` los pagos necesarios para dejar una cuenta por pagar con `saldo_pendiente = 0` y otra con `saldo_pendiente` mayor que cero y menor que `monto_total`.
10. WHEN el Seed_Demo registra un pago, THE Seed_Demo SHALL usar un `monto` menor o igual al `saldo_pendiente` vigente de la cuenta por pagar destino.
11. THE Seed_Demo SHALL dejar al menos una fila en `cuenta_por_pagar` con `fecha_vencimiento` anterior a la fecha actual y `saldo_pendiente` mayor que cero, para que el indicador de cuentas vencidas del Dashboard_Rol devuelva un valor mayor que cero.

---

### Requirement 7: Ciclo de calidad de demostración

**User Story:** Como Operador de Bodega, quiero encontrar solicitudes de devolución de cliente ya inspeccionadas y una devolución a proveedor derivada de ellas, para poder demostrar el ciclo de calidad de punta a punta.

#### Acceptance Criteria

1. THE Seed_Demo SHALL crear entre 2 y 3 filas en `solicitud_devolucion`, todas referidas a pedidos del Seed_Base cuyo `estado` sea `entregado`.
2. IF no existe ningún pedido del Seed_Base con `estado = 'entregado'`, THEN THE Seed_Demo SHALL omitir la creación de solicitudes de devolución y emitir un mensaje en español que indique el motivo.
3. WHEN el Seed_Demo crea una fila en `solicitud_devolucion_detalle`, THE Seed_Demo SHALL usar una `cantidad_devuelta` menor o igual a la `cantidad` de la fila de `detalle_pedido` referida.
4. THE Seed_Demo SHALL dejar entre las filas de `solicitud_devolucion_detalle` creadas al menos una con `resultado_inspeccion = 'apto_reventa'`, al menos una con `resultado_inspeccion = 'defectuoso'` y al menos una con `resultado_inspeccion = 'rechazado'`.
5. WHEN el Seed_Demo marca una línea de devolución como `apto_reventa`, THE Seed_Demo SHALL incrementar `inventario.cantidad` del producto en la bodega correspondiente y THE Seed_Demo SHALL fijar `app.current_user_id` en la sesión antes de ese `UPDATE`.
6. THE Seed_Demo SHALL crear una fila en `devolucion_proveedor` con al menos una fila en `devolucion_proveedor_detalle` de `origen = 'rma_cliente'` que referencie una línea con `resultado_inspeccion = 'defectuoso'` creada por el propio Seed_Demo.
7. THE Seed_Demo SHALL referenciar cada `id_solicitud_devolucion_detalle` en `devolucion_proveedor_detalle` una sola vez, respetando la restricción `UNIQUE` de esa columna.

---

### Requirement 8: Ciclo de manufactura de demostración con costeo real

**User Story:** Como Encargado de Producción, quiero encontrar una orden de producción completada con su costeo real y otra planificada, para poder demostrar el consumo de materia prima, la merma y el costo unitario producido.

#### Acceptance Criteria

1. THE Seed_Demo SHALL crear entre 1 y 2 filas en `orden_produccion`, todas sobre productos con `origen = 'fabricado'` creados por el propio Seed_Demo.
2. THE Seed_Demo SHALL dejar al menos una orden de producción en estado `completada` y, cuando cree dos, THE Seed_Demo SHALL dejar la segunda en estado `planificada`.
3. WHEN el Seed_Demo crea la orden de producción en estado `completada`, THE Seed_Demo SHALL crear una fila en `orden_produccion_consumo` por cada materia prima del `lista_materiales` activo de ese producto, con `cantidad_teorica` igual a `cantidad_necesaria` multiplicada por `cantidad_planificada`.
4. WHEN el Seed_Demo crea una fila en `orden_produccion_consumo`, THE Seed_Demo SHALL fijar `costo_unitario_snapshot` con el valor de `materia_prima.costo_unitario_promedio` vigente en ese instante.
5. THE Seed_Demo SHALL omitir las columnas `orden_produccion_consumo.merma`, `orden_produccion_consumo.costo_linea`, `orden_produccion.costo_total` y `orden_produccion.costo_unitario_producido` en toda sentencia `INSERT` y `UPDATE`.
6. THE Seed_Demo SHALL fijar `orden_produccion.costo_materia_prima` invocando la función `fn_set_costo_materia_prima_op` con el identificador de la orden, en lugar de un `UPDATE` directo sobre esa columna.
7. THE Seed_Demo SHALL dejar `orden_produccion.cantidad_producida` con un valor mayor que cero y menor o igual a `cantidad_planificada` en la orden en estado `completada`.
8. THE Seed_Demo SHALL declarar `cantidad_real` distinta de `cantidad_teorica` en al menos una fila de `orden_produccion_consumo`, de modo que la merma calculada sea distinta de cero.
9. WHEN el Seed_Demo consume materia prima para una orden de producción, THE Seed_Demo SHALL descontar la cantidad de `materia_prima.stock_actual` y THE Seed_Demo SHALL insertar la fila correspondiente en `movimiento_materia_prima` con `tipo_movimiento = 'salida_produccion'` y la referencia `id_orden_produccion`.
10. WHEN el Seed_Demo completa una orden de producción, THE Seed_Demo SHALL incrementar `inventario.cantidad` del producto fabricado en la bodega destino y THE Seed_Demo SHALL fijar `app.current_user_id` en la sesión antes de ese `UPDATE`.
11. THE Seed_Demo SHALL dejar `materia_prima.stock_actual` con valor mayor o igual a cero en todas las filas al terminar, respetando el `CHECK` de esa columna.

---

### Requirement 9: Verificación posterior y comparación con la línea base

**User Story:** Como responsable del proyecto, quiero comparar los conteos posteriores al seed contra la línea base y ver los valores de costeo resultantes, para confirmar con números que el seed dejó datos coherentes.

#### Acceptance Criteria

1. WHEN el Seed_Demo ha terminado, THE Auditor_BD SHALL emitir el conteo de filas de cada una de las 17 Tablas_Bloques_Nuevos y el conteo de productos con `origen = 'fabricado'`.
2. WHEN se emiten los conteos posteriores, THE Auditor_BD SHALL presentar por tabla el valor de la Linea_Base, el valor posterior y la diferencia entre ambos.
3. THE Auditor_BD SHALL verificar que el conteo posterior de cada una de las 17 Tablas_Bloques_Nuevos es mayor o igual al de la Linea_Base.
4. THE Auditor_BD SHALL verificar que los conteos de `ciudad`, `categoria`, `proveedor`, `bodega`, `cliente`, `pedido` y `detalle_pedido` son iguales a los del Seed_Base.
5. THE Auditor_BD SHALL emitir, por cada orden de producción creada, los valores de `costo_materia_prima`, `costo_total` y `costo_unitario_producido`.
6. THE Auditor_BD SHALL verificar que `orden_produccion.costo_materia_prima` es igual a la suma de `orden_produccion_consumo.costo_linea` de esa orden.
7. THE Auditor_BD SHALL verificar que `cuenta_por_pagar.saldo_pendiente` es igual a `monto_total` menos la suma de los `pago_proveedor.monto` asociados, en cada cuenta por pagar creada.
8. IF alguna verificación de los criterios 3 a 7 falla, THEN THE Auditor_BD SHALL reportar la tabla o entidad afectada, el valor esperado y el valor obtenido.

---

### Requirement 10: Dashboard coherente por rol

**User Story:** Como usuario de cualquiera de los seis roles, quiero que el dashboard me muestre los indicadores y accesos de mi función y ninguno ajeno a ella, para no ver tarjetas vacías, errores de permiso ni información que no me corresponde.

#### Acceptance Criteria

1. WHILE la sesión activa tiene el rol Encargado de Compras, THE Dashboard_Rol SHALL mostrar el número de órdenes de compra en estado `pendiente_aprobacion`, el número de cuentas por pagar en estado `vencida` y accesos directos a las rutas `/compras` y `/cuentas-por-pagar`.
2. WHILE la sesión activa tiene el rol Encargado de Producción, THE Dashboard_Rol SHALL mostrar el número de órdenes de producción en estado `en_proceso`, el número de materias primas con `stock_actual` menor que `stock_minimo`, el número de productos con `origen = 'fabricado'` y accesos directos a las rutas `/produccion`, `/materia-prima` y `/produccion/dashboard`.
3. WHILE la sesión activa tiene el rol Encargado de Producción, THE Dashboard_Rol SHALL omitir los indicadores de ventas, el gráfico de ventas por día, el gráfico de pedidos por estado y la lista de productos más vendidos.
4. WHILE la sesión activa tiene el rol Encargado de Compras, THE Dashboard_Rol SHALL omitir los indicadores de ventas, el gráfico de ventas por día, el gráfico de pedidos por estado y la lista de productos más vendidos.
5. THE Dashboard_Rol SHALL invocar un endpoint únicamente cuando la Autorizacion_Backend concede acceso a ese endpoint para al menos un rol de la sesión activa.
6. WHEN se carga el Dashboard_Rol con una sesión de cada uno de Los_Seis_Roles, THE Dashboard_Rol SHALL completar la carga sin que ninguna petición devuelva estado HTTP 403.
7. WHEN el Dashboard_Rol se carga por primera vez, THE Dashboard_Rol SHALL solicitar todos los indicadores que corresponden a los roles de la sesión activa, sin requerir una acción de recarga manual.
8. WHERE un rol de Los_Seis_Roles no tiene indicadores propios definidos, THE Dashboard_Rol SHALL mostrar los accesos directos a los módulos que la Autorizacion_Backend y el Guard_Rutas le conceden.

---

### Requirement 11: Auditoría de navegación por rol y matriz de acceso

**User Story:** Como responsable del proyecto, quiero que la navegación visible, los guards de ruta y la autorización del backend digan lo mismo para cada rol, y que exista un documento único con esa matriz, para que nadie vea un enlace que lo lleve a un error de permiso.

#### Acceptance Criteria

1. THE Matriz_Roles SHALL existir en la raíz del repositorio con el nombre `MATRIZ_ROLES.md`.
2. THE Matriz_Roles SHALL contener una fila por cada ruta declarada en `app.routes.ts` y una columna por cada uno de Los_Seis_Roles.
3. THE Matriz_Roles SHALL indicar, por cada ruta y rol, si el Navbar muestra el enlace, si el Guard_Rutas concede el acceso y si la Autorizacion_Backend concede acceso a los endpoints que esa ruta consume.
4. WHEN la auditoría encuentra una ruta cuyo enlace el Navbar muestra a un rol al que el Guard_Rutas niega el acceso, THE Navbar SHALL dejar de mostrar ese enlace a ese rol.
5. WHEN la auditoría encuentra una ruta a la que el Guard_Rutas concede acceso a un rol para el que la Autorizacion_Backend niega los endpoints que esa ruta consume, THE Guard_Rutas SHALL restringir esa ruta al conjunto de roles que la Autorizacion_Backend autoriza.
6. THE Matriz_Roles SHALL listar las desalineaciones encontradas y la corrección aplicada a cada una.
7. THE Matriz_Roles SHALL usar los nombres de rol exactamente como están almacenados en la columna `rol.nombre`.

---

### Requirement 12: Usuarios de demostración verificados

**User Story:** Como evaluador, quiero poder iniciar sesión con un usuario de cada uno de los seis roles usando credenciales conocidas, para recorrer el sistema desde cada perspectiva.

#### Acceptance Criteria

1. WHEN arranca el backend, THE Inicializador_Usuarios SHALL garantizar la existencia de los usuarios `admin@marathon.com`, `supervisor@marathon.com`, `bodega@marathon.com`, `pedidos@marathon.com`, `compras@marathon.com` y `produccion@marathon.com`.
2. THE Inicializador_Usuarios SHALL verificar la existencia de cada usuario de demostración de forma individual por su correo, con independencia de la existencia de los demás.
3. WHEN el Inicializador_Usuarios crea el usuario `admin@marathon.com`, THE Inicializador_Usuarios SHALL asignarle la contraseña `Admin1234!` cifrada con BCrypt.
4. WHEN el Inicializador_Usuarios crea cualquier usuario de demostración distinto de `admin@marathon.com`, THE Inicializador_Usuarios SHALL asignarle la contraseña `Demo1234!` cifrada con BCrypt.
5. WHEN el Inicializador_Usuarios garantiza un usuario de demostración, THE Inicializador_Usuarios SHALL asignarle la fila correspondiente en `usuario_rol` con el rol que le corresponde y `estado = 'activo'`.
6. IF el usuario `admin@marathon.com` no existe y la base de datos ya contiene datos de negocio, THEN THE Inicializador_Usuarios SHALL crear ese usuario con su rol Administrador.
7. WHEN la verificación de usuarios termina, THE Auditor_BD SHALL emitir el correo, el estado y el nombre de rol de cada uno de los seis usuarios de demostración.

---

### Requirement 13: Documento de setup actualizado

**User Story:** Como persona que levanta el proyecto en una máquina nueva, quiero que la guía de setup incluya el seed de demostración y me diga qué conteos esperar en los módulos nuevos, para saber si la instalación quedó completa.

#### Acceptance Criteria

1. THE Documento_Setup SHALL incluir la ejecución del Seed_Demo como paso posterior a la ejecución de `seed_marathon_sports.sql`.
2. THE Documento_Setup SHALL indicar el comando `psql` completo para ejecutar el Seed_Demo, con la ruta `marathon-backend/sql/fase31_seed_demo_bloques_nuevos.sql`.
3. THE Documento_Setup SHALL indicar que el Seed_Demo es idempotente y que su reejecución no duplica datos.
4. THE Documento_Setup SHALL ampliar su consulta de verificación final con el conteo esperado de filas de cada una de las 17 Tablas_Bloques_Nuevos tras el Seed_Demo.
5. THE Documento_Setup SHALL ampliar su tabla de resumen del orden de ejecución con la fila correspondiente al Seed_Demo.
6. THE Documento_Setup SHALL conservar sin modificación los pasos 1 a 13 existentes.

---

### Requirement 14: Inventario de deuda técnica y fuente única de verdad

**User Story:** Como responsable de la Fase 32, quiero un inventario que distinga la deuda abierta de la ya resuelta y saber cuál archivo de deuda es la fuente de verdad, para arrancar el cierre técnico sin reconstruir el panorama.

#### Acceptance Criteria

1. THE Inventario_Deuda SHALL existir como sección `## Fase 31 — Consolidación` al final de `DEUDA_TECNICA.md`.
2. THE Inventario_Deuda SHALL clasificar cada elemento de deuda acumulada de las fases 21 a 30 como abierto o resuelto.
3. THE Inventario_Deuda SHALL registrar como pendiente prioritario el defecto de `fn_proteger_total_pedido`, cuya condición `pg_trigger_depth() = 0` nunca se cumple dentro de un trigger y por tanto no protege `pedido.total`.
4. THE Inventario_Deuda SHALL asignar a cada elemento abierto una prioridad de valor `alta`, `media` o `baja`.
5. THE Inventario_Deuda SHALL declarar `DEUDA_TECNICA.md` de la raíz como fuente de verdad de la deuda técnica del proyecto.
6. WHEN la reconciliación encuentra en `.kiro/steering/deuda-tecnica.md` un elemento ausente de `DEUDA_TECNICA.md`, THE Inventario_Deuda SHALL incorporar ese elemento.
7. WHEN la reconciliación encuentra en `.kiro/steering/deuda-tecnica.md` una afirmación que contradice el estado verificado de la base de datos, THE Inventario_Deuda SHALL registrar la contradicción y el estado verificado.
8. WHEN la reconciliación termina, THE archivo `.kiro/steering/deuda-tecnica.md` SHALL remitir a `DEUDA_TECNICA.md` como fuente de verdad y conservar únicamente la información de entorno de la máquina de desarrollo.
9. THE Inventario_Deuda SHALL registrar la divergencia entre el valor real `spring.jpa.hibernate.ddl-auto=none` de `application.properties` y el valor `validate` documentado en `.kiro/steering/stack.md`.
10. THE Inventario_Deuda SHALL registrar las limitaciones asumidas por el Seed_Demo, incluidas las recepciones omitidas por la regla de Materia_Prima_De_Estreno.

---

### Requirement 15: Cierre de la fase en el steering

**User Story:** Como responsable del proyecto, quiero que el steering registre la Fase 31 como completada y anuncie la Fase 32, para que la siguiente sesión de trabajo arranque con el contexto correcto.

#### Acceptance Criteria

1. WHEN todos los entregables de la Fase 31 están terminados, THE Steering_Proyecto SHALL registrar la Fase 31 con estado `✅ Completada`.
2. THE Steering_Proyecto SHALL registrar la Fase 32 con nombre `Cierre de Deuda Técnica y Verificación Integral Final` como fase siguiente.
3. THE Steering_Proyecto SHALL registrar que el proyecto va por la fase 31 de 32.
4. THE Steering_Proyecto SHALL registrar que el sistema cubre los ciclos de negocio Order-to-Cash, Procure-to-Pay y Manufactura, más el ciclo de calidad.
5. THE Steering_Proyecto SHALL registrar en la nota de la Fase 31 la decisión adoptada sobre la siembra de recepciones y el Costo_Promedio_Ponderado.
6. WHERE la Fase 31 corrige el Navbar, el Guard_Rutas o el Dashboard_Rol, THE Steering_Proyecto SHALL registrar esas correcciones en la nota de la fase.

