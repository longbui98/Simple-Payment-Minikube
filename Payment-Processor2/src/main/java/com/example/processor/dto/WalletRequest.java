package com.example.processor.dto;

import java.math.BigDecimal;

public record WalletRequest(String userId, BigDecimal balance) {}
