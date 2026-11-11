package com.example.analytics.ingestion.dto;

import java.time.Instant;

public record PaymentProcessingEvent(String eventId, String transactionId, String serviceName,
                                     String status, String errorCode, String errorMessage,
                                     Long processingDurationMs, Instant createdAt) {}
