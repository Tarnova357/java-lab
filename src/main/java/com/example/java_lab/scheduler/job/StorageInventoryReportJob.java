package com.example.java_lab.scheduler.job;

import com.example.java_lab.service.InventoryReportService;
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
public class StorageInventoryReportJob implements Job {

    public static final String KEY_LOW_STOCK_THRESHOLD = "lowStockThreshold";
    public static final String KEY_TIMEZONE = "timeZone";
    public static final String KEY_FORCE_OVERWRITE = "forceOverwrite";
    public static final int MAX_RETRIES = 2;

    private final InventoryReportService reportService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();

        if (!dataMap.containsKey(KEY_LOW_STOCK_THRESHOLD)) {
            throw new JobExecutionException("Відсутній обов'язковий ключ у JobDataMap: lowStockThreshold", false);
        }

        int threshold;
        try {
            threshold = dataMap.getIntValue(KEY_LOW_STOCK_THRESHOLD);
        } catch (ClassCastException | NumberFormatException e) {
            throw new JobExecutionException("Некоректний тип параметра lowStockThreshold", e, false);
        }

        if (threshold < 0) {
            throw new JobExecutionException("Параметр lowStockThreshold не може бути від'ємним", false);
        }

        String timeZone = dataMap.containsKey(KEY_TIMEZONE) ? dataMap.getString(KEY_TIMEZONE) : "Europe/Kyiv";
        boolean forceOverwrite = dataMap.containsKey(KEY_FORCE_OVERWRITE) && dataMap.getBooleanValue(KEY_FORCE_OVERWRITE);

        try {
            reportService.generateDailyInventoryReport(threshold, timeZone, forceOverwrite);
        } catch (Exception ex) {
            int currentRetry = context.getRefireCount();
            if (currentRetry < MAX_RETRIES) {
                log.warn("[StorageInventoryReportJob] Збій генерації звіту (спроба {}/{}). Повторний запуск...",
                        currentRetry + 1, MAX_RETRIES, ex);
                JobExecutionException refireEx = new JobExecutionException(ex);
                refireEx.setRefireImmediately(true);
                throw refireEx;
            }
            log.error("[StorageInventoryReportJob] Вичерпано ліміт спроб ({}). Зупинка повторів.", MAX_RETRIES, ex);
            throw new JobExecutionException(ex, false);
        }
    }
}