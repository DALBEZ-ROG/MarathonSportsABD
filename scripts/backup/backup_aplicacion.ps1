# =============================================================================
# RESPALDO DE LA CAPA DE APLICACION
# -----------------------------------------------------------------------------
# La base de datos no es lo unico que hay que poder recuperar. Si se pierde el
# servidor, con solo el respaldo de la base queda una base sin sistema que la
# consulte. Esta es la mitad que suele olvidarse.
#
# QUE SE RESPALDA Y POR QUE, SEGUN SU NATURALEZA:
#
#   1. CODIGO FUENTE  -> ya esta en Git. Lo que este script hace NO es copiar el
#      codigo, sino registrar el punto exacto de recuperacion: commit, rama y
#      etiqueta. Copiar el codigo a un ZIP diario seria redundante y ademas peor,
#      porque perderia el historial. Lo que falta en Git es la trazabilidad de
#      QUE version estaba en produccion, y eso es lo que se anota aqui.
#
#   2. CONFIGURACION Y SECRETOS -> .env, application-local.properties y los
#      certificados NO estan en Git (estan en .gitignore, y con razon). Si se
#      pierde el disco, se pierden. Estos SI hay que copiarlos, y cifrados,
#      porque contienen la contrasena de la base y el secreto de firma JWT.
#
#   3. ARCHIVOS SUBIDOS POR USUARIOS -> no viven en Git ni en la base. Se
#      respaldan con la misma logica full/diferencial usando Robocopy en modo
#      espejo con historial.
#
#   4. CONFIGURACION DEL PROPIO POSTGRES -> postgresql.conf, pg_hba.conf y
#      postgresql.auto.conf. pg_basebackup ya los incluye, pero se copian
#      aparte para poder consultarlos sin desempacar un respaldo entero.
#
# Uso:  powershell -ExecutionPolicy Bypass -File backup_aplicacion.ps1
# =============================================================================

. "$PSScriptRoot\config.ps1"

$inicio = Get-Date
$sello  = $inicio.ToString('yyyyMMdd_HHmmss')
$log    = Join-Path $LogRoot "aplicacion_$sello.log"

