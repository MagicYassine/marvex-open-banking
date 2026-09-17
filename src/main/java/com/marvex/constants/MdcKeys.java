package com.marvex.constants;

/**
 * MDC keys populated by TraceIdFilter for the lifetime of a request, and
 * read back by AuditService/GlobalExceptionHandler — must match exactly or
 * audit entries/error responses silently lose trace correlation.
 */
public final class MdcKeys {

    private MdcKeys() {}

    public static final String TRACE_ID = "traceId";
    public static final String CLIENT_IP = "clientIp";
    public static final String USER_AGENT = "userAgent";
}
