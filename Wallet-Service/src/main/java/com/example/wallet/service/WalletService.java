package com.example.wallet.service;

import com.example.wallet.data.TransactionEventHistory;
import com.example.wallet.data.Wallet;
import com.example.wallet.dto.BalanceUpdateRequest;
import com.example.wallet.dto.TransactionFailedRetryEvent;
import com.example.wallet.exception.WalletNotFoundException;
import com.example.wallet.exception.WalletUpdateException;
import com.example.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class WalletService {
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);
    private final WalletRepository walletRepository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final TransactionTrackingService transactionTrackingService;

    public WalletService(WalletRepository walletRepository, EntityManager entityManager, TransactionTrackingService transactionTrackingService) {
        this.walletRepository = walletRepository;
        this.objectMapper = new ObjectMapper();
        this.entityManager = entityManager;
        this.transactionTrackingService = transactionTrackingService;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Wallet> updateBalance(BalanceUpdateRequest request) {
        String fromUsrId = request.fromUsrId().trim().toLowerCase();
        String toUsrId = request.toUsrId().trim().toLowerCase();

        Map<String, Wallet> wallets = walletRepository
                .findAllByIdForUpdate(Stream.of(fromUsrId, toUsrId).sorted().toList())
                .stream()
                .collect(Collectors.toMap(Wallet::getUserId, Function.identity()));

        var senderWallet = Optional.ofNullable(wallets.get(fromUsrId))
                .orElseThrow(() -> new RuntimeException("Sender wallet not found"));

        var recipientWallet = Optional.ofNullable(wallets.get(toUsrId))
                .orElseThrow(() -> new RuntimeException("Recipient wallet not found"));

        try {
            var statusDebit = walletRepository.debit(fromUsrId, request.amount());
            if (statusDebit == 0) {
                throw new RuntimeException("Sender wallet not found or insufficient balance for userId: " + fromUsrId);
            }
            var statusCredit = walletRepository.credit(toUsrId, request.amount());
            if (statusCredit == 0) {
                throw new RuntimeException("Error while credit for receipt wallet for userId: " + toUsrId);
            }
            transactionTrackingService.updateStatus(request.transactionId(), "PROCESSED");

            entityManager.refresh(senderWallet);
            entityManager.refresh(recipientWallet);
            return List.of(senderWallet, recipientWallet);
        } catch (Exception e) {
            transactionTrackingService.updateStatus(request.transactionId(), "FAILED");
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

    @SqsListener(
            value = "${sqs.queue.txc.failed.name}"
    )
    public void receiveMessage(String message) {
        try {
            log.info("[WALLET SERVICE]: START receive event");
            TransactionFailedRetryEvent event = objectMapper.readValue(message, TransactionFailedRetryEvent.class);
            var transactionId = event.transactionId();
            Optional<TransactionEventHistory> payment = transactionTrackingService.findByTransactionId(transactionId);
            if (payment.isEmpty()) {
                updateBalance(new BalanceUpdateRequest(event.transactionId(), event.fromUsrId(), event.toUsrId(), event.amount(), ""));
            } else {
                throw new RuntimeException("[WALLET SERVICE]:TransactionId {} " +  event.transactionId() + " is already processed");
            }
        } catch (Exception e) {
            log.error("[WALLET SERVICE]: Error while receiving message from SQS message {}", e.getMessage());
        }
    }
}
