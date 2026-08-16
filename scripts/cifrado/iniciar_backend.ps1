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
    [switch] $Background
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security

$AlmacenArch = 'C:\ProgramData\MarathonSports\crypto\clave.dpapi'
$JavaHome    = 'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'
$Mvn         = 'C:\Users\dbeni\OneDrive\Documentos\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd'
$Backend     = (Resolve-Path (Join-Path $PSScriptRoot '..\..\marathon-backend')).Path

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
    Remove-Item Env:\MARATHON_CRYPTO_KEY -ErrorAction SilentlyContinue
}
