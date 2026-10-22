package com.example.java_lab.service;

import com.example.java_lab.dto.StorageAccountDto;
import com.example.java_lab.entity.StorageAccount;
import com.example.java_lab.entity.enums.AccountStatus;
import com.example.java_lab.mapper.StorageAccountMapper;
import com.example.java_lab.repository.StorageAccountRepository;
import com.example.java_lab.repository.VirtualAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StorageAccountService {

    private final StorageAccountRepository repository;
    private final VirtualAssetRepository virtualAssetRepository;
    private final StorageAccountMapper mapper;

    @Transactional
    public StorageAccountDto create(StorageAccountDto dto) {
        StorageAccount entity = mapper.toEntity(dto);
        if (entity.getStatus() == null) {
            entity.setStatus(AccountStatus.ACTIVE);
        }
        return mapper.toDto(repository.save(entity));
    }

    public List<StorageAccountDto> getAll() {
        return repository.findAll().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    public StorageAccountDto getById(Long id) {
        StorageAccount entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found with id: " + id));
        return mapper.toDto(entity);
    }

    @Transactional
    public StorageAccountDto update(Long id, StorageAccountDto dto) {
        StorageAccount existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found with id: " + id));

        if (!existing.getPlatform().equalsIgnoreCase(dto.getPlatform())) {
            if (virtualAssetRepository.existsByStorageAccountId(id)) {
                throw new IllegalArgumentException("Cannot change platform to " + dto.getPlatform() +
                        ": storage account has linked assets. Remove or transfer them first.");
            }
        }

        existing.setUsername(dto.getUsername());
        existing.setPlatform(dto.getPlatform().toUpperCase());
        existing.setStatus(dto.getStatus());

        return mapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Account not found with id: " + id);
        }
        repository.deleteById(id);
    }
}