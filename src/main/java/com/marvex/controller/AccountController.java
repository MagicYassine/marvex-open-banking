package com.marvex.controller;

import com.marvex.dto.AccountResponse;
import com.marvex.dto.ChangePasswordRequest;
import com.marvex.dto.UpdateAccountRequest;
import com.marvex.entity.Merchant;
import com.marvex.service.AccountService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public AccountResponse get(@AuthenticationPrincipal Merchant merchant) {
        log.info("[AccountController] GET /account - merchantId={}", merchant.getId());
        return accountService.getAccount(merchant);
    }

    @PutMapping
    public AccountResponse update(@AuthenticationPrincipal Merchant merchant,
                                  @Valid @RequestBody UpdateAccountRequest req) {
        log.info("[AccountController] PUT /account - merchantId={}", merchant.getId());
        AccountResponse result = accountService.updateAccount(merchant, req);
        log.info("[AccountController] PUT /account - updated, merchantId={}", merchant.getId());
        return result;
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Merchant merchant,
                               @Valid @RequestBody ChangePasswordRequest req) {
        log.info("[AccountController] PUT /account/password - merchantId={}", merchant.getId());
        accountService.changePassword(merchant, req);
        log.info("[AccountController] PUT /account/password - changed, all sessions revoked for merchantId={}", merchant.getId());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Merchant merchant) {
        log.info("[AccountController] DELETE /account - merchantId={}", merchant.getId());
        accountService.deleteAccount(merchant);
        log.info("[AccountController] DELETE /account - deleted merchantId={}", merchant.getId());
    }

    @GetMapping("/export")
    public void exportData(@AuthenticationPrincipal Merchant merchant,
                           HttpServletResponse response) throws IOException {
        log.info("[AccountController] GET /account/export - merchantId={}", merchant.getId());
        accountService.exportPaymentsCsv(merchant, response);
    }
}
