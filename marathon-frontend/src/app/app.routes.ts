import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { rolGuard } from './core/guards/rol.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./modules/auth/login/login.component').then(m => m.LoginComponent) },
  // F62 — el inicio es EL FLUJO, y es a donde lleva el login.
  //   El tablero de indicadores contesta «cómo va todo», que es una pregunta
  //   que solo sabe hacerse quien ya conoce el sistema. Quien entra por primera
  //   vez tiene otra —«¿y ahora qué hago, y en qué orden?»— y no la contestaba
  //   ni el menú lateral, que agrupa por módulo, ni las cifras. Ahora la
  //   contesta la pantalla de inicio.
  //
  //   Los indicadores NO desaparecen: viven en /indicadores, y el propio flujo
  //   los ofrece como primera opción de su último paso. /dashboard y /portal
  //   siguen respondiendo para no romper enlaces guardados.
  { path: 'inicio', loadComponent: () => import('./modules/flujo/flujo.component').then(m => m.FlujoComponent), canActivate: [authGuard] },
  { path: 'indicadores', loadComponent: () => import('./modules/dashboard/dashboard.component').then(m => m.DashboardComponent), canActivate: [authGuard] },
  { path: 'dashboard', redirectTo: 'indicadores' },
  { path: 'portal', loadComponent: () => import('./modules/portal/portal.component').then(m => m.PortalComponent), canActivate: [authGuard] },
  { path: 'perfil', loadComponent: () => import('./modules/auth/perfil/perfil.component').then(m => m.PerfilComponent), canActivate: [authGuard] },
  {
    path: 'datos-maestros',
    loadComponent: () => import('./modules/datos-maestros/datos-maestros.component').then(m => m.DatosMaestrosComponent),
    canActivate: [authGuard, rolGuard], data: { rol: 'Administrador' },
    children: [
      // D5 — seis pestañas. Productos, Bodegas y Proveedores estaban sueltos en
      //   el menú principal, entre pantallas de operación diaria, cuando son
      //   maestros igual que las otras tres. Sus rutas antiguas siguen vivas más
      //   abajo para no romper enlaces guardados.
      { path: '', redirectTo: 'productos', pathMatch: 'full' },
      { path: 'productos', loadComponent: () => import('./modules/productos/productos.component').then(m => m.ProductosComponent) },
      { path: 'categorias', loadComponent: () => import('./modules/datos-maestros/categorias/categorias.component').then(m => m.CategoriasComponent) },
      { path: 'unidades-medida', loadComponent: () => import('./modules/datos-maestros/unidades-medida/unidades-medida.component').then(m => m.UnidadesMedidaComponent) },
      { path: 'ciudades', loadComponent: () => import('./modules/datos-maestros/ciudades/ciudades.component').then(m => m.CiudadesComponent) },
      { path: 'bodegas', loadComponent: () => import('./modules/bodegas/bodegas.component').then(m => m.BodegasComponent) },
      { path: 'proveedores', loadComponent: () => import('./modules/proveedores/proveedores.component').then(m => m.ProveedoresComponent) }
    ]
  },
  { path: 'usuarios', loadComponent: () => import('./modules/usuarios/usuarios.component').then(m => m.UsuariosComponent), canActivate: [authGuard, rolGuard], data: { rol: 'Administrador' } },
  { path: 'roles', loadComponent: () => import('./modules/roles/roles.component').then(m => m.RolesComponent), canActivate: [authGuard, rolGuard], data: { rol: 'Administrador' } },
  { path: 'proveedores', loadComponent: () => import('./modules/proveedores/proveedores.component').then(m => m.ProveedoresComponent), canActivate: [authGuard, rolGuard], data: { rol: 'Administrador' } },
  { path: 'productos', loadComponent: () => import('./modules/productos/productos.component').then(m => m.ProductosComponent), canActivate: [authGuard, rolGuard], data: { rol: 'Administrador' } },
  { path: 'bodegas', loadComponent: () => import('./modules/bodegas/bodegas.component').then(m => m.BodegasComponent), canActivate: [authGuard, rolGuard], data: { rol: 'Administrador' } },
  { path: 'inventario', loadComponent: () => import('./modules/inventario/inventario.component').then(m => m.InventarioComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Operador de Bodega', 'Supervisor E-Commerce', 'Encargado de Compras'] } },
  { path: 'clientes', loadComponent: () => import('./modules/clientes/clientes.component').then(m => m.ClientesComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Operador de Pedidos', 'Supervisor E-Commerce'] } },
  { path: 'pedidos', loadComponent: () => import('./modules/pedidos/pedidos.component').then(m => m.PedidosComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Supervisor E-Commerce', 'Operador de Pedidos', 'Operador de Bodega'] } },
  { path: 'pedidos/nuevo', loadComponent: () => import('./modules/pedidos/pedido-nuevo/pedido-nuevo.component').then(m => m.PedidoNuevoComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Operador de Pedidos'] } },
  { path: 'pedidos/especiales', loadComponent: () => import('./modules/pedidos/pedidos-especiales/pedidos-especiales.component').then(m => m.PedidosEspecialesComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Supervisor E-Commerce', 'Operador de Pedidos', 'Operador de Bodega'] } },
  { path: 'pedidos/:id', loadComponent: () => import('./modules/pedidos/pedido-detalle/pedido-detalle.component').then(m => m.PedidoDetalleComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Supervisor E-Commerce', 'Operador de Pedidos', 'Operador de Bodega'] } },
  { path: 'comprobantes', loadComponent: () => import('./modules/comprobantes/comprobantes-lista/comprobantes-lista.component').then(m => m.ComprobantesListaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Supervisor E-Commerce', 'Operador de Pedidos', 'Operador de Bodega'] } },
  // F31 — alineado con el backend (/api/picking/** = Admin + Operador de Bodega)
  // y con el navbar. Antes cualquier autenticado entraba y recibía 403.
  { path: 'picking', loadComponent: () => import('./modules/picking/picking-lista/picking-lista.component').then(m => m.PickingListaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Operador de Bodega'] } },
  { path: 'picking/:idPedido', loadComponent: () => import('./modules/picking/picking-ejecucion/picking-ejecucion.component').then(m => m.PickingEjecucionComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Operador de Bodega'] } },
  { path: 'empaque', loadComponent: () => import('./modules/empaque/empaque-lista/empaque-lista.component').then(m => m.EmpaqueListaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Operador de Bodega'] } },
  { path: 'despachos', loadComponent: () => import('./modules/empaque/despacho-lista/despacho-lista.component').then(m => m.DespachoListaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Operador de Bodega', 'Supervisor E-Commerce'] } },
  { path: 'compras', loadComponent: () => import('./modules/compras/ordenes-compra.component').then(m => m.OrdenesCompraComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras'] } },
  { path: 'compras/nueva', loadComponent: () => import('./modules/compras/orden-compra-nueva.component').then(m => m.OrdenCompraNuevaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras'] } },
  { path: 'compras/:id/recepcion', loadComponent: () => import('./modules/compras/recepcion-nueva.component').then(m => m.RecepcionNuevaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras'] } },
  // F66 — /compras/:id/factura ya no existe. El boton de la orden documenta lo
  //   recibido y abre el PDF en el acto: numero, fechas, subtotal e impuesto se
  //   deducen de la orden y de sus recepciones, asi que el formulario pedia
  //   datos que el sistema ya tenia. Para registrar la factura REAL del
  //   proveedor, con su numero y su importe, sigue estando POST
  //   /api/facturas-compra.
  { path: 'compras/:id', loadComponent: () => import('./modules/compras/orden-compra-detalle.component').then(m => m.OrdenCompraDetalleComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras'] } },
  { path: 'cuentas-por-pagar', loadComponent: () => import('./modules/compras/cuentas-por-pagar.component').then(m => m.CuentasPorPagarComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras', 'Supervisor E-Commerce'] } },
  { path: 'cuentas-por-pagar/:id', loadComponent: () => import('./modules/compras/cuenta-por-pagar-detalle.component').then(m => m.CuentaPorPagarDetalleComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras', 'Supervisor E-Commerce'] } },
  { path: 'materia-prima', loadComponent: () => import('./modules/materia-prima/materia-prima.component').then(m => m.MateriaPrimaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Producción', 'Encargado de Compras'] } },
  { path: 'materia-prima/:id/kardex', loadComponent: () => import('./modules/materia-prima/kardex-materia-prima.component').then(m => m.KardexMateriaPrimaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Producción', 'Encargado de Compras'] } },
  { path: 'devoluciones', loadComponent: () => import('./modules/devoluciones/devoluciones-lista.component').then(m => m.DevolucionesListaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Supervisor E-Commerce', 'Operador de Pedidos', 'Operador de Bodega'] } },
  { path: 'devoluciones/nueva/:idPedido', loadComponent: () => import('./modules/devoluciones/solicitud-devolucion-nueva.component').then(m => m.SolicitudDevolucionNuevaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Operador de Pedidos'] } },
  { path: 'devoluciones/:id', loadComponent: () => import('./modules/devoluciones/devolucion-detalle.component').then(m => m.DevolucionDetalleComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Supervisor E-Commerce', 'Operador de Pedidos', 'Operador de Bodega'] } },
  { path: 'devoluciones-proveedor', loadComponent: () => import('./modules/devoluciones-proveedor/devoluciones-proveedor-lista.component').then(m => m.DevolucionesProveedorListaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras'] } },
  { path: 'devoluciones-proveedor/pendientes', loadComponent: () => import('./modules/devoluciones-proveedor/items-defectuosos.component').then(m => m.ItemsDefectuososComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras'] } },
  { path: 'devoluciones-proveedor/:id', loadComponent: () => import('./modules/devoluciones-proveedor/devolucion-proveedor-detalle.component').then(m => m.DevolucionProveedorDetalleComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras'] } },
  { path: 'produccion', loadComponent: () => import('./modules/produccion/ordenes-produccion-lista.component').then(m => m.OrdenesProduccionListaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Producción'] } },
  { path: 'produccion/nueva', loadComponent: () => import('./modules/produccion/orden-produccion-nueva.component').then(m => m.OrdenProduccionNuevaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Producción'] } },
  { path: 'produccion/costos', loadComponent: () => import('./modules/produccion/analisis-costos.component').then(m => m.AnalisisCostosComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Supervisor E-Commerce', 'Encargado de Producción'] } },
  { path: 'produccion/dashboard', loadComponent: () => import('./modules/produccion/dashboard-manufactura.component').then(m => m.DashboardManufacturaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Supervisor E-Commerce', 'Encargado de Producción'] } },
  { path: 'produccion/:id', loadComponent: () => import('./modules/produccion/orden-produccion-detalle.component').then(m => m.OrdenProduccionDetalleComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Producción'] } },
  { path: 'analitica', loadComponent: () => import('./modules/analitica/analitica.component').then(m => m.AnaliticaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Supervisor E-Commerce'] } },
  { path: 'reportes', loadComponent: () => import('./modules/reportes/reportes.component').then(m => m.ReportesComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Supervisor E-Commerce'] } },
  { path: 'ia', loadComponent: () => import('./modules/ia/ia-chat.component').then(m => m.IAChatComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Supervisor E-Commerce'] } },
  { path: 'auditoria', loadComponent: () => import('./modules/auditoria/auditoria.component').then(m => m.AuditoriaComponent), canActivate: [authGuard, rolGuard], data: { rol: 'Administrador' } },
  // F92 — respaldar, borrar y restaurar desde la web. El Supervisor entra a
  //   MIRAR el diario (el backend le da 'respaldos:ver' y SELECT sobre el
  //   esquema control); los tres botones que destruyen algo exigen permisos que
  //   solo tiene el Administrador, y eso lo decide el servidor, no esta ruta.
  { path: 'respaldos', loadComponent: () => import('./modules/respaldos/respaldos.component').then(m => m.RespaldosComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Supervisor E-Commerce'] } },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' }
];
