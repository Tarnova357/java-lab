package com.example.java_lab.dto;

import com.example.java_lab.annotation.PlatformMatch;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@PlatformMatch
public class VirtualAssetDto {
    private Long id;

    @NotBlank(message = "Asset name cannot be empty")
    private String name;

    @NotBlank(message = "Asset type cannot be empty")
    private String assetType;

    @NotNull(message = "Amount is required")
    @Min(value = 1, message = "Amount must be at least 1")
    private Long amount;

    @NotBlank(message = "Platform cannot be empty")
    private String platform;

    @NotNull(message = "Unit price is required")
    @Min(value = 0, message = "Unit price must be positive")
    private Double unitPrice;

    @NotNull(message = "Storage account ID is required")
    private Long storageAccountId;
}