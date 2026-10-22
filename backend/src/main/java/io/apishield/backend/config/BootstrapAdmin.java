package io.apishield.backend.config;

import io.apishield.backend.auth.AuthService;
import io.apishield.backend.user.RoleName;
import io.apishield.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Creates the first administrator from environment variables if (and only if) no administrator exists. No default credentials exist. */
@Component
public class BootstrapAdmin implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);
    private final AppProperties props; private final UserRepository users; private final AuthService auth;
    public BootstrapAdmin(AppProperties props, UserRepository users, AuthService auth) { this.props = props; this.users = users; this.auth = auth; }

    @Override public void run(ApplicationArguments args) {
        var b = props.getBootstrapAdmin();
        if (b.getEmail() == null || b.getEmail().isBlank() || b.getPassword() == null || b.getPassword().length() < 10) return;
        if (users.countActiveWithRole(RoleName.ADMIN) > 0 || users.existsByEmailIgnoreCase(b.getEmail())) return;
        auth.newUser(b.getEmail().trim().toLowerCase(), "Administrator", b.getPassword(), Set.of(RoleName.ADMIN, RoleName.API_MANAGER, RoleName.DEVELOPER));
        log.info("Bootstrap administrator created for {}", b.getEmail());
    }
}
