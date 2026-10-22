package io.apishield.backend.user;

import io.apishield.backend.audit.AuditService;
import io.apishield.backend.common.ApiException;
import io.apishield.backend.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Service
public class UserService {
    private final UserRepository users; private final RoleRepository roles; private final AuditService audit;
    public UserService(UserRepository users, RoleRepository roles, AuditService audit) { this.users = users; this.roles = roles; this.audit = audit; }

    @Transactional(readOnly = true)
    public PageResponse<UserDtos.UserView> list(String q, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        var spec = (org.springframework.data.jpa.domain.Specification<UserAccount>) (root, cq, cb) ->
                (q == null || q.isBlank()) ? cb.conjunction()
                        : cb.or(cb.like(cb.lower(root.get("email")), "%" + q.toLowerCase().replace("%", "") + "%"),
                                cb.like(cb.lower(root.get("displayName")), "%" + q.toLowerCase().replace("%", "") + "%"));
        return PageResponse.of(users.findAll(spec, pageable), UserDtos.UserView::of);
    }

    @Transactional
    public UserDtos.UserView update(Long id, UserDtos.UpdateUserRequest req, Long actorId) {
        UserAccount u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        Set<RoleName> target = req.roles();
        boolean willBeActive = req.active() == null ? u.isActive() : req.active();
        boolean isAdminNow = u.roleNames().contains("ADMIN") && u.isActive();
        boolean willBeAdmin = target.contains(RoleName.ADMIN) && willBeActive;
        if (isAdminNow && !willBeAdmin && users.countActiveWithRole(RoleName.ADMIN) <= 1)
            throw ApiException.conflict("LAST_ADMIN", "At least one active administrator must remain");
        if (id.equals(actorId) && !willBeActive) throw ApiException.conflict("SELF_DEACTIVATION", "You cannot deactivate your own account");
        Set<RoleEntity> newRoles = new HashSet<>();
        target.forEach(r -> newRoles.add(roles.findByName(r).orElseThrow()));
        boolean rolesChanged = !u.roleNames().equals(target.stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
        boolean activeChanged = u.isActive() != willBeActive;
        u.setRoles(newRoles); u.setActive(willBeActive); u.setUpdatedAt(Instant.now());
        if (activeChanged && willBeActive) { u.setFailedAttempts(0); u.setLockedUntil(null); }
        if (rolesChanged) audit.success("USER_ROLES_CHANGED", "USER", String.valueOf(id), "roles=" + target);
        if (activeChanged) audit.success(willBeActive ? "USER_ACTIVATED" : "USER_DEACTIVATED", "USER", String.valueOf(id), null);
        return UserDtos.UserView.of(u);
    }
}
