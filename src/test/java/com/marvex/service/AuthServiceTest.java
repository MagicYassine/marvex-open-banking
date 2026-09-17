package com.marvex.service;

import com.marvex.dto.CompleteRegistrationRequest;
import com.marvex.dto.ForgotPasswordRequest;
import com.marvex.dto.LoginRequest;
import com.marvex.dto.RegisterRequest;
import com.marvex.dto.ResetPasswordRequest;
import com.marvex.entity.Merchant;
import com.marvex.entity.RefreshToken;
import com.marvex.exception.AppException;
import com.marvex.repository.MerchantRepository;
import com.marvex.repository.RefreshTokenRepository;
import com.marvex.security.JwtUtil;
import com.marvex.util.TokenUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock MerchantRepository merchantRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock EmailService emailService;
    @Mock AuditService auditService;
    @Mock HttpServletResponse httpResponse;
    @InjectMocks AuthService authService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryMs", 604800000L);
    }

    @Test
    void register_newEmail_savesAndSendsEmail() {
        when(merchantRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(merchantRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.register(new RegisterRequest("new@test.com"));

        verify(merchantRepository).save(any());
        verify(emailService).sendVerificationEmail(eq("new@test.com"), anyString());
    }

    @Test
    void register_alreadyVerified_throws409() {
        Merchant m = merchant(true);
        when(merchantRepository.findByEmail(m.getEmail())).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> authService.register(new RegisterRequest(m.getEmail())))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("already registered");
    }

    @Test
    void login_validCredentials_returnsToken() {
        Merchant m = merchant(true);
        when(merchantRepository.findByEmail(m.getEmail())).thenReturn(Optional.of(m));
        when(passwordEncoder.matches("password", m.getPasswordHash())).thenReturn(true);
        when(jwtUtil.generateAccessToken(m.getId())).thenReturn("access-token");
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = authService.login(new LoginRequest(m.getEmail(), "password"), httpResponse);

        assertThat(result.accessToken()).isEqualTo("access-token");
    }

    @Test
    void login_wrongPassword_throws401() {
        Merchant m = merchant(true);
        when(merchantRepository.findByEmail(m.getEmail())).thenReturn(Optional.of(m));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        when(merchantRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> authService.login(new LoginRequest(m.getEmail(), "wrong"), httpResponse))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_accountLocked_throws429() {
        Merchant m = merchant(true);
        m.setLockedUntil(Instant.now().plusSeconds(900));
        when(merchantRepository.findByEmail(m.getEmail())).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> authService.login(new LoginRequest(m.getEmail(), "any"), httpResponse))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("locked");
    }

    @Test
    void login_unverifiedAccount_throws403() {
        Merchant m = merchant(false);
        when(merchantRepository.findByEmail(m.getEmail())).thenReturn(Optional.of(m));
        // No password check needed — verified check happens first

        assertThatThrownBy(() -> authService.login(new LoginRequest(m.getEmail(), "password"), httpResponse))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("not verified");
    }

    @Test
    void login_tenFailedAttempts_locksAccount() {
        Merchant m = merchant(true);
        m.setLoginAttempts(9);
        when(merchantRepository.findByEmail(m.getEmail())).thenReturn(Optional.of(m));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        when(merchantRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> authService.login(new LoginRequest(m.getEmail(), "wrong"), httpResponse))
            .isInstanceOf(AppException.class);

        assertThat(m.getLockedUntil()).isNotNull().isAfter(Instant.now());
        assertThat(m.getLoginAttempts()).isZero();
    }

    @Test
    void forgotPassword_unknownEmail_silentlySucceeds() {
        when(merchantRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        authService.forgotPassword(new ForgotPasswordRequest("unknown@test.com"));

        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void verificationTokenIsHashed_notStoredRaw() {
        when(merchantRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(merchantRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.register(new RegisterRequest("new@test.com"));

        verify(merchantRepository).save(argThat(m -> {
            // Token stored must be a Base64-encoded SHA-256 hash (44 chars), not a raw UUID (36 chars)
            String stored = m.getVerificationToken();
            return stored != null && stored.length() == 44;
        }));
    }

    @Test
    void completeRegistration_success_setsVerifiedAndSendsWelcome() {
        Merchant m = merchant(false);
        String rawToken = "raw-token";
        m.setVerificationToken(TokenUtil.sha256(rawToken));
        m.setVerificationTokenExpiresAt(Instant.now().plusSeconds(3600));
        when(merchantRepository.findByVerificationToken(TokenUtil.sha256(rawToken)))
            .thenReturn(Optional.of(m));
        when(passwordEncoder.encode(any())).thenReturn("hashed-pw");
        when(merchantRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.completeRegistration(new CompleteRegistrationRequest(
            rawToken, "StrongPass1!", "My Biz", "monzo_ob", "John Doe", "040004", "12345678"));

        assertThat(m.isVerified()).isTrue();
        verify(emailService).sendWelcomeEmail(eq(m.getEmail()), any());
    }

    @Test
    void completeRegistration_expiredToken_resendAndThrows410() {
        Merchant m = merchant(false);
        String rawToken = "raw-token";
        m.setVerificationToken(TokenUtil.sha256(rawToken));
        m.setVerificationTokenExpiresAt(Instant.now().minusSeconds(1)); // expired
        when(merchantRepository.findByVerificationToken(TokenUtil.sha256(rawToken)))
            .thenReturn(Optional.of(m));
        when(merchantRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> authService.completeRegistration(
            new CompleteRegistrationRequest(
                rawToken, "StrongPass1!", "My Biz", "monzo_ob", "John Doe", "040004", "12345678")))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("expired");

        verify(emailService).sendVerificationEmail(eq(m.getEmail()), anyString());
    }

    @Test
    void resetPassword_success_revokesAllSessions() {
        Merchant m = merchant(true);
        String rawToken = "reset-token";
        m.setPasswordResetToken(TokenUtil.sha256(rawToken));
        m.setPasswordResetTokenExpiresAt(Instant.now().plusSeconds(3600));
        when(merchantRepository.findByPasswordResetToken(TokenUtil.sha256(rawToken)))
            .thenReturn(Optional.of(m));
        when(passwordEncoder.encode(any())).thenReturn("new-hash");
        when(merchantRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.resetPassword(new ResetPasswordRequest(rawToken, "NewPass1!"));

        assertThat(m.getPasswordHash()).isEqualTo("new-hash");
        assertThat(m.getPasswordResetToken()).isNull();
        verify(refreshTokenRepository).revokeAllForMerchant(m);
    }

    @Test
    void resetPassword_expiredToken_throws410() {
        Merchant m = merchant(true);
        String rawToken = "reset-token";
        m.setPasswordResetToken(TokenUtil.sha256(rawToken));
        m.setPasswordResetTokenExpiresAt(Instant.now().minusSeconds(1));
        when(merchantRepository.findByPasswordResetToken(TokenUtil.sha256(rawToken)))
            .thenReturn(Optional.of(m));

        assertThatThrownBy(() -> authService.resetPassword(
            new ResetPasswordRequest(rawToken, "NewPass1!")))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("expired");
    }

    @Test
    void logout_validToken_revokesToken() {
        Merchant m = merchant(true);
        String rawToken = "refresh-token";
        RefreshToken rt = new RefreshToken();
        rt.setMerchant(m);
        rt.setTokenHash(TokenUtil.sha256(rawToken));
        rt.setRevoked(false);
        when(refreshTokenRepository.findByTokenHash(TokenUtil.sha256(rawToken)))
            .thenReturn(Optional.of(rt));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.logout(rawToken);

        assertThat(rt.isRevoked()).isTrue();
    }

    @Test
    void register_unverifiedExists_resendEmail() {
        Merchant m = merchant(false);
        when(merchantRepository.findByEmail(m.getEmail())).thenReturn(Optional.of(m));
        when(merchantRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.register(new RegisterRequest(m.getEmail()));

        verify(emailService).sendVerificationEmail(eq(m.getEmail()), anyString());
    }

    private Merchant merchant(boolean verified) {
        Merchant m = new Merchant();
        m.setId(UUID.randomUUID());
        m.setEmail("test@test.com");
        m.setPasswordHash("hashed");
        m.setVerified(verified);
        m.setLoginAttempts(0);
        return m;
    }
}
