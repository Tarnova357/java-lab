package com.example.java_lab.service;

import com.example.java_lab.dto.DeliveryOrderDto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderLogisticsAsyncService {

    private static final String HMAC_SECRET = "VirtualAssetLogisticsSigningKey2026";
    private static final String NBU_USD_API = "https://bank.gov.ua/NBUStatService/v1/statdirectory/exchange?valcode=USD&json";

    private final HttpClient asyncHttpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8081/realms/java-lab-realm}")
    private String keycloakIssuerUri;

    @Value("${app.receipts.dir:receipts}")
    private String receiptsDirectory;

    @Async("assetTaskExecutor")
    public void generateSignedReceiptArchive(DeliveryOrderDto order) {
        String threadName = Thread.currentThread().getName();
        log.info("[Fire-and-Forget | {}] Генерація підписаної ZIP-квитанції для замовлення ID={}",
                threadName, order.getId());

        if (order.getId() == null || "INVALID_PATH".equalsIgnoreCase(order.getCustomerTag())) {
            throw new IllegalArgumentException("Некоректні дані замовлення для генерації квитанції");
        }

        try {
            Path dirPath = Path.of(receiptsDirectory);
            Files.createDirectories(dirPath);

            String payload = String.format(
                    "ORDER_ID=%d%nCUSTOMER=%s%nPLATFORM=%s%nQUANTITY=%d%nTOTAL_USD=%.2f%nCREATED_AT=%s",
                    order.getId(),
                    order.getCustomerTag(),
                    order.getPlatform(),
                    order.getQuantity(),
                    order.getTotalPrice(),
                    Instant.now()
            );

            String signature = computeHmacSha256(payload);
            String fullReceiptContent = payload + "\nHMAC_SHA256_SIGNATURE=" + signature + "\n";

            Path zipPath = dirPath.resolve("order_" + order.getId() + ".zip");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
                ZipEntry entry = new ZipEntry("receipt_order_" + order.getId() + ".txt");
                zos.putNextEntry(entry);
                zos.write(fullReceiptContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            log.info("[Fire-and-Forget | {}] Квитанцію заархівовано: {}", threadName, zipPath.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Помилка запису ZIP-квитанції на диск", e);
        }
    }

    @Async("assetTaskExecutor")
    public CompletableFuture<Double> fetchLiveUsdRateFromNbu(String apiUrl, long requestTimeoutMs) {
        String threadName = Thread.currentThread().getName();
        log.info("[Async NBU API | {}] Запит курсу USD/UAH до {}", threadName, apiUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofMillis(requestTimeoutMs))
                    .GET()
                    .build();

            HttpResponse<String> response = asyncHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("NBU API повернув статус " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            double rate = root.get(0).get("rate").asDouble();
            log.info("[Async NBU API | {}] Отримано курс НБУ: 1 USD = {} UAH", threadName, rate);
            return CompletableFuture.completedFuture(rate);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Помилка запиту до НБУ API: " + e.getMessage(), e));
        }
    }

    @Async("assetTaskExecutor")
    public CompletableFuture<String> verifyKeycloakOidcHealth(String discoveryUrl, long requestTimeoutMs) {
        String threadName = Thread.currentThread().getName();
        log.info("[Async Keycloak OIDC | {}] Перевірка конфігурації OIDC: {}", threadName, discoveryUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(discoveryUrl))
                    .timeout(Duration.ofMillis(requestTimeoutMs))
                    .GET()
                    .build();

            HttpResponse<String> response = asyncHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Keycloak OIDC повернув статус " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String issuer = root.path("issuer").asText("unknown");
            String result = "ACTIVE (issuer: " + issuer + ")";
            log.info("[Async Keycloak OIDC | {}] Провайдер підтверджено: {}", threadName, result);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Keycloak OIDC недоступний: " + e.getMessage(), e));
        }
    }

    @Async("limitedOverflowExecutor")
    public CompletableFuture<String> executeNetworkCheckInLimitedPool(String targetUrl) {
        log.info("[Limited Pool | {}] Виконання реального HTTP-запиту до {}",
                Thread.currentThread().getName(), targetUrl);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = asyncHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return CompletableFuture.completedFuture("HTTP " + response.statusCode());
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public String getDefaultNbuUrl() {
        return NBU_USD_API;
    }

    public String getDefaultKeycloakDiscoveryUrl() {
        return keycloakIssuerUri + "/.well-known/openid-configuration";
    }

    private String computeHmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Не вдалося обчислити HMAC-SHA256", e);
        }
    }
}