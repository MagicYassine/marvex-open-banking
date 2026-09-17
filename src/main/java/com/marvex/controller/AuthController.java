package com.marvex.controller;

import com.marvex.constants.AuthCookies;
import com.marvex.dto.*;
import com.marvex.exception.AppException;
import com.marvex.service.AuthService;
import com.marvex.util.LogMask;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@Valid @RequestBody RegisterRequest req) {
        log.info("[AuthController] POST /auth/register - email={}", LogMask.maskEmail(req.email()));
        authService.register(req);
        log.info("[AuthController] POST /auth/register - done, email={}", LogMask.maskEmail(req.email()));
    }

    @PostMapping("/complete-registration")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeRegistration(@Valid @RequestBody CompleteRegistrationRequest req) {
        log.info("[AuthController] POST /auth/complete-registration - token={}", LogMask.maskToken(req.token()));
        authService.completeRegistration(req);
        log.info("[AuthController] POST /auth/complete-registration - success, token={}", LogMask.maskToken(req.token()));
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        log.info("[AuthController] POST /auth/forgot-password - email={}", LogMask.maskEmail(req.email()));
        authService.forgotPassword(req);
        log.info("[AuthController] POST /auth/forgot-password - done, email={}", LogMask.maskEmail(req.email()));
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        log.info("[AuthController] POST /auth/reset-password - token={}", LogMask.maskToken(req.token()));
        authService.resetPassword(req);
        log.info("[AuthController] POST /auth/reset-password - success");
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletResponse response) {
        log.info("[AuthController] POST /auth/login - email={}", LogMask.maskEmail(req.email()));
        AuthResponse result = authService.login(req, response);
        log.info("[AuthController] POST /auth/login - success, email={}", LogMask.maskEmail(req.email()));
        return result;
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        log.info("[AuthController] POST /auth/logout");
        // Revoke refresh token if present
        Arrays.stream(request.getCookies() != null ? request.getCookies() : new Cookie[0])
            .filter(c -> AuthCookies.REFRESH_TOKEN_COOKIE.equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .ifPresent(authService::logout);
        // Clear the cookie
        Cookie clear = new Cookie(AuthCookies.REFRESH_TOKEN_COOKIE, "");
        clear.setHttpOnly(true);
        clear.setSecure(true);
        clear.setAttribute(AuthCookies.SAME_SITE_ATTRIBUTE, AuthCookies.SAME_SITE_STRICT);
        clear.setPath(AuthCookies.REFRESH_TOKEN_COOKIE_PATH);
        clear.setMaxAge(0);
        response.addCookie(clear);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        log.info("[AuthController] POST /auth/refresh");
        String token = Arrays.stream(request.getCookies() != null ? request.getCookies() : new Cookie[0])
            .filter(c -> AuthCookies.REFRESH_TOKEN_COOKIE.equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElseThrow(() -> new AppException(
                HttpStatus.UNAUTHORIZED, "No refresh token"));
        AuthResponse result = authService.refresh(token, response);
        log.info("[AuthController] POST /auth/refresh - success");
        return result;
    }
}
