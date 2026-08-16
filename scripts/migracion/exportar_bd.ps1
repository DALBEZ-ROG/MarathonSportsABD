# =============================================================================
# EXPORTAR LA BASE PARA OTRO EQUIPO — Marathon Sports
# -----------------------------------------------------------------------------
# Genera un paquete portable con TODO lo que hace falta para levantar
# mod_venta_inve en otra maquina: la base, los roles y la configuracion.
#
# POR QUE NO SIRVE COPIAR EL RESPALDO DE pg_basebackup.
# Los respaldos de scripts\backup son FISICOS: copian el directorio de datos
# byte a byte. Solo se restauran sobre la MISMA version mayor de PostgreSQL y
# la misma plataforma, y ademas arrastran el cluster entero. Para mover la base
# a otro equipo lo correcto es un volcado LOGICO (pg_dump), que es portable.
#
# LAS DOS TRAMPAS QUE ESTE SCRIPT RESUELVE
#
#   1. LOS ROLES NO VIAJAN EN pg_dump. Los roles y usuarios son objetos del
#      CLUSTER, no de la base. Un pg_dump de mod_venta_inve contiene los GRANT
#      (37 sentencias de privilegios por columna) pero NO los CREATE ROLE, asi
#      que al restaurar en un equipo limpio la restauracion falla con "el rol
#      rol_administrador no existe". Por eso se exporta tambien pg_dumpall
#      --roles-only.
#
#   2. LA CLAVE DE CIFRADO NO VIAJA Y NO PUEDE VIAJAR AQUI. Esta protegida con
#      DPAPI de ambito maquina: el blob es indescifrable en otro equipo. Y no se
#      mete en el paquete a proposito, por lo mismo que no se guarda junto a los
#      respaldos: si la clave viaja con los datos que cifra, cifrar no sirvio de
#      nada. Se entrega POR SEPARADO. Ver el LEEME que genera este script.
#
# USO:
#   powershell -ExecutionPolicy Bypass -File exportar_bd.ps1
#   powershell -ExecutionPolicy Bypass -File exportar_bd.ps1 -Destino D:\entrega
#   powershell -ExecutionPolicy Bypass -File exportar_bd.ps1 -IncluirSecretos
# =============================================================================

param(
    [string] $Destino = "$env:USERPROFILE\migracion_marathon",
    # Incluye .env y application-local.properties, que llevan las contrasenas de
    # los seis usuarios de base de datos. Sin ellos el receptor tiene que
    # escribirlas a mano. CON ellos, el paquete es material sensible y no debe
    # subirse a ningun sitio publico.
    [switch] $IncluirSecretos
)

$ErrorActionPreference = 'Stop'

$PgBin   = 'C:\Program Files\PostgreSQL\18\bin'
$Base    = 'mod_venta_inve'
$Proyecto = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$sello   = (Get-Date).ToString('yyyyMMdd_HHmmss')
$paquete = Join-Path $Destino "marathon_$sello"

# --- credencial de superusuario ----------------------------------------------
$envFile = Join-Path $Proyecto '.env'
if (-not (Test-Path $envFile)) { throw "No se encontro el .env en $Proyecto" }
foreach ($l in Get-Content $envFile) {
    if ($l -match '^\s*PG_SUPERUSER_PASSWORD\s*=\s*(.*)$') { $env:PGPASSWORD = $matches[1].Trim() }
}
if (-not $env:PGPASSWORD) { throw "El .env no tiene PG_SUPERUSER_PASSWORD" }

New-Item -ItemType Directory -Path $paquete -Force | Out-Null
Write-Host "Paquete: $paquete"
Write-Host ""

function Invoke-Pg {
    <#  Los ejecutables de PostgreSQL escriben avisos en stderr; con
        ErrorActionPreference='Stop' eso aborta el script aunque todo haya ido
        bien. Se decide por el codigo de salida, que es el unico fiable. #>
    # OJO con el nombre del parametro: $Args es una VARIABLE AUTOMATICA de
    # PowerShell. Llamarlo asi hace que no se enlace, los argumentos se pierden
    # en silencio y pg_dumpall acaba conectando con el usuario de Windows en vez
    # de con -U postgres. Falla con "autentificacion password fallo para el
    # usuario dbeni", que no se parece en nada a la causa real.
    param([string] $Exe, [string[]] $Argumentos)
    $previo = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $salida = & (Join-Path $PgBin $Exe) @Argumentos 2>&1
        $codigo = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previo }
    return [pscustomobject]@{ ExitCode = $codigo; Salida = $salida }
}

