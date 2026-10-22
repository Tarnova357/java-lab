package com.example.java_lab.dto;

import com.example.java_lab.entity.enums.OrderStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeliveryOrderDto {
    private Long id;

    @NotBlank(message = "Customer tag cannot be empty")
    private String customerTag;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Long quantity;

    private Double totalPrice;

    private String platform;

    private OrderStatus status;

    @NotNull(message = "Virtual asset ID is required")
    private Long virtualAssetId;
}