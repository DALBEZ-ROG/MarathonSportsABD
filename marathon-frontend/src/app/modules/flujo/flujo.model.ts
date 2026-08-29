/**
 * El flujo del sistema, escrito una sola vez.
 *
 * Marathon Sports no es un menú de pantallas sueltas: es una cadena que empieza
 * en el catálogo y termina en la auditoría, y cada eslabón depende del anterior.
 * No se puede comprar sin proveedores, ni vender sin stock, ni despachar sin
 * haber recogido. El menú lateral nunca contó eso —lista pantallas agrupadas por
 * módulo—, así que quien llegaba nuevo tenía que deducir el orden.
 *
 * Esta tabla es esa cadena. El orden de los pasos, y el de las opciones dentro
 * de cada paso, **es el orden real de trabajo**: no es una agrupación temática.
 *
 * Los roles de cada tarjeta salen de `app.routes.ts`, y son los mismos que deja
 * pasar el `rolGuard`. Por eso una tarjeta que aquí aparece abierta se abre de
 * verdad, y una con candado habría respondido «no tienes acceso a esa sección».
 * Si algún día cambian los roles de una ruta, hay que cambiarlos también aquí o
 * el tablero mentirá.
 */
export interface OpcionFlujo {
  nombre: string;
  descripcion: string;
  ruta: string;
  /** Roles que pueden entrar. Copiado de app.routes.ts. */
  roles: string[];
  /** La acción que *arranca* el paso. Se destaca sobre las demás. */
  principal?: boolean;
}

export interface PasoFlujo {
  numero: number;
  titulo: string;
  /** Qué ocurre en este paso, en una línea. */
  resumen: string;
  /** Quién es responsable. Es el contenido del icono de información. */
  responsable: string;
  /** Qué le corresponde hacer a ese rol aquí. */
  incumbencia: string;
  /** Lo que no es una pantalla y, si no se dice, se busca en vano. */
  nota?: string;
  opciones: OpcionFlujo[];
}

const ADMIN = 'Administrador';
const SUPERVISOR = 'Supervisor E-Commerce';
const BODEGA = 'Operador de Bodega';
const PEDIDOS = 'Operador de Pedidos';
const COMPRAS = 'Encargado de Compras';
const PRODUCCION = 'Encargado de Producción';

