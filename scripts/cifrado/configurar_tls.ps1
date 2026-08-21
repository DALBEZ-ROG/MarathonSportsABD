# =============================================================================
# TLS PARA LAS CONEXIONES A POSTGRESQL — Fase 41, etapa 6
# -----------------------------------------------------------------------------
# Cifrar los datos en reposo mientras la conexion viaja en claro es una
# incoherencia, aunque todo sea 127.0.0.1: cualquier proceso local con acceso a
# la interfaz de loopback puede leer el trafico, y las contrasenas de los seis
# roles viajan por ahi en cada arranque.
#
# QUE HACE
#   1. Genera un certificado autofirmado para CN=localhost (825 dias, que es el
#      maximo que aceptan los clientes modernos para certificados de servidor).
#   2. Lo deja en el directorio de datos como server.crt / server.key y da
#      permiso de lectura a NT AUTHORITY\NetworkService, que es la cuenta con la
#      que corre el servicio postgresql-x64-18 en este equipo.
#   3. Enciende ssl con ALTER SYSTEM (postgresql.auto.conf) y recarga. NO toca
#      postgresql.conf ni ningun parametro del planificador de la F39.
#   4. Comprueba con pg_stat_ssl que las conexiones van cifradas de verdad.
#
# ssl es de contexto 'sighup': basta recargar, no hace falta reiniciar el
# servidor ni cortar las conexiones vivas.
#
# PARA REVERTIR:  gestionar_tls.ps1 no hace falta; basta con
#   ALTER SYSTEM SET ssl = off;  SELECT pg_reload_conf();
#
# USO:  powershell -ExecutionPolicy Bypass -File configurar_tls.ps1
#       powershell -ExecutionPolicy Bypass -File configurar_tls.ps1 -Revertir
# =============================================================================

param([switch] $Revertir, [int] $PgPort = 0)

$ErrorActionPreference = 'Stop'

$PgBin   = 'C:\Program Files\PostgreSQL\18\bin'
$DataDir = 'C:\Program Files\PostgreSQL\18\data'
$OpenSsl = 'C:\Program Files\Git\usr\bin\openssl.exe'
$Base    = 'mod_venta_inve'
$Crt     = Join-Path $DataDir 'server.crt'
$Key     = Join-Path $DataDir 'server.key'

# El certificado tiene que quedar tambien donde lo busca el cliente JDBC
# (sslrootcert de application.properties). verify-full no solo cifra: valida la
# cadena contra ese archivo, y sin el el backend no llega a conectar.
$ClienteDir = 'C:\ProgramData\MarathonSports\tls'

$envFile = Join-Path $PSScriptRoot '..\..\.env'
# El puerto no se fija a 5432: si PostgreSQL 18 se instalo conviviendo con otra
# version, el instalador elige 5433 y todo el script apuntaria a un servidor que
# no es. Se toma de DB_PORT del .env, la misma fuente que usa el backend, y
# -PgPort lo pisa si hace falta.
$puertoEnv = 0
if (Test-Path $envFile) {
    foreach ($l in Get-Content $envFile) {
        if ($l -match '^\s*PG_SUPERUSER_PASSWORD\s*=\s*(.*)$') { $env:PGPASSWORD = $matches[1].Trim() }
        if ($l -match '^\s*DB_PORT\s*=\s*(\d+)\s*$')           { $puertoEnv = [int]$matches[1] }
    }
} elseif (-not $env:PGPASSWORD) {
    Write-Host "AVISO: no hay .env; se usa PGPASSWORD del entorno o el valor por defecto del backend."
}
if ($PgPort -eq 0) { $PgPort = if ($puertoEnv -ne 0) { $puertoEnv } else { 5432 } }
Write-Host "Puerto de PostgreSQL: $PgPort"

function Invoke-Sql {
    param([string] $Sql)
    & (Join-Path $PgBin 'psql.exe') -h localhost -p $PgPort -U postgres -d $Base -t -A -c $Sql
}

if ($Revertir) {
    Invoke-Sql "ALTER SYSTEM SET ssl = off;" | Out-Null
    Invoke-Sql "SELECT pg_reload_conf();"    | Out-Null
    Start-Sleep -Seconds 2
    Write-Host "TLS REVERTIDO. ssl = $(Invoke-Sql 'SHOW ssl;')"
    exit 0
}

