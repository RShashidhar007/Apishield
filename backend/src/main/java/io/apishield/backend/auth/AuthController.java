package io.apishield.backend.auth;

import io.apishield.backend.security.AuthUser;
import io.apishield.backend.user.UserDtos;
import io.apishield.backend.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService auth; private final UserRepository users;
    public AuthController(AuthService auth, UserRepository users) { this.auth = auth; this.users = users; }

    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
    UserDtos.UserView register(@Valid @RequestBody AuthDtos.RegisterRequest r) { return auth.register(r); }

    @PostMapping("/auth/login") AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest r) { return auth.login(r); }
    @PostMapping("/auth/refresh") AuthDtos.TokenResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest r) { return auth.refresh(r.refreshToken()); }
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT) void logout(@Valid @RequestBody AuthDtos.RefreshRequest r) { auth.logout(r.refreshToken()); }

    @GetMapping("/me") UserDtos.UserView me(@AuthenticationPrincipal AuthUser me) {
        return UserDtos.UserView.of(users.findById(me.id()).orElseThrow());
    }
}
