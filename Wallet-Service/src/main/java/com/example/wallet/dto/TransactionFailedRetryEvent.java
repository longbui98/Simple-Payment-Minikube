package com.example.wallet.dto;

import java.math.BigDecimal;

public record TransactionFailedRetryEvent(String transactionId, String fromUsrId, String toUsrId, BigDecimal amount) {}
