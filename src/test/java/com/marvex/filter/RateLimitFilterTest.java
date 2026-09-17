package com.marvex.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain chain;

    private RateLimitFilter filter;

    @BeforeEach
    void setup() {
        filter = new RateLimitFilter();
    }

    private Map<String, ?> bucketMap(String field) {
        return (Map<String, ?>) ReflectionTestUtils.getField(filter, field);
    }

    @Test
    void loginRoute_exceedsCapacityOfFive_returns429AndBlocksChain() throws Exception {
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        when(request.getRemoteAddr()).thenReturn("1.1.1.1");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/auth/login");

        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, chain);
        }
        verify(chain, times(5)).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(5)).doFilter(request, response);
        verify(response).setStatus(429);
        verify(response).setContentType("application/json");
        assertThat(body.toString()).contains("Too many requests");
    }

    @Test
    void emailRoutes_registerAndForgotPassword_shareSameBucketPerIp() throws Exception {
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        when(request.getRemoteAddr()).thenReturn("2.2.2.2");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn(
                "/auth/register", "/auth/register", "/auth/forgot-password", "/auth/forgot-password");

        filter.doFilterInternal(request, response, chain);
        filter.doFilterInternal(request, response, chain);
        filter.doFilterInternal(request, response, chain);
        verify(chain, times(3)).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(3)).doFilter(request, response);
        verify(response).setStatus(429);
    }

    @Test
    void unmatchedRoute_fallsBackToGeneralBucket() throws Exception {
        when(request.getRemoteAddr()).thenReturn("3.3.3.3");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/payments/abc");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(bucketMap("generalBuckets")).containsKey("3.3.3.3");
        assertThat(bucketMap("loginBuckets")).isEmpty();
    }

    @Test
    void sameIp_reusesSameBucketAcrossCalls() throws Exception {
        when(request.getRemoteAddr()).thenReturn("4.4.4.4");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/payments/xyz");

        filter.doFilterInternal(request, response, chain);
        filter.doFilterInternal(request, response, chain);

        assertThat(bucketMap("generalBuckets")).hasSize(1);
        verify(chain, times(2)).doFilter(request, response);
    }

    @Test
    void resolveClientIp_usesRemoteAddr() throws Exception {
        when(request.getRemoteAddr()).thenReturn("9.9.9.9");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/general");

        filter.doFilterInternal(request, response, chain);

        assertThat(bucketMap("generalBuckets")).containsKey("9.9.9.9");
    }

    @Test
    void resolveClientIp_ignoresXForwardedForHeader() throws Exception {
        // A client-supplied X-Forwarded-For must never be trusted as the rate-limit key —
        // otherwise sending a different value on every request buckets around the limiter
        // entirely and also grows the bucket maps without bound. Tomcat's RemoteIpValve
        // (server.forward-headers-strategy=native) is the only thing allowed to translate
        // a forwarded header into getRemoteAddr(), and only for a configured trusted proxy.
        lenient().when(request.getHeader("X-Forwarded-For")).thenReturn("6.6.6.6, 7.7.7.7");
        when(request.getRemoteAddr()).thenReturn("5.5.5.5");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/general");

        filter.doFilterInternal(request, response, chain);

        assertThat(bucketMap("generalBuckets")).containsKey("5.5.5.5");
        assertThat(bucketMap("generalBuckets")).doesNotContainKey("6.6.6.6");
        verify(request, never()).getHeader("X-Forwarded-For");
    }
}
