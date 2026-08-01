# Base de Datos — mod_venta_inve (PostgreSQL 15)

## Tablas y Columnas

### ciudad
| Columna | Tipo | Notas |
|---------|------|-------|
| id_ciudad | SERIAL PK | Auto-increment |
| nombre | VARCHAR(100) NOT NULL | Nombre de la ciudad |
| estado | VARCHAR(20) DEFAULT 'activo' | activo/inactivo |
| created_at | TIMESTAMP DEFAULT NOW() | Fecha creación |

### categoria
| Columna | Tipo | Notas |
|---------|------|-------|
| id_categoria | SERIAL PK | |
| nombre | VARCHAR(100) NOT NULL UNIQUE | |
| descripcion | TEXT | |
| estado | VARCHAR(20) DEFAULT 'activo' | |
| created_at | TIMESTAMP DEFAULT NOW() | |

### rol
> ⚠️ **CORREGIDO EN F32:** `rol` **NO tiene columna `estado`**. Son solo 4 columnas.

| Columna | Tipo | Notas |
|---------|------|-------|
| id_rol | SERIAL PK | Los 6 roles ocupan los ids 5–10 |
| nombre | VARCHAR NOT NULL UNIQUE | |
| descripcion | VARCHAR | |
| created_at | TIMESTAMP DEFAULT NOW() | |

> **Nota de estado real (F32):** solo `Administrador` (49) y `Encargado de Compras` (5)
> tienen filas en `rol_permiso`; los otros 4 roles tienen 0. No rompe nada porque la
> autorización efectiva es por **nombre de rol** en `SecurityConfig`, no por permisos
> granulares. Anotado como trabajo futuro en `DEUDA_TECNICA.md`.

### unidad_medida
| Columna | Tipo | Notas |
|---------|------|-------|
| id_unidad | SERIAL PK | |
| nombre | VARCHAR(50) NOT NULL UNIQUE | |
| abreviatura | VARCHAR(10) NOT NULL | |
| estado | VARCHAR(20) DEFAULT 'activo' | |
| created_at | TIMESTAMP DEFAULT NOW() | |

### proveedor
| Columna | Tipo | Notas |
|---------|------|-------|
| id_proveedor | SERIAL PK | |
| nombre | VARCHAR(150) NOT NULL | |
| ruc | VARCHAR(13) UNIQUE | |
| direccion | TEXT | |
| telefono | VARCHAR(20) | |
| email | VARCHAR(100) | |
| id_ciudad | INT FK → ciudad | |
| estado | VARCHAR(20) DEFAULT 'activo' | |
| created_at | TIMESTAMP DEFAULT NOW() | |

### bodega
| Columna | Tipo | Notas |
|---------|------|-------|
| id_bodega | SERIAL PK | |
| nombre | VARCHAR(100) NOT NULL | |
| direccion | TEXT | |
| id_ciudad | INT FK → ciudad | |
| responsable | VARCHAR(150) | |
| estado | VARCHAR(20) DEFAULT 'activo' | |
| created_at | TIMESTAMP DEFAULT NOW() | |

### cliente
| Columna | Tipo | Notas |
|---------|------|-------|
| id_cliente | SERIAL PK | |
| nombre | VARCHAR(150) NOT NULL | |
| apellido | VARCHAR(150) NOT NULL | |
| cedula | VARCHAR(10) UNIQUE | |
| email | VARCHAR(100) | |
| telefono | VARCHAR(20) | |
| direccion | TEXT | |
| id_ciudad | INT FK → ciudad | |
| estado | VARCHAR(20) DEFAULT 'activo' | |
| created_at | TIMESTAMP DEFAULT NOW() | |

### usuario
| Columna | Tipo | Notas |
|---------|------|-------|
| id_usuario | SERIAL PK | |
| nombre | VARCHAR(100) NOT NULL | |
| apellido | VARCHAR(100) NOT NULL | |
| correo | VARCHAR(100) NOT NULL | Campo de login |
| password | VARCHAR(255) NOT NULL | BCrypt hasheado (min 60 chars) — app hashea antes de insertar |
| estado | VARCHAR(20) NOT NULL | activo/inactivo |
| created_at | TIMESTAMP NOT NULL | Fecha creación |
| updated_at | TIMESTAMP | Fecha última modificación |

### producto
| Columna | Tipo | Notas |
|---------|------|-------|
| id_producto | SERIAL PK | |
| codigo | VARCHAR(50) NOT NULL UNIQUE | |
| nombre | VARCHAR(200) NOT NULL | |
| descripcion | TEXT | |
| precio_compra | NUMERIC(10,2) NOT NULL | |
| precio_venta | NUMERIC(10,2) NOT NULL | |
| id_categoria | INT FK → categoria | |
| id_unidad | INT FK → unidad_medida | |
| stock_minimo | INT DEFAULT 0 | |
| estado | VARCHAR(20) DEFAULT 'activo' | |
| origen | VARCHAR(20) NOT NULL DEFAULT 'comprado' | (F27) CHECK `chk_producto_origen`: comprado/fabricado. Trigger `trg_validar_cambio_origen_producto` impide pasar a 'comprado' si hay BOM activo |
| created_at | TIMESTAMP DEFAULT NOW() | |

### usuario_rol
| Columna | Tipo | Notas |
|---------|------|-------|
| id_usuario_rol | SERIAL PK | |
| id_usuario | INT FK → usuario | ON DELETE CASCADE |
| id_rol | INT FK → rol | ON DELETE CASCADE |
| UNIQUE(id_usuario, id_rol) | | Un usuario no repite rol |

