package com.marvex.filter;

import com.marvex.constants.MdcKeys;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)
public class TraceIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String traceId   = UUID.randomUUID().toString();
        String ip        = resolveIp(req);
        String userAgent = req.getHeader("User-Agent");

        MDC.put(MdcKeys.TRACE_ID,   traceId);
        MDC.put(MdcKeys.CLIENT_IP,  ip);
        MDC.put(MdcKeys.USER_AGENT, userAgent != null ? userAgent : "");
        ((HttpServletResponse) response).setHeader("X-Trace-Id", traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MdcKeys.TRACE_ID);
            MDC.remove(MdcKeys.CLIENT_IP);
            MDC.remove(MdcKeys.USER_AGENT);
        }
    }

    private String resolveIp(HttpServletRequest req) {
        return req.getRemoteAddr();
    }
}
