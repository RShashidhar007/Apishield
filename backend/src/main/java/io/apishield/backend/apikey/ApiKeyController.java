package io.apishield.backend.apikey;

import io.apishield.backend.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {
    private final ApiKeyService service;
    public ApiKeyController(ApiKeyService service) { this.service = service; }

    @GetMapping List<ApiKeyDtos.KeyView> list(@AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "false") boolean all) { return service.list(me, all); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    ApiKeyDtos.KeyWithSecret create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ApiKeyDtos.CreateRequest r) { return service.create(me, r); }

    @PostMapping("/{id}/rotate") ApiKeyDtos.KeyWithSecret rotate(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { return service.rotate(me, id); }

    @PostMapping("/{id}/revoke") @ResponseStatus(HttpStatus.NO_CONTENT) void revoke(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { service.revoke(me, id); }
}
