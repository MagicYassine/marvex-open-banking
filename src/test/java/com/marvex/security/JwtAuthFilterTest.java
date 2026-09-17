package com.marvex.security;

import com.marvex.entity.Merchant;
import com.marvex.repository.MerchantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock JwtUtil jwtUtil;
    @Mock MerchantRepository merchantRepository;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain chain;

    @InjectMocks JwtAuthFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerToken_knownMerchant_populatesSecurityContext() throws Exception {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant();
        merchant.setId(merchantId);

        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtUtil.isValid("good-token")).thenReturn(true);
        when(jwtUtil.extractMerchantId("good-token")).thenReturn(merchantId);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        filter.doFilterInternal(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(merchant);
        verify(chain).doFilter(request, response);
    }

    @Test
    void missingAuthorizationHeader_leavesContextUnauthenticated_stillContinuesChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void nonBearerAuthorizationHeader_leavesContextUnauthenticated_stillContinuesChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic abcdef");

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void invalidToken_leavesContextUnauthenticated_stillContinuesChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
        when(jwtUtil.isValid("bad-token")).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void validTokenButMerchantNoLongerExists_doesNotThrow_leavesContextUnauthenticated() throws Exception {
        UUID merchantId = UUID.randomUUID();
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtUtil.isValid("good-token")).thenReturn(true);
        when(jwtUtil.extractMerchantId("good-token")).thenReturn(merchantId);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
