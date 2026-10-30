package com.example.wallet.controller;

import com.example.wallet.data.Wallet;
import com.example.wallet.dto.BalanceUpdateRequest;
import com.example.wallet.dto.WalletRequest;
import com.example.wallet.dto.WalletResponse;
import com.example.wallet.exception.WalletNotFoundException;
import com.example.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/wallets")
public class WalletController {
    private static final Logger log = LoggerFactory.getLogger(WalletController.class);
    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/update-balance")
    public ResponseEntity<List<WalletResponse>> updateBalance(@RequestBody BalanceUpdateRequest request) {
        List<Wallet> updatedWallets = walletService.updateBalance(request);
        List<WalletResponse> responses = updatedWallets.stream()
                .map(w -> new WalletResponse(w.getUserId(), w.getBalance()))
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(@RequestBody WalletRequest request) {
        Wallet wallet = new Wallet("", new BigDecimal(0));
        try {
            walletService.getBalanceByUserId(request.userId());
        } catch (WalletNotFoundException e) {
            wallet = walletService.createWallet(UUID.randomUUID().toString(), request.balance());
        } catch (Exception e) {
           log.error("Error while create wallet with userId {} with message: {}", request.userId(), e.getMessage());
        }
        return ResponseEntity.ok(new WalletResponse(wallet.getUserId(), wallet.getBalance()));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable String userId) {
        try {
            Wallet wallet = walletService.getBalanceByUserId(userId);
            return ResponseEntity.ok(new WalletResponse(wallet.getUserId(), wallet.getBalance()));
        } catch (RuntimeException e) {
            log.error("Wallet for userId {} does not exist", userId);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/validate")
    public ResponseEntity<String> validateUsersWallet(@RequestParam(name = "fromUsrId") String fromUsrId, @RequestParam(name = "toUsrId") String toUsrId,
                                                      @RequestParam(name = "amount") BigDecimal amount) {
        try {
            try {
                Wallet walletFromUsrId = walletService.getBalanceByUserId(fromUsrId);
                if (walletFromUsrId.getBalance().compareTo(amount) < 0) {
                    log.error("Error userId {}'s wallet is not enough balance {} ", fromUsrId, walletFromUsrId.getBalance());
                    return ResponseEntity.status(404).body("fromUsrId's wallet is not enough balance: " + fromUsrId);
                }
            } catch (RuntimeException e) {
                log.error("Sender wallet validation failed: {}", e.getMessage());
                return ResponseEntity.status(404).body("Sender " + e.getMessage());
            }

            try {
                walletService.getBalanceByUserId(toUsrId);
            } catch (RuntimeException e) {
                log.error("Recipient wallet validation failed: {}", e.getMessage());
                return ResponseEntity.status(404).body("Recipient " + e.getMessage());
            }

            return ResponseEntity.ok("Valid");
        } catch (Exception e) {
            log.error("Unexpected error during wallet validation: {}", e.getMessage());
            return ResponseEntity.status(500).body("Unexpected error: " + e.getMessage());
        }
    }
}