# --- 1. certificado ----------------------------------------------------------
if (-not (Test-Path $OpenSsl)) { throw "No se encontro openssl en $OpenSsl" }

if (Test-Path $Crt) {
    Write-Host "Ya existe un certificado en $Crt. No se regenera."
} else {
    Write-Host "Generando certificado autofirmado para CN=localhost..."
    # -nodes: la clave privada sin contrasena. Con contrasena, PostgreSQL la
    # pediria por consola en cada arranque y el servicio no podria iniciarse
    # desatendido.
    # openssl escribe los puntos de progreso de la generacion de la clave en
    # stderr. Con ErrorActionPreference='Stop', PowerShell convierte CUALQUIER
    # escritura a stderr de un ejecutable nativo en excepcion terminante, asi
    # que un certificado perfectamente generado aborta el script con una linea
    # de puntos como "mensaje de error". Es la misma trampa que documenta
    # Invoke-PgTool en scripts\backup\config.ps1: se baja la preferencia solo
    # durante la llamada y se decide por el resultado, no por stderr.
    $previo = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $OpenSsl req -new -x509 -days 825 -nodes `
            -subj "/C=EC/ST=Tungurahua/L=Ambato/O=Marathon Sports/CN=localhost" `
            -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" `
            -keyout $Key -out $Crt 2>&1 | Out-Null
    }
    finally { $ErrorActionPreference = $previo }
    if (-not (Test-Path $Crt)) { throw "openssl no genero el certificado" }
    Write-Host "Certificado generado."
}

# --- 1b. copia para el cliente ----------------------------------------------
# El servidor lee server.crt del directorio de datos, pero el driver JDBC lo
# busca en $ClienteDir (sslrootcert). Se copia siempre, no solo cuando se acaba
# de generar: si el certificado se regenero a mano, la copia vieja dejaria al
# backend con "certificate verify failed" sin ninguna pista de por que.
New-Item -ItemType Directory -Force -Path $ClienteDir | Out-Null
Copy-Item $Crt (Join-Path $ClienteDir 'server.crt') -Force
Write-Host "Certificado copiado a $ClienteDir\server.crt (sslrootcert del backend)."

# --- 2. permisos -------------------------------------------------------------
# La clave privada solo la leen el servicio y los administradores. Se usan SID
# y no nombres porque este Windows esta en espanol.
#   S-1-5-20      NT AUTHORITY\NetworkService  (cuenta del servicio)
#   S-1-5-32-544  Administradores
icacls $Key /inheritance:r /grant:r "*S-1-5-20:(R)" "*S-1-5-32-544:(R)" | Out-Null
Write-Host "Permisos de server.key restringidos al servicio y a Administradores."

# --- 3. encender ssl ---------------------------------------------------------
# ALTER SYSTEM escribe en postgresql.auto.conf, no en postgresql.conf: la
# configuracion de la F39 queda intacta y esto se revierte con una linea.
Invoke-Sql "ALTER SYSTEM SET ssl = on;" | Out-Null
Invoke-Sql "SELECT pg_reload_conf();"   | Out-Null
Start-Sleep -Seconds 2

$ssl = Invoke-Sql "SHOW ssl;"
Write-Host "ssl = $ssl"
if ($ssl -ne 'on') {
    Write-Host "AVISO: ssl no quedo activo. Revisar el registro del servidor."
    exit 1
}

# --- 4. verificar que las conexiones VAN cifradas ----------------------------
# SHOW ssl solo dice que el servidor lo admite. Lo que importa es si las
# sesiones reales lo usan, y eso lo responde pg_stat_ssl.
Write-Host ""
Write-Host "--- pg_stat_ssl: conexiones vivas ---"
& (Join-Path $PgBin 'psql.exe') -h localhost -p $PgPort -U postgres -d $Base -c @"
SELECT a.application_name, a.usename, s.ssl, s.version, s.cipher
FROM pg_stat_ssl s JOIN pg_stat_activity a ON a.pid = s.pid
WHERE a.datname = current_database()
ORDER BY s.ssl DESC, a.usename;
"@

Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
