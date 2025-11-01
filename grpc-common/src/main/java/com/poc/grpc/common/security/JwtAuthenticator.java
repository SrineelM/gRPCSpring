package com.poc.grpc.common.security;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * A component responsible for authenticating a user based on a JWT string.
 *
 * <p>This class acts as a high-level orchestrator, bridging the gap between low-level token parsing
 * and the application's user data model. It combines the cryptographic validation from {@link
 * JwtUtil} with user retrieval from Spring Security's {@link UserDetailsService} to produce a
 * fully-formed {@code Authentication} object.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.jwt.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnBean(UserDetailsService.class)
public class JwtAuthenticator {

  private final JwtUtil jwtUtil;
  // Injects the standard Spring Security service for loading user-specific data.
  // This decouples JWT logic from how user details (like roles and permissions) are stored.
  private final UserDetailsService userDetailsService;

  /**
   * Validates a JWT and constructs a Spring Security Authentication object if the token is valid
   * and corresponds to an existing user.
   *
   * @param jwt The raw JWT string extracted from the Authorization header.
   * @return An {@link Optional} containing the fully populated {@code Authentication} object if
   *     successful, otherwise an empty {@code Optional}.
   */
  public Optional<Authentication> getAuthentication(String jwt) {
    // Step 1: Perform cryptographic validation on the token.
    // This checks the signature, expiration, and format without hitting the database.
    if (!jwtUtil.validateToken(jwt)) {
      return Optional.empty();
    }

    try {
      // Step 2: If the token is cryptographically valid, extract the username.
      String username = jwtUtil.getUsernameFromToken(jwt);

      // Step 3: Load the user's details from the primary data source (e.g., a database).
      // This is a critical step to ensure the user still exists, is not locked, etc.
      UserDetails userDetails = userDetailsService.loadUserByUsername(username);

      // Step 4: Create a fully authenticated token for the SecurityContext.
      // This is the standard object Spring Security uses to represent the current user's session.
      Authentication authentication =
          new UsernamePasswordAuthenticationToken(
              userDetails, // The principal (the user object itself).
              null, // Credentials (like a password) are not needed post-authentication.
              userDetails.getAuthorities() // The user's roles and permissions.
              );
      log.debug("Successfully authenticated user '{}' from JWT.", username);
      return Optional.of(authentication);

    } catch (Exception e) {
      // This catch block handles errors from both getUsernameFromToken (e.g., parsing errors)
      // and loadUserByUsername (e.g., UserNotFoundException).
      log.error("Failed to create authentication object from JWT: {}", e.getMessage());
      return Optional.empty();
    }
  }
}
