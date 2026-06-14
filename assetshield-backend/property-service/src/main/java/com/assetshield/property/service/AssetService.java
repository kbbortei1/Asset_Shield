package com.assetshield.property.service;

import com.assetshield.property.access.AccessLevel;
import com.assetshield.property.access.PropertyAccessService;
import com.assetshield.property.client.EventPublisher;
import com.assetshield.property.common.ApiException;
import com.assetshield.property.common.ErrorCode;
import com.assetshield.property.common.PageEnvelope;
import com.assetshield.property.config.AppProperties;
import com.assetshield.property.domain.Asset;
import com.assetshield.property.domain.AssetCategory;
import com.assetshield.property.domain.AssetReceipt;
import com.assetshield.property.domain.Property;
import com.assetshield.property.repo.AssetReceiptRepository;
import com.assetshield.property.repo.AssetRepository;
import com.assetshield.property.repo.PropertyRepository;
import com.assetshield.property.security.AuthUser;
import com.assetshield.property.storage.StorageProvider;
import com.assetshield.property.web.dto.PropertyDtos.AssetDetailResponse;
import com.assetshield.property.web.dto.PropertyDtos.AssetMetadata;
import com.assetshield.property.web.dto.PropertyDtos.AssetResponse;
import com.assetshield.property.web.dto.PropertyDtos.DeleteResponse;
import com.assetshield.property.web.dto.PropertyDtos.ReceiptItem;
import com.assetshield.property.web.dto.PropertyDtos.ReceiptMetadata;
import com.assetshield.property.web.dto.PropertyDtos.ReceiptResponse;
import com.assetshield.property.web.dto.PropertyDtos.UpdateAssetRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssetService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of(MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE);

    private final AssetRepository assetRepository;
    private final AssetReceiptRepository receiptRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyAccessService accessService;
    private final FreeTierGuard freeTierGuard;
    private final StorageProvider storageProvider;
    private final EventPublisher eventPublisher;
    private final Duration signedUrlTtl;

    public AssetService(AssetRepository assetRepository, AssetReceiptRepository receiptRepository,
                        PropertyRepository propertyRepository, PropertyAccessService accessService,
                        FreeTierGuard freeTierGuard, StorageProvider storageProvider,
                        EventPublisher eventPublisher, AppProperties properties) {
        this.assetRepository = assetRepository;
        this.receiptRepository = receiptRepository;
        this.propertyRepository = propertyRepository;
        this.accessService = accessService;
        this.freeTierGuard = freeTierGuard;
        this.storageProvider = storageProvider;
        this.eventPublisher = eventPublisher;
        this.signedUrlTtl = Duration.ofMinutes(properties.storage().signedUrlTtlMinutes());
    }

    /**
     * Processing order is part of the evidence contract: validate → recompute
     * SHA-256 over the received bytes (mismatch stores NOTHING) → tier quota →
     * duplicate check → store object → insert row → bump lastDocumentedAt.
     */
    @Transactional
    public AssetResponse addAsset(AuthUser user, UUID propertyId, MultipartFile file, AssetMetadata metadata) {
        Property property = accessService.requireMember(propertyId, user.id());
        byte[] bytes = readImage(file);
        String hash = metadata.sha256Hash().toLowerCase();
        if (!Sha256.matches(bytes, hash)) {
            throw new ApiException(ErrorCode.HASH_MISMATCH,
                    "Declared SHA-256 does not match the uploaded file");
        }
        freeTierGuard.checkAssetQuota(user.id(),
                assetRepository.countByPropertyIdAndDeletedAtIsNull(propertyId));
        if (assetRepository.existsByPropertyIdAndSha256HashAndDeletedAtIsNull(propertyId, hash)) {
            throw new ApiException(ErrorCode.DUPLICATE_ASSET_HASH,
                    "An identical photo already exists on this property");
        }
        String objectPath = storageProvider.store(bytes,
                "assets/" + propertyId + "/" + hash + extensionFor(file), file.getContentType());

        Asset asset = new Asset();
        asset.setPropertyId(propertyId);
        asset.setCreatedByUserId(user.id());
        asset.setPhotoUrl(objectPath);
        asset.setSha256Hash(hash);
        asset.setGpsLat(metadata.gpsLat());
        asset.setGpsLng(metadata.gpsLng());
        asset.setCapturedAt(metadata.capturedAt());
        asset.setDescription(metadata.description().trim());
        asset.setEstimatedValue(metadata.estimatedValue());
        asset.setCategory(metadata.category());
        // flush so @CreationTimestamp is populated before the DTO is built
        Asset saved = assetRepository.saveAndFlush(asset);

        property.setLastDocumentedAt(Instant.now());
        propertyRepository.save(property);
        eventPublisher.assetCaptured(user.id(), propertyId);
        return toResponse(saved, 0);
    }

    @Transactional(readOnly = true)
    public PageEnvelope<AssetResponse> listAssets(AuthUser user, UUID propertyId,
                                                  AssetCategory category, int page, int size) {
        accessService.requireMember(propertyId, user.id());
        Pageable pageable = PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size));
        Page<Asset> assets = category == null
                ? assetRepository.findByPropertyIdAndDeletedAtIsNullOrderByCreatedAtDesc(propertyId, pageable)
                : assetRepository.findByPropertyIdAndCategoryAndDeletedAtIsNullOrderByCreatedAtDesc(
                        propertyId, category, pageable);
        List<UUID> ids = assets.map(Asset::getId).getContent();
        Map<UUID, Long> receiptCounts = ids.isEmpty() ? Map.of()
                : receiptRepository.countsByAsset(ids).stream()
                        .collect(Collectors.toMap(AssetReceiptRepository.ReceiptCount::getAssetId,
                                AssetReceiptRepository.ReceiptCount::getReceiptCount));
        return PageEnvelope.of(assets.map(a ->
                toResponse(a, receiptCounts.getOrDefault(a.getId(), 0L))));
    }

    @Transactional(readOnly = true)
    public AssetDetailResponse getAsset(AuthUser user, UUID assetId) {
        Asset asset = requireAsset(assetId);
        accessService.requireMember(asset.getPropertyId(), user.id());
        List<ReceiptItem> receipts = receiptRepository
                .findByAssetIdAndDeletedAtIsNullOrderByCreatedAtAsc(assetId).stream()
                .map(r -> new ReceiptItem(r.getId(),
                        storageProvider.signedUrl(r.getReceiptUrl(), signedUrlTtl), r.getCreatedAt()))
                .toList();
        return new AssetDetailResponse(asset.getId(), asset.getPropertyId(),
                storageProvider.signedUrl(asset.getPhotoUrl(), signedUrlTtl), asset.getSha256Hash(),
                asset.getGpsLat(), asset.getGpsLng(), asset.getCapturedAt(), asset.getDescription(),
                asset.getEstimatedValue(), asset.getCategory(), asset.getCreatedByUserId(),
                asset.getCreatedAt(), receipts);
    }

    /** Only description, value and category — photo, hash, GPS, capturedAt are immutable evidence. */
    @Transactional
    public AssetDetailResponse updateAsset(AuthUser user, UUID assetId, UpdateAssetRequest request) {
        Asset asset = requireEditable(user, assetId);
        if (request.description() != null && !request.description().isBlank()) {
            asset.setDescription(request.description().trim());
        }
        if (request.estimatedValue() != null) {
            asset.setEstimatedValue(request.estimatedValue());
        }
        if (request.category() != null) {
            asset.setCategory(request.category());
        }
        assetRepository.save(asset);
        return getAsset(user, assetId);
    }

    @Transactional
    public DeleteResponse deleteAsset(AuthUser user, UUID assetId) {
        Asset asset = requireEditable(user, assetId);
        Instant now = Instant.now();
        receiptRepository.softDeleteByAsset(assetId, now);
        asset.setDeletedAt(now);
        assetRepository.save(asset);
        return new DeleteResponse(true);
    }

    @Transactional
    public ReceiptResponse addReceipt(AuthUser user, UUID assetId, MultipartFile file,
                                      ReceiptMetadata metadata) {
        Asset asset = requireAsset(assetId);
        accessService.requireMember(asset.getPropertyId(), user.id());
        byte[] bytes = readImage(file);
        String hash = metadata.sha256Hash().toLowerCase();
        if (!Sha256.matches(bytes, hash)) {
            throw new ApiException(ErrorCode.HASH_MISMATCH,
                    "Declared SHA-256 does not match the uploaded file");
        }
        String objectPath = storageProvider.store(bytes,
                "receipts/" + assetId + "/" + hash + extensionFor(file), file.getContentType());
        AssetReceipt receipt = new AssetReceipt();
        receipt.setAssetId(assetId);
        receipt.setReceiptUrl(objectPath);
        receipt.setSha256Hash(hash);
        receipt.setUploadedByUserId(user.id());
        // flush so @CreationTimestamp is populated before the DTO is built
        AssetReceipt saved = receiptRepository.saveAndFlush(receipt);
        return new ReceiptResponse(saved.getId(), assetId,
                storageProvider.signedUrl(objectPath, signedUrlTtl), saved.getCreatedAt());
    }

    public Asset requireAsset(UUID assetId) {
        return assetRepository.findByIdAndDeletedAtIsNull(assetId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Asset not found"));
    }

    /** Property OWNER may edit any asset; members only their own uploads. */
    private Asset requireEditable(AuthUser user, UUID assetId) {
        Asset asset = requireAsset(assetId);
        AccessLevel access = accessService.access(asset.getPropertyId(), user.id());
        if (!access.canView()) {
            throw new ApiException(ErrorCode.NOT_MEMBER, "You are not a member of this property");
        }
        if (!access.isOwner() && !asset.getCreatedByUserId().equals(user.id())) {
            throw new ApiException(ErrorCode.NOT_OWNER,
                    "Members can only modify assets they uploaded themselves");
        }
        return asset;
    }

    AssetResponse toResponse(Asset asset, long receiptCount) {
        return new AssetResponse(asset.getId(), asset.getPropertyId(),
                storageProvider.signedUrl(asset.getPhotoUrl(), signedUrlTtl), asset.getSha256Hash(),
                asset.getGpsLat(), asset.getGpsLng(), asset.getCapturedAt(), asset.getDescription(),
                asset.getEstimatedValue(), asset.getCategory(), receiptCount,
                asset.getCreatedByUserId(), asset.getCreatedAt());
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
