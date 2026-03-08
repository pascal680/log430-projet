package com.canbankx.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "OTP code submitted by the client to activate their account")
public class OtpVerifyDTO {

    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "\\d{6}", message = "OTP must be exactly 6 digits")
    @Schema(description = "6-digit verification code sent by email", example = "482901")
    private String otpCode;
}
