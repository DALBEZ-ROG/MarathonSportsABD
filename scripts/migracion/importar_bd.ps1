# =============================================================================
# IMPORTAR LA BASE EN ESTE EQUIPO — Marathon Sports
# -----------------------------------------------------------------------------
# Contraparte de exportar_bd.ps1. Se ejecuta EN EL EQUIPO DESTINO, dentro de la
# carpeta del paquete que genero el equipo de origen.
#
# EL ORDEN NO ES NEGOCIABLE Y ESTA ES LA RAZON.
# El volcado (02_base.dump) contiene los GRANT del modelo de seguridad, incluidos
# los privilegios por columna sobre las columnas cifradas. Los roles a los que se
# conceden son objetos del CLUSTER y NO viajan dentro de un pg_dump. Si se
# restaura la base antes de crear los roles, cada GRANT falla con "el rol
# rol_administrador no existe" y la base queda levantada pero SIN modelo de
# privilegios: funciona, y por eso el fallo pasa desapercibido.
#
#   01_roles.sql  ->  02_base.dump  ->  03_configuracion.sql
#
# LO QUE ESTE SCRIPT NO PUEDE HACER, Y HAY QUE HACER A MANO DESPUES:
#   - la clave de cifrado (gestionar_clave.ps1 -Accion Importar)
#   - el .env con las contrasenas
#   - el certificado TLS (configurar_tls.ps1)
#   - las tareas programadas de respaldo (registrar_tareas.ps1)
# Se listan al final con su comando exacto.
#
# USO:
#   powershell -ExecutionPolicy Bypass -File importar_bd.ps1
#   powershell -ExecutionPolicy Bypass -File importar_bd.ps1 -Paquete D:\marathon_20260816_1200
#   powershell -ExecutionPolicy Bypass -File importar_bd.ps1 -PgBin 'C:\Program Files\PostgreSQL\18\bin'
# =============================================================================

param(
    # Carpeta con 01_roles.sql, 02_base.dump y 03_configuracion.sql. Por defecto,
    # la carpeta donde esta este script: el paquete se copia entero y se ejecuta
    # desde dentro.
    [string] $Paquete      = $PSScriptRoot,
    [string] $PgBin        = 'C:\Program Files\PostgreSQL\18\bin',
    [string] $Base         = 'mod_venta_inve',
    [string] $Superusuario = 'postgres',
    [string] $PgHost       = 'localhost',
    [int]    $PgPort       = 5432,
    # Sin esto, encontrar la base ya creada aborta. Con esto, la BORRA y la
    # rehace. Es destructivo y por eso es explicito.
    [switch] $Recrear
)

$ErrorActionPreference = 'Stop'

function Paso($n, $t) { Write-Host ""; Write-Host "[$n] $t" -ForegroundColor Cyan }
function Bien($t)     { Write-Host "      OK   $t" }
function Aviso($t)    { Write-Host "      AVISO $t" -ForegroundColor Yellow }

function Invoke-Pg {
    <#  Los ejecutables de PostgreSQL escriben avisos por stderr; con
        ErrorActionPreference='Stop' eso abortaria el script aunque todo haya
        ido bien. Se decide por el codigo de salida, que es lo unico fiable.

        OJO con el nombre del parametro: $Args es una VARIABLE AUTOMATICA de
        PowerShell. Llamarlo asi hace que no se enlace, los argumentos se
        pierden en silencio y la herramienta acaba conectando con el usuario de
        Windows. El error que sale ("autentificacion password fallo para el
        usuario <tu_usuario_windows>") no se parece en nada a la causa real. #>
    param([string] $Exe, [string[]] $Argumentos)
    $previo = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $salida = & (Join-Path $PgBin $Exe) @Argumentos 2>&1
        $codigo = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previo }
    return [pscustomobject]@{ ExitCode = $codigo; Salida = $salida }
}

function Consulta($sql) {
    $r = Invoke-Pg 'psql.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$Superusuario,'-d',$Base,
                                '-t','-A','-c',$sql)
    if ($r.ExitCode -ne 0) { throw "Consulta fallo: $($r.Salida -join ' ')" }
    return ("$($r.Salida)").Trim()
}

Write-Host "=============================================================="
Write-Host " IMPORTAR mod_venta_inve EN ESTE EQUIPO"
Write-Host "=============================================================="

