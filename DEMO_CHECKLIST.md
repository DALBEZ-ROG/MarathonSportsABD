# Guion de Demostración — Marathon Sports

Sistema de Gestión de Pedidos, Compras, Manufactura y Calidad
Proyecto de Administración de Bases de Datos · 32 fases · 37 tablas · 24 triggers

**Duración total: 40–45 minutos.** Si el tiempo aprieta, los ciclos 1 y 3 son los imprescindibles: el primero muestra el trigger de integridad, el tercero es lo que distingue al proyecto.

---

## Preparación previa

- [ ] Backend corriendo en `http://localhost:8080`, arrancado con `scripts/cifrado/iniciar_backend.ps1`. **No vale `mvn spring-boot:run` a secas**: ese script es el que mete en el entorno del proceso la clave de cifrado (sin ella los contactos de cliente y proveedor salen vacíos) y la credencial de respaldo (sin ella la pantalla de Respaldos se declara «no disponible»)
- [ ] Frontend corriendo en `http://localhost:4300`
- [ ] BD con `seed_marathon_sports.sql` **y** `fase31_seed_demo_bloques_nuevos.sql` aplicados
- [ ] Una terminal `psql` abierta en `mod_venta_inve` para las pruebas de integridad en BD
- [ ] Navegador sin sesión previa
- [ ] Credenciales de los 6 usuarios a mano (tabla al final)

---

## Apertura — Seguridad por rol (5 min)

**Usuario: los 6, uno por uno.** Objetivo: mostrar que la UI se adapta al rol y que la restricción es real, no cosmética.

- [ ] Entrar como `admin@marathon.com` / `Admin1234!` → navbar completo, todos los módulos
- [ ] Entrar como `compras@marathon.com` / `Demo1234!` → navbar con Compras, Cuentas por Pagar y Materia Prima; **el dashboard NO muestra KPIs de ventas**, muestra los de compras
- [ ] Entrar como `produccion@marathon.com` / `Demo1234!` → navbar con Manufactura; dashboard con KPIs de producción y accesos rápidos propios
- [ ] **Prueba clave:** con `produccion@marathon.com` escribir a mano en la barra de direcciones `http://localhost:4300/usuarios`
  - El `rolGuard` bloquea y redirige a `/dashboard?acceso=denegado` con el aviso "No tienes acceso a esta sección"
- [ ] Explicar la **doble capa**: el guard evita la pantalla, pero la defensa efectiva es `SecurityConfig` en el backend. Mostrar `MATRIZ_ROLES.md` como la auditoría de esa correspondencia.
- [ ] Volver a `admin@marathon.com`

---

## CICLO 1 — Order-to-Cash (10 min)

### 1.1 Crear el pedido · usuario `pedidos@marathon.com`
- [ ] **Pedidos → Nuevo Pedido**
- [ ] Seleccionar un cliente del seed
- [ ] Agregar 2 productos; observar que el subtotal de cada línea se calcula solo
- [ ] Ingresar un **descuento** (ej. 15.00) y ver bajar el total
- [ ] Marcar **Pedido Especial** → tipo "regalo" → nota
- [ ] Guardar → el pedido aparece como **pendiente**

### 1.2 Demostrar el trigger de integridad · terminal `psql`
Este es el momento fuerte de la demo desde el punto de vista de administración de BD.

```sql
-- El total lo calculó el trigger, no la aplicación:
SELECT id_pedido, total, descuento FROM pedido ORDER BY id_pedido DESC LIMIT 1;

-- Intentar falsear el total: la BD lo rechaza
UPDATE pedido SET total = 9999 WHERE id_pedido = <id>;
-- ERROR: pedido.total es calculado automaticamente por trigger y no puede modificarse manualmente

-- Pero el recálculo legítimo sí funciona: cambiar el descuento
UPDATE pedido SET descuento = 30 WHERE id_pedido = <id>;
SELECT id_pedido, total, descuento FROM pedido WHERE id_pedido = <id>;
-- el total bajó solo, en neto
```

