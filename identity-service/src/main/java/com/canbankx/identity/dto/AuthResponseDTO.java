package com.canbankx.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "Auth response after login or MFA verification")
public class AuthResponseDTO {
    @Schema(description = "Result status", example = "SUCCESS")
    private final String status;
    @Schema(description = "Descriptive message", example = "Login successful")
    private final String message;
    @Schema(description = "Client ID when authentication is complete", example = "550e8400-e29b-41d4-a716-446655440000")
    private final String clientId;
}