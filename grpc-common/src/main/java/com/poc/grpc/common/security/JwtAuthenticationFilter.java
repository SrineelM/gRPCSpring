/**
 * A servlet filter responsible for JWT-based authentication.
 *
 * <p>This filter is a key component of the security mechanism, designed to intercept all incoming
 * HTTP requests. It extends {@link OncePerRequestFilter} to ensure it is executed only once for
 * each request, which is crucial for efficiency and correctness in a servlet container environment.
 *
 * <p>BEGINNER NOTE: This is only for HTTP (REST) endpoints, not for gRPC. If your service is
 * gRPC-only, you may not need this filter.
 *
 * <p>The primary responsibilities of this filter are:
 *
 * <ul>
 *   <li>To extract the JSON Web Token (JWT) from the {@code Authorization} header of the incoming
 *       request.
 *   <li>To validate the extracted JWT, ensuring it is correctly signed, not expired, and has a
 *       valid format.
 *   <li>Upon successful validation, to create an authentication object and set it in the {@link
 *       org.springframework.security.core.context.SecurityContextHolder}, effectively
 *       authenticating the user for the duration of the request.
 * </ul>
 *
 * <p>This filter is conditionally enabled based on the application property {@code
 * app.jwt.enabled}. If this property is set to {@code true}, the filter will be active; otherwise,
 * it will be disabled, allowing for flexible security configurations.
 */
package com.poc.grpc.common.security;

import com.poc.grpc.common.exception.JwtValidationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.jwt.enabled", havingValue = "true", matchIfMissing = false)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;

  @Autowired(required = false)
  private UserDetailsService userDetailsService;

  /**
   * Core logic for JWT authentication, executed for each incoming request.
   *
   * <p>This method orchestrates the entire authentication process for a given request. It first
   * attempts to extract a JWT. If a token is found and there is no existing authentication in the
   * security context, it proceeds with validation.
   *
   * <p>If the token is valid, it retrieves the username. If a {@link UserDetailsService} is
   * configured, it's used to load the full user details, creating a comprehensive authentication
   * object. If not, it falls back to using the information present in the token itself (username
   * and authorities).
   *
   * <p>The resulting authentication token is then enriched with details from the web request (like
   * IP address and session ID) and stored in the {@link SecurityContextHolder}.
   *
   * <p>Error handling is a critical part of this method. If JWT validation fails (e.g., due to an
   * expired token or invalid signature), a warning is logged, and the security context is cleared.
   * Any other unexpected exceptions are caught and logged as errors to prevent security context
   * leakage.
   *
   * <p>Finally, it ensures that the request is passed along the filter chain, regardless of the
   * authentication outcome, allowing other security mechanisms and the application logic to
   * proceed.
   *
   * @param request The incoming {@link HttpServletRequest}.
   * @param response The outgoing {@link HttpServletResponse}.
   * @param filterChain The chain of filters to pass the request to.
   * @throws ServletException If an error occurs during servlet processing.
   * @throws IOException If an I/O error occurs.
   */
  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    final String jwt = getJwtFromRequest(request);

    // If there's no JWT or if the user is already authenticated, continue the filter chain without
    // processing.
    if (!StringUtils.hasText(jwt)
        || SecurityContextHolder.getContext().getAuthentication() != null) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      String username = jwtUtil.getUsernameFromToken(jwt);
      UsernamePasswordAuthenticationToken authentication;

      // If a UserDetailsService is available, use it to load full user details.
      // This is the standard and more secure approach.
      if (userDetailsService != null) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        authentication =
            new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
      } else {
        // As a fallback, create an authentication object directly from the token's claims.
        // This is useful in scenarios where a full user lookup is not necessary or possible.
        var authorities = jwtUtil.getAuthoritiesFromToken(jwt);
        authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
      }

      // Enhance the authentication object with details from the request.
      authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      // Set the authentication in the security context.
      SecurityContextHolder.getContext().setAuthentication(authentication);
      log.debug("Successfully authenticated user '{}' and set security context.", username);

    } catch (JwtValidationException e) {
      // Log validation failures as warnings. This is expected for invalid tokens.
      log.warn(
          "JWT validation failed for request to '{}': {}", request.getRequestURI(), e.getMessage());
      // Ensure the security context is cleared in case of a validation failure.
      SecurityContextHolder.clearContext();
    } catch (Exception e) {
      // Log any other unexpected errors during authentication.
      log.error("An unexpected error occurred during JWT authentication.", e);
      // Clear the context to prevent a partially authenticated state.
      SecurityContextHolder.clearContext();
    }

    // Continue the filter chain.
    filterChain.doFilter(request, response);
  }

  /**
   * Extracts the JWT from the {@code Authorization} header of the request.
   *
   * <p>This method specifically looks for a header in the format: {@code Authorization: Bearer
   * <token>}. It performs a case-sensitive check for the "Bearer " prefix. If the header is present
   * and correctly formatted, it returns the token part of the string.
   *
   * @param request The HTTP request from which to extract the token.
   * @return The JWT as a string, or {@code null} if the header is missing, empty, or not in the
   *     expected format.
   */
  private String getJwtFromRequest(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }
    return null;
  }
}
