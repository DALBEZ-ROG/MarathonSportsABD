# =============================================================================
# CONSTRUIR mod_venta_inve DESDE CERO — Marathon Sports
# -----------------------------------------------------------------------------
# Levanta la base ENTERA a partir de los scripts SQL del repositorio: esquema,
# roles, privilegios, auditoria, cifrado, configuracion y el millon de filas.
# No hace falta ningun volcado ni ningun dato de nadie.
#
# POR QUE ESTA VIA Y NO EL PAQUETE DE exportar_bd.ps1
#   exportar/importar_bd.ps1 COPIAN la base de un equipo a otro: sirven para
#   tener una replica identica, fila a fila, del equipo de origen. Este script
#   la CONSTRUYE. Para un companero de grupo que solo necesita "lo mismo que
#   tienes tu" es mejor construirla:
#     - no viaja ningun dato, solo el repositorio que ya tiene clonado
#     - cada uno genera SU PROPIA clave de cifrado y cifra SUS PROPIOS datos,
#       asi que no hay que compartir la clave por ningun canal
#     - las contrasenas de los seis usuarios las pone cada uno; fase34 las crea
#       aleatorias a proposito, para que no vivan en el repositorio
#   A cambio, los datos generados NO son identicos: son aleatorios. Los
#   RECUENTOS si coinciden (los objetivos estan fijados en los scripts) y las
#   DISTRIBUCIONES tambien, pero un pedido concreto tendra otra fecha y otro
#   cliente. Para una entrega de base de datos eso es lo correcto; si hace falta
#   la copia exacta, usar importar_bd.ps1.
#
# EL PROCESO TIENE UNA PARADA OBLIGATORIA EN MEDIO
#   El paso 12 de SETUP_COMPLETO.md es "arrancar el backend": los roles de
#   aplicacion, los permisos y los usuarios de demostracion los crea el
#   DataInitializer de Spring, no un script SQL. El seed depende de ellos. Por
#   eso este script se ejecuta en dos etapas y para en medio.
#
# USO:
#   # Etapa 1: base vacia + esquema completo (fases 0 a 29)
#   powershell -ExecutionPolicy Bypass -File construir_desde_cero.ps1 -Etapa Esquema
#
#   #   ... arrancar el backend UNA VEZ y pararlo ...
#
#   # Etapa 2: datos, seguridad, auditoria, cifrado y el millon de filas
#   powershell -ExecutionPolicy Bypass -File construir_desde_cero.ps1 -Etapa Datos
# =============================================================================

# NO PONER GUIONES LARGOS DENTRO DE UNA CADENA ENTRE COMILLAS EN ESTE ARCHIVO.
# Estos .ps1 se guardan en UTF-8 SIN BOM, y PowerShell 5.1 lee un archivo sin BOM
# como ANSI (cp1252). El guion largo son tres bytes, E2 80 94, y el ultimo se
# convierte en U+201D: la comilla tipografica de cierre, que PowerShell ACEPTA
# como delimitador de cadena. Resultado: la cadena se cierra a mitad y el script
# entero deja de analizarse, con errores de sintaxis en lineas que no tienen
# nada malo. En comentarios es inofensivo (por eso el resto de scripts del repo
# los usan en sus cabeceras); dentro de "..." rompe el archivo.

param(
    [ValidateSet('Esquema','Datos')]
    [string] $Etapa = 'Esquema',
    [string] $Base         = 'mod_venta_inve',
    [string] $PgBin        = 'C:\Program Files\PostgreSQL\18\bin',
    [string] $Superusuario = 'postgres',
    [string] $PgHost       = 'localhost',
    [int]    $PgPort       = 5432,
    # Solo para la etapa Esquema: si la base ya existe, la BORRA y la rehace.
    [switch] $Recrear,
    # Salta el cifrado (fase 41). La base queda completa y funcional, pero con
    # los datos personales EN CLARO y sin las 8 columnas bytea.
    [switch] $SinCifrado
)

$ErrorActionPreference = 'Stop'

$Proyecto = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Sql      = Join-Path $Proyecto 'marathon-backend\sql'
$Cifrado  = Join-Path $Proyecto 'scripts\cifrado'