- [ ] Señalar que esta protección se **corrigió en la Fase 32**: antes usaba `pg_trigger_depth() = 0`, condición que dentro de un trigger vale 1 y por tanto nunca se cumplía. El trigger existía pero no protegía nada. Ahora compara contra el valor real recalculado.

### 1.3 Picking · usuario `bodega@marathon.com`
- [ ] Cambiar el pedido a **Procesado**
- [ ] **Picking → Ejecutar Picking** → confirmar las líneas → guardar

### 1.4 Empaque y despacho · usuario `bodega@marathon.com`
- [ ] **Empaque → Lista para Empacar**
- [ ] Ingresar número HU, transportista y región destino → confirmar
- [ ] El estado pasa a **Enviado** y el stock baja en la bodega

### 1.5 Entrega y comprobante · usuario `supervisor@marathon.com`
- [ ] Cambiar a **Entregado** (estado final, sin más transiciones)
- [ ] **Generar Comprobante** → observar el número (`COMP-2026-00000X`)
- [ ] **Descargar PDF** y señalar: datos del cliente, líneas con subtotal, descuento y **TOTAL NETO** que cuadra con `pedido.total`

---

## CICLO 2 — Procure-to-Pay (10 min)

### 2.1 Crear la orden · usuario `compras@marathon.com`
- [ ] **Compras → Nueva Orden de Compra**
- [ ] Seleccionar proveedor
- [ ] Agregar una línea de **producto** y una de **materia prima**
  - Explicar la **asociación polimórfica exclusiva**: cada línea es producto O materia prima, nunca ambos ni ninguno, garantizado por el CHECK `chk_oc_detalle_item_exclusivo` en la BD
- [ ] Guardar → estado **borrador** → **Enviar a aprobación**
- [ ] **Intentar aprobar la propia orden** → el sistema lo rechaza: **separación de funciones**

### 2.2 Aprobar · usuario `admin@marathon.com`
- [ ] **Compras** → abrir la orden → **Aprobar**
- [ ] Señalar que `total` lo calculó el trigger `fn_recalcular_total_orden_compra_stmt`

### 2.3 Recibir mercancía · usuario `bodega@marathon.com`
- [ ] **Compras → Recepción** sobre la orden aprobada
- [ ] Recibir **parcialmente** (ej. la mitad de una línea) y declarar alguna unidad **defectuosa**
- [ ] Ver la orden pasar a **recibida_parcial**; recibir el resto → **recibida_completa**
- [ ] Explicar: solo entra al stock `cantidad_buena = recibida − defectuosa`; la defectuosa alimenta el ciclo 4
- [ ] **Materia Prima** → mostrar el **costo promedio ponderado** actualizado por la recepción

```sql
-- Verificación del promedio ponderado
SELECT nombre, stock_actual, costo_unitario_promedio FROM materia_prima ORDER BY id_materia_prima DESC LIMIT 3;
```

### 2.4 Factura, cuenta por pagar y pago · usuario `compras@marathon.com`
- [ ] **Compras → Registrar Factura** sobre la orden recibida (subtotal + impuesto)
  - `total` es columna **GENERATED**: la app no la escribe
  - Al guardar se genera automáticamente la **cuenta por pagar**
- [ ] **Cuentas por Pagar** → abrir la cuenta → **Registrar Pago** por el saldo completo
- [ ] Observar: `saldo_pendiente` llega a **0.00**, la cuenta pasa a **pagada** y **la factura también**, por cascada del trigger `fn_recalcular_monto_pagado_cxp`
- [ ] Intentar un pago mayor al saldo → rechazado con mensaje claro

---

## CICLO 3 — Manufactura (12 min)

Es el bloque que distingue al proyecto. Vale la pena no apurarlo.

