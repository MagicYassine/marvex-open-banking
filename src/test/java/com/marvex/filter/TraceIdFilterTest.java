package com.marvex.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TraceIdFilterTest {

    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain chain;

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void doFilter_setsMdcAndTraceHeaderBeforeChain_thenClearsAfter() throws Exception {
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");
        when(request.getHeader("User-Agent")).thenReturn("test-agent");

        doAnswer(invocation -> {
            assertThat(MDC.get("traceId")).isNotBlank();
            assertThat(MDC.get("clientIp")).isEqualTo("1.2.3.4");
            assertThat(MDC.get("userAgent")).isEqualTo("test-agent");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        verify(response).setHeader(eq("X-Trace-Id"), anyString());
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("clientIp")).isNull();
        assertThat(MDC.get("userAgent")).isNull();
    }

    @Test
    void doFilter_missingUserAgent_defaultsToEmptyStringInMdc() throws Exception {
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");
        when(request.getHeader("User-Agent")).thenReturn(null);

        doAnswer(invocation -> {
            assertThat(MDC.get("userAgent")).isEqualTo("");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);
    }

    @Test
    void doFilter_chainThrows_stillClearsMdcInFinally() throws Exception {
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");
        when(request.getHeader("User-Agent")).thenReturn(null);
        doThrow(new ServletException("boom")).when(chain).doFilter(request, response);

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("clientIp")).isNull();
        assertThat(MDC.get("userAgent")).isNull();
    }

    @Test
    void resolveIp_ignoresXForwardedForHeader_alwaysUsesRemoteAddr() throws Exception {
        lenient().when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");
        when(request.getRemoteAddr()).thenReturn("8.8.8.8");
        when(request.getHeader("User-Agent")).thenReturn(null);

        doAnswer(invocation -> {
            assertThat(MDC.get("clientIp")).isEqualTo("8.8.8.8");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        verify(request, never()).getHeader("X-Forwarded-For");
    }
}
