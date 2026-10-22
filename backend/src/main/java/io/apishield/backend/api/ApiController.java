package io.apishield.backend.api;

import io.apishield.backend.common.PageResponse;
import io.apishield.backend.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/apis")
public class ApiController {
    private final ApiService service;
    public ApiController(ApiService service) { this.service = service; }

    @GetMapping
    PageResponse<ApiDtos.ApiView> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String environment, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.search(me, q, status, environment, page, size);
    }

    @GetMapping("/{id}") ApiDtos.ApiView get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { return service.get(me, id); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('ADMIN','API_MANAGER')")
    ApiDtos.ApiView create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ApiDtos.ApiRequest r) { return service.create(me, r); }

    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','API_MANAGER')")
    ApiDtos.ApiView update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody ApiDtos.ApiRequest r) { return service.update(me, id, r); }

    @PostMapping("/{id}/status") @PreAuthorize("hasAnyRole('ADMIN','API_MANAGER')")
    ApiDtos.ApiView status(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody ApiDtos.StatusRequest r) { return service.setStatus(me, id, r.status()); }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAnyRole('ADMIN','API_MANAGER')")
    void archive(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { service.archive(me, id); }
}
