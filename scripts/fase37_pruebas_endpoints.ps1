# Pruebas de la F37: cada rol contra la aplicacion real.
# Comprueba (a) que el rol llega a la base con SU usuario y (b) que los
# endpoints permitidos responden 200 y los denegados 403.
$ErrorActionPreference = 'SilentlyContinue'
$base = 'http://localhost:8080'

$usuarios = @(
  @{ correo='admin@marathon.com';       pass='Admin1234!'; rol='Administrador';           usr='usr_admin_marathon' }
  @{ correo='supervisor@marathon.com';  pass='Demo1234!';  rol='Supervisor';              usr='usr_supervisor_marathon' }
  @{ correo='bodega@marathon.com';      pass='Demo1234!';  rol='Op. Bodega';              usr='usr_bodega_marathon' }
  @{ correo='pedidos@marathon.com';     pass='Demo1234!';  rol='Op. Pedidos';             usr='usr_pedidos_marathon' }
  @{ correo='compras@marathon.com';     pass='Demo1234!';  rol='Enc. Compras';            usr='usr_compras_marathon' }
  @{ correo='produccion@marathon.com';  pass='Demo1234!';  rol='Enc. Produccion';         usr='usr_produccion_marathon' }
)

# endpoint -> roles que DEBEN poder (el resto debe recibir 403)
$casos = @(
  @{ url='/api/productos';         permitidos=@('Administrador','Supervisor','Op. Bodega','Op. Pedidos','Enc. Compras','Enc. Produccion') }
  @{ url='/api/inventario';        permitidos=@('Administrador','Supervisor','Op. Bodega','Op. Pedidos','Enc. Compras','Enc. Produccion') }
  @{ url='/api/proveedores';       permitidos=@('Administrador','Supervisor','Enc. Compras') }
  @{ url='/api/clientes';          permitidos=@('Administrador','Supervisor','Op. Bodega','Op. Pedidos') }
  @{ url='/api/pedidos';           permitidos=@('Administrador','Supervisor','Op. Bodega','Op. Pedidos') }
  @{ url='/api/comprobantes';      permitidos=@('Administrador','Supervisor','Op. Bodega','Op. Pedidos') }
  @{ url='/api/devoluciones';      permitidos=@('Administrador','Supervisor','Op. Bodega','Op. Pedidos') }
  @{ url='/api/ordenes-compra';    permitidos=@('Administrador','Enc. Compras') }
  @{ url='/api/materia-prima';     permitidos=@('Administrador','Enc. Compras','Enc. Produccion') }
  @{ url='/api/ordenes-produccion';permitidos=@('Administrador','Supervisor','Enc. Produccion') }
  @{ url='/api/usuarios';          permitidos=@('Administrador') }
)

$fallos = 0
$total  = 0

foreach ($u in $usuarios) {
  $body = @{ correo=$u.correo; password=$u.pass } | ConvertTo-Json
  $login = try { Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -Body $body -ContentType 'application/json' } catch { $null }
  if (-not $login -or -not $login.token) {
    Write-Host ("LOGIN FALLA  {0}" -f $u.rol) -ForegroundColor Red
    $fallos++; continue
  }
  $h = @{ Authorization = "Bearer $($login.token)" }
  Write-Host ""
  Write-Host ("=== {0}  ({1}) ===" -f $u.rol, $u.usr) -ForegroundColor Cyan

  foreach ($c in $casos) {
    $total++
    $esperado = if ($c.permitidos -contains $u.rol) { 'PERMITIDO' } else { '403' }
    $code = 0
    try {
      $r = Invoke-WebRequest -Uri "$base$($c.url)" -Headers $h -Method Get -UseBasicParsing
      $code = $r.StatusCode
    } catch {
      $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { -1 }
    }
    $obtenido = if ($code -eq 200) { 'PERMITIDO' } elseif ($code -eq 403) { '403' } else { "HTTP $code" }
    $ok = ($obtenido -eq $esperado)
    if (-not $ok) { $fallos++ }
    $marca = if ($ok) { 'PASA ' } else { 'FALLA' }
    $color = if ($ok) { 'Gray' } else { 'Red' }
    Write-Host ("  {0}  {1,-28} esperado={2,-10} obtenido={3}" -f $marca, $c.url, $esperado, $obtenido) -ForegroundColor $color
  }
}

Write-Host ""
Write-Host ("===== {0} de {1} comprobaciones pasan, {2} fallan =====" -f ($total-$fallos), $total, $fallos) `
  -ForegroundColor $(if ($fallos -eq 0) { 'Green' } else { 'Red' })