### 3.1 BOM · usuario `admin@marathon.com`
- [ ] **Productos** → filtrar por **Origen = fabricado** → abrir uno de los 3 productos de marca propia
- [ ] Mostrar la sección **Lista de Materiales (BOM)**: qué materias primas y cuánto para producir 1 unidad
- [ ] Mostrar el panel de **costo estimado** por BOM, con margen bruto sobre el precio de venta
- [ ] **Prueba de integridad:** intentar cambiar el origen a "comprado" con BOM activo → rechazado por el trigger `trg_validar_cambio_origen_producto`
- [ ] Opcional en `psql`, el trigger recíproco:

```sql
-- Intentar dar BOM a un producto comprado
INSERT INTO lista_materiales (id_producto, id_materia_prima, cantidad_necesaria) VALUES (1, 1, 1);
-- ERROR: solo los productos con origen 'fabricado' pueden tener lista de materiales
```

### 3.2 Crear la orden de producción · usuario `produccion@marathon.com`
- [ ] **Manufactura → Producción → Nueva Orden**
- [ ] Elegir el producto fabricado y una cantidad
- [ ] Mostrar el **panel de disponibilidad en vivo**: calcula el consumo teórico según el BOM y avisa cuánto se puede producir con el stock actual
- [ ] Guardar → estado **planificada**. Señalar que **todavía no se consumió nada**

### 3.3 Iniciar (consumo real) · usuario `produccion@marathon.com`
- [ ] **Iniciar** la orden → estado **en_proceso**
- [ ] Explicar lo que acaba de pasar:
  - Se **re-verificó** la disponibilidad (otra orden pudo consumir stock entre planificar e iniciar)
  - Se descontó la materia prima y se registró en el **kardex** (`salida_produccion`)
  - Se capturó el **snapshot inmutable** del costo unitario de cada material
- [ ] **Materia Prima → Kardex** → mostrar los movimientos de salida
- [ ] Señalar por qué el snapshot importa: si mañana sube el precio del material, el costo de esta orden **no cambia**. Es contabilidad correcta.

### 3.4 Completar (producción y costeo) · usuario `produccion@marathon.com`
- [ ] **Completar** declarando una cantidad producida **menor** a la planificada y algún **consumo real distinto** al teórico
- [ ] Mostrar el panel de costos:
  - **Merma** por línea (columna GENERATED = real − teórico)
  - `costo_materia_prima` + `costo_mano_obra` + `costo_indirecto` = **costo_total** (GENERATED)
  - **costo_unitario_producido** (GENERATED)
- [ ] **Inventario** → el producto terminado entró en la bodega destino
- [ ] Demostrar la protección del costo en `psql`:

```sql
-- Intentar falsear el costo de materia prima de la orden
UPDATE orden_produccion SET costo_materia_prima = 1 WHERE id_orden_produccion = <id>;
-- ERROR: costo_materia_prima solo puede fijarse con el valor real de los consumos
```

### 3.5 Analítica · usuario `produccion@marathon.com`
- [ ] **Dashboard de Producción**: 7 KPIs, top-3 productos fabricados, dona por estado y **semáforo de merma** (verde <5%, amarillo 5–15%, rojo >15%)
- [ ] **Análisis de Costos** → comparativa **fabricar vs comprar**
- [ ] **Reportes** → pestañas "Consumo de Materia Prima" y "Eficiencia de Producción" → vista previa → **exportar Excel y PDF**

---

## CICLO 4 — Calidad / devoluciones (8 min)

### 4.1 Solicitud de devolución · usuario `pedidos@marathon.com`
- [ ] **Devoluciones → Nueva Solicitud**
- [ ] Elegir el pedido **entregado** del ciclo 1 (solo los entregados son elegibles)
- [ ] Seleccionar 2 líneas, cantidad 1 cada una, motivo "producto defectuoso"
- [ ] Guardar → estado **solicitada**