function Titulo($t) { Write-Host ""; Write-Host "==============================================================" ; Write-Host " $t"; Write-Host "==============================================================" }
function Paso($t)   { Write-Host ""; Write-Host ">>> $t" -ForegroundColor Cyan }
function Bien($t)   { Write-Host "    OK   $t" }
function Aviso($t)  { Write-Host "    AVISO $t" -ForegroundColor Yellow }
function Humano($t) { Write-Host ""; Write-Host "    +-- INTERVENCION HUMANA ----------------------------------" -ForegroundColor Magenta
                      foreach ($l in $t) { Write-Host "    | $l" -ForegroundColor Magenta }
                      Write-Host "    +---------------------------------------------------------" -ForegroundColor Magenta }

function Invoke-Pg {
    # Los ejecutables de PostgreSQL escriben avisos por stderr; se decide por el
    # codigo de salida, que es lo unico fiable. (El parametro NO puede llamarse
    # $Args: es variable automatica de PowerShell y los argumentos se perderian
    # en silencio.)
    param([string] $Exe, [string[]] $Argumentos)
    $previo = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $salida = & (Join-Path $PgBin $Exe) @Argumentos 2>&1
        $codigo = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previo }
    return [pscustomobject]@{ ExitCode = $codigo; Salida = $salida }
}

function Ejecutar-Sql {
    <#  Ejecuta un .sql contra la base. ON_ERROR_STOP=1 para que el primer error
        aborte: en una cadena de 20 scripts donde cada uno depende del anterior,
        seguir tras un fallo solo produce una avalancha de errores derivados que
        esconde el primero, que es el unico que importa. #>
    param([string] $Archivo, [string] $Descripcion, [switch] $Opcional)
    $ruta = Join-Path $Sql $Archivo
    if (-not (Test-Path $ruta)) {
        if ($Opcional) { Aviso "no existe $Archivo (opcional, se salta)"; return }
        throw "No se encontro $ruta"
    }
    $ini = Get-Date
    $r = Invoke-Pg 'psql.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$Superusuario,'-d',$Base,
                                '-q','-v','ON_ERROR_STOP=1','-f',$ruta)
    $seg = [int]((Get-Date) - $ini).TotalSeconds
    if ($r.ExitCode -ne 0) {
        Write-Host ""
        Write-Host "FALLO en $Archivo" -ForegroundColor Red
        $r.Salida | Select-Object -Last 15 | ForEach-Object { Write-Host "    $_" }
        throw "$Archivo termino con codigo $($r.ExitCode)"
    }
    Bien "$Descripcion  ($Archivo, $seg s)"
}

function Consulta($sql) {
    $r = Invoke-Pg 'psql.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$Superusuario,'-d',$Base,'-t','-A','-c',$sql)
    if ($r.ExitCode -ne 0) { throw "Consulta fallo: $($r.Salida -join ' ')" }
    return ("$($r.Salida)").Trim()
}

