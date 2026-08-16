# =============================================================================
# GESTION DE LA CLAVE DE CIFRADO — Fase 41
# -----------------------------------------------------------------------------
# La clave que cifra los datos personales de cliente y proveedor.
#
# PRINCIPIO: la clave NUNCA se escribe en claro. Ni en el repositorio, ni en la
# base de datos, ni en application.properties, ni en un script SQL, ni en la
# linea de comandos (que es visible en la lista de procesos), ni en un
# comentario. Lo unico que existe en reposo es un blob DPAPI de ambito MAQUINA,
# igual que el que backup_aplicacion.ps1 usa para la configuracion con secretos.
#
# DONDE VIVE
#   Variable de entorno de MAQUINA  MARATHON_CRYPTO_KEY_PROTECTED
#       = base64 del blob DPAPI (LocalMachine). Indescifrable en otro equipo.
#   Copia operativa en disco       C:\ProgramData\MarathonSports\crypto\clave.dpapi
#       Deliberadamente FUERA de C:\respaldos\marathon: si la clave viajara
#       dentro del mismo respaldo que los datos cifrados, cifrar no habria
#       servido de nada. Quien robe el respaldo se llevaria las dos mitades.
#
# COMO LLEGA A LA APLICACION
#   iniciar_backend.ps1 la descifra y la pasa al proceso Java en una variable de
#   entorno DEL PROCESO (no de la maquina), de modo que la clave en claro solo
#   existe en memoria del JVM. La aplicacion la publica en cada conexion con
#   set_config('app.crypto_key', ?, false) usando un PARAMETRO ENLAZADO, nunca
#   un literal: con log_statement=mod y log_parameter_max_length=-1, un literal
#   acabaria en postgresql-%a.log en texto plano durante siete dias.
#
# ACCIONES
#   Crear     genera una clave nueva de 32 bytes y la protege (no sobreescribe)
#   Estado    dice si la clave existe y su huella, SIN revelarla
#   Ejecutar  corre un .sql con la clave publicada en app.crypto_key
#   Escrow    exporta la clave EN CLARO a una ruta que se le indique, para
#             custodia fuera del equipo. Es la unica forma de sobrevivir a la
#             perdida del equipo: un blob DPAPI LocalMachine muere con la
#             maquina y los datos serian irrecuperables.
#
# USO
#   powershell -ExecutionPolicy Bypass -File gestionar_clave.ps1 -Accion Crear
#   powershell -ExecutionPolicy Bypass -File gestionar_clave.ps1 -Accion Estado
#   powershell -ExecutionPolicy Bypass -File gestionar_clave.ps1 -Accion Ejecutar -Script ..\..\marathon-backend\sql\fase41_cifrado.sql
#   powershell -ExecutionPolicy Bypass -File gestionar_clave.ps1 -Accion Escrow -Destino E:\custodia\clave.txt
# =============================================================================

param(
    [ValidateSet('Crear','Estado','Ejecutar','Escrow','Importar')]
    [string] $Accion = 'Estado',
    [string] $Script,
    [string] $Destino,
    [string] $Base = 'mod_venta_inve',
    # Host y puerto del servidor. Estaban FIJOS a localhost:5432 dentro de la
    # accion Ejecutar, y eso convertia -Base en una promesa falsa: pedir otra
    # base en otro cluster (por ejemplo el de pruebas del puerto 5434) ejecutaba
    # igualmente contra el servidor de produccion del 5432. Se detecto al probar
    # construir_desde_cero.ps1 contra un cluster aparte.
    [string] $PgHost = 'localhost',
    [int]    $PgPort = 5432
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security

$PgBin       = 'C:\Program Files\PostgreSQL\18\bin'
$AlmacenDir  = 'C:\ProgramData\MarathonSports\crypto'
$AlmacenArch = Join-Path $AlmacenDir 'clave.dpapi'
$VarEntorno  = 'MARATHON_CRYPTO_KEY_PROTECTED'

function Protect-Texto {
    param([string] $Texto)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Texto)
    $blob  = [System.Security.Cryptography.ProtectedData]::Protect(
                 $bytes, $null, [System.Security.Cryptography.DataProtectionScope]::LocalMachine)
    return $blob
}

function Unprotect-Texto {
    param([byte[]] $Blob)
    $bytes = [System.Security.Cryptography.ProtectedData]::Unprotect(
                 $Blob, $null, [System.Security.Cryptography.DataProtectionScope]::LocalMachine)
    return [System.Text.Encoding]::UTF8.GetString($bytes)
}

