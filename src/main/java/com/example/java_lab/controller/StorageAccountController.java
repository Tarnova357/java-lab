package com.example.java_lab.controller;

import com.example.java_lab.annotation.PostCreatedJsonMapping;
import com.example.java_lab.dto.StorageAccountDto;
import com.example.java_lab.service.StorageAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/storage-accounts")
@RequiredArgsConstructor
public class StorageAccountController {

    private final StorageAccountService service;

    @PreAuthorize("hasRole('ADMIN')")
    @PostCreatedJsonMapping
    public StorageAccountDto create(@Valid @RequestBody StorageAccountDto dto) {
        return service.create(dto);
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<List<StorageAccountDto>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<StorageAccountDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<StorageAccountDto> update(@PathVariable Long id, @Valid @RequestBody StorageAccountDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}