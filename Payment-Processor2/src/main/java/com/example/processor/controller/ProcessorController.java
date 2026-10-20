package com.example.processor.controller;

import com.example.processor.data.BalanceUpdateRequest;
import com.example.processor.repository.PaymentRepository;
import com.example.processor.data.Payment;
import com.example.processor.dto.PaymentRequest;
import com.example.processor.dto.PaymentResponse;
import com.example.processor.dto.PaymentResponseError;
import com.example.processor.dto.WalletRequest;
import com.example.processor.dto.WalletResponse;
import com.example.processor.utils.ValidatePaymentRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@RestController
public class ProcessorController {
    private static final Logger log = LoggerFactory.getLogger(ProcessorController.class);
    private final PaymentRepository repository;
    private final RestClient walletClient;

    public ProcessorController(PaymentRepository repository,
                               @Value("${wallet.url:http://wallet-service}") String walletUrl) {
        this.repository = repository;
        this.walletClient = RestClient.builder().baseUrl(walletUrl).build();
    }

    @PostMapping("/process")
    public ResponseEntity<?> process(@RequestBody PaymentRequest request, HttpServletRequest httpServletRequest) {
        String traceId = httpServletRequest.getHeader("X-Trace-Id");
        try {
            ValidatePaymentRequest.validateRequest(request, traceId);
            if (Objects.isNull(request.paymentId())) {
                ResponseEntity<String> validationResponse = walletClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/wallets/validate")
                                .queryParam("fromUsrId", request.fromUsrId())
                                .queryParam("toUsrId", request.toUsrId())
                                .queryParam("amount", request.amount())
                                .build())
                        .retrieve()
                        .toEntity(String.class);

                if (!validationResponse.getStatusCode().is2xxSuccessful()) {
                    log.error("Wallet validation failed: {}", validationResponse.getBody());
                    throw new Exception(validationResponse.getBody());
                }

                Payment payment = new Payment();
                payment.setAmount(request.amount());
                payment.setCurrency(request.currency());
                payment.setStatus("PROCESSING");
                payment.setFromUsrId(request.fromUsrId());
                payment.setToUsrId(request.toUsrId());
                Payment saved = repository.save(payment);
                log.info("Success saving payment record {}", saved.getId());

                try {
                    walletClient.post()
                            .uri("/wallets/update-balance")
                            .body(new BalanceUpdateRequest(saved.getFromUsrId(), saved.getToUsrId(), saved.getAmount()))
                            .retrieve()
                            .toBodilessEntity();

                } catch (Exception e) {
                    log.error("Failed to update balances for payment {}: {}", saved.getId(), e.getMessage());
                    saved.setStatus("FAILED");
                    repository.save(saved);
                    return ResponseEntity.internalServerError().body(new PaymentResponseError(traceId, "Wallet update failed: " + e.getMessage()));
                }

                return ResponseEntity.ok(new PaymentResponse(saved.getId(), saved.getAmount(), saved.getCurrency(), saved.getStatus(), saved.getFromUsrId(), saved.getToUsrId()));
            }
            Payment referenceById = repository.getReferenceById(request.paymentId());
            if ("PROCESSING".equals(referenceById.getStatus()) || "CANCELLED".equals(referenceById.getStatus()) || "PROCESSED".equals(referenceById.getStatus())) {
                log.error("Error while process payment record, recordId: {}, status: {} ", referenceById.getId(), referenceById.getStatus());
                return ResponseEntity.ok().body(new PaymentResponseError(traceId, "Payment already in status: " + referenceById.getStatus()));
            }
        } catch (Exception e) {
            log.error("Error while processing payment record: {}", e.getMessage());
            return ResponseEntity.ok(new PaymentResponseError(traceId, e.getMessage()));
        }
        return ResponseEntity.internalServerError().body(new PaymentResponseError(traceId, "Unexpected error occurred"));
    }

    @PostMapping("/wallets")
    public ResponseEntity<WalletResponse> createWallet(@RequestBody WalletRequest request) {
        return walletClient.post()
                .uri("/wallets")
                .body(request)
                .retrieve()
                .toEntity(WalletResponse.class);
    }
}