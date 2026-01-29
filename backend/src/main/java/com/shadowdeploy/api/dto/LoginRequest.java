package com.shadowdeploy.api.dto;

public record LoginRequest(
        String username,
        String password
) {
}
