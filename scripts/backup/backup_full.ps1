# =============================================================================
# RESPALDO COMPLETO (FULL) — Domingo 23:00
# -----------------------------------------------------------------------------
# Usa pg_basebackup en formato PLANO. Plano y no tar por una razon concreta:
# pg_combinebackup, que es la herramienta que fusiona el full con el diferencial
# al restaurar, opera sobre directorios de datos. Un full en tar habria que
# desempacarlo primero, sumando tiempo al RTO sin ningun beneficio.
#
# Genera tambien el manifiesto (backup_manifest) con checksums SHA256. Ese
# manifiesto cumple dos funciones: permite verificar el respaldo con
# pg_verifybackup, y es el punto de referencia contra el que se calculan los
# diferenciales de lunes a sabado.
#
# Uso:  powershell -ExecutionPolicy Bypass -File backup_full.ps1
# =============================================================================

. "$PSScriptRoot\config.ps1"

$inicio = Get-Date
$sello  = $inicio.ToString('yyyyMMdd_HHmmss')
$log    = Join-Path $LogRoot "full_$sello.log"

try {
    Initialize-Backup
    Write-Log "=============================================" 'INFO' $log
    Write-Log "RESPALDO COMPLETO de $PgDatabase" 'INFO' $log

    if (-not (Test-EspacioLibre $log)) {
        Write-Estado -Tipo 'full' -Resultado 'ERROR' -Detalle 'Espacio en disco insuficiente'
        exit 2
    }

    # Comprobar que el servidor responde antes de intentar nada
    $ready = Invoke-PgTool 'pg_isready.exe' @('-h', $PgHost, '-p', "$PgPort", '-U', $PgUser)
    if ($ready.ExitCode -ne 0) {
        Write-Log "El servidor PostgreSQL no responde. Respaldo ABORTADO." 'ERROR' $log
        Write-Estado -Tipo 'full' -Resultado 'ERROR' -Detalle 'pg_isready fallo'
        exit 3
    }

    $destino = Join-Path $FullRoot "full_$sello"
    Write-Log "Destino: $destino" 'INFO' $log

    # -Fp  formato plano (requisito de pg_combinebackup)
    # -Xs  incluye el WAL generado durante el respaldo por streaming: el
    #      resultado queda consistente por si solo, sin depender del archivado
    # -c fast  checkpoint inmediato, para no esperar al checkpoint programado
    # -P   progreso
    $bb = Invoke-PgTool 'pg_basebackup.exe' @(
        '-h', $PgHost, '-p', "$PgPort", '-U', $PgUser,
        '-D', $destino,
        '-Fp', '-Xs', '-c', 'fast', '-P',
        '--manifest-checksums=SHA256',
        "--label=marathon_full_$sello"
    )
    $bb.Salida | ForEach-Object { if ($_) { Write-Log $_ 'INFO' $log } }

    if ($bb.ExitCode -ne 0) {
        Write-Log "pg_basebackup termino con codigo $($bb.ExitCode)" 'ERROR' $log
        if (Test-Path $destino) {
            Remove-Item $destino -Recurse -Force
            Write-Log "Respaldo incompleto eliminado para no dejar un full corrupto que luego se use como base de diferenciales." 'AVISO' $log
        }
        Write-Estado -Tipo 'full' -Resultado 'ERROR' -Detalle "pg_basebackup codigo $($bb.ExitCode)"
        exit 4
    }

    # ---------------------------------------------------------------- verificar
    # Un respaldo que no se verifica no es un respaldo, es una suposicion.
    Write-Log "Verificando el respaldo con pg_verifybackup..." 'INFO' $log
    $vb = Invoke-PgTool 'pg_verifybackup.exe' @($destino)
    $vb.Salida | ForEach-Object { if ($_) { Write-Log $_ 'INFO' $log } }
    if ($vb.ExitCode -ne 0) {
        Write-Log "LA VERIFICACION FALLO. El respaldo no es confiable." 'ERROR' $log
        Write-Estado -Tipo 'full' -Resultado 'ERROR' -Detalle 'pg_verifybackup fallo'
        exit 5
    }
    Write-Log "Verificacion correcta: checksums y manifiesto coinciden." 'OK' $log

    # Al empezar una semana nueva, los diferenciales de la semana anterior
    # quedan huerfanos (referencian un manifiesto que ya no es el vigente).
    # Se archivan junto al full al que pertenecen, no se mezclan.
    $dur    = [int]((Get-Date) - $inicio).TotalSeconds
    $tamano = Get-TamanoMB $destino
    Write-Log "FULL completado en $dur s. Tamano: $tamano MB" 'OK' $log

    # ---------------------------------------------------------------- retencion
    $fulls = Get-ChildItem $FullRoot -Directory | Sort-Object Name -Descending
    if ($fulls.Count -gt $SemanasRetencion) {
        foreach ($viejo in $fulls | Select-Object -Skip $SemanasRetencion) {
            # Los diferenciales del full que se va tambien se eliminan: sin su
            # base son inservibles y solo ocupan disco.
            $difs = Get-ChildItem $DiffRoot -Directory -ErrorAction SilentlyContinue |
                    Where-Object { $_.Name -like "*_base_$($viejo.Name)" }
            foreach ($d in $difs) {
                Remove-Item $d.FullName -Recurse -Force
                Write-Log "Retencion: eliminado diferencial huerfano $($d.Name)" 'INFO' $log
            }
            Remove-Item $viejo.FullName -Recurse -Force
            Write-Log "Retencion: eliminado full antiguo $($viejo.Name)" 'INFO' $log
        }
    }

    Write-Estado -Tipo 'full' -Resultado 'OK' -Ruta $destino -DuracionSeg $dur -TamanoMB $tamano
    Write-Log "=== RESPALDO COMPLETO FINALIZADO ===" 'OK' $log
    exit 0
}
catch {
    Write-Log "EXCEPCION: $($_.Exception.Message)" 'ERROR' $log
    Write-Estado -Tipo 'full' -Resultado 'ERROR' -Detalle $_.Exception.Message
    exit 1
}
finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}
