package com.example.java_lab.service;

import com.example.java_lab.dto.VirtualAssetDto;
import com.example.java_lab.entity.StorageAccount;
import com.example.java_lab.entity.VirtualAsset;
import com.example.java_lab.mapper.VirtualAssetMapper;
import com.example.java_lab.repository.StorageAccountRepository;
import com.example.java_lab.repository.VirtualAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VirtualAssetService {

    private final VirtualAssetRepository assetRepository;
    private final StorageAccountRepository accountRepository;
    private final VirtualAssetMapper mapper;

    @Transactional
    public VirtualAssetDto create(VirtualAssetDto dto) {
        StorageAccount account = accountRepository.findById(dto.getStorageAccountId())
                .orElseThrow(() -> new RuntimeException("Storage account not found: " + dto.getStorageAccountId()));

        VirtualAsset entity = mapper.toEntity(dto);
        entity.setStorageAccount(account);
        return mapper.toDto(assetRepository.save(entity));
    }

    public List<VirtualAssetDto> getAll() {
        return assetRepository.findAll().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    public VirtualAssetDto getById(Long id) {
        VirtualAsset entity = assetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Asset not found with id: " + id));
        return mapper.toDto(entity);
    }

    @Transactional
    public VirtualAssetDto update(Long id, VirtualAssetDto dto) {
        VirtualAsset existing = assetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Asset not found with id: " + id));

        StorageAccount account = accountRepository.findById(dto.getStorageAccountId())
                .orElseThrow(() -> new RuntimeException("Storage account not found: " + dto.getStorageAccountId()));

        existing.setName(dto.getName());
        existing.setAssetType(dto.getAssetType());
        existing.setAmount(dto.getAmount());
        existing.setPlatform(dto.getPlatform().toUpperCase());
        existing.setUnitPrice(dto.getUnitPrice());
        existing.setStorageAccount(account);

        return mapper.toDto(assetRepository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        if (!assetRepository.existsById(id)) {
            throw new RuntimeException("Asset not found with id: " + id);
        }
        assetRepository.deleteById(id);
    }
}