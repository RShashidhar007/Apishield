package io.apishield.backend.auth;

import io.apishield.backend.user.UserDtos;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}
    public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 100) String displayName,
                                  @NotBlank @Size(min = 10, max = 72, message = "Password must be 10-72 characters") String password) {}
    public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}
    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {}
    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, String tokenType, UserDtos.UserView user) {}
}
