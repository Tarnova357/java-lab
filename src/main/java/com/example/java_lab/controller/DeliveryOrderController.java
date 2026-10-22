package com.example.java_lab.controller;

import com.example.java_lab.annotation.IdempotencyKey;
import com.example.java_lab.annotation.PostCreatedJsonMapping;
import com.example.java_lab.dto.DeliveryOrderDto;
import com.example.java_lab.service.DeliveryOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class DeliveryOrderController {

    private final DeliveryOrderService service;

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostCreatedJsonMapping
    public DeliveryOrderDto create(@Valid @RequestBody DeliveryOrderDto dto,
                                   @IdempotencyKey String idempotencyKey) {
        log.info("Оформлення замовлення [Idempotency-Key: {}] для клієнта: {}", idempotencyKey, dto.getCustomerTag());
        return service.create(dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<DeliveryOrderDto>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<DeliveryOrderDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<DeliveryOrderDto> update(@PathVariable Long id, @Valid @RequestBody DeliveryOrderDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}