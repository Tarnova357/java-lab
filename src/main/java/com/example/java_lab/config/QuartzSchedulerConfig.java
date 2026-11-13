package com.example.java_lab.config;

import com.example.java_lab.scheduler.job.PendingOrdersExpirationJob;
import com.example.java_lab.scheduler.job.StorageInventoryReportJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class QuartzSchedulerConfig {

    public static final String GROUP_ORDERS = "orders-maintenance";
    public static final String GROUP_REPORTS = "inventory-reports";

    public static final JobKey EXPIRATION_JOB_KEY = JobKey.jobKey("pendingOrdersExpirationJob", GROUP_ORDERS);
    public static final JobKey REPORT_JOB_KEY = JobKey.jobKey("storageInventoryReportJob", GROUP_REPORTS);

    @Bean
    public JobDetail pendingOrdersExpirationJobDetail() {
        return JobBuilder.newJob(PendingOrdersExpirationJob.class)
                .withIdentity(EXPIRATION_JOB_KEY)
                .withDescription("Скасування прострочених PENDING замовлень та повернення активів")
                .usingJobData(PendingOrdersExpirationJob.KEY_MAX_AGE_MINUTES, 60)
                .usingJobData(PendingOrdersExpirationJob.KEY_BATCH_LIMIT, 50)
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger frequentOrderExpirationTrigger(JobDetail pendingOrdersExpirationJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(pendingOrdersExpirationJobDetail)
                .withIdentity("frequentOrderExpirationTrigger", GROUP_ORDERS)
                .withDescription("Часта перевірка замовлень кожні 15 хвилин")
                .usingJobData(PendingOrdersExpirationJob.KEY_MAX_AGE_MINUTES, 30)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMinutes(15)
                        .repeatForever()
                        .withMisfireHandlingInstructionNextWithRemainingCount())
                .build();
    }

    @Bean
    public Trigger nightlyDeepOrderCleanupTrigger(JobDetail pendingOrdersExpirationJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(pendingOrdersExpirationJobDetail)
                .withIdentity("nightlyDeepOrderCleanupTrigger", GROUP_ORDERS)
                .withDescription("Нічне глибоке очищення замовлень о 03:00 за Києвом")
                .usingJobData(PendingOrdersExpirationJob.KEY_MAX_AGE_MINUTES, 1440)
                .usingJobData(PendingOrdersExpirationJob.KEY_BATCH_LIMIT, 500)
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 3 * * ?")
                        .inTimeZone(TimeZone.getTimeZone("Europe/Kyiv"))
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }

    @Bean
    public JobDetail storageInventoryReportJobDetail() {
        return JobBuilder.newJob(StorageInventoryReportJob.class)
                .withIdentity(REPORT_JOB_KEY)
                .withDescription("Формування щоденного звіту залишків віртуальних активів")
                .usingJobData(StorageInventoryReportJob.KEY_LOW_STOCK_THRESHOLD, 5)
                .usingJobData(StorageInventoryReportJob.KEY_TIMEZONE, "Europe/Kyiv")
                .usingJobData(StorageInventoryReportJob.KEY_FORCE_OVERWRITE, false)
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger dailyInventoryReportTrigger(JobDetail storageInventoryReportJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(storageInventoryReportJobDetail)
                .withIdentity("dailyInventoryReportTrigger", GROUP_REPORTS)
                .withDescription("Щоденний запуск генерації звіту о 08:00 за Києвом")
                .usingJobData(StorageInventoryReportJob.KEY_LOW_STOCK_THRESHOLD, 10)
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 8 * * ?")
                        .inTimeZone(TimeZone.getTimeZone("Europe/Kyiv"))
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
}