package com.marvex.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // Buckets are keyed by the caller's true remote address (see resolveClientIp),
    // which the caller cannot spoof, so this map's key cardinality is bounded by
    // real distinct peers. The sweeper below still bounds memory in case of a
    // large distributed burst.
    private static final Duration ENTRY_TTL = Duration.ofMinutes(15);

    private final Map<String, Entry> loginBuckets   = new ConcurrentHashMap<>();
    private final Map<String, Entry> emailBuckets   = new ConcurrentHashMap<>();
    private final Map<String, Entry> generalBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {

        String ip  = resolveClientIp(request);
        String uri = request.getRequestURI();
        String method = request.getMethod();

        Bucket bucket = switch (method + ":" + uri) {
            case "POST:/auth/login" ->
                bucketFor(loginBuckets, ip, () -> Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofMinutes(1)).build())
                    .build());
            case "POST:/auth/register", "POST:/auth/forgot-password" ->
                bucketFor(emailBuckets, ip, () -> Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(3).refillGreedy(3, Duration.ofMinutes(10)).build())
                    .build());
            default ->
                bucketFor(generalBuckets, ip, () -> Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(60).refillGreedy(60, Duration.ofMinutes(1)).build())
                    .build());
        };

        if (!bucket.tryConsume(1)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests, please try again later\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private Bucket bucketFor(Map<String, Entry> buckets, String key, Supplier<Bucket> factory) {
        Entry entry = buckets.computeIfAbsent(key, k -> new Entry(factory.get()));
        entry.lastAccess = Instant.now();
        return entry.bucket;
    }

    private String resolveClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    void evictStaleEntries() {
        Instant cutoff = Instant.now().minus(ENTRY_TTL);
        loginBuckets.values().removeIf(e -> e.lastAccess.isBefore(cutoff));
        emailBuckets.values().removeIf(e -> e.lastAccess.isBefore(cutoff));
        generalBuckets.values().removeIf(e -> e.lastAccess.isBefore(cutoff));
    }

    private static final class Entry {
        final Bucket bucket;
        volatile Instant lastAccess;

        Entry(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccess = Instant.now();
        }
    }
}
