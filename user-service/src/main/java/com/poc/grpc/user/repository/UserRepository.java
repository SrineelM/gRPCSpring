package com.poc.grpc.user.repository;

import com.poc.grpc.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for User Entity
 *
 * <p>This repository provides data access methods for the User entity. It extends JpaRepository for
 * basic CRUD operations and adds custom query methods for specific business requirements.
 *
 * <p>Features: 1. Basic CRUD operations (inherited from JpaRepository) 2. Custom queries for user
 * management 3. Profile-related queries 4. Security-related queries
 *
 * <p>Usage: - Used by UserService for data persistence - Supports transaction management - Provides
 * optimistic locking via @Version
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  /**
   * Finds a user by username, including their profile. Used for authentication and profile
   * management.
   *
   * @param username The username to search for
   * @return Optional containing the user with profile, or empty if not found
   */
  @Query("SELECT u FROM User u LEFT JOIN FETCH u.profile WHERE u.username = :username")
  Optional<User> findByUsernameWithProfile(@Param("username") String username);

  /**
   * Finds a user by email, including their profile. Used for email-based operations and
   * verification.
   *
   * @param email The email to search for
   * @return Optional containing the user with profile, or empty if not found
   */
  @Query("SELECT u FROM User u LEFT JOIN FETCH u.profile WHERE u.email = :email")
  Optional<User> findByEmailWithProfile(@Param("email") String email);

  /**
   * Checks if a user exists with the given username or email. Used during registration to prevent
   * duplicates.
   *
   * @param username The username to check
   * @param email The email to check
   * @return true if a user exists with either username or email
   */
  @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.username = :username OR u.email = :email")
  boolean existsByUsernameOrEmail(@Param("username") String username, @Param("email") String email);

  /**
   * Finds active users created within a date range. Useful for reporting and analytics.
   *
   * @param startDate Start of the date range
   * @param endDate End of the date range
   * @return List of active users created in the date range
   */
  @Query(
      "SELECT u FROM User u WHERE u.isActive = true AND u.createdAt BETWEEN :startDate AND :endDate")
  List<User> findActiveUsersInDateRange(
      @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

  /**
   * Counts users by their email verification status. Used for monitoring and reporting.
   *
   * @param isVerified Whether to count verified or unverified users
   * @return Number of users with the specified verification status
   */
  @Query("SELECT COUNT(u) FROM User u WHERE u.isEmailVerified = :isVerified")
  long countByEmailVerificationStatus(@Param("isVerified") boolean isVerified);
}