# --- 0. Comprobaciones previas ------------------------------------------------
Paso '0/6' 'Comprobaciones previas'

if (-not (Test-Path $PgBin)) {
    throw ("No se encontro PostgreSQL en '$PgBin'. Instalarlo o pasar la ruta correcta " +
           "con -PgBin. Ejemplo: -PgBin 'C:\Program Files\PostgreSQL\18\bin'")
}
foreach ($exe in @('psql.exe','pg_restore.exe','pg_isready.exe')) {
    if (-not (Test-Path (Join-Path $PgBin $exe))) { throw "Falta $exe en $PgBin" }
}

$archivos = @{
    Roles = Join-Path $Paquete '01_roles.sql'
    Dump  = Join-Path $Paquete '02_base.dump'
    Cfg   = Join-Path $Paquete '03_configuracion.sql'
}
foreach ($k in $archivos.Keys) {
    if (-not (Test-Path $archivos[$k])) {
        throw ("Falta $($archivos[$k]). Ejecutar este script DENTRO de la carpeta del " +
               "paquete, o pasarla con -Paquete <ruta>.")
    }
}
Bien "paquete completo en $Paquete"

# MAX_PATH. psql.exe y pg_restore.exe NO son long-path aware: con una ruta de
# mas de 259 caracteres, -f falla con "No such file or directory" sobre un
# archivo que Test-Path confirma que existe. Es de los errores mas
# desorientadores que hay, porque el mensaje culpa al archivo equivocado.
# Se detecto probando este script desde una carpeta anidada: 02_base.dump
# (256 caracteres) restauro bien y 03_configuracion.sql (264) fallo.
$rutaLarga = ($archivos.Values | Measure-Object -Property Length -Maximum).Maximum
if ($rutaLarga -gt 259) {
    throw ("La ruta del paquete es demasiado larga: $rutaLarga caracteres, y las " +
           "herramientas de PostgreSQL no pasan de 259. Mover la carpeta del paquete " +
           "a una ruta corta (por ejemplo C:\marathon_paquete) y volver a lanzar. " +
           "Sin esto el fallo aparece a mitad del proceso y culpa a un archivo que si existe.")
}
Bien "longitud de rutas correcta (maxima $rutaLarga de 259)"

# La contrasena nunca por parametro: quedaria en el historial de PowerShell.
if (-not $env:PGPASSWORD) {
    Write-Host "      Contrasena del superusuario '$Superusuario' (no se mostrara):"
    $segura = Read-Host -AsSecureString "      PGPASSWORD"
    $bstr   = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($segura)
    try     { $env:PGPASSWORD = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}
$env:PGCLIENTENCODING = 'UTF8'

$ready = Invoke-Pg 'pg_isready.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$Superusuario)
if ($ready.ExitCode -ne 0) {
    throw "El servidor PostgreSQL no responde en ${PgHost}:${PgPort}. Comprobar que el servicio esta arrancado."
}
Bien "el servidor responde en ${PgHost}:${PgPort}"

# La version mayor importa por dos motivos distintos:
#   - pg_restore no puede leer un volcado hecho por una version MAYOR que la suya
#   - summarize_wal (respaldos diferenciales) solo existe desde PostgreSQL 17
$r = Invoke-Pg 'psql.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$Superusuario,'-d','postgres',
                            '-t','-A','-c','SHOW server_version_num;')
if ($r.ExitCode -ne 0) { throw "No se pudo conectar: $($r.Salida -join ' ')" }
$verNum = [int](("$($r.Salida)").Trim())
$verMayor = [math]::Floor($verNum / 10000)
if ($verMayor -lt 17) {
    throw ("Este equipo tiene PostgreSQL $verMayor y hacen falta 17 o mas. " +
           "Los respaldos diferenciales usan summarize_wal y pg_basebackup --incremental, " +
           "que no existen antes de la 17. El proyecto de origen usa la 18.")
}
if ($verMayor -lt 18) { Aviso "PostgreSQL $verMayor (el origen es 18). Funcionara, pero conviene igualar la version mayor." }
else                  { Bien "PostgreSQL $verMayor" }

# --- 1. La base ---------------------------------------------------------------
Paso '1/6' "Base de datos '$Base'"

