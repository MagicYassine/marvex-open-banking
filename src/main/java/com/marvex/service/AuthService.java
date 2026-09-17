package com.marvex.service;

import com.marvex.constants.AuthCookies;
import com.marvex.dto.*;
import com.marvex.entity.Merchant;
import com.marvex.entity.RefreshToken;
import lombok.extern.slf4j.Slf4j;
import com.marvex.exception.AppException;
import com.marvex.repository.MerchantRepository;
import com.marvex.repository.RefreshTokenRepository;
import com.marvex.security.JwtUtil;
import com.marvex.util.LogMask;
import com.marvex.util.PayoutInstitutions;
import com.marvex.util.TokenUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final MerchantRepository merchantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final AuditService auditService;

    @Value("${jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    private static final long VERIFICATION_TOKEN_EXPIRY_MS = 24 * 60 * 60 * 1000L;

    private void setVerificationToken(Merchant merchant, String token) {
        merchant.setVerificationToken(TokenUtil.sha256(token));
        merchant.setVerificationTokenExpiresAt(Instant.now().plusMillis(VERIFICATION_TOKEN_EXPIRY_MS));
    }

    @Transactional
    public void register(RegisterRequest req) {
        log.info("[AuthService] register - email={}", LogMask.maskEmail(req.email()));
        Merchant merchant = merchantRepository.findByEmail(req.email()).orElse(null);

        if (merchant != null) {
            if (merchant.isVerified()) {
                log.warn("[AuthService] register - email already registered and verified: {}", LogMask.maskEmail(req.email()));
                throw new AppException(HttpStatus.CONFLICT, "Email already registered");
            }
            log.info("[AuthService] register - unverified merchant exists, resending verification email: {}", LogMask.maskEmail(req.email()));
            String newToken = UUID.randomUUID().toString();
            setVerificationToken(merchant, newToken);
            merchantRepository.save(merchant);
            try {
                emailService.sendVerificationEmail(req.email(), newToken);
                log.info("[AuthService] register - verification email resent to {}", LogMask.maskEmail(req.email()));
            } catch (Exception e) {
                log.warn("[AuthService] Failed to resend verification email to {}: {}", LogMask.maskEmail(req.email()), e.getMessage());
            }
            return;
        }

        log.info("[AuthService] register - creating new merchant for email={}", LogMask.maskEmail(req.email()));
        String token = UUID.randomUUID().toString();
        merchant = new Merchant();
        merchant.setEmail(req.email());
        merchant.setBusinessName(req.email());
        setVerificationToken(merchant, token);
        merchantRepository.save(merchant);
        auditService.log(merchant.getId(), "REGISTER", "New merchant registered: " + req.email());
        log.info("[AuthService] register - merchant saved, sending verification email to {}", LogMask.maskEmail(req.email()));
        try {
            emailService.sendVerificationEmail(req.email(), token);
            log.info("[AuthService] register - verification email sent to {}", LogMask.maskEmail(req.email()));
        } catch (Exception e) {
            log.warn("[AuthService] Failed to send verification email to {}: {}", LogMask.maskEmail(req.email()), e.getMessage());
        }
    }

    @Transactional
    public void completeRegistration(CompleteRegistrationRequest req) {
        log.info("[AuthService] completeRegistration - token={}", LogMask.maskToken(req.token()));
        Merchant merchant = merchantRepository.findByVerificationToken(TokenUtil.sha256(req.token()))
            .orElseThrow(() -> {
                log.warn("[AuthService] completeRegistration - token not found: {}", LogMask.maskToken(req.token()));
                return new AppException(HttpStatus.BAD_REQUEST, "Link already used or invalid. Please register again to get a new link");
            });

        log.info("[AuthService] completeRegistration - merchant found: email={}, verified={}, tokenExpiresAt={}", LogMask.maskEmail(merchant.getEmail()), merchant.isVerified(), merchant.getVerificationTokenExpiresAt());

        if (merchant.getVerificationTokenExpiresAt() != null &&
                merchant.getVerificationTokenExpiresAt().isBefore(Instant.now())) {
            if (merchant.isVerified()) {
                log.warn("[AuthService] completeRegistration - token already used (verified), email={}", LogMask.maskEmail(merchant.getEmail()));
                throw new AppException(HttpStatus.BAD_REQUEST, "This link has already been used. Please login instead");
            }
            log.info("[AuthService] completeRegistration - token expired, resending new link to {}", LogMask.maskEmail(merchant.getEmail()));
            String newToken = UUID.randomUUID().toString();
            setVerificationToken(merchant, newToken);
            merchantRepository.save(merchant);
            try {
                emailService.sendVerificationEmail(merchant.getEmail(), newToken);
                log.info("[AuthService] completeRegistration - new verification email sent to {}", LogMask.maskEmail(merchant.getEmail()));
            } catch (Exception e) {
                log.warn("[AuthService] Failed to resend verification email to {}: {}", LogMask.maskEmail(merchant.getEmail()), e.getMessage());
            }
            throw new AppException(HttpStatus.GONE, "Link expired, check your email for a new one");
        }

        if (!PayoutInstitutions.isValid(req.institutionId())) {
            log.warn("[AuthService] completeRegistration - invalid institutionId={}, email={}", req.institutionId(), LogMask.maskEmail(merchant.getEmail()));
            throw new AppException(HttpStatus.BAD_REQUEST, PayoutInstitutions.INVALID_INSTITUTION_MESSAGE);
        }

        log.info("[AuthService] completeRegistration - completing registration for email={}", LogMask.maskEmail(merchant.getEmail()));
        merchant.setBusinessName(req.businessName());
        merchant.setPasswordHash(passwordEncoder.encode(req.password()));
        merchant.setInstitutionId(req.institutionId());
        merchant.setPayeeName(req.payeeName());
        merchant.setSortCode(req.sortCode());
        merchant.setAccountNumber(req.accountNumber());
        merchant.setVerified(true);
        merchant.setVerificationTokenExpiresAt(Instant.EPOCH);
        merchantRepository.save(merchant);
        log.info("[AuthService] completeRegistration - success, email={}", LogMask.maskEmail(merchant.getEmail()));
        auditService.log(merchant.getId(), "REGISTRATION_COMPLETED",
            "businessName=" + req.businessName() + " institutionId=" + req.institutionId());
        try {
            emailService.sendWelcomeEmail(merchant.getEmail(), merchant.getBusinessName());
        } catch (Exception e) {
            log.warn("[AuthService] Failed to send welcome email to {}: {}", LogMask.maskEmail(merchant.getEmail()), e.getMessage());
        }
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        log.info("[AuthService] logout - revoking refresh token");
        String hash = TokenUtil.sha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            auditService.log(rt.getMerchant().getId(), "LOGOUT", null);
            log.info("[AuthService] logout - token revoked for merchantId={}", rt.getMerchant().getId());
        });
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        log.info("[AuthService] forgotPassword - email={}", LogMask.maskEmail(req.email()));
        Merchant merchant = merchantRepository.findByEmail(req.email()).orElse(null);
        if (merchant == null || !merchant.isVerified()) {
            log.info("[AuthService] forgotPassword - email not found or not verified, silently ignoring: {}", LogMask.maskEmail(req.email()));
            return;
        }
        String token = UUID.randomUUID().toString();
        merchant.setPasswordResetToken(TokenUtil.sha256(token));
        merchant.setPasswordResetTokenExpiresAt(Instant.now().plusMillis(60 * 60 * 1000L));
        merchantRepository.save(merchant);
        log.info("[AuthService] forgotPassword - reset token saved, sending email to {}", LogMask.maskEmail(req.email()));
        try {
            emailService.sendPasswordResetEmail(merchant.getEmail(), token);
            log.info("[AuthService] forgotPassword - reset email sent to {}", LogMask.maskEmail(req.email()));
        } catch (Exception e) {
            log.warn("[AuthService] Failed to send password reset email to {}: {}", LogMask.maskEmail(merchant.getEmail()), e.getMessage());
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        log.info("[AuthService] resetPassword - token={}", LogMask.maskToken(req.token()));
        Merchant merchant = merchantRepository.findByPasswordResetToken(TokenUtil.sha256(req.token()))
            .orElseThrow(() -> {
                log.warn("[AuthService] resetPassword - token not found: {}", LogMask.maskToken(req.token()));
                return new AppException(HttpStatus.BAD_REQUEST, "Invalid or expired reset link");
            });
        if (merchant.getPasswordResetTokenExpiresAt().isBefore(Instant.now())) {
            log.warn("[AuthService] resetPassword - token expired for email={}", LogMask.maskEmail(merchant.getEmail()));
            throw new AppException(HttpStatus.GONE, "Reset link expired, please request a new one");
        }
        merchant.setPasswordHash(passwordEncoder.encode(req.password()));
        merchant.setPasswordResetToken(null);
        merchant.setPasswordResetTokenExpiresAt(null);
        merchantRepository.save(merchant);
        refreshTokenRepository.revokeAllForMerchant(merchant);
        auditService.log(merchant.getId(), "PASSWORD_RESET", null);
        log.info("[AuthService] resetPassword - success, all sessions revoked for email={}", LogMask.maskEmail(merchant.getEmail()));
    }

    private static final int MAX_LOGIN_ATTEMPTS = 10;
    private static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000L; // 15 minutes

    @Transactional
    public AuthResponse login(LoginRequest req, HttpServletResponse response) {
        log.info("[AuthService] login - email={}", LogMask.maskEmail(req.email()));
        Merchant merchant = merchantRepository.findByEmail(req.email())
            .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        // Check account lockout
        if (merchant.getLockedUntil() != null && merchant.getLockedUntil().isAfter(Instant.now())) {
            log.warn("[AuthService] login - account locked, merchantId={}", merchant.getId());
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "Account temporarily locked. Please try again later.");
        }

        // Unverified accounts cannot log in — check before password to avoid leaking attempt count
        if (!merchant.isVerified()) {
            throw new AppException(HttpStatus.FORBIDDEN, "Account not verified. Please check your email for the verification link");
        }

        if (merchant.getPasswordHash() == null || !passwordEncoder.matches(req.password(), merchant.getPasswordHash())) {
            int attempts = merchant.getLoginAttempts() + 1;
            merchant.setLoginAttempts(attempts);
            if (attempts >= MAX_LOGIN_ATTEMPTS) {
                merchant.setLockedUntil(Instant.now().plusMillis(LOCKOUT_DURATION_MS));
                merchant.setLoginAttempts(0);
                auditService.log(merchant.getId(), "ACCOUNT_LOCKED",
                    "Locked after " + attempts + " failed attempts",
                    null, AuditService.MERCHANT, AuditService.FAILURE, "Max login attempts exceeded");
                log.warn("[AuthService] login - account locked after {} attempts, merchantId={}", attempts, merchant.getId());
            } else {
                auditService.log(merchant.getId(), "LOGIN_FAILED",
                    "Failed attempt " + attempts + "/" + MAX_LOGIN_ATTEMPTS,
                    null, AuditService.MERCHANT, AuditService.FAILURE, "Invalid credentials");
            }
            merchantRepository.save(merchant);
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        merchant.setLoginAttempts(0);
        merchant.setLockedUntil(null);
        merchantRepository.save(merchant);

        String accessToken = jwtUtil.generateAccessToken(merchant.getId());
        issueRefreshToken(merchant, response);
        auditService.log(merchant.getId(), "LOGIN_SUCCESS", null);
        log.info("[AuthService] login - success, merchantId={}", merchant.getId());
        return new AuthResponse(accessToken);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, HttpServletResponse response) {
        log.info("[AuthService] refresh - attempting token refresh");
        String hash = TokenUtil.sha256(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
            .orElseThrow(() -> {
                log.warn("[AuthService] refresh - token hash not found");
                return new AppException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
            });

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            log.warn("[AuthService] refresh - token revoked or expired for merchantId={}", stored.getMerchant().getId());
            refreshTokenRepository.revokeAllForMerchant(stored.getMerchant());
            throw new AppException(HttpStatus.UNAUTHORIZED, "Refresh token invalid or expired");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        String accessToken = jwtUtil.generateAccessToken(stored.getMerchant().getId());
        issueRefreshToken(stored.getMerchant(), response);
        log.info("[AuthService] refresh - success, merchantId={}", stored.getMerchant().getId());
        return new AuthResponse(accessToken);
    }

    private void issueRefreshToken(Merchant merchant, HttpServletResponse response) {
        String raw = UUID.randomUUID().toString();
        RefreshToken rt = new RefreshToken();
        rt.setMerchant(merchant);
        rt.setTokenHash(TokenUtil.sha256(raw));
        rt.setExpiresAt(Instant.now().plusMillis(refreshTokenExpiryMs));
        refreshTokenRepository.save(rt);

        Cookie cookie = new Cookie(AuthCookies.REFRESH_TOKEN_COOKIE, raw);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setAttribute(AuthCookies.SAME_SITE_ATTRIBUTE, AuthCookies.SAME_SITE_STRICT);
        cookie.setPath(AuthCookies.REFRESH_TOKEN_COOKIE_PATH);
        cookie.setMaxAge((int) (refreshTokenExpiryMs / 1000));
        response.addCookie(cookie);
    }
}
