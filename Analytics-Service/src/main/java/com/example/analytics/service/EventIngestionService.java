package com.example.analytics.service;

import com.example.analytics.ingestion.dto.PaymentProcessingEvent;
import com.example.analytics.ingestion.dto.PaymentTransactionEvent;
import com.example.analytics.ingestion.dto.WalletEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

@Service
public class EventIngestionService {
    private final JdbcTemplate jdbcTemplate;

    public EventIngestionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public boolean ingestPaymentEvent(PaymentTransactionEvent event) {
        validate(event.eventId(), event.transactionId(),
                event.eventType(), event.status());

        if (event.occurredAt() == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }

        return jdbcTemplate.update("""
            INSERT INTO payment_transaction_events (
                event_id,
                transaction_id,
                event_type,
                status,
                amount,
                currency,
                occurred_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """,
                event.eventId(),
                event.transactionId(),
                event.eventType(),
                event.status(),
                event.amount(),
                event.currency(),
                Timestamp.from(event.occurredAt())
        ) == 1;
    }

    @Transactional
    public boolean ingestWalletEvent(WalletEvent event) {
        validate(event.eventId(), event.transactionId(),
                event.walletId(), event.eventType());

        if (event.amount() == null || event.amount().signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        if (event.createdAt() == null) {
            throw new IllegalArgumentException("createdAt is required");
        }

        return jdbcTemplate.update("""
            INSERT INTO wallet_events (
                event_id,
                transaction_id,
                wallet_id,
                event_type,
                amount,
                currency,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """,
                event.eventId(),
                event.transactionId(),
                event.walletId(),
                event.eventType(),
                event.amount(),
                event.currency(),
                Timestamp.from(event.createdAt())
        ) == 1;
    }

    @Transactional
    public boolean ingestProcessingEvent(PaymentProcessingEvent event) {
        validate(event.eventId(), event.transactionId(),
                event.serviceName(), event.status());

        if (event.createdAt() == null) {
            throw new IllegalArgumentException("createdAt is required");
        }

        return jdbcTemplate.update("""
            INSERT INTO payment_processing_events (
                event_id,
                transaction_id,
                service_name,
                status,
                error_code,
                error_message,
                processing_duration_ms,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """,
                event.eventId(),
                event.transactionId(),
                event.serviceName(),
                event.status(),
                event.errorCode(),
                event.errorMessage(),
                event.processingDurationMs(),
                Timestamp.from(event.createdAt())
        ) == 1;
    }

    private void validate(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Required event field must not be blank");
            }
        }
    }
}
