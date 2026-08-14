# =============================================================================
# REGISTRO DE LAS TAREAS PROGRAMADAS (Programador de tareas de Windows)
# -----------------------------------------------------------------------------
# Equivalencia con la notacion cron que define la regla de negocio:
#
#   0 23 * * 0    -> Domingo      23:00   Respaldo COMPLETO
#   0 22 * * 1-6  -> Lunes-Sabado 22:00   Respaldo DIFERENCIAL
#   0 23 * * 0    -> Domingo      23:30   Respaldo de la capa de APLICACION
#   0  8 * * *    -> Todos los dias 08:00 VERIFICACION del estado
#
# En Windows no hay cron; el equivalente es el Programador de tareas. Se usa
# ScheduledTasks de PowerShell en lugar de schtasks.exe porque permite declarar
# la configuracion (reintentos, ejecucion diferida si el equipo estaba apagado)
# de forma explicita y legible.
#
# REQUIERE PRIVILEGIOS DE ADMINISTRADOR. El script lo comprueba y avisa.
#
# Uso (consola como administrador):
#   powershell -ExecutionPolicy Bypass -File registrar_tareas.ps1
#   powershell -ExecutionPolicy Bypass -File registrar_tareas.ps1 -Eliminar
# =============================================================================

param(
    [switch] $Eliminar,
    # Cuenta con la que corren las tareas. SYSTEM no necesita contrasena
    # almacenada y sobrevive a cambios de clave del usuario, que es lo que se
    # quiere para un proceso desatendido.
    [string] $Usuario = 'SYSTEM'
)

$ErrorActionPreference = 'Stop'
$carpeta = '\MarathonSports\'

$esAdmin = ([Security.Principal.WindowsPrincipal] `
            [Security.Principal.WindowsIdentity]::GetCurrent()
           ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $esAdmin) {
    Write-Host ""
    Write-Host "Este script necesita privilegios de administrador para registrar tareas" -ForegroundColor Yellow
    Write-Host "programadas que corran con la cuenta SYSTEM." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Abrir PowerShell como administrador y volver a ejecutar:" -ForegroundColor Yellow
    Write-Host "  cd '$PSScriptRoot'" -ForegroundColor Cyan
    Write-Host "  .\registrar_tareas.ps1" -ForegroundColor Cyan
    Write-Host ""
    exit 1
}

$tareas = @(
    @{
        Nombre      = 'Marathon_Respaldo_Full'
        Script      = 'backup_full.ps1'
        Descripcion = 'Respaldo COMPLETO de mod_venta_inve. Domingo 23:00. Base de los diferenciales de la semana.'
        Disparador  = (New-ScheduledTaskTrigger -Weekly -DaysOfWeek Sunday -At '23:00')
    },
    @{
        Nombre      = 'Marathon_Respaldo_Diferencial'
        Script      = 'backup_diferencial.ps1'
        Descripcion = 'Respaldo DIFERENCIAL de mod_venta_inve. Lunes a sabado 22:00. Acumula todo lo cambiado desde el completo del domingo.'
        Disparador  = (New-ScheduledTaskTrigger -Weekly -DaysOfWeek Monday,Tuesday,Wednesday,Thursday,Friday,Saturday -At '22:00')
    },
    @{
        Nombre      = 'Marathon_Respaldo_Aplicacion'
        Script      = 'backup_aplicacion.ps1'
        Descripcion = 'Respaldo de la capa de aplicacion: punto de recuperacion Git, configuracion cifrada y archivos subidos. Domingo 23:30.'
        Disparador  = (New-ScheduledTaskTrigger -Weekly -DaysOfWeek Sunday -At '23:30')
    },
    @{
        Nombre      = 'Marathon_Verificar_Respaldos'
        Script      = 'verificar_respaldos.ps1'
        Descripcion = 'Comprueba a diario que los respaldos se estan ejecutando. Avisa por ausencia, que es el fallo que nadie nota. 08:00.'
        Disparador  = (New-ScheduledTaskTrigger -Daily -At '08:00')
    }
)

# ============================================================================ #
if ($Eliminar) {
    foreach ($t in $tareas) {
        $existe = Get-ScheduledTask -TaskName $t.Nombre -TaskPath $carpeta -ErrorAction SilentlyContinue
        if ($existe) {
            Unregister-ScheduledTask -TaskName $t.Nombre -TaskPath $carpeta -Confirm:$false
            Write-Host "Eliminada: $($t.Nombre)" -ForegroundColor Yellow
        } else {
            Write-Host "No existia: $($t.Nombre)"
        }
    }
    Write-Host "`nTareas eliminadas. Los respaldos ya generados NO se han tocado." -ForegroundColor Green
    exit 0
}

# ============================================================================ #
$principal = New-ScheduledTaskPrincipal -UserId $Usuario -LogonType ServiceAccount -RunLevel Highest

# StartWhenAvailable: si el equipo estaba apagado a la hora prevista, la tarea se
#   ejecuta en cuanto se enciende, en lugar de saltarse la semana entera.
# RestartCount/RestartInterval: reintenta si falla por un bloqueo transitorio.
# ExecutionTimeLimit de 4 h: evita que un respaldo colgado quede corriendo dias.
$ajustes = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -DontStopOnIdleEnd `
    -RestartCount 2 -RestartInterval (New-TimeSpan -Minutes 15) `
    -ExecutionTimeLimit (New-TimeSpan -Hours 4) `
    -MultipleInstances IgnoreNew

foreach ($t in $tareas) {
    $ruta = Join-Path $PSScriptRoot $t.Script
    if (-not (Test-Path $ruta)) { throw "No se encontro el script $ruta" }

    $accion = New-ScheduledTaskAction `
        -Execute 'powershell.exe' `
        -Argument "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$ruta`"" `
        -WorkingDirectory $PSScriptRoot

    $existe = Get-ScheduledTask -TaskName $t.Nombre -TaskPath $carpeta -ErrorAction SilentlyContinue
    if ($existe) {
        Unregister-ScheduledTask -TaskName $t.Nombre -TaskPath $carpeta -Confirm:$false
    }

    Register-ScheduledTask `
        -TaskName    $t.Nombre `
        -TaskPath    $carpeta `
        -Action      $accion `
        -Trigger     $t.Disparador `
        -Principal   $principal `
        -Settings    $ajustes `
        -Description $t.Descripcion | Out-Null

    Write-Host "Registrada: $($t.Nombre)" -ForegroundColor Green
}

Write-Host ""
Write-Host "--- Tareas registradas en $carpeta ---" -ForegroundColor Cyan
Get-ScheduledTask -TaskPath $carpeta |
    Select-Object TaskName, State,
        @{n='ProximaEjecucion'; e={ (Get-ScheduledTaskInfo -TaskName $_.TaskName -TaskPath $carpeta).NextRunTime }} |
    Format-Table -AutoSize

Write-Host "Para probar una tarea sin esperar su horario:" -ForegroundColor Cyan
Write-Host "  Start-ScheduledTask -TaskName 'Marathon_Respaldo_Full' -TaskPath '$carpeta'" -ForegroundColor Gray
Write-Host "Para ver el resultado de la ultima ejecucion:" -ForegroundColor Cyan
Write-Host "  Get-ScheduledTaskInfo -TaskName 'Marathon_Respaldo_Full' -TaskPath '$carpeta'" -ForegroundColor Gray
Write-Host ""
Write-Host "IMPORTANTE: las tareas corren como $Usuario. Esa cuenta debe poder leer" -ForegroundColor Yellow
Write-Host "el archivo .env del proyecto y escribir en C:\respaldos\marathon." -ForegroundColor Yellow
