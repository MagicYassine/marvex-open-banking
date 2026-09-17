package com.marvex.config;

import com.marvex.filter.RateLimitFilter;
import com.marvex.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    // 'unsafe-inline' is required for Vite's injected style tags in the built SPA.
                    // A nonce-based CSP would require server-side rendering support; acceptable trade-off for now.
                    "script-src 'self' 'unsafe-inline'; " +
                    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                    "font-src 'self' https://fonts.gstatic.com; " +
                    "img-src 'self' data: https:; " +
                    "connect-src 'self' wss:; " +
                    "frame-ancestors 'none';"
                ))
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(Customizer.withDefaults())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(pp -> pp.policy(
                    "camera=(), microphone=(), geolocation=(), payment=(self)"))
            )
            .authorizeHttpRequests(auth -> {
                auth
                    .requestMatchers("/", "/index.html", "/register", "/pay/**", "/mock-bank/**", "/dashboard",
                        "/login", "/complete-registration", "/forgot-password", "/reset-password",
                        "/terms", "/privacy", "/my-account", "/payment-qr").permitAll()
                    .requestMatchers("/assets/**", "/favicon.ico", "/*.js", "/*.css", "/*.svg", "/*.png").permitAll()
                    .requestMatchers("/uk_banks_logos/**", "/logo/**").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    .requestMatchers("/auth/**").permitAll()
                    .requestMatchers("/webhooks/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/payments/{id}").permitAll()
                    .requestMatchers(HttpMethod.POST, "/payments/{id}/initiate").permitAll()
                    .requestMatchers(HttpMethod.POST, "/payments/{id}/submit").permitAll()
                    .requestMatchers(HttpMethod.POST, "/payments/{id}/confirm").permitAll()
                    .requestMatchers(HttpMethod.GET, "/institutions").permitAll();

                auth.anyRequest().authenticated();
            })
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