### pedido
| Columna | Tipo | Notas |
|---------|------|-------|
| id_pedido | SERIAL PK | |
| id_cliente | INT FK → cliente | |
| id_usuario | INT FK → usuario | Quien registra |
| fecha_pedido | TIMESTAMP DEFAULT NOW() | |
| total | NUMERIC(12,2) DEFAULT 0 | **CALCULADO POR TRIGGER** — no escribir |
| descuento | NUMERIC(12,2) DEFAULT 0 | |
| estado | VARCHAR DEFAULT 'pendiente' | pendiente/procesado/enviado/entregado/anulado |
| created_at | TIMESTAMP DEFAULT NOW() | |
| updated_at | TIMESTAMP NULL | |
| es_pedido_especial | BOOLEAN NOT NULL DEFAULT false | Fase 12.1 |
| tipo_especial | VARCHAR(50) NULL | CHECK: personalizado/regalo/corporativo |
| nota_especial | TEXT NULL | Fase 12.1 |
| fecha_limite_entrega | TIMESTAMP NULL | Fase 12.1 |
| numero_hu | VARCHAR(50) NULL | Fase 15 — unidad de handling |
| transportista | VARCHAR(100) NULL | Fase 15 |
| region_destino | VARCHAR(100) NULL | Fase 15 |
| fecha_empaque | TIMESTAMP NULL | Fase 15 |

### inventario
> ⚠️ **CORREGIDO EN F32 contra el catálogo real.** La versión anterior de este doc
> decía `cantidad` y `updated_at`; los nombres reales son `stock_actual`,
> `stock_minimo` y `fecha_actualizacion`.

| Columna | Tipo | Notas |
|---------|------|-------|
| id_inventario | SERIAL PK | |
| id_producto | INT FK → producto | |
| id_bodega | INT FK → bodega | |
| stock_actual | INT NOT NULL DEFAULT 0 | **NO** se llama `cantidad`. Antes de modificarlo: `SET LOCAL app.current_user_id` |
| stock_minimo | INT DEFAULT 0 | Umbral de alerta de stock bajo |
| fecha_actualizacion | TIMESTAMP DEFAULT NOW() | **NO** se llama `updated_at` |
| UNIQUE(id_producto, id_bodega) | | Un producto por bodega |

### producto_proveedor
| Columna | Tipo | Notas |
|---------|------|-------|
| id_producto_proveedor | SERIAL PK | |
| id_producto | INT FK → producto | ON DELETE CASCADE |
| id_proveedor | INT FK → proveedor | ON DELETE CASCADE |
| UNIQUE(id_producto, id_proveedor) | | |

### permiso
| Columna | Tipo | Notas |
|---------|------|-------|
| id_permiso | SERIAL PK | |
| modulo | VARCHAR NOT NULL | ej: "pedidos", "usuarios" |
| accion | VARCHAR NOT NULL | ej: "ver", "crear", "editar" |
| descripcion | VARCHAR | Descripción legible "modulo:accion" |

### detalle_pedido
| Columna | Tipo | Notas |
|---------|------|-------|
| id_detalle | SERIAL PK | |
| id_pedido | INT FK → pedido | ON DELETE CASCADE |
| id_producto | INT FK → producto | |
| cantidad | INT NOT NULL CHECK(>0) | |
| precio_unitario | NUMERIC(10,2) NOT NULL | |
| subtotal | NUMERIC(12,2) GENERATED ALWAYS AS (cantidad * precio_unitario) | **NUNCA insertar/actualizar** |
| picking_completado | BOOLEAN NOT NULL DEFAULT false | Fase 14 — picking |
| cantidad_recogida | INTEGER NOT NULL DEFAULT 0 | Fase 14 — CHECK >= 0 |

### comprobante_interno
> ⚠️ **CORREGIDO EN F32 contra el catálogo real.** La versión anterior describía
> un comprobante de movimiento entre bodegas (`tipo`, `id_bodega_origen`,
> `id_bodega_destino`, `observaciones`). En realidad el comprobante está ligado
> a un **pedido** y lleva su **total**.

| Columna | Tipo | Notas |
|---------|------|-------|
| id_comprobante | SERIAL PK | |
| id_pedido | INT FK → pedido | El comprobante documenta un pedido |
| id_usuario | INT FK → usuario | Quien lo emite |
| numero_comprobante | VARCHAR NOT NULL UNIQUE | Formato `COMP-AAAA-NNNNNN` |
| fecha_emision | TIMESTAMP DEFAULT NOW() | **NO** se llama `fecha` |
| total | NUMERIC | Debe cuadrar con `pedido.total` (neto). Validado por `fn_validar_total_comprobante` |
| estado | VARCHAR(20) DEFAULT 'activo' | |
| created_at | TIMESTAMP DEFAULT NOW() | |

### rol_permiso
| Columna | Tipo | Notas |
|---------|------|-------|
| id_rol_permiso | SERIAL PK | |
| id_rol | INT FK → rol | ON DELETE CASCADE |
| id_permiso | INT FK → permiso | ON DELETE CASCADE |
| UNIQUE(id_rol, id_permiso) | | |

### movimiento_inventario
> ⚠️ **CORREGIDO EN F32 contra el catálogo real.** La versión anterior decía que
> referenciaba `id_producto` + `id_bodega`. **NO es así:** referencia
> `id_inventario` (que ya lleva producto y bodega). Para filtrar por producto hay
> que hacer `JOIN inventario`. También faltaban 4 columnas.

| Columna | Tipo | Notas |
|---------|------|-------|
| id_movimiento | SERIAL PK | |
| id_inventario | INT FK → inventario NOT NULL | **NO** hay `id_producto` ni `id_bodega` |
| id_usuario | INT FK → usuario NOT NULL | Quien ejecuta |
| id_proveedor | INT FK → proveedor NULL | Opcional |
| id_pedido | INT FK → pedido NULL | Si el movimiento viene de un despacho |
| id_comprobante | INT FK → comprobante_interno NULL | |
| tipo_movimiento | VARCHAR NOT NULL | entrada/salida |
| cantidad | INT NOT NULL CHECK(>0) | |
| fecha | TIMESTAMP NOT NULL DEFAULT NOW() | |
| observacion | TEXT | Texto descriptivo del origen del movimiento |
| id_inventario_destino | INT FK → inventario NULL | Para transferencias entre bodegas |
| created_at | TIMESTAMP NOT NULL DEFAULT NOW() | |

