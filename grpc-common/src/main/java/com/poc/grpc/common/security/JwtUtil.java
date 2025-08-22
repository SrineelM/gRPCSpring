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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Utility class for handling JWT tokens.
 *
 * <p>This class provides methods for generating, validating, and parsing JWT tokens. It is used to
 * secure the gRPC communication between services.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Token generation with configurable claims
 *   <li>Token validation with proper error handling
 *   <li>Token parsing and authentication creation
 *   <li>Token blacklisting for revocation
 *   <li>Automatic cleanup of expired blacklisted tokens
 * </ul>
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

  @Value("${app.jwt.blacklist.cleanup.interval:300000}")
  private long blacklistCleanupIntervalMs;

  // Thread-safe map to store blacklisted tokens with their expiration timestamps
  private final Map<String, Date> blacklistedTokens = new ConcurrentHashMap<>();

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

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

      // Schedule periodic cleanup of expired blacklisted tokens
      scheduler.scheduleAtFixedRate(
          this::cleanupBlacklistedTokens,
          blacklistCleanupIntervalMs,
          blacklistCleanupIntervalMs,
          TimeUnit.MILLISECONDS);
      log.info("Scheduled blacklisted token cleanup every {} ms", blacklistCleanupIntervalMs);
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
      String tokenId = UUID.randomUUID().toString();
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
              .id(tokenId)
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
  @Cacheable(value = "jwtUsername", key = "#token.hashCode()")
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
  @Cacheable(value = "jwtAuthorities", key = "#token.hashCode()")
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
  @Cacheable(value = "jwtValidation", key = "#token.hashCode()")
  public boolean validateToken(String token) {
    try {
      // Check if token is blacklisted
      if (isTokenBlacklisted(token)) {
        log.warn("Token is blacklisted and cannot be used");
        return false;
      }

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
      // Check if token is blacklisted
      if (isTokenBlacklisted(token)) {
        log.warn("Token is blacklisted and cannot be used for authentication");
        return Optional.empty();
      }

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
   * Blacklists a token to prevent its further use.
   *
   * @param token The JWT token to blacklist.
   * @return true if the token was successfully blacklisted, false otherwise.
   */
  public boolean blacklistToken(String token) {
    try {
      Claims claims = parseToken(token);
      Date expiration = claims.getExpiration();

      // Only blacklist if the token is not already expired
      if (expiration.after(new Date())) {
        blacklistedTokens.put(token, expiration);
        log.info("Token for user '{}' blacklisted until {}", claims.getSubject(), expiration);
        return true;
      } else {
        log.debug("Token is already expired, no need to blacklist");
        return false;
      }
    } catch (Exception e) {
      log.error("Failed to blacklist token", e);
      return false;
    }
  }

  /**
   * Checks if a token is blacklisted.
   *
   * @param token The JWT token to check.
   * @return true if the token is blacklisted, false otherwise.
   */
  public boolean isTokenBlacklisted(String token) {
    return blacklistedTokens.containsKey(token);
  }

  /**
   * Checks if a JWT token is expired.
   *
   * @param token The JWT token to check.
   * @return true if the token is expired, false otherwise.
   */
  public boolean isTokenExpired(String token) {
    try {
      Claims claims = parseToken(token);
      Date expiration = claims.getExpiration();
      Date now = new Date();
      boolean isExpired = expiration.before(now);

      if (isExpired) {
        log.debug("Token for user '{}' is expired since {}", claims.getSubject(), expiration);
      }

      return isExpired;
    } catch (ExpiredJwtException ex) {
      log.debug("Token is already expired: {}", ex.getMessage());
      return true;
    } catch (Exception e) {
      log.error("Error checking token expiration", e);
      return true; // Assuming expired for safety if there's an error
    }
  }

  /** Cleans up expired blacklisted tokens. */
  private void cleanupBlacklistedTokens() {
    try {
      int initialSize = blacklistedTokens.size();
      Date now = new Date();

      blacklistedTokens.entrySet().removeIf(entry -> entry.getValue().before(now));

      int removed = initialSize - blacklistedTokens.size();
      if (removed > 0) {
        log.info("Cleaned up {} expired blacklisted tokens", removed);
      }
    } catch (Exception e) {
      log.error("Error during blacklisted token cleanup", e);
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
