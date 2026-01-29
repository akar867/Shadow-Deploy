package com.shadowdeploy.api.dto;

public record AuthResponse(
        String token,
        UserProfileResponse user,
        String expiresAt
) {
}
