package com.marvex.security;

import com.marvex.repository.MerchantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final MerchantRepository merchantRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(JwtUtil.BEARER_PREFIX)) {
            String token = header.substring(JwtUtil.BEARER_PREFIX.length());
            if (jwtUtil.isValid(token)) {
                var merchantId = jwtUtil.extractMerchantId(token);
                merchantRepository.findById(merchantId).ifPresentOrElse(merchant -> {
                    var auth = new UsernamePasswordAuthenticationToken(merchant, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("[JwtAuthFilter] Authenticated merchantId={} for {} {}", merchantId, request.getMethod(), request.getRequestURI());
                }, () -> log.warn("[JwtAuthFilter] Token valid but merchant not found: {}", merchantId));
            } else {
                log.warn("[JwtAuthFilter] Invalid JWT token for {} {}", request.getMethod(), request.getRequestURI());
            }
        }
        chain.doFilter(request, response);
    }
}
