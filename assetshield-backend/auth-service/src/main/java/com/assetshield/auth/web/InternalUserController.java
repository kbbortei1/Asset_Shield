package com.assetshield.auth.web;

import com.assetshield.auth.common.ApiResponse;
import com.assetshield.auth.service.UserService;
import com.assetshield.auth.web.dto.AuthDtos.InternalUserResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service lookups. Never routed by the gateway; reachable only on
 * the internal Docker network and guarded by the X-Internal-Api-Key header.
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public ApiResponse<InternalUserResponse> byId(@PathVariable UUID id) {
        return ApiResponse.success(userService.internalById(id), "User fetched");
    }

    @GetMapping("/by-phone/{phone}")
    public ApiResponse<InternalUserResponse> byPhone(@PathVariable String phone) {
        return ApiResponse.success(userService.internalByPhone(phone), "User fetched");
    }
}
