package com.assetshield.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * A rule-engine template. The applies_* columns are nullable filters: NULL
 * means "matches any context"; non-NULL must match exactly (see TipEngine).
 * Kept as plain strings — they mirror enums owned by property-service.
 */
@Entity
@Table(name = "tip_templates")
public class TipTemplate {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "tip_text", nullable = false, length = 600)
    private String tipText;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, length = 5)
    private String language = "en";

    @Column(name = "applies_property_type", length = 20)
    private String appliesPropertyType;

    @Column(name = "applies_asset_category", length = 20)
    private String appliesAssetCategory;

    @Column(name = "applies_season", length = 15)
    private String appliesSeason;

    @Column(name = "applies_flood_zone")
    private Boolean appliesFloodZone;

    @Column(name = "min_category_value", precision = 12, scale = 2)
    private BigDecimal minCategoryValue;

    @Column(nullable = false)
    private short priority = 5;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public String getTipText() {
        return tipText;
    }

    public void setTipText(String tipText) {
        this.tipText = tipText;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getAppliesPropertyType() {
        return appliesPropertyType;
    }

    public void setAppliesPropertyType(String appliesPropertyType) {
        this.appliesPropertyType = appliesPropertyType;
    }

    public String getAppliesAssetCategory() {
        return appliesAssetCategory;
    }

    public void setAppliesAssetCategory(String appliesAssetCategory) {
        this.appliesAssetCategory = appliesAssetCategory;
    }

    public String getAppliesSeason() {
        return appliesSeason;
    }

    public void setAppliesSeason(String appliesSeason) {
        this.appliesSeason = appliesSeason;
    }

    public Boolean getAppliesFloodZone() {
        return appliesFloodZone;
    }

    public void setAppliesFloodZone(Boolean appliesFloodZone) {
        this.appliesFloodZone = appliesFloodZone;
    }

    public BigDecimal getMinCategoryValue() {
        return minCategoryValue;
    }

    public void setMinCategoryValue(BigDecimal minCategoryValue) {
        this.minCategoryValue = minCategoryValue;
    }

    public short getPriority() {
        return priority;
    }

    public void setPriority(short priority) {
        this.priority = priority;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
