package com.example.processor.dto;

import java.math.BigDecimal;

public record PaymentRequest(String paymentId, BigDecimal amount, String currency, String fromUsrId, String toUsrId) {}
