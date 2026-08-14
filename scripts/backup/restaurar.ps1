# =============================================================================
# RESTAURACION — fusiona FULL + ultimo DIFERENCIAL
# -----------------------------------------------------------------------------
# La restauracion tiene exactamente DOS insumos, que es la ventaja del esquema
# diferencial: el respaldo completo del domingo y el diferencial mas reciente.
# pg_combinebackup los fusiona en un directorio de datos utilizable.
#
# DOS MODOS:
#
#   -Modo Prueba      (por omision, NO DESTRUCTIVO)
#       Fusiona y arranca una instancia temporal en otro puerto para comprobar
#       que los datos estan ahi. No toca la base de produccion. Es el modo que
#       se usa para medir el RTO y para el simulacro periodico.
#
#   -Modo Produccion  (DESTRUCTIVO, exige -Confirmar)
#       Detiene el servicio, aparta el directorio de datos actual y lo
#       reemplaza por el restaurado. Se usa en un desastre real.
#       El directorio actual NO se borra: se renombra con sufijo .reemplazado_<fecha>
#       para poder volver atras si la restauracion sale mal.
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File restaurar.ps1
#   powershell -ExecutionPolicy Bypass -File restaurar.ps1 -Modo Produccion -Confirmar
# =============================================================================

param(
    [ValidateSet('Prueba','Produccion')] [string] $Modo = 'Prueba',
    [switch] $Confirmar,
    [int]    $PuertoPrueba = 5433
)

. "$PSScriptRoot\config.ps1"

$inicio = Get-Date
$sello  = $inicio.ToString('yyyyMMdd_HHmmss')
$log    = Join-Path $LogRoot "restauracion_$sello.log"

function Resumen-RTO {
    param([datetime] $Desde, [string] $Etapa)
    $seg = [int]((Get-Date) - $Desde).TotalSeconds
    Write-Log ("{0}: {1} s acumulados ({2} min)" -f $Etapa, $seg, [math]::Round($seg/60,1)) 'INFO' $log
    return $seg
}

