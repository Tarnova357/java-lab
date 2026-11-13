package com.example.java_lab.scheduler.job;

import com.example.java_lab.service.OrderMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class PendingOrdersExpirationJob implements Job {

    public static final String KEY_MAX_AGE_MINUTES = "maxAgeMinutes";
    public static final String KEY_BATCH_LIMIT = "batchLimit";
    public static final int MAX_RETRIES = 2;

    private final OrderMaintenanceService maintenanceService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();

        if (!dataMap.containsKey(KEY_MAX_AGE_MINUTES) || !dataMap.containsKey(KEY_BATCH_LIMIT)) {
            throw new JobExecutionException("Відсутні обов'язкові ключі в JobDataMap: maxAgeMinutes або batchLimit", false);
        }

        int maxAgeMinutes;
        int batchLimit;
        try {
            maxAgeMinutes = dataMap.getIntValue(KEY_MAX_AGE_MINUTES);
            batchLimit = dataMap.getIntValue(KEY_BATCH_LIMIT);
        } catch (ClassCastException | NumberFormatException e) {
            throw new JobExecutionException("Некоректний тип параметрів у JobDataMap", e, false);
        }

        if (maxAgeMinutes <= 0 || batchLimit <= 0) {
            throw new JobExecutionException("Параметри maxAgeMinutes та batchLimit мають бути більшими за 0", false);
        }

        try {
            maintenanceService.cancelStalePendingOrders(maxAgeMinutes, batchLimit);
        } catch (Exception ex) {
            int currentRetry = context.getRefireCount();
            if (currentRetry < MAX_RETRIES) {
                log.warn("[PendingOrdersExpirationJob] Збій виконання (спроба {}/{}). Повторний запуск...",
                        currentRetry + 1, MAX_RETRIES, ex);
                JobExecutionException refireEx = new JobExecutionException(ex);
                refireEx.setRefireImmediately(true);
                throw refireEx;
            }
            log.error("[PendingOrdersExpirationJob] Досягнуто ліміт спроб ({}). Очікування наступного розкладу.",
                    MAX_RETRIES, ex);
            throw new JobExecutionException(ex, false);
        }
    }
}