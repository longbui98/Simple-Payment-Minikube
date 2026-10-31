package com.example.processor.service;

import com.example.processor.data.BalanceUpdateRequest;
import com.example.processor.data.Payment;
import com.example.processor.dto.*;
import com.example.processor.repository.PaymentRepository;
import com.example.processor.utils.ValidatePaymentRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.annotation.SqsListenerAcknowledgementMode;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentRepository repository;
    private final RestClient walletClient;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository repository,
                          @Value("${wallet.url:http://wallet-service}") String walletUrl) {
        this.repository = repository;
        this.walletClient = RestClient.builder().baseUrl(walletUrl).build();
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public Payment processPayment(PaymentRequest request, String traceId) throws Exception {
        ValidatePaymentRequest.validateRequest(request, traceId);

        if (Objects.isNull(request.paymentId())) {
            validateWallet(request);

            Payment payment = new Payment();
            payment.setAmount(request.amount());
            payment.setCurrency(request.currency());
            payment.setStatus("PROCESSING");
            payment.setFromUsrId(request.fromUsrId());
            payment.setToUsrId(request.toUsrId());
            Payment saved = repository.save(payment);
            log.info("Success saving payment record {}", saved.getId());

            try {
                updateWalletBalance(saved);
            } catch (Exception e) {
                log.error("Failed to update balances for payment {}: {}", saved.getId(), e.getMessage());
                saved.setStatus("FAILED");
                repository.save(saved);
                throw new Exception("Wallet update failed: " + e.getMessage());
            }
            return saved;
        }

        Payment referenceById = repository.getReferenceById(request.paymentId());
        if ("PROCESSING".equals(referenceById.getStatus()) || "CANCELLED".equals(referenceById.getStatus()) || "PROCESSED".equals(referenceById.getStatus())) {
            log.error("Error while process payment record, recordId: {}, status: {} ", referenceById.getId(), referenceById.getStatus());
            throw new IllegalStateException("Payment already in status: " + referenceById.getStatus());
        }
        
        return referenceById;
    }

    private void validateWallet(PaymentRequest request) throws Exception {
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
    }

    private void updateWalletBalance(Payment saved) {
        walletClient.post()
                .uri("/wallets/update-balance")
                .body(new BalanceUpdateRequest(saved.getId(), saved.getFromUsrId(), saved.getToUsrId(), saved.getAmount()))
                .retrieve()
                .toBodilessEntity();
    }

    public WalletResponse createWallet(WalletRequest request) {
        return walletClient.post()
                .uri("/wallets")
                .body(request)
                .retrieve()
                .toEntity(WalletResponse.class)
                .getBody();
    }

    @SqsListener(
            value = "${sqs.queue.name}"
    )
    private void receiveMessage(String message) {
        try {
            TransactionStatusEvent event = objectMapper.readValue(message, TransactionStatusEvent.class);

            var transactionId = event.transactionId();
            var status = event.status();
            Payment payment = repository.findById(transactionId)
                    .orElseThrow(() ->
                            new EntityNotFoundException("Payment not found: " + transactionId)
                    );
            payment.setStatus(status);

            repository.save(payment);
        } catch (Exception e) {
            log.error("Error while receiving message from SQS message {}", e.getMessage());
            throw new RuntimeException("Error while processing event from message: " + e.getMessage());
        }
    }
}
