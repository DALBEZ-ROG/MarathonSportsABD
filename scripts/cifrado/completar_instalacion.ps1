# =============================================================================
# PASOS QUE EXIGEN ELEVACION — Fase 42
# -----------------------------------------------------------------------------
# Reune en UN SOLO aviso de UAC las dos cosas que quedaron pendientes por
# necesitar privilegios de administrador. Pedir el UAC dos veces para dos tareas
# que se hacen juntas es una forma seguraex de que la segunda no se haga nunca.
#
#   1. Promover MARATHON_CRYPTO_KEY_PROTECTED al ambito de MAQUINA.
#      En ambito de usuario, solo la ve la cuenta que la creo: si el backend
#      corriera como servicio de Windows no encontraria la clave y arrancaria
#      mostrando los datos de contacto vacios. Lo que se guarda es el blob DPAPI
#      cifrado, no la clave, asi que promoverla no cambia quien puede leerla.
#
#   2. Registrar las tareas programadas de respaldo.
#      Los scripts de respaldo existen y funcionan, pero NADIE los ejecuta:
#      Get-ScheduledTask no devuelve ninguna tarea de Marathon. Un plan de
#      respaldos que depende de que alguien se acuerde no es un plan de
#      respaldos.
#
# USO:  powershell -ExecutionPolicy Bypass -File completar_instalacion.ps1
# =============================================================================

param([switch] $YaElevado)

$ErrorActionPreference = 'Continue'
$Var = 'MARATHON_CRYPTO_KEY_PROTECTED'

function Test-Administrador {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    (New-Object Security.Principal.WindowsPrincipal($id)).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)
}

if (-not (Test-Administrador)) {
    if ($YaElevado) { throw "Se pidio elevacion y aun asi no hay privilegios de administrador." }
    Write-Host "Se necesita elevacion UNA VEZ para dos tareas:"
    Write-Host "  1. promover la variable de la clave al ambito de maquina"
    Write-Host "  2. registrar las tareas programadas de respaldo"
    Write-Host "Va a aparecer el aviso de Control de cuentas de usuario (UAC)."
    try {
        $p = Start-Process powershell -Verb RunAs -PassThru -Wait -ArgumentList @(
                 '-NoProfile','-ExecutionPolicy','Bypass','-File',"`"$PSCommandPath`"",'-YaElevado')
        exit $p.ExitCode
    } catch {
        Write-Host ""
        Write-Host "UAC no aceptado. NADA se ha cambiado. Consecuencias mientras siga asi:"
        Write-Host "  - la clave funciona solo para esta cuenta de usuario"
        Write-Host "  - los respaldos NO se ejecutan solos: hay que lanzarlos a mano"
        exit 2
    }
}

# ============================ ya elevado =====================================
$fallos = 0

Write-Host "--- 1. Variable de la clave al ambito de maquina ---"
$valor = [Environment]::GetEnvironmentVariable($Var, 'User')
if ($valor) {
    [Environment]::SetEnvironmentVariable($Var, $valor, 'Machine')
    [Environment]::SetEnvironmentVariable($Var, $null, 'User')
    Write-Host "    OK: promovida (y retirada del ambito de usuario para que no diverjan)."
} elseif ([Environment]::GetEnvironmentVariable($Var, 'Machine')) {
    Write-Host "    OK: ya estaba en ambito de maquina."
} else {
    Write-Host "    AVISO: no existe la variable en ningun ambito. Ejecutar gestionar_clave.ps1 -Accion Crear"
    $fallos++
}

Write-Host ""
Write-Host "--- 2. Tareas programadas de respaldo ---"
$registrar = (Join-Path $PSScriptRoot '..\backup\registrar_tareas.ps1')
if (Test-Path $registrar) {
    # Se invoca EN ESTE MISMO PROCESO, no lanzando otro powershell.
    # La primera version hacia `& powershell -File $registrar` y no registraba
    # nada, sin dar error: el proceso hijo no heredaba bien el token elevado y
    # registrar_tareas.ps1 salia por su propia comprobacion de administrador,
    # cuyo mensaje se perdia al cerrarse la ventana. Con `&` directo el script
    # corre con el token que ya tenemos y su salida se ve aqui.
    & $registrar
    if (-not $?) { Write-Host "    AVISO: registrar_tareas.ps1 fallo."; $fallos++ }
} else {
    Write-Host "    AVISO: no se encontro $registrar"; $fallos++
}

Write-Host ""
Write-Host "--- Comprobacion final ---"
Write-Host "    Variable en maquina : $([bool][Environment]::GetEnvironmentVariable($Var,'Machine'))"
$tareas = Get-ScheduledTask -ErrorAction SilentlyContinue | Where-Object { $_.TaskPath -like '*Marathon*' }
Write-Host "    Tareas registradas  : $($tareas.Count)"
$tareas | ForEach-Object { Write-Host ("      {0,-28} {1}" -f $_.TaskName, $_.State) }

exit $fallos
