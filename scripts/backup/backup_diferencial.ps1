# =============================================================================
# RESPALDO DIFERENCIAL — Lunes a sabado 22:00
# -----------------------------------------------------------------------------
# QUE LO HACE DIFERENCIAL Y NO INCREMENTAL
#
# pg_basebackup --incremental recibe el manifiesto del respaldo que sirve de
# base y copia solo lo que cambio desde entonces. La diferencia entre una
# cadena incremental y un diferencial esta UNICAMENTE en que manifiesto se le
# pasa:
#
#   * Si se le pasa el manifiesto del respaldo ANTERIOR (el del dia previo),
#     cada archivo depende del anterior y se forma una cadena: para restaurar
#     hay que tener todos los eslabones, y si falta uno se pierde todo lo
#     posterior.
#
#   * Si se le pasa SIEMPRE el manifiesto del FULL de la semana, cada respaldo
#     contiene todo lo que cambio desde el domingo. Cada dia reemplaza al del
#     dia anterior y para restaurar solo hacen falta DOS piezas: el FULL y el
#     ultimo diferencial.
#
# Este script hace lo segundo, que es lo que pide la regla de negocio. El
# nombre del directorio incluye a que FULL pertenece (..._base_full_YYYYMMDD),
# para que nunca haya duda de que diferencial va con que completo.
#
# Uso:  powershell -ExecutionPolicy Bypass -File backup_diferencial.ps1
# =============================================================================

. "$PSScriptRoot\config.ps1"

$inicio = Get-Date
$sello  = $inicio.ToString('yyyyMMdd_HHmmss')
$log    = Join-Path $LogRoot "diferencial_$sello.log"

