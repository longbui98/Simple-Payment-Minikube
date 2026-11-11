package com.example.analytics.ingestion.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletEvent(String eventId, String transactionId, String walletId, String eventType,
        BigDecimal amount, String currency, Instant createdAt) {}
