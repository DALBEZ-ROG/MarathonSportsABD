package com.marathon.config;

import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtils {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private long expiration;

    private static final long REFRESH_EXPIRATION = 604800000; // 7 días

    private Key getSigningKey() {
        byte[] keyBytes = Base64.getEncoder().encode(secret.getBytes());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(UserDetails userDetails, List<String> roles, List<String> permisos) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roles);
        claims.put("permisos", permisos);
        return buildToken(claims, userDetails.getUsername(), expiration);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails.getUsername(), REFRESH_EXPIRATION);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expirationTime) {
        return Jwts.builder()
                .setClaims(extraClaims)
                // F60 (D-23): identificador unico por token. Sin el, revocar
                // una sesion obligaria a guardar el token entero —es decir, a
                // guardar la credencial— o a invalidar TODAS las sesiones del
                // usuario a la vez. El jti permite nombrar un token concreto
                // sin conservarlo.
                .setId(UUID.randomUUID().toString())
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("roles", List.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractPermisos(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("permisos", List.class);
    }

    /** Identificador unico del token (claim {@code jti}). Ver F60 / D-23. */
    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    /** La expiracion del token como {@code LocalDateTime}, para persistirla. */
    public java.time.LocalDateTime expiracionComoFecha(String token) {
        return extractExpiration(token).toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}