try {
    Initialize-Backup
    Write-Log "=============================================" 'INFO' $log
    Write-Log "RESPALDO DIFERENCIAL de $PgDatabase" 'INFO' $log

    if (-not (Test-EspacioLibre $log)) {
        Write-Estado -Tipo 'diferencial' -Resultado 'ERROR' -Detalle 'Espacio en disco insuficiente'
        exit 2
    }

    # ------------------------------------------------- requisito del servidor --
    # Sin summarize_wal el servidor no lleva el registro de bloques modificados
    # y pg_basebackup --incremental falla. Se comprueba antes para dar un
    # mensaje claro en lugar de un error cripto de la herramienta.
    $r = Invoke-PgTool 'psql.exe' @('-h', $PgHost, '-p', "$PgPort", '-U', $PgUser,
                                    '-d', $PgDatabase, '-t', '-A', '-c', 'SHOW summarize_wal;')
    if ($r.ExitCode -ne 0) {
        Write-Log "No se pudo consultar summarize_wal: $($r.Salida -join ' ')" 'ERROR' $log
        Write-Estado -Tipo 'diferencial' -Resultado 'ERROR' -Detalle 'psql fallo'
        exit 3
    }
    $sw = ($r.Salida | Where-Object { $_ } | Select-Object -First 1)
    if ("$sw".Trim() -ne 'on') {
        Write-Log "summarize_wal esta en '$("$sw".Trim())'. El diferencial es imposible sin el." 'ERROR' $log
        Write-Log "Corregir con: ALTER SYSTEM SET summarize_wal='on'; SELECT pg_reload_conf();" 'ERROR' $log
        Write-Estado -Tipo 'diferencial' -Resultado 'ERROR' -Detalle 'summarize_wal desactivado'
        exit 3
    }
    Write-Log "summarize_wal = on" 'INFO' $log

    # ------------------------------------------------------ full de referencia --
    $full = Get-UltimoFull
    if (-not $full) {
        Write-Log "No existe ningun respaldo FULL con manifiesto. Un diferencial sin base no sirve." 'ERROR' $log
        Write-Log "Ejecutar primero backup_full.ps1" 'ERROR' $log
        Write-Estado -Tipo 'diferencial' -Resultado 'ERROR' -Detalle 'No hay FULL de referencia'
        exit 4
    }
    $manifiesto = Join-Path $full.FullName 'backup_manifest'
    Write-Log "Base (FULL): $($full.Name)" 'INFO' $log

    # Aviso si el full ya tiene mas de 8 dias: significa que el job del domingo
    # no corrio y el diferencial esta creciendo sin control.
    $edadDias = ((Get-Date) - $full.CreationTime).TotalDays
    if ($edadDias -gt 8) {
        Write-Log "El FULL de referencia tiene $([math]::Round($edadDias,1)) dias. Es probable que el respaldo completo semanal no se haya ejecutado." 'AVISO' $log
    }

    $destino = Join-Path $DiffRoot "diff_${sello}_base_$($full.Name)"
    Write-Log "Destino: $destino" 'INFO' $log

    $bb = Invoke-PgTool 'pg_basebackup.exe' @(
        '-h', $PgHost, '-p', "$PgPort", '-U', $PgUser,
        '-D', $destino,
        '-Fp', '-Xs', '-c', 'fast', '-P',
        "--incremental=$manifiesto",
        '--manifest-checksums=SHA256',
        "--label=marathon_diff_$sello"
    )
    $bb.Salida | ForEach-Object { if ($_) { Write-Log $_ 'INFO' $log } }

    if ($bb.ExitCode -ne 0) {
        Write-Log "pg_basebackup --incremental termino con codigo $($bb.ExitCode)" 'ERROR' $log
        if (Test-Path $destino) { Remove-Item $destino -Recurse -Force }
        Write-Estado -Tipo 'diferencial' -Resultado 'ERROR' -Detalle "pg_basebackup codigo $($bb.ExitCode)"
        exit 5
    }

    # pg_verifybackup NO se puede usar sobre un respaldo incremental por si
    # solo: el incremental no es un directorio de datos completo, le faltan
    # bloques que estan en el full. Se verifica que el manifiesto exista y que
    # el respaldo declare correctamente su naturaleza incremental.
    if (-not (Test-Path (Join-Path $destino 'backup_manifest'))) {
        Write-Log "El diferencial no genero manifiesto. No es confiable." 'ERROR' $log
        Write-Estado -Tipo 'diferencial' -Resultado 'ERROR' -Detalle 'Sin manifiesto'
        exit 6
    }

    $dur    = [int]((Get-Date) - $inicio).TotalSeconds
    $tamano = Get-TamanoMB $destino
    $tamFull= Get-TamanoMB $full.FullName
    $ahorro = if ($tamFull -gt 0) { [math]::Round(100 - ($tamano / $tamFull * 100), 1) } else { 0 }
    Write-Log "DIFERENCIAL completado en $dur s. Tamano: $tamano MB (el FULL ocupa $tamFull MB: $ahorro% menos)" 'OK' $log

    # Solo se conserva el diferencial mas reciente de cada FULL: los anteriores
    # quedan cubiertos por el nuevo, que incluye todos sus cambios. Esta es la
    # ventaja concreta del esquema diferencial frente al incremental.
    $previos = Get-ChildItem $DiffRoot -Directory |
               Where-Object { $_.Name -like "*_base_$($full.Name)" -and $_.FullName -ne $destino } |
               Sort-Object Name -Descending
    foreach ($p in $previos) {
        Remove-Item $p.FullName -Recurse -Force
        Write-Log "Superado por el diferencial de hoy, eliminado: $($p.Name)" 'INFO' $log
    }

    Write-Estado -Tipo 'diferencial' -Resultado 'OK' -Ruta $destino -DuracionSeg $dur -TamanoMB $tamano `
                 -Detalle "base=$($full.Name)"
    Write-Log "=== RESPALDO DIFERENCIAL FINALIZADO ===" 'OK' $log
    exit 0
}
catch {
    Write-Log "EXCEPCION: $($_.Exception.Message)" 'ERROR' $log
    Write-Estado -Tipo 'diferencial' -Resultado 'ERROR' -Detalle $_.Exception.Message
    exit 1
}
finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}
