package com.marathon.config;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.marathon.service.UsuarioDetailsService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          UsuarioDetailsService usuarioDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/auth/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                    "/api/ciudades", "/api/ciudades/**",
                    "/api/categorias", "/api/categorias/**",
                    "/api/unidades-medida", "/api/unidades-medida/**",
                    "/api/proveedores", "/api/proveedores/**",
                    "/api/productos", "/api/productos/**",
                    "/api/bodegas", "/api/bodegas/**",
                    "/api/inventario", "/api/inventario/**",
                    "/api/clientes", "/api/clientes/**",
                    "/api/pedidos", "/api/pedidos/**",
                    "/api/comprobantes", "/api/comprobantes/**"
                ).authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/ciudades", "/api/categorias", "/api/unidades-medida",
                    "/api/proveedores", "/api/productos"
                ).hasRole("ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/bodegas"
                ).hasRole("ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/clientes"
                ).hasAnyRole("ADMINISTRADOR", "OPERADOR_DE_PEDIDOS")
                .requestMatchers(org.springframework.http.HttpMethod.PUT,
                    "/api/clientes/**"
                ).hasAnyRole("ADMINISTRADOR", "OPERADOR_DE_PEDIDOS")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE,
                    "/api/clientes/**"
                ).hasAnyRole("ADMINISTRADOR", "OPERADOR_DE_PEDIDOS")
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/pedidos"
                ).hasAnyRole("ADMINISTRADOR", "OPERADOR_DE_PEDIDOS")
                .requestMatchers(org.springframework.http.HttpMethod.PUT,
                    "/api/pedidos/*/estado"
                ).authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/inventario/movimiento"
                ).hasAnyRole("ADMINISTRADOR", "OPERADOR_BODEGA")
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/comprobantes/*/anular"
                ).hasRole("ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.PUT,
                    "/api/ciudades/**", "/api/categorias/**", "/api/unidades-medida/**",
                    "/api/proveedores/**", "/api/productos/**",
                    "/api/bodegas/**"
                ).hasRole("ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE,
                    "/api/ciudades/**", "/api/categorias/**", "/api/unidades-medida/**",
                    "/api/proveedores/**", "/api/productos/**",
                    "/api/bodegas/**"
                ).hasRole("ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.PUT,
                    "/api/usuarios/*/password"
                ).authenticated()
                .requestMatchers("/api/picking/**")
                    .hasAnyRole("ADMINISTRADOR", "OPERADOR_BODEGA")
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/empaque/pedidos/*/confirmar"
                ).hasAnyRole("ADMINISTRADOR", "OPERADOR_BODEGA")
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                    "/api/empaque/pedidos", "/api/empaque/**"
                ).authenticated()
                .requestMatchers("/api/usuarios/**", "/api/roles/**", "/api/permisos/**")
                    .hasRole("ADMINISTRADOR")
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(Arrays.asList("http://localhost:4200"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
