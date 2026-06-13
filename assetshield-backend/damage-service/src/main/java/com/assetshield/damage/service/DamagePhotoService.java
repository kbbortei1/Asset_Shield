package com.assetshield.damage.service;

import com.assetshield.damage.client.PropertyInternalClient;
import com.assetshield.damage.common.ApiException;
import com.assetshield.damage.common.ErrorCode;
import com.assetshield.damage.config.AppProperties;
import com.assetshield.damage.domain.DamagePhoto;
import com.assetshield.damage.domain.DamageReport;
import com.assetshield.damage.domain.PhotoPair;
import com.assetshield.damage.repo.DamagePhotoRepository;
import com.assetshield.damage.repo.PhotoPairRepository;
import com.assetshield.damage.security.AuthUser;
import com.assetshield.damage.storage.StorageProvider;
import com.assetshield.damage.web.dto.DamageDtos.PairingSuggestion;
import com.assetshield.damage.web.dto.DamageDtos.PhotoMetadata;
import com.assetshield.damage.web.dto.DamageDtos.PhotoUploadResponse;
import com.assetshield.damage.web.dto.DamageDtos.SuggestionsResponse;
import com.assetshield.damage.web.dto.DamageDtos.UploadedPhoto;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DamagePhotoService {

    private static final Logger log = LoggerFactory.getLogger(DamagePhotoService.class);
    private static final Set<String> ALLOWED_TYPES =
            Set.of(MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE);
    private static final double MAX_RADIUS_METERS = 200;

    private final DamagePhotoRepository photoRepository;
    private final PhotoPairRepository pairRepository;
    private final ReportGuard guard;
    private final PropertyInternalClient propertyClient;
    private final StorageProvider storageProvider;
    private final Duration signedUrlTtl;
    private final double defaultRadiusMeters;

    public DamagePhotoService(DamagePhotoRepository photoRepository,
                              PhotoPairRepository pairRepository,
                              ReportGuard guard,
                              PropertyInternalClient propertyClient,
                              StorageProvider storageProvider,
                              AppProperties properties) {
        this.photoRepository = photoRepository;
        this.pairRepository = pairRepository;
        this.guard = guard;
        this.propertyClient = propertyClient;
        this.storageProvider = storageProvider;
        this.signedUrlTtl = Duration.ofMinutes(properties.storage().signedUrlTtlMinutes());
        this.defaultRadiusMeters = properties.pairingRadiusMeters();
    }

    /**
     * validate → recompute SHA-256 (mismatch stores NOTHING) → duplicate check
     * → store → insert → GPS proximity suggestions. A failed suggestion lookup
     * never fails the upload: photo capture must not be lost to a pairing
     * hiccup — the client can pair manually later.
     */
    @Transactional
    public PhotoUploadResponse addPhoto(AuthUser user, UUID reportId, MultipartFile file,
                                        PhotoMetadata metadata) {
        DamageReport report = guard.requireReport(reportId);
        guard.requireMutate(report.getPropertyId(), user.id());
        guard.requireDraft(report);

        byte[] bytes = readImage(file);
        String hash = metadata.sha256Hash().toLowerCase();
        if (!Sha256.matches(bytes, hash)) {
            throw new ApiException(ErrorCode.HASH_MISMATCH,
                    "Declared SHA-256 does not match the uploaded file");
        }
        if (photoRepository.existsByDamageReportIdAndSha256HashAndDeletedAtIsNull(reportId, hash)) {
            throw new ApiException(ErrorCode.DUPLICATE_PHOTO_HASH,
                    "An identical photo already exists on this damage report");
        }
        String objectPath = storageProvider.store(bytes,
                "damage/" + reportId + "/" + hash + extensionFor(file), file.getContentType());

        DamagePhoto photo = new DamagePhoto();
        photo.setDamageReportId(reportId);
        photo.setPhotoUrl(objectPath);
        photo.setSha256Hash(hash);
        photo.setGpsLat(metadata.gpsLat());
        photo.setGpsLng(metadata.gpsLng());
        photo.setCapturedAt(metadata.capturedAt());
        photo.setDescription(metadata.description() == null || metadata.description().isBlank()
                ? null : metadata.description().trim());
        // flush so @CreationTimestamp is populated before the DTO is built
        DamagePhoto saved = photoRepository.saveAndFlush(photo);

        List<PairingSuggestion> suggestions = suggestionsFor(report, saved, defaultRadiusMeters);
        return new PhotoUploadResponse(
                new UploadedPhoto(saved.getId(),
                        storageProvider.signedUrl(objectPath, signedUrlTtl), saved.getSha256Hash(),
                        saved.getGpsLat(), saved.getGpsLng(), saved.getCapturedAt()),
                suggestions);
    }

    /** On-demand re-run for the manual-pairing screen. Radius capped at 200 m. */
    @Transactional(readOnly = true)
    public SuggestionsResponse suggestions(AuthUser user, UUID reportId, UUID photoId, Double radiusM) {
        DamageReport report = guard.requireReport(reportId);
        guard.requireMutate(report.getPropertyId(), user.id());
        guard.requireDraft(report);
        DamagePhoto photo = requirePhotoOfReport(reportId, photoId);
        double radius = radiusM == null ? defaultRadiusMeters
                : Math.min(Math.max(radiusM, 1), MAX_RADIUS_METERS);
        return new SuggestionsResponse(suggestionsFor(report, photo, radius));
    }

    DamagePhoto requirePhotoOfReport(UUID reportId, UUID photoId) {
        DamagePhoto photo = photoRepository.findByIdAndDeletedAtIsNull(photoId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Damage photo not found"));
        if (!photo.getDamageReportId().equals(reportId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Damage photo not found");
        }
        return photo;
    }

    private List<PairingSuggestion> suggestionsFor(DamageReport report, DamagePhoto photo, double radiusM) {
        Set<UUID> alreadyPaired = pairRepository.findByDamagePhotoId(photo.getId()).stream()
                .map(PhotoPair::getAssetId)
                .collect(Collectors.toSet());
        try {
            return propertyClient.assetsNear(report.getPropertyId(),
                            photo.getGpsLat(), photo.getGpsLng(), radiusM).stream()
                    .filter(near -> !alreadyPaired.contains(near.assetId()))
                    .map(near -> new PairingSuggestion(near.assetId(), near.distanceMeters(),
                            near.description(), near.estimatedValue(), near.category(),
                            near.thumbnailUrl(), near.capturedAt()))
                    .toList();
        } catch (Exception e) {
            log.warn("Pairing suggestion lookup failed for photo {} (report {}): {}",
                    photo.getId(), report.getId(), e.getMessage());
            return List.of();
        }
    }

    private static byte[] readImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Uploaded file is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Only image/jpeg and image/png uploads are accepted");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read uploaded file", e);
        }
    }

    private static String extensionFor(MultipartFile file) {
        return MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(file.getContentType()) ? ".png" : ".jpg";
    }
}
