# =============================================================================
# PROMOVER LA VARIABLE DE LA CLAVE AL AMBITO DE MAQUINA — Fase 42
# -----------------------------------------------------------------------------
# En la F41, gestionar_clave.ps1 -Accion Crear no pudo escribir
# MARATHON_CRYPTO_KEY_PROTECTED en el ambito de MAQUINA porque eso exige una
# consola elevada, y cayo al ambito de USUARIO.
#
# POR QUE IMPORTA. En ambito de usuario, la variable solo la ve la cuenta que la
# creo. Si el backend se ejecutase como un servicio de Windows —bajo SYSTEM,
# NETWORK SERVICE o una cuenta de servicio dedicada, que es como deberia correr
# en produccion— no encontraria la clave y la aplicacion arrancaria mostrando
# los datos de contacto vacios, sin mas aviso que un WARN en el registro.
#
# NO ES UN PROBLEMA DE SEGURIDAD: lo que se guarda en la variable es el blob
# DPAPI cifrado, no la clave. Es un problema de disponibilidad.
#
# El blob sigue siendo de ambito LocalMachine, asi que promover la variable no
# cambia quien puede descifrarlo: cualquier cuenta de ESTE equipo podia ya, y
# ninguna de otro equipo puede.
#
# USO (dispara UAC; hay que aceptarlo):
#   powershell -ExecutionPolicy Bypass -File promover_clave_a_maquina.ps1
#
# O manualmente, en una consola de PowerShell ABIERTA COMO ADMINISTRADOR:
#   $v = [Environment]::GetEnvironmentVariable('MARATHON_CRYPTO_KEY_PROTECTED','User')
#   [Environment]::SetEnvironmentVariable('MARATHON_CRYPTO_KEY_PROTECTED',$v,'Machine')
#   [Environment]::SetEnvironmentVariable('MARATHON_CRYPTO_KEY_PROTECTED',$null,'User')
# =============================================================================

param([switch] $YaElevado)

$ErrorActionPreference = 'Stop'
$Var = 'MARATHON_CRYPTO_KEY_PROTECTED'

function Test-Administrador {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    (New-Object Security.Principal.WindowsPrincipal($id)).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)
}

if (-not (Test-Administrador)) {
    if ($YaElevado) { throw "Se pidio elevacion y aun asi no hay privilegios de administrador." }
    Write-Host "Se necesita elevacion. Va a aparecer el aviso de Control de cuentas de usuario (UAC)."
    try {
        $p = Start-Process powershell -Verb RunAs -PassThru -Wait -ArgumentList @(
                 '-NoProfile','-ExecutionPolicy','Bypass','-File',"`"$PSCommandPath`"",'-YaElevado')
        exit $p.ExitCode
    } catch {
        Write-Host ""
        Write-Host "UAC cancelado o no aceptado. La variable sigue en ambito de USUARIO."
        Write-Host "La aplicacion funciona igual mientras la ejecute esta misma cuenta."
        Write-Host "Para promoverla mas tarde, volver a lanzar este script y aceptar el aviso."
        exit 2
    }
}

# --- ya elevado ---------------------------------------------------------------
$valor = [Environment]::GetEnvironmentVariable($Var, 'User')
if (-not $valor) {
    $valor = [Environment]::GetEnvironmentVariable($Var, 'Machine')
    if ($valor) { Write-Host "La variable YA estaba en ambito de maquina. Nada que hacer."; exit 0 }
    throw "No existe $Var en ningun ambito. Ejecutar antes gestionar_clave.ps1 -Accion Crear"
}

[Environment]::SetEnvironmentVariable($Var, $valor, 'Machine')
# Se retira la de usuario para que no queden dos copias que puedan divergir si
# algun dia se rota la clave.
[Environment]::SetEnvironmentVariable($Var, $null, 'User')

Write-Host "$Var promovida al ambito de MAQUINA."
Write-Host "  Maquina : $([bool][Environment]::GetEnvironmentVariable($Var,'Machine'))"
Write-Host "  Usuario : $([bool][Environment]::GetEnvironmentVariable($Var,'User'))  (retirada a proposito)"
exit 0
