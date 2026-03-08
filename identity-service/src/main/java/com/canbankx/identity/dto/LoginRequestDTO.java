package com.canbankx.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Login credentials")
public class LoginRequestDTO {

    @Email
    @NotBlank
    @Schema(description = "Registered email", example = "jean.tremblay@canbankx.ca")
    private String email;

    @NotBlank
    @Schema(description = "Account password", example = "SecurePass123!")
    private String password;
}