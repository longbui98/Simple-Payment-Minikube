package com.example.processor.data;

import java.math.BigDecimal;

public record BalanceUpdateRequest(String transactionId, String fromUsrId, String toUsrId, BigDecimal amount) {}

