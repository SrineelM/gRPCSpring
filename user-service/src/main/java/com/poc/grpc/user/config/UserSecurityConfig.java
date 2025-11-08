package com.poc.grpc.user.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configures security settings for the User Service.
 *
 * <p>This class is central to defining the security posture of the application. It enables
 * method-level security, allowing for fine-grained access control on service methods using
 * annotations. It also provides the application with a password encoder bean for securely
 * handling user credentials.
 *
 * <p>The primary security mechanism enabled here is annotation-based. This means that security
 * rules are not defined in a central place but are instead co-located with the methods they protect.
 * For example:
 *
 * <pre>
 * {@code
 * @Secured("ROLE_ADMIN")
 * public void deleteUser(String userId) { ... }
 *
 * @PreAuthorize("#username == authentication.principal.username or hasRole('ROLE_ADMIN')")
 * public void updateUserProfile(String username, ProfileUpdateRequest request) { ... }
 * }
 * </pre>
 *
 * <p>This configuration enables the following annotations for use throughout the application:
 * <ul>
 *   <li>{@code @Secured}: For simple role-based checks (e.g., "@Secured("ROLE_USER")").
 *   <li>{@code @RolesAllowed}: The JSR-250 standard equivalent of {@code @Secured}.
 *   <li>{@code @PreAuthorize} / {@code @PostAuthorize}: For more complex, expression-based access control.
 * </ul>
 */
@Slf4j
// @EnableMethodSecurity enables Spring's method-level security features. This allows you to secure methods
// with annotations like @Secured, @RolesAllowed, and @PreAuthorize.
// - securedEnabled = true: Enables the @Secured annotation for role-based authorization.
// - jsr250Enabled = true: Enables the @RolesAllowed annotation (a JSR-250 standard alternative).
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@Configuration
public class UserSecurityConfig {

  /**
   * Creates a PasswordEncoder bean to be used for encoding and verifying user passwords.
   *
   * <p>Using a strong, adaptive hashing algorithm like BCrypt is a critical security practice
   * for storing passwords. It hashes the password with a randomly generated salt, making it
   * resistant to rainbow table and brute-force attacks.
   *
   * @return A {@link BCryptPasswordEncoder} instance that will be available for dependency injection.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    // The strength parameter (12) controls how much computational work is required to compute the hash.
    // A higher value increases the cost of hashing, making brute-force attacks slower and more expensive.
    // A value of 12 is considered a strong and secure choice for modern systems.
    log.info("Configuring BCrypt password encoder with strength 12");
    return new BCryptPasswordEncoder(12);
  }
}
