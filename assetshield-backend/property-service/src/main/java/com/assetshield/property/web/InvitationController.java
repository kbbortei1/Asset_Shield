package com.assetshield.property.web;

import com.assetshield.property.common.ApiResponse;
import com.assetshield.property.service.InvitationService;
import com.assetshield.property.web.dto.PropertyDtos.MyInvitationItem;
import com.assetshield.property.web.dto.PropertyDtos.RespondRequest;
import com.assetshield.property.web.dto.PropertyDtos.RespondResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Invitations", description = "Household invitations addressed to the authenticated user")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @Operation(summary = "My pending, unexpired household invitations")
    @GetMapping("/api/v1/users/me/invitations")
    public ApiResponse<Map<String, List<MyInvitationItem>>> myInvitations(Authentication authentication) {
        return ApiResponse.success(
                Map.of("items", invitationService.myInvitations(
                        PropertyController.principal(authentication))),
                "Invitations fetched");
    }

    @Operation(summary = "Accept or decline an invitation (invitee only)")
    @PutMapping("/api/v1/invitations/{id}/respond")
    public ApiResponse<RespondResponse> respond(Authentication authentication, @PathVariable UUID id,
                                                @Valid @RequestBody RespondRequest request) {
        return ApiResponse.success(
                invitationService.respond(PropertyController.principal(authentication), id, request),
                "Invitation " + (request.accept() ? "accepted" : "declined"));
    }
}
