# =============================================================================
# CONFIGURACION COMUN DE LOS RESPALDOS — Marathon Sports
# -----------------------------------------------------------------------------
# Lo cargan todos los demas scripts con:  . "$PSScriptRoot\config.ps1"
#
# La contrasena NO se guarda aqui: se lee del .env del proyecto, que ya esta en
# .gitignore. Asi este archivo puede versionarse sin exponer credenciales.
# =============================================================================

$ErrorActionPreference = 'Stop'

# --- Instalacion de PostgreSQL ------------------------------------------------
$Global:PgBin      = 'C:\Program Files\PostgreSQL\18\bin'
$Global:PgService  = 'postgresql-x64-18'
$Global:PgHost     = 'localhost'
$Global:PgPort     = 5432
$Global:PgDatabase = 'mod_venta_inve'

# --- Credencial de los respaldos ---------------------------------------------
# ATENCION: los respaldos NO usan el usuario de la aplicacion.
#
# Desde que la aplicacion se conecta como usr_admin_marathon (ver la seccion
# "Puesta en produccion" de SEGURIDAD_ROLES.md), DB_USER/DB_PASSWORD del .env
# son la credencial de la APLICACION, y esa cuenta deliberadamente no tiene
# privilegios de superusuario.
#
# pg_basebackup exige el atributo REPLICATION, que usr_admin_marathon no tiene
# ni debe tener: darselo a la cuenta que atiende el trafico web permitiria a
# quien la comprometa clonar la base entera. Por eso los respaldos leen sus
# propias claves del .env, separadas de las de la aplicacion.
$Global:PgUser = 'postgres'   # se sobrescribe con PG_SUPERUSER si esta en .env

# --- Almacenamiento de respaldos ---------------------------------------------
# DELIBERADAMENTE FUERA de la carpeta del proyecto: esta dentro de OneDrive y
# sincronizar decenas de GB de respaldos a la nube en cada corrida seria un
# problema, no una ventaja. Un respaldo en el mismo disco que el dato tampoco
# protege de una falla de disco: ver la seccion "Regla 3-2-1" del documento
# ESTRATEGIA_RESPALDO.md, donde queda anotado como pendiente el segundo destino.
$Global:BackupRoot = 'C:\respaldos\marathon'
$Global:FullRoot   = Join-Path $BackupRoot 'full'
$Global:DiffRoot   = Join-Path $BackupRoot 'diferencial'
$Global:AppRoot    = Join-Path $BackupRoot 'aplicacion'
$Global:LogRoot    = Join-Path $BackupRoot 'logs'
$Global:RestoreRoot= Join-Path $BackupRoot 'restauracion'

# --- Destino SECUNDARIO: disco externo USB (regla 3-2-1, F42) ----------------
# El esquema hasta la F41 tenia las tres copias en el MISMO disco que la base:
# protegia de un borrado accidental y de una corrupcion logica, no de la perdida
# del equipo. Este segundo destino es lo que cierra el requisito 4a.
#
# COMO SE LOCALIZA EL DESTINO, en este orden:
#   1. $SecundarioRuta   ruta explicita (una carpeta, un recurso de red, o una
#                        unidad ya montada). Gana sobre todo lo demas.
#   2. $SecundarioLetra  letra de unidad fija, p.ej. 'E:'
#   3. $SecundarioEtiqueta  etiqueta del volumen. Es la forma RECOMENDADA para
#                        un USB: Windows no garantiza que el mismo pendrive
#                        reciba siempre la misma letra, asi que buscarlo por
#                        etiqueta es lo unico estable.
#
# CIFRA EL USB CON BITLOCKER TO GO. Es un medio extraible que puede salir del
# edificio en un bolsillo. Desde la F41 el respaldo lleva correos, telefonos y
# direcciones ya cifrados, pero 'nombre' y 'apellido' siguen EN CLARO, igual que
# todo el resto del negocio. Ver ESTRATEGIA_RESPALDO.md.
$Global:SecundarioHabilitado = $true
$Global:SecundarioEtiqueta   = 'MARATHON_BK'
$Global:SecundarioLetra      = ''
$Global:SecundarioRuta       = ''
$Global:SecundarioSubcarpeta = 'marathon'