> Es **solo bitácora**: NO existe trigger que aplique el movimiento al stock. El
> stock se actualiza explícitamente en el servicio. Insertar aquí no mueve stock
> (verificado en F22 para evitar doble conteo).

### historial_inventario
> ⚠️ **CORREGIDO EN F32 contra el catálogo real.** La versión anterior decía
> `cantidad_anterior`, `cantidad_nueva`, `fecha_cambio` y `tipo_operacion`.
> Ninguna de esas columnas existe.

| Columna | Tipo | Notas |
|---------|------|-------|
| id_historial | SERIAL PK | |
| id_inventario | INT FK → inventario NOT NULL | |
| id_usuario | INT NULL | Lo pone el trigger leyendo `app.current_user_id` |
| stock_anterior | INT NOT NULL | **NO** se llama `cantidad_anterior` |
| stock_nuevo | INT NOT NULL | **NO** se llama `cantidad_nueva` |
| motivo | VARCHAR NOT NULL | Ej. `actualizacion_stock`. **NO** se llama `tipo_operacion` |
| fecha | TIMESTAMP NOT NULL DEFAULT NOW() | **NO** se llama `fecha_cambio` |

> Lo alimenta el trigger `trg_historial_inventario` / `fn_trg_historial_inventario`.
> Si el servicio no ejecuta `SET LOCAL app.current_user_id` antes del UPDATE,
> `id_usuario` queda NULL y se pierde la autoría.

### log_accion (Fase 19b — auditoría)
> ⚠️ **AGREGADA AL DOC EN F32.** Existe desde F19b pero **no estaba documentada**;
> era la tabla 37 que faltaba en este inventario.

| Columna | Tipo | Notas |
|---------|------|-------|
| id_log | SERIAL PK | |
| id_usuario | INT FK → usuario | Quién ejecutó la acción |
| modulo | VARCHAR | Ej. `pedidos`, `compras` |
| accion | VARCHAR | Ej. `crear`, `editar`, `cambiar_estado` |
| descripcion | TEXT | Detalle legible |
| ip_address | VARCHAR | IP de origen de la petición |
| fecha | TIMESTAMP DEFAULT NOW() | |

### materia_prima (Fase 21)
| Columna | Tipo | Notas |
|---------|------|-------|
| id_materia_prima | INT GENERATED ALWAYS AS IDENTITY PK | |
| nombre | VARCHAR(150) NOT NULL UNIQUE | uq_materia_prima_nombre |
| descripcion | TEXT | |
| id_unidad_medida | INT FK → unidad_medida NOT NULL | |
| estado | VARCHAR(20) NOT NULL DEFAULT 'activo' | CHECK activo/inactivo |
| stock_actual | NUMERIC(12,3) NOT NULL DEFAULT 0 | (F22) CHECK stock_actual >= 0. Stock GLOBAL sin bodega |
| stock_minimo | NUMERIC(12,3) NOT NULL DEFAULT 0 | (F22) |
| costo_unitario_promedio | NUMERIC(12,4) NOT NULL DEFAULT 0 | (F29) CHECK >= 0 (`chk_mp_costo`). **Costo promedio ponderado**, recalculado SOLO al recibir compra |
| created_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

> Catálogo + stock global (F22). NUMERIC porque se mide en metros/kg/litros. Sin kardex/movimientos aún — eso llega en F26 Manufactura.

### orden_compra (Fase 21)
| Columna | Tipo | Notas |
|---------|------|-------|
| id_orden_compra | INT GENERATED ALWAYS AS IDENTITY PK | |
| id_proveedor | INT FK → proveedor NOT NULL | |
| id_usuario_solicitante | INT FK → usuario NOT NULL | Quien crea/solicita |
| id_usuario_aprobador | INT FK → usuario NULL | Quien aprueba (solo Admin) |
| fecha_orden | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |
| fecha_aprobacion | TIMESTAMP NULL | Se setea al aprobar |
| estado | VARCHAR(30) NOT NULL DEFAULT 'borrador' | CHECK: borrador/pendiente_aprobacion/aprobada/rechazada/recibida_parcial/recibida_completa/cancelada |
| total | NUMERIC(12,2) NOT NULL DEFAULT 0 | **CALCULADO POR TRIGGER** — no escribir. Protegido contra UPDATE manual |
| observaciones | TEXT | |
| created_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMP NULL | |

### orden_compra_detalle (Fase 21)
| Columna | Tipo | Notas |
|---------|------|-------|
| id_detalle_oc | INT GENERATED ALWAYS AS IDENTITY PK | |
| id_orden_compra | INT FK → orden_compra NOT NULL | ON DELETE CASCADE |
| tipo_item | VARCHAR(20) NOT NULL | CHECK: producto/materia_prima |
| id_producto | INT FK → producto NULL | Exclusivo con id_materia_prima |
| id_materia_prima | INT FK → materia_prima NULL | Exclusivo con id_producto |
| cantidad | INT NOT NULL CHECK(>0) | |
| precio_unitario | NUMERIC(10,2) NOT NULL CHECK(>0) | |
| subtotal | NUMERIC(12,2) GENERATED ALWAYS AS (cantidad * precio_unitario) STORED | **NUNCA insertar/actualizar** |
| cantidad_recibida | INT NOT NULL DEFAULT 0 | CHECK 0 ≤ recibida ≤ cantidad (recepción real en F22) |

> **Asociación polimórfica exclusiva:** CHECK `chk_oc_detalle_item_exclusivo` garantiza que cada línea sea O producto O materia prima, nunca ambos ni ninguno, según `tipo_item`.

