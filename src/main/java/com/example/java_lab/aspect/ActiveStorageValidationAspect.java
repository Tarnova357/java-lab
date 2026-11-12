package com.example.java_lab.aspect;

import com.example.java_lab.dto.DeliveryOrderDto;
import com.example.java_lab.entity.StorageAccount;
import com.example.java_lab.entity.VirtualAsset;
import com.example.java_lab.entity.enums.AccountStatus;
import com.example.java_lab.repository.VirtualAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ActiveStorageValidationAspect {

    private final VirtualAssetRepository virtualAssetRepository;

    @Around("@annotation(com.example.java_lab.annotation.VerifyActiveStorage)")
    public Object validateOrderStorageAccount(ProceedingJoinPoint joinPoint) throws Throwable {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof DeliveryOrderDto orderDto && orderDto.getVirtualAssetId() != null) {
                VirtualAsset asset = virtualAssetRepository.findById(orderDto.getVirtualAssetId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Virtual asset not found: " + orderDto.getVirtualAssetId()));

                StorageAccount account = asset.getStorageAccount();
                if (account != null && account.getStatus() != AccountStatus.ACTIVE) {
                    log.warn("[AOP Order Guard] Замовлення відхилено: сховище '{}' має статус {}",
                            account.getUsername(), account.getStatus());
                    throw new IllegalArgumentException(
                            "Cannot place order: storage account '" + account.getUsername() +
                                    "' is " + account.getStatus() + ". Active storage is required.");
                }
            }
        }

        return joinPoint.proceed();
    }
}