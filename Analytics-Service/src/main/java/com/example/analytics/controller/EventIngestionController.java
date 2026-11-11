package com.example.analytics.controller;

import com.example.analytics.ingestion.dto.PaymentProcessingEvent;
import com.example.analytics.ingestion.dto.PaymentTransactionEvent;
import com.example.analytics.ingestion.dto.WalletEvent;
import com.example.analytics.service.EventIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/events")
public class EventIngestionController {
    private final EventIngestionService ingestionService;

    public EventIngestionController(EventIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/payments")
    public ResponseEntity<Map<String, Object>> ingestPayment(
            @RequestBody PaymentTransactionEvent event) {

        boolean inserted = ingestionService.ingestPaymentEvent(event);

        return ResponseEntity.ok(Map.of("inserted", inserted));
    }

    @PostMapping("/wallets")
    public ResponseEntity<Map<String, Object>> ingestWallet(
            @RequestBody WalletEvent event) {

        boolean inserted = ingestionService.ingestWalletEvent(event);

        return ResponseEntity.ok(Map.of("inserted", inserted));
    }

    @PostMapping("/processing")
    public ResponseEntity<Map<String, Object>> ingestProcessing(
            @RequestBody PaymentProcessingEvent event) {

        boolean inserted = ingestionService.ingestProcessingEvent(event);

        return ResponseEntity.ok(Map.of("inserted", inserted));
    }
}
