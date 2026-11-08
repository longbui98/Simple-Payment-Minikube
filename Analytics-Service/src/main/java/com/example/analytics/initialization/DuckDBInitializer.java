package com.example.analytics.initialization;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DuckDBInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public DuckDBInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS payment_transactions (
                transaction_id VARCHAR PRIMARY KEY,
                from_user_id VARCHAR,
                to_user_id VARCHAR,
                amount DECIMAL(18, 2) NOT NULL,
                currency VARCHAR(3),
                status VARCHAR NOT NULL,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP,
                processing_duration_ms BIGINT
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS wallet_events (
                event_id VARCHAR PRIMARY KEY,
                transaction_id VARCHAR NOT NULL,
                wallet_id VARCHAR NOT NULL,
                event_type VARCHAR NOT NULL,
                amount DECIMAL(18, 2) NOT NULL,
                currency VARCHAR(3),
                created_at TIMESTAMP NOT NULL
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS payment_processing_events (
                event_id VARCHAR PRIMARY KEY,
                transaction_id VARCHAR NOT NULL,
                service_name VARCHAR NOT NULL,
                status VARCHAR NOT NULL,
                error_code VARCHAR,
                error_message VARCHAR,
                processing_duration_ms BIGINT,
                created_at TIMESTAMP NOT NULL
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS analytics_schema_version (
                version INTEGER PRIMARY KEY,
                description VARCHAR NOT NULL,
                installed_at TIMESTAMP NOT NULL
            )
            """);

        jdbcTemplate.update("""
            INSERT INTO analytics_schema_version (version, description, installed_at)
            SELECT 1, 'Initial payment analytics schema', CURRENT_TIMESTAMP
            WHERE NOT EXISTS (
                SELECT 1 FROM analytics_schema_version WHERE version = 1
            )
            """);
    }
}
