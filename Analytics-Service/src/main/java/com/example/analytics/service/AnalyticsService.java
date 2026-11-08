package com.example.analytics.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AnalyticsService {
    private final JdbcTemplate jdbcTemplate;

    public AnalyticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getSummary() {
        Long transactions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_transactions", Long.class);
        Long processed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_transactions WHERE status = 'PROCESSED'",
                Long.class);
        Long failed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_transactions WHERE status = 'FAILED'",
                Long.class);
        Long walletEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallet_events", Long.class);

        return Map.of(
                "totalTransactions", transactions == null ? 0L : transactions,
                "processedTransactions", processed == null ? 0L : processed,
                "failedTransactions", failed == null ? 0L : failed,
                "walletEvents", walletEvents == null ? 0L : walletEvents
        );
    }
}
