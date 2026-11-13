package com.example.java_lab.service;

import com.example.java_lab.entity.VirtualAsset;
import com.example.java_lab.repository.VirtualAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryReportService {

    private final VirtualAssetRepository assetRepository;

    @Value("${app.reports.dir:reports}")
    private String reportsDirectory;

    @Transactional(readOnly = true)
    public boolean generateDailyInventoryReport(int lowStockThreshold, String timeZoneId, boolean forceOverwrite) {
        LocalDate reportDate = LocalDate.now(ZoneId.of(timeZoneId));
        List<VirtualAsset> assets = assetRepository.findAll();

        long lowStockCount = assets.stream()
                .filter(a -> a.getAmount() != null && a.getAmount() <= lowStockThreshold)
                .count();

        String reportBody = String.format(
                "DATE=%s%nTIMEZONE=%s%nTOTAL_ASSETS=%d%nLOW_STOCK_THRESHOLD=%d%nLOW_STOCK_ITEMS=%d%nDETAILS=%s%n",
                reportDate,
                timeZoneId,
                assets.size(),
                lowStockThreshold,
                lowStockCount,
                assets.stream()
                        .map(a -> a.getName() + ":" + a.getAmount())
                        .collect(Collectors.joining(","))
        );

        try {
            Path dir = Path.of(reportsDirectory);
            Files.createDirectories(dir);
            Path reportFile = dir.resolve("inventory_report_" + reportDate + ".txt");

            if (!forceOverwrite && Files.exists(reportFile)) {
                String existingContent = Files.readString(reportFile, StandardCharsets.UTF_8);
                if (existingContent.equals(reportBody)) {
                    log.info("[InventoryReportService] Звіт за {} вже актуальний — пропуск (ідемпотентність)", reportDate);
                    return false;
                }
            }

            Files.writeString(reportFile, reportBody, StandardCharsets.UTF_8);
            log.info("[InventoryReportService] Сформовано звіт складу: {}", reportFile.toAbsolutePath());
            return true;
        } catch (IOException e) {
            throw new IllegalStateException("Помилка запису файлу звіту складу", e);
        }
    }
}