#!/usr/bin/env bash
# =============================================================================
# Comprobación del sistema completo — Fase 94
# -----------------------------------------------------------------------------
# Recorre por HTTP lo que hace cada rol y comprueba DOS cosas a la vez:
#
#   - que responde (código 200 y contenido con sentido),
#   - que responde rápido (por debajo del umbral).
#
# Existe porque optimizar tocando índices, consultas y DTOs puede romper cosas
# en sitios que no se estaban mirando. Esto lo recorre entero después de tocar.
#
#   ./comprobar_sistema.sh
# =============================================================================
set -u

API=${API:-http://localhost:8080/api}
UMBRAL_MS=${UMBRAL_MS:-1000}

BIEN=0; MAL=0; LENTO=0

entrar() {
  curl -s -X POST "$API/auth/login" -H "Content-Type: application/json" \
       -d "{\"correo\":\"$1\",\"password\":\"$2\"}" \
    | sed -n 's/.*"token":"\([^"]*\)".*/\1/p'
}

# comprobar <token> <etiqueta> <ruta> <patron-esperado-en-la-respuesta>
comprobar() {
  local tok=$1 etiqueta=$2 ruta=$3 patron=${4:-}
  local cuerpo cod ms
  cuerpo=$(curl -s -o /tmp/_c.json -w "%{http_code} %{time_total}" -H "Authorization: Bearer $tok" "$API$ruta")
  cod=$(echo "$cuerpo" | cut -d' ' -f1)
  ms=$(awk "BEGIN{printf \"%.0f\", $(echo "$cuerpo" | cut -d' ' -f2)*1000}")

  local marca="ok"
  if [ "$cod" != "200" ]; then
    marca="FALLA (HTTP $cod)"; MAL=$((MAL+1))
  elif [ -n "$patron" ] && ! grep -q "$patron" /tmp/_c.json; then
    marca="FALLA (no trae «$patron»)"; MAL=$((MAL+1))
  elif [ "$ms" -gt "$UMBRAL_MS" ]; then
    marca="LENTO"; LENTO=$((LENTO+1))
  else
    BIEN=$((BIEN+1))
  fi
  printf "  %-46s %6s ms  %s\n" "$etiqueta" "$ms" "$marca"
}

# --- Administrador: lo ve todo -----------------------------------------------
echo "ADMINISTRADOR"
ADMIN=$(entrar "admin@marathon.com" "Admin1234!")
[ -z "$ADMIN" ] && { echo "  no se pudo entrar"; exit 1; }
comprobar "$ADMIN" "Tablero · indicadores"        "/dashboard/kpis"                              '"pedidosPendientes"'
comprobar "$ADMIN" "Tablero · top productos"      "/dashboard/top-productos?limite=5"            'nombreProducto'
comprobar "$ADMIN" "Clientes · filtrar"           "/clientes?page=0&size=10&nombre=maria"        '"content"'
comprobar "$ADMIN" "Productos · filtrar"          "/productos?page=0&size=10&nombre=zapatilla"   '"content"'
comprobar "$ADMIN" "Proveedores · filtrar"        "/proveedores?page=0&size=10&nombre=distribu"  '"content"'
comprobar "$ADMIN" "Usuarios · filtrar"           "/usuarios?page=0&size=10&nombre=a"            '"content"'
comprobar "$ADMIN" "Auditoría · cambios"          "/auditoria/cambios?page=0&size=20"            '"content"'
comprobar "$ADMIN" "Auditoría · rastro"           "/auditoria/rastro?idUsuario=1"                'totalAcciones'
comprobar "$ADMIN" "Auditoría · historial"        "/auditoria/inventario?page=0&size=20"         '"content"'
comprobar "$ADMIN" "Auditoría · log"              "/logs?page=0&size=20"                         '"content"'
comprobar "$ADMIN" "Respaldos · estado"           "/respaldos/estado"                            'palabraBorrado'
comprobar "$ADMIN" "Respaldos · lista"            "/respaldos"                                   ''
comprobar "$ADMIN" "Roles"                        "/roles"                                       ''
comprobar "$ADMIN" "Asistente · estado"           "/ia/estado"                                   'habilitado'

# --- Operador de Pedidos ------------------------------------------------------
echo "OPERADOR DE PEDIDOS"
PED=$(entrar "pedidos@marathon.com" "Demo1234!")
comprobar "$PED" "Pedidos · lista"                "/pedidos?page=0&size=10"                      '"content"'
comprobar "$PED" "Pedidos · buscar por número"    "/pedidos?page=0&size=10&busqueda=PED-1499999" '"content"'
comprobar "$PED" "Pedidos · buscar por cliente"   "/pedidos?page=0&size=10&busqueda=maria"       '"content"'
comprobar "$PED" "Buscador de cliente"            "/clientes/buscar?q=maria&limite=20"           'idCliente'
comprobar "$PED" "Buscador de producto"           "/productos/buscar?q=zapatilla&limite=20"      'idProducto'
comprobar "$PED" "Comprobantes"                   "/comprobantes?page=0&size=10"                 '"content"'

# --- Operador de Bodega -------------------------------------------------------
echo "OPERADOR DE BODEGA"
BOD=$(entrar "bodega@marathon.com" "Demo1234!")
comprobar "$BOD" "Inventario · lista"             "/inventario?page=0&size=10"                   '"content"'
comprobar "$BOD" "Inventario · buscar"            "/inventario?page=0&size=10&busqueda=zapatilla" '"content"'
comprobar "$BOD" "Inventario · bajo mínimo"       "/inventario/stock-bajo/conteo"                 ''
comprobar "$BOD" "Picking · pendientes"           "/picking/pedidos?page=0&size=10"              ''
comprobar "$BOD" "Empaque · listos"               "/empaque/pedidos/listos?page=0&size=10"       ''

# --- Encargado de Compras -----------------------------------------------------
echo "ENCARGADO DE COMPRAS"
COM=$(entrar "compras@marathon.com" "Demo1234!")
comprobar "$COM" "Órdenes de compra · lista"      "/ordenes-compra?page=0&size=10"               '"content"'
comprobar "$COM" "Órdenes · buscar proveedor"     "/ordenes-compra?page=0&size=10&busqueda=distribu" '"content"'
comprobar "$COM" "Cuentas por pagar"              "/cuentas-por-pagar?page=0&size=10"            '"content"'
comprobar "$COM" "Facturas de compra"             "/facturas-compra?page=0&size=10"              '"content"'
comprobar "$COM" "Materia prima · filtrar"        "/materia-prima?page=0&size=10&nombre=a"       '"content"'
comprobar "$COM" "Dev. a proveedor"               "/devoluciones-proveedor?page=0&size=10"       '"content"'

# --- Encargado de Producción --------------------------------------------------
echo "ENCARGADO DE PRODUCCIÓN"
PRO=$(entrar "produccion@marathon.com" "Demo1234!")
comprobar "$PRO" "Producción · lista"             "/ordenes-produccion?page=0&size=10"           '"content"'
comprobar "$PRO" "Producción · tablero"           "/dashboard/manufactura"                       ''
comprobar "$PRO" "Materia prima · bajo mínimo"    "/materia-prima/stock-bajo/conteo"             ''

# --- Supervisor ---------------------------------------------------------------
echo "SUPERVISOR"
SUP=$(entrar "supervisor@marathon.com" "Demo1234!")
comprobar "$SUP" "Análisis del negocio"           "/dashboard/analitica?dias=30"                 ''
comprobar "$SUP" "Ventas por día"                 "/dashboard/ventas-por-dia?dias=30"            ''
comprobar "$SUP" "Devoluciones"                   "/devoluciones?page=0&size=10"                 '"content"'
comprobar "$SUP" "Respaldos · solo mirar"         "/respaldos/estado"                            'disponible'

echo
echo "  $BIEN bien · $LENTO por encima de ${UMBRAL_MS} ms · $MAL fallando"
[ "$MAL" -gt 0 ] && exit 1
exit 0
