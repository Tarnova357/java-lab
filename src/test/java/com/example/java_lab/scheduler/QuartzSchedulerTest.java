package com.example.java_lab.scheduler;

import com.example.java_lab.config.QuartzSchedulerConfig;
import com.example.java_lab.entity.DeliveryOrder;
import com.example.java_lab.entity.VirtualAsset;
import com.example.java_lab.entity.enums.OrderStatus;
import com.example.java_lab.repository.DeliveryOrderRepository;
import com.example.java_lab.repository.VirtualAssetRepository;
import com.example.java_lab.scheduler.job.PendingOrdersExpirationJob;
import com.example.java_lab.scheduler.job.StorageInventoryReportJob;
import com.example.java_lab.service.InventoryReportService;
import com.example.java_lab.service.OrderMaintenanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.quartz.CronTrigger;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuartzSchedulerTest {

    private final QuartzSchedulerConfig config = new QuartzSchedulerConfig();

    @Test
    @DisplayName("1. Завдання та тригери мають явні назви, групи та пов'язані через JobKey")
    void jobsAndTriggers_haveExplicitNamesGroupsAndJobKeys() {
        JobDetail expirationJob = config.pendingOrdersExpirationJobDetail();
        Trigger frequentTrigger = config.frequentOrderExpirationTrigger(expirationJob);
        Trigger nightlyTrigger = config.nightlyDeepOrderCleanupTrigger(expirationJob);

        assertEquals("pendingOrdersExpirationJob", expirationJob.getKey().getName());
        assertEquals(QuartzSchedulerConfig.GROUP_ORDERS, expirationJob.getKey().getGroup());
        assertEquals(expirationJob.getKey(), frequentTrigger.getJobKey());
        assertEquals(expirationJob.getKey(), nightlyTrigger.getJobKey());
        assertNotEquals(frequentTrigger.getKey(), nightlyTrigger.getKey());
    }

    @Test
    @DisplayName("2. Параметри тригера мають пріоритет над значеннями JobDetail за замовчуванням")
    void jobDataMapMerge_triggerOverridesJobDefaultsAndKeepsUnspecified() {
        JobDetail expirationJob = config.pendingOrdersExpirationJobDetail();
        Trigger frequentTrigger = config.frequentOrderExpirationTrigger(expirationJob);

        JobDataMap merged = new JobDataMap();
        merged.putAll(expirationJob.getJobDataMap());
        merged.putAll(frequentTrigger.getJobDataMap());

        assertEquals(30, merged.getInt(PendingOrdersExpirationJob.KEY_MAX_AGE_MINUTES));
        assertEquals(50, merged.getInt(PendingOrdersExpirationJob.KEY_BATCH_LIMIT));
    }

    @Test
    @DisplayName("3. Відсутній або некоректний ключ у JobDataMap викликає явний JobExecutionException")
    void missingOrInvalidJobDataKey_throwsExplicitException() {
        OrderMaintenanceService maintenanceService = mock(OrderMaintenanceService.class);
        PendingOrdersExpirationJob job = new PendingOrdersExpirationJob(maintenanceService);

        JobExecutionContext context = mock(JobExecutionContext.class);
        JobDataMap invalidMap = new JobDataMap();
        invalidMap.put("wrongKey", 10);
        when(context.getMergedJobDataMap()).thenReturn(invalidMap);

        JobExecutionException ex = assertThrows(JobExecutionException.class, () -> job.execute(context));
        assertFalse(ex.refireImmediately());
        verifyNoInteractions(maintenanceService);
    }

    @Test
    @DisplayName("4. Обидва класи завдань захищені від паралельного запуску (@DisallowConcurrentExecution)")
    void jobClasses_annotatedWithDisallowConcurrentExecution() {
        assertTrue(PendingOrdersExpirationJob.class.isAnnotationPresent(DisallowConcurrentExecution.class));
        assertTrue(StorageInventoryReportJob.class.isAnnotationPresent(DisallowConcurrentExecution.class));
    }

    @Test
    @DisplayName("5. Кожен тригер має чітко визначену інструкцію misfire")
    void triggers_haveExplicitMisfireInstructions() {
        JobDetail expirationJob = config.pendingOrdersExpirationJobDetail();
        SimpleTrigger frequentTrigger = (SimpleTrigger) config.frequentOrderExpirationTrigger(expirationJob);
        CronTrigger nightlyTrigger = (CronTrigger) config.nightlyDeepOrderCleanupTrigger(expirationJob);
        CronTrigger reportTrigger = (CronTrigger) config.dailyInventoryReportTrigger(config.storageInventoryReportJobDetail());

        assertEquals(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT,
                frequentTrigger.getMisfireInstruction());
        assertEquals(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING,
                nightlyTrigger.getMisfireInstruction());
        assertEquals(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW,
                reportTrigger.getMisfireInstruction());
    }

    @Test
    @DisplayName("6. Cron-тригери мають фіксований часовий пояс (невідомий ID замінюється на GMT)")
    void cronTriggers_haveExplicitTimeZoneAndFallbackToGmt() {
        CronTrigger nightlyTrigger = (CronTrigger) config.nightlyDeepOrderCleanupTrigger(
                config.pendingOrdersExpirationJobDetail());
        CronTrigger reportTrigger = (CronTrigger) config.dailyInventoryReportTrigger(
                config.storageInventoryReportJobDetail());

        assertEquals("Europe/Kyiv", nightlyTrigger.getTimeZone().getID());
        assertEquals("Europe/Kyiv", reportTrigger.getTimeZone().getID());
        assertEquals("GMT", TimeZone.getTimeZone("Unknown/Invalid_Zone").getID());
    }

    @Test
    @DisplayName("7. Повторний запуск сервісу завдання є ідемпотентним і не змінює дані вдруге")
    void maintenanceService_isIdempotentOnRepeatedRuns() {
        DeliveryOrderRepository orderRepo = mock(DeliveryOrderRepository.class);
        VirtualAssetRepository assetRepo = mock(VirtualAssetRepository.class);

        VirtualAsset asset = new VirtualAsset();
        asset.setId(1L);
        asset.setAmount(10L);

        DeliveryOrder pendingOrder = new DeliveryOrder();
        pendingOrder.setId(100L);
        pendingOrder.setStatus(OrderStatus.PENDING);
        pendingOrder.setQuantity(3L);
        pendingOrder.setVirtualAsset(asset);

        List<DeliveryOrder> dbState = new ArrayList<>(List.of(pendingOrder));
        when(orderRepo.findAll()).thenReturn(dbState);

        OrderMaintenanceService service = new OrderMaintenanceService(orderRepo, assetRepo);

        int firstRunChanged = service.cancelStalePendingOrders(30, 50);
        int secondRunChanged = service.cancelStalePendingOrders(30, 50);

        assertEquals(1, firstRunChanged);
        assertEquals(0, secondRunChanged);
        assertEquals(OrderStatus.CANCELLED, pendingOrder.getStatus());
        assertEquals(13L, asset.getAmount());
        verify(assetRepo, times(1)).save(asset);
    }

    @Test
    @DisplayName("8. Генерація звіту є ідемпотентною при повторному запуску (@TempDir)")
    void inventoryReportService_isIdempotent(@TempDir Path tempDir) {
        VirtualAssetRepository assetRepo = mock(VirtualAssetRepository.class);
        VirtualAsset asset = new VirtualAsset();
        asset.setName("AK-47 Redline");
        asset.setAmount(3L);
        when(assetRepo.findAll()).thenReturn(List.of(asset));

        InventoryReportService reportService = new InventoryReportService(assetRepo);
        ReflectionTestUtils.setField(reportService, "reportsDirectory", tempDir.toString());

        assertTrue(reportService.generateDailyInventoryReport(5, "Europe/Kyiv", false));
        assertFalse(reportService.generateDailyInventoryReport(5, "Europe/Kyiv", false));
    }

    @Test
    @DisplayName("9. Збій виконання обмежує кількість повторних спроб без нескінченного циклу")
    void failedJobExecution_retriesUpToMaxRetriesAndStops() {
        OrderMaintenanceService maintenanceService = mock(OrderMaintenanceService.class);
        when(maintenanceService.cancelStalePendingOrders(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Database connection lost"));

        PendingOrdersExpirationJob job = new PendingOrdersExpirationJob(maintenanceService);
        JobDataMap validMap = new JobDataMap();
        validMap.put(PendingOrdersExpirationJob.KEY_MAX_AGE_MINUTES, 30);
        validMap.put(PendingOrdersExpirationJob.KEY_BATCH_LIMIT, 50);

        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getMergedJobDataMap()).thenReturn(validMap);

        when(context.getRefireCount()).thenReturn(0);
        JobExecutionException firstEx = assertThrows(JobExecutionException.class, () -> job.execute(context));
        assertTrue(firstEx.refireImmediately());

        when(context.getRefireCount()).thenReturn(PendingOrdersExpirationJob.MAX_RETRIES);
        JobExecutionException finalEx = assertThrows(JobExecutionException.class, () -> job.execute(context));
        assertFalse(finalEx.refireImmediately());
    }
}