package com.marathon.dto.dashboard;

import java.math.BigDecimal;

public class DashboardKpisDTO {

    private Long pedidosPendientes;
    private Long pedidosProcesados;
    private Long pedidosEnviados;
    private Long pedidosEntregados;
    private Long pedidosAnulados;
    private Long pedidosHoy;
    private BigDecimal totalVentasHoy;
    private BigDecimal totalVentasMes;
    private Long productosStockBajo;
    private Long pedidosEspecialesActivos;
    private Long pedidosPickingPendiente;
    private Long productosFabricados;
    private Long ordenesProduccionEnProceso;
    private BigDecimal costoPromedioProduccionMes;

    public DashboardKpisDTO() {}

    public DashboardKpisDTO(Long pedidosPendientes, Long pedidosProcesados, Long pedidosEnviados,
                            Long pedidosEntregados, Long pedidosAnulados, Long pedidosHoy,
                            BigDecimal totalVentasHoy, BigDecimal totalVentasMes,
                            Long productosStockBajo, Long pedidosEspecialesActivos,
                            Long pedidosPickingPendiente) {
        this.pedidosPendientes = pedidosPendientes;
        this.pedidosProcesados = pedidosProcesados;
        this.pedidosEnviados = pedidosEnviados;
        this.pedidosEntregados = pedidosEntregados;
        this.pedidosAnulados = pedidosAnulados;
        this.pedidosHoy = pedidosHoy;
        this.totalVentasHoy = totalVentasHoy;
        this.totalVentasMes = totalVentasMes;
        this.productosStockBajo = productosStockBajo;
        this.pedidosEspecialesActivos = pedidosEspecialesActivos;
        this.pedidosPickingPendiente = pedidosPickingPendiente;
    }

    public Long getPedidosPendientes() { return pedidosPendientes; }
    public void setPedidosPendientes(Long pedidosPendientes) { this.pedidosPendientes = pedidosPendientes; }

    public Long getPedidosProcesados() { return pedidosProcesados; }
    public void setPedidosProcesados(Long pedidosProcesados) { this.pedidosProcesados = pedidosProcesados; }

    public Long getPedidosEnviados() { return pedidosEnviados; }
    public void setPedidosEnviados(Long pedidosEnviados) { this.pedidosEnviados = pedidosEnviados; }

    public Long getPedidosEntregados() { return pedidosEntregados; }
    public void setPedidosEntregados(Long pedidosEntregados) { this.pedidosEntregados = pedidosEntregados; }

    public Long getPedidosAnulados() { return pedidosAnulados; }
    public void setPedidosAnulados(Long pedidosAnulados) { this.pedidosAnulados = pedidosAnulados; }

    public Long getPedidosHoy() { return pedidosHoy; }
    public void setPedidosHoy(Long pedidosHoy) { this.pedidosHoy = pedidosHoy; }

    public BigDecimal getTotalVentasHoy() { return totalVentasHoy; }
    public void setTotalVentasHoy(BigDecimal totalVentasHoy) { this.totalVentasHoy = totalVentasHoy; }

    public BigDecimal getTotalVentasMes() { return totalVentasMes; }
    public void setTotalVentasMes(BigDecimal totalVentasMes) { this.totalVentasMes = totalVentasMes; }

    public Long getProductosStockBajo() { return productosStockBajo; }
    public void setProductosStockBajo(Long productosStockBajo) { this.productosStockBajo = productosStockBajo; }

    public Long getPedidosEspecialesActivos() { return pedidosEspecialesActivos; }
    public void setPedidosEspecialesActivos(Long pedidosEspecialesActivos) { this.pedidosEspecialesActivos = pedidosEspecialesActivos; }

    public Long getPedidosPickingPendiente() { return pedidosPickingPendiente; }
    public void setPedidosPickingPendiente(Long pedidosPickingPendiente) { this.pedidosPickingPendiente = pedidosPickingPendiente; }

    public Long getProductosFabricados() { return productosFabricados; }
    public void setProductosFabricados(Long productosFabricados) { this.productosFabricados = productosFabricados; }

    public Long getOrdenesProduccionEnProceso() { return ordenesProduccionEnProceso; }
    public void setOrdenesProduccionEnProceso(Long ordenesProduccionEnProceso) { this.ordenesProduccionEnProceso = ordenesProduccionEnProceso; }

    public BigDecimal getCostoPromedioProduccionMes() { return costoPromedioProduccionMes; }
    public void setCostoPromedioProduccionMes(BigDecimal costoPromedioProduccionMes) { this.costoPromedioProduccionMes = costoPromedioProduccionMes; }
}
