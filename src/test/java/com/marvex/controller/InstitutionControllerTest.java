package com.marvex.controller;

import com.marvex.service.InstitutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstitutionControllerTest {

    @Mock InstitutionService institutionService;
    @InjectMocks InstitutionController controller;

    @Test
    void list_returnsInstitutionServiceBodyAsIs() {
        String body = "{\"data\":[{\"id\":\"inst-1\"}]}";
        when(institutionService.getInstitutionsJson()).thenReturn(body);

        ResponseEntity<String> resp = controller.list();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(body);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }
}
