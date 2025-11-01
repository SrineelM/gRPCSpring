package com.poc.grpc.common.util;

import com.google.protobuf.Timestamp;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * @class ProtoUtil
 * @description A centralized utility class for converting between Java's native time objects
 * and Google Protobuf's standard time-related message types. This ensures consistency
 * and correctness across the application when dealing with timestamps.
 */
public final class ProtoUtil {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ProtoUtil() {}

    /**
     * Converts a Java {@link LocalDateTime} object to a Google Protobuf {@link Timestamp}.
     * This method preserves full nanosecond precision.
     *
     * @param time The LocalDateTime instance to convert. Can be null.
     * @return The corresponding Protobuf Timestamp, or a default instance if the input was null.
     */
    public static Timestamp toTimestamp(LocalDateTime time) {
        // Handle null input gracefully to prevent NullPointerExceptions downstream.
        if (time == null) {
            return Timestamp.getDefaultInstance();
        }
        // Protobuf Timestamps represent a moment in time in UTC.
        // We convert the LocalDateTime using ZoneOffset.UTC to get the correct epoch seconds and nanos.
        return Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond(ZoneOffset.UTC))
                .setNanos(time.getNano()) // Include nanoseconds to maintain full precision.
                .build();
    }

    /**
     * Converts a Google Protobuf {@link Timestamp} object back to a Java {@link LocalDateTime}.
     *
     * @param timestamp The Protobuf Timestamp to convert. Can be null.
     * @return The corresponding LocalDateTime instance, or null if the input was null.
     */
    public static LocalDateTime fromTimestamp(Timestamp timestamp) {
        // Handle null input gracefully.
        if (timestamp == null || (timestamp.getSeconds() == 0 && timestamp.getNanos() == 0)) {
            return null;
        }
        // Reconstruct the LocalDateTime from epoch seconds and nanoseconds, assuming UTC.
        return LocalDateTime.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos(), ZoneOffset.UTC);
    }
}