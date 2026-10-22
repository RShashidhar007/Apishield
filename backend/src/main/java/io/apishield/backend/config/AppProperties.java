package io.apishield.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "apishield")
public class AppProperties {
    private Jwt jwt = new Jwt();
    private String gatewayToken;
    private String apiKeyPepper;
    private List<String> corsAllowedOrigins = new ArrayList<>();
    private BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();
    private Destination destination = new Destination();
    private Login login = new Login();
    private Detection detection = new Detection();
    private Retention retention = new Retention();

    @Data public static class Jwt { private String secret; private String issuer; private String audience; private Duration accessTtl; private Duration refreshTtl; }
    @Data public static class BootstrapAdmin { private String email; private String password; }
    @Data public static class Destination { private List<String> allowedHosts = new ArrayList<>(); private List<Integer> allowedPorts = new ArrayList<>(); private boolean allowPrivateForAllowedHosts; private boolean allowLoopback; }
    @Data public static class Login { private int maxFailedAttempts = 5; private int lockMinutes = 15; }
    @Data public static class Detection { private int windowMinutes = 10; private int authFailureThreshold = 5; private int rateLimitThreshold = 20; private int blockedRequestThreshold = 10; private int largeRequestThreshold = 3; }
    @Data public static class Retention { private int requestLogDays = 30; private int securityEventDays = 180; }
}
