package io.apishield.backend.user;

import io.apishield.backend.common.PageResponse;
import io.apishield.backend.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {
    private final UserService service;
    public UserController(UserService service) { this.service = service; }

    @GetMapping PageResponse<UserDtos.UserView> list(@RequestParam(required = false) String q,
                                                     @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return service.list(q, page, size); }

    @PutMapping("/{id}") UserDtos.UserView update(@PathVariable Long id, @Valid @RequestBody UserDtos.UpdateUserRequest req, @AuthenticationPrincipal AuthUser me) {
        return service.update(id, req, me.id());
    }
}
