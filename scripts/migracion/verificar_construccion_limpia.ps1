# =============================================================================
# VERIFICAR LA CONSTRUCCION DESDE CERO — Marathon Sports
# -----------------------------------------------------------------------------
# Levanta un cluster de PostgreSQL temporal, construye la base ENTERA con
# construir_desde_cero.ps1, ejecuta los cuatro arneses de prueba y destruye el
# cluster. Sin tocar nada del servidor real.
#
# PARA QUE SIRVE
#   Es la unica prueba que detecta la clase de defecto que documenta
#   DEUDA_TECNICA.md: dependencias que viven en la base de desarrollo y en
#   ningun archivo versionado. Todo el desarrollo ocurrio sobre una base que ya
#   existia, y eso oculto durante meses que faltaba el DDL de las 20 tablas base
#   y seis filas de unidad_medida. Ninguna prueba sobre la base real puede
#   encontrarlos, porque alli ya estan.
#
#   Conviene ejecutarlo al cerrar cada fase. Tarda unos 3 minutos.
#
# POR QUE UN CLUSTER APARTE Y NO UNA BASE DESECHABLE
#   fase34_seguridad_roles.sql hace DROP ROLE de los seis roles, y los roles son
#   objetos del CLUSTER, no de la base. Ademas lleva 'mod_venta_inve' escrito a
#   mano en un REVOKE ... ON DATABASE. Construir sobre 'mi_base_de_pruebas' del
#   mismo servidor tocaria los roles y los privilegios de la base real.
#
# EL PASO QUE PARECIA NO AUTOMATIZABLE
#   El paso 12 de SETUP_COMPLETO.md ("arrancar el backend") no es un script: los
#   roles de aplicacion los crea el DataInitializer de Spring. Aqui se resuelve
#   arrancando Maven en segundo plano, SONDEANDO la tabla 'rol' hasta que se
#   puebla, y matando el arbol de procesos. No se espera un tiempo fijo: se
#   espera al efecto observable, que es lo unico fiable.
#
# USO:
#   powershell -ExecutionPolicy Bypass -File verificar_construccion_limpia.ps1
#   powershell -ExecutionPolicy Bypass -File verificar_construccion_limpia.ps1 -Conservar
# =============================================================================

# NO PONER GUIONES LARGOS DENTRO DE CADENAS ENTRE COMILLAS EN ESTE ARCHIVO.
# Se guarda en UTF-8 sin BOM y PowerShell 5.1 lo lee como cp1252: los tres bytes
# del guion largo acaban en U+201D, comilla tipografica de cierre, que PowerShell
# ACEPTA como delimitador de cadena. La cadena se cierra a mitad y el archivo
# entero deja de analizarse. En comentarios es inofensivo.

param(
    [string] $PgBin    = 'C:\Program Files\PostgreSQL\18\bin',
    # Directorio del cluster temporal. Ruta CORTA y fuera de OneDrive: las
    # herramientas de PostgreSQL no pasan de 259 caracteres, y un cluster dentro
    # de una carpeta sincronizada es una mala idea por razones obvias.
    [string] $DirCluster = "$env:LOCALAPPDATA\marathon_verif",
    [int]    $Puerto   = 5434,
    [string] $JavaHome = '',
    [string] $Mvn      = '',
    # Deja el cluster en pie al terminar, para poder inspeccionarlo.
    [switch] $Conservar,
    # Salta el arranque del backend y, con el, la etapa 2. Sirve para comprobar
    # solo el esquema cuando no hay JDK/Maven a mano.
    [switch] $SoloEsquema
)

$ErrorActionPreference = 'Stop'
$Proyecto = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Clave    = 'verif_' + [guid]::NewGuid().ToString('N').Substring(0, 12)

$script:Fallos = @()
$script:Pasos  = @()