### recepcion_mercancia (Fase 22)
| Columna | Tipo | Notas |
|---------|------|-------|
| id_recepcion | INT GENERATED ALWAYS AS IDENTITY PK | Una entrega/visita física |
| id_orden_compra | INT FK → orden_compra NOT NULL | |
| id_usuario_receptor | INT FK → usuario NOT NULL | Quien recibe |
| id_bodega | INT FK → bodega NOT NULL | Destino (aplica a líneas producto) |
| fecha_recepcion | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |
| numero_guia_remision | VARCHAR(50) | |
| observaciones | TEXT | |
| created_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

> Una orden puede recibirse en MÚLTIPLES entregas parciales, cada una es un `recepcion_mercancia`.

### recepcion_mercancia_detalle (Fase 22)
| Columna | Tipo | Notas |
|---------|------|-------|
| id_detalle_rm | INT GENERATED ALWAYS AS IDENTITY PK | |
| id_recepcion | INT FK → recepcion_mercancia NOT NULL | ON DELETE CASCADE |
| id_detalle_oc | INT FK → orden_compra_detalle NOT NULL | Línea de la OC que se recibe |
| cantidad_recibida_ahora | INT NOT NULL CHECK(>0) | Se ACUMULA en orden_compra_detalle.cantidad_recibida |
| cantidad_defectuosa | INT NOT NULL DEFAULT 0 | CHECK 0 ≤ def ≤ recibida_ahora. NO entra al stock (devolución en F25) |
| observacion | TEXT | |

> Solo entra al stock `cantidad_buena = cantidad_recibida_ahora - cantidad_defectuosa`.

### factura_compra (Fase 23)
| Columna | Tipo | Notas |
|---------|------|-------|
| id_factura_compra | INT GENERATED ALWAYS AS IDENTITY PK | |
| id_orden_compra | INT FK → orden_compra NOT NULL | |
| id_usuario_registro | INT FK → usuario NOT NULL | Quien registra |
| numero_factura_proveedor | VARCHAR(50) NOT NULL | UNIQUE(id_orden_compra, numero_factura_proveedor) |
| fecha_factura | DATE NOT NULL | |
| fecha_vencimiento | DATE NOT NULL | CHECK >= fecha_factura |
| subtotal | NUMERIC(12,2) NOT NULL | CHECK > 0 |
| impuesto | NUMERIC(12,2) NOT NULL DEFAULT 0 | CHECK >= 0 |
| total | NUMERIC(12,2) GENERATED ALWAYS AS (subtotal + impuesto) STORED | **NUNCA insertar/actualizar** |
| estado | VARCHAR(20) NOT NULL DEFAULT 'pendiente' | CHECK: pendiente/pagada/anulada |
| created_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

### cuenta_por_pagar (Fase 23)
| Columna | Tipo | Notas |
|---------|------|-------|
| id_cuenta_pagar | INT GENERATED ALWAYS AS IDENTITY PK | |
| id_factura_compra | INT FK → factura_compra NOT NULL UNIQUE | 1:1 con factura |
| id_proveedor | INT FK → proveedor NOT NULL | |
| monto_total | NUMERIC(12,2) NOT NULL | |
| monto_pagado | NUMERIC(12,2) NOT NULL DEFAULT 0 | **CALCULADO POR TRIGGER** — no escribir. Protegido contra UPDATE manual |
| saldo_pendiente | NUMERIC(12,2) GENERATED ALWAYS AS (monto_total - monto_pagado) STORED | **NUNCA insertar/actualizar** |
| fecha_vencimiento | DATE NOT NULL | |
| estado | VARCHAR(20) NOT NULL DEFAULT 'vigente' | CHECK: vigente/vencida/pagada |
| created_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

### pago_proveedor (Fase 23)
| Columna | Tipo | Notas |
|---------|------|-------|
| id_pago | INT GENERATED ALWAYS AS IDENTITY PK | |
| id_cuenta_pagar | INT FK → cuenta_por_pagar NOT NULL | |
| id_usuario_registro | INT FK → usuario NOT NULL | Quien registra el pago |
| monto | NUMERIC(12,2) NOT NULL | CHECK > 0 |
| fecha_pago | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |
| metodo_pago | VARCHAR(30) NOT NULL | CHECK: transferencia/cheque/efectivo/tarjeta |
| referencia | VARCHAR(100) | Nro. transferencia, cheque, etc. |
| observaciones | TEXT | |

### solicitud_devolucion (Fase 24)
| Columna | Tipo | Notas |
|---------|------|-------|
| id_solicitud | INT GENERATED ALWAYS AS IDENTITY PK | |
| id_pedido | INT FK → pedido NOT NULL | |
| id_usuario_registro | INT FK → usuario NOT NULL | Quien registra la solicitud |
| motivo | VARCHAR(50) NOT NULL | CHECK: producto_defectuoso/talla_incorrecta/no_esperado/cambio_opinion/producto_incompleto/otro |
| descripcion | TEXT | |
| estado | VARCHAR(30) NOT NULL DEFAULT 'solicitada' | CHECK: solicitada/en_inspeccion/completada/rechazada |
| fecha_solicitud | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |
| fecha_inspeccion | TIMESTAMP | Se setea al iniciar inspeccion |
| id_usuario_inspector | INT FK → usuario NULL | Quien inspecciona |
| created_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

### solicitud_devolucion_detalle (Fase 24)
| Columna | Tipo | Notas |
|---------|------|-------|
| id_detalle_sd | INT GENERATED ALWAYS AS IDENTITY PK | |
| id_solicitud | INT FK → solicitud_devolucion NOT NULL | ON DELETE CASCADE |
| id_detalle_pedido | INT FK → detalle_pedido NOT NULL | |
| cantidad_devuelta | INT NOT NULL | CHECK > 0 |
| resultado_inspeccion | VARCHAR(20) NULL | CHECK: NULL o apto_reventa/defectuoso/rechazado |
| observacion_inspeccion | TEXT | |

> apto_reventa: sube stock. defectuoso: registrado para F25. rechazado: no toca nada.

