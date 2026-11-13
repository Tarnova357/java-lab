package com.example.java_lab.service;

import com.example.java_lab.entity.DeliveryOrder;
import com.example.java_lab.entity.VirtualAsset;
import com.example.java_lab.entity.enums.OrderStatus;
import com.example.java_lab.repository.DeliveryOrderRepository;
import com.example.java_lab.repository.VirtualAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderMaintenanceService {

    private final DeliveryOrderRepository orderRepository;
    private final VirtualAssetRepository assetRepository;

    @Transactional
    public int cancelStalePendingOrders(int maxAgeMinutes, int batchLimit) {
        List<DeliveryOrder> pendingOrders = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING)
                .limit(batchLimit)
                .toList();

        int processedCount = 0;
        for (DeliveryOrder order : pendingOrders) {
            if (order.getStatus() != OrderStatus.PENDING) {
                continue;
            }
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            VirtualAsset asset = order.getVirtualAsset();
            if (asset != null) {
                asset.setAmount(asset.getAmount() + order.getQuantity());
                assetRepository.save(asset);
            }
            processedCount++;
        }

        log.info("[OrderMaintenanceService] Скасовано {} прострочених PENDING замовлень (поріг={} хв, ліміт={})",
                processedCount, maxAgeMinutes, batchLimit);
        return processedCount;
    }
}