# --- credencial ---------------------------------------------------------------
if (-not $env:PGPASSWORD) {
    # Nunca por parametro: quedaria en el historial de PowerShell.
    Write-Host "Contrasena del superusuario '$Superusuario' (no se mostrara):"
    $segura = Read-Host -AsSecureString "PGPASSWORD"
    $bstr   = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($segura)
    try     { $env:PGPASSWORD = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}
$env:PGCLIENTENCODING = 'UTF8'

if (-not (Test-Path $PgBin)) { throw "No se encontro PostgreSQL en '$PgBin'. Pasar la ruta con -PgBin." }

# =============================================================================
# ETAPA 1 — ESQUEMA
# =============================================================================
if ($Etapa -eq 'Esquema') {

    Titulo "ETAPA 1 de 2 - ESQUEMA (fases 0 a 29)"

    Paso "Base de datos '$Base'"
    $existe = Invoke-Pg 'psql.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$Superusuario,'-d','postgres',
                                     '-t','-A','-c',"SELECT 1 FROM pg_database WHERE datname='$Base';")
    if (("$($existe.Salida)").Trim() -eq '1') {
        if (-not $Recrear) {
            throw ("La base '$Base' ya existe. Para borrarla y empezar de cero: -Recrear. " +
                   "Para construir en otra base: -Base <nombre>.")
        }
        Aviso "existe y se pidio -Recrear: se ELIMINA"
        $r = Invoke-Pg 'psql.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$Superusuario,'-d','postgres','-c',"DROP DATABASE $Base;")
        if ($r.ExitCode -ne 0) { throw "No se pudo eliminar: $($r.Salida -join ' ')" }
    }
    $r = Invoke-Pg 'psql.exe' @('-h',$PgHost,'-p',"$PgPort",'-U',$Superusuario,'-d','postgres','-c',"CREATE DATABASE $Base;")
    if ($r.ExitCode -ne 0) { throw "CREATE DATABASE fallo: $($r.Salida -join ' ')" }
    Bien "creada"

    Paso "Esquema (el orden importa: cada fase depende de la anterior)"
    Ejecutar-Sql 'fase00_ddl_base.sql'            '20 tablas base, funciones y triggers'
    Ejecutar-Sql 'fase21_ordenes_compra.sql'      'ordenes de compra'
    Ejecutar-Sql 'fase22_recepcion_mercancia.sql' 'recepcion de mercancia'
    Ejecutar-Sql 'fase23_factura_compra_cxp.sql'  'factura de compra y cuentas por pagar'
    Ejecutar-Sql 'fase24_devolucion_cliente.sql'  'devolucion de cliente'
    Ejecutar-Sql 'fase25_devolucion_proveedor.sql' 'devolucion a proveedor'
    Ejecutar-Sql 'fase26_kardex_materia_prima.sql' 'kardex de materia prima'
    Ejecutar-Sql 'fase27_origen_producto_bom.sql' 'origen de producto y BOM'
    Ejecutar-Sql 'fase28_ordenes_produccion.sql'  'ordenes de produccion'
    Ejecutar-Sql 'fase29_costeo_produccion.sql'   'costeo de produccion'

    $tablas = Consulta "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';"
    Paso "Verificacion de la etapa 1"
    Bien "$tablas tablas (esperado 37; la 38 la crea la fase 40)"

    Titulo "ETAPA 1 COMPLETADA"
    Humano @(
        'AHORA HAY QUE ARRANCAR EL BACKEND UNA VEZ Y PARARLO.',
        '',
        'Los roles de aplicacion, los permisos y los usuarios de demostracion',
        'los crea el DataInitializer de Spring, no un script SQL. El seed de la',
        'etapa 2 depende de que existan.',
        '',
        'OJO: ESTE ARRANQUE SE HACE COMO postgres, NO COMO usr_admin_marathon.',
        'Todavia no existe ni un solo GRANT (los otorga la fase 34, en la etapa',
        '2), asi que usr_admin_marathon no tiene ningun privilegio sobre las',
        'tablas y el DataInitializer fallaria. Es la UNICA vez que se usa el',
        'superusuario para la aplicacion; despues manda el modelo de roles.',
        '',
        '  1. Crear el .env en la raiz del proyecto (copiar de .env.example).',
        '     Para este arranque basta con DB_* y JWT_SECRET.',
        '  2. cd marathon-backend',
        '     mvn -q -DskipTests spring-boot:run "-Dspring-boot.run.arguments=--spring.datasource.username=postgres --spring.datasource.password=<clave de postgres> --app.datasource.roles.enabled=false"',
        '',
        '     (--app.datasource.roles.enabled=false porque los otros cinco pools',
        '      tampoco tienen privilegios todavia)',
        '',
        '  3. Esperar a "Datos iniciales cargados correctamente" y parar con Ctrl+C.',
        '',
        'Si java -version dice 1.8, forzar antes el JDK 17:',
        '  $env:JAVA_HOME = ''C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot''',
        '',
        'Despues, la etapa 2:',
        "  powershell -ExecutionPolicy Bypass -File construir_desde_cero.ps1 -Etapa Datos"
    )
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    exit 0
}

# =============================================================================
# ETAPA 2 — DATOS, SEGURIDAD, AUDITORIA, CIFRADO Y VOLUMEN
# =============================================================================
Titulo "ETAPA 2 de 2 - DATOS, SEGURIDAD Y VOLUMEN"

# Comprobacion de que la etapa 1 y el arranque del backend ocurrieron. Sin esto,
# el seed falla a mitad con errores de clave foranea que no dicen la causa real.
Paso "Comprobando que el backend ya arranco una vez"
$nRoles = Consulta "SELECT count(*) FROM rol;"
if ([int]$nRoles -eq 0) {
    throw ("La tabla 'rol' esta vacia: el backend no ha arrancado todavia. " +
           "Los roles de aplicacion los crea el DataInitializer de Spring. " +
           "Arrancar el backend una vez, pararlo, y volver a lanzar esta etapa.")
}
Bien "$nRoles roles de aplicacion presentes"

Paso "Datos de demostracion"
Ejecutar-Sql 'seed_marathon_sports.sql'              'datos de negocio base'
Ejecutar-Sql 'fase31_0_unidades_faltantes.sql'       'unidades de medida 4 a 9 (fase31 las usa por numero)'
Ejecutar-Sql 'fase31_seed_demo_bloques_nuevos.sql'   'demo de compras, devoluciones y manufactura'
Ejecutar-Sql 'fase32_fixes.sql'                      'correcciones de deuda tecnica'

