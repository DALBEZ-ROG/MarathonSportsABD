# =============================================================================
# BUSQUEDA DE FILTRACIONES DE LA CLAVE Y DEL DATO EN CLARO — Fase 41
# -----------------------------------------------------------------------------
# La etapa 3.4 de la fase pide COMPROBAR la filtracion, no suponerla. Un cifrado
# cuya clave aparece en el registro del servidor no cifra nada, y con
# log_statement=mod y siete dias de retencion esa exposicion dura una semana.
#
# Este script busca, y NUNCA imprime, tres cosas:
#   1. la clave de cifrado en postgresql-*.log, en auditoria_cambios y en
#      log_accion
#   2. un correo de cliente en claro en los mismos sitios (el dato que se
#      acaba de cifrar no deberia seguir apareciendo por otra via)
#   3. el marcador de parametros enlazados, para saber si el registro del
#      servidor esta guardando los valores de los prepared statements
#
# Solo informa de CUANTAS coincidencias hay y en que archivo. El material
# sensible no se escribe en la salida en ningun caso.
#
# USO:  powershell -ExecutionPolicy Bypass -File buscar_filtraciones.ps1
# =============================================================================

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security

$PgBin   = 'C:\Program Files\PostgreSQL\18\bin'
$LogDir  = 'C:\Program Files\PostgreSQL\18\data\log'
$Base    = 'mod_venta_inve'

# --- la clave, en memoria y nunca en pantalla --------------------------------
$blob  = [System.IO.File]::ReadAllBytes('C:\ProgramData\MarathonSports\crypto\clave.dpapi')
$clave = [System.Text.Encoding]::UTF8.GetString(
             [System.Security.Cryptography.ProtectedData]::Unprotect(
                 $blob, $null, [System.Security.Cryptography.DataProtectionScope]::LocalMachine))

# --- credencial de la base ---------------------------------------------------
$envFile = Join-Path $PSScriptRoot '..\..\.env'
foreach ($l in Get-Content $envFile) {
    if ($l -match '^\s*PG_SUPERUSER_PASSWORD\s*=\s*(.*)$') { $env:PGPASSWORD = $matches[1].Trim() }
}

$env:MARATHON_CRYPTO_KEY = $clave

