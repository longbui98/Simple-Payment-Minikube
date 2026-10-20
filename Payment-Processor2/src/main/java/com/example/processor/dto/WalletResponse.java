package com.example.processor.dto;

import java.math.BigDecimal;

public record WalletResponse(String userId, BigDecimal balance) {}