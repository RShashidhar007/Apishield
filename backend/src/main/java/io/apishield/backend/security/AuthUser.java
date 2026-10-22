package io.apishield.backend.security;

import java.util.Set;

public record AuthUser(Long id, String email, Set<String> roles) {
    public boolean has(String role) { return roles.contains(role); }
    public boolean isAdminOrManager() { return has("ADMIN") || has("API_MANAGER"); }
}
