package com.assetshield.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron shell around {@link SchedulerService}; cadences are env-overridable
 * (SCHED_TIP_DELIVERY_CRON / SCHED_REDOC_CRON) so demos and tests can run
 * them every few seconds.
 */
@Component
public class NotificationSchedulers {

    private static final Logger log = LoggerFactory.getLogger(NotificationSchedulers.class);

    private final SchedulerService schedulerService;

    public NotificationSchedulers(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @Scheduled(cron = "${app.sched.tip-delivery-cron}", zone = "Africa/Accra")
    public void tipDelivery() {
        try {
            schedulerService.deliverTips();
        } catch (Exception e) {
            log.error("Tip delivery sweep failed: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${app.sched.redoc-cron}", zone = "Africa/Accra")
    public void redocReminder() {
        try {
            schedulerService.remindStaleDocumentation();
        } catch (Exception e) {
            log.error("Redoc reminder sweep failed: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${app.sched.maintenance-cron}", zone = "Africa/Accra")
    public void maintenanceReminder() {
        try {
            schedulerService.remindMaintenanceDue();
        } catch (Exception e) {
            log.error("Maintenance reminder sweep failed: {}", e.getMessage());
        }
    }
}
