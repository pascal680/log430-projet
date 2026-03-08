package com.canbankx.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "Response containing a MFA challenge token")
public class MfaChallengeDTO {
    @Schema(description = "Token to include in the MFA verify request")
    private final String challengeToken;
    @Schema(description = "Human-readable instruction")
    private final String message;
}