# --- 1. roles del cluster -----------------------------------------------------
Write-Host "[1/4] Roles y usuarios..."
$rolesArchivo = Join-Path $paquete '01_roles.sql'
$r = Invoke-Pg 'pg_dumpall.exe' @('-h','localhost','-p','5432','-U','postgres','--roles-only')
if ($r.ExitCode -ne 0) { throw "pg_dumpall fallo: $($r.Salida -join ' ')" }

# Se filtran SOLO los roles del proyecto. Volcar el cluster entero arrastraria
# el propio 'postgres' y cualquier rol del equipo de origen, que en el equipo
# destino ya existen y provocarian errores o, peor, cambios de contrasena.
$lineas = @(
    '-- Roles y usuarios de Marathon Sports.'
    '-- Generado por exportar_bd.ps1. Ejecutar ANTES de restaurar la base:'
    '--   psql -U postgres -f 01_roles.sql'
    '--'
    '-- Incluye los hashes SCRAM de las contrasenas: es material sensible.'
    '-- Los CREATE llevan guarda para poder reejecutarlo sin romper nada.'
    ''
)
foreach ($l in $r.Salida) {
    $t = "$l"
    if ($t -match '^(CREATE ROLE|ALTER ROLE|GRANT)\s+"?(usr_\w+|rol_\w+)') {
        if ($t -match '^CREATE ROLE\s+"?(\w+)') {
            $nombre = $matches[1]
            $lineas += "DO `$`$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='$nombre') THEN"
            $lineas += "  $t"
            $lineas += "END IF; END `$`$;"
        } else {
            $lineas += $t
        }
    }
}
Set-Content -Path $rolesArchivo -Value $lineas -Encoding utf8
Write-Host ("      {0} roles del proyecto" -f (($lineas | Select-String 'CREATE ROLE').Count))

# --- 2. la base ---------------------------------------------------------------
Write-Host "[2/4] Base de datos (formato custom, comprimido)..."
$dump = Join-Path $paquete '02_base.dump'
# SIN --no-privileges y SIN --no-owner: los GRANT son justamente lo que hay que
# conservar. El volcado lleva 37 sentencias de privilegios, incluidos los de
# columna sobre las columnas cifradas, y perderlos dejaria la base migrada sin
# modelo de seguridad. Por eso 01_roles.sql debe aplicarse ANTES: si los roles
# no existen, estos GRANT fallan.
$r = Invoke-Pg 'pg_dump.exe' @('-h','localhost','-p','5432','-U','postgres','-d',$Base,
                               '-Fc','-Z','6','-f',$dump)
if ($r.ExitCode -ne 0) { throw "pg_dump fallo: $($r.Salida -join ' ')" }
Write-Host ("      {0:N1} MB" -f ((Get-Item $dump).Length / 1MB))

# --- 3. configuracion del servidor -------------------------------------------
Write-Host "[3/4] Configuracion del servidor..."
$cfg = Join-Path $paquete '03_configuracion.sql'
@(
    '-- Parametros del servidor ajustados con medicion en las fases 36 y 39.'
    '-- Se aplican con ALTER SYSTEM (escribe en postgresql.auto.conf, no toca'
    '-- postgresql.conf) y requieren recarga; shared_buffers exige reinicio.'
    '--   psql -U postgres -f 03_configuracion.sql'
    ''
    '-- Planificador (F39)'
    "ALTER SYSTEM SET random_page_cost = '1.1';"
    "ALTER SYSTEM SET effective_cache_size = '12GB';"
    ''
    '-- Auditoria y registro (F36 + F39)'
    "ALTER SYSTEM SET log_statement = 'mod';"
    "ALTER SYSTEM SET log_min_duration_statement = '20ms';"
    "ALTER SYSTEM SET log_line_prefix = '%m [%p] usuario=%u base=%d origen=%r app=%a xid=%x ';"
    "ALTER SYSTEM SET log_filename = 'postgresql-%a.log';"
    "ALTER SYSTEM SET logging_collector = 'on';"
    "ALTER SYSTEM SET log_truncate_on_rotation = 'on';"
    "ALTER SYSTEM SET log_rotation_age = '1d';"
    ''
    '-- Respaldos diferenciales (F35). Sin esto pg_basebackup incremental falla.'
    "ALTER SYSTEM SET summarize_wal = 'on';"
    ''
    '-- TLS: se activa DESPUES de generar el certificado en el equipo destino,'
    '-- con scripts\cifrado\configurar_tls.ps1. Dejarlo en on sin certificado'
    '-- impide arrancar el servidor.'
    "-- ALTER SYSTEM SET ssl = 'on';"
    ''
    'SELECT pg_reload_conf();'
) | Set-Content -Path $cfg -Encoding utf8
Write-Host "      03_configuracion.sql"

