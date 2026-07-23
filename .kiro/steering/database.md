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
| Columna | Tipo | Notas |
|---------|------|-------|
| id_rol | SERIAL PK | |
| nombre | VARCHAR(50) NOT NULL UNIQUE | |
| descripcion | TEXT | |
| estado | VARCHAR(20) DEFAULT 'activo' | |
| created_at | TIMESTAMP DEFAULT NOW() | |

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
| Columna | Tipo | Notas |
|---------|------|-------|
| id_inventario | SERIAL PK | |
| id_producto | INT FK → producto | |
| id_bodega | INT FK → bodega | |
| cantidad | INT NOT NULL DEFAULT 0 | |
| UNIQUE(id_producto, id_bodega) | | Un producto por bodega |
| updated_at | TIMESTAMP DEFAULT NOW() | |

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
| Columna | Tipo | Notas |
|---------|------|-------|
| id_comprobante | SERIAL PK | |
| numero_comprobante | VARCHAR(20) NOT NULL UNIQUE | Generado por trigger |
| tipo | VARCHAR(30) NOT NULL | entrada/salida/transferencia |
| id_bodega_origen | INT FK → bodega | NULL si es entrada |
| id_bodega_destino | INT FK → bodega | NULL si es salida |
| id_usuario | INT FK → usuario | |
| fecha | TIMESTAMP DEFAULT NOW() | |
| observaciones | TEXT | |
| estado | VARCHAR(20) DEFAULT 'activo' | |

### rol_permiso
| Columna | Tipo | Notas |
|---------|------|-------|
| id_rol_permiso | SERIAL PK | |
| id_rol | INT FK → rol | ON DELETE CASCADE |
| id_permiso | INT FK → permiso | ON DELETE CASCADE |
| UNIQUE(id_rol, id_permiso) | | |

### movimiento_inventario
| Columna | Tipo | Notas |
|---------|------|-------|
| id_movimiento | SERIAL PK | |
| id_comprobante | INT FK → comprobante_interno | |
| id_producto | INT FK → producto | |
| id_bodega | INT FK → bodega | |
| tipo_movimiento | VARCHAR(20) NOT NULL | entrada/salida |
| cantidad | INT NOT NULL CHECK(>0) | |
| id_usuario | INT FK → usuario | |
| fecha | TIMESTAMP DEFAULT NOW() | |

### historial_inventario
| Columna | Tipo | Notas |
|---------|------|-------|
| id_historial | SERIAL PK | |
| id_inventario | INT FK → inventario | |
| cantidad_anterior | INT | |
| cantidad_nueva | INT | |
| id_usuario | INT | Registrado via SET app.current_user_id |
| fecha_cambio | TIMESTAMP DEFAULT NOW() | |
| tipo_operacion | VARCHAR(20) | INSERT/UPDATE/DELETE |

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

### Funciones (8)
1. **fn_generar_numero_pedido()** — Genera número secuencial para pedidos (PED-000001)
2. **fn_actualizar_total_pedido()** — Recalcula pedido.total sumando subtotales de detalles
3. **fn_generar_numero_comprobante()** — Genera número secuencial para comprobantes (COMP-000001)
4. **fn_registrar_historial_inventario()** — Registra cambios en historial cuando se modifica inventario
5. **fn_aplicar_movimiento_inventario()** — Actualiza cantidad en inventario al insertar movimiento
6. **fn_validar_stock_pedido()** — Valida que haya stock suficiente antes de crear detalle_pedido
7. **fn_recalcular_total_orden_compra_stmt()** (F21) — Recalcula orden_compra.total sumando subtotales (statement-level)
8. **fn_proteger_total_orden_compra()** (F21) — Impide UPDATE manual de orden_compra.total

### Triggers (15)
1. **trg_numero_pedido** → BEFORE INSERT ON pedido → fn_generar_numero_pedido
2. **trg_actualizar_total_insert** → AFTER INSERT ON detalle_pedido → fn_actualizar_total_pedido
3. **trg_actualizar_total_update** → AFTER UPDATE ON detalle_pedido → fn_actualizar_total_pedido
4. **trg_actualizar_total_delete** → AFTER DELETE ON detalle_pedido → fn_actualizar_total_pedido
5. **trg_numero_comprobante** → BEFORE INSERT ON comprobante_interno → fn_generar_numero_comprobante
6. **trg_historial_inventario_insert** → AFTER INSERT ON inventario → fn_registrar_historial_inventario
7. **trg_historial_inventario_update** → AFTER UPDATE ON inventario → fn_registrar_historial_inventario
8. **trg_historial_inventario_delete** → AFTER DELETE ON inventario → fn_registrar_historial_inventario
9. **trg_aplicar_movimiento** → AFTER INSERT ON movimiento_inventario → fn_aplicar_movimiento_inventario
10. **trg_validar_stock_insert** → BEFORE INSERT ON detalle_pedido → fn_validar_stock_pedido
11. **trg_validar_stock_update** → BEFORE UPDATE ON detalle_pedido → fn_validar_stock_pedido
12. **trg_oc_total_insert** (F21) → AFTER INSERT ON orden_compra_detalle → fn_recalcular_total_orden_compra_stmt
13. **trg_oc_total_update** (F21) → AFTER UPDATE ON orden_compra_detalle → fn_recalcular_total_orden_compra_stmt
14. **trg_oc_total_delete** (F21) → AFTER DELETE ON orden_compra_detalle → fn_recalcular_total_orden_compra_stmt
15. **trg_proteger_total_oc** (F21) → BEFORE UPDATE ON orden_compra → fn_proteger_total_orden_compra
