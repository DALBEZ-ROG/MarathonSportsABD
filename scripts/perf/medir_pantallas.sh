#!/usr/bin/env bash
# =============================================================================
# Banco de medicion de las pantallas — Fase 94
# -----------------------------------------------------------------------------
# Recorre por HTTP los listados y los FILTROS de cada pantalla, igual que los usa
# una persona, y anota cuanto tarda cada uno.
#
# Existe porque «esta lento» no es una medida. Con esto se sabe QUE pantalla,
# CON QUE filtro y CUANTOS milisegundos, antes y despues de tocar nada.
#
#   ./medir_pantallas.sh            -> mide y escribe la tabla por pantalla
#   ./medir_pantallas.sh antes.txt  -> ademas guarda el resultado en ese fichero
#
# Cada caso se mide DOS veces y se queda con la segunda: la primera paga el
# calentamiento del JIT y de la cache de PostgreSQL, y eso no es lo que sufre
# quien usa el sistema todo el dia.
# =============================================================================
set -u

API=${API:-http://localhost:8080/api}
CORREO=${CORREO:-admin@marathon.com}
CLAVE=${CLAVE:-Admin1234!}
SALIDA=${1:-}

TOKEN=$(curl -s -X POST "$API/auth/login" -H "Content-Type: application/json" \
        -d "{\"correo\":\"$CORREO\",\"password\":\"$CLAVE\"}" \
        | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

if [ -z "$TOKEN" ]; then
  echo "No se pudo entrar como $CORREO. ¿Esta el backend arriba?" >&2
  exit 1
fi

# Cada linea: etiqueta|ruta con parametros
CASOS=$(cat <<'FIN'
Clientes · lista|/clientes?page=0&size=10
Clientes · filtro nombre|/clientes?page=0&size=10&nombre=maria
Clientes · filtro estado|/clientes?page=0&size=10&estado=activo
Clientes · buscador pedido|/clientes/buscar?q=maria%20cedeno&limite=20
Productos · lista|/productos?page=0&size=10
Productos · filtro nombre|/productos?page=0&size=10&nombre=zapatilla
Productos · buscador pedido|/productos/buscar?q=zapatilla%20adidas&limite=20
Proveedores · lista|/proveedores?page=0&size=10
Proveedores · filtro nombre|/proveedores?page=0&size=10&nombre=distribuidora
Usuarios · lista|/usuarios?page=0&size=10
Usuarios · filtro nombre|/usuarios?page=0&size=10&nombre=maria
Pedidos · lista|/pedidos?page=0&size=10
Pedidos · filtro estado|/pedidos?page=0&size=10&estado=pendiente
Pedidos · busqueda|/pedidos?page=0&size=10&busqueda=PED-1499
Pedidos · especiales|/pedidos/especiales?page=0&size=10
Inventario · lista|/inventario?page=0&size=10
Inventario · busqueda|/inventario?page=0&size=10&busqueda=zapatilla
Inventario · stock bajo|/inventario/stock-bajo
Comprobantes · lista|/comprobantes?page=0&size=10
Comprobantes · filtro numero|/comprobantes?page=0&size=10&numero=COMP
Ordenes compra · lista|/ordenes-compra?page=0&size=10
Ordenes compra · filtro estado|/ordenes-compra?page=0&size=10&estado=aprobada
Ordenes compra · busqueda|/ordenes-compra?page=0&size=10&busqueda=OC-
Materia prima · lista|/materia-prima?page=0&size=10
Materia prima · filtro nombre|/materia-prima?page=0&size=10&nombre=tela
Materia prima · stock bajo|/materia-prima/stock-bajo
Produccion · lista|/ordenes-produccion?page=0&size=10
Produccion · filtro estado|/ordenes-produccion?page=0&size=10&estado=planificada
Devoluciones · lista|/devoluciones?page=0&size=10
Devoluciones · filtro estado|/devoluciones?page=0&size=10&estado=pendiente
Dev. proveedor · lista|/devoluciones-proveedor?page=0&size=10
Facturas compra · lista|/facturas-compra?page=0&size=10
Cuentas por pagar · lista|/cuentas-por-pagar?page=0&size=10
Cuentas por pagar · filtro estado|/cuentas-por-pagar?page=0&size=10&estado=pendiente
Auditoria · cambios|/auditoria/cambios?page=0&size=20
Auditoria · cambios filtrados|/auditoria/cambios?page=0&size=20&tabla=cliente&operacion=UPDATE
Auditoria · historial inventario|/auditoria/inventario?page=0&size=20
Auditoria · log acciones|/logs?page=0&size=20
Auditoria · log por modulo|/logs?page=0&size=20&modulo=pedidos
Tablero · resumen|/dashboard/resumen
Tablero · kpis|/dashboard/kpis
Tablero · ventas por dia|/dashboard/ventas-por-dia?dias=30
Tablero · top productos|/dashboard/top-productos?limite=10
Tablero · pedidos por estado|/dashboard/pedidos-por-estado
Picking · pendientes|/picking/pedidos?page=0&size=10
Empaque · listos|/empaque/pedidos/listos?page=0&size=10
FIN
)

printf "%-42s %10s  %s\n" "PANTALLA / FILTRO" "TIEMPO" "HTTP"
printf "%-42s %10s  %s\n" "------------------------------------------" "----------" "----"

TOTAL=0
LENTOS=0
while IFS='|' read -r etiqueta ruta; do
  [ -z "$etiqueta" ] && continue
  # Primera pasada: calentar. Se descarta.
  curl -s -o /dev/null -H "Authorization: Bearer $TOKEN" "$API$ruta" >/dev/null 2>&1
  # Segunda: la que cuenta.
  linea=$(curl -s -o /dev/null -w "%{time_total} %{http_code}" \
          -H "Authorization: Bearer $TOKEN" "$API$ruta" 2>/dev/null)
  seg=$(echo "$linea" | cut -d' ' -f1)
  cod=$(echo "$linea" | cut -d' ' -f2)
  ms=$(awk "BEGIN{printf \"%.0f\", $seg*1000}")
  marca=""
  # 1000 ms es el umbral a partir del cual una pantalla se siente rota.
  if [ "$ms" -gt 1000 ]; then marca=" <-- LENTO"; LENTOS=$((LENTOS+1)); fi
  TOTAL=$((TOTAL+1))
  printf "%-42s %8s ms  %s%s\n" "$etiqueta" "$ms" "$cod" "$marca"
done <<< "$CASOS" | tee "${SALIDA:-/dev/null}"

echo
echo "($TOTAL casos medidos)"
