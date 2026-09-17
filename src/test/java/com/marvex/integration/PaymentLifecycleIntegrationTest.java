package com.marvex.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marvex.dto.CompleteRegistrationRequest;
import com.marvex.dto.LoginRequest;
import com.marvex.dto.RegisterRequest;
import com.marvex.entity.Payment;
import com.marvex.enums.PaymentStatus;
import com.marvex.filter.RateLimitFilter;
import com.marvex.repository.PaymentRepository;
import com.marvex.service.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PaymentLifecycleIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("marvex_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PaymentRepository paymentRepository;
    @SpyBean EmailService emailService;

    // -----------------------------------------------------------------------
    // Full payment lifecycle
    // -----------------------------------------------------------------------

    @Test
    void fullPaymentLifecycle_createInitiateConfirm_paid() throws Exception {
        String token = registerAndLogin();

        // Create payment
        MvcResult createResult = mvc.perform(post("/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("amount", "5.00", "currency", "GBP"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentUrl").exists())
                .andReturn();

        Map<?, ?> createResp = mapper.readValue(createResult.getResponse().getContentAsString(), Map.class);
        String paymentId = createResp.get("paymentId").toString();
        String paymentToken = extractToken(createResp.get("paymentUrl").toString());

        // Initiate — transitions to PROCESSING
        mvc.perform(post("/payments/" + paymentId + "/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("token", paymentToken, "institutionId", "mock-sandbox"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.redirectUrl").exists());

        // Submit consent — as if returning from the bank's authorisation redirect.
        // confirm() below only calls the provider once a consent token is on record.
        mvc.perform(post("/payments/" + paymentId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("token", paymentToken, "consent", "mock-consent-token"))))
                .andExpect(status().isOk());

        // Confirm — mock provider returns PAID
        mvc.perform(post("/payments/" + paymentId + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("token", paymentToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        // DB state
        assertThat(paymentRepository.findById(UUID.fromString(paymentId)))
                .isPresent()
                .hasValueSatisfying(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAID));
    }

    @Test
    void getPayment_public_returnsPaymentDetails() throws Exception {
        String token = registerAndLogin();

        MvcResult createResult = mvc.perform(post("/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("amount", "1.50", "currency", "GBP"))))
                .andExpect(status().isCreated())
                .andReturn();

        String paymentId = mapper.readValue(
                createResult.getResponse().getContentAsString(), Map.class)
                .get("paymentId").toString();

        // GET /payments/:id is public — no auth needed
        mvc.perform(get("/payments/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void regenerate_expiredPayment_issuesNewToken() throws Exception {
        String token = registerAndLogin();

        MvcResult createResult = mvc.perform(post("/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("amount", "2.00", "currency", "GBP"))))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> createResp = mapper.readValue(createResult.getResponse().getContentAsString(), Map.class);
        String paymentId = createResp.get("paymentId").toString();

        // Force-expire the payment in DB
        Payment payment = paymentRepository.findById(UUID.fromString(paymentId)).orElseThrow();
        payment.setStatus(PaymentStatus.EXPIRED);
        paymentRepository.save(payment);

        // Regenerate
        MvcResult regenResult = mvc.perform(post("/payments/" + paymentId + "/regenerate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentUrl").exists())
                .andReturn();

        // New token should differ from original
        String newUrl = mapper.readValue(regenResult.getResponse().getContentAsString(), Map.class)
                .get("paymentUrl").toString();
        assertThat(newUrl).isNotEqualTo(createResp.get("paymentUrl").toString());
    }

    // -----------------------------------------------------------------------
    // Webhook
    // -----------------------------------------------------------------------

    @Test
    void webhook_invalidSignature_returns401() throws Exception {
        mvc.perform(post("/webhooks/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "providerPaymentId", "mock-123",
                                "status", "PAID",
                                "signature", "bad-sig"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhook_duplicateWebhook_idempotent() throws Exception {
        String token = registerAndLogin();

        MvcResult createResult = mvc.perform(post("/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("amount", "2.00", "currency", "GBP"))))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> createResp = mapper.readValue(createResult.getResponse().getContentAsString(), Map.class);
        String paymentId = createResp.get("paymentId").toString();
        String paymentToken = extractToken(createResp.get("paymentUrl").toString());

        mvc.perform(post("/payments/" + paymentId + "/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("token", paymentToken, "institutionId", "mock-sandbox"))))
                .andExpect(status().isOk());

        String providerPaymentId = paymentRepository.findById(UUID.fromString(paymentId))
                .orElseThrow().getProviderPaymentId();
        String sig = webhookSignature(providerPaymentId, "PAID");

        // First webhook — should succeed
        mvc.perform(post("/webhooks/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "providerPaymentId", providerPaymentId,
                                "status", "PAID",
                                "signature", sig))))
                .andExpect(status().isOk());

        // Duplicate webhook — idempotent, still 200
        mvc.perform(post("/webhooks/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "providerPaymentId", providerPaymentId,
                                "status", "PAID",
                                "signature", sig))))
                .andExpect(status().isOk());

        // Status unchanged — still PAID
        assertThat(paymentRepository.findById(UUID.fromString(paymentId))
                .orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    // -----------------------------------------------------------------------
    // Auth
    // -----------------------------------------------------------------------

    @Test
    void login_wrongPassword_returns401() throws Exception {
        String ip = uniqueIp();
        String email = registerOnly(ip);

        mvc.perform(post("/auth/login")
                        .with(remoteAddr(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(email, "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPassword_unknownEmail_returns204_noLeak() throws Exception {
        // Should silently succeed even for unknown emails — prevents email enumeration
        mvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", "nobody@nowhere.com"))))
                .andExpect(status().isNoContent());
    }

    // -----------------------------------------------------------------------
    // Rate limiting — uses a unique X-Forwarded-For IP to isolate from other tests
    // -----------------------------------------------------------------------

    @Test
    void rateLimiting_loginEndpoint_returns429AfterFiveRequests() throws Exception {
        // Use a unique IP so this test is isolated from bucket state of other tests
        String uniqueIp = uniqueIp();

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/auth/login")
                            .with(remoteAddr(uniqueIp))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(
                                    Map.of("email", "rate@test.com", "password", "wrong"))));
        }

        // 6th request over the limit
        mvc.perform(post("/auth/login")
                        .with(remoteAddr(uniqueIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                Map.of("email", "rate@test.com", "password", "wrong"))))
                .andExpect(status().isTooManyRequests());
    }

    // -----------------------------------------------------------------------
    // Security: protected endpoints require auth
    // -----------------------------------------------------------------------

    @Test
    void protectedEndpoint_noToken_returns403() throws Exception {
        mvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("amount", "1.00", "currency", "GBP"))))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Registers a new merchant and completes the full registration flow,
     * then logs in and returns the JWT access token.
     */
    private String registerAndLogin() throws Exception {
        String ip = uniqueIp();
        String email = registerOnly(ip);

        MvcResult loginResult = mvc.perform(post("/auth/login")
                        .with(remoteAddr(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(email, "Password1!"))))
                .andExpect(status().isOk())
                .andReturn();

        return mapper.readValue(loginResult.getResponse().getContentAsString(), Map.class)
                .get("accessToken").toString();
    }

    /**
     * Registers a new merchant and completes the email verification flow.
     * Returns the verified email address (can then be used to log in).
     */
    private String registerOnly() throws Exception {
        return registerOnly(uniqueIp());
    }

    /**
     * Same as {@link #registerOnly()} but issues the register/complete-registration
     * calls from the given IP — the {@code /auth/register} bucket is shared per-IP by
     * {@link RateLimitFilter} (capacity 3 per 10 minutes), and every
     * test method in this class shares the same default MockMvc remote address, so
     * without a unique IP per call the bucket empties after the 3rd invocation across
     * the whole class run and later tests get 429 instead of the expected response.
     */
    private String registerOnly(String ip) throws Exception {
        String email = "test-" + UUID.randomUUID() + "@test.com";

        mvc.perform(post("/auth/register")
                        .with(remoteAddr(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new RegisterRequest(email))))
                .andExpect(status().isNoContent());

        // Capture the raw verification token from the email service spy
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(eq(email), tokenCaptor.capture());
        String rawToken = tokenCaptor.getValue();

        mvc.perform(post("/auth/complete-registration")
                        .with(remoteAddr(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new CompleteRegistrationRequest(
                                rawToken, "Password1!", "Test Biz",
                                "monzo_ob", "Test Payee", "123456", "12345678"))))
                .andExpect(status().isNoContent());

        return email;
    }

    /** A pseudo-random IP for isolating a test's rate-limit bucket from other tests. */
    private String uniqueIp() {
        return "10.0." + (int) (Math.random() * 255) + "." + (int) (Math.random() * 255);
    }

    /**
     * Sets the request's actual remote address, rather than a spoofable X-Forwarded-For
     * header — RateLimitFilter and TraceIdFilter only trust request.getRemoteAddr() now,
     * so this is how tests isolate their rate-limit buckets from each other.
     */
    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> { request.setRemoteAddr(ip); return request; };
    }

    /** Extracts the token query parameter from a payment URL. */
    private String extractToken(String paymentUrl) {
        return paymentUrl.substring(paymentUrl.indexOf("token=") + 6);
    }

    /** Produces a valid HMAC-SHA256 webhook signature for the test webhook secret. */
    private String webhookSignature(String providerPaymentId, String status) throws Exception {
        String payload = String.format(
                "{\"providerPaymentId\":\"%s\",\"status\":\"%s\"}", providerPaymentId, status);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("webhook-shared-secret".getBytes(), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes()));
    }
}
