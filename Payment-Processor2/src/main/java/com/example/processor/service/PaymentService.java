package com.example.processor.service;

import com.example.processor.data.BalanceUpdateRequest;
import com.example.processor.data.Payment;
import com.example.processor.dto.*;
import com.example.processor.repository.PaymentRepository;
import com.example.processor.utils.ValidatePaymentRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.persistence.EntityNotFoundException;
import org.apache.logging.log4j.util.Strings;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.List;
import java.util.Objects;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentRepository repository;
    private final RestClient walletClient;
    private final ObjectMapper objectMapper;
    private final SqsClient sqsClient;
    private final String queueUrl;

    public PaymentService(PaymentRepository repository,
                          @Value("${wallet.url:http://wallet-service}") String walletUrl,
                          SqsClient sqsClient,
                          @Qualifier("transactionProcessingFailedQueueUrl") String queueUrl, ObjectMapper objectMapper) {
        this.repository = repository;
        this.walletClient = RestClient.builder().baseUrl(walletUrl).build();
        this.objectMapper = objectMapper;
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
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

    @Recurring(id = "minutes-digest", cron = "* * * * *")
    @Job(name = "Scan Failed Transaction")
    public void sendWeeklyDigest() {
        try {
            List<Payment> failed = repository.findAllByStatus("FAILED");
            if (failed.isEmpty()) {
                log.info("We don't have any FAILED transaction at all");
            }
            for (Payment payment : failed) {
                payment.setStatus("PROCESSED");
            }
            repository.saveAll(failed);
            for (Payment payment : failed) {
                sendTransactionFailedStatusEvent(payment);
            }
            log.info("[PAYMENT SERVICE]: COMPLETED SCAN FAILED TRANSACTION");
        } catch (Exception e) {
            log.error("[PAYMENT SERVICE]: ERROR WHILE PROCESSING TO RETRY TRANSACTION FAILED");
        }
    }

    private void sendTransactionFailedStatusEvent(Payment payment) throws Exception {
        try {
            TransactionFailedRetryEvent transactionFailedRetryEvent
                    = new TransactionFailedRetryEvent(payment.getId(), payment.getFromUsrId(), payment.getToUsrId(), payment.getAmount());
            String messageEvent = objectMapper.writeValueAsString(transactionFailedRetryEvent);
            sqsClient.sendMessage(requestBuilder -> requestBuilder
                    .queueUrl(queueUrl)
                    .messageBody(messageEvent)
            );
            log.info("[PAYMENT SERVICE]: SEND TO SQS successfully to Wallet for transactionId {} ", payment.getId());
        } catch (JsonProcessingException e) {
            log.error("[PAYMENT SERVICE]: SEND FAILED SQS to Wallet transactionId {} with ERROR message {}  ", payment.getId(), e.getMessage());
            throw new Exception(e.getMessage());
        }
    }
}