### 4.2 Inspección · usuario `bodega@marathon.com`
- [ ] **Devoluciones** → abrir la solicitud → **Iniciar Inspección** → estado **en_inspeccion**
- [ ] Inspeccionar indicando la bodega destino y el resultado de cada línea:
  - línea 1 → **apto_reventa**
  - línea 2 → **defectuoso**
- [ ] Guardar → estado **completada**
- [ ] **Inventario** → el producto apto **volvió al stock**; el defectuoso **no**
- [ ] **Auditoría → Historial de Inventario** → mostrar el registro con stock anterior, stock nuevo y **el usuario de bodega**, puesto por el trigger `fn_trg_historial_inventario` gracias a `SET LOCAL app.current_user_id`

### 4.3 Devolución a proveedor · usuario `compras@marathon.com`
- [ ] **Devoluciones a Proveedor → Items disponibles**
- [ ] Mostrar que la bandeja reúne **dos orígenes**: el defectuoso del RMA (ciclo 4) y las unidades defectuosas de la recepción (ciclo 2)
- [ ] Seleccionar el item del RMA → crear la devolución → estado **pendiente**
- [ ] Cambiar a **enviada** → **Resolver** con tipo "reembolso" y monto
- [ ] **Prueba de integridad:** intentar crear otra devolución con el mismo item → rechazado ("El item RMA #N ya fue incluido en otra devolución a proveedor"), garantizado por constraints UNIQUE en la BD
- [ ] Cerrar la idea: **el ciclo de calidad queda completo** — el cliente devuelve, bodega inspecciona, compras devuelve al proveedor y el proveedor resuelve

---

## Cierre — Asistente IA, auditoría y recuperación (10 min)

### Asistente IA · usuario `admin@marathon.com`
- [ ] **Asistente IA** → consultar en lenguaje natural, por ejemplo:
  - "¿Cuáles son los 5 productos más vendidos?"
  - "¿Cuántas órdenes de producción se completaron?"
- [ ] Señalar la mitigación de riesgo: el servicio **valida que el SQL generado sea solo SELECT** y limita a 500 filas. Se documenta explícitamente como riesgo conocido: se ejecuta SQL generado por IA.

> Requiere la clave de Gemini en `application-local.properties`. Si no está configurada, saltar esta parte.
>
> **OJO CON LA CUOTA.** El plan gratuito de Gemini permite **20 preguntas al día**
> por modelo. Si alguien estuvo probando el asistente esa mañana, en la demo
> responde «Se agotó la cuota». Comprobarlo ANTES haciendo una pregunta, y no
> gastar el resto en ensayos. El error se ve en la propia pantalla, así que no
> hay sorpresa silenciosa — pero sí un hueco en el guion.

### Auditoría
- [ ] **Auditoría → Rastro por usuario** → elegir a `admin@marathon.com` y enseñar de una sola pantalla en qué módulos trabajó, qué datos cambió y qué stock movió durante la demo
- [ ] Pinchar una línea del desglose: salta al detalle con los filtros ya puestos
- [ ] **Auditoría → Cambios en datos** → enseñar el **antes y el después** de un campo concreto, y desplegar la fila para ver la transacción completa
- [ ] Si aparece alguna fila en ámbar («fuera de la app»), señalarla: es un cambio hecho por `psql` o por un script, no por el sistema. Es el caso que más le interesa a una auditoría
- [ ] **Auditoría → Log de Acciones** → la traza de las operaciones de esta demo, con usuario, módulo, acción e IP

### Recuperación ante desastre · usuario `admin@marathon.com`

> Es la parte que más impresiona y la que más puede salir mal si se improvisa.
> Contar con **6 minutos** y no meterla si va justo de tiempo.

