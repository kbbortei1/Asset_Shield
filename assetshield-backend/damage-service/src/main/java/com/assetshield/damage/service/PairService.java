package com.assetshield.damage.service;

import com.assetshield.damage.client.PropertyInternalClient;
import com.assetshield.damage.common.ApiException;
import com.assetshield.damage.common.ErrorCode;
import com.assetshield.damage.domain.AssetSnapshot;
import com.assetshield.damage.domain.DamagePhoto;
import com.assetshield.damage.domain.DamageReport;
import com.assetshield.damage.domain.PhotoPair;
import com.assetshield.damage.repo.PhotoPairRepository;
import com.assetshield.damage.security.AuthUser;
import com.assetshield.damage.web.dto.DamageDtos.CreatePairRequest;
import com.assetshield.damage.web.dto.DamageDtos.DeleteResponse;
import com.assetshield.damage.web.dto.DamageDtos.PairItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PairService {

    private final PhotoPairRepository pairRepository;
    private final ReportGuard guard;
    private final DamagePhotoService photoService;
    private final PropertyInternalClient propertyClient;
    private final SnapshotMapper snapshotMapper;
    private final DamageReportService reportService;

    public PairService(PhotoPairRepository pairRepository, ReportGuard guard,
                       DamagePhotoService photoService, PropertyInternalClient propertyClient,
                       SnapshotMapper snapshotMapper, DamageReportService reportService) {
        this.pairRepository = pairRepository;
        this.guard = guard;
        this.photoService = photoService;
        this.propertyClient = propertyClient;
        this.snapshotMapper = snapshotMapper;
        this.reportService = reportService;
    }

    /**
     * Freezes the asset's state into the pair's JSONB snapshot — the dossier
     * must stay reproducible even if the asset is later edited or deleted in
     * property-service. Nothing re-reads the live asset after this point.
     */
    @Transactional
    public PairItem create(AuthUser user, UUID reportId, CreatePairRequest request) {
        DamageReport report = guard.requireReport(reportId);
        guard.requireMutate(report.getPropertyId(), user.id());
        guard.requireDraft(report);

        DamagePhoto photo = photoService.requirePhotoOfReport(reportId, request.damagePhotoId());

        if (pairRepository.existsByDamagePhotoIdAndAssetId(photo.getId(), request.assetId())) {
            throw new ApiException(ErrorCode.PAIR_EXISTS,
                    "This photo is already paired with that asset");
        }

        PropertyInternalClient.AssetInfo asset = propertyClient.asset(request.assetId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Asset not found"));
        if (!asset.propertyId().equals(report.getPropertyId())) {
            // an asset from another property can never be evidence for this report
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Asset not found");
        }

        double distance = GeoMath.haversineMeters(
                photo.getGpsLat().doubleValue(), photo.getGpsLng().doubleValue(),
                asset.gpsLat().doubleValue(), asset.gpsLng().doubleValue());

        PhotoPair pair = new PhotoPair();
        pair.setDamageReportId(reportId);
        pair.setDamagePhotoId(photo.getId());
        pair.setAssetId(asset.id());
        pair.setPairingMethod(request.pairingMethod());
        pair.setDistanceMeters(BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP));
        pair.setAssetSnapshot(snapshotMapper.toJson(new AssetSnapshot(
                asset.objectPath(), asset.sha256Hash(), asset.description(), asset.estimatedValue(),
                asset.category(), asset.gpsLat(), asset.gpsLng(), asset.capturedAt())));
        PhotoPair saved = pairRepository.saveAndFlush(pair);
        return reportService.toPairItem(saved);
    }

    /** Pairs are links, not evidence — hard delete. */
    @Transactional
    public DeleteResponse delete(AuthUser user, UUID reportId, UUID pairId) {
        DamageReport report = guard.requireReport(reportId);
        guard.requireMutate(report.getPropertyId(), user.id());
        guard.requireDraft(report);

        PhotoPair pair = pairRepository.findById(pairId)
                .filter(p -> p.getDamageReportId().equals(reportId))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Pair not found"));
        pairRepository.delete(pair);
        return new DeleteResponse(true);
    }
}
