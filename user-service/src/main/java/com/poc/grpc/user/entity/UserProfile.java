package com.poc.grpc.user.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import lombok.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * User Profile Entity
 *
 * <p>This entity represents additional profile information for a user. It maintains a one-to-one
 * relationship with the User entity and stores extended user details.
 *
 * <p>Key Features: 1. One-to-one relationship with User 2. Extended user information 3. Contact
 * details 4. Address information 5. User preferences
 *
 * <p>Database Table: user_profiles Indexes: - Primary Key: id (auto-increment) - Foreign Key:
 * user_id (references users.id) - Index: created_at
 */
@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(name = "phone_number", length = 20)
  private String phoneNumber;

  @Column(name = "date_of_birth")
  private LocalDateTime dateOfBirth;

  @Column(length = 500)
  private String address;

  @Column(length = 100)
  private String city;

  @Column(length = 100)
  private String state;

  @Column(length = 100)
  private String country;

  @Column(name = "postal_code", length = 20)
  private String postalCode;

  @Column(columnDefinition = "TEXT")
  private String preferences;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  /**
   * Gets the full name by combining first and last name.
   *
   * @return The user's full name
   */
  public String getFullName() {
    return firstName + " " + lastName;
  }

  /**
   * Gets the full address as a formatted string.
   *
   * @return The complete address or null if no address is set
   */
  public String getFullAddress() {
    if (address == null || address.trim().isEmpty()) {
      return null;
    }

    StringBuilder sb = new StringBuilder(address.trim());
    if (city != null && !city.trim().isEmpty()) {
      sb.append(", ").append(city.trim());
    }
    if (state != null && !state.trim().isEmpty()) {
      sb.append(", ").append(state.trim());
    }
    if (postalCode != null && !postalCode.trim().isEmpty()) {
      sb.append(" ").append(postalCode.trim());
    }
    if (country != null && !country.trim().isEmpty()) {
      sb.append(", ").append(country.trim());
    }
    return sb.toString();
  }

  /**
   * Calculates the user's age based on date of birth.
   *
   * @return The user's age in years, or null if date of birth is not set
   */
  public Integer getAge() {
    if (dateOfBirth == null) {
      return null;
    }
    return Period.between(dateOfBirth.toLocalDate(), LocalDate.now()).getYears();
  }

  /**
   * Updates the profile information from a request. Only updates non-null fields.
   *
   * @param firstName New first name or null to keep current
   * @param lastName New last name or null to keep current
   * @param phoneNumber New phone number or null to keep current
   * @param address New address or null to keep current
   */
  public void updateProfile(String firstName, String lastName, String phoneNumber, String address) {
    if (firstName != null) this.firstName = firstName;
    if (lastName != null) this.lastName = lastName;
    if (phoneNumber != null) this.phoneNumber = phoneNumber;
    if (address != null) this.address = address;
    this.updatedAt = LocalDateTime.now();
  }
}