Paso "Fase 33 - optimizacion (indices)"
Ejecutar-Sql 'fase33_optimizacion_indices.sql'       'indices sobre las consultas criticas'

Paso "Fase 34 - seguridad: roles y privilegios"
Ejecutar-Sql 'fase34_seguridad_roles.sql'            '6 roles, 6 usuarios y privilegios por columna'
Aviso "los 6 usuarios se crearon con contrasena ALEATORIA (ver la parada del final)"

Paso "Fases 35 a 37 - respaldo, auditoria nativa y conexion por rol"
Ejecutar-Sql 'fase35_respaldo_prerequisitos.sql'     'summarize_wal para los diferenciales'
Ejecutar-Sql 'fase36_auditoria_nativa.sql'           'registro nativo de PostgreSQL'
Ejecutar-Sql 'fase37_conexion_por_rol.sql'           'privilegios para el pool por rol'

Paso "Fase 38 - poblado masivo (esto tarda; se generan cientos de miles de filas)"
Ejecutar-Sql 'fase38_poblado_masivo.sql'             'carga a 1.000.000 de filas'
Ejecutar-Sql 'fase38_correccion_distribuciones.sql'  'correccion de distribuciones'
Ejecutar-Sql 'fase38_verificacion_poblado.sql'       'verificacion del poblado'
Ejecutar-Sql 'fase38_1_cierre_verificacion.sql'      'invariantes e integridad'

Paso "Fase 39 - volumen en compras y manufactura"
Ejecutar-Sql 'fase39_volumen_compras.sql'            '~40.000 filas en compras y produccion'

Paso "Fase 40 - auditoria de cambios"
Ejecutar-Sql 'fase40_correcciones_datos.sql'         'realismo de datos'
Ejecutar-Sql 'fase40_auditoria_generica.sql'         'auditoria_cambios campo a campo'

Paso "Fase 41 - optimizacion con evidencia"
Ejecutar-Sql 'fase41_eliminar_indices.sql'           'baja de 4 indices con idx_scan = 0'

# --- cifrado ------------------------------------------------------------------
if ($SinCifrado) {
    Aviso "cifrado OMITIDO por -SinCifrado: los datos personales quedan EN CLARO"
} else {
    Paso "Fase 41 - cifrado de datos personales"

    # La clave se crea AQUI, en este equipo. No es la del equipo de origen y no
    # tiene por que serlo: estos datos se acaban de generar aqui. Compartir la
    # clave solo hace falta si se restaura un volcado ajeno (importar_bd.ps1).
    $gestor = Join-Path $Cifrado 'gestionar_clave.ps1'
    if (-not (Test-Path $gestor)) { throw "No se encontro $gestor" }

    & powershell -NoProfile -ExecutionPolicy Bypass -File $gestor -Accion Crear
    if ($LASTEXITCODE -ne 0) { throw "No se pudo crear la clave de cifrado." }

    # fase41_cifrado.sql lee la clave con \getenv MARATHON_CRYPTO_KEY. Solo
    # gestionar_clave.ps1 -Accion Ejecutar la pone en el entorno del proceso:
    # lanzarlo con psql a pelo falla con "app.crypto_key no esta fijada".
    $ini = Get-Date
    & powershell -NoProfile -ExecutionPolicy Bypass -File $gestor `
        -Accion Ejecutar -Script (Join-Path $Sql 'fase41_cifrado.sql') `
        -Base $Base -PgHost $PgHost -PgPort $PgPort
    if ($LASTEXITCODE -ne 0) { throw "fase41_cifrado.sql fallo." }
    Bien "8 columnas cifradas  (fase41_cifrado.sql, $([int]((Get-Date)-$ini).TotalSeconds) s)"
}

Paso "Fase 43 - el millon, en tablas de negocio"
Ejecutar-Sql 'fase43_ampliacion_negocio.sql'         '+65.000 pedidos y sus lineas'

# --- verificacion -------------------------------------------------------------
Paso "Verificacion final"