function Titulo($t) { Write-Host ""; Write-Host "==============================================================" -ForegroundColor Cyan
                      Write-Host " $t" -ForegroundColor Cyan
                      Write-Host "==============================================================" -ForegroundColor Cyan }
function Paso($t)   { Write-Host ""; Write-Host ">>> $t" }
function Bien($t)   { Write-Host "    OK    $t" -ForegroundColor Green;  $script:Pasos += @{ Ok = $true;  Texto = $t } }
function Mal($t)    { Write-Host "    FALLO $t" -ForegroundColor Red;    $script:Pasos += @{ Ok = $false; Texto = $t }
                      $script:Fallos += $t }

function Invoke-Pg {
    # El parametro NO puede llamarse $Args: es variable automatica de PowerShell,
    # no se enlaza, y los argumentos se pierden en silencio.
    param([string] $Exe, [string[]] $Argumentos)
    $previo = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $salida = & (Join-Path $PgBin $Exe) @Argumentos 2>&1
        $codigo = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previo }
    return [pscustomobject]@{ ExitCode = $codigo; Salida = $salida }
}

function Invoke-Externo {
    <#  Ejecuta un proceso externo capturando TODA su salida, incluida la de
        stderr, sin que eso aborte el script.

        Es imprescindible bajar ErrorActionPreference a 'Continue' aqui dentro.
        Con 'Stop' (lo que usa este archivo), PowerShell convierte la PRIMERA
        linea que el proceso escriba en stderr en un error terminante, y psql
        manda por stderr TODOS los NOTICE, que son mensajes informativos.
        Resultado: la construccion iba perfecta y el verificador la daba por
        fallida citando como "excepcion" un
            NOTICE: cliente: 1000 filas cifradas
        que era exactamente la senal de que todo iba bien. Se decide por el
        codigo de salida, que es lo unico fiable.  #>
    param([string] $Exe, [string[]] $Argumentos)
    $previo = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $salida = & $Exe @Argumentos 2>&1
        $codigo = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previo }
    return [pscustomobject]@{ ExitCode = $codigo; Salida = $salida }
}

function Consulta($sql) {
    $r = Invoke-Pg 'psql.exe' @('-h','localhost','-p',"$Puerto",'-U','postgres','-d','mod_venta_inve','-t','-A','-c',$sql)
    if ($r.ExitCode -ne 0) { return $null }
    return ("$($r.Salida)").Trim()
}

function Detener-Cluster {
    if (Test-Path (Join-Path $DirCluster 'postmaster.pid')) {
        Invoke-Pg 'pg_ctl.exe' @('-D', $DirCluster, '-m', 'fast', 'stop') | Out-Null
        Start-Sleep -Seconds 2
    }
}

# =============================================================================
Titulo "VERIFICACION DE CONSTRUCCION LIMPIA"
Write-Host " Cluster temporal : $DirCluster"
Write-Host " Puerto           : $Puerto"
Write-Host " Proyecto         : $Proyecto"

$inicioTotal = Get-Date

