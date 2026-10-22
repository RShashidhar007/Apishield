package io.apishield.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apishield.backend.common.ApiError;
import io.apishield.backend.common.CorrelationIdFilter;
import io.apishield.backend.config.AppProperties;
import io.apishield.backend.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless bearer-token API: CSRF protection is intentionally disabled because no ambient credentials (cookies)
 * are used. The Angular client keeps the access token in memory and the refresh token in sessionStorage (see docs/SECURITY.md).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http, JwtService jwt, UserRepository users, AppProperties props, ObjectMapper mapper) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .cors(c -> c.configurationSource(corsSource(props)))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h.contentTypeOptions(c -> {}).frameOptions(f -> f.deny())
                    .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .authorizeHttpRequests(a -> a
                    .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                    .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/internal/**").hasRole("GATEWAY")
                    // URL-level rules run BEFORE body validation; @PreAuthorize on controllers is a second, independent layer.
                    .requestMatchers("/api/v1/users/**", "/api/v1/audit/**").hasRole("ADMIN")
                    .requestMatchers("/api/v1/analytics/**", "/api/v1/security/**", "/api/v1/system/**").hasAnyRole("ADMIN", "API_MANAGER")
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/apis/**").hasAnyRole("ADMIN", "API_MANAGER")
                    .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/apis/**").hasAnyRole("ADMIN", "API_MANAGER")
                    .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/apis/**").hasAnyRole("ADMIN", "API_MANAGER")
                    .anyRequest().authenticated())
            .exceptionHandling(e -> e
                    .authenticationEntryPoint((req, res, ex) -> write(res, mapper, 401, "UNAUTHENTICATED", "Authentication is required"))
                    .accessDeniedHandler((req, res, ex) -> write(res, mapper, 403, "FORBIDDEN", "You do not have permission to perform this action")))
            .addFilterBefore(new GatewayTokenFilter(props), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtAuthFilter(jwt, users), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void write(jakarta.servlet.http.HttpServletResponse res, ObjectMapper mapper, int status, String code, String msg) throws java.io.IOException {
        res.setStatus(status); res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), new ApiError(code, msg, CorrelationIdFilter.current(), null));
    }

    private CorsConfigurationSource corsSource(AppProperties props) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(props.getCorsAllowedOrigins());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        c.setExposedHeaders(List.of("X-Request-Id"));
        c.setMaxAge(600L);
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/api/**", c);
        return s;
    }
}