# --- 4. secretos de la aplicacion (opcional) ---------------------------------
Write-Host "[4/4] Configuracion de la aplicacion..."
if ($IncluirSecretos) {
    $sec = Join-Path $paquete 'secretos'
    New-Item -ItemType Directory -Path $sec -Force | Out-Null
    Copy-Item $envFile (Join-Path $sec 'env.txt') -Force
    $localProps = Join-Path $Proyecto 'marathon-backend\src\main\resources\application-local.properties'
    if (Test-Path $localProps) { Copy-Item $localProps $sec -Force }
    Write-Host "      INCLUIDOS. El paquete contiene contrasenas: tratalo como material sensible."
} else {
    @(
        'QUE FALTA EN ESTE PAQUETE, A PROPOSITO'
        '======================================'
        ''
        'Dos archivos del proyecto estan en .gitignore porque llevan contrasenas,'
        'asi que no vienen ni en el repositorio ni aqui:'
        ''
        '  .env'
        '      DB_USER / DB_PASSWORD              -> usr_admin_marathon (la aplicacion)'
        '      PG_SUPERUSER / PG_SUPERUSER_PASSWORD -> postgres (los respaldos)'
        '      JWT_SECRET'
        ''
        '  marathon-backend/src/main/resources/application-local.properties'
        '      spring.datasource.url  (con sslmode=verify-full y sslrootcert)'
        '      las credenciales de los seis pools por rol'
        ''
        'Las contrasenas de los seis usuarios de base de datos son las MISMAS que'
        'en el equipo de origen: 01_roles.sql lleva sus hashes SCRAM. Hay que'
        'escribirlas en el .env del equipo destino.'
        ''
        'Para incluirlos automaticamente:  exportar_bd.ps1 -IncluirSecretos'
        '(el paquete pasa entonces a ser material sensible)'
    ) | Set-Content -Path (Join-Path $paquete 'FALTAN_SECRETOS.txt') -Encoding utf8
    Write-Host "      NO incluidos. Ver FALTAN_SECRETOS.txt"
}

# --- el script de importacion y la guia viajan dentro del paquete ------------
# El paquete se copia a otro equipo por USB o red, separado del repositorio, asi
# que tiene que llevar dentro con que usarlo. Sin -ErrorAction SilentlyContinue:
# si falta alguno, hay que enterarse AQUI y no cuando el receptor abra la
# carpeta y no encuentre como seguir.
Copy-Item (Join-Path $PSScriptRoot 'importar_bd.ps1') $paquete -Force
$guia = Join-Path $Proyecto 'GUIA_REPLICACION.md'
if (Test-Path $guia) { Copy-Item $guia $paquete -Force }
else { Write-Host "      AVISO: no se encontro GUIA_REPLICACION.md en la raiz del proyecto." }

Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue

$mb = [math]::Round((Get-ChildItem $paquete -Recurse -File | Measure-Object Length -Sum).Sum / 1MB, 1)
Write-Host ""
Write-Host "=============================================================="
Write-Host "Paquete listo: $paquete  ($mb MB)"
Write-Host "=============================================================="
Write-Host ""
Write-Host "FALTA LA CLAVE DE CIFRADO, y no esta aqui a proposito."
Write-Host "Sin ella, en el equipo destino los correos, telefonos y direcciones"
Write-Host "de clientes y proveedores se veran VACIOS (el resto funciona)."
Write-Host ""
Write-Host "Para llevarla, por un canal DISTINTO de este paquete:"
Write-Host "  gestionar_clave.ps1 -Accion Escrow -Destino <ruta>"
Write-Host ""
Write-Host "Siguiente paso: copiar la carpeta al otro equipo y ejecutar alli"
Write-Host "  powershell -ExecutionPolicy Bypass -File importar_bd.ps1"