$tablas   = Consulta "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';"
$roles    = Consulta "SELECT count(*) FROM pg_roles WHERE NOT rolcanlogin AND rolname LIKE 'rol\_%';"
$usuarios = Consulta "SELECT count(*) FROM pg_roles WHERE rolcanlogin AND rolname LIKE 'usr\_%';"
$peligro  = Consulta "SELECT count(*) FROM pg_roles WHERE rolname LIKE 'usr\_%' AND (rolsuper OR rolcreaterole OR rolbypassrls);"
$privPub  = Consulta "SELECT count(*) FROM information_schema.table_privileges WHERE grantee='PUBLIC' AND table_schema='public';"
$triggers = Consulta "SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal;"
$apagados = Consulta "SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal AND tgenabled<>'O';"
$cifradas = Consulta "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND data_type='bytea';"
$negocio  = Consulta @"
WITH e AS (SELECT relname, (xpath('/row/c/text()', query_to_xml(
             format('SELECT count(*) AS c FROM public.%I', relname), false, true, '')))[1]::text::bigint AS f
           FROM pg_stat_user_tables WHERE schemaname='public')
SELECT sum(f) FILTER (WHERE relname NOT IN ('log_accion','historial_inventario','auditoria_cambios')) FROM e;
"@

$esperadoCifradas = if ($SinCifrado) { '0' } else { '8' }
$filas = @(
    @{ N='Tablas';                    V=$tablas;   E='38' },
    @{ N='Roles NOLOGIN';             V=$roles;    E='6'  },
    @{ N='Usuarios de login';         V=$usuarios; E='6'  },
    @{ N='Usuarios con superpoderes'; V=$peligro;  E='0'  },
    @{ N='Concesiones a PUBLIC';      V=$privPub;  E='0'  },
    @{ N='Triggers no internos';      V=$triggers; E='30' },
    @{ N='Triggers desactivados';     V=$apagados; E='0'  },
    @{ N='Columnas cifradas';         V=$cifradas; E=$esperadoCifradas }
)

Write-Host ""
Write-Host ("    {0,-28} {1,12} {2,12}   {3}" -f 'COMPROBACION','OBTENIDO','ESPERADO','')
$fallos = 0
foreach ($f in $filas) {
    $ok = ($f.V -eq $f.E); if (-not $ok) { $fallos++ }
    Write-Host ("    {0,-28} {1,12} {2,12}   {3}" -f $f.N, $f.V, $f.E, $(if ($ok) {'OK'} else {'DISTINTO'}))
}
# Las filas no se comparan con un valor exacto: los objetivos de los scripts son
# fijos, pero un par de bloques rellenan "hasta llegar a", asi que el total puede
# variar en unas decenas. Lo que importa es que pase del millon.
$ok = ([long]$negocio -ge 1000000)
if (-not $ok) { $fallos++ }
Write-Host ("    {0,-28} {1,12} {2,12}   {3}" -f 'Filas de negocio', $negocio, '>= 1.000.000', $(if ($ok) {'OK'} else {'INSUFICIENTE'}))

Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue

Titulo $(if ($fallos -eq 0) { "BASE CONSTRUIDA Y VERIFICADA" } else { "CONSTRUIDA CON $fallos DIFERENCIA(S)" })

Humano @(
    'FALTAN TRES COSAS QUE NO PUEDE HACER UN SCRIPT:',
    '',
    '1. LAS CONTRASENAS DE LOS SEIS USUARIOS.',
    '   fase34 los creo con contrasena aleatoria a proposito, para que no',
    '   vivan en el repositorio. Elegir una para cada uno y fijarlas:',
    '',
    "     ALTER ROLE usr_admin_marathon      WITH PASSWORD '<clave>';",
    "     ALTER ROLE usr_supervisor_marathon WITH PASSWORD '<clave>';",
    "     ALTER ROLE usr_bodega_marathon     WITH PASSWORD '<clave>';",
    "     ALTER ROLE usr_pedidos_marathon    WITH PASSWORD '<clave>';",
    "     ALTER ROLE usr_compras_marathon    WITH PASSWORD '<clave>';",
    "     ALTER ROLE usr_produccion_marathon WITH PASSWORD '<clave>';",
    '',
    '   Y escribir esas mismas seis en el .env (DB_PASSWORD y las cinco',
    '   DB_PASSWORD_*). Sin esto el backend no abre los pools por rol.',
    '',
    '2. EL CERTIFICADO TLS de este equipo:',
    '     powershell -File scripts\cifrado\configurar_tls.ps1',
    '',
    '3. LAS TAREAS DE RESPALDO (consola de administrador):',
    '     powershell -File scripts\backup\registrar_tareas.ps1',
    '',
    'Y para arrancar, SIEMPRE con este script y no con mvn a secas:',
    '     powershell -File scripts\cifrado\iniciar_backend.ps1'
)

if ($fallos -ne 0) { exit 1 }
exit 0