### reembolso_cliente (Fase 24)
| Columna | Tipo | Notas |
|---------|------|-------|
| id_reembolso | INT GENERATED ALWAYS AS IDENTITY PK | |
| id_solicitud | INT FK → solicitud_devolucion NOT NULL UNIQUE | 1:1 con solicitud |
| id_usuario_registro | INT FK → usuario NOT NULL | |
| monto | NUMERIC(10,2) NOT NULL | CHECK > 0 |
| metodo | VARCHAR(30) NOT NULL | CHECK: nota_credito/transferencia/efectivo |
| fecha_reembolso | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |
| observaciones | TEXT | |

### devolucion_proveedor (Fase 25)
| Columna | Tipo | Notas |
|---------|------|-------|
| id_devolucion_prov | INT GENERATED ALWAYS AS IDENTITY PK | |
| id_proveedor | INT FK → proveedor NOT NULL | |
| id_usuario_registro | INT FK → usuario NOT NULL | |
| fecha_devolucion | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |
| estado | VARCHAR(20) NOT NULL DEFAULT 'pendiente' | CHECK: pendiente/enviada/resuelta/rechazada |
| tipo_resolucion | VARCHAR(20) NULL | CHECK: NULL o reembolso/reposicion |
| monto_reembolso | NUMERIC(10,2) NULL | CHECK NULL o > 0 |
| observaciones | TEXT | |
| created_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

### devolucion_proveedor_detalle (Fase 25)
| Columna | Tipo | Notas |
|---------|------|-------|
| id_detalle_dp | INT GENERATED ALWAYS AS IDENTITY PK | |
| id_devolucion_prov | INT FK → devolucion_proveedor NOT NULL | ON DELETE CASCADE |
| origen | VARCHAR(20) NOT NULL | CHECK: rma_cliente/recepcion_compra |
| id_solicitud_devolucion_detalle | INT FK → solicitud_devolucion_detalle NULL UNIQUE | Exclusivo con id_recepcion_detalle |
| id_recepcion_detalle | INT FK → recepcion_mercancia_detalle NULL UNIQUE | Exclusivo con id_solicitud_devolucion_detalle |
| id_producto | INT FK → producto NOT NULL | |
| cantidad | INT NOT NULL | CHECK > 0 |
| motivo | TEXT | |

> Asociacion polimorfica exclusiva: CHECK `chk_dpd_origen_exclusivo`. UNIQUE en cada FK origen garantiza que un item defectuoso no se use dos veces.

### movimiento_materia_prima (Fase 26)
| Columna | Tipo | Notas |
|---------|------|-------|
| id_movimiento_mp | INT GENERATED ALWAYS AS IDENTITY PK | |
| id_materia_prima | INT FK → materia_prima NOT NULL | |
| id_usuario | INT FK → usuario NOT NULL | |
| tipo_movimiento | VARCHAR(20) NOT NULL | CHECK: entrada_compra/salida_produccion/ajuste/merma |
| cantidad | NUMERIC(12,3) NOT NULL | CHECK > 0 |
| stock_anterior | NUMERIC(12,3) NOT NULL | Snapshot antes del movimiento |
| stock_nuevo | NUMERIC(12,3) NOT NULL | Snapshot despues del movimiento |
| id_recepcion | INT FK → recepcion_mercancia NULL | Solo para entrada_compra |
| id_orden_produccion | INT FK → orden_produccion NULL | (F28) FK `fk_mmp_orden_produccion` aplicada en el retrofit; ON DELETE SET NULL. Para movimientos 'salida_produccion'/'merma'/'ajuste' de una OP |
| observacion | TEXT | |
| fecha | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

> RecepcionMercanciaService fue retrofiteado (F26) para registrar kardex tipo `entrada_compra` al recibir materia prima.

### lista_materiales (Fase 27) — BOM / receta
| Columna | Tipo | Notas |
|---------|------|-------|
| id_bom | INT GENERATED ALWAYS AS IDENTITY PK | |
| id_producto | INT FK → producto NOT NULL | ON DELETE CASCADE. Debe tener origen='fabricado' (trigger) |
| id_materia_prima | INT FK → materia_prima NOT NULL | ON DELETE RESTRICT |
| cantidad_necesaria | NUMERIC(12,3) NOT NULL | CHECK > 0. Cantidad para producir 1 unidad |
| estado | VARCHAR(20) NOT NULL DEFAULT 'activo' | CHECK activo/inactivo |
| created_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

> UNIQUE `uq_bom_producto_materia (id_producto, id_materia_prima)`. La receta define QUÉ y CUÁNTA materia prima consume un producto fabricado; el consumo real ocurre en F28. Solo productos con `origen='fabricado'` admiten BOM (trigger `trg_validar_bom_producto_fabricado`).

### orden_produccion (Fase 28)
| Columna | Tipo | Notas |
|---------|------|-------|
| id_orden_produccion | INT GENERATED ALWAYS AS IDENTITY PK | |
| id_producto | INT FK → producto NOT NULL | Debe tener origen='fabricado' (trigger) |
| id_bodega_destino | INT FK → bodega NOT NULL | Donde entra el producto terminado |
| id_usuario_registro | INT FK → usuario NOT NULL | Quien crea la orden |
| id_usuario_completa | INT FK → usuario NULL | Quien la completa (SET NULL) |
| cantidad_planificada | INT NOT NULL | CHECK > 0 |
| cantidad_producida | INT NULL | CHECK NULL o >= 0. Se llena al completar |
| estado | VARCHAR(20) NOT NULL DEFAULT 'planificada' | CHECK: planificada/en_proceso/completada/cancelada |
| fecha_creacion | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |
| fecha_inicio | TIMESTAMP NULL | Se setea al iniciar (consumo de MP) |
| fecha_fin | TIMESTAMP NULL | Se setea al completar |
| observaciones | TEXT | |
| costo_materia_prima | NUMERIC(14,2) NOT NULL DEFAULT 0 | (F29) **Calculado por el servicio** vía `fn_set_costo_materia_prima_op(id)` = SUM(costo_linea). Protegido por `trg_proteger_costo_materia_prima_op`. NO es GENERATED (PostgreSQL no admite subconsultas) |
| costo_mano_obra | NUMERIC(14,2) NOT NULL DEFAULT 0 | (F29) Monto global por orden, capturado al completar |
| costo_indirecto | NUMERIC(14,2) NOT NULL DEFAULT 0 | (F29) Monto global por orden |
| costo_total | NUMERIC(14,2) GENERATED ALWAYS AS (costo_materia_prima + costo_mano_obra + costo_indirecto) STORED | (F29) **NUNCA insertar/actualizar** |
| costo_unitario_producido | NUMERIC(14,4) GENERATED ALWAYS AS (CASE WHEN cantidad_producida IS NULL OR = 0 THEN 0 ELSE total / cantidad_producida END) STORED | (F29) **NUNCA insertar/actualizar** |

