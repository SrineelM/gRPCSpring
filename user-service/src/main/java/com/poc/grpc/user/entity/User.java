package com.poc.grpc.user.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * User Entity
 *
 * <p>This entity represents a user in the system with authentication and profile information. It
 * uses UUID as primary key and maintains audit information.
 *
 * <p>Key Features: 1. Secure password storage 2. Account status tracking 3. Login attempt
 * monitoring 4. Bi-directional relationship with UserProfile 5. Audit fields for tracking
 *
 * <p>Database Table: users Indexes: - Primary Key: id (UUID) - Unique: username, email - Index:
 * is_active, created_at
 *
 * <p>Security Notes: - Passwords are stored using BCrypt - Account locking after failed attempts -
 * Email verification tracking
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 50)
  private String username;

  @Column(nullable = false, unique = true, length = 100)
  private String email;

  @Column(nullable = false)
  private String password;

  @Column(name = "first_name", nullable = false, length = 50)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 50)
  private String lastName;

  @Column(name = "is_active", nullable = false)
  @Builder.Default
  private boolean isActive = true;

  @Column(name = "is_email_verified", nullable = false)
  @Builder.Default
  private boolean isEmailVerified = false;

  @Column(name = "login_attempts", nullable = false)
  @Builder.Default
  private int loginAttempts = 0;

  @Column(name = "last_login_at")
  private LocalDateTime lastLoginAt;

  @Version private Long version;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Column(name = "created_by")
  private String createdBy;

  @Column(name = "updated_by")
  private String updatedBy;

  @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private UserProfile profile;

  /**
   * Checks if the account is locked due to too many failed login attempts. An account is locked if
   * it has 5 or more failed attempts.
   *
   * @return true if the account is locked
   */
  public boolean isAccountLocked() {
    return loginAttempts >= 5;
  }

  /**
   * Records a failed login attempt and potentially locks the account. This method is thread-safe
   * due to @Version annotation.
   */
  public void recordFailedLogin() {
    this.loginAttempts++;
    this.updatedAt = LocalDateTime.now();
  }

  /**
   * Records a successful login, resetting the failed attempt counter. Also updates the last login
   * timestamp.
   */
  public void recordSuccessfulLogin() {
    this.loginAttempts = 0;
    this.lastLoginAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  /**
   * Checks if the user is valid for placing orders. A user must be active, email verified, and not
   * locked.
   *
   * @return true if the user can place orders
   */
  public boolean isValidForOrder() {
    return isActive && isEmailVerified && !isAccountLocked();
  }

  /**
   * Sets up bi-directional relationship with UserProfile.
   *
   * @param profile The profile to associate with this user
   */
  public void setProfile(UserProfile profile) {
    this.profile = profile;
    if (profile != null) {
      profile.setUser(this);
    }
  }
}
