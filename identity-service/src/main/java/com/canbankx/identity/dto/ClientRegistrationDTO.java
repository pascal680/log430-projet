package com.canbankx.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Payload to register a new client")
public class ClientRegistrationDTO {

    @NotBlank(message = "First name is required")
    @Schema(description = "Client's first name", example = "Jean")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Schema(description = "Client's last name", example = "Tremblay")
    private String lastName;

    @Email(message = "Email must be a valid address")
    @NotBlank(message = "Email is required")
    @Schema(description = "Client's email address", example = "jean.tremblay@canbankx.ca")
    private String email;

    @NotBlank(message = "Password is required")
    @Schema(description = "Password (min 8 chars)", example = "SecurePass123!")
    private String password;

    @NotBlank(message = "Phone number is required")
    @Schema(description = "Phone number", example = "5141234567")
    private String phoneNumber;

    @NotBlank(message = "Address is required")
    @Schema(description = "Mailing address", example = "123 Rue Sainte-Catherine, Montréal, QC H3G 1M8")
    private String address;

    @NotBlank(message = "NAS (Social Insurance Number) is required")
    @Schema(description = "Social Insurance Number (9 digits)", example = "123456789")
    private String nas;
}