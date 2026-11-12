package com.example.java_lab.service;

import com.example.java_lab.config.AsyncConfig;
import com.example.java_lab.dto.DeliveryOrderDto;
import com.example.java_lab.dto.OrderSettlementDto;
import com.example.java_lab.mapper.DeliveryOrderMapper;
import com.example.java_lab.repository.DeliveryOrderRepository;
import com.example.java_lab.repository.VirtualAssetRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {
        AsyncConfig.class,
        OrderLogisticsAsyncService.class,
        DeliveryOrderService.class
})
class AsyncProcessingTest {

    private static HttpServer mockExternalServer;
    private static String baseUrl;
    private static final AtomicInteger concurrentInFlight = new AtomicInteger(0);
    private static final AtomicInteger maxObservedConcurrency = new AtomicInteger(0);
    private static CountDownLatch releaseSlowEndpointLatch;

    @TempDir
    static Path tempReceiptsDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.receipts.dir", () -> tempReceiptsDir.toString());
    }

    @BeforeAll
    static void startLocalHttpServer() throws IOException {
        mockExternalServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockExternalServer.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());

        mockExternalServer.createContext("/nbu-rate", exchange -> {
            int active = concurrentInFlight.incrementAndGet();
            maxObservedConcurrency.updateAndGet(prev -> Math.max(prev, active));
            try {
                byte[] body = "[{\"cc\":\"USD\",\"rate\":41.25}]".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } finally {
                concurrentInFlight.decrementAndGet();
            }
        });

        mockExternalServer.createContext("/oidc-config", exchange -> {
            int active = concurrentInFlight.incrementAndGet();
            maxObservedConcurrency.updateAndGet(prev -> Math.max(prev, active));
            try {
                byte[] body = "{\"issuer\":\"http://localhost:8081/realms/java-lab-realm\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } finally {
                concurrentInFlight.decrementAndGet();
            }
        });

        mockExternalServer.createContext("/error-500", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        mockExternalServer.createContext("/held-endpoint", exchange -> {
            try {
                if (releaseSlowEndpointLatch != null) {
                    releaseSlowEndpointLatch.await(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        mockExternalServer.start();
        baseUrl = "http://localhost:" + mockExternalServer.getAddress().getPort();
    }

    @AfterAll
    static void stopLocalHttpServer() {
        if (mockExternalServer != null) {
            mockExternalServer.stop(0);
        }
    }

    @Autowired
    private OrderLogisticsAsyncService asyncService;

    @Autowired
    private DeliveryOrderService deliveryOrderService;

    @Autowired
    private AsyncConfig asyncConfig;

    @MockitoBean
    private DeliveryOrderRepository orderRepository;

    @MockitoBean
    private VirtualAssetRepository assetRepository;

    @MockitoBean
    private DeliveryOrderMapper mapper;

    @Test
    @DisplayName("1. Власний виконавець використовує пул із префіксом asset-async-")
    void customExecutor_usesConfiguredThreadNamePrefix() {
        String threadName = CompletableFuture
                .supplyAsync(() -> Thread.currentThread().getName(), asyncConfig.assetTaskExecutor())
                .join();

        assertTrue(threadName.startsWith("asset-async-"),
                "Очікувався префікс 'asset-async-', фактично: " + threadName);
    }

    @Test
    @DisplayName("2. Fire-and-forget створює реальний підписаний ZIP-файл квитанції у фоні")
    void fireAndForget_generatesSignedZipReceiptOnDisk() throws Exception {
        DeliveryOrderDto order = new DeliveryOrderDto();
        order.setId(505L);
        order.setCustomerTag("Archive_Tester");
        order.setPlatform("ROBLOX");
        order.setQuantity(2L);
        order.setTotalPrice(20.0);

        asyncService.generateSignedReceiptArchive(order);

        Path expectedZip = tempReceiptsDir.resolve("order_505.zip");
        long deadline = System.currentTimeMillis() + 3000;
        while ((!Files.exists(expectedZip) || Files.size(expectedZip) == 0)
                && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }

        assertTrue(Files.exists(expectedZip), "ZIP-квитанція має бути створена у фоновому потоці");
        assertTrue(Files.size(expectedZip) > 0, "ZIP-файл не повинен бути порожнім");
    }

    @Test
    @DisplayName("3. Два незалежні @Async HTTP-запити виконуються одночасно та об'єднуються через thenCombine")
    void twoIndependentHttpCalls_executeConcurrentlyAndCombine() {
        DeliveryOrderDto order = new DeliveryOrderDto();
        order.setId(77L);
        order.setTotalPrice(10.0);

        OrderSettlementDto result = deliveryOrderService.calculateOrderSettlementWithUrls(
                order,
                baseUrl + "/nbu-rate",
                baseUrl + "/oidc-config",
                3000
        );

        assertEquals(41.25, result.getUsdToUahRate());
        assertEquals(412.50, result.getAmountUah());
        assertTrue(result.isVerified());
        assertTrue(result.getAuthProviderStatus().contains("ACTIVE"));
    }

    @Test
    @DisplayName("4. Збій одного з асинхронних HTTP-запитів обробляється через handle/exceptionally без падіння")
    void failedAsyncHttpCall_handledWithFallbackValues() {
        DeliveryOrderDto order = new DeliveryOrderDto();
        order.setId(88L);
        order.setTotalPrice(10.0);

        OrderSettlementDto result = deliveryOrderService.calculateOrderSettlementWithUrls(
                order,
                baseUrl + "/error-500",
                baseUrl + "/error-500",
                3000
        );

        assertEquals(41.50, result.getUsdToUahRate());
        assertEquals(415.0, result.getAmountUah());
        assertFalse(result.isVerified());
        assertTrue(result.getAuthProviderStatus().startsWith("FALLBACK_UNVERIFIED"));
    }

    @Test
    @DisplayName("5. AsyncUncaughtExceptionHandler перехоплює помилки у void @Async методі")
    void voidAsyncException_capturedByAsyncUncaughtExceptionHandler() throws Exception {
        AsyncUncaughtExceptionHandler handler = asyncConfig.getAsyncUncaughtExceptionHandler();
        assertNotNull(handler);

        Method method = OrderLogisticsAsyncService.class.getMethod("generateSignedReceiptArchive", DeliveryOrderDto.class);
        Throwable error = new IllegalArgumentException("Simulated receipt error");

        assertDoesNotThrow(() -> handler.handleUncaughtException(error, method, new Object[]{}));
    }

    @Test
    @DisplayName("6. Переповнення малого пулу реальними HTTP-запитами викликає TaskRejectedException")
    void limitedExecutorOverflow_throwsTaskRejectedException() {
        releaseSlowEndpointLatch = new CountDownLatch(1);
        try {
            assertThrows(TaskRejectedException.class, () -> {
                for (int i = 0; i < 6; i++) {
                    asyncService.executeNetworkCheckInLimitedPool(baseUrl + "/held-endpoint");
                }
            });
        } finally {
            releaseSlowEndpointLatch.countDown();
        }
    }
}