package com.example.processor.dto;

import java.math.BigDecimal;

public record TransactionFailedRetryEvent(String transactionId, String fromUsrId, String toUsrId, BigDecimal amount) {}
