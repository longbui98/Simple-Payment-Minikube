package com.example.wallet.dto;

import java.math.BigDecimal;

public record BalanceUpdateRequest(String fromUsrId, String toUsrId, BigDecimal amount, String type) {}
