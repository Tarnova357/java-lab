package com.example.java_lab.service;

import com.example.java_lab.annotation.MaskCustomerTag;
import com.example.java_lab.annotation.VerifyActiveStorage;
import com.example.java_lab.dto.DeliveryOrderDto;
import com.example.java_lab.dto.OrderSettlementDto;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeliveryOrderService {

    private static final double FALLBACK_USD_RATE = 41.50;

    private final DeliveryOrderRepository orderRepository;
    private final VirtualAssetRepository assetRepository;
    private final DeliveryOrderMapper mapper;
    private final OrderLogisticsAsyncService asyncLogisticsService;

    @Transactional
    @VerifyActiveStorage
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

        DeliveryOrderDto savedDto = mapper.toDto(orderRepository.save(entity));
        asyncLogisticsService.generateSignedReceiptArchive(savedDto);
        return savedDto;
    }

    public OrderSettlementDto calculateOrderSettlement(Long orderId, long timeoutMs) {
        DeliveryOrderDto order = getById(orderId);
        return calculateOrderSettlementWithUrls(
                order,
                asyncLogisticsService.getDefaultNbuUrl(),
                asyncLogisticsService.getDefaultKeycloakDiscoveryUrl(),
                timeoutMs
        );
    }

    public OrderSettlementDto calculateOrderSettlementWithUrls(
            DeliveryOrderDto order,
            String nbuUrl,
            String keycloakDiscoveryUrl,
            long timeoutMs) {

        long startTime = System.currentTimeMillis();

        CompletableFuture<Double> rateFuture = asyncLogisticsService
                .fetchLiveUsdRateFromNbu(nbuUrl, timeoutMs)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> FALLBACK_USD_RATE);

        CompletableFuture<String> oidcFuture = asyncLogisticsService
                .verifyKeycloakOidcHealth(keycloakDiscoveryUrl, timeoutMs)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .handle((res, ex) -> ex != null
                        ? "FALLBACK_UNVERIFIED (" + ex.getMessage() + ")"
                        : res);

        return rateFuture.thenCombine(oidcFuture, (rate, oidcStatus) -> {
            long elapsed = System.currentTimeMillis() - startTime;
            double amountUsd = order.getTotalPrice() != null ? order.getTotalPrice() : 0.0;
            double amountUah = Math.round(amountUsd * rate * 100.0) / 100.0;
            boolean verified = oidcStatus.startsWith("ACTIVE");

            return new OrderSettlementDto(
                    order.getId(),
                    amountUsd,
                    rate,
                    amountUah,
                    oidcStatus,
                    verified,
                    elapsed
            );
        }).join();
    }

    @MaskCustomerTag(visiblePrefix = 2, visibleSuffix = 2, bypassForAdmin = true)
    public List<DeliveryOrderDto> getAll() {
        return orderRepository.findAll().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    public List<DeliveryOrderDto> getAllViaSelfInvocation() {
        return this.getAll();
    }
    @MaskCustomerTag(visiblePrefix = 2, visibleSuffix = 2, bypassForAdmin = true)
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