> CHECK `chk_op_costos`: los tres costos base >= 0.
> Trigger `trg_validar_op_producto_fabricado` (BEFORE INSERT/UPDATE): solo productos con `origen='fabricado'` admiten órdenes de producción. El consumo de MP ocurre al INICIAR; el alta del producto terminado, al COMPLETAR.

### orden_produccion_consumo (Fase 28)
| Columna | Tipo | Notas |
|---------|------|-------|
| id_consumo | INT GENERATED ALWAYS AS IDENTITY PK | |
| id_orden_produccion | INT FK → orden_produccion NOT NULL | ON DELETE CASCADE |
| id_materia_prima | INT FK → materia_prima NOT NULL | ON DELETE RESTRICT |
| cantidad_teorica | NUMERIC(12,3) NOT NULL | CHECK > 0. bom.cantidad_necesaria × unidades |
| cantidad_real | NUMERIC(12,3) NULL | CHECK NULL o >= 0. Se declara al completar |
| merma | NUMERIC(12,3) GENERATED ALWAYS AS (COALESCE(cantidad_real, cantidad_teorica) - cantidad_teorica) STORED | **NUNCA insertar/actualizar**. En JPA con `@Generated(INSERT,UPDATE)` |
| costo_unitario_snapshot | NUMERIC(12,4) NOT NULL DEFAULT 0 | (F29) **Snapshot inmutable** del `costo_unitario_promedio` capturado al INICIAR la OP |
| costo_linea | NUMERIC(14,4) GENERATED ALWAYS AS (COALESCE(cantidad_real, cantidad_teorica) * costo_unitario_snapshot) STORED | (F29) **NUNCA insertar/actualizar**. En JPA con `@Generated(INSERT,UPDATE)` |

> UNIQUE `uq_opc_orden_materia (id_orden_produccion, id_materia_prima)`. Merma positiva = se gastó de más; negativa = sobró.

## Relaciones FK

| Tabla Origen | Columna FK | Tabla Destino | ON UPDATE | ON DELETE |
|-------------|-----------|---------------|-----------|-----------|
| proveedor | id_ciudad | ciudad | CASCADE | SET NULL |
| bodega | id_ciudad | ciudad | CASCADE | SET NULL |
| cliente | id_ciudad | ciudad | CASCADE | SET NULL |
| usuario_rol | id_usuario | usuario | CASCADE | CASCADE |
| usuario_rol | id_rol | rol | CASCADE | CASCADE |
| producto | id_categoria | categoria | CASCADE | SET NULL |
| producto | id_unidad | unidad_medida | CASCADE | SET NULL |
| pedido | id_cliente | cliente | CASCADE | RESTRICT |
| pedido | id_usuario | usuario | CASCADE | RESTRICT |
| detalle_pedido | id_pedido | pedido | CASCADE | CASCADE |
| detalle_pedido | id_producto | producto | CASCADE | RESTRICT |
| inventario | id_producto | producto | CASCADE | CASCADE |
| inventario | id_bodega | bodega | CASCADE | CASCADE |
| producto_proveedor | id_producto | producto | CASCADE | CASCADE |
| producto_proveedor | id_proveedor | proveedor | CASCADE | CASCADE |
| rol_permiso | id_rol | rol | CASCADE | CASCADE |
| rol_permiso | id_permiso | permiso | CASCADE | CASCADE |
| comprobante_interno | id_bodega_origen | bodega | CASCADE | SET NULL |
| comprobante_interno | id_bodega_destino | bodega | CASCADE | SET NULL |
| comprobante_interno | id_usuario | usuario | CASCADE | RESTRICT |
| movimiento_inventario | id_comprobante | comprobante_interno | CASCADE | CASCADE |
| materia_prima | id_unidad_medida | unidad_medida | CASCADE | RESTRICT |
| orden_compra | id_proveedor | proveedor | CASCADE | RESTRICT |
| orden_compra | id_usuario_solicitante | usuario | CASCADE | RESTRICT |
| orden_compra | id_usuario_aprobador | usuario | CASCADE | SET NULL |
| orden_compra_detalle | id_orden_compra | orden_compra | CASCADE | CASCADE |
| orden_compra_detalle | id_producto | producto | CASCADE | RESTRICT |
| orden_compra_detalle | id_materia_prima | materia_prima | CASCADE | RESTRICT |
| recepcion_mercancia | id_orden_compra | orden_compra | CASCADE | RESTRICT |
| recepcion_mercancia | id_usuario_receptor | usuario | CASCADE | RESTRICT |
| recepcion_mercancia | id_bodega | bodega | CASCADE | RESTRICT |
| recepcion_mercancia_detalle | id_recepcion | recepcion_mercancia | CASCADE | CASCADE |
| recepcion_mercancia_detalle | id_detalle_oc | orden_compra_detalle | CASCADE | RESTRICT |
| movimiento_inventario | id_producto | producto | CASCADE | RESTRICT |
| movimiento_inventario | id_bodega | bodega | CASCADE | RESTRICT |
| movimiento_inventario | id_usuario | usuario | CASCADE | RESTRICT |
| historial_inventario | id_inventario | inventario | CASCADE | CASCADE |
| factura_compra | id_orden_compra | orden_compra | CASCADE | RESTRICT |
| factura_compra | id_usuario_registro | usuario | CASCADE | RESTRICT |
| cuenta_por_pagar | id_factura_compra | factura_compra | CASCADE | RESTRICT |
| cuenta_por_pagar | id_proveedor | proveedor | CASCADE | RESTRICT |
| pago_proveedor | id_cuenta_pagar | cuenta_por_pagar | CASCADE | RESTRICT |
| pago_proveedor | id_usuario_registro | usuario | CASCADE | RESTRICT |
| solicitud_devolucion | id_pedido | pedido | CASCADE | RESTRICT |
| solicitud_devolucion | id_usuario_registro | usuario | CASCADE | RESTRICT |
| solicitud_devolucion | id_usuario_inspector | usuario | CASCADE | SET NULL |
| solicitud_devolucion_detalle | id_solicitud | solicitud_devolucion | CASCADE | CASCADE |
| solicitud_devolucion_detalle | id_detalle_pedido | detalle_pedido | CASCADE | RESTRICT |
| reembolso_cliente | id_solicitud | solicitud_devolucion | CASCADE | RESTRICT |
| reembolso_cliente | id_usuario_registro | usuario | CASCADE | RESTRICT |
| devolucion_proveedor | id_proveedor | proveedor | CASCADE | RESTRICT |
| devolucion_proveedor | id_usuario_registro | usuario | CASCADE | RESTRICT |
| devolucion_proveedor_detalle | id_devolucion_prov | devolucion_proveedor | CASCADE | CASCADE |
| devolucion_proveedor_detalle | id_solicitud_devolucion_detalle | solicitud_devolucion_detalle | CASCADE | RESTRICT |
| devolucion_proveedor_detalle | id_recepcion_detalle | recepcion_mercancia_detalle | CASCADE | RESTRICT |
| devolucion_proveedor_detalle | id_producto | producto | CASCADE | RESTRICT |
| movimiento_materia_prima | id_materia_prima | materia_prima | CASCADE | RESTRICT |
| movimiento_materia_prima | id_usuario | usuario | CASCADE | RESTRICT |
| movimiento_materia_prima | id_recepcion | recepcion_mercancia | CASCADE | SET NULL |
| movimiento_materia_prima | id_orden_produccion | orden_produccion | CASCADE | SET NULL |
| lista_materiales | id_producto | producto | CASCADE | CASCADE |
| lista_materiales | id_materia_prima | materia_prima | CASCADE | RESTRICT |
| orden_produccion | id_producto | producto | CASCADE | RESTRICT |
| orden_produccion | id_bodega_destino | bodega | CASCADE | RESTRICT |
| orden_produccion | id_usuario_registro | usuario | CASCADE | RESTRICT |
| orden_produccion | id_usuario_completa | usuario | CASCADE | SET NULL |
| orden_produccion_consumo | id_orden_produccion | orden_produccion | CASCADE | CASCADE |
| orden_produccion_consumo | id_materia_prima | materia_prima | CASCADE | RESTRICT |

