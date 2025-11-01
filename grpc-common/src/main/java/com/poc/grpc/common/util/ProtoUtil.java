package com.poc.grpc.common.util;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;

/**
 * Protocol Buffer Utility Class
 *
 * <p>This utility class provides conversion methods between Java time types and Protocol Buffer
 * timestamp messages. It ensures consistent timestamp handling across the microservices.
 *
 * <p>Features: 1. LocalDateTime to Protobuf Timestamp conversion 2. Protobuf Timestamp to
 * LocalDateTime conversion 3. Null-safe operations
 *
 * <p><b>Important:</b> This utility currently uses the JVM's default timezone ({@code
 * ZoneId.systemDefault()}). For robust, production-grade applications, it is highly recommended to
 * use a fixed, explicit timezone (like UTC) or inject the application's configured {@link
 * java.time.Clock} to ensure consistency across all services, regardless of their host environment.
 */
@Slf4j
public class ProtoUtil {

  private ProtoUtil() {
    // Private constructor to prevent instantiation of this utility class.
  }

  /**
   * Converts a LocalDateTime to a Protocol Buffer Timestamp. Handles null values gracefully.
   *
   * @param dateTime The LocalDateTime to convert, may be null
   * @return The Protocol Buffer Timestamp, or null if input is null
   */
  public static Timestamp toTimestamp(LocalDateTime dateTime) {
    if (dateTime == null) {
      log.debug("Null LocalDateTime provided, returning null Timestamp");
      return null;
    }

    try {
      // WARNING: Using systemDefault() can lead to inconsistencies in a distributed system.
      // It is safer to use a fixed zone, e.g., ZoneId.of("UTC"), or the application's
      // configured clock.
      Instant instant = dateTime.atZone(ZoneId.systemDefault()).toInstant();

      // Build the Protobuf Timestamp from the Instant's epoch seconds and nanoseconds.
      Timestamp timestamp =
          Timestamp.newBuilder()
              .setSeconds(instant.getEpochSecond())
              .setNanos(instant.getNano())
              .build();

      log.trace("Converted LocalDateTime to Timestamp: {} -> {}", dateTime, timestamp);
      return timestamp;
    } catch (Exception e) {
      log.error("Failed to convert LocalDateTime to Timestamp: {}", dateTime, e);
      throw new IllegalArgumentException("Invalid LocalDateTime value", e);
    }
  }

  /**
   * Converts a Protocol Buffer Timestamp to a LocalDateTime. Handles null values gracefully.
   *
   * @param timestamp The Protocol Buffer Timestamp to convert, may be null
   * @return The LocalDateTime, or null if input is null
   */
  public static LocalDateTime fromTimestamp(Timestamp timestamp) {
    if (timestamp == null) {
      log.debug("Null Timestamp provided, returning null LocalDateTime");
      return null;
    }

    try {
      // Reconstruct the Instant from the Protobuf Timestamp's fields.
      Instant instant = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());

      // WARNING: Using systemDefault() can lead to inconsistencies. See note in toTimestamp().
      // The same fixed timezone should be used here for consistent conversion.
      LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

      log.trace("Converted Timestamp to LocalDateTime: {} -> {}", timestamp, dateTime);
      return dateTime;
    } catch (Exception e) {
      log.error("Failed to convert Timestamp to LocalDateTime: {}", timestamp, e);
      throw new IllegalArgumentException("Invalid Timestamp value", e);
    }
  }
}
