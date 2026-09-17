package com.marvex.service;

import com.marvex.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstitutionServiceTest {

    @Mock RestTemplate restTemplate;

    private InstitutionService institutionService;

    @BeforeEach
    void setup() {
        institutionService = new InstitutionService();
        ReflectionTestUtils.setField(institutionService, "baseUrl", "https://api.yapily.com");
        ReflectionTestUtils.setField(institutionService, "basicToken", "test-token");
        ReflectionTestUtils.setField(institutionService, "restTemplate", restTemplate);
    }

    @Test
    void getInstitutionsJson_success_returnsProviderBodyAsIs() {
        String body = "{\"data\":[{\"id\":\"inst-1\"}]}";
        when(restTemplate.exchange(eq("https://api.yapily.com/institutions"), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));

        assertThat(institutionService.getInstitutionsJson()).isEqualTo(body);
    }

    @Test
    void getInstitutionsJson_providerUnreachable_throwsBadGateway() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> institutionService.getInstitutionsJson())
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void isLiveInstitution_returnsTrueOnlyForIdsInProviderResponse() {
        String body = "{\"data\":[{\"id\":\"inst-1\"},{\"id\":\"inst-2\"}]}";
        when(restTemplate.exchange(eq("https://api.yapily.com/institutions"), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));

        assertThat(institutionService.isLiveInstitution("inst-1")).isTrue();
        assertThat(institutionService.isLiveInstitution("mock-sandbox")).isFalse();
        assertThat(institutionService.isLiveInstitution(null)).isFalse();
    }
}
