package com.marathon.dto.ia;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class IAResponseDTO {

    private String pregunta;
    private String sql;
    private String explicacion;
    private List<Map<String, Object>> resultados;
    private Integer totalResultados;
    private String error;
    private LocalDateTime timestamp;

    public IAResponseDTO() {}

    public String getPregunta() { return pregunta; }
    public void setPregunta(String pregunta) { this.pregunta = pregunta; }

    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }

    public String getExplicacion() { return explicacion; }
    public void setExplicacion(String explicacion) { this.explicacion = explicacion; }

    public List<Map<String, Object>> getResultados() { return resultados; }
    public void setResultados(List<Map<String, Object>> resultados) { this.resultados = resultados; }

    public Integer getTotalResultados() { return totalResultados; }
    public void setTotalResultados(Integer totalResultados) { this.totalResultados = totalResultados; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
