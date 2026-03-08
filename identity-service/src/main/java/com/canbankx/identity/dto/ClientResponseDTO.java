package com.canbankx.identity.dto;

import com.canbankx.identity.model.Client;
import com.canbankx.identity.model.enums.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Schema(description = "Client data returned by the API")
public class ClientResponseDTO {

    @Schema(description = "Client unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private final UUID id;
    @Schema(description = "First name", example = "Jean")
    private final String firstName;
    @Schema(description = "Last name", example = "Tremblay")
    private final String lastName;
    @Schema(description = "Email address", example = "jean.tremblay@canbankx.ca")
    private final String email;
    @Schema(description = "Phone number", example = "5141234567")
    private final String phoneNumber;
    @Schema(description = "Mailing address", example = "123 Rue Sainte-Catherine, Montréal, QC H3G 1M8")
    private final String address;
    @Schema(description = "Account status", example = "PENDING")
    private final Status status;
    @Schema(description = "Account creation timestamp")
    private final Instant createdAt;
    @Schema(description = "Last update timestamp")
    private final Instant updatedAt;

    public ClientResponseDTO(Client client) {
        this.id = client.getId();
        this.firstName = client.getFirstName();
        this.lastName = client.getLastName();
        this.email = client.getEmail();
        this.phoneNumber = client.getPhoneNumber();
        this.address = client.getAddress();
        this.status = client.getStatus();
        this.createdAt = client.getCreatedAt();
        this.updatedAt = client.getUpdatedAt();
    }
}