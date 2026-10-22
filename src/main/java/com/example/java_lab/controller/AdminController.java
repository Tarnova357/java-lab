package com.example.java_lab.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/system-status")
    public ResponseEntity<Map<String, String>> getSystemStatus() {
        return ResponseEntity.ok(Map.of(
                "status", "OPERATIONAL",
                "message", "Панель адміністратора працює в штатному режимі"
        ));
    }
}