function Get-ClaveEnClaro {
    <#  Devuelve la clave descifrada. El llamador debe tratarla como material
        sensible: no imprimirla, no escribirla, no pasarla por linea de comandos. #>
    if (Test-Path $AlmacenArch) {
        return Unprotect-Texto ([System.IO.File]::ReadAllBytes($AlmacenArch))
    }
    foreach ($ambito in @('Machine','User')) {
        $b64 = [Environment]::GetEnvironmentVariable($VarEntorno, $ambito)
        if ($b64) {
            return Unprotect-Texto ([Convert]::FromBase64String($b64))
        }
    }
    throw "No hay clave de cifrado. Ejecutar primero: gestionar_clave.ps1 -Accion Crear"
}

function Get-Huella {
    <#  Huella SHA256 truncada de la clave. Sirve para comprobar que dos
        entornos usan la MISMA clave sin llegar a mostrarla nunca. #>
    param([string] $Clave)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    $h   = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Clave))
    return ([BitConverter]::ToString($h) -replace '-','').Substring(0,16).ToLower()
}

switch ($Accion) {

  'Crear' {
        if (Test-Path $AlmacenArch) {
            Write-Host "La clave YA EXISTE en $AlmacenArch. No se sobreescribe."
            Write-Host "Sobreescribirla haria ilegibles todos los datos ya cifrados."
            $clave = Get-ClaveEnClaro
            Write-Host "Huella actual: $(Get-Huella $clave)"
            exit 0
        }

        # 32 bytes de un generador criptografico, en base64. No se deriva de
        # ninguna contrasena existente: si se reutilizara la de la base, quien
        # tuviera acceso a la base tendria tambien la clave.
        $bytes = New-Object byte[] 32
        [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
        $clave = [Convert]::ToBase64String($bytes)

        if (-not (Test-Path $AlmacenDir)) { New-Item -ItemType Directory -Path $AlmacenDir -Force | Out-Null }
        $blob = Protect-Texto $clave
        [System.IO.File]::WriteAllBytes($AlmacenArch, $blob)

        # Solo Administradores y SYSTEM pueden leer el blob. Se usan SID y no
        # nombres: este equipo tiene Windows en espanol, donde el grupo se llama
        # "Administradores" y un icacls con "Administrators" falla con "no se
        # efectuo ninguna asignacion entre los nombres de cuenta y los SID".
        # Los SID son invariantes al idioma.
        #
        # La cuenta que EJECUTA el backend tambien tiene que poder leerlo. Sin
        # ella la ACL queda perfecta y la aplicacion no arranca: se probo con
        # solo Administradores+SYSTEM y gestionar_clave.ps1 fallo con "acceso
        # denegado" al no estar la consola elevada.
        icacls $AlmacenArch /inheritance:r `
               /grant:r "*S-1-5-32-544:(R)" "*S-1-5-18:(R)" "$($env:USERDOMAIN)\$($env:USERNAME):(R)" | Out-Null

        # La variable de MAQUINA exige elevacion. Si no la hay, se cae a la
        # variable de USUARIO y se avisa: es una degradacion real del alcance
        # (solo la ve este usuario), no un detalle cosmetico, asi que se dice.
        $ambito = 'Machine'
        try {
            [Environment]::SetEnvironmentVariable($VarEntorno, [Convert]::ToBase64String($blob), 'Machine')
        } catch {
            $ambito = 'User'
            [Environment]::SetEnvironmentVariable($VarEntorno, [Convert]::ToBase64String($blob), 'User')
            Write-Host "AVISO: sin permisos para escribir la variable de MAQUINA (requiere consola elevada)."
            Write-Host "       Se escribio en ambito USUARIO. El blob DPAPI sigue siendo de maquina."
            Write-Host "       Para promoverla, en consola de administrador:"
            Write-Host "       [Environment]::SetEnvironmentVariable('$VarEntorno', [Environment]::GetEnvironmentVariable('$VarEntorno','User'), 'Machine')"
        }

        Write-Host "Clave de cifrado creada."
        Write-Host "  Almacen DPAPI : $AlmacenArch"
        Write-Host "  Variable      : $VarEntorno (ambito $ambito)"
        Write-Host "  Huella        : $(Get-Huella $clave)"
        Write-Host ""
        Write-Host "PENDIENTE MANUAL: exportar una copia de custodia FUERA de este equipo"
        Write-Host "con -Accion Escrow. Sin ella, si el equipo se pierde los datos"
        Write-Host "cifrados son IRRECUPERABLES: DPAPI LocalMachine muere con la maquina."
        $clave = $null
        exit 0
  }

  'Estado' {
        $existeArch = Test-Path $AlmacenArch
        $ambitoVar  = @('Machine','User') |
                      Where-Object { [Environment]::GetEnvironmentVariable($VarEntorno, $_) } |
                      Select-Object -First 1
        $existeVar  = [bool]$ambitoVar
        Write-Host "Almacen DPAPI presente : $existeArch"
        if ($existeVar) { Write-Host "Variable de entorno    : presente (ambito $ambitoVar)" }
        else            { Write-Host "Variable de entorno    : ausente" }
        if ($existeArch -or $existeVar) {
            $clave = Get-ClaveEnClaro
            Write-Host "Huella de la clave     : $(Get-Huella $clave)"
            Write-Host "Longitud               : $($clave.Length) caracteres base64"
            $clave = $null
        }
        exit 0
  }

  'Ejecutar' {
        if (-not $Script) { throw "Falta -Script <ruta.sql>" }
        if (-not (Test-Path $Script)) { throw "No existe el script $Script" }

        $clave = Get-ClaveEnClaro

        # La clave viaja al proceso hijo por VARIABLE DE ENTORNO, no por -v ni
        # por la linea de comandos: los argumentos de un proceso son visibles
        # para cualquiera que liste procesos, una variable de entorno de proceso
        # no. Dentro del .sql se recoge con \getenv.
        $env:MARATHON_CRYPTO_KEY = $clave
        $clave = $null

        # Si el llamante ya trae PGPASSWORD en el entorno, se respeta: es la unica
        # forma de apuntar a un servidor cuya contrasena no es la del .env de este
        # repositorio (un cluster de pruebas, el equipo de un companero). Si no,
        # se cae al .env, que es el caso normal.
        $pwPropia = [bool]$env:PGPASSWORD
        if (-not $pwPropia) {
            $envFile = Join-Path $PSScriptRoot '..\..\.env'
            $pw = $null
            if (Test-Path $envFile) {
                foreach ($l in Get-Content $envFile) {
                    if ($l -match '^\s*PG_SUPERUSER_PASSWORD\s*=\s*(.*)$') { $pw = $matches[1].Trim() }
                }
            }
            if (-not $pw) { throw "No se encontro PG_SUPERUSER_PASSWORD en el .env (ni PGPASSWORD en el entorno)" }
            $env:PGPASSWORD = $pw
        }

        try {
            & (Join-Path $PgBin 'psql.exe') -h $PgHost -p $PgPort -U postgres -d $Base -f $Script
            $codigo = $LASTEXITCODE
        }
        finally {
            Remove-Item Env:\MARATHON_CRYPTO_KEY -ErrorAction SilentlyContinue
            if (-not $pwPropia) { Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue }
        }
        exit $codigo
  }

  'Escrow' {
        if (-not $Destino) { throw "Falta -Destino <ruta fuera de este equipo, p.ej. E:\custodia\clave.txt>" }

        $rutaCompleta = [System.IO.Path]::GetFullPath($Destino)
        $proyecto     = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
        $respaldos    = 'C:\respaldos\marathon'

        # Dos destinos prohibidos, por razones distintas:
        #   - dentro del repositorio: acabaria en un commit
        #   - dentro de los respaldos de datos: la clave viajaria junto al dato
        #     que cifra, y el cifrado dejaria de proteger nada
        if ($rutaCompleta.StartsWith($proyecto, 'OrdinalIgnoreCase')) {
            throw "Destino invalido: esta DENTRO del repositorio. La clave acabaria en un commit."
        }
        if ($rutaCompleta.StartsWith($respaldos, 'OrdinalIgnoreCase')) {
            throw "Destino invalido: esta dentro de $respaldos. La clave no puede viajar con los datos que cifra."
        }

        # Tercer destino prohibido, anadido en la F42: cualquier carpeta
        # sincronizada con OneDrive. En este equipo el Escritorio y Documentos
        # ESTAN dentro de OneDrive, asi que "guardarla en el escritorio" subiria
        # la clave en claro a un servicio de terceros, la replicaria en todos los
        # dispositivos vinculados y la dejaria en el historial de versiones aunque
        # despues se borre el archivo. Es una fuga silenciosa y permanente.
        if ($env:OneDrive -and $rutaCompleta.StartsWith($env:OneDrive, 'OrdinalIgnoreCase')) {
            throw ("Destino invalido: '$rutaCompleta' esta dentro de OneDrive ($env:OneDrive). " +
                   "La clave en claro se sincronizaria a la nube y quedaria en el historial de versiones. " +
                   "Usar una ruta local no sincronizada o una unidad extraible.")
        }

        $clave = Get-ClaveEnClaro
        @"
CLAVE DE CIFRADO — Marathon Sports / mod_venta_inve
====================================================
Generada por gestionar_clave.ps1. Equipo de origen: $env:COMPUTERNAME
Fecha de exportacion: $(Get-Date -Format 'yyyy-MM-dd HH:mm')
Huella (SHA256, 16 hex): $(Get-Huella $clave)

CLAVE:
$clave

QUE HACE
  Cifra cliente.correo/telefono/direccion y proveedor.correo/telefono/
  direccion/contacto mediante pgp_sym_encrypt (pgcrypto).

SI SE PIERDE
  Los datos cifrados son IRRECUPERABLES. No hay puerta trasera, y los
  respaldos de pg_basebackup contienen el dato YA CIFRADO.

COMO SE RESTAURA
  En el equipo nuevo, con este mismo archivo a mano:
      powershell -File gestionar_clave.ps1 -Accion Importar -Destino <ruta a este archivo>
  Eso vuelve a protegerla con DPAPI en el equipo destino y deja la huella de
  arriba. NO usar -Accion Crear: genera una clave NUEVA y los datos ya cifrados
  quedarian ilegibles. Detalle en CIFRADO.md, seccion 6.

CUSTODIA
  Este archivo contiene la clave EN CLARO. Guardarlo fuera del equipo y fuera
  de C:\respaldos\marathon: caja fuerte, gestor de contrasenas o unidad
  extraible bajo llave. Borrar cualquier copia intermedia.
"@ | Set-Content -Path $rutaCompleta -Encoding utf8
        $clave = $null

        # El archivo contiene la clave EN CLARO. Mientras este en disco, que solo
        # pueda leerlo quien lo genero. Se usan SID y no nombres porque este
        # Windows esta en espanol y "Administrators" no existe como tal.
        try {
            icacls $rutaCompleta /inheritance:r `
                   /grant:r "$($env:USERDOMAIN)\$($env:USERNAME):(R,W)" "*S-1-5-32-544:(R)" | Out-Null
        } catch {
            Write-Host "AVISO: no se pudieron restringir los permisos del archivo. Revisarlos a mano."
        }

        Write-Host "Copia de custodia escrita en $rutaCompleta"
        Write-Host "Permisos restringidos a $($env:USERNAME) y Administradores."
        Write-Host ""
        Write-Host "ESTE ARCHIVO ES UN PASO INTERMEDIO, NO EL DESTINO FINAL."
        Write-Host "Contiene la clave EN CLARO. Ahora:"
        Write-Host "  1. Copiar su contenido a un gestor de contrasenas, o el archivo a una"
        Write-Host "     unidad extraible que NO sea la de los respaldos."
        Write-Host "  2. Borrar este archivo del disco."
        Write-Host "Mientras siga aqui, la clave y los datos que cifra estan en el mismo equipo."
        exit 0
  }

  'Importar' {
        <#  Instala en ESTE equipo una clave que YA EXISTE, traida de la custodia.
            Es la contraparte de Escrow y el paso que faltaba para replicar el
            entorno en otra maquina.

            Por que hace falta una accion propia y no vale -Accion Crear: Crear
            GENERA una clave nueva al azar. Si se usa en un equipo donde ya se
            restauro la base, los bytea existentes quedan ilegibles para siempre,
            porque fueron cifrados con la otra. La confusion es facil de cometer
            y el dano es irreversible, asi que se separa en dos verbos.

            DPAPI es por equipo: el blob clave.dpapi del equipo de origen NO se
            puede copiar, hay que volver a proteger el texto de la clave aqui.  #>

        $clave = $null

        if ($Destino) {
            if (-not (Test-Path $Destino)) { throw "No existe el archivo de custodia: $Destino" }
            # Formato del archivo que escribe -Accion Escrow: una linea 'CLAVE:'
            # y debajo la clave sola. Se lee asi y no con un indice de linea fijo
            # para no romperse si la cabecera cambia de tamano.
            $lineas = Get-Content $Destino
            for ($i = 0; $i -lt $lineas.Count; $i++) {
                if ($lineas[$i].Trim() -eq 'CLAVE:') {
                    for ($j = $i + 1; $j -lt $lineas.Count; $j++) {
                        if ($lineas[$j].Trim()) { $clave = $lineas[$j].Trim(); break }
                    }
                    break
                }
            }
            if (-not $clave) { throw "El archivo no tiene una linea 'CLAVE:' seguida de la clave. Revisar que sea el que genero -Accion Escrow." }
            Write-Host "Clave leida de $Destino"
        } else {
            # Sin -Destino se pide por consola. NO se acepta por parametro suelto
            # a proposito: quedaria en el historial de PowerShell en claro.
            Write-Host "Pega la clave de la custodia (no se mostrara en pantalla)."
            $segura = Read-Host -AsSecureString "CLAVE"
            $bstr   = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($segura)
            try     { $clave = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr).Trim() }
            finally { [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
        }

        if (-not $clave) { throw "No se recibio ninguna clave." }

        # Comprobacion de forma: la clave son 32 bytes en base64 (44 caracteres
        # con el relleno). Si lo pegado es media clave o lleva un salto de linea,
        # se detecta AQUI y no tres pasos despues, cuando pgp_sym_decrypt empiece
        # a devolver nulos sin decir por que.
        try { $bytes = [Convert]::FromBase64String($clave) }
        catch { throw "Lo pegado no es base64 valido. Debe ser la linea completa que sigue a 'CLAVE:'." }
        if ($bytes.Length -ne 32) {
            throw "La clave mide $($bytes.Length) bytes y deben ser 32. Probablemente se copio incompleta."
        }

        $huellaNueva = Get-Huella $clave

        # Guarda: si aqui ya hay una clave DISTINTA, sobreescribirla haria
        # ilegible lo que ya este cifrado en esta maquina. Se aborta.
        if (Test-Path $AlmacenArch) {
            $actual = Get-ClaveEnClaro
            $huellaActual = Get-Huella $actual
            $actual = $null
            if ($huellaActual -eq $huellaNueva) {
                Write-Host "Esta misma clave YA esta instalada (huella $huellaActual). No se hace nada."
                $clave = $null
                exit 0
            }
            throw ("ABORTADO: ya hay una clave distinta instalada (huella $huellaActual, " +
                   "la que se importa es $huellaNueva). Sobreescribirla dejaria ilegibles los " +
                   "datos ya cifrados con la actual. Si de verdad hay que reemplazarla, borrar " +
                   "primero $AlmacenArch a conciencia.")
        }

        if (-not (Test-Path $AlmacenDir)) { New-Item -ItemType Directory -Path $AlmacenDir -Force | Out-Null }
        $blob = Protect-Texto $clave
        [System.IO.File]::WriteAllBytes($AlmacenArch, $blob)

        # Misma ACL que en Crear: SID y no nombres, porque este Windows esta en
        # espanol y "Administrators" no existe como cuenta.
        icacls $AlmacenArch /inheritance:r `
               /grant:r "*S-1-5-32-544:(R)" "*S-1-5-18:(R)" "$($env:USERDOMAIN)\$($env:USERNAME):(R)" | Out-Null

        $ambito = 'Machine'
        try {
            [Environment]::SetEnvironmentVariable($VarEntorno, [Convert]::ToBase64String($blob), 'Machine')
        } catch {
            $ambito = 'User'
            [Environment]::SetEnvironmentVariable($VarEntorno, [Convert]::ToBase64String($blob), 'User')
            Write-Host "AVISO: sin permisos para la variable de MAQUINA (requiere consola elevada)."
            Write-Host "       Se escribio en ambito USUARIO: solo la vera este usuario de Windows."
        }

        Write-Host ""
        Write-Host "Clave importada en este equipo."
        Write-Host "  Almacen DPAPI : $AlmacenArch"
        Write-Host "  Variable      : $VarEntorno (ambito $ambito)"
        Write-Host "  Huella        : $huellaNueva"
        Write-Host ""
        Write-Host "COMPROBAR: esta huella debe coincidir con la anotada en la custodia."
        Write-Host "Si no coincide, la clave no es la de esta base y los datos personales"
        Write-Host "seguiran saliendo vacios."
        $clave = $null
        exit 0
  }
}
