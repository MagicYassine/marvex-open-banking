package com.marvex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marvex.exception.AppException;
import com.marvex.provider.YapilyConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Fetches and caches the live institution list from the payment provider (Yapily).
 * Backs both the /institutions endpoint (payer-facing bank picker) and server-side
 * validation of any institutionId the client submits — a merchant or payer submitting
 * an institutionId string is not itself proof that institution exists or is a real,
 * money-moving institution (e.g. a provider sandbox/test institution).
 */
@Service
@Slf4j
public class InstitutionService {

    @Value("${yapily.base-url}")
    private String baseUrl;

    @Value("${yapily.basic-token}")
    private String basicToken;

    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private volatile String cachedJson;
    private volatile Set<String> cachedIds = Collections.emptySet();
    private volatile Instant cachedAt = Instant.EPOCH;

    private static RestTemplate createRestTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        return new RestTemplate(factory);
    }

    public synchronized String getInstitutionsJson() {
        refreshIfStale();
        return cachedJson;
    }

    /**
     * Whether institutionId is one of the provider's actual live institutions. Used to
     * reject an attacker-chosen institution (e.g. a test/sandbox institution ID) on the
     * payer-facing payment initiation path.
     */
    public boolean isLiveInstitution(String institutionId) {
        if (institutionId == null) return false;
        refreshIfStale();
        return cachedIds.contains(institutionId);
    }

    private synchronized void refreshIfStale() {
        if (cachedJson != null && Instant.now().isBefore(cachedAt.plus(CACHE_TTL))) {
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, YapilyConstants.BASIC_AUTH_PREFIX + basicToken);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/institutions",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );
            String body = response.getBody();
            cachedIds = parseIds(body);
            cachedJson = body;
            cachedAt = Instant.now();
        } catch (RestClientException e) {
            log.error("[InstitutionService] Failed to fetch institutions from provider: {}", e.getMessage());
            if (cachedJson != null) {
                // Serve the stale cache rather than fail every request/validation because
                // of a transient upstream blip.
                return;
            }
            throw new AppException(HttpStatus.BAD_GATEWAY, "Unable to reach payment provider, please try again later");
        }
    }

    private Set<String> parseIds(String body) {
        Set<String> ids = new HashSet<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode node : data) {
                    JsonNode id = node.path("id");
                    if (id.isTextual()) ids.add(id.asText());
                }
            }
        } catch (Exception e) {
            log.error("[InstitutionService] Failed to parse institutions response: {}", e.getMessage());
        }
        return ids;
    }
}
