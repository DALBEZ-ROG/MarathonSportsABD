# =============================================================================
# ARRANQUE DEL BACKEND CON LA CLAVE DE CIFRADO — Fase 41
# -----------------------------------------------------------------------------
# Descifra la clave del almacen DPAPI y se la pasa al proceso Java en una
# variable de entorno DEL PROCESO. La clave en claro existe solo en la memoria
# de este script y del JVM: no se escribe en disco, no entra en
# application.properties y no aparece en la linea de comandos.
#
# Sin este arranque la aplicacion funciona igual pero muestra los datos de
# contacto de cliente y proveedor vacios, y deja un WARN en el registro.
#
# USO:  powershell -ExecutionPolicy Bypass -File iniciar_backend.ps1
# =============================================================================

param(
    [switch] $Background,
    # Escotilla de escape por si la deteccion automatica de mas abajo no acierta.
    # Vacios por omision: si se declararan con una ruta concreta volveriamos al
    # problema que esto arregla.
    [string] $JavaHome,
    [string] $Mvn
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security

$AlmacenArch = 'C:\ProgramData\MarathonSports\crypto\clave.dpapi'
$Backend     = (Resolve-Path (Join-Path $PSScriptRoot '..\..\marathon-backend')).Path

# El JDK y Maven NO se fijan a una ruta concreta: estaban puestos a las del
# equipo de origen y en cualquier otro equipo el script moria con "no se
# encontro mvn.cmd" antes de llegar a arrancar nada. Se buscan en este orden:
# lo que pasen -JavaHome / -Mvn, luego JAVA_HOME, luego el PATH, y solo al
# final las instalaciones habituales de Windows.
function Resolver-JavaHome {
    param([string] $Explicito)
    if ($Explicito -and (Test-Path (Join-Path $Explicito 'bin\java.exe'))) { return $Explicito }
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { return $env:JAVA_HOME }
    $enPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($enPath) {
        # De ...\bin\java.exe se sube dos niveles para tener el JAVA_HOME.
        $candidato = Split-Path (Split-Path $enPath.Source -Parent) -Parent
        # El java del PATH puede ser un JDK 8, que no sirve para Spring Boot 3.
        $version = & $enPath.Source -version 2>&1 | Select-Object -First 1
        if ("$version" -notmatch '"1\.8') { return $candidato }
    }
    foreach ($raiz in 'C:\Program Files\Microsoft','C:\Program Files\Eclipse Adoptium','C:\Program Files\Java') {
        if (-not (Test-Path $raiz)) { continue }
        $jdk = Get-ChildItem $raiz -Directory -ErrorAction SilentlyContinue |
               Where-Object { $_.Name -match '17' -and (Test-Path (Join-Path $_.FullName 'bin\java.exe')) } |
               Sort-Object Name -Descending | Select-Object -First 1
        if ($jdk) { return $jdk.FullName }
    }
    throw "No se encontro un JDK 17. Pasar la ruta con -JavaHome o fijar JAVA_HOME."
}

function Resolver-Mvn {
    param([string] $Explicito)
    if ($Explicito -and (Test-Path $Explicito)) { return $Explicito }
    $enPath = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (-not $enPath) { $enPath = Get-Command mvn -ErrorAction SilentlyContinue }
    if ($enPath) { return $enPath.Source }
    throw "No se encontro Maven. Pasar la ruta de mvn.cmd con -Mvn o anadirlo al PATH."
}

$JavaHome = Resolver-JavaHome $JavaHome
$Mvn      = Resolver-Mvn $Mvn
Write-Host "JDK   : $JavaHome"
Write-Host "Maven : $Mvn"

if (-not (Test-Path $AlmacenArch)) {
    throw "No existe la clave de cifrado en $AlmacenArch. Ejecutar antes gestionar_clave.ps1 -Accion Crear"
}

$blob  = [System.IO.File]::ReadAllBytes($AlmacenArch)
$clave = [System.Text.Encoding]::UTF8.GetString(
             [System.Security.Cryptography.ProtectedData]::Unprotect(
                 $blob, $null, [System.Security.Cryptography.DataProtectionScope]::LocalMachine))

# Variable de entorno del PROCESO: la heredan mvn y el JVM hijo, y muere con
# ellos. No es una variable de maquina ni de usuario, asi que no queda visible
# para otras sesiones ni sobrevive al reinicio.
$env:MARATHON_CRYPTO_KEY = $clave
$clave = $null

# -----------------------------------------------------------------------------
# F92: la credencial de RESPALDO, por el mismo camino
# -----------------------------------------------------------------------------
# La pantalla «Respaldos y recuperacion» ejecuta pg_dump y pg_restore, y eso
# necesita el superusuario: usr_admin_marathon deliberadamente no lo es (ver
# scripts\backup\config.ps1). Esa contrasena no puede vivir en
# application.properties, que se sube al repositorio, ni en
# application-local.properties, que es un sitio mas donde olvidarla.
#
# Se lee del .env —que ya esta en .gitignore y ya la tiene, porque los scripts
# de respaldo la usan— y se pasa al JVM en una variable del PROCESO, que muere
# con el. Sin esto la aplicacion arranca igual: la pantalla se declara «no
# disponible» y dice exactamente que falta.
$envArch = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path '.env'
if (Test-Path $envArch) {
    foreach ($linea in Get-Content $envArch) {
        if ($linea -match '^\s*(PG_SUPERUSER|PG_SUPERUSER_PASSWORD|DB_PORT)\s*=\s*(.+?)\s*$') {
            Set-Item -Path "Env:\$($Matches[1])" -Value $Matches[2]
        }
    }
    if ($env:PG_SUPERUSER_PASSWORD) {
        Write-Host "Credencial de respaldo cargada en el entorno del proceso."
    } else {
        Write-Host "AVISO: el .env no trae PG_SUPERUSER_PASSWORD. La pantalla de respaldos quedara desactivada."
    }
} else {
    Write-Host "AVISO: no hay .env en $envArch. La pantalla de respaldos quedara desactivada."
}

# El java del PATH es 1.8 y no sirve para Spring Boot 3.
$env:JAVA_HOME = $JavaHome
$env:PATH      = "$JavaHome\bin;$env:PATH"

Write-Host "Clave de cifrado cargada en el entorno del proceso."
Write-Host "Arrancando el backend desde $Backend ..."

Push-Location $Backend
try {
    if ($Background) {
        Start-Process -FilePath $Mvn `
                      -ArgumentList '-q','-DskipTests','spring-boot:run' `
                      -NoNewWindow
        Write-Host "Backend lanzado en segundo plano."
    } else {
        & $Mvn -q -DskipTests spring-boot:run
    }
}
finally {
    Pop-Location
    Remove-Item Env:\MARATHON_CRYPTO_KEY   -ErrorAction SilentlyContinue
    Remove-Item Env:\PG_SUPERUSER_PASSWORD -ErrorAction SilentlyContinue
}
