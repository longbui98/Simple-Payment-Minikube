package com.example.analytics.ingestion.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentTransactionEvent(String eventId, String transactionId, String eventType,
                                      String status, BigDecimal amount, String currency, Instant occurredAt) {}