export const FLUJO: PasoFlujo[] = [
  {
    numero: 1,
    titulo: 'Preparar el catálogo',
    resumen: 'Antes de comprar o de vender, el sistema tiene que saber qué se vende, dónde se guarda y a quién se le compra.',
    responsable: ADMIN,
    incumbencia: 'Solo entra el Administrador. Da de alta productos, categorías, unidades, ciudades, bodegas y proveedores, y decide quién usa el sistema y con qué permisos. Todo lo que viene después depende de que esto esté puesto.',
    nota: 'Un producto sin proveedor no se puede comprar, y sin bodega no se puede almacenar. Si más adelante una pantalla aparece vacía, casi siempre falta algo de aquí.',
    opciones: [
      { nombre: 'Datos maestros', descripcion: 'Productos, categorías, unidades, ciudades, bodegas y proveedores', ruta: '/datos-maestros', roles: [ADMIN], principal: true },
      { nombre: 'Usuarios', descripcion: 'Quién entra al sistema', ruta: '/usuarios', roles: [ADMIN] },
      { nombre: 'Roles y permisos', descripcion: 'Qué puede hacer cada quien', ruta: '/roles', roles: [ADMIN] }
    ]
  },
  {
    numero: 2,
    titulo: 'Comprar al proveedor',
    resumen: 'La mercancía entra por aquí: se pide, llega, se factura y se paga.',
    responsable: COMPRAS,
    incumbencia: 'El Encargado de Compras crea la orden, registra lo que llega, la factura del proveedor y los pagos. Pero NO la aprueba: aprobar y rechazar son del Administrador. Y quien solicita una orden no puede aprobarla, aunque tenga el permiso; el Administrador es la única excepción y sí puede aprobar la suya.',
    nota: 'Recibir la mercancía y registrar la factura NO son opciones del menú: se hacen entrando a la orden concreta desde «Órdenes de compra». Es el orden correcto, porque no se puede facturar lo que todavía no ha llegado.',
    opciones: [
      { nombre: 'Nueva orden de compra', descripcion: 'Pedir mercancía a un proveedor', ruta: '/compras/nueva', roles: [ADMIN, COMPRAS], principal: true },
      { nombre: 'Órdenes de compra', descripcion: 'Seguirlas, aprobarlas, recibir y facturar', ruta: '/compras', roles: [ADMIN, COMPRAS] },
      { nombre: 'Cuentas por pagar', descripcion: 'Lo que se le debe a cada proveedor', ruta: '/cuentas-por-pagar', roles: [ADMIN, COMPRAS, SUPERVISOR] },
      { nombre: 'Ítems defectuosos', descripcion: 'Lo que llegó mal y espera decisión', ruta: '/devoluciones-proveedor/pendientes', roles: [ADMIN, COMPRAS] },
      { nombre: 'Devoluciones a proveedor', descripcion: 'Lo que se devuelve, y por qué', ruta: '/devoluciones-proveedor', roles: [ADMIN, COMPRAS] }
    ]
  },
  {
    numero: 3,
    titulo: 'Fabricar lo propio',
    resumen: 'Lo que Marathon no compra hecho lo produce, a partir de materia prima.',
    responsable: PRODUCCION,
    incumbencia: 'El Encargado de Producción gestiona la materia prima y lanza las órdenes de producción. Solo puede fabricar productos de marca propia que además tengan definida su lista de materiales.',
    nota: 'Este paso solo vale para la marca propia. Lo de marca ajena —Nike, Adidas— se compra en el paso 2, y el sistema no deja crearle una orden de producción.',
    opciones: [
      { nombre: 'Nueva orden de producción', descripcion: 'Lanzar una fabricación', ruta: '/produccion/nueva', roles: [ADMIN, PRODUCCION], principal: true },
      { nombre: 'Órdenes de producción', descripcion: 'Iniciarlas, completarlas, cancelarlas', ruta: '/produccion', roles: [ADMIN, PRODUCCION] },
      { nombre: 'Materia prima', descripcion: 'Existencias y kardex de insumos', ruta: '/materia-prima', roles: [ADMIN, PRODUCCION, COMPRAS] },
      { nombre: 'Tablero de manufactura', descripcion: 'Cómo va la producción ahora mismo', ruta: '/produccion/dashboard', roles: [ADMIN, SUPERVISOR, PRODUCCION] },
      { nombre: 'Análisis de costos', descripcion: 'Cuánto cuesta de verdad fabricar', ruta: '/produccion/costos', roles: [ADMIN, SUPERVISOR, PRODUCCION] }
    ]
  },
  {
    numero: 4,
    titulo: 'Controlar las existencias',
    resumen: 'Todo lo que se compró o se fabricó queda aquí, repartido por bodega.',
    responsable: BODEGA,
    incumbencia: 'El Operador de Bodega mueve stock entre bodegas y ajusta lo que no cuadra. Es el punto donde se ve lo que hay disponible de verdad: el stock menos lo ya reservado por pedidos en curso.',
    nota: 'Disponible no es lo mismo que stock. Un pedido procesado reserva unidades que siguen físicamente en la bodega, pero que ya tienen dueño.',
    opciones: [
      { nombre: 'Inventario', descripcion: 'Stock por bodega, movimientos y reservas', ruta: '/inventario', roles: [ADMIN, BODEGA, SUPERVISOR, COMPRAS], principal: true }
    ]
  },
  {
    numero: 5,
    titulo: 'Vender: tomar el pedido',
    resumen: 'Entra el pedido del cliente y se comprueba que haya con qué cumplirlo.',
    responsable: PEDIDOS,
    incumbencia: 'El Operador de Pedidos da de alta clientes y registra pedidos. Al crear el pedido se comprueba el stock; al pasarlo a procesado se reservan las unidades. También emite los comprobantes.',
    nota: 'Un pedido normal no se crea si no hay stock. Los especiales —personalizado, corporativo y regalo— sí, porque existen precisamente para prepararse o fabricarse.',
    opciones: [
      { nombre: 'Nuevo pedido', descripcion: 'Registrar el pedido de un cliente', ruta: '/pedidos/nuevo', roles: [ADMIN, PEDIDOS], principal: true },
      { nombre: 'Pedidos', descripcion: 'Seguirlos y cambiarles el estado', ruta: '/pedidos', roles: [ADMIN, SUPERVISOR, PEDIDOS, BODEGA] },
      { nombre: 'Clientes', descripcion: 'A quién se le vende', ruta: '/clientes', roles: [ADMIN, PEDIDOS, SUPERVISOR] },
      { nombre: 'Pedidos especiales', descripcion: 'Personalizados, corporativos y de regalo', ruta: '/pedidos/especiales', roles: [ADMIN, SUPERVISOR, PEDIDOS, BODEGA] },
      { nombre: 'Comprobantes', descripcion: 'Documentos emitidos al cliente', ruta: '/comprobantes', roles: [ADMIN, SUPERVISOR, PEDIDOS, BODEGA] }
    ]
  },
  {
    numero: 6,
    titulo: 'Preparar y entregar',
    resumen: 'El pedido se recoge de la bodega, se empaqueta y sale.',
    responsable: BODEGA,
    incumbencia: 'El Operador de Bodega ejecuta el picking, confirma el empaque y despacha. Aquí es donde el stock baja de verdad: hasta el despacho las unidades estaban reservadas, pero seguían contando.',
    nota: 'El orden es obligatorio y lo impone el sistema: primero picking, luego empaque, luego despacho. Un pedido no aparece en la cola de empaque hasta que está recogido del todo.',
    opciones: [
      { nombre: 'Picking', descripcion: 'Recoger de la bodega lo que pide el pedido', ruta: '/picking', roles: [ADMIN, BODEGA], principal: true },
      { nombre: 'Empaque', descripcion: 'Empaquetar lo que ya está recogido', ruta: '/empaque', roles: [ADMIN, BODEGA] },
      { nombre: 'Despachos', descripcion: 'Lo que ya salió, y hacia dónde', ruta: '/despachos', roles: [ADMIN, BODEGA, SUPERVISOR] }
    ]
  },
  {
    numero: 7,
    titulo: 'Atender la posventa',
    resumen: 'Lo que el cliente devuelve: se inspecciona, se decide y se reembolsa.',
    responsable: BODEGA,
    incumbencia: 'Va y viene entre dos roles: el Operador de Pedidos abre la solicitud desde el pedido y, al final, autoriza el reembolso; en medio, el Operador de Bodega es quien inspecciona lo que ha vuelto y decide si se acepta. El Supervisor E-Commerce lo ve todo, pero no interviene.',
    nota: 'Los tres pasos son de roles distintos a propósito, y el sistema lo impone con permisos separados: crear, inspeccionar y reembolsar. Quien pide la devolución no es quien juzga el estado de la mercancía.',
    opciones: [
      { nombre: 'Devoluciones', descripcion: 'Solicitudes, inspección y reembolsos', ruta: '/devoluciones', roles: [ADMIN, SUPERVISOR, PEDIDOS, BODEGA], principal: true }
    ]
  },
  {
    numero: 8,
    titulo: 'Medir y auditar',
    resumen: 'Con el ciclo cerrado, se mira qué pasó y quién hizo qué.',
    responsable: SUPERVISOR,
    incumbencia: 'El Supervisor E-Commerce vive en los indicadores y los reportes. La auditoría es solo del Administrador: es la traza de quién tocó qué, y por eso no la ve quien opera.',
    nota: 'Dos lecturas distintas: los indicadores contestan «cómo va todo ahora» y se miran de pie; el análisis contesta «qué está pasando» —qué se vende, quién compra, dónde— y se mira sentado, cambiando el período.',
    opciones: [
      { nombre: 'Indicadores', descripcion: 'El tablero de cifras de tu rol', ruta: '/indicadores', roles: [ADMIN, SUPERVISOR, BODEGA, PEDIDOS, COMPRAS, PRODUCCION], principal: true },
      { nombre: 'Análisis del negocio', descripcion: 'Lo más vendido, quién deja más y dónde se vende', ruta: '/analitica', roles: [ADMIN, SUPERVISOR] },
      { nombre: 'Reportes', descripcion: 'Ventas, inventario y manufactura', ruta: '/reportes', roles: [ADMIN, SUPERVISOR] },
      { nombre: 'Asistente IA', descripcion: 'Preguntar a los datos en castellano', ruta: '/ia', roles: [ADMIN, SUPERVISOR] },
      { nombre: 'Auditoría', descripcion: 'Quién hizo qué, y cuándo', ruta: '/auditoria', roles: [ADMIN] }
    ]
  }
];
