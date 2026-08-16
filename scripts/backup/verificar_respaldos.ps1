# =============================================================================
# VERIFICACION DE RESPALDOS — deteccion de jobs que NO corrieron
# -----------------------------------------------------------------------------
# El fallo mas peligroso de un sistema de respaldos no es que un respaldo salga
# con error: eso se ve en el log. Es que el job DEJE DE EJECUTARSE y nadie lo
# note, porque entonces no hay ningun error que mirar. Se descubre el dia que se
# necesita restaurar.
#
# Este script se ejecuta a diario y avisa por ausencia: revisa la antiguedad del
# ultimo respaldo de cada tipo contra lo que deberia ser y reporta lo que falta.
#
# Codigos de salida:  0 todo bien | 1 hay avisos | 2 hay fallos criticos
#
# Uso:  powershell -ExecutionPolicy Bypass -File verificar_respaldos.ps1
# =============================================================================

. "$PSScriptRoot\config.ps1"

$sello  = (Get-Date).ToString('yyyyMMdd_HHmmss')
$log    = Join-Path $LogRoot "verificacion_$sello.log"
$avisos = 0
$fallos = 0

function Revisar {
    param(
        [string] $Tipo,
        [int]    $MaxHorasEsperadas,
        [string] $Descripcion
    )
    $archivo = Join-Path $LogRoot "estado_$Tipo.json"

    if (-not (Test-Path $archivo)) {
        Write-Log "CRITICO  [$Tipo] $Descripcion - nunca se ha ejecutado (no existe estado_$Tipo.json)" 'ERROR' $log
        return 2
    }

    $e = Get-Content $archivo -Raw | ConvertFrom-Json
    $horas = [math]::Round(((Get-Date) - [datetime]$e.fecha).TotalHours, 1)

    if ($e.resultado -ne 'OK') {
        Write-Log "CRITICO  [$Tipo] la ultima ejecucion FALLO hace $horas h: $($e.detalle)" 'ERROR' $log
        return 2
    }
    if ($horas -gt $MaxHorasEsperadas) {
        Write-Log "CRITICO  [$Tipo] $Descripcion - el ultimo correcto fue hace $horas h (maximo tolerado: $MaxHorasEsperadas h). EL JOB NO ESTA CORRIENDO." 'ERROR' $log
        return 2
    }
    Write-Log "OK       [$Tipo] hace $horas h, $($e.tamano_mb) MB, $($e.duracion_seg) s" 'OK' $log
    return 0
}

