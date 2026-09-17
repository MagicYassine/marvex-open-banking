package com.marvex.dto;

import java.util.UUID;

public record AccountResponse(
    UUID id,
    String email,
    String businessName,
    String institutionId,
    String payeeName,
    String sortCode,
    String accountNumber
) {}
