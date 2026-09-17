package com.marvex.dto;

public record AnalyticsResponse(
    long totalRevenuePence,
    long paidToday,
    long paidThisWeek,
    long totalPaid
) {}