try {
    Initialize-Backup
    Write-Log "=============================================" 'INFO' $log
    Write-Log "VERIFICACION DEL ESTADO DE LOS RESPALDOS" 'INFO' $log

    # FULL semanal (domingo 23:00): se tolera hasta 8 dias por si el domingo
    # el equipo estuvo apagado y la tarea se recupero al dia siguiente.
    $fallos += [int](Revisar 'full'        (8 * 24) 'Respaldo completo semanal') / 2
    # DIFERENCIAL diario (lunes a sabado 22:00). El domingo no corre, asi que
    # el margen es de 48 h para no dar una falsa alarma cada domingo.
    $fallos += [int](Revisar 'diferencial' 48       'Respaldo diferencial diario') / 2
    $fallos += [int](Revisar 'aplicacion'  (8 * 24) 'Respaldo de la capa de aplicacion') / 2

    # ------------------------------------------------- coherencia del conjunto --
    Write-Log "--- Coherencia del conjunto de respaldos ---" 'INFO' $log
    $full = Get-UltimoFull
    if ($full) {
        $difs = Get-ChildItem $DiffRoot -Directory -EA SilentlyContinue |
                Where-Object { $_.Name -like "*_base_$($full.Name)" }
        if ($difs.Count -eq 0) {
            Write-Log "AVISO: el FULL $($full.Name) no tiene ningun diferencial asociado. Ante un desastre se perderia todo lo posterior al completo." 'AVISO' $log
            $avisos++
        } elseif ($difs.Count -gt 1) {
            # No deberia pasar: el script de diferencial elimina los superados.
            Write-Log "AVISO: hay $($difs.Count) diferenciales para el mismo FULL. Se esperaba 1. Revisar la limpieza en backup_diferencial.ps1." 'AVISO' $log
            $avisos++
        } else {
            Write-Log "OK       el FULL vigente tiene exactamente 1 diferencial, como corresponde al esquema" 'OK' $log
        }

        # Los dos insumos de la restauracion deben existir en disco AHORA
        if (-not (Test-Path (Join-Path $full.FullName 'backup_manifest'))) {
            Write-Log "CRITICO: al FULL vigente le falta el backup_manifest. Sin el no se pueden generar mas diferenciales." 'ERROR' $log
            $fallos++
        }
    }

    # summarize_wal debe seguir activo: si alguien reinicia con otra config, los
    # diferenciales dejan de ser posibles y conviene saberlo antes del proximo job.
    $r = Invoke-PgTool 'psql.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$PgUser,'-d',$PgDatabase,
                                    '-t','-A','-c','SHOW summarize_wal;')
    $sw = ($r.Salida | Where-Object { $_ } | Select-Object -First 1)
    if ($r.ExitCode -ne 0) {
        Write-Log "CRITICO: no se pudo consultar el servidor." 'ERROR' $log; $fallos++
    } elseif ("$sw".Trim() -ne 'on') {
        Write-Log "CRITICO: summarize_wal = '$("$sw".Trim())'. Los diferenciales fallaran." 'ERROR' $log; $fallos++
    } else {
        Write-Log "OK       summarize_wal sigue activo" 'OK' $log
    }

    # ------------------------------------------------------------- simulacro ----
    $arRest = Join-Path $LogRoot 'estado_restauracion.json'
    if (Test-Path $arRest) {
        $er = Get-Content $arRest -Raw | ConvertFrom-Json
        $dias = [math]::Round(((Get-Date) - [datetime]$er.fecha).TotalDays, 1)
        if ($dias -gt 30) {
            Write-Log "AVISO: la ultima prueba de restauracion fue hace $dias dias. Un respaldo que no se ha restaurado nunca es una hipotesis, no un respaldo. Ejecutar restaurar.ps1 -Modo Prueba." 'AVISO' $log
            $avisos++
        } else {
            Write-Log "OK       simulacro de restauracion hace $dias dias ($($er.detalle))" 'OK' $log
        }
    } else {
        Write-Log "AVISO: nunca se ha probado una restauracion." 'AVISO' $log
        $avisos++
    }

    # ----------------------------------------------- destino secundario 3-2-1 --
    # Se comprueban LOS DOS destinos y se reporta la antiguedad de la copia mas
    # reciente en cada uno. Un secundario que dejo de actualizarse hace tres
    # semanas es peor que no tenerlo: da una sensacion de cobertura que no
    # existe.
    Write-Log "--- Destino secundario (regla 3-2-1) ---" 'INFO' $log
    if (-not $SecundarioHabilitado) {
        Write-Log "AVISO: el destino secundario esta DESHABILITADO. La regla 3-2-1 no se cumple: todas las copias estan en el mismo disco que la base." 'AVISO' $log
        $avisos++
    }
    else {
        $destSec = Get-DestinoSecundario -Archivo $log

        foreach ($t in @('full','diferencial')) {
            $marca = Join-Path $LogRoot "estado_secundario_$t.json"
            if (-not (Test-Path $marca)) {
                Write-Log "AVISO   [secundario/$t] nunca se ha replicado fuera del equipo." 'AVISO' $log
                $avisos++
                continue
            }
            $e = Get-Content $marca -Raw | ConvertFrom-Json
            $h = [math]::Round(((Get-Date) - [datetime]$e.fecha).TotalHours, 1)
            # Mismos margenes que el primario: 8 dias para el full, 48 h para el
            # diferencial, mas un margen porque el USB puede estar desconectado
            # algun dia y eso es una situacion prevista, no una averia.
            $max = if ($t -eq 'full') { 8 * 24 * 2 } else { 48 * 2 }
            if ($h -gt $max) {
                Write-Log "AVISO   [secundario/$t] la ultima replica fue hace $h h (maximo tolerado $max h). Conectar el disco externo." 'AVISO' $log
                $avisos++
            } else {
                Write-Log "OK      [secundario/$t] replicado hace $h h en $($e.destino)" 'OK' $log
            }
        }

        if ($destSec) {
            # El USB esta conectado AHORA: se puede comprobar que lo que hay en
            # el es realmente utilizable, no solo que el log diga que se copio.
            foreach ($t in @('full','diferencial')) {
                $carpeta = Join-Path $destSec $t
                if (Test-Path $carpeta) {
                    $ult = Get-ChildItem $carpeta -Directory -EA SilentlyContinue |
                           Where-Object { $_.Name -notlike '*.parcial' } |
                           Sort-Object Name -Descending | Select-Object -First 1
                    if ($ult) {
                        $edad = [math]::Round(((Get-Date) - $ult.CreationTime).TotalHours, 1)
                        Write-Log "OK      [secundario/$t] copia mas reciente en disco: $($ult.Name) (hace $edad h)" 'OK' $log
                    } else {
                        Write-Log "AVISO   [secundario/$t] la carpeta existe pero esta vacia." 'AVISO' $log; $avisos++
                    }
                }
                $parciales = Get-ChildItem (Join-Path $destSec $t) -Directory -Filter '*.parcial' -EA SilentlyContinue
                if ($parciales) {
                    Write-Log "AVISO   [secundario/$t] hay $($parciales.Count) copia(s) .parcial: una replica se interrumpio a medias. Se pueden borrar." 'AVISO' $log
                    $avisos++
                }
            }
        } else {
            Write-Log "AVISO: el destino secundario NO esta disponible ahora mismo. El respaldo local sigue funcionando; la regla 3-2-1 no." 'AVISO' $log
            $avisos++
        }
    }

    # ------------------------------------------------------------- espacio ------
    $libre = [math]::Round((Get-PSDrive ((Get-Item $BackupRoot).PSDrive.Name)).Free / 1GB, 2)
    $ocupa = Get-TamanoMB $BackupRoot
    Write-Log "Espacio: respaldos ocupan $ocupa MB; libre en disco $libre GB" 'INFO' $log
    if ($libre -lt ($MinEspacioLibreGB * 2)) {
        Write-Log "AVISO: quedan $libre GB. Con el umbral en $MinEspacioLibreGB GB, los proximos respaldos se abortaran pronto. Reducir SemanasRetencion o mover los respaldos a otro volumen." 'AVISO' $log
        $avisos++
    }

    Write-Log "-----------------------------------------------" 'INFO' $log
    if ($fallos -gt 0) {
        Write-Log "RESULTADO: $fallos fallo(s) critico(s) y $avisos aviso(s). REQUIERE ATENCION." 'ERROR' $log
        exit 2
    }
    if ($avisos -gt 0) {
        Write-Log "RESULTADO: sin fallos criticos, $avisos aviso(s)." 'AVISO' $log
        exit 1
    }
    Write-Log "RESULTADO: todos los respaldos al dia." 'OK' $log
    exit 0
}
catch {
    Write-Log "EXCEPCION: $($_.Exception.Message)" 'ERROR' $log
    exit 2
}
finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}
