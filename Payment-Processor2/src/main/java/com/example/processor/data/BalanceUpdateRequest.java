package com.example.processor.data;

import java.math.BigDecimal;

public record BalanceUpdateRequest(String fromUsrId, String toUsrId, BigDecimal amount) {}

