package com.assetshield.damage.service;

import com.assetshield.damage.client.PaymentServiceClient;
import com.assetshield.damage.client.PropertyInternalClient;
import com.assetshield.damage.common.ApiException;
import com.assetshield.damage.common.ErrorCode;
import com.assetshield.damage.common.PageEnvelope;
import com.assetshield.damage.config.AppProperties;
import com.assetshield.damage.domain.AssetSnapshot;
import com.assetshield.damage.domain.DamagePhoto;
import com.assetshield.damage.domain.DamageReport;
import com.assetshield.damage.domain.Dossier;
import com.assetshield.damage.domain.DossierStatus;
import com.assetshield.damage.domain.PhotoPair;
import com.assetshield.damage.domain.ReportStatus;
import com.assetshield.damage.repo.DamagePhotoRepository;
import com.assetshield.damage.repo.DossierRepository;
import com.assetshield.damage.repo.PhotoPairRepository;
import com.assetshield.damage.security.AuthUser;
import com.assetshield.damage.storage.StorageProvider;
import com.assetshield.damage.web.dto.DossierDtos.DownloadResponse;
import com.assetshield.damage.web.dto.DossierDtos.GenerateResponse;
import com.assetshield.damage.web.dto.DossierDtos.MetaResponse;
import com.assetshield.damage.web.dto.DossierDtos.Mismatch;
import com.assetshield.damage.web.dto.DossierDtos.MyDossierItem;
import com.assetshield.damage.web.dto.DossierDtos.PaymentBlock;
import com.assetshield.damage.web.dto.DossierDtos.PaymentConfirmedResponse;
import com.assetshield.damage.web.dto.DossierDtos.RotateResponse;
import com.assetshield.damage.web.dto.DossierDtos.SharedResponse;
import com.assetshield.damage.web.dto.DossierDtos.StatusResponse;
import com.assetshield.damage.web.dto.DossierDtos.VerifyResponse;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DossierService {

    private static final Logger log = LoggerFactory.getLogger(DossierService.class);
    private static final Set<DossierStatus> LIVE_STATUSES =
            Set.of(DossierStatus.PENDING_PAYMENT, DossierStatus.GENERATING, DossierStatus.READY);
    private static final DateTimeFormatter FILE_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final DossierRepository dossierRepository;
    private final DamagePhotoRepository photoRepository;
    private final PhotoPairRepository pairRepository;
    private final ReportGuard guard;
    private final PaymentServiceClient paymentClient;
    private final PropertyInternalClient propertyClient;
    private final DossierGeneratorService generatorService;
    private final ManifestService manifestService;
    private final SnapshotMapper snapshotMapper;
    private final StorageProvider storageProvider;
    private final Duration signedUrlTtl;
    private final java.math.BigDecimal dossierFee;

    public DossierService(DossierRepository dossierRepository,
                          DamagePhotoRepository photoRepository,
                          PhotoPairRepository pairRepository,
                          ReportGuard guard,
                          PaymentServiceClient paymentClient,
                          PropertyInternalClient propertyClient,
                          DossierGeneratorService generatorService,
                          ManifestService manifestService,
                          SnapshotMapper snapshotMapper,
                          StorageProvider storageProvider,
                          AppProperties properties) {
        this.dossierRepository = dossierRepository;
        this.photoRepository = photoRepository;
        this.pairRepository = pairRepository;
        this.guard = guard;
        this.paymentClient = paymentClient;
        this.propertyClient = propertyClient;
        this.generatorService = generatorService;
        this.manifestService = manifestService;
        this.snapshotMapper = snapshotMapper;
        this.storageProvider = storageProvider;
        this.signedUrlTtl = Duration.ofMinutes(properties.storage().signedUrlTtlMinutes());
        this.dossierFee = properties.dossierFeeGhs();
    }

    // ── request + payment ───────────────────────────────────────────────────

    @Transactional
    public GenerateResponse requestDossier(AuthUser user, UUID reportId) {
        DamageReport report = guard.requireReport(reportId);
        guard.requireMutate(report.getPropertyId(), user.id());
        if (report.getStatus() != ReportStatus.COMPLETED) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only completed damage reports can be turned into a dossier");
        }

        dossierRepository.findFirstByDamageReportIdAndStatusInOrderByCreatedAtDesc(reportId, LIVE_STATUSES)
                .ifPresent(existing -> {
                    Map<String, String> fields = new LinkedHashMap<>();
                    fields.put("dossierId", existing.getId().toString());
                    fields.put("status", existing.getStatus().name());
                    if (existing.getStatus() == DossierStatus.PENDING_PAYMENT) {
                        // the old init may have expired — give the client a fresh checkout
                        PaymentServiceClient.PaymentInit init = paymentClient.initializeDossierFee(
                                user.id(), user.phone(), dossierFee, existing.getId());
                        fields.put("reference", init.reference());
                        fields.put("authorizationUrl", init.authorizationUrl());
                    }
                    throw new ApiException(ErrorCode.DOSSIER_EXISTS,
                            "A dossier for this report already exists", fields);
                });

        Dossier dossier = new Dossier();
        dossier.setDamageReportId(reportId);
        dossier.setRequestedByUserId(user.id());
        Dossier saved = dossierRepository.saveAndFlush(dossier);

        PaymentServiceClient.PaymentInit init = paymentClient.initializeDossierFee(
                user.id(), user.phone(), dossierFee, saved.getId());
        return new GenerateResponse(saved.getId(), saved.getStatus(),
                new PaymentBlock(init.paymentId(), dossierFee, "GHS",
                        init.reference(), init.authorizationUrl()));
    }

    /**
     * Fresh checkout handle for an unpaid dossier, so the client can resume
     * payment any time (e.g. the user abandoned the original checkout and came
     * back later from the dossier list). Each call re-initializes with the
     * provider: old INITIATED rows are harmless, settlement is replay-safe.
     */
    @Transactional
    public GenerateResponse paymentHandle(AuthUser user, UUID dossierId) {
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Dossier not found"));
        DamageReport report = guard.requireReport(dossier.getDamageReportId());
        guard.requireMutate(report.getPropertyId(), user.id());
        if (dossier.getStatus() != DossierStatus.PENDING_PAYMENT) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "This dossier's fee has already been paid");
        }
        PaymentServiceClient.PaymentInit init = paymentClient.initializeDossierFee(
                user.id(), user.phone(), dossierFee, dossier.getId());
        return new GenerateResponse(dossier.getId(), dossier.getStatus(),
                new PaymentBlock(init.paymentId(), dossierFee, "GHS",
                        init.reference(), init.authorizationUrl()));
    }

    // ── status / download / shared / rotate / retry / list ─────────────────

    @Transactional(readOnly = true)
    public StatusResponse status(AuthUser user, UUID dossierId) {
        Dossier dossier = requireAccessible(user, dossierId);
        return new StatusResponse(dossier.getId(), dossier.getStatus(),
                dossier.getTotalEstimatedLoss(), dossier.getPageCount(), dossier.getManifestHash(),
                dossier.getGeneratedAt(), dossier.getFailureReason());
    }

    @Transactional(readOnly = true)
    public DownloadResponse download(AuthUser user, UUID dossierId) {
        Dossier dossier = requireAccessible(user, dossierId);
        switch (dossier.getStatus()) {
            case PENDING_PAYMENT -> throw new ApiException(ErrorCode.PAYMENT_REQUIRED,
                    "The dossier fee has not been paid yet");
            case GENERATING -> throw new ApiException(ErrorCode.GENERATION_IN_PROGRESS,
                    "The dossier is still being generated");
            case FAILED -> throw new ApiException(ErrorCode.GENERATION_FAILED,
                    "Dossier generation failed: " + dossier.getFailureReason());
            case READY -> { /* fall through */ }
        }
        DamageReport report = guard.requireReport(dossier.getDamageReportId());
        String propertyName = propertyClient.property(report.getPropertyId())
                .map(PropertyInternalClient.PropertyInfo::name)
                .orElse("Property");
        String fileName = "AssetShield_Dossier_" + propertyName.replaceAll("[^A-Za-z0-9]+", "")
                + "_" + FILE_DATE.format(dossier.getGeneratedAt()) + ".pdf";
        return new DownloadResponse(storageProvider.signedUrl(dossier.getFileUrl(), signedUrlTtl),
                fileName);
    }

    /**
     * Signed download for a consented agent. The caller (marketplace) has
     * already enforced an active, unrevoked share, so there is no owner check
     * here — only the READY gate, mirroring {@link #download}. A fresh signed
     * URL is minted each call, so a revoked agent (who can no longer reach this
     * path) cannot mint a new one.
     */
    @Transactional(readOnly = true)
    public DownloadResponse signedDownloadForShared(UUID dossierId) {
        Dossier dossier = dossierRepository.findById(dossierId)
                .filter(d -> d.getStatus() == DossierStatus.READY)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Dossier not found"));
        DamageReport report = guard.requireReport(dossier.getDamageReportId());
        String propertyName = propertyClient.property(report.getPropertyId())
                .map(PropertyInternalClient.PropertyInfo::name)
                .orElse("Property");
        String fileName = "AssetShield_Dossier_" + propertyName.replaceAll("[^A-Za-z0-9]+", "")
                + "_" + FILE_DATE.format(dossier.getGeneratedAt()) + ".pdf";
        return new DownloadResponse(storageProvider.signedUrl(dossier.getFileUrl(), signedUrlTtl),
                fileName);
    }

    /** Public share link: READY only — every other state is an opaque 404. */
    @Transactional(readOnly = true)
    public SharedResponse shared(UUID shareToken) {
        Dossier dossier = dossierRepository.findByShareToken(shareToken)
                .filter(d -> d.getStatus() == DossierStatus.READY)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Not found"));
        DamageReport report = guard.requireReport(dossier.getDamageReportId());
        String propertyName = propertyClient.property(report.getPropertyId())
                .map(PropertyInternalClient.PropertyInfo::name)
                .orElse("Property");
        return new SharedResponse(storageProvider.signedUrl(dossier.getFileUrl(), signedUrlTtl),
                propertyName, report.getDisasterType(), dossier.getGeneratedAt(),
                dossier.getManifestHash());
    }

    @Transactional
    public RotateResponse rotateShareToken(AuthUser user, UUID dossierId) {
        Dossier dossier = requireAccessible(user, dossierId);
        dossier.setShareToken(UUID.randomUUID());
        dossierRepository.saveAndFlush(dossier);
        return new RotateResponse(dossier.getShareToken());
    }

    @Transactional
    public StatusResponse retry(AuthUser user, UUID dossierId) {
        Dossier dossier = requireAccessible(user, dossierId);
        if (dossier.getStatus() != DossierStatus.FAILED || dossier.getPaymentId() == null) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Retry is only possible for a failed, already-paid dossier");
        }
        dossier.setStatus(DossierStatus.GENERATING);
        dossier.setFailureReason(null);
        dossierRepository.saveAndFlush(dossier);
        kickGenerationAfterCommit(dossier.getId());
        return new StatusResponse(dossier.getId(), dossier.getStatus(), dossier.getTotalEstimatedLoss(),
                dossier.getPageCount(), dossier.getManifestHash(), dossier.getGeneratedAt(), null);
    }

    @Transactional(readOnly = true)
    public PageEnvelope<MyDossierItem> myDossiers(AuthUser user, int page, int size) {
        Page<Dossier> dossiers = dossierRepository.findByRequestedByUserIdOrderByCreatedAtDesc(
                user.id(), PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size)));
        Map<UUID, String> propertyNames = new HashMap<>();
        return PageEnvelope.of(dossiers.map(dossier -> {
            DamageReport report = guard.requireReport(dossier.getDamageReportId());
            String propertyName = propertyNames.computeIfAbsent(report.getPropertyId(),
                    id -> propertyClient.property(id)
                            .map(PropertyInternalClient.PropertyInfo::name)
                            .orElse("Property"));
            return new MyDossierItem(dossier.getId(), dossier.getDamageReportId(), propertyName,
                    report.getDisasterType(), dossier.getStatus(), dossier.getTotalEstimatedLoss(),
                    dossier.getGeneratedAt());
        }));
    }

    // ── internal API ────────────────────────────────────────────────────────

    /** The ONLY trigger for PENDING_PAYMENT → GENERATING. Idempotent. */
    @Transactional
    public PaymentConfirmedResponse paymentConfirmed(UUID dossierId, UUID paymentId) {
        Dossier dossier = requireDossier(dossierId);
        switch (dossier.getStatus()) {
            case PENDING_PAYMENT -> {
                dossier.setPaymentId(paymentId);
                dossier.setStatus(DossierStatus.GENERATING);
                dossierRepository.saveAndFlush(dossier);
                kickGenerationAfterCommit(dossier.getId());
            }
            case GENERATING, READY -> log.info(
                    "payment-confirmed replay for dossier {} ({}) — idempotent no-op",
                    dossierId, dossier.getStatus());
            case FAILED -> {
                // settled payment + failed generation: treat the confirm as a retry
                dossier.setPaymentId(paymentId);
                dossier.setStatus(DossierStatus.GENERATING);
                dossier.setFailureReason(null);
                dossierRepository.saveAndFlush(dossier);
                kickGenerationAfterCommit(dossier.getId());
            }
        }
        return new PaymentConfirmedResponse(true);
    }

    @Transactional(readOnly = true)
    public MetaResponse meta(UUID dossierId) {
        Dossier dossier = requireDossier(dossierId);
        DamageReport report = guard.requireReport(dossier.getDamageReportId());
        return new MetaResponse(dossier.getId(), dossier.getRequestedByUserId(),
                report.getPropertyId(), dossier.getDamageReportId(), dossier.getStatus(),
                dossier.getManifestHash(), dossier.getFileUrl(), dossier.getTotalEstimatedLoss(),
                report.getDisasterType(), dossier.getGeneratedAt());
    }

    /**
     * Integrity check: re-downloads every stored object referenced by the
     * dossier's report, re-hashes the bytes, rebuilds the manifest and
     * compares. tamperEvident=true means INTACT.
     */
    @Transactional(readOnly = true)
    public VerifyResponse verify(UUID dossierId) {
        Dossier dossier = requireDossier(dossierId);
        UUID reportId = dossier.getDamageReportId();
        List<DamagePhoto> photos = photoRepository
                .findByDamageReportIdAndDeletedAtIsNullOrderByCreatedAtAsc(reportId);
        List<PhotoPair> pairs = pairRepository.findByDamageReportIdOrderByCreatedAtAsc(reportId);

        Map<UUID, AssetSnapshot> distinctAssets = new LinkedHashMap<>();
        for (PhotoPair pair : pairs) {
            distinctAssets.computeIfAbsent(pair.getAssetId(),
                    id -> snapshotMapper.fromJson(pair.getAssetSnapshot()));
        }

        List<Mismatch> mismatches = new ArrayList<>();
        Map<UUID, String> actualAssetHashes = new LinkedHashMap<>();
        distinctAssets.forEach((assetId, snapshot) -> actualAssetHashes.put(assetId,
                rehash(snapshot.objectPath(), snapshot.sha256Hash(), mismatches)));
        Map<UUID, String> actualPhotoHashes = new LinkedHashMap<>();
        photos.forEach(photo -> actualPhotoHashes.put(photo.getId(),
                rehash(photo.getPhotoUrl(), photo.getSha256Hash(), mismatches)));

        String recomputed = manifestService.manifestHash(actualAssetHashes, actualPhotoHashes);
        boolean intact = mismatches.isEmpty()
                && dossier.getManifestHash() != null
                && dossier.getManifestHash().equals(recomputed);
        return new VerifyResponse(dossier.getManifestHash(), recomputed, intact,
                photos.size(), mismatches);
    }

    /** Re-hash the stored bytes; record a mismatch when they differ from the DB hash. */
    private String rehash(String objectPath, String expected, List<Mismatch> mismatches) {
        String actual;
        try {
            actual = Sha256.hex(storageProvider.load(objectPath));
        } catch (Exception e) {
            actual = "MISSING";
        }
        if (!actual.equalsIgnoreCase(expected)) {
            mismatches.add(new Mismatch(objectPath, expected, actual));
        }
        return actual;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Dossier requireDossier(UUID dossierId) {
        return dossierRepository.findById(dossierId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Dossier not found"));
    }

    private Dossier requireAccessible(AuthUser user, UUID dossierId) {
        Dossier dossier = requireDossier(dossierId);
        DamageReport report = guard.requireReport(dossier.getDamageReportId());
        // The dossier owner (the person who filed the report) can always view and
        // manage it — a dossier is a permanent, sealed record that must survive
        // the property being deleted. Others still need live property access.
        if (!report.getCreatedByUserId().equals(user.id())) {
            guard.requireMutate(report.getPropertyId(), user.id());
        }
        return dossier;
    }

    /** The async generator must only start once the GENERATING row is committed. */
    private void kickGenerationAfterCommit(UUID dossierId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    generatorService.generate(dossierId);
                }
            });
        } else {
            generatorService.generate(dossierId);
        }
    }
}
