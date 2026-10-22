package io.apishield.backend.user;

import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.Set;

public final class UserDtos {
    private UserDtos() {}
    public record UserView(Long id, String email, String displayName, Set<String> roles, boolean active, Instant lastLoginAt, Instant createdAt) {
        public static UserView of(UserAccount u) { return new UserView(u.getId(), u.getEmail(), u.getDisplayName(), u.roleNames(), u.isActive(), u.getLastLoginAt(), u.getCreatedAt()); }
    }
    public record UpdateUserRequest(@NotEmpty Set<RoleName> roles, Boolean active) {}
}
