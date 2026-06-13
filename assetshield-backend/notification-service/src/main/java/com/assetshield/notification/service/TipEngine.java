package com.assetshield.notification.service;

import com.assetshield.notification.client.PropertyClient;
import com.assetshield.notification.config.AppProperties;
import com.assetshield.notification.domain.FloodZone;
import com.assetshield.notification.domain.Season;
import com.assetshield.notification.domain.Tip;
import com.assetshield.notification.domain.TipTemplate;
import com.assetshield.notification.repo.FloodZoneRepository;
import com.assetshield.notification.repo.TipRepository;
import com.assetshield.notification.repo.TipTemplateRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Ghana-specific safety-tips rule engine. Matches active templates
 * against a property's type, asset categories/values, current season
 * (Africa/Accra) and flood-zone membership; never repeats a template for the
 * same user+property (ux_tip_once is the race-safe backstop).
 */
@Service
public class TipEngine {

    private static final Logger log = LoggerFactory.getLogger(TipEngine.class);

    private final TipTemplateRepository templateRepository;
    private final TipRepository tipRepository;
    private final FloodZoneRepository floodZoneRepository;
    private final AppProperties properties;
    private final Clock clock;

    public TipEngine(TipTemplateRepository templateRepository, TipRepository tipRepository,
                     FloodZoneRepository floodZoneRepository, AppProperties properties, Clock clock) {
        this.templateRepository = templateRepository;
        this.tipRepository = tipRepository;
        this.floodZoneRepository = floodZoneRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public List<Tip> generateFor(PropertyClient.TipsContext context) {
        Season season = Season.forDate(LocalDate.now(clock));
        boolean inFloodZone = floodZoneRepository.findAll().stream()
                .anyMatch(zone -> zone.contains(context.gpsLat(), context.gpsLng()));
        Map<String, BigDecimal> valueByCategory = new HashMap<>();
        Set<String> presentCategories = new HashSet<>();
        for (PropertyClient.CategoryLine line : context.byCategory()) {
            if (line.count() > 0) {
                presentCategories.add(line.category());
                valueByCategory.merge(line.category(),
                        line.value() == null ? BigDecimal.ZERO : line.value(), BigDecimal::add);
            }
        }

        Set<UUID> alreadyUsed = new HashSet<>(
                tipRepository.usedTemplateIds(context.ownerUserId(), context.propertyId()));

        record Scored(TipTemplate template, int specificity, long tiebreak) {
        }
        List<Scored> candidates = templateRepository.findByActiveTrueAndLanguage("en").stream()
                .filter(template -> !alreadyUsed.contains(template.getId()))
                .filter(template -> matches(template, context.propertyType(), presentCategories,
                        valueByCategory, season, inFloodZone))
                .map(template -> new Scored(template, specificity(template),
                        ThreadLocalRandom.current().nextLong()))
                .sorted(Comparator
                        .comparingInt((Scored scored) -> scored.template().getPriority())
                        .thenComparing(Comparator.comparingInt(Scored::specificity).reversed())
                        .thenComparingLong(Scored::tiebreak))
                .toList();

        int batchSize = Math.max(3, properties.tips().batchSize());
        List<Tip> created = candidates.stream()
                .limit(batchSize)
                .map(scored -> toTip(scored.template(), context))
                .toList();
        try {
            created = tipRepository.saveAllAndFlush(created);
        } catch (DataIntegrityViolationException e) {
            // a concurrent generation won the ux_tip_once race — its tips stand
            log.warn("Concurrent tip generation for property {} — skipping duplicates",
                    context.propertyId());
            return List.of();
        }
        log.info("Tip generation for property {}: {} candidate(s), {} created (season={}, floodZone={})",
                context.propertyId(), candidates.size(), created.size(), season, inFloodZone);
        return created;
    }

    /** Every non-NULL applies_* column must match the context. */
    private static boolean matches(TipTemplate template, String propertyType,
                                   Set<String> presentCategories,
                                   Map<String, BigDecimal> valueByCategory,
                                   Season season, boolean inFloodZone) {
        if (template.getAppliesPropertyType() != null
                && !template.getAppliesPropertyType().equals(propertyType)) {
            return false;
        }
        if (template.getAppliesAssetCategory() != null
                && !presentCategories.contains(template.getAppliesAssetCategory())) {
            return false;
        }
        if (template.getAppliesSeason() != null && !"ANY".equals(template.getAppliesSeason())
                && (season == null || !template.getAppliesSeason().equals(season.name()))) {
            return false;
        }
        if (Boolean.TRUE.equals(template.getAppliesFloodZone()) && !inFloodZone) {
            return false;
        }
        if (template.getMinCategoryValue() != null) {
            // value threshold applies to the targeted category, or any category
            // when the template targets none
            BigDecimal total = template.getAppliesAssetCategory() != null
                    ? valueByCategory.getOrDefault(template.getAppliesAssetCategory(), BigDecimal.ZERO)
                    : valueByCategory.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(template.getMinCategoryValue()) < 0) {
                return false;
            }
        }
        return true;
    }

    /** +1 per non-NULL matched dimension — specific beats generic at equal priority. */
    private static int specificity(TipTemplate template) {
        int score = 0;
        if (template.getAppliesPropertyType() != null) {
            score++;
        }
        if (template.getAppliesAssetCategory() != null) {
            score++;
        }
        if (template.getAppliesSeason() != null && !"ANY".equals(template.getAppliesSeason())) {
            score++;
        }
        if (Boolean.TRUE.equals(template.getAppliesFloodZone())) {
            score++;
        }
        if (template.getMinCategoryValue() != null) {
            score++;
        }
        return score;
    }

    private static Tip toTip(TipTemplate template, PropertyClient.TipsContext context) {
        Tip tip = new Tip();
        tip.setUserId(context.ownerUserId());
        tip.setPropertyId(context.propertyId());
        tip.setTipTemplateId(template.getId());
        tip.setTipText(template.getTipText());
        tip.setCategory(template.getCategory());
        return tip;
    }
}