## Módulos Principales

### Módulo Maestros
- ciudad, categoria, unidad_medida, proveedor, bodega

### Módulo Usuarios
- usuario, rol, usuario_rol, permiso, rol_permiso

### Módulo Productos e Inventario
- producto, producto_proveedor, inventario, movimiento_inventario, historial_inventario, comprobante_interno

### Módulo Pedidos
- cliente, pedido, detalle_pedido

## Funciones y Triggers

> ⚠️ **VERIFICADO CONTRA LA BD REAL (F32).** Esta lista se corrigió consultando
> `pg_proc` y `pg_trigger`. La versión anterior arrastraba nombres heredados que
> **no existen** en la base (`fn_generar_numero_pedido`, `fn_actualizar_total_pedido`,
> `fn_generar_numero_comprobante`, `fn_registrar_historial_inventario`,
> `fn_aplicar_movimiento_inventario`, `fn_validar_stock_pedido`) y omitía 6 que sí
> existen. Totales reales: **17 funciones y 24 triggers**.

### Funciones (17)

**Base F1–F20 (7)**
1. **fn_recalcular_total_pedido()** — Recalcula `pedido.total` sumando subtotales de detalles
2. **fn_recalcular_total_pedido_stmt()** — Versión statement-level, usada por los triggers de INSERT/UPDATE de `detalle_pedido`
3. **fn_recalcular_total_pedido_delete()** — Recálculo tras DELETE de `detalle_pedido`
4. **fn_recalcular_total_por_descuento()** — Recalcula el total del pedido cuando cambia el descuento
5. **fn_proteger_total_pedido()** — Impide UPDATE manual de `pedido.total` (**corregido en F32**; antes usaba `pg_trigger_depth() = 0` y no protegía)
6. **fn_trg_historial_inventario()** — Registra en `historial_inventario` los cambios de `inventario` (lee `app.current_user_id`)
7. **fn_set_updated_at()** — Setea `updated_at` en cliente, pedido, producto, proveedor y usuario
8. **fn_validar_total_comprobante()** — Valida el total del comprobante interno

**Bloques nuevos F21–F29 (9)**
9. **fn_recalcular_total_orden_compra_stmt()** (F21) — Recalcula orden_compra.total sumando subtotales (statement-level)
10. **fn_proteger_total_orden_compra()** (F21) — Impide UPDATE manual de orden_compra.total
11. **fn_recalcular_monto_pagado_cxp()** (F23) — Recalcula cuenta_por_pagar.monto_pagado tras cada pago; si pagada, marca factura también
12. **fn_proteger_monto_pagado_cxp()** (F23) — Impide UPDATE manual de cuenta_por_pagar.monto_pagado
13. **fn_validar_bom_producto_fabricado()** (F27) — Impide insertar/actualizar líneas de `lista_materiales` si el producto no tiene origen='fabricado'
14. **fn_validar_cambio_origen_producto()** (F27) — Impide cambiar `producto.origen` a 'comprado' si el producto tiene BOM activo
15. **fn_validar_op_producto_fabricado()** (F28) — Impide crear/actualizar `orden_produccion` si el producto no tiene origen='fabricado'
16. **fn_proteger_costo_materia_prima_op()** (F29) — Solo permite escribir `orden_produccion.costo_materia_prima` si coincide con SUM(costo_linea) de sus consumos
17. **fn_set_costo_materia_prima_op(id)** (F29) — Función usada por el servicio: calcula SUM(costo_linea) y actualiza `costo_materia_prima` (satisface el trigger por construcción)

