package io.apishield.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {
    private String managementUrl; private String gatewayToken; private String apiKeyPepper;
    private Jwt jwt = new Jwt(); private long configRefreshMs = 5000; private long maxStaleSeconds = 0;
    private long rateLimitRedisTimeoutMs = 200; private List<String> trustedProxies = new ArrayList<>();
    private Ingest ingest = new Ingest(); private Destination destination = new Destination();
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    public static class Jwt { private String secret; private String issuer; private String audience;
        public String getSecret() { return secret; } public void setSecret(String s) { secret = s; }
        public String getIssuer() { return issuer; } public void setIssuer(String s) { issuer = s; }
        public String getAudience() { return audience; } public void setAudience(String s) { audience = s; } }
    public static class Ingest { private long flushIntervalMs = 5000; private int queueCapacity = 20000; private int batchSize = 500;
        public long getFlushIntervalMs() { return flushIntervalMs; } public void setFlushIntervalMs(long v) { flushIntervalMs = v; }
        public int getQueueCapacity() { return queueCapacity; } public void setQueueCapacity(int v) { queueCapacity = v; }
        public int getBatchSize() { return batchSize; } public void setBatchSize(int v) { batchSize = v; } }
    public static class Destination { private List<String> allowedHosts = new ArrayList<>(); private List<Integer> allowedPorts = new ArrayList<>();
        private boolean allowPrivateForAllowedHosts; private boolean allowLoopback;
        public List<String> getAllowedHosts() { return allowedHosts; } public void setAllowedHosts(List<String> v) { allowedHosts = v; }
        public List<Integer> getAllowedPorts() { return allowedPorts; } public void setAllowedPorts(List<Integer> v) { allowedPorts = v; }
        public boolean isAllowPrivateForAllowedHosts() { return allowPrivateForAllowedHosts; } public void setAllowPrivateForAllowedHosts(boolean v) { allowPrivateForAllowedHosts = v; }
        public boolean isAllowLoopback() { return allowLoopback; } public void setAllowLoopback(boolean v) { allowLoopback = v; } }

    public String getManagementUrl() { return managementUrl; } public void setManagementUrl(String v) { managementUrl = v; }
    public String getGatewayToken() { return gatewayToken; } public void setGatewayToken(String v) { gatewayToken = v; }
    public String getApiKeyPepper() { return apiKeyPepper; } public void setApiKeyPepper(String v) { apiKeyPepper = v; }
    public Jwt getJwt() { return jwt; } public void setJwt(Jwt v) { jwt = v; }
    public long getConfigRefreshMs() { return configRefreshMs; } public void setConfigRefreshMs(long v) { configRefreshMs = v; }
    public long getMaxStaleSeconds() { return maxStaleSeconds; } public void setMaxStaleSeconds(long v) { maxStaleSeconds = v; }
    public long getRateLimitRedisTimeoutMs() { return rateLimitRedisTimeoutMs; } public void setRateLimitRedisTimeoutMs(long v) { rateLimitRedisTimeoutMs = v; }
    public List<String> getTrustedProxies() { return trustedProxies; } public void setTrustedProxies(List<String> v) { trustedProxies = v; }
    public Ingest getIngest() { return ingest; } public void setIngest(Ingest v) { ingest = v; }
    public Destination getDestination() { return destination; } public void setDestination(Destination v) { destination = v; }
    public String getInstanceId() { return instanceId; }
}
