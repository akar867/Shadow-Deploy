package com.shadowdeploy.api.dto;

public record UserProfileResponse(
        String username,
        String displayName,
        String role
) {
}