# Retencion propia del secundario. Se declara aparte del primario porque un USB
# suele ser mas pequeno que el disco interno y conviene poder recortarla sin
# tocar la politica principal.
$Global:SecundarioSemanas    = 4
$Global:SecundarioMinLibreGB = 2

# Sobrescritura por variable de entorno. Sirve para dos cosas reales: apuntar a
# un recurso de red sin editar este archivo, y hacer un simulacro de
# restauracion contra una carpeta cualquiera sin desconfigurar el USB de verdad.
if ($env:MARATHON_BK_SECUNDARIO) { $Global:SecundarioRuta = $env:MARATHON_BK_SECUNDARIO }

# --- Retencion ---------------------------------------------------------------
# Semanas de respaldos completos que se conservan. Cada FULL se guarda con sus
# diferenciales; al eliminar un FULL se eliminan tambien sus diferenciales,
# porque un diferencial sin su FULL no sirve para nada.
#
# Bajado de 4 a 2 el 16/08/2026. Al crecer la base a 250 MB tras la F43, cada
# full pasa de 303 a 326 MB: cuatro fulls son ~1,3 GB y el disco quedo en
# 9,28 GB libres, con el umbral de aborto en 5 GB. Con 2 se recuperan ~650 MB.
#
# OJO: mientras el USB MARATHON_BK no este conectado, estas copias son las
# UNICAS que hay, y ahora son dos en vez de cuatro. La retencion se aplica al
# ejecutar el siguiente FULL, no al guardar este archivo.
$Global:SemanasRetencion = 2

# --- Umbral de espacio libre -------------------------------------------------
# El disco C: tenia 11.7 GB libres al disenar esto. Si el margen baja de este
# valor el respaldo se aborta ANTES de empezar, en lugar de llenar el disco y
# tumbar el servidor de base de datos.
$Global:MinEspacioLibreGB = 5

# --- Ruta del proyecto (para el respaldo de la capa de aplicacion) -----------
$Global:ProyectoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

# =============================================================================
# FUNCIONES DE APOYO
# =============================================================================

function Initialize-Backup {
    <#  Prepara el entorno: carpetas, credencial y PATH. #>
    foreach ($d in @($BackupRoot, $FullRoot, $DiffRoot, $AppRoot, $LogRoot, $RestoreRoot)) {
        if (-not (Test-Path $d)) { New-Item -ItemType Directory -Path $d -Force | Out-Null }
    }

    $envFile = Join-Path $ProyectoRoot '.env'
    if (-not (Test-Path $envFile)) {
        throw "No se encontro el archivo .env en $ProyectoRoot. Sin el no hay credencial de base de datos."
    }

    $claves = @{}
    foreach ($l in Get-Content $envFile) {
        if ($l -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            $claves[$matches[1]] = $matches[2].Trim()
        }
    }

    if ($claves.ContainsKey('PG_SUPERUSER')) { $Global:PgUser = $claves['PG_SUPERUSER'] }

    if (-not $claves.ContainsKey('PG_SUPERUSER_PASSWORD') -or
        [string]::IsNullOrWhiteSpace($claves['PG_SUPERUSER_PASSWORD'])) {
        throw ("El .env no contiene PG_SUPERUSER_PASSWORD. Los respaldos necesitan una " +
               "cuenta con REPLICATION (postgres), que NO es la de la aplicacion. " +
               "Agregar PG_SUPERUSER=postgres y PG_SUPERUSER_PASSWORD=<clave> al .env.")
    }

    # La credencial se pasa por variable de entorno del proceso: no aparece en la
    # linea de comandos, asi que no queda visible en la lista de procesos.
    $env:PGPASSWORD = $claves['PG_SUPERUSER_PASSWORD']
}

function Write-Log {
    param(
        [Parameter(Mandatory)][string] $Mensaje,
        [ValidateSet('INFO','AVISO','ERROR','OK')][string] $Nivel = 'INFO',
        [string] $Archivo
    )
    $linea = "{0} [{1,-5}] {2}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Nivel, $Mensaje
    Write-Host $linea
    if ($Archivo) { Add-Content -Path $Archivo -Value $linea -Encoding utf8 }
}

