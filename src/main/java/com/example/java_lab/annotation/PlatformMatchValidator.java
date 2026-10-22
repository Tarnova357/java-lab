package com.example.java_lab.annotation;

import com.example.java_lab.dto.VirtualAssetDto;
import com.example.java_lab.entity.StorageAccount;
import com.example.java_lab.repository.StorageAccountRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PlatformMatchValidator implements ConstraintValidator<PlatformMatch, VirtualAssetDto> {

    private final StorageAccountRepository storageAccountRepository;

    @Override
    public boolean isValid(VirtualAssetDto dto, ConstraintValidatorContext context) {
        if (dto == null || dto.getStorageAccountId() == null || dto.getPlatform() == null) {
            return true;
        }

        Optional<StorageAccount> accountOptional = storageAccountRepository.findById(dto.getStorageAccountId());
        if (accountOptional.isEmpty()) {
            return false;
        }

        StorageAccount account = accountOptional.get();
        return dto.getPlatform().equalsIgnoreCase(account.getPlatform());
    }
}