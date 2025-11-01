package com.poc.grpc.user.security;

import com.poc.grpc.user.entity.User;
import com.poc.grpc.user.repository.UserRepository;
import java.util.Collections;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Implements the {@link UserDetailsService} interface to integrate with Spring Security.
 *
 * <p>This service is responsible for loading a user's details from the database given a username.
 * It is a core component of the authentication process, providing the necessary user information
 * for credential validation.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {
  private final UserRepository userRepository;

  /**
   * Constructs a new CustomUserDetailsService with the specified UserRepository.
   *
   * @param userRepository The repository for accessing user data.
   */
  public CustomUserDetailsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  /**
   * Loads the user by their username.
   *
   * @param username The username of the user to load.
   * @return A {@link UserDetails} object containing the user's information.
   * @throws UsernameNotFoundException if the user could not be found.
   */
  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user =
        userRepository
            .findByUsernameWithProfile(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
        .password(user.getPassword())
        .authorities(Collections.singletonList(() -> "ROLE_USER"))
        .accountLocked(user.isAccountLocked())
        .disabled(!user.isActive())
        .build();
  }
}
