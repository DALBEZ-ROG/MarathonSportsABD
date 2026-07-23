package com.marathon.config;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
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
                // --- Rutas públicas ---
                .requestMatchers(
                    "/api/auth/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()

                // --- Lectura: cualquier usuario autenticado ---
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

                // --- Maestros (crear/editar/eliminar): solo Administrador ---
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/ciudades", "/api/categorias", "/api/unidades-medida",
                    "/api/proveedores", "/api/productos", "/api/bodegas"
                ).hasAuthority("ROLE_ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.PUT,
                    "/api/ciudades/**", "/api/categorias/**", "/api/unidades-medida/**",
                    "/api/proveedores/**", "/api/productos/**", "/api/bodegas/**"
                ).hasAuthority("ROLE_ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE,
                    "/api/ciudades/**", "/api/categorias/**", "/api/unidades-medida/**",
                    "/api/proveedores/**", "/api/productos/**", "/api/bodegas/**"
                ).hasAuthority("ROLE_ADMINISTRADOR")

                // --- Clientes (crear/editar/eliminar): Administrador u Operador de Pedidos ---
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/clientes"
                ).hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_OPERADOR DE PEDIDOS")
                .requestMatchers(org.springframework.http.HttpMethod.PUT,
                    "/api/clientes/**"
                ).hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_OPERADOR DE PEDIDOS")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE,
                    "/api/clientes/**"
                ).hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_OPERADOR DE PEDIDOS")

                // --- Pedidos (crear): Administrador u Operador de Pedidos ---
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/pedidos"
                ).hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_OPERADOR DE PEDIDOS")

                // --- Cambio de estado de pedido: cualquier usuario autenticado ---
                .requestMatchers(org.springframework.http.HttpMethod.PUT,
                    "/api/pedidos/*/estado"
                ).authenticated()

                // --- Comprobantes (generar/anular): Administrador u Operador de Pedidos para generar,
                //     solo Administrador para anular ---
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/comprobantes/*/generar", "/api/comprobantes/pedido/*/generar"
                ).hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_OPERADOR DE PEDIDOS")
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/comprobantes/*/anular"
                ).hasAuthority("ROLE_ADMINISTRADOR")

                // --- Inventario (movimientos): Administrador u Operador de Bodega ---
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/inventario/movimiento"
                ).hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_OPERADOR DE BODEGA")

                // --- Cambio de contraseña: cualquier usuario autenticado (solo la propia) ---
                .requestMatchers(org.springframework.http.HttpMethod.PUT,
                    "/api/usuarios/*/password"
                ).authenticated()

                // --- Picking: Administrador u Operador de Bodega ---
                .requestMatchers("/api/picking/**")
                    .hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_OPERADOR DE BODEGA")

                // --- Empaque (confirmar): Administrador u Operador de Bodega ---
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/empaque/pedidos/*/confirmar"
                ).hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_OPERADOR DE BODEGA")

                // --- Empaque (consulta): cualquier usuario autenticado ---
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                    "/api/empaque/pedidos", "/api/empaque/**"
                ).authenticated()

                // --- Usuarios, roles y permisos: solo Administrador ---
                .requestMatchers("/api/usuarios/**", "/api/roles/**", "/api/permisos/**")
                    .hasAuthority("ROLE_ADMINISTRADOR")

                // --- Logs y auditoría: solo Administrador ---
                .requestMatchers("/api/logs/**").hasAuthority("ROLE_ADMINISTRADOR")
                .requestMatchers("/api/auditoria/**").hasAuthority("ROLE_ADMINISTRADOR")

                // --- Órdenes de Compra (F21): Encargado de Compras o Administrador ---
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                    "/api/ordenes-compra", "/api/ordenes-compra/**"
                ).hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_ENCARGADO DE COMPRAS")
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/ordenes-compra", "/api/ordenes-compra/**"
                ).hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_ENCARGADO DE COMPRAS")
                .requestMatchers(org.springframework.http.HttpMethod.PUT,
                    "/api/ordenes-compra/**"
                ).hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_ENCARGADO DE COMPRAS")

                // --- Recepción de Mercancía (F22): Encargado de Compras o Administrador ---
                .requestMatchers("/api/recepciones/**")
                    .hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_ENCARGADO DE COMPRAS")

                // --- Materia Prima (F21) ---
                //   Lectura: Encargado de Compras, Encargado de Producción y Administrador
                //   Escritura: Encargado de Producción y Administrador
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                    "/api/materia-prima", "/api/materia-prima/**"
                ).hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_ENCARGADO DE COMPRAS", "ROLE_ENCARGADO DE PRODUCCIÓN")
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/materia-prima", "/api/materia-prima/**"
                ).hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_ENCARGADO DE PRODUCCIÓN")
                .requestMatchers(org.springframework.http.HttpMethod.PUT,
                    "/api/materia-prima/**"
                ).hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_ENCARGADO DE PRODUCCIÓN")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE,
                    "/api/materia-prima/**"
                ).hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_ENCARGADO DE PRODUCCIÓN")

                // --- Dashboard, reportes e IA: Administrador o Supervisor E-Commerce ---
                .requestMatchers("/api/dashboard/**")
                    .hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_SUPERVISOR E-COMMERCE")
                .requestMatchers("/api/reportes/**")
                    .hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_SUPERVISOR E-COMMERCE")
                .requestMatchers("/api/ia/**")
                    .hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_SUPERVISOR E-COMMERCE")

                // --- Resto: autenticado ---
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json; charset=UTF-8");
                    String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                    response.getWriter().write(
                        "{\"status\":401,\"error\":\"Unauthorized\","
                        + "\"message\":\"Debes iniciar sesión para acceder a este recurso\","
                        + "\"timestamp\":\"" + ts + "\"}"
                    );
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json; charset=UTF-8");
                    String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                    response.getWriter().write(
                        "{\"status\":403,\"error\":\"Forbidden\","
                        + "\"message\":\"No tienes permisos para acceder a este recurso\","
                        + "\"timestamp\":\"" + ts + "\"}"
                    );
                })
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
