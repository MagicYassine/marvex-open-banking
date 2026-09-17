package com.marvex.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendInvoiceRequest(
    @NotBlank @Email String recipientEmail
) {}
