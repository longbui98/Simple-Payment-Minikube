package com.example.analytics.controller;

import com.example.analytics.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/payments/summary")
    public Map<String, Object> paymentSummary() {
        return analyticsService.getPaymentSummary();
    }

    @GetMapping("/payments/trends")
    public List<Map<String, Object>> paymentTrends() {
        return analyticsService.getPaymentTrends();
    }

    @GetMapping("/processing")
    public List<Map<String, Object>> processingStatistics() {
        return analyticsService.getProcessingStatistics();
    }

    @GetMapping("/wallets")
    public List<Map<String, Object>> walletStatistics() {
        return analyticsService.getWalletStatistics();
    }
}
