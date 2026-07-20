package com.assetshield.notification.service;

import com.assetshield.notification.client.PropertyClient;
import com.assetshield.notification.config.AppProperties;
import com.assetshield.notification.domain.MaintenanceReminder;
import com.assetshield.notification.domain.NotificationType;
import com.assetshield.notification.domain.RedocReminder;
import com.assetshield.notification.domain.Tip;
import com.assetshield.notification.domain.TipsFrequency;
import com.assetshield.notification.repo.MaintenanceReminderRepository;
import com.assetshield.notification.repo.RedocReminderRepository;
import com.assetshield.notification.repo.TipRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional bodies behind the cron shell — tests drive these directly. */
@Service
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final TipRepository tipRepository;
    private final RedocReminderRepository redocReminderRepository;
    private final MaintenanceReminderRepository maintenanceReminderRepository;
    private final PreferenceService preferenceService;
    private final TipGenerationService tipGenerationService;
    private final NotificationDispatchService dispatchService;
    private final PropertyClient propertyClient;
    private final AppProperties properties;
    private final Clock clock;

    public SchedulerService(TipRepository tipRepository,
                            RedocReminderRepository redocReminderRepository,
                            MaintenanceReminderRepository maintenanceReminderRepository,
                            PreferenceService preferenceService,
                            TipGenerationService tipGenerationService,
                            NotificationDispatchService dispatchService,
                            PropertyClient propertyClient, AppProperties properties, Clock clock) {
        this.tipRepository = tipRepository;
        this.redocReminderRepository = redocReminderRepository;
        this.maintenanceReminderRepository = maintenanceReminderRepository;
        this.preferenceService = preferenceService;
        this.tipGenerationService = tipGenerationService;
        this.dispatchService = dispatchService;
        this.propertyClient = propertyClient;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Daily 07:00 Africa/Accra. DAILY users get their batch every run, WEEKLY
     * only on Mondays, OFF accumulates silently. Feeds are topped up first so
     * the sweep also serves users whose properties had no recent uploads.
     */
    @Transactional
    public void deliverTips() {
        boolean monday = LocalDate.now(clock).getDayOfWeek() == DayOfWeek.MONDAY;
        Set<UUID> candidates = new LinkedHashSet<>(tipRepository.allUserIds());
        for (UUID userId : candidates) {
            TipsFrequency frequency = preferenceService.frequencyFor(userId);
            if (frequency == TipsFrequency.OFF || (frequency == TipsFrequency.WEEKLY && !monday)) {
                continue;
            }
            // generation first: keep the feed fresh for properties whose
            // upload trigger never fired since the last delivery
            for (UUID propertyId : tipRepository.propertyIdsForUser(userId)) {
                if (!tipRepository.existsByUserIdAndPropertyIdAndDeliveredAtIsNull(userId, propertyId)) {
                    tipGenerationService.generateNow(propertyId);
                }
            }
            List<Tip> undelivered = tipRepository.findByUserIdAndDeliveredAtIsNull(userId);
            if (undelivered.isEmpty()) {
                continue;
            }
            Instant now = clock.instant();
            for (Tip tip : undelivered) {
                tip.setDeliveredAt(now);
            }
            tipRepository.saveAll(undelivered);
            // one push per batch — the tip texts are read in-app
            dispatchService.dispatch(userId, NotificationType.TIP,
                    "You have " + undelivered.size() + " new safety tip"
                            + (undelivered.size() == 1 ? "" : "s"),
                    "Open AssetShield to read today's protection advice for your property.",
                    Map.of("deepLink", "tips/feed", "count", undelivered.size()));
            log.info("Delivered {} tip(s) to user {} ({})", undelivered.size(), userId, frequency);
        }
    }

    /**
     * Daily 09:00 Africa/Accra. Pages property-service's stale-documentation
     * list; one REDOC_REMINDER per property per suppression window.
     */
    @Transactional
    public void remindStaleDocumentation() {
        int staleDays = properties.sched().redocStaleDays();
        Instant suppressionCutoff = clock.instant().minus(Duration.ofDays(staleDays));
        int page = 0;
        PropertyClient.StalePage stalePage;
        do {
            stalePage = propertyClient.staleDocumentation(staleDays, page, 100);
            for (PropertyClient.StaleProperty property : stalePage.items()) {
                RedocReminder reminder = redocReminderRepository
                        .findById(property.propertyId()).orElse(null);
                if (reminder != null && reminder.getRemindedAt().isAfter(suppressionCutoff)) {
                    continue; // reminded recently — stay quiet
                }
                dispatchService.dispatch(property.ownerUserId(), NotificationType.REDOC_REMINDER,
                        "Time to refresh your documentation",
                        "It's been over " + staleDays + " days since you documented "
                                + property.name()
                                + " — prices and stock change; update your evidence.",
                        Map.of("propertyId", property.propertyId().toString()));
                if (reminder == null) {
                    redocReminderRepository.save(new RedocReminder(property.propertyId(), clock.instant()));
                } else {
                    reminder.setRemindedAt(clock.instant());
                    redocReminderRepository.save(reminder);
                }
            }
            page++;
        } while (page < stalePage.totalPages());
    }

    /**
     * Daily 08:15 Africa/Accra. Pages property-service's maintenance-due feed
     * for both kinds; one reminder per asset+kind per due date — rescheduling
     * the date re-arms the reminder.
     */
    @Transactional
    public void remindMaintenanceDue() {
        int lookaheadDays = properties.sched().maintenanceLookaheadDays();
        for (String kind : List.of("WARRANTY", "SERVICE")) {
            int page = 0;
            PropertyClient.MaintenancePage duePage;
            do {
                duePage = propertyClient.maintenanceDue(kind, lookaheadDays, page, 100);
                for (PropertyClient.MaintenanceDueItem item : duePage.items()) {
                    MaintenanceReminder reminder = maintenanceReminderRepository
                            .findById(new MaintenanceReminder.Key(item.assetId(), kind)).orElse(null);
                    if (reminder != null && reminder.getDueOn().equals(item.dueOn())) {
                        continue; // already reminded for this exact date
                    }
                    dispatchService.dispatch(item.ownerUserId(), NotificationType.MAINTENANCE_DUE,
                            "WARRANTY".equals(kind)
                                    ? "Warranty expiring soon" : "Maintenance due soon",
                            reminderBody(kind, item),
                            Map.of("assetId", item.assetId().toString(),
                                    "propertyId", item.propertyId().toString(),
                                    "kind", kind,
                                    "dueOn", item.dueOn().toString(),
                                    "deepLink", "asset/" + item.assetId()));
                    if (reminder == null) {
                        maintenanceReminderRepository.save(new MaintenanceReminder(
                                item.assetId(), kind, item.dueOn(), clock.instant()));
                    } else {
                        reminder.setDueOn(item.dueOn());
                        reminder.setRemindedAt(clock.instant());
                        maintenanceReminderRepository.save(reminder);
                    }
                    log.info("Maintenance reminder ({}) sent to user {} for asset {} due {}",
                            kind, item.ownerUserId(), item.assetId(), item.dueOn());
                }
                page++;
            } while (page < duePage.totalPages());
        }
    }

    /** notifications.body is VARCHAR(500) — keep the description contribution bounded. */
    private static String reminderBody(String kind, PropertyClient.MaintenanceDueItem item) {
        String description = item.description().length() > 120
                ? item.description().substring(0, 117) + "..." : item.description();
        String where = item.propertyName().length() > 120
                ? item.propertyName().substring(0, 117) + "..." : item.propertyName();
        return "WARRANTY".equals(kind)
                ? "The warranty on \"" + description + "\" at " + where + " expires on "
                        + item.dueOn() + ". Consider renewing or re-documenting it."
                : "\"" + description + "\" at " + where + " is due for servicing on "
                        + item.dueOn() + ".";
    }
}
