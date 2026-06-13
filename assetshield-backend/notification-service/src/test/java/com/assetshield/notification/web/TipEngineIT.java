package com.assetshield.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetshield.notification.client.PropertyClient;
import com.assetshield.notification.domain.Tip;
import com.assetshield.notification.domain.TipTemplate;
import com.assetshield.notification.repo.TipRepository;
import com.assetshield.notification.repo.TipTemplateRepository;
import com.assetshield.notification.service.TipEngine;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class TipEngineIT extends NotificationITBase {

    @Autowired
    TipEngine tipEngine;

    @Autowired
    TipRepository tipRepository;

    @Autowired
    TipTemplateRepository templateRepository;

    private Map<UUID, TipTemplate> templatesById() {
        return templateRepository.findAll().stream()
                .collect(Collectors.toMap(TipTemplate::getId, Function.identity()));
    }

    /** The PRD scenario: Kantamanto stall, clothing stock, Harmattan. */
    @Test
    void kantamantoClothingStallInHarmattanLeadsWithCommercialFireTips() {
        clock.setDate(2026, 1, 15); // HARMATTAN; Kantamanto is in no flood zone
        var ctx = context(UUID.randomUUID(), UUID.randomUUID(), "COMMERCIAL",
                KANTAMANTO_LAT, KANTAMANTO_LNG, line("CLOTHING_STOCK", 40, "18000.00"));

        List<Tip> tips = tipEngine.generateFor(ctx);

        assertThat(tips).hasSizeGreaterThanOrEqualTo(3);
        // the priority-1 commercial clothing-stock fire tip leads the batch
        assertThat(tips.get(0).getCategory()).isEqualTo("FIRE");
        Map<UUID, TipTemplate> templates = templatesById();
        TipTemplate first = templates.get(tips.get(0).getTipTemplateId());
        assertThat(first.getPriority()).isEqualTo((short) 1);
        assertThat(first.getAppliesPropertyType()).isEqualTo("COMMERCIAL");
        assertThat(first.getAppliesAssetCategory()).isEqualTo("CLOTHING_STOCK");
        // priorities never decrease down the batch; everything matched commercial
        for (int i = 1; i < tips.size(); i++) {
            assertThat(templates.get(tips.get(i).getTipTemplateId()).getPriority())
                    .isGreaterThanOrEqualTo(templates.get(tips.get(i - 1).getTipTemplateId()).getPriority());
        }
        // never a flood-zone or residential template for this context
        for (Tip tip : tips) {
            TipTemplate template = templates.get(tip.getTipTemplateId());
            assertThat(Boolean.TRUE.equals(template.getAppliesFloodZone())).isFalse();
            assertThat(template.getAppliesPropertyType()).isNotEqualTo("RESIDENTIAL");
        }
    }

    @Test
    void floodZoneTemplatesMatchInsideKaneshieAndNeverOutside() {
        clock.setDate(2026, 5, 15); // RAINY season
        Map<UUID, TipTemplate> templates = templatesById();

        // inside the Kaneshie box → flood-zone templates rank top (priority 1)
        List<Tip> inside = tipEngine.generateFor(context(UUID.randomUUID(), UUID.randomUUID(),
                "COMMERCIAL", KANESHIE_LAT, KANESHIE_LNG, line("ELECTRONICS", 5, "2000.00")));
        assertThat(inside).anyMatch(tip ->
                Boolean.TRUE.equals(templates.get(tip.getTipTemplateId()).getAppliesFloodZone()));

        // same context outside every box → zero flood-zone templates
        List<Tip> outside = tipEngine.generateFor(context(UUID.randomUUID(), UUID.randomUUID(),
                "COMMERCIAL", KANTAMANTO_LAT, KANTAMANTO_LNG, line("ELECTRONICS", 5, "2000.00")));
        assertThat(outside).isNotEmpty();
        assertThat(outside).noneMatch(tip ->
                Boolean.TRUE.equals(templates.get(tip.getTipTemplateId()).getAppliesFloodZone()));
    }

    @Test
    void floodZoneBoundingBoxEdgeIsInclusive() {
        clock.setDate(2026, 5, 15); // RAINY
        Map<UUID, TipTemplate> templates = templatesById();
        // exact south-west corner of the seeded Kaneshie box
        List<Tip> tips = tipEngine.generateFor(context(UUID.randomUUID(), UUID.randomUUID(),
                "COMMERCIAL", new java.math.BigDecimal("5.556700"),
                new java.math.BigDecimal("-0.243300"), line("ELECTRONICS", 2, "1000.00")));
        assertThat(tips).anyMatch(tip ->
                Boolean.TRUE.equals(templates.get(tip.getTipTemplateId()).getAppliesFloodZone()));
    }

    @Test
    void minCategoryValueGatesTheHighValueTheftTip() {
        clock.setDate(2026, 9, 15); // neither season — fewer competing templates
        UUID serialTipTemplate = templateRepository.findAll().stream()
                .filter(t -> "THEFT".equals(t.getCategory())
                        && "ELECTRONICS".equals(t.getAppliesAssetCategory())
                        && t.getMinCategoryValue() != null
                        && t.getMinCategoryValue().intValue() == 5000)
                .findFirst().orElseThrow().getId();

        List<Tip> rich = tipEngine.generateFor(context(UUID.randomUUID(), UUID.randomUUID(),
                "RESIDENTIAL", KANTAMANTO_LAT, KANTAMANTO_LNG, line("ELECTRONICS", 4, "6000.00")));
        assertThat(rich).anyMatch(tip -> tip.getTipTemplateId().equals(serialTipTemplate));

        List<Tip> modest = tipEngine.generateFor(context(UUID.randomUUID(), UUID.randomUUID(),
                "RESIDENTIAL", KANTAMANTO_LAT, KANTAMANTO_LNG, line("ELECTRONICS", 4, "4000.00")));
        assertThat(modest).noneMatch(tip -> tip.getTipTemplateId().equals(serialTipTemplate));
    }

    @Test
    void generationsNeverRepeatTemplatesAndDrainTheCandidatePool() {
        clock.setDate(2026, 9, 15);
        UUID propertyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        // a deliberately narrow context: only the generic candidates apply
        var ctx = context(propertyId, ownerId, "RENTAL",
                KANTAMANTO_LAT, KANTAMANTO_LNG, line("OTHER", 1, "100.00"));

        Set<UUID> seen = new HashSet<>();
        int previousBatch = Integer.MAX_VALUE;
        for (int round = 0; round < 10; round++) {
            List<Tip> batch = tipEngine.generateFor(ctx);
            for (Tip tip : batch) {
                assertThat(seen.add(tip.getTipTemplateId()))
                        .as("template repeated across generations")
                        .isTrue();
            }
            if (batch.isEmpty()) {
                break; // pool exhausted — never pads with already-seen tips
            }
            previousBatch = batch.size();
        }
        assertThat(previousBatch).isLessThanOrEqualTo(3);

        // ux_tip_once is the race-safe backstop beneath the query filter
        UUID anyUsed = seen.iterator().next();
        Tip duplicate = new Tip();
        duplicate.setUserId(ownerId);
        duplicate.setPropertyId(propertyId);
        duplicate.setTipTemplateId(anyUsed);
        duplicate.setTipText("dup");
        duplicate.setCategory("GENERAL");
        assertThatThrownBy(() -> tipRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
