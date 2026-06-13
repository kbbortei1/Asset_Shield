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

    @Transactional
    public TipsFrequency update(UUID userId, TipsFrequency frequency) {
        NotificationPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    NotificationPreference created = new NotificationPreference();
                    created.setUserId(userId);
                    return created;
                });
        preference.setTipsFrequency(frequency);
        return preferenceRepository.save(preference).getTipsFrequency();
    }

    /** No row means the default: WEEKLY. */
    @Transactional(readOnly = true)
    public TipsFrequency frequencyFor(UUID userId) {
        return preferenceRepository.findByUserId(userId)
                .map(NotificationPreference::getTipsFrequency)
                .orElse(TipsFrequency.WEEKLY);
    }
}
