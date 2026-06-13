package com.assetshield.notification.web;

import com.assetshield.notification.common.ApiResponse;
import com.assetshield.notification.service.NotificationDispatchService;
import com.assetshield.notification.service.TipGenerationService;
import com.assetshield.notification.web.dto.NotificationDtos.AcceptedResponse;
import com.assetshield.notification.web.dto.NotificationDtos.AssetCapturedRequest;
import com.assetshield.notification.web.dto.NotificationDtos.InternalSendRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service entry points. Never routed by the gateway; reachable
 * only on the internal Docker network and guarded by X-Internal-Api-Key.
 */
@RestController
@RequestMapping("/internal")
public class InternalNotificationController {

    private final NotificationDispatchService dispatchService;
    private final TipGenerationService tipGenerationService;

    public InternalNotificationController(NotificationDispatchService dispatchService,
                                          TipGenerationService tipGenerationService) {
        this.dispatchService = dispatchService;
        this.tipGenerationService = tipGenerationService;
    }

    /** The single notify entry point every other service calls. */
    @PostMapping("/notifications/send")
    public ResponseEntity<ApiResponse<AcceptedResponse>> send(@Valid @RequestBody InternalSendRequest request) {
        dispatchService.dispatchAsync(request.userId(), request.type(), request.title(),
                request.body(), request.payload());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(new AcceptedResponse(true), "Notification accepted"));
    }

    /** property-service asset uploads: debounced tip generation. */
    @PostMapping("/events/asset-captured")
    public ResponseEntity<ApiResponse<AcceptedResponse>> assetCaptured(
            @Valid @RequestBody AssetCapturedRequest request) {
        tipGenerationService.onAssetCaptured(request.propertyId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(new AcceptedResponse(true), "Event accepted"));
    }
}