function Test-EspacioLibre {
    param([string] $Archivo)
    $libre = [math]::Round((Get-PSDrive ((Get-Item $BackupRoot).PSDrive.Name)).Free / 1GB, 2)
    if ($libre -lt $MinEspacioLibreGB) {
        Write-Log "Espacio libre insuficiente: $libre GB (minimo $MinEspacioLibreGB GB). Respaldo ABORTADO." 'ERROR' $Archivo
        return $false
    }
    Write-Log "Espacio libre disponible: $libre GB" 'INFO' $Archivo
    return $true
}

function Get-UltimoFull {
    <#  Devuelve el directorio del FULL mas reciente que tenga manifiesto valido. #>
    Get-ChildItem $FullRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'backup_manifest') } |
        Sort-Object Name -Descending |
        Select-Object -First 1
}

function Write-Estado {
    <#  Deja un resumen legible por maquina del resultado de la ultima corrida.
        Lo consume verificar_respaldos.ps1 para detectar jobs que no corrieron. #>
    param(
        [Parameter(Mandatory)][string] $Tipo,       # full | diferencial | aplicacion
        [Parameter(Mandatory)][string] $Resultado,  # OK | ERROR
        [string] $Detalle = '',
        [string] $Ruta = '',
        [int]    $DuracionSeg = 0,
        [double] $TamanoMB = 0
    )
    $estado = [ordered]@{
        tipo         = $Tipo
        resultado    = $Resultado
        fecha        = (Get-Date -Format 'o')
        detalle      = $Detalle
        ruta         = $Ruta
        duracion_seg = $DuracionSeg
        tamano_mb    = $TamanoMB
        equipo       = $env:COMPUTERNAME
    }
    $destino = Join-Path $LogRoot "estado_$Tipo.json"
    $estado | ConvertTo-Json | Set-Content -Path $destino -Encoding utf8
}

function Get-TamanoMB {
    param([string] $Ruta)
    if (-not (Test-Path $Ruta)) { return 0 }
    $b = (Get-ChildItem $Ruta -Recurse -File -ErrorAction SilentlyContinue |
          Measure-Object -Property Length -Sum).Sum
    return [math]::Round($b / 1MB, 2)
}