try {
    Initialize-Backup
    $destino = Join-Path $AppRoot "app_$sello"
    New-Item -ItemType Directory -Path $destino -Force | Out-Null

    Write-Log "=============================================" 'INFO' $log
    Write-Log "RESPALDO DE LA CAPA DE APLICACION" 'INFO' $log
    Write-Log "Destino: $destino" 'INFO' $log

    if (-not (Test-EspacioLibre $log)) {
        Write-Estado -Tipo 'aplicacion' -Resultado 'ERROR' -Detalle 'Espacio insuficiente'
        exit 2
    }

    # ========================================================================
    # 1. PUNTO DE RECUPERACION DEL CODIGO (no el codigo: su identificador)
    # ========================================================================
    Write-Log "--- Punto de recuperacion en Git ---" 'INFO' $log
    $git = @{}
    Push-Location $ProyectoRoot
    try {
        $prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
        $git['commit']    = (& git rev-parse HEAD 2>&1 | Out-String).Trim()
        $git['rama']      = (& git rev-parse --abbrev-ref HEAD 2>&1 | Out-String).Trim()
        $git['etiqueta']  = (& git describe --tags --always 2>&1 | Out-String).Trim()
        $git['remoto']    = (& git config --get remote.origin.url 2>&1 | Out-String).Trim()
        $git['sucio']     = (& git status --porcelain 2>&1 | Out-String).Trim()
        $ErrorActionPreference = $prev
    } finally { Pop-Location }

    if ($git['sucio']) {
        # Un respaldo cuyo codigo no esta commiteado no es reproducible: al
        # restaurar, ese trabajo no estara en ninguna parte.
        Write-Log "AVISO: hay cambios sin confirmar en el repositorio. Esos cambios NO son recuperables desde Git." 'AVISO' $log
        $git['sucio'] -split "`n" | Select-Object -First 10 | ForEach-Object { Write-Log "  $_" 'AVISO' $log }
    }

    $git['fecha_respaldo'] = (Get-Date -Format 'o')
    $git | ConvertTo-Json | Set-Content (Join-Path $destino 'punto_recuperacion_git.json') -Encoding utf8
    Write-Log "Commit: $($git['commit'])  Rama: $($git['rama'])  Etiqueta: $($git['etiqueta'])" 'OK' $log

    # ========================================================================
    # 2. CONFIGURACION Y SECRETOS  (cifrados)
    # ========================================================================
    Write-Log "--- Configuracion y secretos ---" 'INFO' $log
    $tmpCfg = Join-Path $env:TEMP "cfg_$sello"
    New-Item -ItemType Directory -Path $tmpCfg -Force | Out-Null

    $sensibles = @(
        '.env',
        'marathon-backend\src\main\resources\application.properties',
        'marathon-backend\src\main\resources\application-local.properties',
        'marathon-backend\src\main\resources\application-dev.properties',
        'docker-compose.yml',
        'marathon-frontend\src\environments\environment.ts',
        'marathon-frontend\src\environments\environment.prod.ts'
    )
    $copiados = 0
    foreach ($rel in $sensibles) {
        $src = Join-Path $ProyectoRoot $rel
        if (Test-Path $src) {
            $dst = Join-Path $tmpCfg ($rel -replace '[\\/]', '__')
            Copy-Item $src $dst -Force
            $copiados++
            Write-Log "  incluido: $rel" 'INFO' $log
        }
    }

    # Configuracion del servidor PostgreSQL
    $pgData = 'C:\Program Files\PostgreSQL\18\data'
    foreach ($f in @('postgresql.conf','pg_hba.conf','pg_ident.conf','postgresql.auto.conf')) {
        $src = Join-Path $pgData $f
        if (Test-Path $src) {
            Copy-Item $src (Join-Path $tmpCfg "pgconf__$f") -Force
            $copiados++
            Write-Log "  incluido: $f (servidor)" 'INFO' $log
        }
    }
    Write-Log "$copiados archivos de configuracion recogidos" 'INFO' $log

    # ------------------------------------------------------------------ cifrado
    # Estos archivos contienen la contrasena de la base y el secreto JWT. Un ZIP
    # sin cifrar en una carpeta de respaldos es una filtracion esperando ocurrir.
    # Se usa DPAPI a nivel de MAQUINA: solo se puede descifrar en este equipo, lo
    # que es adecuado para un respaldo local y no exige custodiar otra clave.
    # LIMITACION IMPORTANTE, anotada a proposito: si se pierde el equipo, este
    # archivo es indescifrable. Para el respaldo externo hay que usar una clave
    # gestionada aparte (ver ESTRATEGIA_RESPALDO.md, seccion Regla 3-2-1).
    $zipPlano = Join-Path $env:TEMP "cfg_$sello.zip"
    Compress-Archive -Path (Join-Path $tmpCfg '*') -DestinationPath $zipPlano -Force

    Add-Type -AssemblyName System.Security
    $bytes  = [System.IO.File]::ReadAllBytes($zipPlano)
    $cifrado= [System.Security.Cryptography.ProtectedData]::Protect(
                  $bytes, $null, [System.Security.Cryptography.DataProtectionScope]::LocalMachine)
    [System.IO.File]::WriteAllBytes((Join-Path $destino 'configuracion_secretos.zip.dpapi'), $cifrado)

    # El material sin cifrar se destruye
    Remove-Item $zipPlano -Force
    Remove-Item $tmpCfg -Recurse -Force
    Write-Log "Configuracion cifrada con DPAPI (ambito: maquina local)" 'OK' $log

    # Instrucciones de descifrado junto al archivo, porque un respaldo que nadie
    # sabe abrir no sirve de nada en una emergencia.
    @"
COMO DESCIFRAR configuracion_secretos.zip.dpapi
------------------------------------------------
Solo funciona EN ESTE MISMO EQUIPO ($env:COMPUTERNAME). DPAPI en ambito
LocalMachine ata el cifrado al equipo; en otra maquina no se puede abrir.

    Add-Type -AssemblyName System.Security
    `$c = [System.IO.File]::ReadAllBytes('configuracion_secretos.zip.dpapi')
    `$p = [System.Security.Cryptography.ProtectedData]::Unprotect(
              `$c, `$null, [System.Security.Cryptography.DataProtectionScope]::LocalMachine)
    [System.IO.File]::WriteAllBytes('configuracion_secretos.zip', `$p)
    Expand-Archive configuracion_secretos.zip -DestinationPath .\config

Tras extraer, los archivos con prefijo pgconf__ van al directorio de datos de
PostgreSQL; el resto vuelve a la ruta que indica su nombre, donde los dobles
guiones bajos representan separadores de carpeta.
"@ | Set-Content (Join-Path $destino 'COMO_DESCIFRAR.txt') -Encoding utf8

    # ========================================================================
    # 3. ARCHIVOS SUBIDOS POR USUARIOS
    # ========================================================================
    Write-Log "--- Archivos subidos por usuarios ---" 'INFO' $log
    $carpetasSubidas = @('uploads', 'archivos', 'marathon-backend\uploads', 'marathon-backend\files')
    $encontrada = $false
    foreach ($c in $carpetasSubidas) {
        $src = Join-Path $ProyectoRoot $c
        if (Test-Path $src) {
            $encontrada = $true
            $dst = Join-Path $destino ('subidas__' + ($c -replace '[\\/]', '__'))
            # /MIR espeja, /R:2 reintenta 2 veces, /NP sin porcentaje por linea
            $prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
            & robocopy $src $dst /MIR /R:2 /W:2 /NP /NFL /NDL 2>&1 | Out-Null
            $rc = $LASTEXITCODE
            $ErrorActionPreference = $prev
            # Robocopy: 0-7 son exitos (0 sin cambios, 1 copiado, etc.); >=8 es error
            if ($rc -ge 8) { Write-Log "Robocopy devolvio $rc al copiar $c" 'ERROR' $log }
            else { Write-Log "  espejado: $c (robocopy $rc)" 'OK' $log }
        }
    }
    if (-not $encontrada) {
        Write-Log "No se hallaron carpetas de archivos subidos. El sistema genera los PDF al vuelo y no los persiste, asi que hoy no hay nada que respaldar por esta via." 'INFO' $log
        Write-Log 'Si mas adelante se agregan imagenes de producto o adjuntos, anadir su carpeta a la lista $carpetasSubidas de este script.' 'AVISO' $log
    }

    # ========================================================================
    # 4. RETENCION
    # ========================================================================
    $apps = Get-ChildItem $AppRoot -Directory | Sort-Object Name -Descending
    if ($apps.Count -gt $SemanasRetencion) {
        foreach ($v in $apps | Select-Object -Skip $SemanasRetencion) {
            Remove-Item $v.FullName -Recurse -Force
            Write-Log "Retencion: eliminado $($v.Name)" 'INFO' $log
        }
    }

    $dur    = [int]((Get-Date) - $inicio).TotalSeconds
    $tamano = Get-TamanoMB $destino
    Write-Log "Respaldo de aplicacion completado en $dur s. Tamano: $tamano MB" 'OK' $log
    Write-Estado -Tipo 'aplicacion' -Resultado 'OK' -Ruta $destino -DuracionSeg $dur -TamanoMB $tamano `
                 -Detalle "commit=$($git['commit'])"
    exit 0
}
catch {
    Write-Log "EXCEPCION: $($_.Exception.Message)" 'ERROR' $log
    Write-Estado -Tipo 'aplicacion' -Resultado 'ERROR' -Detalle $_.Exception.Message
    exit 1
}
finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}
