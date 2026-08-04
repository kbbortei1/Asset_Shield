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
import com.assetshield.property.domain.AssetPhoto;
import com.assetshield.property.domain.AssetReceipt;
import com.assetshield.property.domain.Property;
import com.assetshield.property.repo.AssetPhotoRepository;
import com.assetshield.property.repo.AssetReceiptRepository;
import com.assetshield.property.repo.AssetRepository;
import com.assetshield.property.repo.PropertyRepository;
import com.assetshield.property.security.AuthUser;
import com.assetshield.property.storage.StorageProvider;
import com.assetshield.property.web.dto.PropertyDtos;
import com.assetshield.property.web.dto.PropertyDtos.AssetDetailResponse;
import com.assetshield.property.web.dto.PropertyDtos.AssetPhotoItem;
import com.assetshield.property.web.dto.PropertyDtos.AssetResponse;
import com.assetshield.property.web.dto.PropertyDtos.CreateAssetMetadata;
import com.assetshield.property.web.dto.PropertyDtos.DeleteResponse;
import com.assetshield.property.web.dto.PropertyDtos.PhotoInput;
import com.assetshield.property.web.dto.PropertyDtos.ReceiptItem;
import com.assetshield.property.web.dto.PropertyDtos.ReceiptMetadata;
import com.assetshield.property.web.dto.PropertyDtos.ReceiptResponse;
import com.assetshield.property.web.dto.PropertyDtos.UpdateAssetRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
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
    private final AssetPhotoRepository assetPhotoRepository;
    private final AssetReceiptRepository receiptRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyAccessService accessService;
    private final FreeTierGuard freeTierGuard;
    private final StorageProvider storageProvider;
    private final EventPublisher eventPublisher;
    private final Duration signedUrlTtl;

    public AssetService(AssetRepository assetRepository, AssetPhotoRepository assetPhotoRepository,
                        AssetReceiptRepository receiptRepository,
                        PropertyRepository propertyRepository, PropertyAccessService accessService,
                        FreeTierGuard freeTierGuard, StorageProvider storageProvider,
                        EventPublisher eventPublisher, AppProperties properties) {
        this.assetRepository = assetRepository;
        this.assetPhotoRepository = assetPhotoRepository;
        this.receiptRepository = receiptRepository;
        this.propertyRepository = propertyRepository;
        this.accessService = accessService;
        this.freeTierGuard = freeTierGuard;
        this.storageProvider = storageProvider;
        this.eventPublisher = eventPublisher;
        this.signedUrlTtl = Duration.ofMinutes(properties.storage().signedUrlTtlMinutes());
    }

    /**
     * Create ONE asset (e.g. "Kitchen") from 1..15 photos. Shared
     * description/value/category live on the asset; each photo keeps its own
     * hash/gps/capturedAt. The {@code files} arrive in the SAME order as
     * {@code metadata.photos()}; photo #0 becomes the cover (mirrored into the
     * Asset columns for back-compat with lists, pairing, dossier and CSV).
     *
     * Processing order is part of the evidence contract: validate + recompute
     * SHA-256 over EVERY file (a single mismatch stores NOTHING) → tier quota →
     * per-photo duplicate check → store objects → insert rows → bump
     * lastDocumentedAt. Nothing is written until every photo has passed.
     */
    @Transactional
    public AssetResponse addAssets(AuthUser user, UUID propertyId, List<MultipartFile> files,
                                   CreateAssetMetadata metadata) {
        Property property = accessService.requireMember(propertyId, user.id());
        List<PhotoInput> inputs = metadata.photos();
        if (files == null || files.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "At least one photo is required");
        }
        if (files.size() != inputs.size()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Number of files does not match the photos metadata");
        }
        if (files.size() > PropertyDtos.MAX_ASSET_PHOTOS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "An asset can hold at most " + PropertyDtos.MAX_ASSET_PHOTOS + " photos");
        }
        freeTierGuard.checkPhotoQuota(user.id(),
                assetPhotoRepository.countByPropertyIdAndDeletedAtIsNull(propertyId), files.size());

        // Validate ALL photos before storing anything: hash match, no repeat
        // within this batch, and no per-property duplicate.
        List<byte[]> allBytes = new ArrayList<>(files.size());
        Set<String> batchHashes = new HashSet<>();
        boolean duplicateElsewhere = false;
        for (int i = 0; i < files.size(); i++) {
            byte[] bytes = readImage(files.get(i));
            String hash = inputs.get(i).sha256Hash().toLowerCase();
            if (!Sha256.matches(bytes, hash)) {
                throw new ApiException(ErrorCode.HASH_MISMATCH,
                        "Declared SHA-256 does not match the uploaded file");
            }
            if (!batchHashes.add(hash)) {
                throw new ApiException(ErrorCode.DUPLICATE_ASSET_HASH,
                        "The same photo was included twice in this upload");
            }
            if (assetPhotoRepository.existsByPropertyIdAndSha256HashAndDeletedAtIsNull(propertyId, hash)) {
                throw new ApiException(ErrorCode.DUPLICATE_ASSET_HASH,
                        "An identical photo already exists on this property");
            }
            // fraud signal: same-property was rejected above, so any remaining
            // match lives on ANOTHER property
            if (assetPhotoRepository.countBySha256HashAndDeletedAtIsNull(hash) > 0) {
                duplicateElsewhere = true;
            }
            allBytes.add(bytes);
        }

        // Store objects, then persist the asset (cover = #0) and its photo rows.
        PhotoInput cover = inputs.get(0);
        String coverPath = storageProvider.store(allBytes.get(0),
                "assets/" + propertyId + "/" + cover.sha256Hash().toLowerCase() + extensionFor(files.get(0)),
                files.get(0).getContentType());

        Asset asset = new Asset();
        asset.setPropertyId(propertyId);
        asset.setCreatedByUserId(user.id());
        asset.setPhotoUrl(coverPath);
        asset.setSha256Hash(cover.sha256Hash().toLowerCase());
        asset.setGpsLat(cover.gpsLat());
        asset.setGpsLng(cover.gpsLng());
        asset.setCapturedAt(cover.capturedAt());
        asset.setDescription(metadata.description().trim());
        asset.setEstimatedValue(metadata.estimatedValue());
        asset.setCategory(metadata.category());
        asset.setWarrantyExpiresOn(metadata.warrantyExpiresOn());
        asset.setNextServiceOn(metadata.nextServiceOn());
        Asset saved = assetRepository.saveAndFlush(asset);

        List<AssetPhoto> rows = new ArrayList<>(files.size());
        rows.add(assetPhotoRow(saved.getId(), propertyId, coverPath, cover, 0));
        for (int i = 1; i < files.size(); i++) {
            PhotoInput in = inputs.get(i);
            String path = storageProvider.store(allBytes.get(i),
                    "assets/" + propertyId + "/" + in.sha256Hash().toLowerCase() + extensionFor(files.get(i)),
                    files.get(i).getContentType());
            rows.add(assetPhotoRow(saved.getId(), propertyId, path, in, i));
        }
        assetPhotoRepository.saveAll(rows);

        property.setLastDocumentedAt(Instant.now());
        propertyRepository.save(property);
        eventPublisher.assetCaptured(user.id(), propertyId);
        return toResponse(saved, 0, files.size(), duplicateElsewhere);
    }

    private static AssetPhoto assetPhotoRow(UUID assetId, UUID propertyId, String objectPath,
                                            PhotoInput in, int position) {
        AssetPhoto photo = new AssetPhoto();
        photo.setAssetId(assetId);
        photo.setPropertyId(propertyId);
        photo.setPhotoUrl(objectPath);
        photo.setSha256Hash(in.sha256Hash().toLowerCase());
        photo.setGpsLat(in.gpsLat());
        photo.setGpsLng(in.gpsLng());
        photo.setCapturedAt(in.capturedAt());
        photo.setPosition(position);
        return photo;
    }

    @Transactional(readOnly = true)
    public PageEnvelope<AssetResponse> listAssets(AuthUser user, UUID propertyId,
                                                  AssetCategory category, String q,
                                                  BigDecimal minValue, BigDecimal maxValue,
                                                  int page, int size) {
        accessService.requireMember(propertyId, user.id());
        Pageable pageable = PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size));
        // "" and the full value range mean "no filter" — see AssetRepository
        String text = q == null ? "" : q.trim();
        if (text.length() > 100) {
            text = text.substring(0, 100);
        }
        BigDecimal min = minValue == null ? BigDecimal.ZERO : minValue;
        BigDecimal max = maxValue == null ? new BigDecimal(PropertyDtos.MAX_VALUE) : maxValue;
        Page<Asset> assets = category == null
                ? assetRepository.search(propertyId, text, min, max, pageable)
                : assetRepository.searchByCategory(propertyId, category, text, min, max, pageable);
        List<UUID> ids = assets.map(Asset::getId).getContent();
        Map<UUID, Long> receiptCounts = ids.isEmpty() ? Map.of()
                : receiptRepository.countsByAsset(ids).stream()
                        .collect(Collectors.toMap(AssetReceiptRepository.ReceiptCount::getAssetId,
                                AssetReceiptRepository.ReceiptCount::getReceiptCount));
        Map<UUID, Long> photoCounts = ids.isEmpty() ? Map.of()
                : assetPhotoRepository.countsByAsset(ids).stream()
                        .collect(Collectors.toMap(AssetPhotoRepository.PhotoCount::getAssetId,
                                AssetPhotoRepository.PhotoCount::getPhotoCount));
        return PageEnvelope.of(assets.map(a ->
                toResponse(a, receiptCounts.getOrDefault(a.getId(), 0L),
                        photoCounts.getOrDefault(a.getId(), 1L))));
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
        List<AssetPhotoItem> photos = assetPhotoRepository
                .findByAssetIdAndDeletedAtIsNullOrderByPositionAsc(assetId).stream()
                .map(p -> new AssetPhotoItem(p.getId(),
                        storageProvider.signedUrl(p.getPhotoUrl(), signedUrlTtl), p.getSha256Hash(),
                        p.getGpsLat(), p.getGpsLng(), p.getCapturedAt()))
                .toList();
        // Pre-multi-photo assets have no rows yet in the (backfilled) table only
        // if something went wrong; fall back to the cover so detail never blanks.
        if (photos.isEmpty()) {
            photos = List.of(new AssetPhotoItem(asset.getId(),
                    storageProvider.signedUrl(asset.getPhotoUrl(), signedUrlTtl), asset.getSha256Hash(),
                    asset.getGpsLat(), asset.getGpsLng(), asset.getCapturedAt()));
        }
        return new AssetDetailResponse(asset.getId(), asset.getPropertyId(),
                storageProvider.signedUrl(asset.getPhotoUrl(), signedUrlTtl), asset.getSha256Hash(),
                asset.getGpsLat(), asset.getGpsLng(), asset.getCapturedAt(), asset.getDescription(),
                asset.getEstimatedValue(), asset.getCategory(), asset.getWarrantyExpiresOn(),
                asset.getNextServiceOn(), asset.getCreatedByUserId(), asset.getCreatedAt(), photos, receipts);
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
        if (request.warrantyExpiresOn() != null) {
            asset.setWarrantyExpiresOn(request.warrantyExpiresOn());
        }
        if (request.nextServiceOn() != null) {
            asset.setNextServiceOn(request.nextServiceOn());
        }
        assetRepository.save(asset);
        return getAsset(user, assetId);
    }

    @Transactional
    public DeleteResponse deleteAsset(AuthUser user, UUID assetId) {
        Asset asset = requireEditable(user, assetId);
        Instant now = Instant.now();
        receiptRepository.softDeleteByAsset(assetId, now);
        assetPhotoRepository.softDeleteByAsset(assetId, now);
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

    AssetResponse toResponse(Asset asset, long receiptCount, long photoCount) {
        return toResponse(asset, receiptCount, photoCount, null);
    }

    AssetResponse toResponse(Asset asset, long receiptCount, long photoCount, Boolean duplicateWarning) {
        return new AssetResponse(asset.getId(), asset.getPropertyId(),
                storageProvider.signedUrl(asset.getPhotoUrl(), signedUrlTtl), asset.getSha256Hash(),
                asset.getGpsLat(), asset.getGpsLng(), asset.getCapturedAt(), asset.getDescription(),
                asset.getEstimatedValue(), asset.getCategory(), asset.getWarrantyExpiresOn(),
                asset.getNextServiceOn(), receiptCount, photoCount, asset.getCreatedByUserId(),
                asset.getCreatedAt(), duplicateWarning);
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
