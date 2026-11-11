package com.example.analytics.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class AnalyticsRepository
{
    private final JdbcTemplate jdbcTemplate;

    public AnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getPaymentSummary() {
        return jdbcTemplate.queryForMap("""
            WITH latest_events AS (
                SELECT
                    transaction_id,
                    status,
                    ROW_NUMBER() OVER (
                        PARTITION BY transaction_id
                        ORDER BY occurred_at DESC, event_id DESC
                    ) AS row_num
                FROM payment_transaction_events
            )
            SELECT
                COUNT(*) AS total_transactions,
                COUNT(*) FILTER (
                    WHERE status = 'PROCESSED'
                ) AS successful_transactions,
                COUNT(*) FILTER (
                    WHERE status = 'FAILED'
                ) AS failed_transactions
            FROM latest_events
            WHERE row_num = 1
            """);
    }

    public List<Map<String, Object>> getPaymentTrends() {
        return jdbcTemplate.queryForList("""
            SELECT
                CAST(occurred_at AS DATE) AS payment_date,
                currency,
                COUNT(*) AS total_transactions,
                SUM(amount) AS total_amount
            FROM payment_transaction_events
            WHERE event_type = 'PAYMENT_CREATED'
            GROUP BY CAST(occurred_at AS DATE), currency
            ORDER BY payment_date
            """);
    }

    public List<Map<String, Object>> getProcessingStatistics() {
        return jdbcTemplate.queryForList("""
            SELECT
                service_name,
                COUNT(*) AS total_events,
                ROUND(AVG(processing_duration_ms), 2)
                    AS average_duration_ms,
                MAX(processing_duration_ms) AS max_duration_ms,
                COUNT(*) FILTER (
                    WHERE status = 'FAILED'
                ) AS failed_events
            FROM payment_processing_events
            GROUP BY service_name
            ORDER BY average_duration_ms DESC
            """);
    }

    public List<Map<String, Object>> getWalletStatistics() {
        return jdbcTemplate.queryForList("""
            SELECT
                wallet_id,
                currency,
                COUNT(*) AS total_events,
                SUM(
                    CASE WHEN event_type = 'DEBIT'
                         THEN amount ELSE 0 END
                ) AS total_debit,
                SUM(
                    CASE WHEN event_type = 'CREDIT'
                         THEN amount ELSE 0 END
                ) AS total_credit
            FROM wallet_events
            GROUP BY wallet_id, currency
            ORDER BY total_events DESC
            LIMIT 20
            """);
    }
}
