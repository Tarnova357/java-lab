package com.example.java_lab.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderSettlementDto {
    private Long orderId;
    private Double amountUsd;
    private Double usdToUahRate;
    private Double amountUah;
    private String authProviderStatus;
    private boolean verified;
    private long executionTimeMs;
}