function Invoke-Sql {
    <#
      Ejecuta SQL a traves de un archivo temporal y NO por -c.
      Los argumentos de un proceso son visibles para cualquiera que liste
      procesos; pasar la clave o un correo por -c los expondria a todo el
      equipo mientras dure la consulta. El archivo temporal se borra siempre,
      y la clave entra en el con \getenv, no escrita.
    #>
    param([string] $Sql, [switch] $ConClave)
    $tmp = Join-Path $env:TEMP ("f41_" + [guid]::NewGuid().ToString('N') + ".sql")
    try {
        $lineas = @()
        if ($ConClave) {
            # \getenv recoge la clave del entorno del proceso. set_config la
            # publica en la sesion, y a partir de ahi el SQL la referencia con
            # current_setting en vez de llevarla escrita.
            $lineas += '\getenv clave MARATHON_CRYPTO_KEY'
            # IS NOT NULL, y no set_config a secas: set_config DEVUELVE el valor
            # que fija, asi que sin esto psql imprimiria la clave en la salida.
            $lineas += "SELECT set_config('app.crypto_key', :'clave', false) IS NOT NULL AS clave_ok;"
        }
        $lineas += $Sql
        Set-Content -Path $tmp -Value $lineas -Encoding utf8
        & (Join-Path $PgBin 'psql.exe') -h localhost -p 5432 -U postgres -d $Base -t -A -q -f $tmp
    }
    finally { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
}

Write-Host "=============================================================="
Write-Host " BUSQUEDA DE FILTRACIONES — Fase 41"
Write-Host "=============================================================="

# --- 1. la clave en el registro del servidor ---------------------------------
Write-Host ""
Write-Host "[1] Clave de cifrado en los registros de PostgreSQL"
$logs = Get-ChildItem $LogDir -Filter 'postgresql-*.log' -ErrorAction SilentlyContinue
if (-not $logs) { Write-Host "    (no hay archivos de registro en $LogDir)" }
$totalClaveLog = 0
foreach ($f in $logs) {
    $n = (Select-String -Path $f.FullName -SimpleMatch -Pattern $clave -AllMatches -ErrorAction SilentlyContinue |
          Measure-Object).Count
    $totalClaveLog += $n
    $marca = if ($n -eq 0) { 'limpio' } else { '*** FILTRACION ***' }
    Write-Host ("    {0,-24} {1,4} coincidencias  {2}" -f $f.Name, $n, $marca)
}
Write-Host ("    TOTAL en registros: {0}" -f $totalClaveLog)

# --- 2. la clave en las bitacoras de la aplicacion ---------------------------
Write-Host ""
Write-Host "[2] Clave de cifrado en auditoria_cambios y log_accion"
# La clave NO se interpola en el SQL: se compara contra current_setting, que ya
# la tiene publicada en la sesion. Asi no aparece ni en el archivo temporal.
$a = (Invoke-Sql -ConClave "SELECT count(*) FROM auditoria_cambios WHERE valor_anterior LIKE '%'||current_setting('app.crypto_key')||'%' OR valor_nuevo LIKE '%'||current_setting('app.crypto_key')||'%';") | Select-Object -Last 1
$b = (Invoke-Sql -ConClave "SELECT count(*) FROM log_accion WHERE descripcion LIKE '%'||current_setting('app.crypto_key')||'%';") | Select-Object -Last 1
Write-Host ("    auditoria_cambios : {0} coincidencias" -f $a)
Write-Host ("    log_accion        : {0} coincidencias" -f $b)

# --- 3. el DATO en claro, que es la otra mitad del problema ------------------
# Se toma un correo real descifrandolo, y se busca ese texto por todas partes.
# Si el correo cifrado sigue apareciendo legible en el registro del servidor,
# haber cifrado la columna no ha servido de nada.
Write-Host ""
Write-Host "[3] Un correo de cliente EN CLARO en los mismos sitios"
$correo = (Invoke-Sql -ConClave "SELECT fn_descifrar(correo_enc) FROM cliente WHERE correo_enc IS NOT NULL ORDER BY id_cliente LIMIT 1;" |
           Select-Object -Last 1)
if ($correo) {
    $totalCorreoLog = 0
    foreach ($f in $logs) {
        $n = (Select-String -Path $f.FullName -SimpleMatch -Pattern $correo -AllMatches -ErrorAction SilentlyContinue |
              Measure-Object).Count
        $totalCorreoLog += $n
        $marca = if ($n -eq 0) { 'limpio' } else { '*** DATO EN CLARO EN EL REGISTRO ***' }
        Write-Host ("    {0,-24} {1,4} coincidencias  {2}" -f $f.Name, $n, $marca)
    }
    $sqlC = "WITH m AS (SELECT fn_descifrar(correo_enc) AS c FROM cliente WHERE correo_enc IS NOT NULL ORDER BY id_cliente LIMIT 1) " +
            "SELECT count(*) FROM auditoria_cambios a, m WHERE a.valor_anterior LIKE '%'||m.c||'%' OR a.valor_nuevo LIKE '%'||m.c||'%';"
    $c = (Invoke-Sql -ConClave $sqlC) | Select-Object -Last 1
    Write-Host ("    auditoria_cambios : {0} coincidencias" -f $c)
}

# --- 4. parametros enlazados en el registro ----------------------------------
# log_parameter_max_length = -1 significa "registrar los parametros COMPLETOS"
# cuando una sentencia se registra. Con log_min_duration_statement=20ms, un
# INSERT lento con datos personales enlazados los dejaria escritos.
Write-Host ""
Write-Host "[4] Registro de parametros enlazados (riesgo estructural)"
$plml = Invoke-Sql "SHOW log_parameter_max_length;"
$lmds = Invoke-Sql "SHOW log_min_duration_statement;"
$lst  = Invoke-Sql "SHOW log_statement;"
Write-Host ("    log_statement               = {0}" -f $lst)
Write-Host ("    log_min_duration_statement  = {0}" -f $lmds)
Write-Host ("    log_parameter_max_length    = {0}" -f $plml)
$conParam = 0
foreach ($f in $logs) {
    $conParam += (Select-String -Path $f.FullName -SimpleMatch -Pattern 'parameters: $1' -AllMatches -ErrorAction SilentlyContinue |
                  Measure-Object).Count
}
Write-Host ("    lineas 'parameters: `$1' en los registros: {0}" -f $conParam)

Remove-Item Env:\MARATHON_CRYPTO_KEY -ErrorAction SilentlyContinue
Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
$clave = $null; $correo = $null

Write-Host ""
Write-Host "=============================================================="
