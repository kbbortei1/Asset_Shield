package com.assetshield.notification.service;

import com.assetshield.notification.domain.NotificationPreference;
import com.assetshield.notification.domain.TipsFrequency;
import com.assetshield.notification.repo.NotificationPreferenceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    public PreferenceService(NotificationPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    /** Full snapshot returned to the client / used by dispatch. */
    public record Prefs(TipsFrequency tipsFrequency, boolean pushEnabled, boolean inAppEnabled) {
    }

    /** The two delivery-channel switches dispatch consults. */
    public record Channels(boolean pushEnabled, boolean inAppEnabled) {
    }

    private static final Prefs DEFAULTS = new Prefs(TipsFrequency.WEEKLY, true, true);

    /** Partial update: only non-null fields change; returns the full snapshot. */
    @Transactional
    public Prefs update(UUID userId, TipsFrequency frequency, Boolean pushEnabled, Boolean inAppEnabled) {
        NotificationPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    NotificationPreference created = new NotificationPreference();
                    created.setUserId(userId);
                    return created;
                });
        if (frequency != null) {
            preference.setTipsFrequency(frequency);
        }
        if (pushEnabled != null) {
            preference.setPushEnabled(pushEnabled);
        }
        if (inAppEnabled != null) {
            preference.setInAppEnabled(inAppEnabled);
        }
        return toPrefs(preferenceRepository.save(preference));
    }

    /** No row means the defaults (WEEKLY tips, both channels on). */
    @Transactional(readOnly = true)
    public Prefs get(UUID userId) {
        return preferenceRepository.findByUserId(userId).map(PreferenceService::toPrefs).orElse(DEFAULTS);
    }

    /** No row means the default: WEEKLY. */
    @Transactional(readOnly = true)
    public TipsFrequency frequencyFor(UUID userId) {
        return get(userId).tipsFrequency();
    }

    /** Delivery-channel switches for the dispatch pipeline (defaults: both on). */
    @Transactional(readOnly = true)
    public Channels channelsFor(UUID userId) {
        Prefs prefs = get(userId);
        return new Channels(prefs.pushEnabled(), prefs.inAppEnabled());
    }

    private static Prefs toPrefs(NotificationPreference p) {
        return new Prefs(p.getTipsFrequency(), p.isPushEnabled(), p.isInAppEnabled());
    }
}
