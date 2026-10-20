package com.example.wallet.dto;

import java.math.BigDecimal;

public record WalletRequest(String userId, BigDecimal balance) {}
