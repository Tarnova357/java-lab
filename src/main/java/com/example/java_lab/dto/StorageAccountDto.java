package com.example.java_lab.dto;

import com.example.java_lab.entity.enums.AccountStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StorageAccountDto {
    private Long id;

    @NotBlank(message = "Username cannot be empty")
    private String username;

    @NotBlank(message = "Platform cannot be empty")
    private String platform;

    @NotNull(message = "Status is required")
    private AccountStatus status;
}