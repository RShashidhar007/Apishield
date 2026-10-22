package io.apishield.backend.config;

import io.apishield.common.net.DestinationPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean DestinationPolicy destinationPolicy(AppProperties p) {
        var d = p.getDestination();
        return new DestinationPolicy(new HashSet<>(d.getAllowedHosts()), new HashSet<>(d.getAllowedPorts()),
                d.isAllowPrivateForAllowedHosts(), d.isAllowLoopback());
    }

    /** Fail fast on weak or missing secrets instead of starting insecurely. */
    @Bean org.springframework.boot.ApplicationRunner secretsCheck(AppProperties p) {
        return args -> {
            if (p.getJwt().getSecret() == null || p.getJwt().getSecret().getBytes().length < 32)
                throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
            if (p.getApiKeyPepper() == null || p.getApiKeyPepper().length() < 32)
                throw new IllegalStateException("API_KEY_PEPPER must be at least 32 characters");
            if (p.getGatewayToken() == null || p.getGatewayToken().length() < 24)
                throw new IllegalStateException("GATEWAY_TOKEN must be at least 24 characters");
        };
    }
}
