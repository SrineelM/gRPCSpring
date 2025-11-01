package com.poc.grpc.order.config;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for gRPC client channels.
 * <p>
 * This class manually configures and creates ManagedChannel beans for communicating
 * with other gRPC services. This approach provides fine-grained control over channel
 * properties like security, keep-alives, and retry policies.
 * <p>
 * Note: This manual configuration bypasses the gRPC client auto-configuration
 * from the 'net.devh.boot:grpc-client-spring-boot-starter'.
 */
@Slf4j
@Configuration
public class GrpcClientConfig {

    /**
     * Creates and configures the ManagedChannel for communicating with the UserService.
     * <p>
     * This single bean handles both secure (TLS) and insecure (plaintext) connections,
     * determined by the 'grpc.client.user-service.enable-tls' property. It is configured
     * with keep-alives, message size limits, and a robust retry policy via a service config.
     *
     * @param address        The target address of the user-service gRPC server.
     * @param enableTls      A flag to determine if transport security should be used.
     * @param connectTimeout The timeout for establishing a connection, in milliseconds.
     * @return A configured, ready-to-use ManagedChannel.
     */
    @Bean(name = "userServiceChannel", destroyMethod = "shutdownNow")
    public ManagedChannel userServiceChannel(
            @Value("${grpc.client.user-service.address:localhost:9091}") String address,
            @Value("${grpc.client.user-service.enable-tls:false}") boolean enableTls,
            @Value("${grpc.client.user-service.connect-timeout-millis:5000}") int connectTimeout
    ) {
        log.info("Configuring gRPC channel 'userServiceChannel' for address: {}", address);

        // NettyChannelBuilder provides more transport-specific options than the standard ManagedChannelBuilder.
        NettyChannelBuilder channelBuilder = NettyChannelBuilder.forTarget(address);

        if (enableTls) {
            log.info("TLS is enabled for the gRPC channel.");
            channelBuilder.useTransportSecurity();
            // For production, mTLS configuration would be added here, e.g., by providing an SslContext
            // built with trusted root CAs and client certificates/keys.
            // .sslContext(buildSslContext(...))
        } else {
            log.warn("TLS is disabled. Using plaintext for gRPC channel. This is NOT recommended for production.");
            channelBuilder.usePlaintext();
        }

        return channelBuilder
                // Set a timeout for establishing the initial TCP connection.
                .withOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)

                // Keep-alive settings help maintain long-lived connections and detect dead ones.
                .keepAliveTime(30, TimeUnit.SECONDS) // Ping the server if no activity for 30s.
                .keepAliveTimeout(10, TimeUnit.SECONDS) // Wait 10s for ping ack before closing.
                .keepAliveWithoutCalls(true) // Allow pings even when there are no active calls.

                // Set message size limits to prevent client/server from running out of memory.
                .maxInboundMessageSize(16 * 1024 * 1024) // 16MB

                // Use client-side round-robin load balancing if the address resolves to multiple backends.
                .defaultLoadBalancingPolicy("round_robin")

                // Apply a default service config for retry policies and timeouts. This is the recommended
                // way to configure retries as it's more powerful and granular than .enableRetry().
                .defaultServiceConfig(buildServiceConfig())
                // Important: Prevents the client from trying to fetch this config from a DNS TXT record.
                .disableServiceConfigLookUp()

                .build();
    }

    /**
     * Builds a JSON string representing the gRPC service configuration.
     * <p>
     * This configuration defines default behavior for all methods within the 'UserService'.
     * It specifies a timeout and a retry policy for transient failures.
     *
     * @return A JSON string containing the service config.
     */
    private static String buildServiceConfig() {
        // Using a text block for a clean, readable JSON structure.
        return """
            {
              "methodConfig": [{
                "name": [{"service": "UserService"}],
                "waitForReady": true,
                "timeout": "10s",
                "retryPolicy": {
                  "maxAttempts": 3,
                  "initialBackoff": "0.5s",
                  "maxBackoff": "2s",
                  "backoffMultiplier": 2,
                  "retryableStatusCodes": ["UNAVAILABLE", "DEADLINE_EXCEEDED"]
                }
              }]
            }
            """;
    }
}