function Get-DestinoSecundario {
    <#
      Devuelve la ruta raiz del destino secundario, o $null si no esta
      disponible. NUNCA lanza excepcion: que el USB este desconectado es una
      situacion normal, no un error.
    #>
    param([string] $Archivo)

    if (-not $SecundarioHabilitado) { return $null }

    $raiz = $null

    if ($SecundarioRuta) {
        if (Test-Path $SecundarioRuta) { $raiz = $SecundarioRuta }
        else { Write-Log "Destino secundario: la ruta configurada '$SecundarioRuta' no existe." 'AVISO' $Archivo; return $null }
    }
    elseif ($SecundarioLetra) {
        $l = $SecundarioLetra.TrimEnd(':') + ':\'
        if (Test-Path $l) { $raiz = $l }
        else { Write-Log "Destino secundario: la unidad $SecundarioLetra no esta montada." 'AVISO' $Archivo; return $null }
    }
    elseif ($SecundarioEtiqueta) {
        $vol = Get-CimInstance Win32_LogicalDisk -ErrorAction SilentlyContinue |
               Where-Object { $_.VolumeName -eq $SecundarioEtiqueta } |
               Select-Object -First 1
        if ($vol) { $raiz = $vol.DeviceID + '\' }
        else { Write-Log "Destino secundario: no hay ningun volumen con etiqueta '$SecundarioEtiqueta' conectado." 'AVISO' $Archivo; return $null }
    }
    else {
        Write-Log "Destino secundario habilitado pero sin configurar (ni ruta, ni letra, ni etiqueta)." 'AVISO' $Archivo
        return $null
    }

    $destino = Join-Path $raiz $SecundarioSubcarpeta
    try {
        if (-not (Test-Path $destino)) { New-Item -ItemType Directory -Path $destino -Force | Out-Null }
    } catch {
        Write-Log "Destino secundario: no se pudo crear '$destino'. $($_.Exception.Message)" 'AVISO' $Archivo
        return $null
    }

    # Espacio libre del volumen del secundario. Se comprueba ANTES de copiar
    # para no dejar una copia a medias que parezca valida.
    try {
        $unidad = (Get-Item $destino).PSDrive.Name
        $libre  = [math]::Round((Get-PSDrive $unidad).Free / 1GB, 2)
        if ($libre -lt $SecundarioMinLibreGB) {
            Write-Log "Destino secundario: solo quedan $libre GB (minimo $SecundarioMinLibreGB GB). NO se copiara." 'AVISO' $Archivo
            return $null
        }
        Write-Log "Destino secundario disponible: $destino ($libre GB libres)" 'INFO' $Archivo
    } catch {
        Write-Log "Destino secundario: no se pudo medir el espacio libre de '$destino'." 'AVISO' $Archivo
        return $null
    }

    return $destino
}

function Copy-ARespaldoSecundario {
    <#
      Replica un respaldo ya completado y verificado en el destino secundario.

      DEVUELVE $true si copio, $false si no habia destino o fallo la copia.
      El llamador decide que hacer con eso; lo que NO debe hacer nunca es fallar
      el respaldo entero porque un USB este desenchufado. El respaldo local ya
      esta hecho y verificado en ese punto: perderlo por no poder replicarlo
      seria cambiar un problema pequeno por uno grande.
    #>
    param(
        [Parameter(Mandatory)][string] $Origen,   # carpeta del respaldo recien hecho
        [Parameter(Mandatory)][string] $Tipo,     # full | diferencial | aplicacion
        [string] $Archivo
    )

    $destinoRaiz = Get-DestinoSecundario -Archivo $Archivo
    if (-not $destinoRaiz) {
        Write-Log "COPIA SECUNDARIA OMITIDA ($Tipo). El respaldo primario esta completo y verificado; solo falta la replica fuera del equipo." 'AVISO' $Archivo
        return $false
    }

    $carpetaTipo = Join-Path $destinoRaiz $Tipo
    if (-not (Test-Path $carpetaTipo)) { New-Item -ItemType Directory -Path $carpetaTipo -Force | Out-Null }

    $nombre  = Split-Path $Origen -Leaf
    $destino = Join-Path $carpetaTipo $nombre

    try {
        $t0 = Get-Date
        if (Test-Path $Origen -PathType Container) {
            # Se copia a una carpeta temporal y se renombra al final. Si la copia
            # se interrumpe (el USB se retira a media escritura, que es
            # exactamente lo que pasa con los medios extraibles), lo que queda
            # es una carpeta .parcial que nadie confundira con un respaldo bueno.
            $temporal = "$destino.parcial"
            if (Test-Path $temporal) { Remove-Item $temporal -Recurse -Force -ErrorAction SilentlyContinue }
            Copy-Item -Path $Origen -Destination $temporal -Recurse -Force -ErrorAction Stop
            if (Test-Path $destino) { Remove-Item $destino -Recurse -Force -ErrorAction SilentlyContinue }
            Rename-Item -Path $temporal -NewName $nombre -ErrorAction Stop
        } else {
            Copy-Item -Path $Origen -Destination $destino -Force -ErrorAction Stop
        }
        $seg = [int]((Get-Date) - $t0).TotalSeconds
        $mb  = Get-TamanoMB $destino
        Write-Log "COPIA SECUNDARIA OK ($Tipo): $destino - $mb MB en $seg s" 'OK' $Archivo
    }
    catch {
        Write-Log "COPIA SECUNDARIA FALLO ($Tipo): $($_.Exception.Message)" 'AVISO' $Archivo
        Write-Log "El respaldo primario NO se ve afectado." 'AVISO' $Archivo
        return $false
    }

    # --- retencion propia del secundario ---
    try {
        $copias = Get-ChildItem $carpetaTipo -Directory -ErrorAction SilentlyContinue |
                  Where-Object { $_.Name -notlike '*.parcial' } |
                  Sort-Object Name -Descending
        if ($copias.Count -gt $SecundarioSemanas) {
            foreach ($vieja in $copias | Select-Object -Skip $SecundarioSemanas) {
                Remove-Item $vieja.FullName -Recurse -Force -ErrorAction SilentlyContinue
                Write-Log "Retencion secundaria: eliminada $($vieja.Name)" 'INFO' $Archivo
            }
        }
    } catch {
        Write-Log "Retencion secundaria: no se pudo aplicar. $($_.Exception.Message)" 'AVISO' $Archivo
    }

    # Marca legible por maquina, para que verificar_respaldos.ps1 sepa cuando fue
    # la ultima replica sin tener que recorrer el USB (que puede no estar).
    $estado = [ordered]@{
        tipo      = $Tipo
        resultado = 'OK'
        fecha     = (Get-Date -Format 'o')
        destino   = $destino
        equipo    = $env:COMPUTERNAME
    }
    $estado | ConvertTo-Json | Set-Content -Path (Join-Path $LogRoot "estado_secundario_$Tipo.json") -Encoding utf8

    return $true
}

function Invoke-PgTool {
    <#
      Ejecuta una herramienta de linea de comandos de PostgreSQL y devuelve
      un objeto con ExitCode y Salida.

      POR QUE HACE FALTA ESTE ENVOLTORIO: pg_basebackup y pg_verifybackup
      escriben su progreso y sus avisos en stderr, no en stdout. Con
      $ErrorActionPreference = 'Stop', PowerShell convierte CUALQUIER escritura
      a stderr de un ejecutable nativo en una excepcion terminante. El efecto
      es que un respaldo perfectamente correcto aborta con el mensaje
      "waiting for checkpoint", que no es un error sino una linea de progreso.
      Aqui se baja la preferencia a Continue solo durante la llamada, se junta
      stderr con stdout y se decide el exito por el CODIGO DE SALIDA, que es el
      unico indicador fiable.
    #>
    param(
        [Parameter(Mandatory)][string]   $Exe,
        [Parameter(Mandatory)][string[]] $Argumentos
    )
    $previo = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $salida = & (Join-Path $PgBin $Exe) @Argumentos 2>&1 | ForEach-Object { "$_" }
        $codigo = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previo
    }
    return [pscustomobject]@{ ExitCode = $codigo; Salida = $salida }
}