try {
    # --- 0. Requisitos --------------------------------------------------------
    Paso "Comprobaciones previas"

    if (-not (Test-Path $PgBin)) { throw "No se encontro PostgreSQL en '$PgBin'. Pasar la ruta con -PgBin." }
    foreach ($exe in @('initdb.exe','pg_ctl.exe','psql.exe','pg_isready.exe')) {
        if (-not (Test-Path (Join-Path $PgBin $exe))) { throw "Falta $exe en $PgBin" }
    }
    Bien "herramientas de PostgreSQL"

    # El puerto tiene que estar libre. Si esta ocupado y no se comprueba, initdb
    # funciona, pg_ctl start falla y el mensaje no dice que el problema es el
    # puerto.
    $ocupado = Test-NetConnection -ComputerName localhost -Port $Puerto -InformationLevel Quiet -WarningAction SilentlyContinue
    if ($ocupado) { throw "El puerto $Puerto ya esta en uso. Elegir otro con -Puerto." }
    Bien "puerto $Puerto libre"

    if ($DirCluster.Length -gt 200) { throw "La ruta del cluster es demasiado larga ($($DirCluster.Length)). Usar -DirCluster con una ruta corta." }
    if ($env:OneDrive -and $DirCluster.StartsWith($env:OneDrive, 'OrdinalIgnoreCase')) {
        throw "El cluster no puede vivir dentro de OneDrive ($env:OneDrive). Usar -DirCluster fuera de la carpeta sincronizada."
    }
    Bien "ruta del cluster valida"

    if (-not $SoloEsquema) {
        if (-not $JavaHome) {
            $JavaHome = Get-ChildItem 'C:\Program Files\Microsoft','C:\Program Files\Eclipse Adoptium','C:\Program Files\Java' `
                          -Directory -ErrorAction SilentlyContinue |
                        Where-Object { $_.Name -match 'jdk-?17' } |
                        Select-Object -First 1 -ExpandProperty FullName
        }
        if (-not $JavaHome -or -not (Test-Path $JavaHome)) {
            throw ("No se encontro un JDK 17. Pasarlo con -JavaHome, o usar -SoloEsquema para " +
                   "verificar unicamente las fases 0 a 29 (que no necesitan el backend).")
        }
        if (-not $Mvn) {
            $Mvn = (Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source
            if (-not $Mvn) {
                $Mvn = Get-ChildItem "$env:USERPROFILE" -Recurse -Filter 'mvn.cmd' -ErrorAction SilentlyContinue -Depth 6 |
                       Select-Object -First 1 -ExpandProperty FullName
            }
        }
        if (-not $Mvn -or -not (Test-Path $Mvn)) {
            throw ("No se encontro Maven (mvn.cmd). Pasarlo con -Mvn, o usar -SoloEsquema. " +
                   "El repositorio no incluye mvnw.")
        }
        Bien "JDK 17 en $JavaHome"
        Bien "Maven en $Mvn"
    }

    # --- 1. Cluster temporal --------------------------------------------------
    Paso "Creando el cluster temporal"

    if (Test-Path $DirCluster) {
        Detener-Cluster
        Remove-Item $DirCluster -Recurse -Force
    }
    New-Item -ItemType Directory -Path $DirCluster -Force | Out-Null

    # La clave va por archivo y no por consola: initdb --pwfile no la deja en el
    # historial ni en la lista de procesos. Se borra en cuanto se usa.
    $archClave = Join-Path $env:TEMP ('marathon_verif_' + [guid]::NewGuid().ToString('N') + '.txt')
    Set-Content -Path $archClave -Value $Clave -Encoding ascii -NoNewline
    try {
        $r = Invoke-Pg 'initdb.exe' @('-D', $DirCluster, '-U', 'postgres', "--pwfile=$archClave", '-E', 'UTF8', '--locale=C')
        if ($r.ExitCode -ne 0) { throw "initdb fallo: $($r.Salida -join ' ')" }
    } finally { Remove-Item $archClave -Force -ErrorAction SilentlyContinue }
    Bien "initdb"

    # CUIDADO: 'pg_ctl start' NO se puede invocar con & capturando su salida.
    # El proceso 'postgres' que arranca HEREDA los descriptores de salida, y
    # PowerShell espera a que se cierren todos antes de continuar: el servidor
    # queda perfectamente arrancado y el script se cuelga para siempre. Se
    # detecto asi la primera vez que se ejecuto este archivo.
    # Se lanza desacoplado y se espera al efecto observable, que es que el
    # servidor acepte conexiones.
    Start-Process -FilePath (Join-Path $PgBin 'pg_ctl.exe') `
        -ArgumentList '-D', "`"$DirCluster`"", '-o', "`"-p $Puerto`"", '-l', "`"$(Join-Path $DirCluster 'server.log')`"", 'start' `
        -WindowStyle Hidden | Out-Null

    $arriba = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 1
        if ((Invoke-Pg 'pg_isready.exe' @('-h','localhost','-p',"$Puerto",'-U','postgres')).ExitCode -eq 0) { $arriba = $true; break }
    }
    if (-not $arriba) { throw "El cluster no arranco en 30 s. Ver $(Join-Path $DirCluster 'server.log')" }
    Bien "servidor escuchando en el puerto $Puerto"

    $env:PGPASSWORD       = $Clave
    $env:PGCLIENTENCODING = 'UTF8'

    # --- 2. Etapa 1: esquema --------------------------------------------------
    Paso "Etapa 1: esquema (fases 0 a 29)"

    $constructor = Join-Path $PSScriptRoot 'construir_desde_cero.ps1'
    if (-not (Test-Path $constructor)) { throw "No se encontro $constructor" }

    # La salida se CAPTURA, no se descarta: si el constructor falla, el motivo
    # esta en sus ultimas lineas y sin ellas el informe solo dice "codigo 1",
    # que no sirve para nada. Se aprendio en la primera ejecucion de este
    # archivo, que reporto un fallo de fase35 sin decir cual.
    $ini = Get-Date
    $r = Invoke-Externo 'powershell' @('-NoProfile','-ExecutionPolicy','Bypass','-File',$constructor,'-Etapa','Esquema','-PgPort',"$Puerto")
    if ($r.ExitCode -ne 0) {
        Mal "la etapa Esquema termino con codigo $($r.ExitCode)"
        $r.Salida | Select-Object -Last 20 | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray }
        throw "Etapa Esquema fallida"
    }

    $tablas = Consulta "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';"
    if ($tablas -eq '37') { Bien "37 tablas en $([int]((Get-Date)-$ini).TotalSeconds) s" }
    else                  { Mal  "se esperaban 37 tablas y hay $tablas" }

    if ($SoloEsquema) {
        Write-Host ""
        Write-Host "    -SoloEsquema: se omiten el backend y la etapa 2." -ForegroundColor Yellow
    }
    else {
        # --- 3. El paso que no es un script -----------------------------------
        Paso "Arrancando el backend para que el DataInitializer cree los roles"

        # Como postgres y con los pools por rol desactivados: en este punto no
        # existe ni un GRANT (los otorga la fase 34, en la etapa 2), asi que
        # usr_admin_marathon no puede escribir en unas tablas que son de postgres.
        $argsSpring = "--spring.datasource.url=jdbc:postgresql://localhost:$Puerto/mod_venta_inve?sslmode=disable " +
                      "--spring.datasource.username=postgres " +
                      "--spring.datasource.password=$Clave " +
                      "--app.datasource.roles.enabled=false"
        $logBackend = Join-Path $DirCluster 'backend.log'

        $env:JAVA_HOME = $JavaHome
        $proc = Start-Process -FilePath $Mvn `
                    -ArgumentList '-q','-DskipTests','spring-boot:run',"`"-Dspring-boot.run.arguments=$argsSpring`"" `
                    -WorkingDirectory (Join-Path $Proyecto 'marathon-backend') `
                    -RedirectStandardOutput $logBackend `
                    -RedirectStandardError (Join-Path $DirCluster 'backend.err.log') `
                    -WindowStyle Hidden -PassThru

        try {
            # Se espera al EFECTO OBSERVABLE (la tabla 'rol' poblada), no a un
            # tiempo fijo: la compilacion tarda lo que tarde segun la maquina y
            # segun si el repositorio local de Maven esta caliente.
            $listo = $false
            $limite = (Get-Date).AddMinutes(6)
            while ((Get-Date) -lt $limite) {
                Start-Sleep -Seconds 5
                if ($proc.HasExited) {
                    Mal "el backend termino solo (codigo $($proc.ExitCode)). Ver $logBackend"
                    break
                }
                $n = Consulta "SELECT count(*) FROM rol;"
                if ($n -and [int]$n -gt 0) { $listo = $true; break }
            }
            if ($listo) { Bien "DataInitializer termino: $(Consulta 'SELECT count(*) FROM rol;') roles de aplicacion" }
            elseif (-not $proc.HasExited) { Mal "el DataInitializer no termino en 6 minutos" }
        }
        finally {
            # taskkill /T porque mvn.cmd lanza un java hijo: matar solo el padre
            # deja el proceso Java vivo, con el puerto 8080 y una conexion a la
            # base abiertos, y el pg_ctl stop del final se queda esperando.
            if (-not $proc.HasExited) {
                & taskkill /PID $proc.Id /T /F 2>&1 | Out-Null
                Start-Sleep -Seconds 3
            }
        }
        if ($script:Fallos.Count -gt 0) { throw "El arranque del backend fallo" }
        Bien "backend detenido"

        # --- 4. Etapa 2 -------------------------------------------------------
        Paso "Etapa 2: datos, seguridad, auditoria, cifrado y volumen"
        $ini = Get-Date
        $r = Invoke-Externo 'powershell' @('-NoProfile','-ExecutionPolicy','Bypass','-File',$constructor,'-Etapa','Datos','-PgPort',"$Puerto")
        if ($r.ExitCode -ne 0) {
            Mal "la etapa Datos termino con codigo $($r.ExitCode)"
            $r.Salida | Select-Object -Last 20 | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray }
            # Sin la etapa 2 no hay roles, ni auditoria, ni cifrado: seguir con
            # los arneses solo produce ruido que esconde el fallo real.
            throw "Etapa Datos fallida"
        }
        Bien "etapa Datos completada en $([int]((Get-Date)-$ini).TotalSeconds) s"

        # --- 5. Arneses -------------------------------------------------------
        Paso "Arneses de prueba"

        $arneses = @(
            @{ Archivo = 'fase34_pruebas_roles.sql';        Patron = '(\d+) de (\d+) pruebas de privilegios PASAN'; Nombre = 'privilegios'; Clave = $false },
            @{ Archivo = 'fase40_pruebas_auditoria.sql';    Patron = '(\d+) de (\d+) pruebas de auditoria PASAN';   Nombre = 'auditoria';   Clave = $false },
            @{ Archivo = 'fase41_pruebas_cifrado.sql';      Patron = '(\d+) de (\d+) pruebas de cifrado PASAN';     Nombre = 'cifrado';     Clave = $true  }
        )

        foreach ($a in $arneses) {
            $ruta = Join-Path $Proyecto "marathon-backend\sql\$($a.Archivo)"
            if (-not (Test-Path $ruta)) { Mal "no existe $($a.Archivo)"; continue }

            if ($a.Clave) {
                # fase41 lee la clave con \getenv: solo llega al entorno del
                # proceso a traves de gestionar_clave.ps1 -Accion Ejecutar.
                $gestor = Join-Path $Proyecto 'scripts\cifrado\gestionar_clave.ps1'
                $salida = (Invoke-Externo 'powershell' @('-NoProfile','-ExecutionPolicy','Bypass','-File',$gestor,
                                                         '-Accion','Ejecutar','-Script',$ruta,'-Base','mod_venta_inve',
                                                         '-PgHost','localhost','-PgPort',"$Puerto")).Salida
            } else {
                $salida = (Invoke-Pg 'psql.exe' @('-h','localhost','-p',"$Puerto",'-U','postgres',
                                                  '-d','mod_venta_inve','-q','-f',$ruta)).Salida
            }

            $m = [regex]::Match(("$salida"), $a.Patron)
            if ($m.Success -and $m.Groups[1].Value -eq $m.Groups[2].Value) {
                Bien "$($m.Groups[1].Value)/$($m.Groups[2].Value) pruebas de $($a.Nombre)"
            } elseif ($m.Success) {
                Mal "$($m.Groups[1].Value)/$($m.Groups[2].Value) pruebas de $($a.Nombre)"
            } else {
                Mal "no se pudo leer el resultado de $($a.Archivo)"
            }
        }

        # fase38.1 no imprime un marcador de "N de N": se comprueban sus tres
        # afirmaciones por separado.
        $ruta = Join-Path $Proyecto 'marathon-backend\sql\fase38_1_cierre_verificacion.sql'
        $salida = "$((Invoke-Pg 'psql.exe' @('-h','localhost','-p',"$Puerto",'-U','postgres','-d','mod_venta_inve','-q','-f',$ruta)).Salida)"
        foreach ($c in @(
            @{ P = 'Los 6 invariantes cuadran al centavo'; T = 'los 6 invariantes financieros cuadran' },
            @{ P = 'Integridad estructural: 0 violaciones'; T = '0 violaciones de integridad estructural' },
            @{ P = 'Triggers verificados: 30 de 30';        T = '30 de 30 triggers activos' }
        )) {
            if ($salida -match [regex]::Escape($c.P)) { Bien $c.T } else { Mal "no se confirmo: $($c.T)" }
        }

        # --- 6. Recuento final ------------------------------------------------
        Paso "Recuento final"
        $negocio = Consulta @"
WITH e AS (SELECT relname, (xpath('/row/c/text()', query_to_xml(
             format('SELECT count(*) AS c FROM public.%I', relname), false, true, '')))[1]::text::bigint AS f
           FROM pg_stat_user_tables WHERE schemaname='public')
SELECT sum(f) FILTER (WHERE relname NOT IN ('log_accion','historial_inventario','auditoria_cambios')) FROM e;
"@
        $tablas = Consulta "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';"
        if ($tablas -eq '38') { Bien "38 tablas" } else { Mal "se esperaban 38 tablas y hay $tablas" }
        if ($negocio -and [long]$negocio -ge 1000000) { Bien "$negocio filas de negocio (>= 1.000.000)" }
        else { Mal "solo $negocio filas de negocio, se esperaba 1.000.000 o mas" }
    }
}
catch {
    Mal "EXCEPCION: $($_.Exception.Message)"
}
finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    if ($Conservar) {
        Write-Host ""
        Write-Host "    -Conservar: el cluster sigue en pie en $DirCluster (puerto $Puerto)." -ForegroundColor Yellow
        Write-Host "    Para pararlo:  pg_ctl -D `"$DirCluster`" -m fast stop"
    } else {
        Paso "Limpieza"
        Detener-Cluster
        Remove-Item $DirCluster -Recurse -Force -ErrorAction SilentlyContinue
        if (Test-Path $DirCluster) { Write-Host "    AVISO no se pudo borrar $DirCluster" -ForegroundColor Yellow }
        else                       { Bien "cluster temporal eliminado" }
    }
}

# =============================================================================
$seg = [int]((Get-Date) - $inicioTotal).TotalSeconds
Titulo $(if ($script:Fallos.Count -eq 0) { "CONSTRUCCION LIMPIA VERIFICADA  ($seg s)" }
         else { "$($script:Fallos.Count) FALLO(S) EN LA CONSTRUCCION LIMPIA  ($seg s)" })

if ($script:Fallos.Count -gt 0) {
    Write-Host ""
    foreach ($f in $script:Fallos) { Write-Host "  - $f" -ForegroundColor Red }
    Write-Host ""
    Write-Host "  Un fallo aqui NO significa que la base real este mal: significa que el"
    Write-Host "  repositorio no basta para reconstruirla. Ver DEUDA_TECNICA.md, seccion"
    Write-Host "  'Construccion desde cero'."
    exit 1
}

Write-Host ""
Write-Host "  El repositorio basta para reconstruir el sistema entero desde cero."
exit 0
