package com.assetshield.damage.service;

import com.assetshield.damage.client.AuthUserClient;
import com.assetshield.damage.client.NotificationClient;
import com.assetshield.damage.client.PropertyInternalClient;
import com.assetshield.damage.domain.AssetSnapshot;
import com.assetshield.damage.domain.DamagePhoto;
import com.assetshield.damage.domain.DamageReport;
import com.assetshield.damage.domain.Dossier;
import com.assetshield.damage.domain.DossierStatus;
import com.assetshield.damage.domain.PhotoPair;
import com.assetshield.damage.repo.DamagePhotoRepository;
import com.assetshield.damage.repo.DamageReportRepository;
import com.assetshield.damage.repo.DossierRepository;
import com.assetshield.damage.repo.PhotoPairRepository;
import com.assetshield.damage.storage.StorageProvider;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Async PDF generation (pool of 2, queue 10). Pulls everything from the local
 * repositories (no HTTP self-call), embeds images one at a time, and flips
 * the dossier to READY or FAILED. NFR: a 15-pair report should finish in
 * ≤ 20 s — slower runs log a WARN with the elapsed time.
 */
@Service
public class DossierGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(DossierGeneratorService.class);
    private static final long TARGET_MILLIS = 20_000;

    private final DossierRepository dossierRepository;
    private final DamageReportRepository reportRepository;
    private final DamagePhotoRepository photoRepository;
    private final PhotoPairRepository pairRepository;
    private final SnapshotMapper snapshotMapper;
    private final ManifestService manifestService;
    private final DossierPdfBuilder pdfBuilder;
    private final StorageProvider storageProvider;
    private final PropertyInternalClient propertyClient;
    private final AuthUserClient authUserClient;
    private final NotificationClient notificationClient;

    public DossierGeneratorService(DossierRepository dossierRepository,
                                   DamageReportRepository reportRepository,
                                   DamagePhotoRepository photoRepository,
                                   PhotoPairRepository pairRepository,
                                   SnapshotMapper snapshotMapper,
                                   ManifestService manifestService,
                                   DossierPdfBuilder pdfBuilder,
                                   StorageProvider storageProvider,
                                   PropertyInternalClient propertyClient,
                                   AuthUserClient authUserClient,
                                   NotificationClient notificationClient) {
        this.dossierRepository = dossierRepository;
        this.reportRepository = reportRepository;
        this.photoRepository = photoRepository;
        this.pairRepository = pairRepository;
        this.snapshotMapper = snapshotMapper;
        this.manifestService = manifestService;
        this.pdfBuilder = pdfBuilder;
        this.storageProvider = storageProvider;
        this.propertyClient = propertyClient;
        this.authUserClient = authUserClient;
        this.notificationClient = notificationClient;
    }

    @Async("dossierExecutor")
    public void generate(UUID dossierId) {
        long started = System.currentTimeMillis();
        Dossier dossier = dossierRepository.findById(dossierId).orElse(null);
        if (dossier == null) {
            log.error("Dossier {} vanished before generation", dossierId);
            return;
        }
        try {
            DamageReport report = reportRepository.findByIdAndDeletedAtIsNull(dossier.getDamageReportId())
                    .orElseThrow(() -> new IllegalStateException("Damage report missing"));
            List<DamagePhoto> photos = photoRepository
                    .findByDamageReportIdAndDeletedAtIsNullOrderByCreatedAtAsc(report.getId());
            List<PhotoPair> pairs = pairRepository.findByDamageReportIdOrderByCreatedAtAsc(report.getId());

            // distinct paired assets, first occurrence wins (snapshot is frozen anyway)
            Map<UUID, AssetSnapshot> distinctAssets = new LinkedHashMap<>();
            for (PhotoPair pair : pairs) {
                distinctAssets.computeIfAbsent(pair.getAssetId(),
                        id -> snapshotMapper.fromJson(pair.getAssetSnapshot()));
            }

            Map<UUID, String> assetHashes = distinctAssets.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().sha256Hash()));
            Map<UUID, String> photoHashes = photos.stream()
                    .collect(Collectors.toMap(DamagePhoto::getId, DamagePhoto::getSha256Hash));
            String manifestHash = manifestService.manifestHash(assetHashes, photoHashes);

            String propertyName = "Property";
            String propertyType = "-";
            String locality = "-";
            var property = propertyClient.property(report.getPropertyId());
            if (property.isPresent()) {
                propertyName = property.get().name();
                propertyType = property.get().type();
                locality = property.get().locality();
            }
            String ownerName = property
                    .flatMap(info -> authUserClient.byId(info.ownerUserId()))
                    .map(AuthUserClient.AuthUserInfo::fullName)
                    .orElse("Owner");

            Set<UUID> pairedPhotoIds = pairs.stream().map(PhotoPair::getDamagePhotoId)
                    .collect(Collectors.toSet());
            Map<UUID, DamagePhoto> photosById = photos.stream()
                    .collect(Collectors.toMap(DamagePhoto::getId, p -> p));

            DossierPdfBuilder.Content content = new DossierPdfBuilder.Content(
                    dossier.getId(), propertyName, propertyType, locality, ownerName,
                    report.getDisasterType(), report.getOccurredAt(), report.getCompletedAt(),
                    Instant.now(), report.getDescription(),
                    pairs.stream()
                            .map(pair -> new DossierPdfBuilder.PairContent(pair.getPairingMethod(),
                                    pair.getDistanceMeters(),
                                    snapshotMapper.fromJson(pair.getAssetSnapshot()),
                                    photosById.get(pair.getDamagePhotoId())))
                            .filter(pc -> pc.after() != null)
                            .toList(),
                    photos.stream().filter(p -> !pairedPhotoIds.contains(p.getId())).toList(),
                    distinctAssets.values().stream()
                            .map(s -> new DossierPdfBuilder.AssetRow(s.description(), s.category(),
                                    s.estimatedValue(), s.capturedAt()))
                            .toList(),
                    report.getTotalEstimatedLoss(),
                    manifestService.orderedEntries(assetHashes, photoHashes),
                    manifestHash);

            DossierPdfBuilder.BuiltPdf pdf = pdfBuilder.build(content, storageProvider::load);

            String objectPath = storageProvider.store(pdf.bytes(),
                    "dossiers/" + dossier.getId() + ".pdf", MediaType.APPLICATION_PDF_VALUE);

            dossier.setStatus(DossierStatus.READY);
            dossier.setFileUrl(objectPath);
            dossier.setManifestHash(manifestHash);
            dossier.setPageCount((short) pdf.pageCount());
            dossier.setGeneratedAt(Instant.now());
            dossier.setTotalEstimatedLoss(report.getTotalEstimatedLoss());
            dossier.setFailureReason(null);
            dossierRepository.saveAndFlush(dossier);

            long elapsed = System.currentTimeMillis() - started;
            if (elapsed > TARGET_MILLIS) {
                log.warn("Dossier {} generation took {} ms (target {} ms, {} pairs)",
                        dossierId, elapsed, TARGET_MILLIS, pairs.size());
            } else {
                log.info("Dossier {} READY: {} pages, {} pairs, {} ms",
                        dossierId, pdf.pageCount(), pairs.size(), elapsed);
            }

            notificationClient.send(dossier.getRequestedByUserId(), "DOSSIER_READY",
                    "Your dossier is ready",
                    "The damage evidence dossier for " + propertyName + " is ready to download",
                    Map.of("dossierId", dossier.getId().toString()));
        } catch (Exception e) {
            log.error("Dossier {} generation failed", dossierId, e);
            dossier.setStatus(DossierStatus.FAILED);
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            dossier.setFailureReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
            dossierRepository.saveAndFlush(dossier);
        }
    }
}
