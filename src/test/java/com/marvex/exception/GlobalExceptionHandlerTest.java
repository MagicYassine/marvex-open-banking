package com.marvex.exception;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void handleApp_includesStatusMessageAndTraceId() {
        MDC.put("traceId", "trace-123");

        ResponseEntity<Map<String, String>> resp =
            handler.handleApp(new AppException(HttpStatus.UNAUTHORIZED, "Invalid payment token"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody()).containsEntry("error", "Invalid payment token");
        assertThat(resp.getBody()).containsEntry("traceId", "trace-123");
    }

    @Test
    void handleApp_noTraceIdInMdc_omitsTraceIdField() {
        ResponseEntity<Map<String, String>> resp =
            handler.handleApp(new AppException(HttpStatus.NOT_FOUND, "Payment not found"));

        assertThat(resp.getBody()).containsEntry("error", "Payment not found");
        assertThat(resp.getBody()).doesNotContainKey("traceId");
    }

    @Test
    void handleValidation_aggregatesFieldErrorsIntoSingleMessage() throws NoSuchMethodException {
        MDC.put("traceId", "trace-456");
        Object target = new Object() {
            @SuppressWarnings("unused")
            public void dummy(String currency) {}
        };
        Method method = target.getClass().getMethod("dummy", String.class);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "createPaymentRequest");
        bindingResult.addError(new FieldError("createPaymentRequest", "currency", "Only GBP is supported"));

        MethodArgumentNotValidException ex =
            new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

        ResponseEntity<Map<String, String>> resp = handler.handleValidation(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("error")).isEqualTo("currency: Only GBP is supported");
        assertThat(resp.getBody()).containsEntry("traceId", "trace-456");
    }

    @Test
    void handleGeneric_neverLeaksRawExceptionMessageToClient() {
        ResponseEntity<Map<String, String>> resp =
            handler.handleGeneric(new RuntimeException("db password is hunter2"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().get("error")).isEqualTo("Internal server error");
        assertThat(resp.getBody().get("error")).doesNotContain("hunter2");
    }
}
