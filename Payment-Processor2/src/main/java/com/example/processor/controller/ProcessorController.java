package com.example.processor.controller;

import com.example.processor.data.Payment;
import com.example.processor.dto.PaymentRequest;
import com.example.processor.dto.PaymentResponse;
import com.example.processor.dto.PaymentResponseError;
import com.example.processor.dto.WalletRequest;
import com.example.processor.dto.WalletResponse;
import com.example.processor.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProcessorController {
    private static final Logger log = LoggerFactory.getLogger(ProcessorController.class);
    private final PaymentService paymentService;

    public ProcessorController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/process")
    public ResponseEntity<?> process(@RequestBody PaymentRequest request, HttpServletRequest httpServletRequest) {
        String traceId = httpServletRequest.getHeader("X-Trace-Id");
        try {
            Payment payment = paymentService.processPayment(request, traceId);
            return ResponseEntity.ok(new PaymentResponse(
                    payment.getId(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getStatus(),
                    payment.getFromUsrId(),
                    payment.getToUsrId()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(new PaymentResponseError(traceId, e.getMessage()));
        } catch (Exception e) {
            log.error("Error while processing payment record: {}", e.getMessage());
            return ResponseEntity.ok(new PaymentResponseError(traceId, e.getMessage()));
        }
    }

    @PostMapping("/wallets")
    public ResponseEntity<WalletResponse> createWallet(@RequestBody WalletRequest request) {
        WalletResponse response = paymentService.createWallet(request);
        return ResponseEntity.ok(response);
    }
}