### Triggers (24)

**Base F1–F20 (11)**
1. **trg_recalcular_total_pedido_insert** → AFTER INSERT ON detalle_pedido → fn_recalcular_total_pedido_stmt
2. **trg_recalcular_total_pedido_update** → AFTER UPDATE ON detalle_pedido → fn_recalcular_total_pedido_stmt
3. **trg_recalcular_total_pedido_delete** → AFTER DELETE ON detalle_pedido → fn_recalcular_total_pedido_delete
4. **trg_recalcular_total_por_descuento** → ON pedido → fn_recalcular_total_por_descuento
5. **trg_proteger_total_pedido** → BEFORE UPDATE ON pedido → fn_proteger_total_pedido (**corregido en F32**)
6. **trg_historial_inventario** → ON inventario → fn_trg_historial_inventario
7. **trg_validar_total_comprobante** → ON comprobante_interno → fn_validar_total_comprobante
8. **trg_cliente_updated_at** → ON cliente → fn_set_updated_at
9. **trg_pedido_updated_at** → ON pedido → fn_set_updated_at
10. **trg_producto_updated_at** → ON producto → fn_set_updated_at
11. **trg_proveedor_updated_at** → ON proveedor → fn_set_updated_at
12. **trg_usuario_updated_at** → ON usuario → fn_set_updated_at

**Bloques nuevos F21–F29 (12)**
13. **trg_oc_total_insert** (F21) → AFTER INSERT ON orden_compra_detalle → fn_recalcular_total_orden_compra_stmt
14. **trg_oc_total_update** (F21) → AFTER UPDATE ON orden_compra_detalle → fn_recalcular_total_orden_compra_stmt
15. **trg_oc_total_delete** (F21) → AFTER DELETE ON orden_compra_detalle → fn_recalcular_total_orden_compra_stmt
16. **trg_proteger_total_oc** (F21) → BEFORE UPDATE ON orden_compra → fn_proteger_total_orden_compra
17. **trg_cxp_pagado_insert** (F23) → AFTER INSERT ON pago_proveedor → fn_recalcular_monto_pagado_cxp
18. **trg_cxp_pagado_update** (F23) → AFTER UPDATE ON pago_proveedor → fn_recalcular_monto_pagado_cxp
19. **trg_cxp_pagado_delete** (F23) → AFTER DELETE ON pago_proveedor → fn_recalcular_monto_pagado_cxp
20. **trg_proteger_monto_pagado_cxp** (F23) → BEFORE UPDATE ON cuenta_por_pagar → fn_proteger_monto_pagado_cxp
21. **trg_validar_bom_producto_fabricado** (F27) → BEFORE INSERT OR UPDATE ON lista_materiales → fn_validar_bom_producto_fabricado
22. **trg_validar_cambio_origen_producto** (F27) → BEFORE UPDATE OF origen ON producto → fn_validar_cambio_origen_producto
23. **trg_validar_op_producto_fabricado** (F28) → BEFORE INSERT OR UPDATE ON orden_produccion → fn_validar_op_producto_fabricado
24. **trg_proteger_costo_materia_prima_op** (F29) → BEFORE UPDATE OF costo_materia_prima ON orden_produccion → fn_proteger_costo_materia_prima_op

> **Patrón de los triggers de protección (F32):** todos comparan el nuevo valor contra
> el **valor real recalculado** y rechazan si difiere. NO usan `pg_trigger_depth() = 0`,
> porque dentro de una función de trigger esa función devuelve 1 (nunca 0) y por tanto
> la condición jamás se cumple: la protección no protegería nada. Este bug afectaba a
> `fn_proteger_total_pedido` y se corrigió en la Fase 32.

### Corrección aplicada en F32 — `fn_proteger_total_pedido`

Script: **`marathon-backend/sql/fase32_fixes.sql`** (idempotente, con autoverificación).

**Antes** (roto): la excepción estaba condicionada a `pg_trigger_depth() = 0`, así que
`UPDATE pedido SET total = 9999` pasaba sin error. Comprobado empíricamente.

**Ahora** (correcto): compara contra el total real recalculado.

```sql
IF NEW.total IS DISTINCT FROM OLD.total THEN
    SELECT GREATEST(COALESCE(SUM(d.subtotal), 0) - COALESCE(NEW.descuento, 0), 0)
      INTO v_total_real
    FROM detalle_pedido d WHERE d.id_pedido = NEW.id_pedido;

    IF NEW.total IS DISTINCT FROM v_total_real THEN
        RAISE EXCEPTION 'El campo pedido.total es calculado automáticamente ...';
    END IF;
END IF;
```

> ⚠️ **La fórmula de referencia es NETA de descuento**, igual que
> `fn_recalcular_total_por_descuento`: `GREATEST(SUM(subtotal) − descuento, 0)`.
> Comparar contra la suma bruta de subtotales haría que el propio trigger de recálculo
> legítimo fuera rechazado en **todo pedido con descuento**, rompiendo el sistema.
> Si alguna vez hay que volver a tocar este trigger, ese es el detalle que importa.

**Estado verificado en F32:** de las 17 funciones de `public`, **ninguna** contiene
`pg_trigger_depth`. Los otros 6 protectores ya usaban el patrón correcto y no se
modificaron. La secuencia de instalación exige aplicar `fase32_fixes.sql` como último
paso (ver `SETUP_COMPLETO.md`); sin él la BD queda con el bug.
