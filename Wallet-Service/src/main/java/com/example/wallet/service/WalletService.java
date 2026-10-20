package com.example.wallet.service;

import com.example.wallet.data.Wallet;
import com.example.wallet.dto.BalanceUpdateRequest;
import com.example.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class WalletService {
    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional
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

        return walletRepository.saveAll(walletFromAndToUsrList);
    }

    @Transactional(readOnly = true)
    public Wallet getBalanceByUserId(String userId) {
        String normalizedId = userId.trim().toLowerCase();
        return walletRepository.findById(normalizedId)
                .orElseThrow(() -> new RuntimeException("Wallet not found for user: " + normalizedId));
    }

    @Transactional
    public Wallet createWallet(String userId, java.math.BigDecimal initialBalance) {
        String normalizedId = userId.trim().toLowerCase();
        Wallet wallet = new Wallet(normalizedId, initialBalance);
        return walletRepository.save(wallet);
    }
}
