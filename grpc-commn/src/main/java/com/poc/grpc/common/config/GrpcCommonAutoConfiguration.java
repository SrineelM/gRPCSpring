package com.poc.grpc.common.config;

import com.poc.grpc.common.exception.GlobalExceptionHandlerInterceptor;
import com.poc.grpc.common.security.JwtAuthenticationInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcCommonAutoConfiguration {

    /**
     * Auto-configures the global exception handler interceptor.
     */
    @Bean
    public GlobalExceptionHandlerInterceptor globalExceptionHandlerInterceptor() {
        return new GlobalExceptionHandlerInterceptor();
    }

    /**
     * Auto-configures the JWT security interceptor.
     * Can be disabled by setting security.jwt.enabled=false in application properties.
     */
    @Bean
    @ConditionalOnProperty(name = "security.jwt.enabled", havingValue = "true", matchIfMissing = true)
    public JwtAuthenticationInterceptor jwtAuthenticationInterceptor(
            org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.core.RedisTemplate<String, Object>> redisTemplateProvider,
            org.springframework.core.env.Environment env) {

        // Safely retrieve properties with defaults
        String secret = env.getProperty("security.jwt.secret", "default-secret-for-local-dev-only-change-it");
        int rateLimit = env.getProperty("security.rate-limit.max-requests-per-minute", Integer.class, 60);
        String audience = env.getProperty("security.jwt.audience", "my-ecommerce-app");
        String issuer = env.getProperty("security.jwt.issuer", "my-ecommerce-platform");

        // The ObjectProvider safely handles cases where Redis might not be configured
        return new JwtAuthenticationInterceptor(secret, rateLimit, audience, issuer, redisTemplateProvider.getIfAvailable());
    }
}