function Invoke-PgCtl {
    <#
      Ejecuta pg_ctl usando Start-Process con redireccion a ARCHIVOS.

      POR QUE NO SE PUEDE USAR Invoke-PgTool AQUI: 'pg_ctl start' lanza el
      proceso servidor, que HEREDA el descriptor de salida de su padre y lo
      mantiene abierto mientras siga vivo. Si se captura esa salida por la
      tuberia de PowerShell, la tuberia espera a que el descriptor se cierre,
      es decir, a que el servidor se apague. Resultado: el script se cuelga
      indefinidamente aunque el arranque haya sido correcto.
      Redirigiendo a archivos, Start-Process -Wait espera solo a que termine
      pg_ctl (el lanzador), no el servidor.
    #>
    param(
        [Parameter(Mandatory)][string[]] $Argumentos,
        [int] $TimeoutSeg = 120
    )
    $out = Join-Path $env:TEMP ("pgctl_out_" + [guid]::NewGuid().ToString('N') + ".txt")
    $err = Join-Path $env:TEMP ("pgctl_err_" + [guid]::NewGuid().ToString('N') + ".txt")
    try {
        $p = Start-Process -FilePath (Join-Path $PgBin 'pg_ctl.exe') `
                           -ArgumentList $Argumentos `
                           -NoNewWindow -PassThru `
                           -RedirectStandardOutput $out `
                           -RedirectStandardError  $err
        if (-not $p.WaitForExit($TimeoutSeg * 1000)) {
            try { $p.Kill() } catch { }
            return [pscustomobject]@{ ExitCode = 124; Salida = @("pg_ctl excedio el tiempo limite de $TimeoutSeg s") }
        }
        # La sobrecarga WaitForExit(int) puede devolver true sin que el objeto
        # Process haya terminado de poblar ExitCode, que entonces queda en $null
        # y hace fallar cualquier comparacion contra 0. La llamada sin argumentos
        # fuerza el procesamiento completo de la salida del proceso.
        $p.WaitForExit()
        $codigo = $p.ExitCode
        if ($null -eq $codigo) { $codigo = 0 }

        $salida = @()
        foreach ($f in @($out, $err)) {
            if (Test-Path $f) { $salida += (Get-Content $f -ErrorAction SilentlyContinue | Where-Object { $_ }) }
        }
        return [pscustomobject]@{ ExitCode = $codigo; Salida = $salida }
    }
    finally {
        Remove-Item $out, $err -Force -ErrorAction SilentlyContinue
    }
}
