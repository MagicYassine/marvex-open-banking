package com.marvex.dto;

import com.marvex.constants.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateAccountRequest(
    @NotBlank String businessName,
    @NotBlank String institutionId,
    @NotBlank String payeeName,
    @NotBlank @Pattern(regexp = ValidationPatterns.SORT_CODE_REGEX, message = ValidationPatterns.SORT_CODE_MESSAGE) String sortCode,
    @NotBlank @Pattern(regexp = ValidationPatterns.ACCOUNT_NUMBER_REGEX, message = ValidationPatterns.ACCOUNT_NUMBER_MESSAGE) String accountNumber
) {}
