package com.poc.grpc.common.security;

import com.poc.grpc.common.exception.JwtGenerationException;
import com.poc.grpc.common.exception.JwtValidationException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import java.security.Key;
import java.util.*;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Utility class for handling JWT (JSON Web Token) operations.
 *
 * <p>This class provides comprehensive JWT functionality including:
 *
 * <ul>
 *   <li>Token generation with user authentication and role claims
 *   <li>Cryptographic validation using HMAC-SHA512 signatures
 *   <li>Claims extraction (username, roles, authorities)
 *   <li>Token expiration management
 *   <li>Integration with Spring Security Authentication
 * </ul>
 *
 * <p><strong>Security Considerations:</strong>
 *
 * <ul>
 *   <li>Secret key must be Base64-encoded and minimum 256 bits
 *   <li>Tokens include issuer and audience validation
 *   <li>All validation errors are logged for audit trails
 *   <li>Thread-safe for concurrent token operations
 * </ul>
 *
 * <p><strong>Token Structure:</strong>
 *
 * <pre>
 * Header:  {"alg": "HS512", "typ": "JWT"}
 * Payload: {"sub": "username", "iss": "issuer", "aud": "audience",
 *           "iat": timestamp, "exp": timestamp, "roles": ["ROLE_USER"]}
 * Signature: HMACSHA512(base64(header) + "." + base64(payload), secret)
 * </pre>
 *
 * @author gRPC Spring Team
 * @since 1.0.0
 * @see io.jsonwebtoken.Jwts
 * @see org.springframework.security.core.Authentication
 */
@Slf4j
@Component
public class JwtUtil {
  @Value("${app.jwt.secret}")
  private String jwtSecret;

  @Value("${app.jwt.issuer}")
  private String jwtIssuer;

  @Value("${app.jwt.audience}")
  private String jwtAudience;

  @Value("${app.jwt.expiration}")
  private int jwtExpirationMs;

  private Key key;

  private static final String ROLES_CLAIM = "roles";

  /** Initializes the JWT utility by creating the signing key from the configured secret. */
  @PostConstruct
  public void init() {
    log.info(
        "Initializing JWT utility with issuer: {}, audience: {}, expiration: {} ms",
        jwtIssuer,
        jwtAudience,
        jwtExpirationMs);
    try {
      byte[] keyBytes = Decoders.BASE64.decode(this.jwtSecret);
      this.key = Keys.hmacShaKeyFor(keyBytes);
      log.debug("JWT signing key initialized successfully");
    } catch (Exception e) {
      log.error("Failed to initialize JWT signing key", e);
      throw new IllegalStateException("Could not initialize JWT signing key", e);
    }
  }

  /**
   * Generates a JWT token for the given authentication.
   *
   * @param authentication The authentication object containing user details.
   * @return The generated JWT token.
   * @throws JwtGenerationException If an error occurs during token generation.
   */
  public String generateToken(Authentication authentication) {
    log.debug("Generating JWT token for user: {}", authentication.getName());
    try {
      String username = authentication.getName();
      Date now = new Date();
      Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

      List<String> roles =
          authentication.getAuthorities().stream()
              .map(GrantedAuthority::getAuthority)
              .collect(Collectors.toList());

      String token =
          Jwts.builder()
              .subject(username)
              .issuer(jwtIssuer)
              .audience()
              .add(jwtAudience)
              .and()
              .issuedAt(now)
              .expiration(expiryDate)
              .claim(ROLES_CLAIM, roles)
              .signWith((SecretKey) key, Jwts.SIG.HS512)
              .compact();

      log.info("JWT token generated successfully for user: {}", username);
      log.debug("Token expiration set to: {}", expiryDate);
      return token;
    } catch (Exception e) {
      log.error("Failed to generate JWT token for user: {}", authentication.getName(), e);
      throw new JwtGenerationException("Could not generate token", e);
    }
  }

  /**
   * Extracts the username from the given JWT token.
   *
   * @param token The JWT token.
   * @return The username.
   * @throws JwtValidationException If an error occurs during token parsing.
   */
  public String getUsernameFromToken(String token) {
    try {
      Claims claims = parseToken(token);
      String username = claims.getSubject();
      log.debug("Username extracted from JWT token: {}", username);
      return username;
    } catch (Exception e) {
      log.error("Failed to extract username from token", e);
      throw new JwtValidationException("Could not extract username from token", e);
    }
  }

  /**
   * Extracts the authorities from the given JWT token.
   *
   * @param token The JWT token.
   * @return A collection of granted authorities.
   */
  public Collection<? extends GrantedAuthority> getAuthoritiesFromToken(String token) {
    try {
      Claims claims = parseToken(token);
      Object rolesObj = claims.get(ROLES_CLAIM);
      if (rolesObj instanceof List<?> roleList) {
        return roleList.stream()
            .map(Object::toString)
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
      } else if (rolesObj instanceof String roleStr) {
        return List.of(new SimpleGrantedAuthority(roleStr));
      }
      return Collections.emptyList();
    } catch (Exception e) {
      log.error("Failed to extract authorities from token", e);
      return Collections.emptyList();
    }
  }

  /**
   * Validates the given JWT token.
   *
   * @param token The JWT token.
   * @return True if the token is valid, false otherwise.
   */
  public boolean validateToken(String token) {
    try {
      parseToken(token);
      log.debug("JWT token validated successfully");
      return true;
    } catch (SecurityException | SignatureException ex) {
      log.error("Invalid JWT signature: {}", ex.getMessage());
    } catch (MalformedJwtException ex) {
      log.error("Invalid JWT token: {}", ex.getMessage());
    } catch (ExpiredJwtException ex) {
      log.error("Expired JWT token: {}", ex.getMessage());
    } catch (UnsupportedJwtException ex) {
      log.error("Unsupported JWT token: {}", ex.getMessage());
    } catch (IllegalArgumentException ex) {
      log.error("JWT claims string is empty: {}", ex.getMessage());
    } catch (JwtException ex) {
      log.error("General JWT validation error: {}", ex.getMessage());
    }
    return false;
  }

  /**
   * Creates an {@link Authentication} object from the given JWT token.
   *
   * @param token The JWT token.
   * @return An optional containing the authentication object, or empty if the token is invalid.
   */
  public Optional<Authentication> getAuthentication(String token) {
    try {
      Claims claims = parseToken(token);
      String username = claims.getSubject();
      List<GrantedAuthority> authorities = new ArrayList<>(getAuthoritiesFromToken(token));
      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken(username, null, authorities);
      log.debug("Created authentication token for user: {} with roles: {}", username, authorities);
      return Optional.of(auth);
    } catch (Exception e) {
      log.error("Failed to get authentication from token", e);
      return Optional.empty();
    }
  }

  /**
   * Parses the given JWT token and returns the claims.
   *
   * @param token The JWT token.
   * @return The claims from the token.
   */
  private Claims parseToken(String token) {
    return Jwts.parser()
        .verifyWith((SecretKey) key)
        .requireIssuer(jwtIssuer)
        .requireAudience(jwtAudience)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}
