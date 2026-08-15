# Segunda tanda de la F37: los enlaces que CADA ROL tiene en su navbar deben
# responder 200. Si el menu ofrece una pantalla, la base tiene que dejarla
# abrir; lo contrario es la incoherencia que esta fase viene a eliminar.
$ErrorActionPreference = 'SilentlyContinue'
$base = 'http://localhost:8080'

$usuarios = @(
  @{ correo='admin@marathon.com';      pass='Admin1234!'; rol='Administrador' }
  @{ correo='supervisor@marathon.com'; pass='Demo1234!';  rol='Supervisor' }
  @{ correo='bodega@marathon.com';     pass='Demo1234!';  rol='Op. Bodega' }
  @{ correo='pedidos@marathon.com';    pass='Demo1234!';  rol='Op. Pedidos' }
  @{ correo='compras@marathon.com';    pass='Demo1234!';  rol='Enc. Compras' }
  @{ correo='produccion@marathon.com'; pass='Demo1234!';  rol='Enc. Produccion' }
)

# endpoint -> roles cuyo NAVBAR o guard de ruta lo ofrece (deben recibir 200)
$navbar = @(
  @{ url='/api/picking/pedidos';            roles=@('Administrador','Op. Bodega') }
  @{ url='/api/empaque/pedidos';            roles=@('Administrador','Op. Bodega') }
  @{ url='/api/cuentas-por-pagar';          roles=@('Administrador','Enc. Compras','Supervisor') }
  @{ url='/api/facturas-compra';            roles=@('Administrador','Enc. Compras') }
  @{ url='/api/devoluciones-proveedor';     roles=@('Administrador','Enc. Compras') }
  @{ url='/api/dashboard/manufactura';      roles=@('Administrador','Supervisor','Enc. Produccion') }
  @{ url='/api/analisis-costos/productos-fabricados';  roles=@('Administrador','Supervisor','Enc. Produccion') }
  @{ url='/api/dashboard/kpis';          roles=@('Administrador','Supervisor') }
  @{ url='/api/logs';                       roles=@('Administrador') }
)

$fallos = 0; $total = 0
foreach ($u in $usuarios) {
  $login = try { Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType 'application/json' `
                   -Body (@{correo=$u.correo;password=$u.pass}|ConvertTo-Json) } catch { $null }
  if (-not $login.token) { Write-Host "LOGIN FALLA $($u.rol)" -ForegroundColor Red; $fallos++; continue }
  $h = @{ Authorization = "Bearer $($login.token)" }
  Write-Host ""; Write-Host "=== $($u.rol) ===" -ForegroundColor Cyan

  foreach ($c in $navbar) {
    if ($c.roles -notcontains $u.rol) { continue }   # solo se prueba lo que su menu ofrece
    $total++
    $code = 0
    try { $code = (Invoke-WebRequest -Uri "$base$($c.url)" -Headers $h -Method Get -UseBasicParsing).StatusCode }
    catch { $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { -1 } }
    # 404 se acepta: el endpoint existe y autorizo, pero el recurso concreto no esta
    $ok = ($code -eq 200 -or $code -eq 404)
    if (-not $ok) { $fallos++ }
    Write-Host ("  {0}  {1,-42} HTTP {2}" -f $(if($ok){'PASA '}else{'FALLA'}), $c.url, $code) `
      -ForegroundColor $(if($ok){'Gray'}else{'Red'})
  }
}
Write-Host ""
Write-Host ("===== {0} de {1} enlaces del navbar abren, {2} fallan =====" -f ($total-$fallos), $total, $fallos) `
  -ForegroundColor $(if ($fallos -eq 0) { 'Green' } else { 'Red' })