- [ ] **Respaldos y recuperación** → «Guardar un punto ahora», con una nota. Tarda **unos 30 segundos** y la barra avanza de verdad
- [ ] Enseñar la vista previa del borrado: **39 tablas y unos 46 millones de registros**. Señalar que `historial_inventario` está en la lista aunque no se haya marcado nada — se va porque cuelga del inventario por clave ajena
- [ ] Explicar qué NO se borra: usuarios y roles (si no, nadie podría volver a entrar) y el diario de esta pantalla (vive en un esquema aparte que no entra en los volcados)
- [ ] Teclear `BORRAR mod_venta_inve` y borrar. Tarda **menos de un segundo**
- [ ] Abrir cualquier otra pantalla: **no hay datos**. El desastre es real
- [ ] Volver a Respaldos → «Restaurar» sobre el punto recién tomado, teclear `RESTAURAR mod_venta_inve`
- [ ] Mientras dura (**4-5 minutos**): abrir otra pestaña del sistema y enseñar el **503 «en mantenimiento»**. La pantalla de respaldos sigue viva porque se responde desde memoria, sin tocar la base
- [ ] Al terminar: los datos están de vuelta, y el **diario conserva las dos operaciones** con quién las hizo y desde qué IP

---

## Puntos clave para el jurado

1. **La base de datos es la fuente de verdad, no la aplicación.** Los totales, subtotales, saldos, mermas y costos son columnas GENERATED o los calculan triggers. La aplicación tiene prohibido escribirlos y la BD lo hace cumplir.
2. **Los triggers de protección comparan contra el valor real recalculado.** El patrón `pg_trigger_depth() = 0` que se usaba en un trigger heredado **no funciona** (dentro de un trigger vale 1, nunca 0): se detectó en F29, se demostró explotable y se corrigió en F32. Un trigger que parece proteger y no protege es peor que no tenerlo.
3. **Costeo contablemente correcto.** Promedio ponderado que solo se recalcula al comprar, y snapshot inmutable al consumir: el costo histórico de una orden nunca cambia retroactivamente.
4. **Asociaciones polimórficas exclusivas** resueltas con CHECK constraints, no con lógica de aplicación (líneas de orden de compra y de devolución a proveedor).
5. **Trazabilidad completa de stock:** todo cambio de inventario queda en `historial_inventario` con usuario y timestamp, vía trigger alimentado por `SET LOCAL app.current_user_id`.
6. **Triple capa de seguridad:** `rolGuard` en el frontend, `SecurityConfig` en el backend, constraints y triggers en la BD.
7. **Deuda técnica documentada y priorizada** por fase, con lo pospuesto declarado y justificado en `DEUDA_TECNICA.md`. El criterio en la fase final fue estabilidad para la demo sobre cerrar el 100 %.
8. **La recuperación está probada, no supuesta.** El respaldo lógico se toma en 29 s y se restaura en 4-5 min, medido sobre la base de 12 GB y 50,8 M de filas — y el ciclo completo (respaldar → vaciar 46 M de registros → restaurar) se ha ejecutado de extremo a extremo. El diario de la operación vive en un esquema que los volcados excluyen a propósito: es lo único que sobrevive al desastre, y por eso puede decir quién lo provocó.

---

## Credenciales

| Correo | Contraseña | Rol | Usado en |
|--------|-----------|-----|----------|
| `admin@marathon.com` | `Admin1234!` | Administrador | Apertura, aprobación de OC, BOM, IA, auditoría |
| `supervisor@marathon.com` | `Demo1234!` | Supervisor E-Commerce | Entrega y comprobante (ciclo 1) |
| `bodega@marathon.com` | `Demo1234!` | Operador de Bodega | Picking, empaque, recepción, inspección |
| `pedidos@marathon.com` | `Demo1234!` | Operador de Pedidos | Crear pedido, crear RMA |
| `compras@marathon.com` | `Demo1234!` | Encargado de Compras | Orden de compra, factura, CxP, devolución a proveedor |
| `produccion@marathon.com` | `Demo1234!` | Encargado de Producción | Órdenes de producción, costos, dashboard |
