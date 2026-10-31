package com.example.wallet.service;

import com.example.wallet.data.TransactionEventHistory;
import com.example.wallet.data.Wallet;
import com.example.wallet.dto.BalanceUpdateRequest;
import com.example.wallet.dto.TransactionFailedRetryEvent;
import com.example.wallet.dto.TransactionStatusEvent;
import com.example.wallet.exception.WalletNotFoundException;
import com.example.wallet.exception.WalletUpdateException;
import com.example.wallet.repository.TransactionTrackingEventRepository;
import com.example.wallet.repository.WalletRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class WalletService {
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);
    private final WalletRepository walletRepository;
    private final TransactionTrackingEventRepository transactionTrackingEventRepository;
    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;

    public WalletService(WalletRepository walletRepository, TransactionTrackingEventRepository transactionTrackingEventRepository,
                         SqsClient sqsClient,
                         @Qualifier("transactionWalletQueueUrl") String queueUrl) {
        this.walletRepository = walletRepository;
        this.transactionTrackingEventRepository = transactionTrackingEventRepository;
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Wallet> updateBalance(BalanceUpdateRequest request) {
        String fromUsrId = request.fromUsrId().trim().toLowerCase();
        String toUsrId = request.toUsrId().trim().toLowerCase();
        
        Wallet walletFromUsrId = walletRepository.findById(fromUsrId)
                .orElseThrow(() -> new RuntimeException("Wallet not found for user (sender): " + fromUsrId));

        Wallet walletToUsrId = walletRepository.findById(toUsrId)
                .orElseThrow(() -> new RuntimeException("Wallet not found for user (recipient): " + toUsrId));

        walletFromUsrId.setBalance(walletFromUsrId.getBalance().subtract(request.amount()));
        walletToUsrId.setBalance(walletToUsrId.getBalance().add(request.amount()));

        List<Wallet> walletFromAndToUsrList = new ArrayList<>();
        walletFromAndToUsrList.add(walletFromUsrId);
        walletFromAndToUsrList.add(walletToUsrId);

        try {
            var resUpdate = walletRepository.saveAll(walletFromAndToUsrList);
            sendTransactionStatusEvent(request.transactionId(), "PROCESSED");
            return resUpdate;
        } catch (Exception e) {
            sendTransactionStatusEvent(request.transactionId(), "FAILED");
            throw new WalletUpdateException("Failed to update balances in database", e);
        }
    }

    @Transactional(readOnly = true)
    public Wallet getBalanceByUserId(String userId) {
        String normalizedId = userId.trim().toLowerCase();
        return walletRepository.findById(normalizedId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + normalizedId));
    }

    @Transactional
    public Wallet createWallet(String userId, java.math.BigDecimal initialBalance) {
        String normalizedId = userId.trim().toLowerCase();
        Wallet wallet = new Wallet(normalizedId, initialBalance);
        return walletRepository.save(wallet);
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

    @SqsListener(
            value = "${sqs.queue.txc.failed.name}"
    )
    public void receiveMessage(String message) {
        try {
            log.info("[WALLET SERVICE]: START receive event");
            TransactionFailedRetryEvent event = objectMapper.readValue(message, TransactionFailedRetryEvent.class);
            var transactionId = event.transactionId();
            Optional<TransactionEventHistory> payment = transactionTrackingEventRepository.findById(transactionId);
            if (payment.isEmpty()) {
                updateBalance(new BalanceUpdateRequest(event.transactionId(), event.fromUsrId(), event.toUsrId(), event.amount(), ""));
            } else {
                log.error("[WALLET SERVICE]: TransactionID: {} is already processed", event.transactionId());
                throw new RuntimeException("[WALLET SERVICE]:TransactionId {} " +  event.transactionId() + " is already processed");
            }
        } catch (Exception e) {
            log.error("[WALLET SERVICE]: Error while receiving message from SQS message {}", e.getMessage());
            throw new RuntimeException("[WALLET SERVICE]: Error while processing event from message: " + e.getMessage());
        }
    }
}
