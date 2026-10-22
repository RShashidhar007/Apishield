package io.apishield.backend.security;

import io.apishield.backend.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Authenticates the gateway runtime on /internal/** with a shared secret (constant-time comparison). */
public class GatewayTokenFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Gateway-Token";
    private final AppProperties props;
    public GatewayTokenFilter(AppProperties props) { this.props = props; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith("/internal/"); }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String t = req.getHeader(HEADER);
        if (t != null && MessageDigest.isEqual(t.getBytes(StandardCharsets.UTF_8), props.getGatewayToken().getBytes(StandardCharsets.UTF_8))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("gateway", null, List.of(new SimpleGrantedAuthority("ROLE_GATEWAY"))));
        }
        chain.doFilter(req, res);
    }
}
