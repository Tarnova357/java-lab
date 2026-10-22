package com.example.java_lab.controller;

import com.example.java_lab.annotation.PostCreatedJsonMapping;
import com.example.java_lab.dto.VirtualAssetDto;
import com.example.java_lab.service.VirtualAssetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/virtual-assets")
@RequiredArgsConstructor
public class VirtualAssetController {

    private final VirtualAssetService service;

    @PreAuthorize("hasRole('ADMIN')")
    @PostCreatedJsonMapping
    public VirtualAssetDto create(@Valid @RequestBody VirtualAssetDto dto) {
        return service.create(dto);
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<List<VirtualAssetDto>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<VirtualAssetDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<VirtualAssetDto> update(@PathVariable Long id, @Valid @RequestBody VirtualAssetDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}