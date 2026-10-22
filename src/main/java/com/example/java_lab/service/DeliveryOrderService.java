package com.example.java_lab.service;

import com.example.java_lab.dto.DeliveryOrderDto;
import com.example.java_lab.entity.DeliveryOrder;
import com.example.java_lab.entity.VirtualAsset;
import com.example.java_lab.entity.enums.OrderStatus;
import com.example.java_lab.mapper.DeliveryOrderMapper;
import com.example.java_lab.repository.DeliveryOrderRepository;
import com.example.java_lab.repository.VirtualAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeliveryOrderService {

    private final DeliveryOrderRepository orderRepository;
    private final VirtualAssetRepository assetRepository;
    private final DeliveryOrderMapper mapper;

    @Transactional
    public DeliveryOrderDto create(DeliveryOrderDto dto) {
        VirtualAsset asset = assetRepository.findById(dto.getVirtualAssetId())
                .orElseThrow(() -> new RuntimeException("Virtual asset not found with id: " + dto.getVirtualAssetId()));

        if (asset.getAmount() < dto.getQuantity()) {
            throw new IllegalArgumentException("Insufficient assets in storage. Available: " + asset.getAmount() + ", requested: " + dto.getQuantity());
        }

        asset.setAmount(asset.getAmount() - dto.getQuantity());
        assetRepository.save(asset);

        DeliveryOrder entity = mapper.toEntity(dto);
        entity.setVirtualAsset(asset);
        entity.setPlatform(asset.getPlatform());
        entity.setTotalPrice(asset.getUnitPrice() * dto.getQuantity());
        entity.setStatus(OrderStatus.PENDING);

        return mapper.toDto(orderRepository.save(entity));
    }

    public List<DeliveryOrderDto> getAll() {
        return orderRepository.findAll().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    public DeliveryOrderDto getById(Long id) {
        DeliveryOrder entity = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));
        return mapper.toDto(entity);
    }

    @Transactional
    public DeliveryOrderDto update(Long id, DeliveryOrderDto dto) {
        DeliveryOrder existing = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

        VirtualAsset asset = assetRepository.findById(dto.getVirtualAssetId())
                .orElseThrow(() -> new RuntimeException("Virtual asset not found with id: " + dto.getVirtualAssetId()));

        existing.setCustomerTag(dto.getCustomerTag());
        existing.setQuantity(dto.getQuantity());
        existing.setPlatform(asset.getPlatform());
        existing.setTotalPrice(asset.getUnitPrice() * dto.getQuantity());
        existing.setStatus(dto.getStatus() != null ? dto.getStatus() : existing.getStatus());
        existing.setVirtualAsset(asset);

        return mapper.toDto(orderRepository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        orderRepository.deleteById(id);
    }
}