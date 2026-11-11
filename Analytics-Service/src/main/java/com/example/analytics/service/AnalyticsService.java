package com.example.analytics.service;

import com.example.analytics.repository.AnalyticsRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {
    private final AnalyticsRepository repository;

    public AnalyticsService(AnalyticsRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> getPaymentSummary() {
        Map<String, Object> summary = repository.getPaymentSummary();

        long total = ((Number) summary.get("total_transactions")).longValue();
        long successful = ((Number) summary.get("successful_transactions")).longValue();

        double successRate = total == 0
                ? 0.0
                : successful * 100.0 / total;

        summary.put("success_rate", Math.round(successRate * 100.0) / 100.0);

        return summary;
    }

    public List<Map<String, Object>> getPaymentTrends() {
        return repository.getPaymentTrends();
    }

    public List<Map<String, Object>> getProcessingStatistics() {
        return repository.getProcessingStatistics();
    }

    public List<Map<String, Object>> getWalletStatistics() {
        return repository.getWalletStatistics();
    }
}
