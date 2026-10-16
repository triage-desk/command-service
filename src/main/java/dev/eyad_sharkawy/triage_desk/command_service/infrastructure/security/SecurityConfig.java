package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.security;

import dev.eyad_sharkawy.triage_desk.command_service.infrastructure.idempotency.IdempotencyFilter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@NullMarked
@RequiredArgsConstructor
public class SecurityConfig {

    private final TenantResolutionFilter tenantResolutionFilter;
    private final IdempotencyFilter idempotencyFilter;

    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private final List<String> allowedOrigins;

    @Bean
    @SuppressWarnings("java:S4502") // Safe: Stateless REST service using JWT Bearer authentication
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        try {
            http.csrf(AbstractHttpConfigurer::disable)
                    .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                    .sessionManagement(
                            session ->
                                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(
                            auth ->
                                    auth.requestMatchers("/actuator/**")
                                            .permitAll()
                                            .requestMatchers("/api/v1/**")
                                            .authenticated()
                                            .anyRequest()
                                            .authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                    .addFilterAfter(tenantResolutionFilter, BearerTokenAuthenticationFilter.class)
                    .addFilterAfter(idempotencyFilter, TenantResolutionFilter.class);

            return http.build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure SecurityFilterChain", e);
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Tenant-ID"));
        configuration.setExposedHeaders(List.of("Location", "Idempotency-Key"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
