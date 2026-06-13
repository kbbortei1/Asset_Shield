package com.assetshield.damage.service;

import com.assetshield.damage.domain.AssetSnapshot;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Serializes the frozen asset snapshot to/from the JSONB column. */
@Component
public class SnapshotMapper {

    private final ObjectMapper objectMapper;

    public SnapshotMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(AssetSnapshot snapshot) {
        return objectMapper.writeValueAsString(snapshot);
    }

    public AssetSnapshot fromJson(String json) {
        return objectMapper.readValue(json, AssetSnapshot.class);
    }
}
