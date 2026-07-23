import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { rolGuard } from './core/guards/rol.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./modules/auth/login/login.component').then(m => m.LoginComponent) },
  { path: 'dashboard', loadComponent: () => import('./modules/dashboard/dashboard.component').then(m => m.DashboardComponent), canActivate: [authGuard] },
  { path: 'perfil', loadComponent: () => import('./modules/auth/perfil/perfil.component').then(m => m.PerfilComponent), canActivate: [authGuard] },
  {
    path: 'datos-maestros',
    loadComponent: () => import('./modules/datos-maestros/datos-maestros.component').then(m => m.DatosMaestrosComponent),
    canActivate: [authGuard, rolGuard], data: { rol: 'Administrador' },
    children: [
      { path: '', redirectTo: 'ciudades', pathMatch: 'full' },
      { path: 'ciudades', loadComponent: () => import('./modules/datos-maestros/ciudades/ciudades.component').then(m => m.CiudadesComponent) },
      { path: 'categorias', loadComponent: () => import('./modules/datos-maestros/categorias/categorias.component').then(m => m.CategoriasComponent) },
      { path: 'unidades-medida', loadComponent: () => import('./modules/datos-maestros/unidades-medida/unidades-medida.component').then(m => m.UnidadesMedidaComponent) }
    ]
  },
  { path: 'usuarios', loadComponent: () => import('./modules/usuarios/usuarios.component').then(m => m.UsuariosComponent), canActivate: [authGuard, rolGuard], data: { rol: 'Administrador' } },
  { path: 'roles', loadComponent: () => import('./modules/roles/roles.component').then(m => m.RolesComponent), canActivate: [authGuard, rolGuard], data: { rol: 'Administrador' } },
  { path: 'proveedores', loadComponent: () => import('./modules/proveedores/proveedores.component').then(m => m.ProveedoresComponent), canActivate: [authGuard, rolGuard], data: { rol: 'Administrador' } },
  { path: 'productos', loadComponent: () => import('./modules/productos/productos.component').then(m => m.ProductosComponent), canActivate: [authGuard, rolGuard], data: { rol: 'Administrador' } },
  { path: 'bodegas', loadComponent: () => import('./modules/bodegas/bodegas.component').then(m => m.BodegasComponent), canActivate: [authGuard, rolGuard], data: { rol: 'Administrador' } },
  { path: 'inventario', loadComponent: () => import('./modules/inventario/inventario.component').then(m => m.InventarioComponent), canActivate: [authGuard] },
  { path: 'clientes', loadComponent: () => import('./modules/clientes/clientes.component').then(m => m.ClientesComponent), canActivate: [authGuard] },
  { path: 'pedidos', loadComponent: () => import('./modules/pedidos/pedidos.component').then(m => m.PedidosComponent), canActivate: [authGuard] },
  { path: 'pedidos/nuevo', loadComponent: () => import('./modules/pedidos/pedido-nuevo/pedido-nuevo.component').then(m => m.PedidoNuevoComponent), canActivate: [authGuard] },
  { path: 'pedidos/especiales', loadComponent: () => import('./modules/pedidos/pedidos-especiales/pedidos-especiales.component').then(m => m.PedidosEspecialesComponent), canActivate: [authGuard] },
  { path: 'pedidos/:id', loadComponent: () => import('./modules/pedidos/pedido-detalle/pedido-detalle.component').then(m => m.PedidoDetalleComponent), canActivate: [authGuard] },
  { path: 'comprobantes', loadComponent: () => import('./modules/comprobantes/comprobantes-lista/comprobantes-lista.component').then(m => m.ComprobantesListaComponent), canActivate: [authGuard] },
  { path: 'picking', loadComponent: () => import('./modules/picking/picking-lista/picking-lista.component').then(m => m.PickingListaComponent), canActivate: [authGuard] },
  { path: 'picking/:idPedido', loadComponent: () => import('./modules/picking/picking-ejecucion/picking-ejecucion.component').then(m => m.PickingEjecucionComponent), canActivate: [authGuard] },
  { path: 'empaque', loadComponent: () => import('./modules/empaque/empaque-lista/empaque-lista.component').then(m => m.EmpaqueListaComponent), canActivate: [authGuard] },
  { path: 'despachos', loadComponent: () => import('./modules/empaque/despacho-lista/despacho-lista.component').then(m => m.DespachoListaComponent), canActivate: [authGuard] },
  { path: 'compras', loadComponent: () => import('./modules/compras/ordenes-compra.component').then(m => m.OrdenesCompraComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras'] } },
  { path: 'compras/nueva', loadComponent: () => import('./modules/compras/orden-compra-nueva.component').then(m => m.OrdenCompraNuevaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras'] } },
  { path: 'compras/:id/recepcion', loadComponent: () => import('./modules/compras/recepcion-nueva.component').then(m => m.RecepcionNuevaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras'] } },
  { path: 'compras/:id/factura', loadComponent: () => import('./modules/compras/factura-compra-nueva.component').then(m => m.FacturaCompraNuevaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras'] } },
  { path: 'compras/:id', loadComponent: () => import('./modules/compras/orden-compra-detalle.component').then(m => m.OrdenCompraDetalleComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras'] } },
  { path: 'cuentas-por-pagar', loadComponent: () => import('./modules/compras/cuentas-por-pagar.component').then(m => m.CuentasPorPagarComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras', 'Supervisor E-Commerce'] } },
  { path: 'cuentas-por-pagar/:id', loadComponent: () => import('./modules/compras/cuenta-por-pagar-detalle.component').then(m => m.CuentaPorPagarDetalleComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras', 'Supervisor E-Commerce'] } },
  { path: 'materia-prima', loadComponent: () => import('./modules/materia-prima/materia-prima.component').then(m => m.MateriaPrimaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Producción', 'Encargado de Compras'] } },
  { path: 'materia-prima/:id/kardex', loadComponent: () => import('./modules/materia-prima/kardex-materia-prima.component').then(m => m.KardexMateriaPrimaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Producción', 'Encargado de Compras'] } },
  { path: 'devoluciones', loadComponent: () => import('./modules/devoluciones/devoluciones-lista.component').then(m => m.DevolucionesListaComponent), canActivate: [authGuard] },
  { path: 'devoluciones/nueva/:idPedido', loadComponent: () => import('./modules/devoluciones/solicitud-devolucion-nueva.component').then(m => m.SolicitudDevolucionNuevaComponent), canActivate: [authGuard] },
  { path: 'devoluciones/:id', loadComponent: () => import('./modules/devoluciones/devolucion-detalle.component').then(m => m.DevolucionDetalleComponent), canActivate: [authGuard] },
  { path: 'devoluciones-proveedor', loadComponent: () => import('./modules/devoluciones-proveedor/devoluciones-proveedor-lista.component').then(m => m.DevolucionesProveedorListaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras'] } },
  { path: 'devoluciones-proveedor/pendientes', loadComponent: () => import('./modules/devoluciones-proveedor/items-defectuosos.component').then(m => m.ItemsDefectuososComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras'] } },
  { path: 'devoluciones-proveedor/:id', loadComponent: () => import('./modules/devoluciones-proveedor/devolucion-proveedor-detalle.component').then(m => m.DevolucionProveedorDetalleComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Compras'] } },
  { path: 'produccion', loadComponent: () => import('./modules/produccion/ordenes-produccion-lista.component').then(m => m.OrdenesProduccionListaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Producción'] } },
  { path: 'produccion/nueva', loadComponent: () => import('./modules/produccion/orden-produccion-nueva.component').then(m => m.OrdenProduccionNuevaComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Producción'] } },
  { path: 'produccion/:id', loadComponent: () => import('./modules/produccion/orden-produccion-detalle.component').then(m => m.OrdenProduccionDetalleComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Encargado de Producción'] } },
  { path: 'reportes', loadComponent: () => import('./modules/reportes/reportes.component').then(m => m.ReportesComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Supervisor E-Commerce'] } },
  { path: 'ia', loadComponent: () => import('./modules/ia/ia-chat.component').then(m => m.IAChatComponent), canActivate: [authGuard, rolGuard], data: { roles: ['Administrador', 'Supervisor E-Commerce'] } },
  { path: 'auditoria', loadComponent: () => import('./modules/auditoria/auditoria.component').then(m => m.AuditoriaComponent), canActivate: [authGuard, rolGuard], data: { rol: 'Administrador' } },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' }
];
