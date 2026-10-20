package com.example.processor.dto;

import java.math.BigDecimal;

public record PaymentResponseError(String traceId, String message) {}