try {
    Initialize-Backup
    Write-Log "=============================================" 'INFO' $log
    Write-Log "RESTAURACION en modo $Modo" 'INFO' $log

    if ($Modo -eq 'Produccion' -and -not $Confirmar) {
        Write-Log "El modo Produccion reemplaza el directorio de datos en uso. Volver a ejecutar agregando -Confirmar si es lo que se pretende." 'ERROR' $log
        exit 10
    }

    # ------------------------------------------------- localizar los 2 insumos --
    $full = Get-UltimoFull
    if (-not $full) { Write-Log "No hay respaldo FULL disponible." 'ERROR' $log; exit 4 }

    $diff = Get-ChildItem $DiffRoot -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "*_base_$($full.Name)" } |
            Sort-Object Name -Descending | Select-Object -First 1

    Write-Log "Insumo 1 (FULL)        : $($full.Name)" 'INFO' $log
    if ($diff) {
        Write-Log "Insumo 2 (DIFERENCIAL) : $($diff.Name)" 'INFO' $log
    } else {
        Write-Log "No hay diferencial para este FULL. Se restaurara solo el completo (perdida de datos hasta 7 dias: revisar por que no corrio el job diario)." 'AVISO' $log
    }

    $salidaDir = Join-Path $RestoreRoot "restaurado_$sello"

    # --------------------------------------------------------- pg_combinebackup --
    Write-Log "Fusionando respaldos con pg_combinebackup..." 'INFO' $log
    $args = @()
    $args += $full.FullName
    if ($diff) { $args += $diff.FullName }
    $args += @('-o', $salidaDir)

    $cb = Invoke-PgTool 'pg_combinebackup.exe' $args
    $cb.Salida | ForEach-Object { if ($_) { Write-Log $_ 'INFO' $log } }
    if ($cb.ExitCode -ne 0) {
        Write-Log "pg_combinebackup fallo con codigo $($cb.ExitCode)" 'ERROR' $log
        exit 5
    }
    Write-Log "Fusion completada. Directorio de datos: $salidaDir" 'OK' $log
    $tFusion = Resumen-RTO $inicio 'Fusion'

    # ------------------------------------------------------------ integridad ----
    $cd = Invoke-PgTool 'pg_controldata.exe' @($salidaDir)
    $estadoCluster = ($cd.Salida | Select-String 'Database cluster state|Estado del cluster') -join ' '
    Write-Log "pg_controldata: $estadoCluster" 'INFO' $log

    # ==========================================================================
    if ($Modo -eq 'Prueba') {
    # ==========================================================================
        Write-Log "Arrancando instancia temporal en el puerto $PuertoPrueba..." 'INFO' $log

        # pg_ctl en Windows se niega a arrancar desde una consola con privilegios
        # de administrador. Si eso ocurre no es un fallo del respaldo, asi que se
        # informa y se cae a una verificacion estructural.
        $pgctlLog = Join-Path $LogRoot "instancia_prueba_$sello.log"
        $st = Invoke-PgCtl @('-D', "`"$salidaDir`"", '-o', "`"-p $PuertoPrueba`"",
                             '-l', "`"$pgctlLog`"", '-w', '-t', '60', 'start')
        $st.Salida | ForEach-Object { if ($_) { Write-Log $_ 'INFO' $log } }

        if ($st.ExitCode -ne 0) {
            Write-Log "No se pudo arrancar la instancia temporal (codigo $($st.ExitCode))." 'AVISO' $log
            Write-Log "Causa habitual en Windows: la consola tiene privilegios de administrador. Reintentar en una consola normal." 'AVISO' $log
            Write-Log "Se valida la estructura con pg_verifybackup sobre el resultado fusionado." 'AVISO' $log
            $vb = Invoke-PgTool 'pg_verifybackup.exe' @('-n', $salidaDir)
            $vb.Salida | ForEach-Object { if ($_) { Write-Log $_ 'INFO' $log } }
            exit 6
        }

        Write-Log "Instancia arriba. Comprobando el contenido..." 'OK' $log
        $consulta = @"
SELECT 'usuarios='   || (SELECT count(*) FROM usuario)
    || ' clientes='  || (SELECT count(*) FROM cliente)
    || ' productos=' || (SELECT count(*) FROM producto)
    || ' pedidos='   || (SELECT count(*) FROM pedido)
    || ' detalles='  || (SELECT count(*) FROM detalle_pedido)
    || ' inventario='|| (SELECT count(*) FROM inventario)
    || ' logs='      || (SELECT count(*) FROM log_accion) AS conteos;
SELECT count(*) AS marca_diferencial FROM log_accion WHERE descripcion = 'MARCA-DIFERENCIAL-F35';
SELECT count(*) AS roles_restaurados FROM pg_roles WHERE rolname LIKE 'rol\_%';
SELECT count(*) AS indices_f33 FROM pg_indexes WHERE schemaname='public'
  AND indexname IN ('idx_pedido_estado_fecha','idx_pedido_cliente_fecha','idx_inventario_stock_bajo','idx_log_modulo_fecha');
"@
        $consulta | Out-File -Encoding ascii (Join-Path $env:TEMP 'verif_restore.sql')
        $q = Invoke-PgTool 'psql.exe' @('-h', 'localhost', '-p', "$PuertoPrueba", '-U', $PgUser,
                                        '-d', $PgDatabase, '-f', (Join-Path $env:TEMP 'verif_restore.sql'))
        $q.Salida | ForEach-Object { if ($_) { Write-Log $_ 'INFO' $log } }

        $tListo = Resumen-RTO $inicio 'Base restaurada y consultable'

        Write-Log "Deteniendo la instancia temporal..." 'INFO' $log
        $sp = Invoke-PgCtl @('-D', "`"$salidaDir`"", '-w', '-t', '60', '-m', 'fast', 'stop')
        $sp.Salida | ForEach-Object { if ($_) { Write-Log $_ 'INFO' $log } }
        if ($sp.ExitCode -ne 0) {
            Write-Log "La instancia temporal NO se detuvo. Detenerla a mano para no dejar un servidor escuchando en el puerto ${PuertoPrueba}." 'ERROR' $log
            Write-Log "  pg_ctl -D `"$salidaDir`" -m fast stop" 'ERROR' $log
        }

        # -------------------------------------------------------------- RTO -----
        $rtoMin      = [math]::Round($tListo / 60, 2)
        $objetivoMin = 120
        Write-Log "-----------------------------------------------" 'INFO' $log
        Write-Log ("RTO MEDIDO      : {0} s  ({1} min)" -f $tListo, $rtoMin) 'OK' $log
        Write-Log ("RTO OBJETIVO    : {0} min" -f $objetivoMin) 'INFO' $log
        if ($rtoMin -le $objetivoMin) {
            Write-Log ("CUMPLE el RTO con un margen de {0} min" -f [math]::Round($objetivoMin - $rtoMin, 2)) 'OK' $log
        } else {
            Write-Log "NO CUMPLE el RTO objetivo" 'ERROR' $log
        }

        Write-Estado -Tipo 'restauracion' -Resultado 'OK' -Ruta $salidaDir `
                     -DuracionSeg $tListo -TamanoMB (Get-TamanoMB $salidaDir) `
                     -Detalle "RTO=$rtoMin min; objetivo=$objetivoMin min"

        Write-Log "El directorio restaurado queda en $salidaDir para inspeccion. Eliminarlo cuando ya no se necesite." 'INFO' $log
        exit 0
    }

    # ==========================================================================
    # MODO PRODUCCION
    # ==========================================================================
    Write-Log "ATENCION: se va a reemplazar el directorio de datos en produccion." 'AVISO' $log
    $dataDir = (Invoke-PgTool 'psql.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$PgUser,'-d',$PgDatabase,
                                           '-t','-A','-c','SHOW data_directory;')).Salida |
               Where-Object { $_ } | Select-Object -First 1
    $dataDir = "$dataDir".Trim()
    Write-Log "Directorio de datos actual: $dataDir" 'INFO' $log

    Write-Log "Deteniendo el servicio $PgService..." 'INFO' $log
    Stop-Service -Name $PgService -Force
    (Get-Service $PgService).WaitForStatus('Stopped', '00:02:00')
    Write-Log "Servicio detenido." 'OK' $log

    # El directorio anterior se aparta, no se destruye: es la unica via de vuelta
    # si el respaldo restaurado resultara estar mal.
    $apartado = "$dataDir.reemplazado_$sello"
    Rename-Item -Path $dataDir -NewName (Split-Path $apartado -Leaf)
    Write-Log "Directorio anterior conservado en: $apartado" 'AVISO' $log

    Copy-Item -Path $salidaDir -Destination $dataDir -Recurse
    Write-Log "Datos restaurados copiados a $dataDir" 'OK' $log

    Write-Log "Arrancando el servicio..." 'INFO' $log
    Start-Service -Name $PgService
    (Get-Service $PgService).WaitForStatus('Running', '00:02:00')

    $tListo = Resumen-RTO $inicio 'Servicio de produccion arriba'
    Write-Log ("RTO REAL: {0} min" -f [math]::Round($tListo/60,2)) 'OK' $log
    Write-Log "Comprobar la aplicacion antes de dar por buena la restauracion." 'AVISO' $log
    Write-Log "Si todo esta bien, eliminar $apartado para recuperar espacio." 'INFO' $log
    Write-Estado -Tipo 'restauracion' -Resultado 'OK' -Ruta $dataDir -DuracionSeg $tListo
    exit 0
}
catch {
    Write-Log "EXCEPCION: $($_.Exception.Message)" 'ERROR' $log
    Write-Estado -Tipo 'restauracion' -Resultado 'ERROR' -Detalle $_.Exception.Message
    exit 1
}
finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}