$existe = Invoke-Pg 'psql.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$Superusuario,'-d','postgres',
                                 '-t','-A','-c',"SELECT 1 FROM pg_database WHERE datname='$Base';")
if (("$($existe.Salida)").Trim() -eq '1') {
    if (-not $Recrear) {
        throw ("La base '$Base' YA EXISTE en este equipo. Restaurar encima mezclaria dos " +
               "estados y dejaria errores de clave duplicada dificiles de leer. " +
               "Para borrarla y rehacerla: volver a lanzar con -Recrear.")
    }
    Aviso "la base existe y se pidio -Recrear: se ELIMINA"
    $r = Invoke-Pg 'psql.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$Superusuario,'-d','postgres',
                                '-c',"DROP DATABASE $Base;")
    if ($r.ExitCode -ne 0) { throw "No se pudo eliminar: $($r.Salida -join ' ')" }
}

$r = Invoke-Pg 'psql.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$Superusuario,'-d','postgres',
                            '-c',"CREATE DATABASE $Base;")
if ($r.ExitCode -ne 0) { throw "CREATE DATABASE fallo: $($r.Salida -join ' ')" }
Bien "creada"

# --- 2. Roles (ANTES del volcado) --------------------------------------------
Paso '2/6' 'Roles y usuarios del cluster'

# Se aplica sobre 'postgres' y no sobre la base: los roles son del cluster.
$r = Invoke-Pg 'psql.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$Superusuario,'-d','postgres',
                            '-v','ON_ERROR_STOP=1','-f',$archivos.Roles)
if ($r.ExitCode -ne 0) { throw "01_roles.sql fallo: $($r.Salida -join ' ')" }

$nRoles = Invoke-Pg 'psql.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$Superusuario,'-d','postgres',
                                 '-t','-A','-c',"SELECT count(*) FROM pg_roles WHERE rolname LIKE 'usr\_%' OR rolname LIKE 'rol\_%';")
Bien "$(("$($nRoles.Salida)").Trim()) roles del proyecto presentes (esperado 12: 6 usuarios + 6 roles)"

# --- 3. El volcado ------------------------------------------------------------
Paso '3/6' 'Restaurando la base (esto tarda)'

$ini = Get-Date
# --exit-on-error NO se usa a proposito: pg_restore avisa por objetos que ya
# existen (el esquema public, la extension pgcrypto si la instalo otro paquete)
# y abortar por eso dejaria la restauracion a medias. Se cuentan los errores
# reales despues.
$r = Invoke-Pg 'pg_restore.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$Superusuario,
                                  '-d',$Base,'--no-password','-j','2',$archivos.Dump)
$errores = @($r.Salida | Where-Object { "$_" -match 'error:' })
$seg = [int]((Get-Date) - $ini).TotalSeconds

if ($errores.Count -gt 0) {
    Aviso "$($errores.Count) error(es) durante la restauracion. Los 10 primeros:"
    $errores | Select-Object -First 10 | ForEach-Object { Write-Host "        $_" }
    Write-Host ""
    Aviso "Si hablan de 'ya existe', suelen ser inocuos. Si hablan de 'el rol ... no existe',"
    Aviso "es que 01_roles.sql no se aplico: la base quedaria SIN modelo de privilegios."
} else {
    Bien "sin errores"
}
Bien "restaurada en $seg s"

# --- 4. Configuracion del servidor -------------------------------------------
Paso '4/6' 'Parametros del servidor'

# ALTER SYSTEM escribe en postgresql.auto.conf; no toca postgresql.conf.
$r = Invoke-Pg 'psql.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$Superusuario,'-d',$Base,
                            '-v','ON_ERROR_STOP=1','-f',$archivos.Cfg)
if ($r.ExitCode -ne 0) { throw "03_configuracion.sql fallo: $($r.Salida -join ' ')" }
Bien "aplicados y recargados"
Aviso "shared_buffers y logging_collector exigen REINICIAR el servicio de PostgreSQL"
Aviso "  Restart-Service postgresql-x64-$verMayor   (en consola de administrador)"

# --- 5. Estadisticas ----------------------------------------------------------
Paso '5/6' 'ANALYZE'
# Un volcado restaurado llega SIN estadisticas: pg_dump no las incluye. Sin
# ANALYZE el planificador trabaja a ciegas sobre un millon de filas y cualquier
# medicion de la fase 39 daria numeros que no se parecen a los del origen.
$r = Invoke-Pg 'psql.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$Superusuario,'-d',$Base,'-c','ANALYZE;')
if ($r.ExitCode -ne 0) { throw "ANALYZE fallo: $($r.Salida -join ' ')" }
Bien "estadisticas calculadas"

