package com.poc.grpc.common.security.core;

import com.poc.grpc.common.exception.JwtValidationException;
import com.poc.grpc.common.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * Authenticator for JWT tokens that validates tokens and creates Authentication objects. Enhanced
 * with caching, detailed error logging, and integration with UserDetailsService.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticator {

  private final JwtUtil jwtUtil;
  private final CacheManager cacheManager;
  private final UserDetailsService userDetailsService;

  private static final String AUTH_CACHE_NAME = "jwtAuthCache";

  /**
   * Authenticate a JWT token and return an Authentication object if valid. Uses caching to improve
   * performance for frequently authenticated tokens.
   *
   * @param token JWT token to authenticate
   * @return Optional containing Authentication if valid, empty otherwise
   */
  public Optional<Authentication> authenticate(String token) {
    if (token == null) {
      log.debug("Token is null, authentication failed");
      return Optional.empty();
    }

    // Try to get from cache first
    Cache cache = cacheManager.getCache(AUTH_CACHE_NAME);
    if (cache != null) {
      Authentication cachedAuth = cache.get(token, Authentication.class);
      if (cachedAuth != null) {
        log.debug("Authentication found in cache for token");
        return Optional.of(cachedAuth);
      }
    }

    try {
      // Validate token
      if (!jwtUtil.validateToken(token)) {
        log.debug("Token validation failed");
        return Optional.empty();
      }

      // Get username and authorities
      String username = jwtUtil.getUsernameFromToken(token);
      Collection<? extends org.springframework.security.core.GrantedAuthority> rawAuthorities =
          jwtUtil.getAuthoritiesFromToken(token);
      Collection<SimpleGrantedAuthority> authorities =
          rawAuthorities.stream()
              .map(a -> new SimpleGrantedAuthority(a.getAuthority()))
              .collect(Collectors.toList());

      // Load user details if UserDetailsService is available
      try {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        Authentication auth =
            new UsernamePasswordAuthenticationToken(userDetails, null, authorities);

        // Cache the authentication
        if (cache != null) {
          cache.put(token, auth);
          log.debug("Authentication cached for token");
        }

        return Optional.of(auth);
      } catch (Exception e) {
        log.warn("Failed to load user details for {}: {}", username, e.getMessage());

        // Fallback to simple authentication without UserDetails
        User user = new User(username, "", authorities);
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, authorities);

        // Cache the authentication
        if (cache != null) {
          cache.put(token, auth);
        }

        return Optional.of(auth);
      }

    } catch (ExpiredJwtException e) {
      Claims claims = e.getClaims();
      Date expiration = claims.getExpiration();
      Date issuedAt = claims.getIssuedAt();

      log.warn(
          "Token expired for subject: {}, issued at: {}, expiration: {}",
          claims.getSubject(),
          issuedAt,
          expiration);

      return Optional.empty();

    } catch (JwtException e) {
      log.warn("JWT validation error: {}", e.getMessage());
      return Optional.empty();

    } catch (Exception e) {
      log.error(
          "Authentication error: {}, Token: {}",
          e.getMessage(),
          token.substring(0, Math.min(10, token.length())) + "...",
          e);

      return Optional.empty();
    }
  }

  /**
   * Validate JWT token without creating an Authentication object.
   *
   * @param token JWT token to validate
   * @return true if valid, false otherwise
   */
  public boolean validateToken(String token) {
    try {
      return jwtUtil.validateToken(token);
    } catch (JwtValidationException e) {
      log.debug("Token validation failed: {}", e.getMessage());
      return false;
    } catch (Exception e) {
      log.error("Unexpected error during token validation", e);
      return false;
    }
  }

  /**
   * Extract username from JWT token.
   *
   * @param token JWT token
   * @return username from token
   * @throws JwtValidationException if token is invalid
   */
  public String getUsernameFromToken(String token) {
    return jwtUtil.getUsernameFromToken(token);
  }

  /**
   * Extract authorities from JWT token.
   *
   * @param token JWT token
   * @return collection of authorities from token
   * @throws JwtValidationException if token is invalid
   */
  public Collection<SimpleGrantedAuthority> getAuthoritiesFromToken(String token) {
    Collection<? extends org.springframework.security.core.GrantedAuthority> rawAuthorities =
        jwtUtil.getAuthoritiesFromToken(token);
    return rawAuthorities.stream()
        .map(a -> new SimpleGrantedAuthority(a.getAuthority()))
        .collect(Collectors.toList());
  }
} // ...existing code...
// Enhanced JwtAuthenticator implementation from the plan
// ...full code from the markdown attachment...
