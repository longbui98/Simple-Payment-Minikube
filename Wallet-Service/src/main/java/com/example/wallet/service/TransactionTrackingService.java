package com.example.wallet.service;

import com.example.wallet.data.TransactionEventHistory;
import com.example.wallet.dto.TransactionStatusEvent;
import com.example.wallet.repository.TransactionTrackingEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.Optional;

@Service
public class TransactionTrackingService {
    private static final Logger log = LoggerFactory.getLogger(TransactionTrackingService.class);
    private final TransactionTrackingEventRepository transactionTrackingEventRepository;
    private final ObjectMapper objectMapper;
    private final SqsClient sqsClient;
    private final String queueUrl;

    public TransactionTrackingService(TransactionTrackingEventRepository transactionTrackingEventRepository,
                                      SqsClient sqsClient, @Qualifier("transactionWalletQueueUrl") String queueUrl) {
        this.transactionTrackingEventRepository = transactionTrackingEventRepository;
        this.objectMapper = new ObjectMapper();
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(String transactionId, String status) {
        try {
            TransactionEventHistory history = transactionTrackingEventRepository.findById(transactionId)
                    .orElseGet(() -> {
                        TransactionEventHistory newHistory =
                                new TransactionEventHistory();
                        newHistory.setTransactionId(transactionId);
                        return newHistory;
                    });

            history.setStatus(status);
            transactionTrackingEventRepository.save(history);
            sendTransactionStatusEvent(transactionId, "SUCCEED");
        } catch (Exception e) {
            sendTransactionStatusEvent(transactionId, "FAILED");
            log.error("[WALLET SERVICE] Error while storing transaction event {} ", transactionId);
        }
    }

    @Transactional(readOnly = true)
    public Optional<TransactionEventHistory> findByTransactionId(String id) {
        return transactionTrackingEventRepository.findById(id);
    }

    private void sendTransactionStatusEvent(String transactionId, String status) {
        try {
            TransactionStatusEvent transactionStatusEvent = new TransactionStatusEvent(transactionId, status);
            String messageEvent = objectMapper.writeValueAsString(transactionStatusEvent);
            sqsClient.sendMessage(requestBuilder -> requestBuilder
                    .queueUrl(queueUrl)
                    .messageBody(messageEvent)
            );
            log.info("SEND TO SQS successfully for transactionId {} ", transactionId);
        } catch (JsonProcessingException e) {
            log.error("SEND FAILED SQS for transactionId {} with ERROR message {}  ", transactionId, e.getMessage());
        }
    }
}
