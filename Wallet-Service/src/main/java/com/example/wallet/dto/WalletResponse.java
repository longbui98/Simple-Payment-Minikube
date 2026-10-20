package com.example.wallet.dto;

import java.math.BigDecimal;

public record WalletResponse(String userId, BigDecimal balance) {}