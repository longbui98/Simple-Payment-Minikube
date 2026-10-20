package com.example.processor.dto;

import java.math.BigDecimal;

public record PaymentResponse(String paymentId, BigDecimal amount, String currency, String status, String fromUsrId, String toUsrId) {}