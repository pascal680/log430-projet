package com.canbankx.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MfaVerifyDTO {

    @NotBlank(message = "Challenge token is required")
    private String challengeToken;

    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "\\d{6}", message = "OTP must be exactly 6 digits")
    private String otpCode;
}
