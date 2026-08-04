package com.assetshield.damage.service;

import com.assetshield.damage.client.PropertyInternalClient;
import com.assetshield.damage.common.ApiException;
import com.assetshield.damage.common.ErrorCode;
import com.assetshield.damage.common.PageEnvelope;
import com.assetshield.damage.config.AppProperties;
import com.assetshield.damage.domain.AssetSnapshot;
import com.assetshield.damage.domain.DamagePhoto;
import com.assetshield.damage.domain.DamageReport;
import com.assetshield.damage.domain.PhotoPair;
import com.assetshield.damage.domain.ReportStatus;
import com.assetshield.damage.repo.DamagePhotoRepository;
import com.assetshield.damage.repo.DamageReportRepository;
import com.assetshield.damage.repo.PhotoPairRepository;
import com.assetshield.damage.security.AuthUser;
import com.assetshield.damage.storage.StorageProvider;
import com.assetshield.damage.web.dto.DamageDtos.BeforeBlock;
import com.assetshield.damage.web.dto.DamageDtos.CompleteResponse;
import com.assetshield.damage.web.dto.DamageDtos.CreateReportRequest;
import com.assetshield.damage.web.dto.DamageDtos.MyReportItem;
import com.assetshield.damage.web.dto.DamageDtos.PairItem;
import com.assetshield.damage.web.dto.DamageDtos.PhotoItem;
import com.assetshield.damage.web.dto.DamageDtos.ReportCreatedResponse;
import com.assetshield.damage.web.dto.DamageDtos.ReportDetailResponse;
import com.assetshield.damage.web.dto.DamageDtos.ReportListItem;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DamageReportService {

    private final DamageReportRepository reportRepository;
    private final DamagePhotoRepository photoRepository;
    private final PhotoPairRepository pairRepository;
    private final ReportGuard guard;
    private final PropertyInternalClient propertyClient;
    private final SnapshotMapper snapshotMapper;
    private final StorageProvider storageProvider;
    private final Duration signedUrlTtl;

    public DamageReportService(DamageReportRepository reportRepository,
                               DamagePhotoRepository photoRepository,
                               PhotoPairRepository pairRepository,
                               ReportGuard guard,
                               PropertyInternalClient propertyClient,
                               SnapshotMapper snapshotMapper,
                               StorageProvider storageProvider,
                               AppProperties properties) {
        this.reportRepository = reportRepository;
        this.photoRepository = photoRepository;
        this.pairRepository = pairRepository;
        this.guard = guard;
        this.propertyClient = propertyClient;
        this.snapshotMapper = snapshotMapper;
        this.storageProvider = storageProvider;
        this.signedUrlTtl = Duration.ofMinutes(properties.storage().signedUrlTtlMinutes());
    }

    @Transactional
    public ReportCreatedResponse create(AuthUser user, UUID propertyId, CreateReportRequest request) {
        PropertyInternalClient.PropertyInfo property = propertyClient.property(propertyId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Property not found"));
        if (property.deleted()) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Property not found");
        }
        guard.requireMutate(propertyId, user.id());

        DamageReport report = new DamageReport();
        report.setPropertyId(propertyId);
        report.setCreatedByUserId(user.id());
        report.setDisasterType(request.disasterType());
        report.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        report.setOccurredAt(request.occurredAt());
        // flush so @CreationTimestamp is populated before the DTO is built
        DamageReport saved = reportRepository.saveAndFlush(report);
        return new ReportCreatedResponse(saved.getId(), saved.getPropertyId(), saved.getDisasterType(),
                saved.getStatus(), saved.getOccurredAt(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public PageEnvelope<ReportListItem> listForProperty(AuthUser user, UUID propertyId, int page, int size) {
        guard.requireView(propertyId, user.id());
        Page<DamageReport> reports = reportRepository.findByPropertyIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                propertyId, PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size)));
        Map<UUID, DamageReportRepository.ReportCounts> counts = countsFor(reports);
        return PageEnvelope.of(reports.map(r -> new ReportListItem(r.getId(), r.getDisasterType(),
                r.getStatus(), r.getOccurredAt(), r.getTotalEstimatedLoss(),
                photoCount(counts, r.getId()), pairCount(counts, r.getId()), r.getCompletedAt())));
    }

    @Transactional(readOnly = true)
    public PageEnvelope<MyReportItem> myReports(AuthUser user, int page, int size) {
        Page<DamageReport> reports = reportRepository.findByCreatedByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                user.id(), PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size)));
        Map<UUID, DamageReportRepository.ReportCounts> counts = countsFor(reports);
        return PageEnvelope.of(reports.map(r -> new MyReportItem(r.getId(), r.getPropertyId(),
                r.getDisasterType(), r.getStatus(), r.getOccurredAt(), r.getTotalEstimatedLoss(),
                photoCount(counts, r.getId()), pairCount(counts, r.getId()), r.getCompletedAt())));
    }

    @Transactional(readOnly = true)
    public ReportDetailResponse detail(AuthUser user, UUID reportId) {
        DamageReport report = guard.requireReport(reportId);
        // The report's author can always view it — even after the property was
        // deleted — so their dossier evidence remains accessible. Others need
        // live property view access.
        if (!report.getCreatedByUserId().equals(user.id())) {
            guard.requireView(report.getPropertyId(), user.id());
        }

        List<PhotoPair> pairs = pairRepository.findByDamageReportIdOrderByCreatedAtAsc(reportId);
        Set<UUID> pairedPhotoIds = pairs.stream().map(PhotoPair::getDamagePhotoId)
                .collect(Collectors.toSet());

        List<PhotoItem> photos = photoRepository
                .findByDamageReportIdAndDeletedAtIsNullOrderByCreatedAtAsc(reportId).stream()
                .map(p -> new PhotoItem(p.getId(),
                        storageProvider.signedUrl(p.getPhotoUrl(), signedUrlTtl), p.getSha256Hash(),
                        p.getGpsLat(), p.getGpsLng(), p.getCapturedAt(), p.getDescription(),
                        pairedPhotoIds.contains(p.getId())))
                .toList();

        List<PairItem> pairItems = pairs.stream().map(this::toPairItem).toList();

        return new ReportDetailResponse(report.getId(), report.getPropertyId(), report.getDisasterType(),
                report.getStatus(), report.getDescription(), report.getOccurredAt(),
                report.getTotalEstimatedLoss(), report.getCompletedAt(), photos, pairItems);
    }

    @Transactional
    public CompleteResponse complete(AuthUser user, UUID reportId) {
        DamageReport report = guard.requireReport(reportId);
        guard.requireMutate(report.getPropertyId(), user.id());
        guard.requireDraft(report);

        long photoCount = photoRepository.countByDamageReportIdAndDeletedAtIsNull(reportId);
        if (photoCount == 0) {
            throw new ApiException(ErrorCode.EMPTY_REPORT,
                    "A damage report needs at least one photo before completion");
        }

        List<PhotoPair> pairs = pairRepository.findByDamageReportIdOrderByCreatedAtAsc(reportId);
        BigDecimal totalLoss = LossCalculator.totalLoss(pairs, PhotoPair::getAssetId,
                pair -> snapshotMapper.fromJson(pair.getAssetSnapshot()));

        report.setStatus(ReportStatus.COMPLETED);
        report.setCompletedAt(Instant.now());
        report.setTotalEstimatedLoss(totalLoss);
        reportRepository.saveAndFlush(report);

        return new CompleteResponse(report.getStatus(), report.getTotalEstimatedLoss(),
                pairs.size(), photoCount, report.getCompletedAt());
    }

    /** Everything Day 4's PDF builder needs in one call — object paths, not signed URLs. */
    @Transactional(readOnly = true)
    public com.assetshield.damage.web.dto.DamageDtos.InternalReportResponse internalReport(UUID reportId) {
        DamageReport report = guard.requireReport(reportId);
        List<com.assetshield.damage.web.dto.DamageDtos.InternalPhoto> photos = photoRepository
                .findByDamageReportIdAndDeletedAtIsNullOrderByCreatedAtAsc(reportId).stream()
                .map(p -> new com.assetshield.damage.web.dto.DamageDtos.InternalPhoto(p.getId(),
                        p.getPhotoUrl(), p.getSha256Hash(), p.getGpsLat(), p.getGpsLng(),
                        p.getCapturedAt(), p.getDescription()))
                .toList();
        List<com.assetshield.damage.web.dto.DamageDtos.InternalPair> pairs = pairRepository
                .findByDamageReportIdOrderByCreatedAtAsc(reportId).stream()
                .map(pair -> {
                    AssetSnapshot snapshot = snapshotMapper.fromJson(pair.getAssetSnapshot());
                    return new com.assetshield.damage.web.dto.DamageDtos.InternalPair(pair.getId(),
                            pair.getDamagePhotoId(), pair.getAssetId(), pair.getPairingMethod(),
                            pair.getDistanceMeters(),
                            new BeforeBlock(snapshot.objectPath(), snapshot.sha256Hash(),
                                    snapshot.description(), snapshot.estimatedValue(),
                                    snapshot.category(), snapshot.gpsLat(), snapshot.gpsLng(),
                                    snapshot.capturedAt()));
                })
                .toList();
        return new com.assetshield.damage.web.dto.DamageDtos.InternalReportResponse(report.getId(),
                report.getPropertyId(), report.getCreatedByUserId(), report.getDisasterType(),
                report.getStatus(), report.getDescription(), report.getOccurredAt(),
                report.getTotalEstimatedLoss(), report.getCompletedAt(), photos, pairs);
    }

    PairItem toPairItem(PhotoPair pair) {
        AssetSnapshot snapshot = snapshotMapper.fromJson(pair.getAssetSnapshot());
        return new PairItem(pair.getId(), pair.getDamagePhotoId(), pair.getAssetId(),
                pair.getPairingMethod(), pair.getDistanceMeters(),
                new BeforeBlock(storageProvider.signedUrl(snapshot.objectPath(), signedUrlTtl),
                        snapshot.sha256Hash(), snapshot.description(), snapshot.estimatedValue(),
                        snapshot.category(), snapshot.gpsLat(), snapshot.gpsLng(), snapshot.capturedAt()));
    }

    private Map<UUID, DamageReportRepository.ReportCounts> countsFor(Page<DamageReport> reports) {
        List<UUID> ids = reports.map(DamageReport::getId).getContent();
        return ids.isEmpty() ? Map.of()
                : reportRepository.countsFor(ids).stream()
                        .collect(Collectors.toMap(DamageReportRepository.ReportCounts::getReportId,
                                Function.identity()));
    }

    private static long photoCount(Map<UUID, DamageReportRepository.ReportCounts> counts, UUID id) {
        DamageReportRepository.ReportCounts c = counts.get(id);
        return c == null ? 0 : c.getPhotoCount();
    }

    private static long pairCount(Map<UUID, DamageReportRepository.ReportCounts> counts, UUID id) {
        DamageReportRepository.ReportCounts c = counts.get(id);
        return c == null ? 0 : c.getPairCount();
    }
}
