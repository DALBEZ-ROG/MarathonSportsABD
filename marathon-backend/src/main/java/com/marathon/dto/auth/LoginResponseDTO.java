package com.marathon.dto.auth;

import java.util.List;

public class LoginResponseDTO {

    private String token;
    private String refreshToken;
    private String tipo;
    private Integer idUsuario;
    private String nombre;
    private String apellido;
    private String correo;
    private String rol;
    private List<String> permisos;

    /**
     * Instante en que caduca la sesion, en milisegundos desde 1970.
     *
     * <p>Existe por la F60 (D-27): el token pasa a una cookie HttpOnly, asi que
     * el navegador YA NO PUEDE leerlo para mirar cuando expira —era lo que hacia
     * antes, descifrando el payload del JWT a mano—. La caducidad no es un
     * secreto, asi que se la damos aparte y el front la usa para saber cuando
     * mandar al usuario a la pantalla de entrada sin esperar a comerse un 401.
     */
    private Long expiraEn;

    public LoginResponseDTO() {
        this.tipo = "Bearer";
    }

    public LoginResponseDTO(String token, String refreshToken, Integer idUsuario,
                            String nombre, String apellido, String correo,
                            String rol, List<String> permisos) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.tipo = "Bearer";
        this.idUsuario = idUsuario;
        this.nombre = nombre;
        this.apellido = apellido;
        this.correo = correo;
        this.rol = rol;
        this.permisos = permisos;
    }

    public Long getExpiraEn() { return expiraEn; }
    public void setExpiraEn(Long expiraEn) { this.expiraEn = expiraEn; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    public List<String> getPermisos() { return permisos; }
    public void setPermisos(List<String> permisos) { this.permisos = permisos; }
}