# --- 6. Verificacion ----------------------------------------------------------
Paso '6/6' 'Verificacion'

$tablas    = Consulta "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';"
$triggers  = Consulta "SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal;"
$apagados  = Consulta "SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal AND tgenabled<>'O';"
$privCol   = Consulta "SELECT count(*) FROM information_schema.column_privileges WHERE grantee LIKE 'rol\_%';"
$privPub   = Consulta "SELECT count(*) FROM information_schema.table_privileges WHERE grantee='PUBLIC' AND table_schema='public';"
$cifradas  = Consulta "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND data_type='bytea';"
$indices   = Consulta "SELECT count(*) FROM pg_indexes WHERE schemaname='public';"
$negocio   = Consulta @"
WITH e AS (SELECT relname, (xpath('/row/c/text()', query_to_xml(
             format('SELECT count(*) AS c FROM public.%I', relname), false, true, '')))[1]::text::bigint AS f
           FROM pg_stat_user_tables WHERE schemaname='public')
SELECT sum(f) FILTER (WHERE relname NOT IN ('log_accion','historial_inventario','auditoria_cambios')) FROM e;
"@

$esperado = @(
    @{ N='Tablas';                     V=$tablas;   E='38'   },
    @{ N='Triggers no internos';       V=$triggers; E='30'   },
    @{ N='Triggers desactivados';      V=$apagados; E='0'    },
    @{ N='Privilegios de columna';     V=$privCol;  E='2155' },
    @{ N='Concesiones a PUBLIC';       V=$privPub;  E='0'    },
    @{ N='Columnas cifradas (bytea)';  V=$cifradas; E='8'    },
    @{ N='Indices';                    V=$indices;  E='122'  },
    @{ N='Filas de negocio';           V=$negocio;  E='1011103' }
)

Write-Host ""
Write-Host ("      {0,-28} {1,12} {2,12}   {3}" -f 'COMPROBACION','OBTENIDO','ESPERADO','')
$fallos = 0
foreach ($e in $esperado) {
    $ok = ($e.V -eq $e.E)
    if (-not $ok) { $fallos++ }
    Write-Host ("      {0,-28} {1,12} {2,12}   {3}" -f $e.N, $e.V, $e.E, $(if ($ok) {'OK'} else {'DISTINTO'}))
}

Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "=============================================================="
if ($fallos -eq 0) {
    Write-Host " BASE IMPORTADA Y VERIFICADA" -ForegroundColor Green
} else {
    Write-Host " BASE IMPORTADA CON $fallos DIFERENCIA(S)" -ForegroundColor Yellow
    Write-Host " Revisar arriba. 'Privilegios de columna' a 0 significa que los"
    Write-Host " roles no existian al restaurar: rehacer con -Recrear."
}
Write-Host "=============================================================="

Write-Host ""
Write-Host "FALTA POR HACER A MANO EN ESTE EQUIPO:"
Write-Host ""
Write-Host "  1. La clave de cifrado. Sin ella los correos, telefonos y direcciones"
Write-Host "     de clientes y proveedores se ven VACIOS (el resto funciona)."
Write-Host "       powershell -File scripts\cifrado\gestionar_clave.ps1 -Accion Importar -Destino <archivo de custodia>"
Write-Host "     Comprobar que la huella coincide con la del equipo de origen."
Write-Host ""
Write-Host "  2. El .env en la raiz del proyecto (copiar de .env.example y completar)."
Write-Host "     Las contrasenas de los 6 usuarios son las MISMAS que en el origen:"
Write-Host "     01_roles.sql trae sus hashes SCRAM."
Write-Host ""
Write-Host "  3. El certificado TLS de este equipo:"
Write-Host "       powershell -File scripts\cifrado\configurar_tls.ps1"
Write-Host ""
Write-Host "  4. Las tareas programadas de respaldo (consola de administrador):"
Write-Host "       powershell -File scripts\backup\registrar_tareas.ps1"
Write-Host ""
if ($fallos -ne 0) { exit 1 }
exit 0
